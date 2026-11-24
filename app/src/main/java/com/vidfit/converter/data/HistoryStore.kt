package com.vidfit.converter.data

import android.content.Context
import android.net.Uri
import com.vidfit.converter.model.ConversionResult
import com.vidfit.converter.model.DownloadFormatMode
import com.vidfit.converter.model.DownloadResult
import com.vidfit.converter.model.SubtitlePreset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedDownloadPrefs(
    val formatMode: DownloadFormatMode = DownloadFormatMode.VIDEO_MP4,
    val embedSubtitles: Boolean = false,
    val subtitlePreset: SubtitlePreset = SubtitlePreset.MULTI_4,
    val includeAutoSubtitles: Boolean = false,
    val autoConvertAfterDownload: Boolean = false,
    val useNightlyChannel: Boolean = false
)

/**
 * Lightweight local JSON persistence for download history, conversion history,
 * and download preferences inside app-private storage.
 */
class HistoryStore(private val context: Context) {

    private val storeDir: File
        get() = File(context.filesDir, "history").apply { mkdirs() }

    private val downloadsFile: File get() = File(storeDir, "downloads.json")
    private val conversionsFile: File get() = File(storeDir, "conversions.json")
    private val prefsFile: File get() = File(storeDir, "download_prefs.json")

    fun loadDownloads(): List<DownloadResult> = runCatching {
        if (!downloadsFile.exists()) return emptyList()
        decodeDownloadsJson(downloadsFile.readText())
    }.getOrDefault(emptyList())

    fun saveDownloads(items: List<DownloadResult>) {
        runCatching {
            val trimmed = items.take(MAX_HISTORY_ITEMS)
            writeAtomic(downloadsFile, encodeDownloadsJson(trimmed))
        }
    }

    fun loadConversions(): List<ConversionResult> = runCatching {
        if (!conversionsFile.exists()) return emptyList()
        decodeConversionsJson(conversionsFile.readText())
    }.getOrDefault(emptyList())

    fun saveConversions(items: List<ConversionResult>) {
        runCatching {
            val trimmed = items.take(MAX_HISTORY_ITEMS)
            writeAtomic(conversionsFile, encodeConversionsJson(trimmed))
        }
    }

    fun loadPrefs(): SavedDownloadPrefs = runCatching {
        if (!prefsFile.exists()) return SavedDownloadPrefs()
        val obj = JSONObject(prefsFile.readText())
        val modeName = obj.optString("formatMode", DownloadFormatMode.VIDEO_MP4.name)
        val mode = DownloadFormatMode.entries.firstOrNull { it.name == modeName }
            ?: DownloadFormatMode.VIDEO_MP4
        val presetName = obj.optString("subtitlePreset", SubtitlePreset.MULTI_4.name)
        val preset = SubtitlePreset.entries.firstOrNull { it.name == presetName }
            ?: SubtitlePreset.MULTI_4
        SavedDownloadPrefs(
            formatMode = mode,
            embedSubtitles = obj.optBoolean("embedSubtitles", false),
            subtitlePreset = if (preset == SubtitlePreset.CUSTOM) SubtitlePreset.MULTI_4 else preset,
            includeAutoSubtitles = obj.optBoolean("includeAutoSubtitles", false),
            autoConvertAfterDownload = obj.optBoolean("autoConvertAfterDownload", false),
            useNightlyChannel = obj.optBoolean("useNightlyChannel", false)
        )
    }.getOrDefault(SavedDownloadPrefs())

    fun savePrefs(prefs: SavedDownloadPrefs) {
        runCatching {
            val obj = JSONObject().apply {
                put("formatMode", prefs.formatMode.name)
                put("embedSubtitles", prefs.embedSubtitles)
                put("subtitlePreset", prefs.subtitlePreset.name)
                put("includeAutoSubtitles", prefs.includeAutoSubtitles)
                put("autoConvertAfterDownload", prefs.autoConvertAfterDownload)
                put("useNightlyChannel", prefs.useNightlyChannel)
            }
            writeAtomic(prefsFile, obj.toString())
        }
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            runCatching { tmp.delete() }
        }
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 50

        fun encodeDownloadsJson(items: List<DownloadResult>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("uri", item.uri.toString())
                        put("fileName", item.fileName)
                        put("title", item.title)
                        put("mimeType", item.mimeType)
                        put("sizeBytes", item.sizeBytes)
                        put("isAudioOnly", item.isAudioOnly)
                        put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeDownloadsJson(raw: String): List<DownloadResult> {
            if (raw.isBlank()) return emptyList()
            val arr = JSONArray(raw)
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uriStr = obj.optString("uri")
                    if (uriStr.isBlank()) continue
                    val thumb = if (obj.isNull("thumbnailUrl")) null else obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    add(
                        DownloadResult(
                            uri = Uri.parse(uriStr),
                            fileName = obj.optString("fileName", "video.mp4"),
                            title = obj.optString("title", "未命名视频"),
                            mimeType = obj.optString("mimeType", "video/mp4"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            isAudioOnly = obj.optBoolean("isAudioOnly", false),
                            thumbnailUrl = thumb
                        )
                    )
                }
            }
        }

        fun encodeConversionsJson(items: List<ConversionResult>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("outputUri", item.outputUri.toString())
                        put("outputFileName", item.outputFileName)
                        put("originalSizeBytes", item.originalSizeBytes)
                        put("outputSizeBytes", item.outputSizeBytes)
                        put("outputWidth", item.outputWidth)
                        put("outputHeight", item.outputHeight)
                        put("elapsedMs", item.elapsedMs)
                        put("codecLabel", item.codecLabel)
                        put("convertedHdrToSdr", item.convertedHdrToSdr)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeConversionsJson(raw: String): List<ConversionResult> {
            if (raw.isBlank()) return emptyList()
            val arr = JSONArray(raw)
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uriStr = obj.optString("outputUri")
                    if (uriStr.isBlank()) continue
                    add(
                        ConversionResult(
                            outputUri = Uri.parse(uriStr),
                            outputFileName = obj.optString("outputFileName", "video.mp4"),
                            originalSizeBytes = obj.optLong("originalSizeBytes", 0L),
                            outputSizeBytes = obj.optLong("outputSizeBytes", 0L),
                            outputWidth = obj.optInt("outputWidth", 0),
                            outputHeight = obj.optInt("outputHeight", 0),
                            elapsedMs = obj.optLong("elapsedMs", 0L),
                            codecLabel = obj.optString("codecLabel", "H.264"),
                            convertedHdrToSdr = obj.optBoolean("convertedHdrToSdr", false)
                        )
                    )
                }
            }
        }
    }
}
