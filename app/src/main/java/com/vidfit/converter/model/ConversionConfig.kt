package com.vidfit.converter.model

import androidx.annotation.StringRes
import com.vidfit.converter.R

enum class OutputVideoCodec(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
) {
    H264(R.string.codec_h264_label, R.string.codec_h264_desc),
    H265(R.string.codec_h265_label, R.string.codec_h265_desc)
}

enum class ResolutionCap(
    val fixedLabel: String?,
    @StringRes val labelRes: Int?,
    val maxShortSide: Int?
) {
    ORIGINAL(null, R.string.res_original, null),
    P1080("1080p", null, 1080),
    P720("720p", null, 720),
    P480("480p", null, 480)
}

enum class FrameRateCap(
    val fixedLabel: String?,
    @StringRes val labelRes: Int?,
    val fps: Float?
) {
    ORIGINAL(null, R.string.fps_original, null),
    FPS_30("30 fps", null, 30f),
    FPS_24("24 fps", null, 24f)
}

enum class AspectRatioOption(
    @StringRes val labelRes: Int,
    val ratio: Float?
) {
    ORIGINAL(R.string.ratio_original, null),
    RATIO_16_9(R.string.ratio_16_9, 16f / 9f),
    RATIO_9_16(R.string.ratio_9_16, 9f / 16f),
    RATIO_1_1(R.string.ratio_1_1, 1f),
    RATIO_4_5(R.string.ratio_4_5, 4f / 5f)
}

enum class AspectFitMode(@StringRes val labelRes: Int) {
    CROP(R.string.fit_crop),
    FIT(R.string.fit_pad)
}

enum class CompressionMode(@StringRes val labelRes: Int) {
    QUALITY_LEVEL(R.string.mode_quality),
    TARGET_SIZE(R.string.mode_target_size)
}

enum class QualityTier(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val bitsPerPixelPerFrame: Double
) {
    HIGH(R.string.quality_high, R.string.quality_high_desc, 0.14),
    BALANCED(R.string.quality_balanced, R.string.quality_balanced_desc, 0.095),
    SMALL(R.string.quality_small, R.string.quality_small_desc, 0.06)
}

data class ConversionConfig(
    val presetId: String = ConversionPreset.UNIVERSAL_COMPAT.id,
    val videoCodec: OutputVideoCodec = OutputVideoCodec.H264,
    val toneMapHdrToSdr: Boolean = true,
    val resolutionCap: ResolutionCap = ResolutionCap.P1080,
    val frameRateCap: FrameRateCap = FrameRateCap.FPS_30,
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    val aspectFitMode: AspectFitMode = AspectFitMode.CROP,
    val compressionMode: CompressionMode = CompressionMode.QUALITY_LEVEL,
    val qualityTier: QualityTier = QualityTier.BALANCED,
    val targetSizeMb: Float = 25f,
    val autoDownscaleForTargetSize: Boolean = true,
    val removeAudio: Boolean = false,
    val stripLocationMetadata: Boolean = true,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null
) {
    fun effectiveDurationMs(sourceDurationMs: Long): Long {
        val end = (trimEndMs ?: sourceDurationMs).coerceIn(0L, sourceDurationMs)
        val start = trimStartMs.coerceIn(0L, end)
        return (end - start).coerceAtLeast(100L)
    }
}
