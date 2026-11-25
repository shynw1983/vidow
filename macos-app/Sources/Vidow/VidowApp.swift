import AppKit
import SwiftUI

final class VidowAppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }
}

@main
struct VidowMacApp: App {
    @NSApplicationDelegateAdaptor(VidowAppDelegate.self) private var appDelegate
    @StateObject private var vm = VidowViewModel()

    init() {
        if CommandLine.arguments.contains("--self-test") {
            VidowSelfTest.runAllAndExit()
        }
    }

    var body: some Scene {
        WindowGroup("Vidow") {
            VidowMainShell(vm: vm)
                .frame(minWidth: 900, minHeight: 640)
        }
        .defaultSize(width: 1080, height: 740)
        .windowStyle(.hiddenTitleBar)
        .commands {
            CommandGroup(after: .newItem) {
                Button(L10n.t("conv_pick_file")) {
                    vm.pickVideoFile()
                }
                .keyboardShortcut("o", modifiers: .command)

                Button("\(L10n.t("dl_paste")) & \(L10n.t("dl_fetch"))") {
                    vm.selectedSection = .download
                    vm.pasteFromClipboard(andFetch: true)
                }
                .keyboardShortcut("v", modifiers: [.command, .shift])
            }
        }
    }
}

struct VidowMainShell: View {
    @ObservedObject var vm: VidowViewModel

    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 14) {
                // Brand Wordmark
                VStack(alignment: .leading, spacing: 6) {
                    VidowWordmarkBadge()
                    Text(L10n.t("app_tagline"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.leading, 4)
                }
                .padding(.top, 12)
                .padding(.horizontal, 14)

                // Navigation Items
                List(SidebarSection.allCases, selection: $vm.selectedSection) { section in
                    Label(section.title, systemImage: section.icon)
                        .font(.system(size: 13.5, weight: .medium))
                        .padding(.vertical, 4)
                        .tag(section)
                }
                .listStyle(.sidebar)

                Spacer()

                // Bottom Utility Cards: Cookies & Engine
                VStack(spacing: 8) {
                    Button {
                        vm.showCookieSheet = true
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "key.fill")
                                .foregroundStyle(VidowColors.brandBlue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L10n.t("sidebar_cookies"))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.primary)
                                Text(vm.loggedInSiteIDs.isEmpty
                                     ? L10n.t("sidebar_cookies_none")
                                     : L10n.f("sidebar_cookies_count", vm.loggedInSiteIDs.count))
                                    .font(.caption2)
                                    .foregroundStyle(vm.loggedInSiteIDs.isEmpty ? Color.secondary : Color.green)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.05)))
                    }
                    .buttonStyle(.plain)

                    Button {
                        vm.showEngineSheet = true
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "terminal.fill")
                                .foregroundStyle(VidowColors.brandCyan)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L10n.t("sidebar_engine"))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.primary)
                                Text(vm.ytDlpVersion ?? L10n.t("sidebar_engine_missing"))
                                    .font(.caption2.monospaced())
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.05)))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 14)
            }
            .navigationSplitViewColumnWidth(min: 210, ideal: 230, max: 260)
        } detail: {
            Group {
                switch vm.selectedSection {
                case .download:
                    DownloadWorkspaceView(vm: vm)
                case .convert:
                    ConvertWorkspaceView(vm: vm)
                case .history:
                    HistoryWorkspaceView(vm: vm)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(nsColor: .windowBackgroundColor))
        }
        .sheet(isPresented: $vm.showCookieSheet) {
            CookieManagerSheet(vm: vm)
        }
        .sheet(item: $vm.activeLoginSite) { site in
            SiteWebLoginSheet(
                site: site,
                onSave: { vm.saveCookies(for: site) },
                onCancel: { vm.activeLoginSite = nil }
            )
        }
        .sheet(isPresented: $vm.showEngineSheet) {
            EngineSettingsSheet(vm: vm)
        }
    }
}
