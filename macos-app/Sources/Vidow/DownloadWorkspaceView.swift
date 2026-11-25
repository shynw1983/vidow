import SwiftUI

struct DownloadWorkspaceView: View {
    @ObservedObject var vm: VidowViewModel
    @State private var showAllOfficialSubs = false
    @State private var showAllAutoSubs = false

    var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    // Header
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L10n.t("dl_title"))
                            .font(.system(size: 24, weight: .bold))
                        Text(L10n.t("dl_subtitle"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    // 1. URL Input Card
                    BentoCard {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 10) {
                                TextField(L10n.t("dl_url_placeholder"), text: $vm.urlInput)
                                    .textFieldStyle(.roundedBorder)
                                    .font(.system(size: 13.5))
                                    .onSubmit { vm.parseCurrentURL() }

                                if !vm.urlInput.isEmpty {
                                    Button(L10n.t("dl_clear")) {
                                        vm.clearLink()
                                    }
                                    .buttonStyle(.bordered)
                                }

                                Button {
                                    vm.pasteFromClipboard(andFetch: false)
                                } label: {
                                    Label(L10n.t("dl_paste"), systemImage: "doc.on.clipboard")
                                }
                                .buttonStyle(.bordered)

                                Button {
                                    vm.parseCurrentURL()
                                } label: {
                                    if case .parsing = vm.downloadState {
                                        HStack(spacing: 6) {
                                            ProgressView().controlSize(.small)
                                            Text(L10n.t("dl_fetching"))
                                        }
                                    } else {
                                        Label(L10n.t("dl_fetch"), systemImage: "magnifyingglass")
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(VidowColors.brandBlue)
                                .disabled(vm.urlInput.trimmingCharacters(in: .whitespaces).isEmpty || isParsing)
                            }

                            // Save folder compact strip
                            HStack(spacing: 8) {
                                Image(systemName: "folder.fill")
                                    .foregroundStyle(.secondary)
                                    .font(.caption)
                                Text("\(L10n.t("dl_save_folder")):")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(vm.effectiveDownloadDirectory.path.replacingOccurrences(of: FileManager.default.homeDirectoryForCurrentUser.path, with: "~"))
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                                Spacer()
                                Button(L10n.t("dl_choose_folder")) {
                                    vm.chooseDownloadFolder()
                                }
                                .font(.caption)
                                .buttonStyle(.link)
                            }
                        }
                    }

                    // 2. Parsed Video Card
                    if let parsed = vm.parsedLink {
                        parsedVideoCard(parsed)
                    }

                    // 3. Inline Error Banner (if failed outside active download)
                    if case .failed(let msg) = vm.downloadState, vm.parsedLink == nil {
                        HStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                            Text(msg)
                                .font(.callout)
                                .foregroundStyle(.primary)
                            Spacer()
                            Button(L10n.t("dl_close")) { vm.dismissDownloadOverlay() }
                                .buttonStyle(.bordered)
                        }
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.orange.opacity(0.12)))
                    }
                }
                .padding(24)
            }

            // Focus Overlay for active Download / Result / Error
            downloadOverlay
        }
    }

    private var isParsing: Bool {
        if case .parsing = vm.downloadState { return true }
        return false
    }

    @ViewBuilder
    private func parsedVideoCard(_ parsed: ParsedLinkInfo) -> some View {
        BentoCard(accentBorder: true) {
            VStack(alignment: .leading, spacing: 16) {
                // Top Media Preview + Metadata Row
                HStack(alignment: .top, spacing: 16) {
                    ZStack(alignment: .topLeading) {
                        Group {
                            if let thumbStr = parsed.thumbnailUrl, let url = URL(string: thumbStr) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().aspectRatio(contentMode: .fill)
                                    default:
                                        Rectangle().fill(Color.primary.opacity(0.08))
                                            .overlay(Image(systemName: "film").font(.title).foregroundStyle(.secondary))
                                    }
                                }
                            } else {
                                Rectangle().fill(Color.primary.opacity(0.08))
                                    .overlay(Image(systemName: "film").font(.title).foregroundStyle(.secondary))
                            }
                        }
                        .frame(width: 240, height: 135)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                        Text(parsed.extractor)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 6))
                            .padding(8)

                        if parsed.durationSeconds > 0 {
                            VStack {
                                Spacer()
                                HStack {
                                    Spacer()
                                    Text(formatDuration(Double(parsed.durationSeconds)))
                                        .font(.system(size: 11, weight: .semibold).monospacedDigit())
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 3)
                                        .background(Color.black.opacity(0.78), in: RoundedRectangle(cornerRadius: 6))
                                        .padding(8)
                                }
                            }
                            .frame(width: 240, height: 135)
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(parsed.title)
                            .font(.system(size: 16, weight: .bold))
                            .lineLimit(2)

                        Text(L10n.f("dl_author", parsed.uploader))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        specPillsRow(parsed)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Divider()

                // Download Mode Selection
                VStack(alignment: .leading, spacing: 8) {
                    Text(L10n.t("dl_type_label"))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)

                    WrapFlowLayout(spacing: 8, rowSpacing: 8) {
                        ForEach(DownloadFormatMode.allCases) { mode in
                            SelectableChip(label: mode.label, isSelected: vm.formatMode == mode) {
                                vm.formatMode = mode
                                vm.persistPrefs()
                            }
                        }
                    }

                    Text(vm.formatMode.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                // Quality Selector (Video modes)
                if vm.formatMode != .audioM4A {
                    let qualities = parsed.qualities(for: vm.formatMode)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.t("dl_quality_label"))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)

                        WrapFlowLayout(spacing: 8, rowSpacing: 8) {
                            let bestHint = qualities.first.map { " (\($0.localizedLabel))" } ?? ""
                            SelectableChip(
                                label: L10n.t("dl_quality_best") + bestHint,
                                isSelected: vm.selectedMaxHeight == nil
                            ) {
                                vm.selectedMaxHeight = nil
                            }

                            ForEach(qualities) { q in
                                let sizeStr = q.approxSizeBytes.map { " · \(formatFileSize($0))" } ?? ""
                                SelectableChip(
                                    label: "\(q.localizedLabel)\(sizeStr)",
                                    isSelected: vm.selectedMaxHeight == q.ytDlpHeight
                                ) {
                                    vm.selectedMaxHeight = q.ytDlpHeight
                                }
                            }
                        }
                    }

                    Divider()

                    // Combined Subtitle Controls
                    subtitleControlsSection(parsed)

                    // Auto-convert Toggle
                    Toggle(isOn: Binding(
                        get: { vm.autoConvertAfterDownload },
                        set: { vm.autoConvertAfterDownload = $0; vm.persistPrefs() }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.t("dl_auto_convert")).font(.callout.weight(.semibold))
                            Text(L10n.t("dl_auto_convert_sub")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .toggleStyle(.switch)
                }

                // Primary Download CTA
                Button {
                    vm.startDownload()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.down.circle.fill")
                        Text(vm.formatMode == .audioM4A ? L10n.t("dl_start_audio") : L10n.t("dl_start_video"))
                            .font(.system(size: 14.5, weight: .bold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                }
                .buttonStyle(.borderedProminent)
                .tint(VidowColors.brandBlue)
                .controlSize(.large)
            }
        }
    }

    @ViewBuilder
    private func specPillsRow(_ parsed: ParsedLinkInfo) -> some View {
        let activeQ = parsed.activeQuality(mode: vm.formatMode, selectedMaxHeight: vm.selectedMaxHeight)
        WrapFlowLayout(spacing: 6, rowSpacing: 6) {
            if vm.formatMode == .audioM4A {
                SpecPill(text: "M4A")
                SpecPill(text: parsed.bestAudioCodec)
                if parsed.bestAudioBitrateKbps > 0 {
                    SpecPill(text: "\(parsed.bestAudioBitrateKbps) kbps")
                }
                if let size = parsed.bestAudioSizeBytes {
                    SpecPill(text: "~\(formatFileSize(size))")
                }
            } else if let q = activeQ {
                if q.width > 0 && q.height > 0 {
                    SpecPill(text: "\(q.width)×\(q.height)")
                }
                SpecPill(text: q.ext)
                if let vc = q.videoCodec { SpecPill(text: vc) }
                if let ac = q.audioCodec { SpecPill(text: ac) }
                if q.fps > 0 { SpecPill(text: "\(q.fps) fps") }
                if q.bitrateKbps > 0 {
                    let br = q.bitrateKbps >= 1000
                        ? String(format: "%.1f Mbps", Double(q.bitrateKbps) / 1000.0)
                        : "\(q.bitrateKbps) kbps"
                    SpecPill(text: br)
                }
                if let size = q.approxSizeBytes {
                    SpecPill(text: "~\(formatFileSize(size))")
                }
                let nonUniversal = ["AV1", "VP9", "H.265"].contains(q.videoCodec ?? "") || ["WEBM", "MKV"].contains(q.ext)
                if nonUniversal {
                    SpecPill(text: "⚠ \(L10n.t("dl_non_universal"))", warning: true)
                }
            } else {
                SpecPill(text: parsed.defaultExt)
                if let vc = parsed.defaultVideoCodec { SpecPill(text: vc) }
                SpecPill(text: parsed.bestAudioCodec)
            }
        }
    }

    @ViewBuilder
    private func subtitleControlsSection(_ parsed: ParsedLinkInfo) -> some View {
        let subDesc: String = {
            if !parsed.officialSubtitles.isEmpty && !parsed.autoSubtitles.isEmpty {
                return L10n.f("dl_sub_detected_both", parsed.officialSubtitles.count)
            } else if !parsed.officialSubtitles.isEmpty {
                return L10n.f("dl_sub_detected_official", parsed.officialSubtitles.count)
            } else if !parsed.autoSubtitles.isEmpty {
                return L10n.t("dl_sub_detected_auto_only")
            }
            return L10n.t("dl_embed_subs_sub")
        }()

        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: Binding(
                get: { vm.embedSubtitles },
                set: { vm.embedSubtitles = $0; vm.persistPrefs() }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.t("dl_embed_subs")).font(.callout.weight(.semibold))
                    Text(subDesc).font(.caption).foregroundStyle(.secondary)
                }
            }
            .toggleStyle(.switch)

            if vm.embedSubtitles {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L10n.t("dl_sub_strategy"))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)

                    let presets = parsed.hasAnySubtitles
                        ? SubtitlePreset.allCases
                        : SubtitlePreset.allCases.filter { $0 != .custom }

                    WrapFlowLayout(spacing: 8, rowSpacing: 8) {
                        ForEach(presets) { preset in
                            let suffix = (preset == .custom && !vm.selectedSubtitleLangs.isEmpty)
                                ? " (\(vm.selectedSubtitleLangs.count))"
                                : ""
                            SelectableChip(label: preset.label + suffix, isSelected: vm.subtitlePreset == preset) {
                                vm.selectSubtitlePreset(preset)
                            }
                        }
                    }

                    if vm.subtitlePreset == .custom {
                        if !parsed.officialSubtitles.isEmpty {
                            Text(L10n.t("dl_sub_official_sec"))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                            let limit = 10
                            let list = (showAllOfficialSubs || parsed.officialSubtitles.count <= limit)
                                ? parsed.officialSubtitles
                                : Array(parsed.officialSubtitles.prefix(limit))
                            WrapFlowLayout(spacing: 6, rowSpacing: 6) {
                                ForEach(list) { track in
                                    SelectableChip(
                                        label: track.chipLabel,
                                        isSelected: vm.selectedSubtitleLangs.contains(track.langCode)
                                    ) {
                                        vm.toggleSubtitleLang(track.langCode)
                                    }
                                }
                                if !showAllOfficialSubs && parsed.officialSubtitles.count > limit {
                                    SelectableChip(
                                        label: L10n.f("dl_sub_more", parsed.officialSubtitles.count - limit),
                                        isSelected: false,
                                        accentText: true
                                    ) {
                                        showAllOfficialSubs = true
                                    }
                                }
                            }
                        }

                        if !parsed.autoSubtitles.isEmpty && (vm.includeAutoSubtitles || parsed.officialSubtitles.isEmpty) {
                            Text(L10n.t("dl_sub_auto_sec"))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                            let limit = 8
                            let list = (showAllAutoSubs || parsed.autoSubtitles.count <= limit)
                                ? parsed.autoSubtitles
                                : Array(parsed.autoSubtitles.prefix(limit))
                            WrapFlowLayout(spacing: 6, rowSpacing: 6) {
                                ForEach(list) { track in
                                    SelectableChip(
                                        label: track.chipLabel,
                                        isSelected: vm.selectedSubtitleLangs.contains(track.langCode)
                                    ) {
                                        vm.toggleSubtitleLang(track.langCode)
                                    }
                                }
                                if !showAllAutoSubs && parsed.autoSubtitles.count > limit {
                                    SelectableChip(
                                        label: L10n.f("dl_sub_more", parsed.autoSubtitles.count - limit),
                                        isSelected: false,
                                        accentText: true
                                    ) {
                                        showAllAutoSubs = true
                                    }
                                }
                            }
                        }
                    } else if !parsed.officialSubtitles.isEmpty {
                        let names = parsed.officialSubtitles.prefix(5).map(\.displayName).joined(separator: ", ")
                        let suffix = parsed.officialSubtitles.count > 5 ? "…" : ""
                        Text(L10n.f("dl_sub_preview", names + suffix))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }

                    if vm.subtitlePreset != .allOfficial || parsed.officialSubtitles.isEmpty {
                        Divider()
                        Toggle(isOn: Binding(
                            get: { vm.includeAutoSubtitles },
                            set: { vm.includeAutoSubtitles = $0; vm.persistPrefs() }
                        )) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L10n.t("dl_sub_include_auto")).font(.caption.weight(.semibold))
                                Text(L10n.t("dl_sub_include_auto_sub")).font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                        .toggleStyle(.switch)
                    }
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.primary.opacity(0.04))
                )
            }
        }
    }

    @ViewBuilder
    private var downloadOverlay: some View {
        switch vm.downloadState {
        case .downloading(let pct, let eta, let line):
            modalBackdrop {
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .stroke(Color.primary.opacity(0.1), lineWidth: 8)
                            .frame(width: 96, height: 96)
                        Circle()
                            .trim(from: 0, to: CGFloat(pct) / 100.0)
                            .stroke(VidowColors.brandGradient, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .frame(width: 96, height: 96)
                            .animation(.easeInOut(duration: 0.25), value: pct)
                        Text("\(pct)%")
                            .font(.system(size: 22, weight: .bold).monospacedDigit())
                    }

                    Text(L10n.t("dl_downloading"))
                        .font(.headline)

                    if eta > 0 {
                        Text(L10n.f("dl_eta", eta))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Text(line)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    Button(L10n.t("dl_cancel"), role: .destructive) {
                        vm.cancelDownload()
                    }
                    .buttonStyle(.bordered)
                }
                .frame(width: 360)
            }

        case .success(let item):
            modalBackdrop {
                VStack(spacing: 14) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 46))
                        .foregroundStyle(.green)

                    Text(L10n.t("dl_done_title"))
                        .font(.title3.bold())

                    Text(item.fileName)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    SpecPill(text: formatFileSize(item.sizeBytes))

                    HStack(spacing: 10) {
                        Button {
                            vm.revealInFinder(path: item.filePath)
                        } label: {
                            Label(L10n.t("dl_reveal_finder"), systemImage: "folder")
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(VidowColors.brandBlue)

                        Button {
                            vm.openFile(path: item.filePath)
                        } label: {
                            Label(L10n.t("dl_play"), systemImage: "play.fill")
                        }
                        .buttonStyle(.bordered)
                    }

                    if !item.isAudioOnly {
                        Button {
                            vm.openInConverter(url: URL(fileURLWithPath: item.filePath))
                        } label: {
                            Label(L10n.t("dl_go_convert"), systemImage: "wand.and.stars")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }

                    Button(L10n.t("dl_close")) {
                        vm.dismissDownloadOverlay()
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
                }
                .frame(width: 380)
            }

        case .failed(let msg) where vm.parsedLink != nil:
            modalBackdrop {
                VStack(spacing: 14) {
                    Image(systemName: "xmark.octagon.fill")
                        .font(.system(size: 42))
                        .foregroundStyle(.red)
                    Text(L10n.t("dl_failed_title")).font(.title3.bold())
                    Text(msg)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(5)
                    HStack(spacing: 10) {
                        Button(L10n.t("dl_retry")) { vm.startDownload() }
                            .buttonStyle(.borderedProminent)
                            .tint(VidowColors.brandBlue)
                        Button(L10n.t("dl_close")) { vm.dismissDownloadOverlay() }
                            .buttonStyle(.bordered)
                    }
                }
                .frame(width: 380)
            }

        default:
            EmptyView()
        }
    }

    private func modalBackdrop<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ZStack {
            Color.black.opacity(0.42).ignoresSafeArea()
            content()
                .padding(24)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.primary.opacity(0.14), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.28), radius: 24, y: 12)
        }
    }
}
