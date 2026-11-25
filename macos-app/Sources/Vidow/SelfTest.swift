import Foundation

/// Built-in core logic verification suite runnable via `Vidow --self-test`
/// (works with Apple CommandLineTools without requiring full Xcode XCTest).
enum VidowSelfTest {
    private static func check(_ condition: @autoclosure () -> Bool, _ message: String) {
        if !condition() {
            fputs("FAIL: \(message)\n", stderr)
            exit(1)
        }
    }

    private static func sampleVideo(
        width: Int = 3840,
        height: Int = 2160,
        duration: Double = 60,
        fileSizeBytes: Int64 = 120 * 1024 * 1024,
        bitrateBps: Int64 = 16_000_000,
        isHDR: Bool = true
    ) -> LocalVideoInfo {
        LocalVideoInfo(
            url: URL(fileURLWithPath: "/tmp/sample.mov"),
            fileName: "sample.mov",
            fileSizeBytes: fileSizeBytes,
            durationSeconds: duration,
            width: width,
            height: height,
            frameRate: 60.0,
            videoCodec: "H.265",
            audioCodec: "AAC",
            containerExt: "mov",
            bitrateBps: bitrateBps,
            isHDR: isHDR,
            hasAudio: true
        )
    }

    static func runAllAndExit() -> Never {
        // 1. Exact 8px alignment
        check(BitrateCalculator.alignDimension(2160) == 2160, "2160p alignment")
        check(BitrateCalculator.alignDimension(1080) == 1080, "1080p alignment")
        check(BitrateCalculator.alignDimension(720) == 720, "720p alignment")
        check(BitrateCalculator.alignDimension(480) == 480, "480p alignment")

        // 2. No-bloat target size ceiling
        let small = sampleVideo(
            width: 1920,
            height: 1080,
            duration: 15,
            fileSizeBytes: 5 * 1024 * 1024,
            bitrateBps: 2_600_000,
            isHDR: false
        )
        var cfgSmall = ConversionConfig.forPreset(.upload25MB, duration: 15)
        cfgSmall.targetSizeMb = 25
        let planSmall = BitrateCalculator.buildPlan(info: small, config: cfgSmall)
        check(planSmall.notice == .alreadyUnderTarget(targetMb: 25), "alreadyUnderTarget notice")
        check(planSmall.estimatedBytes < 10 * 1024 * 1024, "no bloat estimated size")

        // 3. Auto-downscaling on tight target MB
        let long4K = sampleVideo(duration: 300, fileSizeBytes: 600 * 1024 * 1024)
        var cfgTight = ConversionConfig.forPreset(.upload25MB, duration: 300)
        cfgTight.targetSizeMb = 15
        cfgTight.autoDownscaleForTargetSize = true
        let planTight = BitrateCalculator.buildPlan(info: long4K, config: cfgTight)
        check(planTight.outputHeight < 1080, "auto-downscale height < 1080")
        if case .autoDownscaled(_, let mb) = planTight.notice {
            check(mb == 15, "auto-downscale targetMb")
        } else {
            check(false, "expected autoDownscaled notice")
        }

        // 4. URL extraction, codec normalizer, portrait resolution tag
        let share = "【测试】 https://b23.tv/abc123XYZ 复制打开"
        check(YtDlpService.extractFirstURL(from: share) == "https://b23.tv/abc123XYZ", "URL extraction")
        check(YtDlpService.normalizeVideoCodec("avc1.64002a") == "H.264", "H.264 normalize")
        check(YtDlpService.normalizeVideoCodec("av01.0.08M.08") == "AV1", "AV1 normalize")
        check(YtDlpService.normalizeAudioCodec("mp4a.40.2") == "AAC", "AAC normalize")
        let portrait = YtDlpService.computeResolutionTag(width: 720, height: 1280)
        check(portrait.baseTag == "720p" && portrait.isPortrait, "portrait tag")

        // 5. Subtitle resolver + anti-429 guardrail
        let multi4 = YtDlpService.resolveSubtitleDownloadArgs(
            preset: .multi4, selectedLangs: [], systemLanguage: "ja-JP", includeAutoSubtitles: true
        )
        check(multi4 == SubtitleDownloadArgs(subLangs: "zh.*,en.*,ja.*,ko.*", writeAutoSubs: true), "multi4 subs")

        let sys = YtDlpService.resolveSubtitleDownloadArgs(
            preset: .systemLang, selectedLangs: [], systemLanguage: "ja-JP", includeAutoSubtitles: false
        )
        check(sys == SubtitleDownloadArgs(subLangs: "ja.*", writeAutoSubs: false), "system lang subs")

        let allOfficial = YtDlpService.resolveSubtitleDownloadArgs(
            preset: .allOfficial, selectedLangs: [], systemLanguage: "zh", includeAutoSubtitles: true, hasOfficialSubtitles: true
        )
        check(allOfficial == SubtitleDownloadArgs(subLangs: "all,-live_chat,-danmaku", writeAutoSubs: false), "allOfficial guardrail")

        let customAuto = YtDlpService.resolveSubtitleDownloadArgs(
            preset: .custom, selectedLangs: ["ja", "zh-Hans"], systemLanguage: "ja", includeAutoSubtitles: false, selectedIncludesAutoTrack: true
        )
        check(customAuto == SubtitleDownloadArgs(subLangs: "ja,zh-Hans", writeAutoSubs: true), "custom auto subs")

        // 6. Subtitle track parser excludes live_chat & deduplicates
        let mock: [String: Any] = [
            "subtitles": [
                "ja": [["ext": "vtt", "name": "日本語"]],
                "live_chat": [["ext": "json"]],
                "en": [["ext": "vtt", "name": "English"]]
            ],
            "automatic_captions": [
                "ja": [["ext": "vtt", "name": "日本語 (自動)"]],
                "ko": [["ext": "vtt", "name": "한국어"]]
            ]
        ]
        let (official, auto) = YtDlpService.parseSubtitleTracks(from: mock)
        check(Set(official.map(\.langCode)) == ["ja", "en"], "official subtitle parsing")
        check(Set(auto.map(\.langCode)) == ["ko"], "auto subtitle deduplication")

        // 7. Progress line parser
        let prog = YtDlpService.parseProgressLine("[download]  47.8% of   42.50MiB at    6.20MiB/s ETA 00:05")
        check(prog.percent == 48 && prog.eta == 5, "progress parser")

        print("PASS: All 7 Vidow macOS core test suites passed!")
        exit(0)
    }
}
