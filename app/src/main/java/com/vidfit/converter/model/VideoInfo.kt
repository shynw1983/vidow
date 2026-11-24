package com.vidfit.converter.model

import android.net.Uri

/**
 * Compatibility diagnostic warning discovered while inspecting the source video.
 */
enum class CompatibilityIssue(
    val title: String,
    val detail: String
) {
    NON_H264_CODEC(
        title = "非 H.264 编码",
        detail = "部分老旧设备、Windows 默认播放器或网页可能无法解码，建议转为 H.264"
    ),
    HDR_COLOR(
        title = "HDR 色彩",
        detail = "在不支持 HDR 的平台或电脑上可能出现发灰、过曝，建议转为 SDR (Rec.709)"
    ),
    NON_AAC_AUDIO(
        title = "非通用音频编码",
        detail = "部分播放器可能出现“有画无声”，建议统一转为 AAC"
    ),
    HIGH_FPS(
        title = "高帧率视频",
        detail = "高帧率占用额外体积且老旧设备易卡顿，上传场景可降至 30fps"
    ),
    ROTATION_METADATA(
        title = "依赖旋转元数据",
        detail = "部分老播放器忽略旋转标记导致画面横置，转码时将自动旋正"
    ),
    LARGE_FILE_SIZE(
        title = "文件体积较大",
        detail = "超过常见社交或邮件上传限制，建议使用目标体积压缩"
    )
}

/**
 * Inspected metadata for a selected input video.
 */
data class VideoInfo(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    /** Raw coded width before rotation. */
    val rawWidth: Int,
    /** Raw coded height before rotation. */
    val rawHeight: Int,
    /** Rotation degrees stored in container metadata (0, 90, 180, 270). */
    val rotationDegrees: Int,
    val videoMimeType: String,
    val audioMimeType: String?,
    val videoBitrateBps: Int,
    val frameRate: Float,
    val isHdr: Boolean,
    val colorTransferLabel: String,
    val hasAudio: Boolean,
    val hasLocationMetadata: Boolean
) {
    val uri: Uri
        get() = Uri.parse(uriString)
    /** Display width after applying container rotation. */
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight else rawWidth

    /** Display height after applying container rotation. */
    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawWidth else rawHeight

    val isPortrait: Boolean
        get() = displayHeight > displayWidth

    val shortSide: Int
        get() = minOf(displayWidth, displayHeight)

    val fileSizeMb: Double
        get() = fileSizeBytes / (1024.0 * 1024.0)

    val shortCodecName: String
        get() = when {
            videoMimeType.contains("avc", ignoreCase = true) -> "H.264"
            videoMimeType.contains("hevc", ignoreCase = true) -> "H.265 (HEVC)"
            videoMimeType.contains("av01", ignoreCase = true) -> "AV1"
            videoMimeType.contains("vp9", ignoreCase = true) -> "VP9"
            videoMimeType.contains("vp8", ignoreCase = true) -> "VP8"
            videoMimeType.contains("mp4v", ignoreCase = true) -> "MPEG-4"
            videoMimeType.isBlank() -> "未知"
            else -> videoMimeType.substringAfter("/")
        }

    val shortAudioCodecName: String
        get() = when {
            !hasAudio || audioMimeType == null -> "无音轨"
            audioMimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            audioMimeType.contains("opus", ignoreCase = true) -> "Opus"
            audioMimeType.contains("ac3", ignoreCase = true) ||
                audioMimeType.contains("eac3", ignoreCase = true) -> "AC-3"
            audioMimeType.contains("mpeg", ignoreCase = true) -> "MP3"
            audioMimeType.contains("flac", ignoreCase = true) -> "FLAC"
            audioMimeType.contains("raw", ignoreCase = true) -> "PCM"
            else -> audioMimeType.substringAfter("/")
        }

    /**
     * Diagnoses potential compatibility and upload issues.
     */
    val compatibilityIssues: List<CompatibilityIssue>
        get() = buildList {
            if (!videoMimeType.contains("avc", ignoreCase = true)) {
                add(CompatibilityIssue.NON_H264_CODEC)
            }
            if (isHdr) {
                add(CompatibilityIssue.HDR_COLOR)
            }
            if (hasAudio && audioMimeType != null && !audioMimeType.contains("mp4a", ignoreCase = true)) {
                add(CompatibilityIssue.NON_AAC_AUDIO)
            }
            if (frameRate > 35f) {
                add(CompatibilityIssue.HIGH_FPS)
            }
            if (rotationDegrees != 0) {
                add(CompatibilityIssue.ROTATION_METADATA)
            }
            if (fileSizeMb > 25.0) {
                add(CompatibilityIssue.LARGE_FILE_SIZE)
            }
        }
}
