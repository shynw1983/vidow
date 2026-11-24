package com.vidfit.converter.engine

import android.content.Context
import com.vidfit.converter.model.DownloadFormatMode
import com.vidfit.converter.model.ParsedLinkInfo
import com.vidfit.converter.model.ParsedQualityOption
import com.vidfit.converter.model.YtDlpUpdateState
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Wraps `youtubedl-android` (yt-dlp + FFmpeg) for YTDLnis-style URL parsing,
 * downloading (with H.264-priority compatible MP4 mode and optional cookies.txt),
 * cancellation, and Stable/Nightly core hot-updates.
 */
class YtDlpEngine(private val appContext: Context) {

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    private var activeProcessId: String? = null

    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (initialized) return@withContext Result.success(Unit)
            runCatching {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                initialized = true
            }
        }
    }

    suspend fun getCoreVersion(): String? = withContext(Dispatchers.IO) {
        ensureInitialized().getOrNull() ?: return@withContext null
        runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
    }

    suspend fun updateCore(useNightly: Boolean = false): YtDlpUpdateState =
        withContext(Dispatchers.IO) {
            val initResult = ensureInitialized()
            if (initResult.isFailure) {
                return@withContext YtDlpUpdateState.Failed(
                    "核心引擎初始化失败：${initResult.exceptionOrNull()?.localizedMessage ?: "未知错误"}"
                )
            }
            try {
                val channel = if (useNightly) {
                    YoutubeDL.UpdateChannel._NIGHTLY
                } else {
                    YoutubeDL.UpdateChannel._STABLE
                }
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, channel)
                val ver = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull().orEmpty()
                val label = if (useNightly) "Nightly $ver" else ver.ifBlank { "最新版" }
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateState.Updated(label.trim())
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateState.UpToDate(label.trim())
                    null -> YtDlpUpdateState.Failed("未获取到更新状态，请检查网络连接")
                }
            } catch (e: Exception) {
                YtDlpUpdateState.Failed("更新失败：${e.localizedMessage ?: "请检查网络或代理"}")
            }
        }

    suspend fun parseUrl(
        url: String,
        cookiesFile: File? = null
    ): Result<ParsedLinkInfo> = withContext(Dispatchers.IO) {
        ensureInitialized().onFailure { return@withContext Result.failure(it) }
        runCatching {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                if (cookiesFile != null && cookiesFile.exists()) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }
            }
            val info = YoutubeDL.getInstance().getInfo(request)
            val durationSec = info.duration.toLong().coerceAtLeast(0L)

            val rawThumbnail = info.thumbnail?.takeIf { it.isNotBlank() }
                ?: info.thumbnails?.lastOrNull { !it.url.isNullOrBlank() }?.url
            val safeThumbnail = rawThumbnail?.let {
                if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it
            }

            val allFormats = info.formats.orEmpty()

            // Find best standalone audio stream (used when pairing with DASH video-only streams)
            val audioOnlyFormats = allFormats.filter { fmt ->
                (fmt.vcodec == null || fmt.vcodec.equals("none", ignoreCase = true)) &&
                    !fmt.acodec.isNullOrBlank() &&
                    !fmt.acodec.equals("none", ignoreCase = true)
            }
            val bestM4aAudio = audioOnlyFormats
                .filter { it.ext.equals("m4a", ignoreCase = true) || it.acodec?.contains("mp4a", true) == true }
                .maxByOrNull { numberToInt(it.abr).takeIf { a -> a > 0 } ?: numberToInt(it.tbr) }
            val bestAnyAudio = audioOnlyFormats
                .maxByOrNull { numberToInt(it.abr).takeIf { a -> a > 0 } ?: numberToInt(it.tbr) }

            val audioForCompat = bestM4aAudio ?: bestAnyAudio
            val audioForBest = bestAnyAudio ?: bestM4aAudio

            val bestAudioCodec = normalizeAudioCodec(audioForBest?.acodec) ?: "AAC"
            val bestAudioExt = (audioForBest?.ext ?: "m4a").uppercase(Locale.US)
            val bestAudioAbr = audioForBest?.let {
                numberToInt(it.abr).takeIf { a -> a > 0 } ?: numberToInt(it.tbr)
            } ?: 0
            val bestAudioSize = audioForBest?.let { estimateFormatSize(it, durationSec) }

            val videoFormats = allFormats.filter { fmt ->
                fmt.height > 0 && (fmt.vcodec == null || !fmt.vcodec.equals("none", ignoreCase = true))
            }

            fun toQualityOption(
                fmt: VideoFormat,
                pairedAudio: VideoFormat?,
                forceMp4MergeLabel: Boolean
            ): ParsedQualityOption {
                val w = fmt.width.coerceAtLeast(0)
                val h = fmt.height
                val vCodec = normalizeVideoCodec(fmt.vcodec) ?: "未知"
                val hasEmbeddedAudio = !fmt.acodec.isNullOrBlank() && !fmt.acodec.equals("none", ignoreCase = true)
                val aCodec = if (hasEmbeddedAudio) {
                    normalizeAudioCodec(fmt.acodec)
                } else {
                    normalizeAudioCodec(pairedAudio?.acodec) ?: bestAudioCodec
                }
                val rawExt = (fmt.ext ?: info.ext ?: "mp4").uppercase(Locale.US)
                val displayExt = if (forceMp4MergeLabel) "MP4" else rawExt
                val fps = numberToInt(fmt.fps)
                val vBitrate = numberToInt(fmt.tbr)
                val videoBytes = estimateFormatSize(fmt, durationSec)
                val audioBytes = if (!hasEmbeddedAudio) pairedAudio?.let { estimateFormatSize(it, durationSec) } else null
                val totalBytes = when {
                    videoBytes != null && audioBytes != null -> videoBytes + audioBytes
                    videoBytes != null -> videoBytes
                    else -> null
                }

                return ParsedQualityOption(
                    ytDlpHeight = h,
                    width = w,
                    height = h,
                    label = formatQualityLabel(width = w, height = h),
                    ext = displayExt,
                    videoCodec = vCodec,
                    audioCodec = aCodec,
                    fps = fps,
                    bitrateKbps = vBitrate,
                    approxSizeBytes = totalBytes
                )
            }

            val groupedByHeight = videoFormats.groupBy { it.height }

            // 1. Compatible MP4 ranking: strongly prefer H.264 + MP4 per height
            val compatQualities = groupedByHeight.map { (_, list) ->
                val chosen = list.maxWithOrNull(
                    compareBy<VideoFormat>(
                        { if (normalizeVideoCodec(it.vcodec) == "H.264") 2 else if (it.ext.equals("mp4", true)) 1 else 0 },
                        { if (!it.acodec.isNullOrBlank() && !it.acodec.equals("none", true)) 1 else 0 },
                        { numberToInt(it.tbr) },
                        { maxOf(it.fileSize, it.fileSizeApproximate) }
                    )
                ) ?: list.first()
                toQualityOption(chosen, audioForCompat, forceMp4MergeLabel = true)
            }.sortedByDescending { it.ytDlpHeight }

            // 2. Highest original quality ranking: prefer highest bitrate/size regardless of codec
            val bestQualities = groupedByHeight.map { (_, list) ->
                val chosen = list.maxWithOrNull(
                    compareBy<VideoFormat>(
                        { numberToInt(it.tbr) },
                        { maxOf(it.fileSize, it.fileSizeApproximate) },
                        { numberToInt(it.fps) }
                    )
                ) ?: list.first()
                toQualityOption(chosen, audioForBest, forceMp4MergeLabel = false)
            }.sortedByDescending { it.ytDlpHeight }

            ParsedLinkInfo(
                url = url,
                title = info.title?.takeIf { it.isNotBlank() } ?: "未命名视频",
                uploader = info.uploader?.takeIf { it.isNotBlank() } ?: (info.extractor ?: "网络视频"),
                durationSeconds = durationSec,
                extractor = info.extractorKey ?: info.extractor ?: "Web",
                thumbnailUrl = safeThumbnail,
                defaultExt = (info.ext ?: "mp4").uppercase(Locale.US),
                defaultVideoCodec = compatQualities.firstOrNull()?.videoCodec
                    ?: bestQualities.firstOrNull()?.videoCodec
                    ?: "未知",
                bestAudioCodec = bestAudioCodec,
                bestAudioExt = bestAudioExt,
                bestAudioBitrateKbps = bestAudioAbr,
                bestAudioSizeBytes = bestAudioSize,
                compatQualities = compatQualities,
                bestQualities = bestQualities
            )
        }
    }

    suspend fun download(
        url: String,
        mode: DownloadFormatMode,
        selectedMaxHeight: Int?,
        embedSubtitles: Boolean,
        cookiesFile: File? = null,
        onProgress: (percent: Int, etaSeconds: Long, line: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        ensureInitialized().onFailure { return@withContext Result.failure(it) }

        val workDir = File(appContext.cacheDir, "ytdlp_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        val processId = "vidfit_dl_${UUID.randomUUID()}"
        activeProcessId = processId

        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                addOption("-o", File(workDir, "%(title).60s.%(ext)s").absolutePath)

                if (cookiesFile != null && cookiesFile.exists()) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }

                when (mode) {
                    DownloadFormatMode.AUDIO_M4A -> {
                        addOption("-x")
                        addOption("--audio-format", "m4a")
                        addOption("--audio-quality", "0")
                    }
                    DownloadFormatMode.VIDEO_MP4 -> {
                        val heightFilter = selectedMaxHeight?.let { "[height<=$it]" } ?: ""
                        addOption("-S", "vcodec:h264,res,acodec:m4a,ext:mp4:m4a")
                        addOption(
                            "-f",
                            "bestvideo${heightFilter}+bestaudio/best${heightFilter}/best"
                        )
                        addOption("--merge-output-format", "mp4")
                    }
                    DownloadFormatMode.VIDEO_BEST -> {
                        val heightFilter = selectedMaxHeight?.let { "[height<=$it]" } ?: ""
                        addOption(
                            "-f",
                            "bestvideo${heightFilter}+bestaudio/best${heightFilter}/best"
                        )
                    }
                }

                if (embedSubtitles && mode != DownloadFormatMode.AUDIO_M4A) {
                    addOption("--write-subs")
                    addOption("--sub-langs", "zh.*,en.*")
                    addOption("--embed-subs")
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val pct = progress.toInt().coerceIn(0, 100)
                onProgress(pct, etaInSeconds.coerceAtLeast(0L), line.orEmpty())
            }

            val outputFile = workDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0L }
                ?.maxByOrNull { it.length() }
                ?: error("下载完成但未找到输出媒体文件")

            Result.success(outputFile)
        } catch (e: Exception) {
            workDir.deleteRecursively()
            Result.failure(e)
        } finally {
            if (activeProcessId == processId) {
                activeProcessId = null
            }
        }
    }

    fun cancelActiveDownload(): Boolean {
        val pid = activeProcessId ?: return false
        activeProcessId = null
        return runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }.getOrDefault(false)
    }

    private fun estimateFormatSize(fmt: VideoFormat, durationSec: Long): Long? {
        val exact = maxOf(fmt.fileSize, fmt.fileSizeApproximate)
        if (exact > 0L) return exact
        val kbps = numberToInt(fmt.tbr).takeIf { it > 0 } ?: numberToInt(fmt.abr)
        if (kbps > 0 && durationSec > 0L) {
            return (kbps.toLong() * 1000L * durationSec) / 8L
        }
        return null
    }

    private fun numberToInt(value: Any?): Int = (value as? Number)?.toInt() ?: 0

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s\u4e00-\u9fa5"'<>]+""", RegexOption.IGNORE_CASE)

        fun extractFirstUrl(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            return URL_REGEX.find(rawText.trim())?.value?.trimEnd(')', ']', '.', ',', '，', '。')
        }

        fun formatQualityLabel(width: Int, height: Int): String {
            if (width <= 0) return "${height}p"
            val isPortrait = height > width
            val shortSide = minOf(width, height)
            val baseTag = when {
                shortSide >= 2100 -> "4K"
                shortSide in 1400..1500 -> "2K"
                else -> "${shortSide}p"
            }
            return if (isPortrait) "$baseTag 竖屏" else baseTag
        }

        fun normalizeVideoCodec(raw: String?): String? {
            val s = raw?.trim()?.lowercase(Locale.US) ?: return null
            if (s.isEmpty() || s == "none" || s == "unknown") return null
            return when {
                s.startsWith("avc") || s.contains("h264") || s.contains("264") -> "H.264"
                s.startsWith("hev") || s.startsWith("hvc") || s.contains("hevc") || s.contains("265") -> "H.265"
                s.startsWith("av01") || s == "av1" -> "AV1"
                s.startsWith("vp09") || s.startsWith("vp9") -> "VP9"
                s.startsWith("vp8") -> "VP8"
                else -> s.substringBefore('.').uppercase(Locale.US)
            }
        }

        fun normalizeAudioCodec(raw: String?): String? {
            val s = raw?.trim()?.lowercase(Locale.US) ?: return null
            if (s.isEmpty() || s == "none" || s == "unknown") return null
            return when {
                s.startsWith("mp4a") || s.contains("aac") -> "AAC"
                s.contains("opus") -> "Opus"
                s.contains("mp3") || s.contains("mp4a.6b") -> "MP3"
                s.contains("vorbis") -> "Vorbis"
                s.contains("flac") -> "FLAC"
                s.contains("ac-3") || s.contains("ac3") || s.contains("ec-3") -> "AC-3"
                else -> s.substringBefore('.').uppercase(Locale.US)
            }
        }
    }
}
