package com.vidfit.converter.model

/**
 * One-tap scenario presets hiding codec complexity from everyday users.
 */
enum class ConversionPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    UNIVERSAL_COMPAT(
        id = "universal_compat",
        title = "万能兼容",
        subtitle = "MP4 · H.264 · AAC · HDR转SDR · 旋正",
        badge = "推荐"
    ),
    UPLOAD_25MB(
        id = "upload_25mb",
        title = "压到 25MB 内",
        subtitle = "适配邮件附件 / 常规上传限制",
        badge = "上传"
    ),
    DISCORD_10MB(
        id = "discord_10mb",
        title = "压到 10MB 内",
        subtitle = "适配 Discord / 聊天极速发送",
        badge = "小体积"
    ),
    SOCIAL_VERTICAL(
        id = "social_vertical",
        title = "竖屏短视频 9:16",
        subtitle = "1080p · 30fps · 抖音/小红书/Reels",
        badge = "社媒"
    ),
    HIGH_QUALITY_COMPAT(
        id = "hq_compat",
        title = "高清兼容存档",
        subtitle = "保留原分辨率与高码率，仅转 H.264 + SDR",
        badge = "高清"
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
