import AVFoundation
import Foundation

enum ConverterError: LocalizedError {
    case ffmpegMissing
    case failed(String)
    case cancelled

    var errorDescription: String? {
        switch self {
        case .ffmpegMissing: return "FFmpeg binary not found."
        case .failed(let msg): return msg
        case .cancelled: return L10n.t("dl_cancel")
        }
    }
}

final class VideoConverterService: @unchecked Sendable {
    static let shared = VideoConverterService()

    private let lock = NSLock()
    private var activeProcess: Process?
    private var cancelled = false

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

    func cancelActiveConversion() {
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

    func inspectVideo(at url: URL) async throws -> LocalVideoInfo {
        let fileSize = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
        let ext = url.pathExtension.lowercased()

        // Try ffprobe first for widest container/codec support (MKV, WebM, FLV, AV1, HDR transfer info)
        if let ffprobe = BinaryManager.shared.resolve("ffprobe"),
           let probed = await inspectWithFFprobe(ffprobe: ffprobe, url: url, fileSize: fileSize, ext: ext) {
            return probed
        }

        // Fallback to native AVAsset
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration).seconds
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        let audioTracks = try await asset.loadTracks(withMediaType: .audio)
        guard let vTrack = videoTracks.first else {
            throw ConverterError.failed("No video track found in file.")
        }
        let natural = try await vTrack.load(.naturalSize)
        let transform = try await vTrack.load(.preferredTransform)
        let transformed = natural.applying(transform)
        let width = max(64, Int(abs(transformed.width).rounded()))
        let height = max(64, Int(abs(transformed.height).rounded()))
        let fps = Double(try await vTrack.load(.nominalFrameRate))
        let estRate = Int64(try await vTrack.load(.estimatedDataRate))
        let chars = (try? await vTrack.load(.mediaCharacteristics)) ?? []
        let hasHDR = chars.contains(.containsHDRVideo)

        return LocalVideoInfo(
            url: url,
            fileName: url.lastPathComponent,
            fileSizeBytes: fileSize,
            durationSeconds: max(0.5, duration.isFinite ? duration : 0.5),
            width: width,
            height: height,
            frameRate: fps > 1 ? fps : 30.0,
            videoCodec: "Video",
            audioCodec: audioTracks.isEmpty ? nil : "Audio",
            containerExt: ext,
            bitrateBps: estRate,
            isHDR: hasHDR,
            hasAudio: !audioTracks.isEmpty
        )
    }

    private func inspectWithFFprobe(ffprobe: URL, url: URL, fileSize: Int64, ext: String) async -> LocalVideoInfo? {
        let args = [
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            url.path
        ]
        guard let out = await BinaryManager.shared.runSimpleOutput(ffprobe, args: args),
              let data = out.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streams = root["streams"] as? [[String: Any]] else {
            return nil
        }

        guard let vStream = streams.first(where: { ($0["codec_type"] as? String) == "video" }) else {
            return nil
        }
        let aStream = streams.first(where: { ($0["codec_type"] as? String) == "audio" })
        let format = root["format"] as? [String: Any]

        var w = (vStream["width"] as? Int) ?? 0
        var h = (vStream["height"] as? Int) ?? 0

        // Check rotation side_data or tags
        var rotation = 0
        if let tags = vStream["tags"] as? [String: Any], let rotStr = tags["rotate"] as? String, let r = Int(rotStr) {
            rotation = abs(r) % 360
        }
        if let sideData = vStream["side_data_list"] as? [[String: Any]] {
            for sd in sideData {
                if let r = sd["rotation"] as? Int {
                    rotation = abs(r) % 360
                }
            }
        }
        if rotation == 90 || rotation == 270 {
            swap(&w, &h)
        }

        let durationStr = (format?["duration"] as? String) ?? (vStream["duration"] as? String) ?? "0"
        let duration = max(0.5, Double(durationStr) ?? 0.5)

        let rawCodec = (vStream["codec_name"] as? String) ?? "unknown"
        let codecLabel = YtDlpService.normalizeVideoCodec(rawCodec) ?? rawCodec.uppercased()
        let audioCodec = (aStream?["codec_name"] as? String).flatMap { YtDlpService.normalizeAudioCodec($0) ?? $0.uppercased() }

        let fps = parseRationalFPS(vStream["avg_frame_rate"] as? String)
            ?? parseRationalFPS(vStream["r_frame_rate"] as? String)
            ?? 30.0

        let vBitrate = Int64((vStream["bit_rate"] as? String) ?? "") ?? Int64((format?["bit_rate"] as? String) ?? "") ?? 0
        let colorTransfer = ((vStream["color_transfer"] as? String) ?? "").lowercased()
        let colorPrimaries = ((vStream["color_primaries"] as? String) ?? "").lowercased()
        let isHDR = colorTransfer.contains("smpte2084") || colorTransfer.contains("arib-std-b67") || colorPrimaries.contains("bt2020")

        return LocalVideoInfo(
            url: url,
            fileName: url.lastPathComponent,
            fileSizeBytes: fileSize,
            durationSeconds: duration,
            width: max(64, w),
            height: max(64, h),
            frameRate: fps,
            videoCodec: codecLabel,
            audioCodec: audioCodec,
            containerExt: ext,
            bitrateBps: vBitrate,
            isHDR: isHDR,
            hasAudio: aStream != nil
        )
    }

    private func parseRationalFPS(_ raw: String?) -> Double? {
        guard let raw, !raw.isEmpty, raw != "0/0" else { return nil }
        let parts = raw.split(separator: "/")
        if parts.count == 2, let num = Double(parts[0]), let den = Double(parts[1]), den > 0 {
            let val = num / den
            return (val >= 1 && val <= 240) ? val : nil
        }
        return Double(raw)
    }

    func convert(
        info: LocalVideoInfo,
        config: ConversionConfig,
        plan: EncodingPlan,
        destinationDir: URL,
        onProgress: @escaping @Sendable (Int) -> Void
    ) async throws -> ConversionHistoryItem {
        guard let ffmpeg = BinaryManager.shared.resolve("ffmpeg") else {
            throw ConverterError.ffmpegMissing
        }

        let fm = FileManager.default
        try fm.createDirectory(at: destinationDir, withIntermediateDirectories: true)

        let baseName = (info.fileName as NSString).deletingPathExtension
        let tag = config.videoCodec == .hevc ? "HEVC" : "MP4"
        let outputURL = uniqueOutputURL(in: destinationDir, base: "\(baseName)_Vidow_\(tag)", ext: "mp4")

        let startTime = Date()
        let args = buildFFmpegArgs(info: info, config: config, plan: plan, outputURL: outputURL)

        let (exitCode, stderrTail) = try await runFFmpeg(
            ffmpeg: ffmpeg,
            args: args,
            totalDurationSec: plan.effectiveDurationSeconds,
            onProgress: onProgress
        )

        if wasCancelled() {
            try? fm.removeItem(at: outputURL)
            throw ConverterError.cancelled
        }

        guard exitCode == 0 else {
            try? fm.removeItem(at: outputURL)
            throw ConverterError.failed(stderrTail.isEmpty ? "FFmpeg exited with code \(exitCode)" : stderrTail)
        }

        let outputSize = (try? outputURL.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
        let elapsed = Date().timeIntervalSince(startTime)

        return ConversionHistoryItem(
            outputPath: outputURL.path,
            outputFileName: outputURL.lastPathComponent,
            originalSizeBytes: info.fileSizeBytes,
            outputSizeBytes: outputSize,
            outputWidth: plan.outputWidth,
            outputHeight: plan.outputHeight,
            elapsedSeconds: elapsed,
            codecLabel: config.videoCodec == .hevc ? "H.265" : "H.264",
            convertedHdrToSdr: info.isHDR && config.toneMapHdrToSdr,
            date: Date()
        )
    }

    private func buildFFmpegArgs(
        info: LocalVideoInfo,
        config: ConversionConfig,
        plan: EncodingPlan,
        outputURL: URL
    ) -> [String] {
        var args: [String] = ["-y", "-hide_banner", "-nostats", "-progress", "pipe:1"]

        let trimStart = max(0.0, config.trimStartSeconds)
        let trimEnd = config.trimEndSeconds > trimStart ? min(info.durationSeconds, config.trimEndSeconds) : info.durationSeconds
        let isTrimmed = trimStart > 0.1 || (info.durationSeconds - trimEnd) > 0.1

        if trimStart > 0.1 {
            args += ["-ss", String(format: "%.3f", trimStart)]
        }
        args += ["-i", info.url.path]
        if isTrimmed {
            args += ["-t", String(format: "%.3f", max(0.5, trimEnd - trimStart))]
        }

        // Build video filter chain
        var filters: [String] = []
        let w = plan.outputWidth
        let h = plan.outputHeight

        if config.aspectRatio != .original {
            switch config.aspectFitMode {
            case .crop:
                filters.append("scale=\(w):\(h):force_original_aspect_ratio=increase,crop=\(w):\(h)")
            case .pad:
                filters.append("scale=\(w):\(h):force_original_aspect_ratio=decrease,pad=\(w):\(h):(ow-iw)/2:(oh-ih)/2:black")
            }
        } else {
            filters.append("scale=\(w):\(h)")
        }

        if info.isHDR && config.toneMapHdrToSdr {
            // Safe Rec.709 SDR mapping compatible with standard FFmpeg builds
            filters.append("colorspace=all=bt709:iall=bt2020:fast=1")
        }
        filters.append("format=yuv420p")

        args += ["-vf", filters.joined(separator: ",")]
        args += ["-r", "\(plan.outputFps)"]

        // Apple Silicon VideoToolbox hardware encoding
        let videoKbps = max(220, plan.videoBitrateBps / 1000)
        switch config.videoCodec {
        case .h264:
            args += [
                "-c:v", "h264_videotoolbox",
                "-profile:v", "high",
                "-b:v", "\(videoKbps)k",
                "-maxrate", "\(Int(Double(videoKbps) * 1.35))k",
                "-bufsize", "\(videoKbps * 2)k"
            ]
        case .hevc:
            args += [
                "-c:v", "hevc_videotoolbox",
                "-tag:v", "hvc1",
                "-b:v", "\(videoKbps)k",
                "-maxrate", "\(Int(Double(videoKbps) * 1.35))k",
                "-bufsize", "\(videoKbps * 2)k"
            ]
        }

        if info.isHDR && config.toneMapHdrToSdr {
            args += ["-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709"]
        }

        if config.removeAudio || !info.hasAudio {
            args.append("-an")
        } else {
            let audioKbps = max(64, plan.audioBitrateBps / 1000)
            args += ["-c:a", "aac", "-b:a", "\(audioKbps)k", "-ac", "2"]
        }

        if config.stripMetadata {
            args += ["-map_metadata", "-1"]
        }

        args += ["-movflags", "+faststart", outputURL.path]
        return args
    }

    private func runFFmpeg(
        ffmpeg: URL,
        args: [String],
        totalDurationSec: Double,
        onProgress: @escaping @Sendable (Int) -> Void
    ) async throws -> (Int32, String) {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let proc = Process()
                proc.executableURL = ffmpeg
                proc.arguments = args
                let outPipe = Pipe()
                let errPipe = Pipe()
                proc.standardOutput = outPipe
                proc.standardError = errPipe

                self.registerProcess(proc)

                var errLines: [String] = []
                let errLock = NSLock()
                let totalUs = max(1.0, totalDurationSec * 1_000_000.0)

                outPipe.fileHandleForReading.readabilityHandler = { handle in
                    let data = handle.availableData
                    guard !data.isEmpty, let chunk = String(data: data, encoding: .utf8) else { return }
                    for line in chunk.components(separatedBy: .newlines) {
                        if line.hasPrefix("out_time_us=") {
                            let valStr = line.replacingOccurrences(of: "out_time_us=", with: "").trimmingCharacters(in: .whitespaces)
                            if let us = Double(valStr), us >= 0 {
                                let pct = min(99, max(0, Int((us / totalUs * 100.0).rounded())))
                                onProgress(pct)
                            }
                        } else if line == "progress=end" {
                            onProgress(100)
                        }
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
                    let summary = errLines.suffix(4).joined(separator: "\n")
                    errLock.unlock()
                    continuation.resume(returning: (proc.terminationStatus, summary))
                } catch {
                    outPipe.fileHandleForReading.readabilityHandler = nil
                    errPipe.fileHandleForReading.readabilityHandler = nil
                    self.unregisterProcess(proc)
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private func uniqueOutputURL(in dir: URL, base: String, ext: String) -> URL {
        let fm = FileManager.default
        var candidate = dir.appendingPathComponent("\(base).\(ext)")
        var idx = 1
        while fm.fileExists(atPath: candidate.path) {
            candidate = dir.appendingPathComponent("\(base) (\(idx)).\(ext)")
            idx += 1
        }
        return candidate
    }
}
