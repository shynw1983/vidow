package com.vidfit.converter.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private object ThumbnailMemoryCache {
    private val cache = object : LruCache<String, Bitmap>(32) {}

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}

/**
 * Lightweight thumbnail loader with BOTH in-memory LruCache AND persistent disk cache
 * so history thumbnails load instantaneously after restarting the app, even if remote CDN URLs expire.
 */
@Composable
fun MediaThumbnailBox(
    thumbnailUrl: String? = null,
    localVideoUri: Uri? = null,
    isAudioOnly: Boolean = false,
    showPlayBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheKey = thumbnailUrl ?: localVideoUri?.toString()
    var imageBitmap by remember(cacheKey) {
        mutableStateOf<ImageBitmap?>(cacheKey?.let { ThumbnailMemoryCache.get(it)?.asImageBitmap() })
    }

    LaunchedEffect(cacheKey, isAudioOnly) {
        if (imageBitmap != null || cacheKey == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            val diskFile = diskCacheFile(context, cacheKey)
            var bmp: Bitmap? = if (diskFile.exists()) {
                runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
            } else {
                null
            }

            if (bmp == null && !thumbnailUrl.isNullOrBlank()) {
                bmp = fetchRemoteBitmap(thumbnailUrl)
            }
            if (bmp == null && localVideoUri != null && !isAudioOnly) {
                bmp = loadLocalVideoFrame(context, localVideoUri)
            }
            if (bmp != null) {
                ThumbnailMemoryCache.put(cacheKey, bmp)
                if (!diskFile.exists()) {
                    saveBitmapToDisk(diskFile, bmp)
                }
            }
            bmp
        }
        if (loaded != null) {
            imageBitmap = loaded.asImageBitmap()
        }
    }

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = imageBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isAudioOnly) Icons.Rounded.Audiotrack else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (showPlayBadge && bmp != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun diskCacheFile(context: Context, key: String): File {
    val dir = File(context.filesDir, "thumb_cache").apply { mkdirs() }
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(24)
    return File(dir, "$hash.jpg")
}

private fun saveBitmapToDisk(file: File, bmp: Bitmap) {
    runCatching {
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
    }
}

private fun fetchRemoteBitmap(urlString: String): Bitmap? = runCatching {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        )
    }
    try {
        if (conn.responseCode !in 200..299) return@runCatching null
        val bytes = conn.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > 720) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } finally {
        conn.disconnect()
    }
}.getOrNull()

private fun loadLocalVideoFrame(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(480, 320), null)
        }.getOrNull()?.let { return@runCatching it }
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()
