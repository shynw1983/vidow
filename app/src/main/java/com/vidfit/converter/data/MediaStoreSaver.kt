package com.vidfit.converter.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.vidfit.converter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedVideo(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String = "video/mp4"
)

/**
 * Exports converted or downloaded media files into system Gallery (Movies/VidFit)
 * or Music (Music/VidFit) using Scoped Storage (MediaStore IS_PENDING) on Android 10+.
 */
class MediaStoreSaver(private val context: Context) {

    fun buildOutputFileName(sourceDisplayName: String, suffix: String, ext: String = "mp4"): String {
        val base = sourceDisplayName
            .substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]"), "_")
            .take(40)
            .ifBlank { "video" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${base}_${suffix}_${stamp}.$ext"
    }

    suspend fun saveToGallery(tempFile: File, fileName: String): SavedVideo =
        saveMediaFile(tempFile = tempFile, fileName = fileName, isAudio = false)

    suspend fun saveDownloadedMedia(tempFile: File, preferredTitle: String, isAudio: Boolean): SavedVideo {
        val ext = tempFile.extension.lowercase(Locale.US).ifBlank { if (isAudio) "m4a" else "mp4" }
        val fileName = buildOutputFileName(preferredTitle, if (isAudio) "audio" else "dl", ext)
        return saveMediaFile(tempFile = tempFile, fileName = fileName, isAudio = isAudio)
    }

    private suspend fun saveMediaFile(
        tempFile: File,
        fileName: String,
        isAudio: Boolean
    ): SavedVideo = withContext(Dispatchers.IO) {
        val sizeBytes = tempFile.length()
        val ext = tempFile.extension.lowercase(Locale.US)
        val mimeType = when {
            isAudio && ext == "mp3" -> "audio/mpeg"
            isAudio && ext == "opus" -> "audio/opus"
            isAudio -> "audio/mp4"
            ext == "webm" -> "video/webm"
            ext == "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }

        val directory = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val collection = if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Vidow")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: error(context.getString(R.string.saver_err_create))

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: error(context.getString(R.string.saver_err_stream))

                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                SavedVideo(uri = uri, fileName = fileName, sizeBytes = sizeBytes, mimeType = mimeType)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(directory),
                "Vidow"
            ).apply { mkdirs() }
            val dest = if (targetDir.canWrite()) {
                File(targetDir, fileName)
            } else {
                val appDir = context.getExternalFilesDir(directory) ?: context.filesDir
                File(appDir, fileName)
            }
            tempFile.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(dest.absolutePath),
                arrayOf(mimeType),
                null
            )
            SavedVideo(uri = Uri.fromFile(dest), fileName = fileName, sizeBytes = sizeBytes, mimeType = mimeType)
        }
    }
}
