package com.vidfit.converter.model

enum class OutputVideoCodec(
    val label: String,
    val description: String
) {
    H264("H.264 (AVC)", "全平台最强兼容，手机/电脑/网页通吃"),
    H265("H.265 (HEVC)", "同等画质体积更小，适合新设备")
}

enum class ResolutionCap(
    val label: String,
    val maxShortSide: Int?
) {
    ORIGINAL("保持原分辨率", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480)
}

enum class FrameRateCap(
    val label: String,
    val fps: Float?
) {
    ORIGINAL("原帧率", null),
    FPS_30("30 fps", 30f),
    FPS_24("24 fps", 24f)
}

enum class AspectRatioOption(
    val label: String,
    val ratio: Float?
) {
    ORIGINAL("原比例", null),
    RATIO_16_9("16:9 横屏", 16f / 9f),
    RATIO_9_16("9:16 竖屏", 9f / 16f),
    RATIO_1_1("1:1 方形", 1f),
    RATIO_4_5("4:5 社媒", 4f / 5f)
}

enum class AspectFitMode(val label: String) {
    CROP("裁剪填满"),
    FIT("完整保留（留黑边）")
}

enum class CompressionMode(val label: String) {
    QUALITY_LEVEL("画质档位"),
    TARGET_SIZE("指定目标体积 (MB)")
}

enum class QualityTier(
    val label: String,
    val description: String,
    /** Bits per pixel per frame factor used for estimating target bitrate. */
    val bitsPerPixelPerFrame: Double
) {
    HIGH("高画质", "细节保留好，适合保存与分享", 0.14),
    BALANCED("均衡", "画质与体积最佳平衡（推荐）", 0.095),
    SMALL("高压缩", "明显减小体积，适合快速上传", 0.06)
}

/**
 * User-selected conversion settings.
 */
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
    /** Target file size in MB when compressionMode == TARGET_SIZE. */
    val targetSizeMb: Float = 25f,
    /** Automatically step down resolution when target size makes bitrate too low. */
    val autoDownscaleForTargetSize: Boolean = true,
    val removeAudio: Boolean = false,
    val stripLocationMetadata: Boolean = true,
    /** Optional clipping range in milliseconds. null means full video. */
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null
) {
    fun effectiveDurationMs(sourceDurationMs: Long): Long {
        val end = (trimEndMs ?: sourceDurationMs).coerceIn(0L, sourceDurationMs)
        val start = trimStartMs.coerceIn(0L, end)
        return (end - start).coerceAtLeast(100L)
    }
}
