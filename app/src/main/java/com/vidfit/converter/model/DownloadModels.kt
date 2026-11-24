package com.vidfit.converter.model

import android.net.Uri

enum class AppTab(val label: String) {
    DOWNLOAD("视频下载"),
    CONVERT("格式转换")
}

enum class DownloadFormatMode(
    val label: String,
    val subtitle: String
) {
    VIDEO_MP4("兼容视频 MP4", "优先直下原生 H.264 + AAC 的 MP4，下完即播免转码"),
    VIDEO_BEST("最高原画", "不限编码（VP9 / AV1 / 4K HDR），追求极限画质存档"),
    AUDIO_M4A("仅提取音频", "提取高质量 M4A 音频保存到音乐目录")
}

/**
 * A concrete video quality tier actually offered by the parsed source video,
 * enriched with format container, video/audio codec, fps, bitrate, and estimated size.
 */
data class ParsedQualityOption(
    val ytDlpHeight: Int,
    val width: Int,
    val height: Int,
    val label: String,
    val ext: String = "MP4",
    val videoCodec: String = "未知",
    val audioCodec: String? = null,
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val approxSizeBytes: Long? = null
)

data class ParsedLinkInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val extractor: String,
    val thumbnailUrl: String?,
    val defaultExt: String = "MP4",
    val defaultVideoCodec: String = "未知",
    val bestAudioCodec: String = "AAC",
    val bestAudioExt: String = "M4A",
    val bestAudioBitrateKbps: Int = 0,
    val bestAudioSizeBytes: Long? = null,
    /** Quality options ranked with H.264 / MP4 priority (matches VIDEO_MP4 mode). */
    val compatQualities: List<ParsedQualityOption> = emptyList(),
    /** Quality options ranked purely by highest bitrate/quality (matches VIDEO_BEST mode). */
    val bestQualities: List<ParsedQualityOption> = emptyList()
) {
    fun qualitiesFor(mode: DownloadFormatMode): List<ParsedQualityOption> = when (mode) {
        DownloadFormatMode.VIDEO_MP4 -> compatQualities
        DownloadFormatMode.VIDEO_BEST -> bestQualities
        DownloadFormatMode.AUDIO_M4A -> emptyList()
    }

    fun activeQuality(mode: DownloadFormatMode, selectedMaxHeight: Int?): ParsedQualityOption? {
        val list = qualitiesFor(mode)
        if (list.isEmpty()) return null
        return if (selectedMaxHeight == null) {
            list.first()
        } else {
            list.firstOrNull { it.ytDlpHeight == selectedMaxHeight }
                ?: list.firstOrNull { it.ytDlpHeight <= selectedMaxHeight }
                ?: list.first()
        }
    }
}

data class DownloadResult(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isAudioOnly: Boolean,
    val thumbnailUrl: String? = null
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Parsing : DownloadState
    data class Downloading(
        val progressPercent: Int,
        val etaSeconds: Long,
        val statusLine: String
    ) : DownloadState
    data object Saving : DownloadState
    data class Success(val result: DownloadResult) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Cancelled : DownloadState
}

sealed interface YtDlpUpdateState {
    data object Idle : YtDlpUpdateState
    data object Updating : YtDlpUpdateState
    data class UpToDate(val version: String) : YtDlpUpdateState
    data class Updated(val version: String) : YtDlpUpdateState
    data class Failed(val message: String) : YtDlpUpdateState
}
