package com.vidfit.converter.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vidfit.converter.MainActivity
import com.vidfit.converter.R
import com.vidfit.converter.VidFitApp
import com.vidfit.converter.data.MediaStoreSaver
import com.vidfit.converter.domain.EncodingPlan
import com.vidfit.converter.engine.VideoConverterEngine
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.ConversionResult
import com.vidfit.converter.model.ConversionState
import com.vidfit.converter.model.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground Service keeping video conversion alive when the app moves to background
 * or the screen locks, with a live progress notification and Cancel action.
 */
class ConversionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: VideoConverterEngine
    private lateinit var saver: MediaStoreSaver
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTempFile: File? = null
    private var startRealtimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        engine = VideoConverterEngine(applicationContext)
        saver = MediaStoreSaver(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                handleCancel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val job = ConversionRepository.pendingJob
                if (job == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification(0, getString(R.string.conv_preparing))
                runConversion(job.info, job.config)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runConversion(info: VideoInfo, config: ConversionConfig) {
        acquireWakeLock()
        startRealtimeMs = SystemClock.elapsedRealtime()
        val tempDir = File(cacheDir, "conversions").apply { mkdirs() }
        val tempFile = File(tempDir, "vidfit_${System.currentTimeMillis()}.mp4")
        currentTempFile = tempFile

        val codecLabel = getString(config.videoCodec.labelRes)
        ConversionRepository.updateState(
            ConversionState.Converting(
                progressPercent = 0,
                statusText = getString(R.string.conv_running_codec, codecLabel),
                elapsedMs = 0L
            )
        )

        engine.start(
            info = info,
            config = config,
            outputFile = tempFile,
            callbacks = object : VideoConverterEngine.Callbacks {
                override fun onProgress(percent: Int) {
                    val elapsed = SystemClock.elapsedRealtime() - startRealtimeMs
                    val status = getString(R.string.conv_progress_pct, percent)
                    ConversionRepository.updateState(
                        ConversionState.Converting(
                            progressPercent = percent,
                            statusText = status,
                            elapsedMs = elapsed
                        )
                    )
                    updateNotification(percent, status)
                }

                override fun onCompleted(outputFile: File, plan: EncodingPlan) {
                    serviceScope.launch {
                        try {
                            updateNotification(99, getString(R.string.conv_saving_gallery))
                            ConversionRepository.updateState(
                                ConversionState.Converting(
                                    progressPercent = 99,
                                    statusText = getString(R.string.conv_saving_short),
                                    elapsedMs = SystemClock.elapsedRealtime() - startRealtimeMs
                                )
                            )
                            val suffix = "${plan.outputHeight}p_${config.videoCodec.name.lowercase()}"
                            val fileName = saver.buildOutputFileName(info.displayName, suffix)
                            val saved = saver.saveToGallery(outputFile, fileName)
                            val elapsed = SystemClock.elapsedRealtime() - startRealtimeMs

                            ConversionRepository.updateState(
                                ConversionState.Success(
                                    ConversionResult(
                                        outputUri = saved.uri,
                                        outputFileName = saved.fileName,
                                        originalSizeBytes = info.fileSizeBytes,
                                        outputSizeBytes = saved.sizeBytes,
                                        outputWidth = plan.outputWidth,
                                        outputHeight = plan.outputHeight,
                                        elapsedMs = elapsed,
                                        codecLabel = codecLabel,
                                        convertedHdrToSdr = info.isHdr && config.toneMapHdrToSdr
                                    )
                                )
                            )
                        } catch (e: Exception) {
                            ConversionRepository.updateState(
                                ConversionState.Failed(
                                    getString(R.string.conv_err_save, e.localizedMessage ?: "Unknown")
                                )
                            )
                        } finally {
                            cleanupTempFile()
                            finishService()
                        }
                    }
                }

                override fun onError(message: String) {
                    cleanupTempFile()
                    ConversionRepository.updateState(ConversionState.Failed(message))
                    finishService()
                }
            }
        )
    }

    private fun handleCancel() {
        engine.cancel()
        cleanupTempFile()
        ConversionRepository.updateState(ConversionState.Cancelled)
        finishService()
    }

    private fun startForegroundWithNotification(progress: Int, statusText: String) {
        val notification = buildNotification(progress, statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (Build.VERSION.SDK_INT >= 35) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Int, statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(progress, statusText))
    }

    private fun buildNotification(progress: Int, statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ConversionService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VidFitApp.CHANNEL_CONVERSION)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_cancel), cancelPending)
            .build()
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VidFit::ConversionWakeLock")
            ?.apply { acquire(30 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun cleanupTempFile() {
        currentTempFile?.let { runCatching { if (it.exists()) it.delete() } }
        currentTempFile = null
    }

    private fun finishService() {
        releaseWakeLock()
        ConversionRepository.pendingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine.cancel()
        cleanupTempFile()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.vidfit.converter.action.START"
        const val ACTION_CANCEL = "com.vidfit.converter.action.CANCEL"

        fun startConversion(context: Context, info: VideoInfo, config: ConversionConfig) {
            ConversionRepository.pendingJob = PendingConversionJob(info, config)
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelConversion(context: Context) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
