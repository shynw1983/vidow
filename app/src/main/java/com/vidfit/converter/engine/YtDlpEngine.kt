package com.vidfit.converter.engine

import android.content.Context
import com.vidfit.converter.R
import com.vidfit.converter.model.DownloadFormatMode
import com.vidfit.converter.model.ParsedLinkInfo
import com.vidfit.converter.model.ParsedQualityOption
import com.vidfit.converter.model.SubtitlePreset
import com.vidfit.converter.model.SubtitleTrackOption
import com.vidfit.converter.model.YtDlpUpdateState
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class SubtitleDownloadArgs(
    val subLangs: String,
    val writeAutoSubs: Boolean
)

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
                    appContext.getString(
                        R.string.dl_err_engine_init,
                        initResult.exceptionOrNull()?.localizedMessage ?: "Unknown"
                    )
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
                val label = if (useNightly) "Nightly $ver" else ver.ifBlank { "latest" }
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateState.Updated(label.trim())
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateState.UpToDate(label.trim())
                    null -> YtDlpUpdateState.Failed(appContext.getString(R.string.dl_err_update_null))
                }
            } catch (e: Exception) {
                YtDlpUpdateState.Failed(
                    appContext.getString(R.string.dl_err_update_failed, e.localizedMessage ?: "Network error")
                )
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
                addOption("--dump-json")
                if (cookiesFile != null && cookiesFile.exists()) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }
            }
            val response = YoutubeDL.getInstance().execute(request)
            val rawOut = response.out.orEmpty().trim()
            val jsonLine = rawOut.lineSequence().firstOrNull { it.trimStart().startsWith("{") } ?: rawOut
            val json = JSONObject(jsonLine)

            val durationSec = json.optDouble("duration", 0.0)
                .let { if (it.isNaN() || it.isInfinite()) 0L else it.toLong() }
                .coerceAtLeast(0L)

            val rawThumbnail = json.optNullableString("thumbnail")
                ?: run {
                    val thumbs = json.optJSONArray("thumbnails")
                    var picked: String? = null
                    if (thumbs != null) {
                        for (i in thumbs.length() - 1 downTo 0) {
                            val tUrl = thumbs.optJSONObject(i)?.optNullableString("url")
                            if (!tUrl.isNullOrBlank()) {
                                picked = tUrl
                                break
                            }
                        }
                    }
                    picked
                }
            val safeThumbnail = rawThumbnail?.let {
                if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it
            }

            val allFormats = buildList {
                val arr = json.optJSONArray("formats")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(RawFormat.fromJson(it)) }
                    }
                }
            }

            val audioOnlyFormats = allFormats.filter { fmt ->
                (fmt.vcodec == null || fmt.vcodec.equals("none", ignoreCase = true)) &&
                    !fmt.acodec.isNullOrBlank() &&
                    !fmt.acodec.equals("none", ignoreCase = true)
            }
            val bestM4aAudio = audioOnlyFormats
                .filter { it.ext.equals("m4a", ignoreCase = true) || it.acodec?.contains("mp4a", true) == true }
                .maxByOrNull { it.abr.takeIf { a -> a > 0 } ?: it.tbr }
            val bestAnyAudio = audioOnlyFormats
                .maxByOrNull { it.abr.takeIf { a -> a > 0 } ?: it.tbr }

            val audioForCompat = bestM4aAudio ?: bestAnyAudio
            val audioForBest = bestAnyAudio ?: bestM4aAudio

            val bestAudioCodec = normalizeAudioCodec(audioForBest?.acodec) ?: "AAC"
            val bestAudioExt = (audioForBest?.ext ?: "m4a").uppercase(Locale.US)
            val bestAudioAbr = audioForBest?.let {
                it.abr.takeIf { a -> a > 0 } ?: it.tbr
            } ?: 0
            val bestAudioSize = audioForBest?.let { estimateFormatSize(it, durationSec) }

            val videoFormats = allFormats.filter { fmt ->
                fmt.height > 0 && (fmt.vcodec == null || !fmt.vcodec.equals("none", ignoreCase = true))
            }

            val fallbackExt = json.optNullableString("ext") ?: "mp4"

            fun toQualityOption(
                fmt: RawFormat,
                pairedAudio: RawFormat?,
                forceMp4MergeLabel: Boolean
            ): ParsedQualityOption {
                val w = fmt.width.coerceAtLeast(0)
                val h = fmt.height
                val vCodec = normalizeVideoCodec(fmt.vcodec)
                val hasEmbeddedAudio = !fmt.acodec.isNullOrBlank() && !fmt.acodec.equals("none", ignoreCase = true)
                val aCodec = if (hasEmbeddedAudio) {
                    normalizeAudioCodec(fmt.acodec)
                } else {
                    normalizeAudioCodec(pairedAudio?.acodec) ?: bestAudioCodec
                }
                val rawExt = (fmt.ext ?: fallbackExt).uppercase(Locale.US)
                val displayExt = if (forceMp4MergeLabel) "MP4" else rawExt
                val fps = fmt.fps
                val vBitrate = fmt.tbr
                val videoBytes = estimateFormatSize(fmt, durationSec)
                val audioBytes = if (!hasEmbeddedAudio) pairedAudio?.let { estimateFormatSize(it, durationSec) } else null
                val totalBytes = when {
                    videoBytes != null && audioBytes != null -> videoBytes + audioBytes
                    videoBytes != null -> videoBytes
                    else -> null
                }

                val (baseTag, isPortrait) = computeResolutionTag(width = w, height = h)
                return ParsedQualityOption(
                    ytDlpHeight = h,
                    width = w,
                    height = h,
                    baseTag = baseTag,
                    isPortrait = isPortrait,
                    ext = displayExt,
                    videoCodec = vCodec,
                    audioCodec = aCodec,
                    fps = fps,
                    bitrateKbps = vBitrate,
                    approxSizeBytes = totalBytes
                )
            }

            val groupedByHeight = videoFormats.groupBy { it.height }

            val compatQualities = groupedByHeight.map { (_, list) ->
                val chosen = list.maxWithOrNull(
                    compareBy<RawFormat>(
                        { if (normalizeVideoCodec(it.vcodec) == "H.264") 2 else if (it.ext.equals("mp4", true)) 1 else 0 },
                        { if (!it.acodec.isNullOrBlank() && !it.acodec.equals("none", true)) 1 else 0 },
                        { it.tbr },
                        { maxOf(it.fileSize, it.fileSizeApproximate) }
                    )
                ) ?: list.first()
                toQualityOption(chosen, audioForCompat, forceMp4MergeLabel = true)
            }.sortedByDescending { it.ytDlpHeight }

            val bestQualities = groupedByHeight.map { (_, list) ->
                val chosen = list.maxWithOrNull(
                    compareBy<RawFormat>(
                        { it.tbr },
                        { maxOf(it.fileSize, it.fileSizeApproximate) },
                        { it.fps }
                    )
                ) ?: list.first()
                toQualityOption(chosen, audioForBest, forceMp4MergeLabel = false)
            }.sortedByDescending { it.ytDlpHeight }

            val (officialSubs, autoSubs) = parseSubtitleTracks(json, Locale.getDefault())

            val extractorKey = json.optNullableString("extractor_key")
            val extractor = json.optNullableString("extractor")

            ParsedLinkInfo(
                url = url,
                title = json.optNullableString("title") ?: appContext.getString(R.string.dl_unnamed_video),
                uploader = json.optNullableString("uploader") ?: extractor ?: "Web",
                durationSeconds = durationSec,
                extractor = extractorKey ?: extractor ?: "Web",
                thumbnailUrl = safeThumbnail,
                defaultExt = fallbackExt.uppercase(Locale.US),
                defaultVideoCodec = compatQualities.firstOrNull()?.videoCodec
                    ?: bestQualities.firstOrNull()?.videoCodec,
                bestAudioCodec = bestAudioCodec,
                bestAudioExt = bestAudioExt,
                bestAudioBitrateKbps = bestAudioAbr,
                bestAudioSizeBytes = bestAudioSize,
                compatQualities = compatQualities,
                bestQualities = bestQualities,
                officialSubtitles = officialSubs,
                autoSubtitles = autoSubs
            )
        }
    }

    suspend fun download(
        url: String,
        mode: DownloadFormatMode,
        selectedMaxHeight: Int?,
        embedSubtitles: Boolean,
        subtitlePreset: SubtitlePreset = SubtitlePreset.MULTI_4,
        selectedSubtitleLangs: Set<String> = emptySet(),
        includeAutoSubtitles: Boolean = false,
        hasOfficialSubtitles: Boolean = true,
        selectedIncludesAutoTrack: Boolean = false,
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
                    val subArgs = resolveSubtitleDownloadArgs(
                        preset = subtitlePreset,
                        selectedLangs = selectedSubtitleLangs,
                        systemLanguage = Locale.getDefault().language,
                        includeAutoSubtitles = includeAutoSubtitles,
                        hasOfficialSubtitles = hasOfficialSubtitles,
                        selectedIncludesAutoTrack = selectedIncludesAutoTrack
                    )
                    addOption("--write-subs")
                    if (subArgs.writeAutoSubs) {
                        addOption("--write-auto-subs")
                    }
                    addOption("--sub-langs", subArgs.subLangs)
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
                ?: error(appContext.getString(R.string.dl_err_no_output))

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

    private data class RawFormat(
        val vcodec: String?,
        val acodec: String?,
        val ext: String?,
        val width: Int,
        val height: Int,
        val fps: Int,
        val tbr: Int,
        val abr: Int,
        val fileSize: Long,
        val fileSizeApproximate: Long
    ) {
        companion object {
            fun fromJson(obj: JSONObject): RawFormat {
                fun optIntSafe(key: String): Int {
                    val v = obj.optDouble(key, 0.0)
                    return if (v.isNaN() || v.isInfinite()) 0 else v.toInt().coerceAtLeast(0)
                }
                return RawFormat(
                    vcodec = obj.optNullableString("vcodec"),
                    acodec = obj.optNullableString("acodec"),
                    ext = obj.optNullableString("ext"),
                    width = optIntSafe("width"),
                    height = optIntSafe("height"),
                    fps = optIntSafe("fps"),
                    tbr = optIntSafe("tbr"),
                    abr = optIntSafe("abr"),
                    fileSize = obj.optLong("filesize", 0L).coerceAtLeast(0L),
                    fileSizeApproximate = obj.optLong("filesize_approx", 0L).coerceAtLeast(0L)
                )
            }
        }
    }

    private fun estimateFormatSize(fmt: RawFormat, durationSec: Long): Long? {
        val exact = maxOf(fmt.fileSize, fmt.fileSizeApproximate)
        if (exact > 0L) return exact
        val kbps = fmt.tbr.takeIf { it > 0 } ?: fmt.abr
        if (kbps > 0 && durationSec > 0L) {
            return (kbps.toLong() * 1000L * durationSec) / 8L
        }
        return null
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s\u4e00-\u9fa5"'<>]+""", RegexOption.IGNORE_CASE)
        private val IGNORED_SUBTITLE_KEYS = setOf("live_chat", "danmaku")
        private val PRIORITY_SUB_LANGS = listOf("zh", "en", "ja", "ko")

        private fun JSONObject.optNullableString(key: String): String? {
            if (isNull(key)) return null
            return optString(key, "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

        fun resolveSubtitleDownloadArgs(
            preset: SubtitlePreset,
            selectedLangs: Set<String>,
            systemLanguage: String,
            includeAutoSubtitles: Boolean,
            hasOfficialSubtitles: Boolean = true,
            selectedIncludesAutoTrack: Boolean = false
        ): SubtitleDownloadArgs {
            val sysBase = systemLanguage.trim().lowercase(Locale.US)
                .substringBefore('-')
                .substringBefore('_')
                .ifBlank { "en" }
            return when (preset) {
                SubtitlePreset.MULTI_4 -> SubtitleDownloadArgs(
                    subLangs = "zh.*,en.*,ja.*,ko.*",
                    writeAutoSubs = includeAutoSubtitles
                )
                SubtitlePreset.SYSTEM_LANG -> SubtitleDownloadArgs(
                    subLangs = "$sysBase.*",
                    writeAutoSubs = includeAutoSubtitles
                )
                SubtitlePreset.ALL_OFFICIAL -> {
                    // Never pair `all` with --write-auto-subs (YouTube has 100+ auto-translated tracks
                    // that quickly trigger HTTP 429 rate-limits).
                    if (includeAutoSubtitles && !hasOfficialSubtitles) {
                        SubtitleDownloadArgs(
                            subLangs = "zh.*,en.*,ja.*,ko.*",
                            writeAutoSubs = true
                        )
                    } else {
                        SubtitleDownloadArgs(
                            subLangs = "all,-live_chat,-danmaku",
                            writeAutoSubs = false
                        )
                    }
                }
                SubtitlePreset.CUSTOM -> {
                    val cleaned = selectedLangs.map { it.trim() }.filter { it.isNotBlank() }
                    if (cleaned.isEmpty()) {
                        SubtitleDownloadArgs(
                            subLangs = "zh.*,en.*,ja.*,ko.*",
                            writeAutoSubs = includeAutoSubtitles
                        )
                    } else {
                        SubtitleDownloadArgs(
                            subLangs = cleaned.joinToString(","),
                            writeAutoSubs = includeAutoSubtitles || selectedIncludesAutoTrack
                        )
                    }
                }
            }
        }

        fun parseSubtitleTracks(
            json: JSONObject,
            displayLocale: Locale = Locale.getDefault()
        ): Pair<List<SubtitleTrackOption>, List<SubtitleTrackOption>> {
            val officialMap = extractSubtitleMap(json.optJSONObject("subtitles"), isAuto = false, displayLocale = displayLocale)
            val autoMap = extractSubtitleMap(json.optJSONObject("automatic_captions"), isAuto = true, displayLocale = displayLocale)
                .filterKeys { it !in officialMap.keys }

            val sysLang = displayLocale.language.lowercase(Locale.US)
            val comparator = compareBy<SubtitleTrackOption>(
                { trackSortRank(it.langCode, sysLang) },
                { it.langCode.lowercase(Locale.US) }
            )
            return officialMap.values.sortedWith(comparator) to autoMap.values.sortedWith(comparator)
        }

        private fun extractSubtitleMap(
            obj: JSONObject?,
            isAuto: Boolean,
            displayLocale: Locale
        ): Map<String, SubtitleTrackOption> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, SubtitleTrackOption>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next() ?: continue
                val keyLower = rawKey.lowercase(Locale.US)
                if (keyLower in IGNORED_SUBTITLE_KEYS) continue
                val tracks = obj.optJSONArray(rawKey) ?: continue
                if (tracks.length() == 0) continue

                var remoteName: String? = null
                for (i in 0 until tracks.length()) {
                    val name = tracks.optJSONObject(i)?.optNullableString("name")
                    if (!name.isNullOrBlank()) {
                        remoteName = name
                        break
                    }
                }
                val label = remoteName ?: formatLanguageCodeLabel(rawKey, displayLocale)
                out[rawKey] = SubtitleTrackOption(
                    langCode = rawKey,
                    displayName = label,
                    isAutoGenerated = isAuto
                )
            }
            return out
        }

        private fun trackSortRank(langCode: String, systemLang: String): Int {
            val lower = langCode.lowercase(Locale.US)
            val base = lower.substringBefore('-').substringBefore('_')
            return when {
                lower.endsWith("-orig") -> 0
                systemLang.isNotBlank() && base == systemLang -> 1
                base in PRIORITY_SUB_LANGS -> 2 + PRIORITY_SUB_LANGS.indexOf(base)
                else -> 10
            }
        }

        fun formatLanguageCodeLabel(langCode: String, displayLocale: Locale = Locale.getDefault()): String {
            val clean = langCode.removeSuffix("-orig").replace('_', '-')
            val parsed = Locale.forLanguageTag(clean)
            val localized = parsed.getDisplayName(displayLocale)
            return if (!localized.isNullOrBlank() && !localized.equals(clean, ignoreCase = true)) {
                localized
            } else {
                langCode
            }
        }

        fun extractFirstUrl(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            return URL_REGEX.find(rawText.trim())?.value?.trimEnd(')', ']', '.', ',', '，', '。')
        }

        fun computeResolutionTag(width: Int, height: Int): Pair<String, Boolean> {
            if (width <= 0) return "${height}p" to false
            val isPortrait = height > width
            val shortSide = minOf(width, height)
            val baseTag = when {
                shortSide >= 2100 -> "4K"
                shortSide in 1400..1500 -> "2K"
                else -> "${shortSide}p"
            }
            return baseTag to isPortrait
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
