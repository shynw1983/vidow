package com.vidfit.converter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.vidfit.converter.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VidFitApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var ytDlpEngine: YtDlpEngine
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        ytDlpEngine = YtDlpEngine(applicationContext)
        // Warm up yt-dlp + FFmpeg runtime in the background so first URL parse is instant
        appScope.launch {
            ytDlpEngine.ensureInitialized()
        }
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_CONVERSION,
            getString(R.string.channel_conversion_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_conversion_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_CONVERSION = "vidfit_conversion_channel"
    }
}
