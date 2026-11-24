package com.vidfit.converter.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.vidfit.converter.R
import com.vidfit.converter.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inspects local video content:// Uris using MediaExtractor + MediaMetadataRetriever
 * without copying the file to app cache.
 */
class VideoInspector(private val context: Context) {

    suspend fun inspect(uri: Uri): Result<VideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val (displayName, queriedSize) = queryNameAndSize(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

            var durationMs = 0L
            var rawWidth = 0
            var rawHeight = 0
            var rotation = 0
            var bitrate = 0
            var hasLocation = false
            var colorTransfer = MediaFormat.COLOR_TRANSFER_SDR_VIDEO
            var frameRate = 0f

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull() ?: 0
                hasLocation = !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    .isNullOrBlank()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                        ?.toIntOrNull() ?: MediaFormat.COLOR_TRANSFER_SDR_VIDEO
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.toFloatOrNull()
                    if (frameCount != null && durationMs > 0L) {
                        frameRate = frameCount / (durationMs / 1000f)
                    }
                }
            } finally {
                runCatching { retriever.release() }
            }

            var videoMime = ""
            var audioMime: String? = null
            var hasAudio = false

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (trackMime.startsWith("video/") && videoMime.isEmpty()) {
                        videoMime = trackMime
                        if (rawWidth == 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                            rawWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (rawHeight == 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            rawHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            val fmtFps = runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                                .getOrElse { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            if (fmtFps > 0f) frameRate = fmtFps
                        }
                        if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                            colorTransfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        }
                        if (durationMs == 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                        }
                    } else if (trackMime.startsWith("audio/") && !hasAudio) {
                        hasAudio = true
                        audioMime = trackMime
                    }
                }
            } finally {
                runCatching { extractor.release() }
            }

            val finalSize = if (queriedSize > 0L) {
                queriedSize
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    ?.takeIf { it > 0L } ?: 0L
            }

            if (bitrate <= 0 && finalSize > 0L && durationMs > 0L) {
                bitrate = ((finalSize * 8.0) / (durationMs / 1000.0)).toInt()
            }

            val isHdr = colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                colorTransfer == MediaFormat.COLOR_TRANSFER_HLG
            val colorLabel = when (colorTransfer) {
                MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ"
                MediaFormat.COLOR_TRANSFER_HLG -> "HLG HDR"
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR (Rec.709)"
                else -> "SDR"
            }

            require(rawWidth > 0 && rawHeight > 0 && durationMs > 0L) {
                context.getString(R.string.inspect_error_corrupt)
            }

            VideoInfo(
                uriString = uri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                fileSizeBytes = finalSize,
                durationMs = durationMs,
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                rotationDegrees = rotation,
                videoMimeType = videoMime,
                audioMimeType = audioMime,
                videoBitrateBps = bitrate,
                frameRate = if (frameRate > 0f) frameRate else 30f,
                isHdr = isHdr,
                colorTransferLabel = colorLabel,
                hasAudio = hasAudio,
                hasLocationMetadata = hasLocation
            )
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "video.mp4"
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    name = cursor.getString(nameIdx)
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
