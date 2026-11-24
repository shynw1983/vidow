package com.vidfit.converter

import com.vidfit.converter.data.HistoryStore
import com.vidfit.converter.data.SiteCookieStore
import com.vidfit.converter.domain.BitrateCalculator
import com.vidfit.converter.domain.EncodingNotice
import com.vidfit.converter.engine.YtDlpEngine
import com.vidfit.converter.model.AspectRatioOption
import com.vidfit.converter.model.CompatibilityIssue
import com.vidfit.converter.model.CompressionMode
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.ConversionPreset
import com.vidfit.converter.model.FrameRateCap
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.ResolutionCap
import com.vidfit.converter.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitrateCalculatorTest {

    private fun sampleVideo(
        rawWidth: Int = 3840,
        rawHeight: Int = 2160,
        rotation: Int = 90,
        durationMs: Long = 120_000L,
        fileSizeMb: Double = 180.0,
        videoBitrateBps: Int = 12_000_000,
        fps: Float = 60f,
        videoMime: String = "video/hevc",
        audioMime: String? = "audio/mp4a-latm",
        isHdr: Boolean = true
    ) = VideoInfo(
        uriString = "content://test/IMG_0001.MOV",
        displayName = "IMG_0001.MOV",
        mimeType = "video/quicktime",
        fileSizeBytes = (fileSizeMb * 1024 * 1024).toLong(),
        durationMs = durationMs,
        rawWidth = rawWidth,
        rawHeight = rawHeight,
        rotationDegrees = rotation,
        videoMimeType = videoMime,
        audioMimeType = audioMime,
        videoBitrateBps = videoBitrateBps,
        frameRate = fps,
        isHdr = isHdr,
        colorTransferLabel = if (isHdr) "HLG HDR" else "SDR",
        hasAudio = audioMime != null,
        hasLocationMetadata = true
    )

    @Test
    fun alignDimension_preservesExactStandardResolutionsAndAlignsTo8() {
        assertEquals(1080, BitrateCalculator.alignDimension(1080))
        assertEquals(1920, BitrateCalculator.alignDimension(1920))
        assertEquals(720, BitrateCalculator.alignDimension(720))
        assertEquals(480, BitrateCalculator.alignDimension(480))
        assertEquals(2160, BitrateCalculator.alignDimension(2160))
        assertEquals(856, BitrateCalculator.alignDimension(854))
        assertEquals(8, BitrateCalculator.alignDimension(3))
    }

    @Test
    fun computeDimensions_preservesExact1080pPortrait() {
        val (w, h) = BitrateCalculator.computeDimensions(
            sourceWidth = 2160,
            sourceHeight = 3840,
            maxShortSide = 1080,
            aspectRatioOption = AspectRatioOption.ORIGINAL
        )
        assertEquals(1080, w)
        assertEquals(1920, h)
        assertEquals(0, w % 8)
        assertEquals(0, h % 8)
    }

    @Test
    fun computeDimensions_appliesAspectRatioOverride() {
        val (w, h) = BitrateCalculator.computeDimensions(
            sourceWidth = 1920,
            sourceHeight = 1080,
            maxShortSide = 720,
            aspectRatioOption = AspectRatioOption.RATIO_1_1
        )
        assertEquals(720, w)
        assertEquals(720, h)
    }

    @Test
    fun targetSizeMode_staysWithinTargetAndAutoDownscalesLongVideo() {
        val info = sampleVideo(durationMs = 180_000L, fileSizeMb = 250.0)
        val config = ConversionPreset.DISCORD_10MB.toConfig()
        val plan = BitrateCalculator.buildPlan(info, config)

        assertTrue("Expected estimated size <= 10.2MB, got ${plan.estimatedSizeMb}", plan.estimatedSizeMb <= 10.2)
        assertTrue(plan.notice is EncodingNotice.AutoDownscaled)
        assertTrue(minOf(plan.outputWidth, plan.outputHeight) <= 480)
        assertEquals(0, plan.outputWidth % 8)
        assertEquals(0, plan.outputHeight % 8)
    }

    @Test
    fun targetSizeMode_doesNotInflateAlreadySmallShortVideo() {
        val smallShort = sampleVideo(
            rawWidth = 1080,
            rawHeight = 1920,
            rotation = 0,
            durationMs = 14_000L,
            fileSizeMb = 2.1,
            videoBitrateBps = 1_200_000,
            fps = 24f,
            videoMime = "video/hevc",
            isHdr = false
        )
        val plan = BitrateCalculator.buildPlan(smallShort, ConversionPreset.UPLOAD_25MB.toConfig())

        assertEquals(1080, plan.outputWidth)
        assertEquals(1920, plan.outputHeight)
        assertTrue("Small 2.1MB clip must not inflate near 25MB, got ${plan.estimatedSizeMb}", plan.estimatedSizeMb < 4.0)
        assertTrue(plan.notice is EncodingNotice.AlreadyUnderTarget)
    }

    @Test
    fun qualityMode_h265EstimatesLowerBitrateThanH264() {
        val info = sampleVideo(durationMs = 60_000L, videoMime = "video/avc", isHdr = false)
        val h264Plan = BitrateCalculator.buildPlan(
            info,
            ConversionConfig(
                videoCodec = OutputVideoCodec.H264,
                resolutionCap = ResolutionCap.P1080,
                frameRateCap = FrameRateCap.FPS_30,
                compressionMode = CompressionMode.QUALITY_LEVEL
            )
        )
        val h265Plan = BitrateCalculator.buildPlan(
            info,
            ConversionConfig(
                videoCodec = OutputVideoCodec.H265,
                resolutionCap = ResolutionCap.P1080,
                frameRateCap = FrameRateCap.FPS_30,
                compressionMode = CompressionMode.QUALITY_LEVEL
            )
        )
        assertTrue(h265Plan.videoBitrateBps < h264Plan.videoBitrateBps)
    }

    @Test
    fun videoInfo_diagnosesCompatibilityIssuesAccurately() {
        val info = sampleVideo(
            rotation = 90,
            fps = 60f,
            videoMime = "video/hevc",
            audioMime = "audio/opus",
            isHdr = true,
            fileSizeMb = 80.0
        )
        val issues = info.compatibilityIssues
        assertTrue(issues.contains(CompatibilityIssue.NON_H264_CODEC))
        assertTrue(issues.contains(CompatibilityIssue.HDR_COLOR))
        assertTrue(issues.contains(CompatibilityIssue.NON_AAC_AUDIO))
        assertTrue(issues.contains(CompatibilityIssue.HIGH_FPS))
        assertTrue(issues.contains(CompatibilityIssue.ROTATION_METADATA))
        assertTrue(issues.contains(CompatibilityIssue.LARGE_FILE_SIZE))
        assertEquals(2160, info.displayWidth)
        assertEquals(3840, info.displayHeight)
    }

    @Test
    fun ytDlpUrlExtractor_extractsCleanUrlFromShareCaptions() {
        val bilibiliShare = "【超清测试视频】 https://b23.tv/abc123XYZ 复制链接打开"
        assertEquals("https://b23.tv/abc123XYZ", YtDlpEngine.extractFirstUrl(bilibiliShare))
        val ytUrl = "Check this out: https://youtu.be/dQw4w9WgXcQ?si=12345."
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=12345", YtDlpEngine.extractFirstUrl(ytUrl))
    }

    @Test
    fun ytDlpQualityLabel_handlesPortraitAndLandscapeResolutions() {
        assertEquals("720p" to true, YtDlpEngine.computeResolutionTag(720, 1280))
        assertEquals("1080p" to false, YtDlpEngine.computeResolutionTag(1920, 1080))
        assertEquals("4K" to false, YtDlpEngine.computeResolutionTag(3840, 2160))
        assertEquals("480p" to true, YtDlpEngine.computeResolutionTag(480, 852))
    }

    @Test
    fun siteCookieStore_formatsValidNetscapeCookies() {
        val parsed = SiteCookieStore.parseCookieHeader("SESSDATA=abc123; bili_jct=xyz=456; ")
        assertEquals(2, parsed.size)
        assertEquals("SESSDATA" to "abc123", parsed[0])
        assertEquals("bili_jct" to "xyz=456", parsed[1])

        val lines = SiteCookieStore.formatNetscapeLines(
            mapOf((".bilibili.com" to "SESSDATA") to "abc123"),
            expiryEpoch = 2000000000L
        )
        assertEquals(1, lines.size)
        assertEquals(".bilibili.com\tTRUE\t/\tTRUE\t2000000000\tSESSDATA\tabc123", lines[0])
    }

    @Test
    fun historyStore_handlesEmptyInputSafely() {
        assertTrue(HistoryStore.decodeDownloadsJson("").isEmpty())
        assertTrue(HistoryStore.decodeConversionsJson("").isEmpty())
    }

    @Test
    fun ytDlpCodecNormalizer_mapsRawCodecStringsCleanly() {
        assertEquals("H.264", YtDlpEngine.normalizeVideoCodec("avc1.64002a"))
        assertEquals("H.265", YtDlpEngine.normalizeVideoCodec("hev1.1.6.L120.90"))
        assertEquals("AV1", YtDlpEngine.normalizeVideoCodec("av01.0.08M.08"))
        assertEquals("VP9", YtDlpEngine.normalizeVideoCodec("vp09.00.51.08"))
        assertEquals("AAC", YtDlpEngine.normalizeAudioCodec("mp4a.40.2"))
        assertEquals("Opus", YtDlpEngine.normalizeAudioCodec("opus"))
    }
}
