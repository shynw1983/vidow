import AppKit
import Foundation
import SwiftUI

@MainActor
final class VidowViewModel: ObservableObject {
    @Published var selectedSection: SidebarSection = .download

    // Download State
    @Published var urlInput: String = ""
    @Published var parsedLink: ParsedLinkInfo? = nil
    @Published var formatMode: DownloadFormatMode
    @Published var selectedMaxHeight: Int? = nil
    @Published var embedSubtitles: Bool
    @Published var subtitlePreset: SubtitlePreset
    @Published var includeAutoSubtitles: Bool
    @Published var selectedSubtitleLangs: Set<String> = []
    @Published var autoConvertAfterDownload: Bool
    @Published var downloadState: DownloadState = .idle
    @Published var customDownloadFolder: URL?

    // Engine & Cookies
    @Published var ytDlpVersion: String? = nil
    @Published var ffmpegVersion: String? = nil
    @Published var useNightlyChannel: Bool
    @Published var isUpdatingEngine: Bool = false
    @Published var engineUpdateMessage: String? = nil
    @Published var loggedInSiteIDs: Set<String> = []
    @Published var cookieNotice: String? = nil

    // Convert State
    @Published var isInspectingVideo: Bool = false
    @Published var inspectError: String? = nil
    @Published var videoInfo: LocalVideoInfo? = nil
    @Published var conversionConfig: ConversionConfig = ConversionConfig()
    @Published var conversionState: ConversionState = .idle

    // History
    @Published var recentDownloads: [DownloadHistoryItem] = []
    @Published var recentConversions: [ConversionHistoryItem] = []

    // Sheets
    @Published var showCookieSheet: Bool = false
    @Published var activeLoginSite: CookieSite? = nil
    @Published var showEngineSheet: Bool = false

    private var activeDownloadTask: Task<Void, Never>?
    private var activeConversionTask: Task<Void, Never>?

    var encodingPlan: EncodingPlan? {
        guard let info = videoInfo else { return nil }
        return BitrateCalculator.buildPlan(info: info, config: conversionConfig)
    }

    var effectiveDownloadDirectory: URL {
        if let custom = customDownloadFolder {
            return custom
        }
        let home = FileManager.default.homeDirectoryForCurrentUser
        if formatMode == .audioM4A {
            return home.appendingPathComponent("Music/Vidow", isDirectory: true)
        }
        return home.appendingPathComponent("Movies/Vidow", isDirectory: true)
    }

    var effectiveConvertDirectory: URL {
        FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Movies/Vidow", isDirectory: true)
    }

    init() {
        let prefs = HistoryStore.shared.loadPrefs()
        self.formatMode = prefs.formatMode
        self.embedSubtitles = prefs.embedSubtitles
        self.subtitlePreset = prefs.subtitlePreset
        self.includeAutoSubtitles = prefs.includeAutoSubtitles
        self.autoConvertAfterDownload = prefs.autoConvertAfterDownload
        self.useNightlyChannel = prefs.useNightlyChannel
        self.customDownloadFolder = prefs.customDownloadFolder.map { URL(fileURLWithPath: $0) }
        self.recentDownloads = HistoryStore.shared.loadDownloads()
        self.recentConversions = HistoryStore.shared.loadConversions()
        self.loggedInSiteIDs = SiteCookieManager.shared.loggedInSiteIDs()

        Task {
            await refreshEngineVersions()
        }
    }

    func refreshEngineVersions() async {
        async let yt = BinaryManager.shared.ytDlpVersion()
        async let ff = BinaryManager.shared.ffmpegVersion()
        ytDlpVersion = await yt
        ffmpegVersion = await ff
    }

    func persistPrefs() {
        let prefs = SavedAppPrefs(
            formatMode: formatMode,
            embedSubtitles: embedSubtitles,
            subtitlePreset: subtitlePreset == .custom ? .multi4 : subtitlePreset,
            includeAutoSubtitles: includeAutoSubtitles,
            autoConvertAfterDownload: autoConvertAfterDownload,
            useNightlyChannel: useNightlyChannel,
            customDownloadFolder: customDownloadFolder?.path
        )
        HistoryStore.shared.savePrefs(prefs)
    }

    // MARK: - Download Actions

    func pasteFromClipboard(andFetch: Bool = false) {
        if let str = NSPasteboard.general.string(forType: .string), !str.isEmpty {
            urlInput = str.trimmingCharacters(in: .whitespacesAndNewlines)
            if andFetch {
                parseCurrentURL()
            }
        }
    }

    func clearLink() {
        urlInput = ""
        parsedLink = nil
        selectedMaxHeight = nil
        selectedSubtitleLangs = []
        downloadState = .idle
    }

    func parseCurrentURL() {
        let raw = urlInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let extracted = YtDlpService.extractFirstURL(from: raw) ?? raw
        guard !extracted.isEmpty else {
            downloadState = .failed(L10n.t("dl_err_empty_url"))
            return
        }
        urlInput = extracted
        parsedLink = nil
        selectedMaxHeight = nil
        selectedSubtitleLangs = []
        downloadState = .parsing

        Task {
            do {
                let info = try await YtDlpService.shared.parseURL(
                    extracted,
                    cookiesFile: SiteCookieManager.shared.combinedCookiesFile()
                )
                parsedLink = info
                if subtitlePreset == .custom && !info.hasAnySubtitles {
                    subtitlePreset = .multi4
                }
                downloadState = .idle
            } catch {
                downloadState = .failed(error.localizedDescription)
            }
        }
    }

    func selectSubtitlePreset(_ preset: SubtitlePreset) {
        subtitlePreset = preset
        if preset == .custom && selectedSubtitleLangs.isEmpty, let parsed = parsedLink {
            let candidates = !parsed.officialSubtitles.isEmpty ? parsed.officialSubtitles : parsed.autoSubtitles
            let preferred = candidates.filter { track in
                let base = track.langCode.lowercased().components(separatedBy: CharacterSet(charactersIn: "-_")).first ?? ""
                return ["zh", "en", "ja", "ko"].contains(base)
            }.prefix(2).map(\.langCode)
            selectedSubtitleLangs = Set(preferred.isEmpty ? candidates.prefix(1).map(\.langCode) : preferred)
        }
        if preset != .custom {
            persistPrefs()
        }
    }

    func toggleSubtitleLang(_ code: String) {
        subtitlePreset = .custom
        if selectedSubtitleLangs.contains(code) {
            selectedSubtitleLangs.remove(code)
        } else {
            selectedSubtitleLangs.insert(code)
        }
    }

    func chooseDownloadFolder() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.canCreateDirectories = true
        panel.directoryURL = effectiveDownloadDirectory
        if panel.runModal() == .OK, let url = panel.url {
            customDownloadFolder = url
            persistPrefs()
        }
    }

    func startDownload() {
        let url = parsedLink?.url ?? YtDlpService.extractFirstURL(from: urlInput) ?? urlInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty else {
            downloadState = .failed(L10n.t("dl_err_empty_url"))
            return
        }

        let mode = formatMode
        let maxH = selectedMaxHeight
        let embedSubs = embedSubtitles
        let subPreset = subtitlePreset
        let subLangs = selectedSubtitleLangs
        let incAuto = includeAutoSubtitles
        let hasOfficial = parsedLink.map { !$0.officialSubtitles.isEmpty } ?? true
        let selectedHasAuto = parsedLink?.autoSubtitles.contains(where: { subLangs.contains($0.langCode) }) ?? false
        let destDir = effectiveDownloadDirectory
        let parsedTitle = parsedLink?.title
        let thumbUrl = parsedLink?.thumbnailUrl
        let autoConv = autoConvertAfterDownload

        downloadState = .downloading(percent: 0, etaSeconds: 0, statusLine: L10n.t("dl_connecting"))

        activeDownloadTask?.cancel()
        activeDownloadTask = Task {
            do {
                let outputURL = try await YtDlpService.shared.download(
                    url: url,
                    mode: mode,
                    selectedMaxHeight: maxH,
                    embedSubtitles: embedSubs,
                    subtitlePreset: subPreset,
                    selectedSubtitleLangs: subLangs,
                    includeAutoSubtitles: incAuto,
                    hasOfficialSubtitles: hasOfficial,
                    selectedIncludesAutoTrack: selectedHasAuto,
                    cookiesFile: SiteCookieManager.shared.combinedCookiesFile(),
                    destinationDir: destDir
                ) { pct, eta, line in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        if case .downloading(let curPct, let curEta, _) = self.downloadState {
                            let nextPct = pct >= 0 ? pct : curPct
                            let nextEta = eta > 0 ? eta : curEta
                            let clean = String(line.trimmingCharacters(in: .whitespacesAndNewlines).prefix(96))
                            self.downloadState = .downloading(percent: nextPct, etaSeconds: nextEta, statusLine: clean)
                        }
                    }
                }

                let size = (try? outputURL.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
                let item = DownloadHistoryItem(
                    filePath: outputURL.path,
                    fileName: outputURL.lastPathComponent,
                    title: parsedTitle ?? outputURL.deletingPathExtension().lastPathComponent,
                    sizeBytes: size,
                    isAudioOnly: mode == .audioM4A,
                    thumbnailUrl: thumbUrl,
                    date: Date()
                )
                recentDownloads = [item] + recentDownloads.filter { $0.filePath != item.filePath }
                HistoryStore.shared.saveDownloads(recentDownloads)
                downloadState = .success(item)

                if autoConv && mode != .audioM4A {
                    openInConverter(url: outputURL)
                }
            } catch {
                if case YtDlpError.cancelled = error {
                    downloadState = .idle
                } else {
                    downloadState = .failed(error.localizedDescription)
                }
            }
        }
    }

    func cancelDownload() {
        YtDlpService.shared.cancelActiveDownload()
        activeDownloadTask?.cancel()
        downloadState = .idle
    }

    func dismissDownloadOverlay() {
        downloadState = .idle
    }

    // MARK: - Cookies & Engine

    func saveCookies(for site: CookieSite) {
        Task {
            let count = await SiteCookieManager.shared.captureFromWebView(site: site)
            loggedInSiteIDs = SiteCookieManager.shared.loggedInSiteIDs()
            cookieNotice = count > 0
                ? L10n.f("cookie_saved_msg", count, site.displayName)
                : L10n.f("cookie_empty_msg", site.displayName)
            activeLoginSite = nil
        }
    }

    func clearCookies(for site: CookieSite) {
        SiteCookieManager.shared.clearSite(site)
        loggedInSiteIDs = SiteCookieManager.shared.loggedInSiteIDs()
        cookieNotice = L10n.f("cookie_cleared_msg", site.displayName)
    }

    func clearAllCookies() {
        Task {
            await SiteCookieManager.shared.clearAll()
            loggedInSiteIDs = []
            cookieNotice = L10n.t("cookie_cleared_all_msg")
        }
    }

    func updateYtDlpEngine() {
        guard !isUpdatingEngine else { return }
        isUpdatingEngine = true
        engineUpdateMessage = nil
        let nightly = useNightlyChannel
        Task {
            let res = await BinaryManager.shared.updateYtDlp(useNightly: nightly)
            await refreshEngineVersions()
            isUpdatingEngine = false
            switch res {
            case .updated(let v):
                engineUpdateMessage = L10n.f("engine_updated", v)
            case .upToDate(let v):
                engineUpdateMessage = L10n.f("engine_uptodate", v)
            case .failed(let msg):
                engineUpdateMessage = L10n.f("engine_failed", msg)
            }
        }
    }

    // MARK: - Converter Actions

    func pickVideoFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.movie, .mpeg4Movie, .quickTimeMovie, .avi, .video]
        if panel.runModal() == .OK, let url = panel.url {
            openInConverter(url: url)
        }
    }

    func openInConverter(url: URL) {
        selectedSection = .convert
        downloadState = .idle
        conversionState = .idle
        inspectError = nil
        isInspectingVideo = true

        Task {
            do {
                let info = try await VideoConverterService.shared.inspectVideo(at: url)
                videoInfo = info
                conversionConfig = ConversionConfig.forPreset(.universalCompat, duration: info.durationSeconds)
                isInspectingVideo = false
            } catch {
                inspectError = error.localizedDescription
                isInspectingVideo = false
            }
        }
    }

    func applyPreset(_ preset: ConversionPreset) {
        let dur = videoInfo?.durationSeconds ?? 0
        conversionConfig = ConversionConfig.forPreset(preset, duration: dur)
    }

    func startConversion() {
        guard let info = videoInfo, let plan = encodingPlan else { return }
        let cfg = conversionConfig
        let codecName = cfg.videoCodec == .hevc ? "HEVC VideoToolbox" : "H.264 VideoToolbox"
        conversionState = .converting(percent: 0, codecLabel: codecName)

        activeConversionTask?.cancel()
        activeConversionTask = Task {
            do {
                let result = try await VideoConverterService.shared.convert(
                    info: info,
                    config: cfg,
                    plan: plan,
                    destinationDir: effectiveConvertDirectory
                ) { pct in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        if case .converting(_, let c) = self.conversionState {
                            self.conversionState = .converting(percent: pct, codecLabel: c)
                        }
                    }
                }
                recentConversions = [result] + recentConversions.filter { $0.outputPath != result.outputPath }
                HistoryStore.shared.saveConversions(recentConversions)
                conversionState = .success(result)
            } catch {
                if case ConverterError.cancelled = error {
                    conversionState = .idle
                } else {
                    conversionState = .failed(error.localizedDescription)
                }
            }
        }
    }

    func cancelConversion() {
        VideoConverterService.shared.cancelActiveConversion()
        activeConversionTask?.cancel()
        conversionState = .idle
    }

    func clearSelectedVideo() {
        videoInfo = nil
        inspectError = nil
        conversionState = .idle
    }

    // MARK: - History & Finder Utilities

    func revealInFinder(path: String) {
        let url = URL(fileURLWithPath: path)
        if FileManager.default.fileExists(atPath: path) {
            NSWorkspace.shared.activateFileViewerSelecting([url])
        }
    }

    func openFile(path: String) {
        let url = URL(fileURLWithPath: path)
        if FileManager.default.fileExists(atPath: path) {
            NSWorkspace.shared.open(url)
        }
    }

    func removeDownloadHistory(_ item: DownloadHistoryItem) {
        recentDownloads.removeAll { $0.filePath == item.filePath }
        HistoryStore.shared.saveDownloads(recentDownloads)
    }

    func clearDownloadHistory() {
        recentDownloads = []
        HistoryStore.shared.saveDownloads([])
    }

    func removeConversionHistory(_ item: ConversionHistoryItem) {
        recentConversions.removeAll { $0.outputPath == item.outputPath }
        HistoryStore.shared.saveConversions(recentConversions)
    }

    func clearConversionHistory() {
        recentConversions = []
        HistoryStore.shared.saveConversions([])
    }
}
