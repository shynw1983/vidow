package com.vidfit.converter.model

import android.net.Uri
import androidx.annotation.StringRes
import com.vidfit.converter.R

/**
 * Compatibility diagnostic warning discovered while inspecting the source video.
 */
enum class CompatibilityIssue(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int
) {
    NON_H264_CODEC(R.string.issue_non_h264_title, R.string.issue_non_h264_detail),
    HDR_COLOR(R.string.issue_hdr_title, R.string.issue_hdr_detail),
    NON_AAC_AUDIO(R.string.issue_non_aac_title, R.string.issue_non_aac_detail),
    HIGH_FPS(R.string.issue_high_fps_title, R.string.issue_high_fps_detail),
    ROTATION_METADATA(R.string.issue_rotation_title, R.string.issue_rotation_detail),
    LARGE_FILE_SIZE(R.string.issue_large_size_title, R.string.issue_large_size_detail)
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
    val rawWidth: Int,
    val rawHeight: Int,
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

    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight else rawWidth

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
            videoMimeType.isBlank() -> "—"
            else -> videoMimeType.substringAfter("/")
        }

    val shortAudioCodecName: String
        get() = when {
            !hasAudio || audioMimeType == null -> "—"
            audioMimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            audioMimeType.contains("opus", ignoreCase = true) -> "Opus"
            audioMimeType.contains("ac3", ignoreCase = true) ||
                audioMimeType.contains("eac3", ignoreCase = true) -> "AC-3"
            audioMimeType.contains("mpeg", ignoreCase = true) -> "MP3"
            audioMimeType.contains("flac", ignoreCase = true) -> "FLAC"
            audioMimeType.contains("raw", ignoreCase = true) -> "PCM"
            else -> audioMimeType.substringAfter("/")
        }

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
