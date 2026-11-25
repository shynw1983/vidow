import Foundation

final class HistoryStore {
    static let shared = HistoryStore()

    private let dir: URL
    private let downloadsFile: URL
    private let conversionsFile: URL
    private let prefsFile: URL
    private let maxItems = 60

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private init() {
        dir = BinaryManager.shared.appSupportDir.appendingPathComponent("history", isDirectory: true)
        downloadsFile = dir.appendingPathComponent("downloads.json")
        conversionsFile = dir.appendingPathComponent("conversions.json")
        prefsFile = dir.appendingPathComponent("prefs.json")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    func loadDownloads() -> [DownloadHistoryItem] {
        guard let data = try? Data(contentsOf: downloadsFile),
              let items = try? decoder.decode([DownloadHistoryItem].self, from: data) else {
            return []
        }
        return items
    }

    func saveDownloads(_ items: [DownloadHistoryItem]) {
        guard let data = try? encoder.encode(Array(items.prefix(maxItems))) else { return }
        try? data.write(to: downloadsFile, options: .atomic)
    }

    func loadConversions() -> [ConversionHistoryItem] {
        guard let data = try? Data(contentsOf: conversionsFile),
              let items = try? decoder.decode([ConversionHistoryItem].self, from: data) else {
            return []
        }
        return items
    }

    func saveConversions(_ items: [ConversionHistoryItem]) {
        guard let data = try? encoder.encode(Array(items.prefix(maxItems))) else { return }
        try? data.write(to: conversionsFile, options: .atomic)
    }

    func loadPrefs() -> SavedAppPrefs {
        guard let data = try? Data(contentsOf: prefsFile),
              var prefs = try? decoder.decode(SavedAppPrefs.self, from: data) else {
            return SavedAppPrefs()
        }
        if prefs.subtitlePreset == .custom {
            prefs.subtitlePreset = .multi4
        }
        return prefs
    }

    func savePrefs(_ prefs: SavedAppPrefs) {
        var sanitized = prefs
        if sanitized.subtitlePreset == .custom {
            sanitized.subtitlePreset = .multi4
        }
        guard let data = try? encoder.encode(sanitized) else { return }
        try? data.write(to: prefsFile, options: .atomic)
    }
}
