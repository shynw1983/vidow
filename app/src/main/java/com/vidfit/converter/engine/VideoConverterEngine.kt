package com.vidfit.converter.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4LocationData
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMuxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.vidfit.converter.R
import com.vidfit.converter.domain.BitrateCalculator
import com.vidfit.converter.domain.EncodingPlan
import com.vidfit.converter.model.AspectFitMode
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.VideoInfo
import java.io.File

/**
 * Wraps Jetpack Media3 Transformer for hardware-accelerated video transcoding:
 * - H.264 / H.265 video + AAC audio
 * - HDR -> SDR (Rec.709) OpenGL tone-mapping
 * - 16-pixel aligned scaling & aspect ratio crop/letterbox
 * - Frame rate capping via FrameDropEffect
 * - Trimming, audio removal, GPS metadata stripping
 */
@OptIn(UnstableApi::class)
class VideoConverterEngine(private val context: Context) {

    interface Callbacks {
        fun onProgress(percent: Int)
        fun onCompleted(outputFile: File, plan: EncodingPlan)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun start(
        info: VideoInfo,
        config: ConversionConfig,
        outputFile: File,
        callbacks: Callbacks
    ): EncodingPlan {
        cancel()

        val plan = BitrateCalculator.buildPlan(info, config)

        val mediaItemBuilder = MediaItem.Builder().setUri(info.uri)
        val hasTrim = config.trimStartMs > 0L ||
            (config.trimEndMs != null && config.trimEndMs < info.durationMs)
        if (hasTrim) {
            val startMs = config.trimStartMs.coerceIn(0L, info.durationMs)
            val endMs = (config.trimEndMs ?: info.durationMs).coerceIn(startMs + 100L, info.durationMs)
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
        }

        val videoEffects = mutableListOf<Effect>()

        val layoutMode = when (config.aspectFitMode) {
            AspectFitMode.CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            AspectFitMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
        }
        // Always apply Presentation with 16px-aligned dimensions; also guarantees rotation is baked into pixels.
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                plan.outputWidth,
                plan.outputHeight,
                layoutMode
            )
        )

        if (plan.targetFps != null) {
            videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(plan.targetFps))
        }

        val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(config.removeAudio || !info.hasAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val hdrMode = if (config.toneMapHdrToSdr) {
            Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
        } else {
            Composition.HDR_MODE_KEEP_HDR
        }

        val composition = Composition.Builder(EditedMediaItemSequence.Builder(editedItem).build())
            .setHdrMode(hdrMode)
            .build()

        val targetVideoMime = when (config.videoCodec) {
            OutputVideoCodec.H264 -> MimeTypes.VIDEO_H264
            OutputVideoCodec.H265 -> MimeTypes.VIDEO_H265
        }

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(plan.videoBitrateBps)
                    .build()
            )
            .setEnableFallback(true)
            .build()

        val muxerFactoryBuilder = InAppMuxer.Factory.Builder()
        if (config.stripLocationMetadata) {
            muxerFactoryBuilder.setMetadataProvider { metadataEntries ->
                metadataEntries.removeAll { it is Mp4LocationData }
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(targetVideoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(muxerFactoryBuilder.build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    activeTransformer = null
                    callbacks.onProgress(100)
                    callbacks.onCompleted(outputFile, plan)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    activeTransformer = null
                    val readable = buildReadableError(exportException)
                    callbacks.onError(readable)
                }
            })
            .build()

        activeTransformer = transformer

        mainHandler.post {
            try {
                if (outputFile.exists()) outputFile.delete()
                transformer.start(composition, outputFile.absolutePath)
                startProgressPolling(transformer, callbacks)
            } catch (e: Exception) {
                stopProgressPolling()
                activeTransformer = null
                callbacks.onError(
                    context.getString(
                        R.string.conv_err_start,
                        e.localizedMessage ?: e.javaClass.simpleName
                    )
                )
            }
        }

        return plan
    }

    fun cancel() {
        stopProgressPolling()
        val current = activeTransformer
        activeTransformer = null
        if (current != null) {
            mainHandler.post {
                runCatching { current.cancel() }
            }
        }
    }

    private fun startProgressPolling(transformer: Transformer, callbacks: Callbacks) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                if (activeTransformer !== transformer) return
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    callbacks.onProgress(holder.progress.coerceIn(0, 99))
                }
                mainHandler.postDelayed(this, 250L)
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 250L)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun buildReadableError(e: ExportException): String {
        return when (e.errorCode) {
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                context.getString(R.string.conv_err_decoder)
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                context.getString(R.string.conv_err_encoder)
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ExportException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                context.getString(R.string.conv_err_io)
            else -> context.getString(
                R.string.conv_err_generic,
                e.errorCode,
                e.cause?.localizedMessage ?: e.localizedMessage ?: "Unknown"
            )
        }
    }
}
