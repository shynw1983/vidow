package com.vidfit.converter.model

import android.net.Uri
import androidx.annotation.StringRes
import com.vidfit.converter.R

enum class AppTab(@StringRes val labelRes: Int) {
    DOWNLOAD(R.string.tab_download),
    CONVERT(R.string.tab_convert)
}

enum class DownloadFormatMode(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int
) {
    VIDEO_MP4(R.string.dl_mode_mp4_label, R.string.dl_mode_mp4_sub),
    VIDEO_BEST(R.string.dl_mode_best_label, R.string.dl_mode_best_sub),
    AUDIO_M4A(R.string.dl_mode_audio_label, R.string.dl_mode_audio_sub)
}

data class ParsedQualityOption(
    val ytDlpHeight: Int,
    val width: Int,
    val height: Int,
    /** Language-neutral base resolution tag (e.g. "1080p", "720p", "4K"). */
    val baseTag: String,
    val isPortrait: Boolean = false,
    val ext: String = "MP4",
    val videoCodec: String? = null,
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
    val defaultVideoCodec: String? = null,
    val bestAudioCodec: String = "AAC",
    val bestAudioExt: String = "M4A",
    val bestAudioBitrateKbps: Int = 0,
    val bestAudioSizeBytes: Long? = null,
    val compatQualities: List<ParsedQualityOption> = emptyList(),
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
