import Foundation

enum YtDlpUpdateResult {
    case updated(String)
    case upToDate(String)
    case failed(String)
}

final class BinaryManager {
    static let shared = BinaryManager()

    let appSupportDir: URL
    let userBinDir: URL

    private init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support")
        appSupportDir = base.appendingPathComponent("Vidow", isDirectory: true)
        userBinDir = appSupportDir.appendingPathComponent("bin", isDirectory: true)
        try? FileManager.default.createDirectory(at: userBinDir, withIntermediateDirectories: true)
    }

    func resolve(_ tool: String) -> URL? {
        let fm = FileManager.default
        var candidates: [URL] = [
            userBinDir.appendingPathComponent(tool)
        ]
        if let resURL = Bundle.main.resourceURL {
            candidates.append(resURL.appendingPathComponent("bin/\(tool)"))
        }
        // Also support running directly from swift build during development
        let devFallback = URL(fileURLWithPath: "/tmp/vidow-macos-bins/\(tool)")
        candidates.append(devFallback)
        candidates.append(URL(fileURLWithPath: "/opt/homebrew/bin/\(tool)"))
        candidates.append(URL(fileURLWithPath: "/usr/local/bin/\(tool)"))

        return candidates.first { fm.isExecutableFile(atPath: $0.path) }
    }

    func ytDlpVersion() async -> String? {
        guard let bin = resolve("yt-dlp") else { return nil }
        return await runSimpleOutput(bin, args: ["--version"])?.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ffmpegVersion() async -> String? {
        guard let bin = resolve("ffmpeg") else { return nil }
        guard let out = await runSimpleOutput(bin, args: ["-version"]) else { return nil }
        return out.components(separatedBy: .newlines).first?.replacingOccurrences(of: "ffmpeg version ", with: "").components(separatedBy: " ").first
    }

    func updateYtDlp(useNightly: Bool) async -> YtDlpUpdateResult {
        let downloadURLString = useNightly
            ? "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp_macos"
            : "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"

        guard let url = URL(string: downloadURLString) else {
            return .failed("Invalid update URL")
        }

        let previousVersion = await ytDlpVersion()

        do {
            let (tempURL, response) = try await URLSession.shared.download(from: url)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                return .failed("HTTP \(http.statusCode)")
            }

            let target = userBinDir.appendingPathComponent("yt-dlp")
            let staging = userBinDir.appendingPathComponent("yt-dlp.new")
            let fm = FileManager.default
            try? fm.removeItem(at: staging)
            try fm.moveItem(at: tempURL, to: staging)
            try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: staging.path)

            // Verify binary executes cleanly before replacing
            guard let newVer = await runSimpleOutput(staging, args: ["--version"])?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !newVer.isEmpty else {
                try? fm.removeItem(at: staging)
                return .failed("Downloaded binary failed verification")
            }

            try? fm.removeItem(at: target)
            try fm.moveItem(at: staging, to: target)

            let label = useNightly ? "Nightly \(newVer)" : newVer
            if previousVersion == newVer {
                return .upToDate(label)
            }
            return .updated(label)
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    func runSimpleOutput(_ executable: URL, args: [String]) async -> String? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                let proc = Process()
                proc.executableURL = executable
                proc.arguments = args
                let pipe = Pipe()
                proc.standardOutput = pipe
                proc.standardError = FileHandle.nullDevice
                do {
                    try proc.run()
                    let data = pipe.fileHandleForReading.readDataToEndOfFile()
                    proc.waitUntilExit()
                    guard proc.terminationStatus == 0 else {
                        continuation.resume(returning: nil)
                        return
                    }
                    continuation.resume(returning: String(data: data, encoding: .utf8))
                } catch {
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}
