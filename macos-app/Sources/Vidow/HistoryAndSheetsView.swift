import SwiftUI
import WebKit

struct HistoryWorkspaceView: View {
    @ObservedObject var vm: VidowViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(L10n.t("hist_title"))
                    .font(.system(size: 24, weight: .bold))

                // Downloads History
                BentoCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Label(L10n.f("hist_downloads", vm.recentDownloads.count), systemImage: "arrow.down.circle.fill")
                                .font(.headline)
                            Spacer()
                            if !vm.recentDownloads.isEmpty {
                                Button(L10n.t("hist_clear")) {
                                    vm.clearDownloadHistory()
                                }
                                .buttonStyle(.link)
                            }
                        }

                        if vm.recentDownloads.isEmpty {
                            Text(L10n.t("hist_empty_dl"))
                                .font(.callout)
                                .foregroundStyle(.secondary)
                                .padding(.vertical, 10)
                        } else {
                            ForEach(vm.recentDownloads) { item in
                                HStack(spacing: 12) {
                                    Group {
                                        if let t = item.thumbnailUrl, let u = URL(string: t) {
                                            AsyncImage(url: u) { phase in
                                                if case .success(let img) = phase {
                                                    img.resizable().aspectRatio(contentMode: .fill)
                                                } else {
                                                    Color.primary.opacity(0.08)
                                                }
                                            }
                                        } else {
                                            Color.primary.opacity(0.08)
                                                .overlay(Image(systemName: item.isAudioOnly ? "music.note" : "film").foregroundStyle(.secondary))
                                        }
                                    }
                                    .frame(width: 72, height: 44)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(item.title)
                                            .font(.callout.weight(.semibold))
                                            .lineLimit(1)
                                        Text("\(item.fileName) · \(formatFileSize(item.sizeBytes))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    if !item.isAudioOnly {
                                        Button(L10n.t("dl_go_convert")) {
                                            vm.openInConverter(url: URL(fileURLWithPath: item.filePath))
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.small)
                                    }
                                    Button {
                                        vm.revealInFinder(path: item.filePath)
                                    } label: {
                                        Image(systemName: "folder")
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)
                                    .help(L10n.t("dl_reveal_finder"))

                                    Button {
                                        vm.openFile(path: item.filePath)
                                    } label: {
                                        Image(systemName: "play.fill")
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)
                                    .help(L10n.t("dl_play"))

                                    Button {
                                        vm.removeDownloadHistory(item)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundStyle(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.vertical, 4)
                                if item.id != vm.recentDownloads.last?.id { Divider() }
                            }
                        }
                    }
                }

                // Conversions History
                BentoCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Label(L10n.f("hist_conversions", vm.recentConversions.count), systemImage: "wand.and.stars")
                                .font(.headline)
                            Spacer()
                            if !vm.recentConversions.isEmpty {
                                Button(L10n.t("hist_clear")) {
                                    vm.clearConversionHistory()
                                }
                                .buttonStyle(.link)
                            }
                        }

                        if vm.recentConversions.isEmpty {
                            Text(L10n.t("hist_empty_conv"))
                                .font(.callout)
                                .foregroundStyle(.secondary)
                                .padding(.vertical, 10)
                        } else {
                            ForEach(vm.recentConversions) { item in
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(item.outputFileName)
                                            .font(.callout.weight(.semibold))
                                            .lineLimit(1)
                                        Text("\(formatFileSize(item.originalSizeBytes)) → \(formatFileSize(item.outputSizeBytes)) · \(item.outputWidth)×\(item.outputHeight) · \(item.codecLabel)")
                                            .font(.caption.monospacedDigit())
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Button {
                                        vm.revealInFinder(path: item.outputPath)
                                    } label: {
                                        Image(systemName: "folder")
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)

                                    Button {
                                        vm.openFile(path: item.outputPath)
                                    } label: {
                                        Image(systemName: "play.fill")
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)

                                    Button {
                                        vm.removeConversionHistory(item)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundStyle(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.vertical, 4)
                                if item.id != vm.recentConversions.last?.id { Divider() }
                            }
                        }
                    }
                }
            }
            .padding(24)
        }
    }
}

// MARK: - Site Cookie Manager Sheet

struct CookieManagerSheet: View {
    @ObservedObject var vm: VidowViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label(L10n.t("cookie_title"), systemImage: "key.fill")
                    .font(.title3.bold())
                Spacer()
                Button(L10n.t("dl_close")) { dismiss() }
                    .buttonStyle(.bordered)
            }

            Text(L10n.t("cookie_desc"))
                .font(.callout)
                .foregroundStyle(.secondary)

            if let notice = vm.cookieNotice {
                Text(notice)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(VidowColors.brandBlue)
            }

            VStack(spacing: 8) {
                ForEach(CookieSite.allCases) { site in
                    let loggedIn = vm.loggedInSiteIDs.contains(site.id)
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(site.displayName).font(.callout.weight(.semibold))
                            Text(loggedIn ? L10n.t("cookie_logged_in") : L10n.t("cookie_not_logged"))
                                .font(.caption)
                                .foregroundStyle(loggedIn ? .green : .secondary)
                        }
                        Spacer()
                        if loggedIn {
                            Button(L10n.t("cookie_clear_site")) {
                                vm.clearCookies(for: site)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                        Button(loggedIn ? L10n.t("cookie_relogin") : L10n.t("cookie_login")) {
                            dismiss()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                vm.activeLoginSite = site
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(VidowColors.brandBlue)
                        .controlSize(.small)
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.04)))
                }
            }

            if !vm.loggedInSiteIDs.isEmpty {
                HStack {
                    Spacer()
                    Button(L10n.t("cookie_clear_all"), role: .destructive) {
                        vm.clearAllCookies()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .padding(22)
        .frame(width: 480)
    }
}

// MARK: - Built-in WKWebView Site Login Sheet

struct SiteWebLoginSheet: View {
    let site: CookieSite
    let onSave: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(site.displayName).font(.headline)
                    Text(L10n.t("cookie_hint")).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button(L10n.t("dl_cancel")) { onCancel() }
                    .buttonStyle(.bordered)
                Button(L10n.t("cookie_save_btn")) { onSave() }
                    .buttonStyle(.borderedProminent)
                    .tint(VidowColors.brandBlue)
            }
            .padding(14)
            .background(.bar)
            Divider()
            NativeWebView(url: site.loginURL)
        }
        .frame(width: 820, height: 620)
    }
}

struct NativeWebView: NSViewRepresentable {
    let url: URL

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
        wv.load(URLRequest(url: url))
        return wv
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {}
}

// MARK: - Engine Update Sheet

struct EngineSettingsSheet: View {
    @ObservedObject var vm: VidowViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label(L10n.t("engine_title"), systemImage: "terminal.fill")
                    .font(.title3.bold())
                Spacer()
                Button(L10n.t("dl_close")) { dismiss() }
                    .buttonStyle(.bordered)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(L10n.f("engine_ytdlp_ver", vm.ytDlpVersion ?? L10n.t("sidebar_engine_missing")))
                    .font(.callout.monospaced())
                Text(L10n.f("engine_ffmpeg_ver", vm.ffmpegVersion ?? L10n.t("sidebar_engine_missing")))
                    .font(.callout.monospaced())
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.05)))

            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.t("engine_channel")).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Picker("", selection: Binding(
                    get: { vm.useNightlyChannel },
                    set: { vm.useNightlyChannel = $0; vm.persistPrefs() }
                )) {
                    Text(L10n.t("engine_stable")).tag(false)
                    Text(L10n.t("engine_nightly")).tag(true)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            if let msg = vm.engineUpdateMessage {
                Text(msg)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(VidowColors.brandBlue)
            }

            Button {
                vm.updateYtDlpEngine()
            } label: {
                HStack(spacing: 8) {
                    if vm.isUpdatingEngine {
                        ProgressView().controlSize(.small)
                        Text(L10n.t("engine_updating"))
                    } else {
                        Image(systemName: "arrow.triangle.2.circlepath")
                        Text(L10n.t("engine_update_btn"))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(VidowColors.brandBlue)
            .controlSize(.large)
            .disabled(vm.isUpdatingEngine)
        }
        .padding(22)
        .frame(width: 440)
    }
}
