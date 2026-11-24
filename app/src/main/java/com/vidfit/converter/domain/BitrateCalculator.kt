package com.vidfit.converter.domain

import com.vidfit.converter.model.AspectRatioOption
import com.vidfit.converter.model.CompressionMode
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.QualityTier
import com.vidfit.converter.model.VideoInfo
import kotlin.math.roundToInt

/**
 * Structured, locale-independent notice produced by [BitrateCalculator].
 */
sealed interface EncodingNotice {
    data class AlreadyUnderTarget(val targetMb: Int) : EncodingNotice
    data class AutoDownscaled(val targetMb: Int, val newShortSide: Int) : EncodingNotice
}

data class EncodingPlan(
    val outputWidth: Int,
    val outputHeight: Int,
    val targetFps: Float?,
    val videoBitrateBps: Int,
    val estimatedAudioBitrateBps: Int,
    val estimatedOutputBytes: Long,
    val notice: EncodingNotice? = null
) {
    val estimatedSizeMb: Double
        get() = estimatedOutputBytes / (1024.0 * 1024.0)
}

object BitrateCalculator {

    const val DEFAULT_AAC_BITRATE_BPS = 128_000
    private const val CONTAINER_SAFETY_MARGIN = 0.92
    private const val MIN_VIDEO_BITRATE_BPS = 250_000
    private const val MAX_VIDEO_BITRATE_BPS = 35_000_000

    fun alignDimension(value: Int, alignment: Int = 8): Int {
        val safeValue = value.coerceAtLeast(alignment)
        val rounded = ((safeValue + alignment / 2) / alignment) * alignment
        return rounded.coerceAtLeast(alignment)
    }

    fun computeDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        maxShortSide: Int?,
        aspectRatioOption: AspectRatioOption
    ): Pair<Int, Int> {
        val safeSrcW = sourceWidth.coerceAtLeast(16)
        val safeSrcH = sourceHeight.coerceAtLeast(16)
        val srcShortSide = minOf(safeSrcW, safeSrcH)
        val effectiveShortSide = if (maxShortSide != null && srcShortSide > maxShortSide) {
            maxShortSide
        } else {
            srcShortSide
        }

        val targetRatio = aspectRatioOption.ratio ?: (safeSrcW.toFloat() / safeSrcH.toFloat())
        val rawW: Double
        val rawH: Double
        if (targetRatio >= 1f) {
            rawH = effectiveShortSide.toDouble()
            rawW = rawH * targetRatio
        } else {
            rawW = effectiveShortSide.toDouble()
            rawH = rawW / targetRatio
        }

        return alignDimension(rawW.roundToInt()) to alignDimension(rawH.roundToInt())
    }

    fun buildPlan(info: VideoInfo, config: ConversionConfig): EncodingPlan {
        val durationSec = config.effectiveDurationMs(info.durationMs) / 1000.0
        val hasOutputAudio = info.hasAudio && !config.removeAudio
        val audioBitrate = if (hasOutputAudio) DEFAULT_AAC_BITRATE_BPS else 0

        val sourceFps = info.frameRate.takeIf { it in 10f..240f } ?: 30f
        var targetFps = config.frameRateCap.fps?.let { cap ->
            if (sourceFps > cap + 1f) cap else null
        }
        var effectiveFps = targetFps ?: sourceFps

        val requestedShortSide = config.resolutionCap.maxShortSide
        var (outW, outH) = computeDimensions(
            sourceWidth = info.displayWidth,
            sourceHeight = info.displayHeight,
            maxShortSide = requestedShortSide,
            aspectRatioOption = config.aspectRatio
        )

        val codecEfficiency = when (config.videoCodec) {
            OutputVideoCodec.H264 -> 1.0
            OutputVideoCodec.H265 -> 0.72
        }

        val sourceCap = if (info.videoBitrateBps > 0) {
            val isMoreEfficientSource = info.videoMimeType.contains("hevc", ignoreCase = true) ||
                info.videoMimeType.contains("av01", ignoreCase = true) ||
                info.videoMimeType.contains("vp9", ignoreCase = true)
            val inflationAllowance = if (isMoreEfficientSource && config.videoCodec == OutputVideoCodec.H264) {
                1.35
            } else {
                1.05
            }
            (info.videoBitrateBps * inflationAllowance).roundToInt().coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
        } else {
            MAX_VIDEO_BITRATE_BPS
        }

        var notice: EncodingNotice? = null
        val videoBitrate: Int

        when (config.compressionMode) {
            CompressionMode.TARGET_SIZE -> {
                val targetBytes = config.targetSizeMb.coerceAtLeast(1f) * 1024.0 * 1024.0
                val totalBps = (targetBytes * 8.0 * CONTAINER_SAFETY_MARGIN) / durationSec
                val targetBudgetBps = (totalBps - audioBitrate).roundToInt()
                    .coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

                val highQualityCeiling = (
                    outW.toDouble() *
                        outH.toDouble() *
                        effectiveFps.coerceAtMost(60f).toDouble() *
                        QualityTier.HIGH.bitsPerPixelPerFrame *
                        codecEfficiency
                    ).roundToInt().coerceAtLeast(MIN_VIDEO_BITRATE_BPS)

                val noBloatCap = minOf(sourceCap, highQualityCeiling)
                if (targetBudgetBps > noBloatCap) {
                    videoBitrate = noBloatCap.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
                    notice = EncodingNotice.AlreadyUnderTarget(config.targetSizeMb.roundToInt())
                } else {
                    videoBitrate = targetBudgetBps
                    if (config.autoDownscaleForTargetSize) {
                        val currentShort = minOf(outW, outH)
                        val recommendedShort = when {
                            videoBitrate < 550_000 -> 360
                            videoBitrate < 1_100_000 -> 480
                            videoBitrate < 2_200_000 -> 720
                            else -> currentShort
                        }
                        if (recommendedShort < currentShort) {
                            val (scaledW, scaledH) = computeDimensions(
                                sourceWidth = info.displayWidth,
                                sourceHeight = info.displayHeight,
                                maxShortSide = recommendedShort,
                                aspectRatioOption = config.aspectRatio
                            )
                            outW = scaledW
                            outH = scaledH
                            notice = EncodingNotice.AutoDownscaled(
                                targetMb = config.targetSizeMb.roundToInt(),
                                newShortSide = recommendedShort
                            )
                        }
                        if (videoBitrate < 1_500_000 && effectiveFps > 31f) {
                            targetFps = 30f
                            effectiveFps = 30f
                        }
                    }
                }
            }

            CompressionMode.QUALITY_LEVEL -> {
                val rawEstimate = (
                    outW.toDouble() *
                        outH.toDouble() *
                        effectiveFps.coerceAtMost(60f).toDouble() *
                        config.qualityTier.bitsPerPixelPerFrame *
                        codecEfficiency
                    ).roundToInt()

                videoBitrate = rawEstimate
                    .coerceAtMost(sourceCap)
                    .coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
            }
        }

        val estimatedBytes = (((videoBitrate.toLong() + audioBitrate.toLong()) * durationSec / 8.0) * 1.03)
            .toLong()
            .coerceAtLeast(1024L)

        return EncodingPlan(
            outputWidth = outW,
            outputHeight = outH,
            targetFps = targetFps,
            videoBitrateBps = videoBitrate,
            estimatedAudioBitrateBps = audioBitrate,
            estimatedOutputBytes = estimatedBytes,
            notice = notice
        )
    }
}
