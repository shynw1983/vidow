package com.vidfit.converter.model

import androidx.annotation.StringRes
import com.vidfit.converter.R

enum class ConversionPreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val badgeRes: Int
) {
    UNIVERSAL_COMPAT(
        id = "universal_compat",
        titleRes = R.string.preset_universal_title,
        subtitleRes = R.string.preset_universal_sub,
        badgeRes = R.string.preset_universal_badge
    ),
    UPLOAD_25MB(
        id = "upload_25mb",
        titleRes = R.string.preset_25mb_title,
        subtitleRes = R.string.preset_25mb_sub,
        badgeRes = R.string.preset_25mb_badge
    ),
    DISCORD_10MB(
        id = "discord_10mb",
        titleRes = R.string.preset_10mb_title,
        subtitleRes = R.string.preset_10mb_sub,
        badgeRes = R.string.preset_10mb_badge
    ),
    SOCIAL_VERTICAL(
        id = "social_vertical",
        titleRes = R.string.preset_vertical_title,
        subtitleRes = R.string.preset_vertical_sub,
        badgeRes = R.string.preset_vertical_badge
    ),
    HIGH_QUALITY_COMPAT(
        id = "hq_compat",
        titleRes = R.string.preset_hq_title,
        subtitleRes = R.string.preset_hq_sub,
        badgeRes = R.string.preset_hq_badge
    );

    fun toConfig(trimStartMs: Long = 0L, trimEndMs: Long? = null): ConversionConfig = when (this) {
        UNIVERSAL_COMPAT -> ConversionConfig(
            presetId = id,
            videoCodec = OutputVideoCodec.H264,
            toneMapHdrToSdr = true,
            resolutionCap = ResolutionCap.P1080,
            frameRateCap = FrameRateCap.FPS_30,
            aspectRatio = AspectRatioOption.ORIGINAL,
            compressionMode = CompressionMode.QUALITY_LEVEL,
            qualityTier = QualityTier.BALANCED,
            stripLocationMetadata = true,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        UPLOAD_25MB -> ConversionConfig(
            presetId = id,
            videoCodec = OutputVideoCodec.H264,
            toneMapHdrToSdr = true,
            resolutionCap = ResolutionCap.P1080,
            frameRateCap = FrameRateCap.FPS_30,
            aspectRatio = AspectRatioOption.ORIGINAL,
            compressionMode = CompressionMode.TARGET_SIZE,
            targetSizeMb = 25f,
            autoDownscaleForTargetSize = true,
            stripLocationMetadata = true,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        DISCORD_10MB -> ConversionConfig(
            presetId = id,
            videoCodec = OutputVideoCodec.H264,
            toneMapHdrToSdr = true,
            resolutionCap = ResolutionCap.P720,
            frameRateCap = FrameRateCap.FPS_30,
            aspectRatio = AspectRatioOption.ORIGINAL,
            compressionMode = CompressionMode.TARGET_SIZE,
            targetSizeMb = 10f,
            autoDownscaleForTargetSize = true,
            stripLocationMetadata = true,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        SOCIAL_VERTICAL -> ConversionConfig(
            presetId = id,
            videoCodec = OutputVideoCodec.H264,
            toneMapHdrToSdr = true,
            resolutionCap = ResolutionCap.P1080,
            frameRateCap = FrameRateCap.FPS_30,
            aspectRatio = AspectRatioOption.RATIO_9_16,
            aspectFitMode = AspectFitMode.CROP,
            compressionMode = CompressionMode.QUALITY_LEVEL,
            qualityTier = QualityTier.HIGH,
            stripLocationMetadata = true,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        HIGH_QUALITY_COMPAT -> ConversionConfig(
            presetId = id,
            videoCodec = OutputVideoCodec.H264,
            toneMapHdrToSdr = true,
            resolutionCap = ResolutionCap.ORIGINAL,
            frameRateCap = FrameRateCap.ORIGINAL,
            aspectRatio = AspectRatioOption.ORIGINAL,
            compressionMode = CompressionMode.QUALITY_LEVEL,
            qualityTier = QualityTier.HIGH,
            stripLocationMetadata = true,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
    }
}
