import Foundation

struct SubtitleDownloadArgs: Equatable {
    let subLangs: String
    let writeAutoSubs: Bool
}

enum YtDlpError: LocalizedError {
    case binaryMissing
    case failed(String)
    case cancelled
    case noOutputFile

    var errorDescription: String? {
        switch self {
        case .binaryMissing: return L10n.t("dl_err_no_ytdlp")
        case .failed(let msg): return msg
        case .cancelled: return L10n.t("dl_cancel")
        case .noOutputFile: return L10n.t("dl_err_no_output")
        }
    }
}

final class YtDlpService: @unchecked Sendable {
    static let shared = YtDlpService()

    private let lock = NSLock()
    private var activeProcess: Process?
    private var cancelled = false

    private static let ignoredSubKeys: Set<String> = ["live_chat", "danmaku"]
    private static let prioritySubLangs = ["zh", "en", "ja", "ko"]

    private func wasCancelled() -> Bool {
        lock.withLock { cancelled }
    }

    private func registerProcess(_ proc: Process) {
        lock.withLock {
            cancelled = false
            activeProcess = proc
        }
    }

    private func unregisterProcess(_ proc: Process) {
        lock.withLock {
            if activeProcess === proc { activeProcess = nil }
        }
    }

    func cancelActiveDownload() {
        let proc: Process? = lock.withLock {
            cancelled = true
            let p = activeProcess
            activeProcess = nil
            return p
        }
        if let proc, proc.isRunning {
            proc.terminate()
        }
    }

    func parseURL(_ url: String, cookiesFile: URL?) async throws -> ParsedLinkInfo {
        guard let bin = BinaryManager.shared.resolve("yt-dlp") else {
            throw YtDlpError.binaryMissing
        }
        var args = ["--no-playlist", "--no-warnings", "--dump-json"]
        if let cookiesFile, FileManager.default.fileExists(atPath: cookiesFile.path) {
            args += ["--cookies", cookiesFile.path]
        }
        args.append(url)

        let (exitCode, stdout, stderr) = try await runCaptured(executable: bin, args: args)
        guard exitCode == 0 else {
            let msg = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            throw YtDlpError.failed(msg.isEmpty ? "yt-dlp exited with code \(exitCode)" : msg)
        }

        let jsonLine = stdout.components(separatedBy: .newlines)
            .first(where: { $0.trimmingCharacters(in: .whitespaces).hasPrefix("{") }) ?? stdout
        guard let data = jsonLine.data(using: .utf8),
              let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw YtDlpError.failed("Invalid JSON metadata returned by yt-dlp")
        }

        return Self.parseMetadataDict(dict, fallbackURL: url)
    }

    func download(
        url: String,
        mode: DownloadFormatMode,
        selectedMaxHeight: Int?,
        embedSubtitles: Bool,
        subtitlePreset: SubtitlePreset,
        selectedSubtitleLangs: Set<String>,
        includeAutoSubtitles: Bool,
        hasOfficialSubtitles: Bool,
        selectedIncludesAutoTrack: Bool,
        cookiesFile: URL?,
        destinationDir: URL,
        onProgress: @escaping @Sendable (Int, Int, String) -> Void
    ) async throws -> URL {
        guard let bin = BinaryManager.shared.resolve("yt-dlp") else {
            throw YtDlpError.binaryMissing
        }

        let fm = FileManager.default
        try fm.createDirectory(at: destinationDir, withIntermediateDirectories: true)

        let workDir = fm.temporaryDirectory.appendingPathComponent("vidow_dl_\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: workDir, withIntermediateDirectories: true)

        var args: [String] = [
            "--no-playlist",
            "--no-mtime",
            "--newline",
            "--progress",
            "-o", workDir.appendingPathComponent("%(title).90s.%(ext)s").path
        ]

        if let ffmpegBin = BinaryManager.shared.resolve("ffmpeg") {
            args += ["--ffmpeg-location", ffmpegBin.deletingLastPathComponent().path]
        }

        if let cookiesFile, fm.fileExists(atPath: cookiesFile.path) {
            args += ["--cookies", cookiesFile.path]
        }

        switch mode {
        case .audioM4A:
            args += ["-x", "--audio-format", "m4a", "--audio-quality", "0"]
        case .videoMP4:
            let heightFilter = selectedMaxHeight.map { "[height<=\($0)]" } ?? ""
            args += [
                "-S", "vcodec:h264,res,acodec:m4a,ext:mp4:m4a",
                "-f", "bestvideo\(heightFilter)+bestaudio/best\(heightFilter)/best",
                "--merge-output-format", "mp4"
            ]
        case .videoBest:
            let heightFilter = selectedMaxHeight.map { "[height<=\($0)]" } ?? ""
            args += ["-f", "bestvideo\(heightFilter)+bestaudio/best\(heightFilter)/best"]
        }

        if embedSubtitles && mode != .audioM4A {
            let sysLang = Locale.preferredLanguages.first ?? Locale.current.identifier
            let subArgs = Self.resolveSubtitleDownloadArgs(
                preset: subtitlePreset,
                selectedLangs: selectedSubtitleLangs,
                systemLanguage: sysLang,
                includeAutoSubtitles: includeAutoSubtitles,
                hasOfficialSubtitles: hasOfficialSubtitles,
                selectedIncludesAutoTrack: selectedIncludesAutoTrack
            )
            args.append("--write-subs")
            if subArgs.writeAutoSubs {
                args.append("--write-auto-subs")
            }
            args += ["--sub-langs", subArgs.subLangs, "--embed-subs"]
        }

        args.append(url)

        defer {
            try? fm.removeItem(at: workDir)
        }

        let (exitCode, stderrTail) = try await runStreaming(executable: bin, args: args, onLine: { line in
            let parsed = Self.parseProgressLine(line)
            if let pct = parsed.percent {
                onProgress(pct, parsed.eta ?? 0, line)
            } else if !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                onProgress(-1, 0, line)
            }
        })

        if wasCancelled() {
            throw YtDlpError.cancelled
        }
        guard exitCode == 0 else {
            throw YtDlpError.failed(stderrTail.isEmpty ? "yt-dlp exited with code \(exitCode)" : stderrTail)
        }

        let files = (try? fm.contentsOfDirectory(at: workDir, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        let mediaFiles = files.filter { url in
            let name = url.lastPathComponent
            let ext = url.pathExtension.lowercased()
            return !name.hasSuffix(".part") && !name.hasSuffix(".ytdl") && ext != "vtt" && ext != "srt" && ext != "ass"
        }
        guard let outputTemp = mediaFiles.max(by: { fileSize($0) < fileSize($1) }), fileSize(outputTemp) > 0 else {
            throw YtDlpError.noOutputFile
        }

        // Move to final destination with unique filename if collision
        let finalURL = uniqueDestinationURL(in: destinationDir, preferredName: outputTemp.lastPathComponent)
        try fm.moveItem(at: outputTemp, to: finalURL)
        return finalURL
    }

    private func fileSize(_ url: URL) -> Int64 {
        (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
    }

    private func uniqueDestinationURL(in dir: URL, preferredName: String) -> URL {
        let fm = FileManager.default
        let base = (preferredName as NSString).deletingPathExtension
        let ext = (preferredName as NSString).pathExtension
        var candidate = dir.appendingPathComponent(preferredName)
        var index = 1
        while fm.fileExists(atPath: candidate.path) {
            let name = ext.isEmpty ? "\(base) (\(index))" : "\(base) (\(index)).\(ext)"
            candidate = dir.appendingPathComponent(name)
            index += 1
        }
        return candidate
    }

    private func runCaptured(executable: URL, args: [String]) async throws -> (Int32, String, String) {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let proc = Process()
                proc.executableURL = executable
                proc.arguments = args
                let outPipe = Pipe()
                let errPipe = Pipe()
                proc.standardOutput = outPipe
                proc.standardError = errPipe
                do {
                    try proc.run()
                    var outData = Data()
                    var errData = Data()
                    let group = DispatchGroup()
                    group.enter()
                    DispatchQueue.global(qos: .utility).async {
                        outData = outPipe.fileHandleForReading.readDataToEndOfFile()
                        group.leave()
                    }
                    group.enter()
                    DispatchQueue.global(qos: .utility).async {
                        errData = errPipe.fileHandleForReading.readDataToEndOfFile()
                        group.leave()
                    }
                    proc.waitUntilExit()
                    group.wait()
                    let outStr = String(data: outData, encoding: .utf8) ?? ""
                    let errStr = String(data: errData, encoding: .utf8) ?? ""
                    continuation.resume(returning: (proc.terminationStatus, outStr, errStr))
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private func runStreaming(
        executable: URL,
        args: [String],
        onLine: @escaping @Sendable (String) -> Void
    ) async throws -> (Int32, String) {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let proc = Process()
                proc.executableURL = executable
                proc.arguments = args
                let outPipe = Pipe()
                let errPipe = Pipe()
                proc.standardOutput = outPipe
                proc.standardError = errPipe

                self.registerProcess(proc)

                var errLines: [String] = []
                let errLock = NSLock()

                outPipe.fileHandleForReading.readabilityHandler = { handle in
                    let data = handle.availableData
                    guard !data.isEmpty, let chunk = String(data: data, encoding: .utf8) else { return }
                    for line in chunk.components(separatedBy: CharacterSet.newlines) where !line.isEmpty {
                        onLine(line)
                    }
                }
                errPipe.fileHandleForReading.readabilityHandler = { handle in
                    let data = handle.availableData
                    guard !data.isEmpty, let chunk = String(data: data, encoding: .utf8) else { return }
                    errLock.lock()
                    errLines.append(contentsOf: chunk.components(separatedBy: .newlines).filter { !$0.isEmpty })
                    if errLines.count > 12 { errLines = Array(errLines.suffix(12)) }
                    errLock.unlock()
                }

                do {
                    try proc.run()
                    proc.waitUntilExit()
                    outPipe.fileHandleForReading.readabilityHandler = nil
                    errPipe.fileHandleForReading.readabilityHandler = nil

                    self.unregisterProcess(proc)

                    errLock.lock()
                    let errSummary = errLines.suffix(4).joined(separator: "\n")
                    errLock.unlock()

                    continuation.resume(returning: (proc.terminationStatus, errSummary))
                } catch {
                    outPipe.fileHandleForReading.readabilityHandler = nil
                    errPipe.fileHandleForReading.readabilityHandler = nil
                    self.unregisterProcess(proc)
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    // MARK: - Pure Helpers (Unit Tested)

    static func extractFirstURL(from rawText: String) -> String? {
        let pattern = #"https?://[^\s\u4e00-\u9fa5"'<>]+"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return nil }
        let nsRange = NSRange(rawText.startIndex..<rawText.endIndex, in: rawText)
        guard let match = regex.firstMatch(in: rawText, range: nsRange),
              let range = Range(match.range, in: rawText) else { return nil }
        let trimChars = CharacterSet(charactersIn: ").],，。")
        return String(rawText[range]).trimmingCharacters(in: trimChars)
    }

    static func computeResolutionTag(width: Int, height: Int) -> (baseTag: String, isPortrait: Bool) {
        if width <= 0 { return ("\(height)p", false) }
        let isPortrait = height > width
        let shortSide = min(width, height)
        let tag: String
        if shortSide >= 2100 {
            tag = "4K"
        } else if (1400...1500).contains(shortSide) {
            tag = "2K"
        } else {
            tag = "\(shortSide)p"
        }
        return (tag, isPortrait)
    }

    static func normalizeVideoCodec(_ raw: String?) -> String? {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !s.isEmpty, s != "none", s != "unknown" else { return nil }
        if s.hasPrefix("avc") || s.contains("h264") || s.contains("264") { return "H.264" }
        if s.hasPrefix("hev") || s.hasPrefix("hvc") || s.contains("hevc") || s.contains("265") { return "H.265" }
        if s.hasPrefix("av01") || s == "av1" { return "AV1" }
        if s.hasPrefix("vp09") || s.hasPrefix("vp9") { return "VP9" }
        if s.hasPrefix("vp8") { return "VP8" }
        return s.components(separatedBy: ".").first?.uppercased()
    }

    static func normalizeAudioCodec(_ raw: String?) -> String? {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !s.isEmpty, s != "none", s != "unknown" else { return nil }
        if s.hasPrefix("mp4a") || s.contains("aac") { return "AAC" }
        if s.contains("opus") { return "Opus" }
        if s.contains("vorbis") { return "Vorbis" }
        if s.contains("mp3") { return "MP3" }
        if s.contains("flac") { return "FLAC" }
        return s.components(separatedBy: ".").first?.uppercased()
    }

    static func resolveSubtitleDownloadArgs(
        preset: SubtitlePreset,
        selectedLangs: Set<String>,
        systemLanguage: String,
        includeAutoSubtitles: Bool,
        hasOfficialSubtitles: Bool = true,
        selectedIncludesAutoTrack: Bool = false
    ) -> SubtitleDownloadArgs {
        let sysBase = systemLanguage
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .components(separatedBy: CharacterSet(charactersIn: "-_"))
            .first
            .flatMap { $0.isEmpty ? nil : $0 } ?? "en"

        switch preset {
        case .multi4:
            return SubtitleDownloadArgs(subLangs: "zh.*,en.*,ja.*,ko.*", writeAutoSubs: includeAutoSubtitles)
        case .systemLang:
            return SubtitleDownloadArgs(subLangs: "\(sysBase).*", writeAutoSubs: includeAutoSubtitles)
        case .allOfficial:
            if includeAutoSubtitles && !hasOfficialSubtitles {
                return SubtitleDownloadArgs(subLangs: "zh.*,en.*,ja.*,ko.*", writeAutoSubs: true)
            }
            return SubtitleDownloadArgs(subLangs: "all,-live_chat,-danmaku", writeAutoSubs: false)
        case .custom:
            let cleaned = selectedLangs.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }.sorted()
            if cleaned.isEmpty {
                return SubtitleDownloadArgs(subLangs: "zh.*,en.*,ja.*,ko.*", writeAutoSubs: includeAutoSubtitles)
            }
            return SubtitleDownloadArgs(
                subLangs: cleaned.joined(separator: ","),
                writeAutoSubs: includeAutoSubtitles || selectedIncludesAutoTrack
            )
        }
    }

    static func parseProgressLine(_ line: String) -> (percent: Int?, eta: Int?) {
        var pct: Int? = nil
        var eta: Int? = nil
        if let pctRange = line.range(of: #"\d+(?:\.\d+)?%"#, options: .regularExpression) {
            let numStr = line[pctRange].dropLast()
            if let val = Double(numStr) {
                pct = max(0, min(100, Int(val.rounded())))
            }
        }
        if let etaRange = line.range(of: #"ETA\s+(\d+):(\d+)(?::(\d+))?"#, options: .regularExpression) {
            let token = String(line[etaRange]).replacingOccurrences(of: "ETA", with: "").trimmingCharacters(in: .whitespaces)
            let parts = token.split(separator: ":").compactMap { Int($0) }
            if parts.count == 2 {
                eta = parts[0] * 60 + parts[1]
            } else if parts.count == 3 {
                eta = parts[0] * 3600 + parts[1] * 60 + parts[2]
            }
        }
        return (pct, eta)
    }

    static func parseMetadataDict(_ dict: [String: Any], fallbackURL: String) -> ParsedLinkInfo {
        let durationSec = max(0, Int((dict["duration"] as? Double) ?? Double(dict["duration"] as? Int ?? 0)))
        var rawThumb = (dict["thumbnail"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        if rawThumb == nil, let thumbs = dict["thumbnails"] as? [[String: Any]] {
            rawThumb = thumbs.reversed().compactMap { $0["url"] as? String }.first { !$0.isEmpty }
        }
        let safeThumb = rawThumb.map { $0.hasPrefix("http://") ? "https://" + $0.dropFirst(7) : $0 }

        struct RawFmt {
            let vcodec: String?
            let acodec: String?
            let ext: String?
            let width: Int
            let height: Int
            let fps: Int
            let tbr: Int
            let abr: Int
            let filesize: Int64
            let filesizeApprox: Int64
        }

        let rawFormats: [RawFmt] = (dict["formats"] as? [[String: Any]] ?? []).map { f in
            RawFmt(
                vcodec: f["vcodec"] as? String,
                acodec: f["acodec"] as? String,
                ext: f["ext"] as? String,
                width: (f["width"] as? Int) ?? Int(f["width"] as? Double ?? 0),
                height: (f["height"] as? Int) ?? Int(f["height"] as? Double ?? 0),
                fps: Int((f["fps"] as? Double) ?? Double(f["fps"] as? Int ?? 0)),
                tbr: Int((f["tbr"] as? Double) ?? Double(f["tbr"] as? Int ?? 0)),
                abr: Int((f["abr"] as? Double) ?? Double(f["abr"] as? Int ?? 0)),
                filesize: (f["filesize"] as? Int64) ?? Int64(f["filesize"] as? Int ?? 0),
                filesizeApprox: (f["filesize_approx"] as? Int64) ?? Int64(f["filesize_approx"] as? Int ?? 0)
            )
        }

        func estimateSize(_ f: RawFmt) -> Int64? {
            let exact = max(f.filesize, f.filesizeApprox)
            if exact > 0 { return exact }
            let kbps = f.tbr > 0 ? f.tbr : f.abr
            if kbps > 0 && durationSec > 0 {
                return Int64(kbps) * 1000 * Int64(durationSec) / 8
            }
            return nil
        }

        let audioOnly = rawFormats.filter { f in
            (f.vcodec == nil || f.vcodec?.lowercased() == "none") &&
            f.acodec != nil && f.acodec?.lowercased() != "none"
        }
        let bestM4a = audioOnly
            .filter { $0.ext?.lowercased() == "m4a" || ($0.acodec?.lowercased().contains("mp4a") ?? false) }
            .max(by: { ($0.abr > 0 ? $0.abr : $0.tbr) < ($1.abr > 0 ? $1.abr : $1.tbr) })
        let bestAnyAudio = audioOnly
            .max(by: { ($0.abr > 0 ? $0.abr : $0.tbr) < ($1.abr > 0 ? $1.abr : $1.tbr) })

        let audioForCompat = bestM4a ?? bestAnyAudio
        let audioForBest = bestAnyAudio ?? bestM4a
        let bestAudioCodec = normalizeAudioCodec(audioForBest?.acodec) ?? "AAC"
        let bestAudioExt = (audioForBest?.ext ?? "m4a").uppercased()
        let bestAudioAbr = audioForBest.map { $0.abr > 0 ? $0.abr : $0.tbr } ?? 0
        let bestAudioSize = audioForBest.flatMap { estimateSize($0) }

        let fallbackExt = (dict["ext"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "mp4"

        let videoFormats = rawFormats.filter { $0.height > 0 && ($0.vcodec == nil || $0.vcodec?.lowercased() != "none") }
        let grouped = Dictionary(grouping: videoFormats, by: { $0.height })

        func makeOption(_ f: RawFmt, pairedAudio: RawFmt?, forceMp4: Bool) -> ParsedQualityOption {
            let hasAudio = f.acodec != nil && f.acodec?.lowercased() != "none"
            let aCodec = hasAudio ? normalizeAudioCodec(f.acodec) : (normalizeAudioCodec(pairedAudio?.acodec) ?? bestAudioCodec)
            let vBytes = estimateSize(f)
            let aBytes = hasAudio ? nil : pairedAudio.flatMap { estimateSize($0) }
            let totalBytes: Int64? = (vBytes != nil && aBytes != nil) ? (vBytes! + aBytes!) : vBytes
            let (tag, isPortrait) = computeResolutionTag(width: max(0, f.width), height: f.height)
            return ParsedQualityOption(
                ytDlpHeight: f.height,
                width: max(0, f.width),
                height: f.height,
                baseTag: tag,
                isPortrait: isPortrait,
                ext: forceMp4 ? "MP4" : (f.ext ?? fallbackExt).uppercased(),
                videoCodec: normalizeVideoCodec(f.vcodec),
                audioCodec: aCodec,
                fps: f.fps,
                bitrateKbps: f.tbr,
                approxSizeBytes: totalBytes
            )
        }

        let compatQualities = grouped.values.compactMap { list -> ParsedQualityOption? in
            guard let chosen = list.max(by: { a, b in
                let aScore = normalizeVideoCodec(a.vcodec) == "H.264" ? 2 : (a.ext?.lowercased() == "mp4" ? 1 : 0)
                let bScore = normalizeVideoCodec(b.vcodec) == "H.264" ? 2 : (b.ext?.lowercased() == "mp4" ? 1 : 0)
                if aScore != bScore { return aScore < bScore }
                if a.tbr != b.tbr { return a.tbr < b.tbr }
                return max(a.filesize, a.filesizeApprox) < max(b.filesize, b.filesizeApprox)
            }) else { return nil }
            return makeOption(chosen, pairedAudio: audioForCompat, forceMp4: true)
        }.sorted(by: { $0.ytDlpHeight > $1.ytDlpHeight })

        let bestQualities = grouped.values.compactMap { list -> ParsedQualityOption? in
            guard let chosen = list.max(by: { a, b in
                if a.tbr != b.tbr { return a.tbr < b.tbr }
                if max(a.filesize, a.filesizeApprox) != max(b.filesize, b.filesizeApprox) {
                    return max(a.filesize, a.filesizeApprox) < max(b.filesize, b.filesizeApprox)
                }
                return a.fps < b.fps
            }) else { return nil }
            return makeOption(chosen, pairedAudio: audioForBest, forceMp4: false)
        }.sorted(by: { $0.ytDlpHeight > $1.ytDlpHeight })

        let (officialSubs, autoSubs) = parseSubtitleTracks(from: dict)

        let extractor = (dict["extractor_key"] as? String) ?? (dict["extractor"] as? String) ?? "Web"
        let title = (dict["title"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Untitled Video"
        let uploader = (dict["uploader"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? extractor

        return ParsedLinkInfo(
            url: fallbackURL,
            title: title,
            uploader: uploader,
            durationSeconds: durationSec,
            extractor: extractor,
            thumbnailUrl: safeThumb,
            defaultExt: fallbackExt.uppercased(),
            defaultVideoCodec: compatQualities.first?.videoCodec ?? bestQualities.first?.videoCodec,
            bestAudioCodec: bestAudioCodec,
            bestAudioExt: bestAudioExt,
            bestAudioBitrateKbps: bestAudioAbr,
            bestAudioSizeBytes: bestAudioSize,
            compatQualities: compatQualities,
            bestQualities: bestQualities,
            officialSubtitles: officialSubs,
            autoSubtitles: autoSubs
        )
    }

    static func parseSubtitleTracks(from dict: [String: Any]) -> ([SubtitleTrackOption], [SubtitleTrackOption]) {
        let displayLocale = Locale.current
        let sysLang = (Locale.preferredLanguages.first ?? displayLocale.identifier)
            .lowercased()
            .components(separatedBy: CharacterSet(charactersIn: "-_"))
            .first ?? "en"

        func extract(_ rawObj: Any?, isAuto: Bool) -> [String: SubtitleTrackOption] {
            guard let map = rawObj as? [String: Any] else { return [:] }
            var out: [String: SubtitleTrackOption] = [:]
            for (langKey, value) in map {
                let lower = langKey.lowercased()
                if ignoredSubKeys.contains(lower) { continue }
                guard let arr = value as? [[String: Any]], !arr.isEmpty else { continue }
                let remoteName = arr.compactMap { $0["name"] as? String }.first { !$0.isEmpty }
                let cleanCode = langKey.replacingOccurrences(of: "-orig", with: "")
                let localized = displayLocale.localizedString(forIdentifier: cleanCode)
                let label = remoteName ?? localized ?? langKey
                out[langKey] = SubtitleTrackOption(langCode: langKey, displayName: label, isAutoGenerated: isAuto)
            }
            return out
        }

        func sortRank(_ code: String) -> Int {
            let lower = code.lowercased()
            let base = lower.components(separatedBy: CharacterSet(charactersIn: "-_")).first ?? lower
            if lower.hasSuffix("-orig") { return 0 }
            if base == sysLang { return 1 }
            if let idx = prioritySubLangs.firstIndex(of: base) { return 2 + idx }
            return 10
        }

        let officialMap = extract(dict["subtitles"], isAuto: false)
        let autoMap = extract(dict["automatic_captions"], isAuto: true).filter { officialMap[$0.key] == nil }

        let sortFn: (SubtitleTrackOption, SubtitleTrackOption) -> Bool = { a, b in
            let ra = sortRank(a.langCode)
            let rb = sortRank(b.langCode)
            if ra != rb { return ra < rb }
            return a.langCode.lowercased() < b.langCode.lowercased()
        }

        return (officialMap.values.sorted(by: sortFn), autoMap.values.sorted(by: sortFn))
    }
}
