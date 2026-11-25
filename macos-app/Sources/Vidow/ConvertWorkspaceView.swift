import AVKit
import SwiftUI
import UniformTypeIdentifiers

struct ConvertWorkspaceView: View {
    @ObservedObject var vm: VidowViewModel
    @State private var isDropTargeted = false
    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        // Header
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(L10n.t("conv_title"))
                                    .font(.system(size: 24, weight: .bold))
                                Text(L10n.t("conv_subtitle"))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if vm.videoInfo != nil {
                                Button {
                                    vm.pickVideoFile()
                                } label: {
                                    Label(L10n.t("conv_switch_file"), systemImage: "folder.badge.plus")
                                }
                                .buttonStyle(.bordered)
                            }
                        }

                        if vm.isInspectingVideo {
                            BentoCard {
                                HStack(spacing: 12) {
                                    ProgressView()
                                    Text(L10n.t("conv_inspecting"))
                                        .font(.headline)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 40)
                            }
                        } else if let info = vm.videoInfo {
                            loadedConverterContent(info: info)
                        } else {
                            dropZoneCard
                        }

                        if let err = vm.inspectError {
                            Text(err)
                                .font(.callout)
                                .foregroundStyle(.red)
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.red.opacity(0.1)))
                        }
                    }
                    .padding(24)
                }

                // Bottom Action Dock when video is loaded
                if let info = vm.videoInfo, let plan = vm.encodingPlan {
                    bottomActionDock(info: info, plan: plan)
                }
            }

            // Conversion Focus Overlay
            conversionOverlay
        }
        .onDrop(of: [.fileURL], isTargeted: $isDropTargeted) { providers in
            handleFileDrop(providers)
        }
        .onChange(of: vm.videoInfo?.url) { _, newURL in
            player?.pause()
            player = newURL.map { AVPlayer(url: $0) }
        }
        .onAppear {
            if player == nil, let url = vm.videoInfo?.url {
                player = AVPlayer(url: url)
            }
        }
        .onDisappear {
            player?.pause()
        }
    }

    private var dropZoneCard: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(VidowColors.brandBlue.opacity(0.14))
                    .frame(width: 72, height: 72)
                Image(systemName: "film.stack")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(VidowColors.brandBlue)
            }

            VStack(spacing: 6) {
                Text(L10n.t("conv_drop_title"))
                    .font(.title3.bold())
                Text(L10n.t("conv_drop_sub"))
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            Button {
                vm.pickVideoFile()
            } label: {
                Label(L10n.t("conv_pick_file"), systemImage: "plus.circle.fill")
                    .font(.system(size: 14, weight: .semibold))
            }
            .buttonStyle(.borderedProminent)
            .tint(VidowColors.brandBlue)
            .controlSize(.large)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 72)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(isDropTargeted ? VidowColors.brandBlue.opacity(0.10) : Color(nsColor: .controlBackgroundColor).opacity(0.6))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(
                    isDropTargeted ? VidowColors.brandBlue : Color.primary.opacity(0.16),
                    style: StrokeStyle(lineWidth: isDropTargeted ? 2.5 : 1.5, dash: [8, 6])
                )
        )
    }

    @ViewBuilder
    private func loadedConverterContent(info: LocalVideoInfo) -> some View {
        // 1. Video Preview + Diagnostics Card
        BentoCard {
            HStack(alignment: .top, spacing: 18) {
                Group {
                    if let player {
                        VideoPlayer(player: player)
                    } else {
                        Rectangle().fill(Color.black.opacity(0.8))
                    }
                }
                .frame(width: 280, height: 158)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 10) {
                    Text(info.fileName)
                        .font(.headline)
                        .lineLimit(2)

                    WrapFlowLayout(spacing: 6, rowSpacing: 6) {
                        SpecPill(text: info.resolutionLabel)
                        SpecPill(text: info.videoCodec)
                        SpecPill(text: "\(Int(info.frameRate.rounded())) fps")
                        SpecPill(text: formatDuration(info.durationSeconds))
                        SpecPill(text: formatFileSize(info.fileSizeBytes))
                        if info.isHDR {
                            SpecPill(text: "HDR", warning: true)
                        }
                    }

                    let issues = info.compatibilityIssues
                    if issues.isEmpty {
                        Label(L10n.t("conv_compat_ok"), systemImage: "checkmark.seal.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.green)
                    } else {
                        VStack(alignment: .leading, spacing: 4) {
                            Label(L10n.t("conv_compat_warn"), systemImage: "exclamationmark.triangle.fill")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.orange)
                            ForEach(issues, id: \.self) { issue in
                                Text("• \(issue)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }

        // 2. One-Tap Presets Strip
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.t("conv_presets"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            WrapFlowLayout(spacing: 8, rowSpacing: 8) {
                ForEach(ConversionPreset.allCases) { preset in
                    SelectableChip(
                        label: preset.label,
                        isSelected: vm.conversionConfig.presetId == preset.rawValue
                    ) {
                        vm.applyPreset(preset)
                    }
                }
            }
        }

        // 3. 2×2 Native Mac Bento Controls Grid
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
            // Card A: Codec & Color
            BentoCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label(L10n.t("bento_codec_title"), systemImage: "cpu")
                        .font(.subheadline.weight(.bold))

                    Picker(L10n.t("bento_codec_label"), selection: customBinding(\.videoCodec)) {
                        ForEach(OutputVideoCodec.allCases) { c in
                            Text(c.label).tag(c)
                        }
                    }
                    .pickerStyle(.segmented)

                    Toggle(isOn: customBinding(\.toneMapHdrToSdr)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.t("bento_hdr_sdr")).font(.callout.weight(.medium))
                            Text(L10n.t("bento_hdr_sdr_sub")).font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .toggleStyle(.switch)
                }
            }

            // Card B: Quality & Target MB
            BentoCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label(L10n.t("bento_size_title"), systemImage: "gauge.with.dots.needle.67percent")
                        .font(.subheadline.weight(.bold))

                    Picker("", selection: customBinding(\.compressionMode)) {
                        ForEach(CompressionMode.allCases) { m in
                            Text(m.label).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()

                    if vm.conversionConfig.compressionMode == .quality {
                        Picker("", selection: customBinding(\.qualityTier)) {
                            ForEach(QualityTier.allCases) { tier in
                                Text(tier.label).tag(tier)
                            }
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()
                    } else {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(L10n.f("target_mb_label", vm.conversionConfig.targetSizeMb))
                                    .font(.callout.weight(.semibold).monospacedDigit())
                                Spacer()
                                Stepper("", value: customBinding(\.targetSizeMb), in: 2...500, step: 5)
                                    .labelsHidden()
                            }
                            Slider(
                                value: Binding(
                                    get: { Double(vm.conversionConfig.targetSizeMb) },
                                    set: {
                                        vm.conversionConfig.targetSizeMb = Int($0.rounded())
                                        vm.conversionConfig.presetId = "custom"
                                    }
                                ),
                                in: 2...200,
                                step: 1
                            )
                            Toggle(isOn: customBinding(\.autoDownscaleForTargetSize)) {
                                Text(L10n.t("auto_downscale")).font(.caption.weight(.medium))
                            }
                            .toggleStyle(.switch)
                        }
                    }
                }
            }

            // Card C: Resolution, FPS & Aspect Ratio
            BentoCard {
                VStack(alignment: .leading, spacing: 10) {
                    Label(L10n.t("bento_frame_title"), systemImage: "aspectratio")
                        .font(.subheadline.weight(.bold))

                    HStack {
                        Text(L10n.t("res_label")).font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Picker("", selection: customBinding(\.resolutionCap)) {
                            ForEach(ResolutionCap.allCases) { r in Text(r.label).tag(r) }
                        }
                        .labelsHidden()
                        .frame(width: 130)
                    }

                    HStack {
                        Text(L10n.t("fps_label")).font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Picker("", selection: customBinding(\.frameRateCap)) {
                            ForEach(FrameRateCap.allCases) { f in Text(f.label).tag(f) }
                        }
                        .labelsHidden()
                        .frame(width: 130)
                    }

                    HStack {
                        Text(L10n.t("ratio_label")).font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Picker("", selection: customBinding(\.aspectRatio)) {
                            ForEach(AspectRatioOption.allCases) { a in Text(a.label).tag(a) }
                        }
                        .labelsHidden()
                        .frame(width: 130)
                    }

                    if vm.conversionConfig.aspectRatio != .original {
                        Picker(L10n.t("fit_label"), selection: customBinding(\.aspectFitMode)) {
                            ForEach(AspectFitMode.allCases) { m in Text(m.label).tag(m) }
                        }
                        .pickerStyle(.segmented)
                    }
                }
            }

            // Card D: Trim & Privacy
            BentoCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label(L10n.t("bento_trim_title"), systemImage: "scissors")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                        if vm.conversionConfig.trimStartSeconds > 0.1 || vm.conversionConfig.trimEndSeconds < info.durationSeconds - 0.1 {
                            Button(L10n.t("trim_reset")) {
                                vm.conversionConfig.trimStartSeconds = 0
                                vm.conversionConfig.trimEndSeconds = info.durationSeconds
                            }
                            .font(.caption)
                            .buttonStyle(.link)
                        }
                    }

                    let maxDur = max(1.0, info.durationSeconds)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(L10n.f("trim_start", formatDuration(vm.conversionConfig.trimStartSeconds)))
                            Spacer()
                            Text(L10n.f("trim_end", formatDuration(vm.conversionConfig.trimEndSeconds)))
                        }
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)

                        Slider(
                            value: Binding(
                                get: { vm.conversionConfig.trimStartSeconds },
                                set: {
                                    vm.conversionConfig.trimStartSeconds = min($0, max(0, vm.conversionConfig.trimEndSeconds - 0.5))
                                    vm.conversionConfig.presetId = "custom"
                                }
                            ),
                            in: 0...maxDur
                        )
                        Slider(
                            value: Binding(
                                get: { vm.conversionConfig.trimEndSeconds },
                                set: {
                                    vm.conversionConfig.trimEndSeconds = max($0, min(maxDur, vm.conversionConfig.trimStartSeconds + 0.5))
                                    vm.conversionConfig.presetId = "custom"
                                }
                            ),
                            in: 0...maxDur
                        )
                    }

                    Toggle(L10n.t("mute_audio"), isOn: customBinding(\.removeAudio))
                        .font(.caption.weight(.medium))
                    Toggle(L10n.t("strip_gps"), isOn: customBinding(\.stripMetadata))
                        .font(.caption.weight(.medium))
                }
            }
        }
    }

    private func bottomActionDock(info: LocalVideoInfo, plan: EncodingPlan) -> some View {
        VStack(spacing: 8) {
            if let notice = plan.notice {
                HStack(spacing: 6) {
                    Image(systemName: "info.circle.fill").foregroundStyle(VidowColors.brandBlue)
                    Text(notice.localizedMessage).font(.caption).foregroundStyle(.secondary)
                    Spacer()
                }
            }

            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.f(
                        "est_summary",
                        formatFileSize(info.fileSizeBytes),
                        formatFileSize(plan.estimatedBytes),
                        plan.outputWidth,
                        plan.outputHeight,
                        vm.conversionConfig.videoCodec == .hevc ? "HEVC" : "H.264",
                        Double(plan.videoBitrateBps) / 1_000_000.0
                    ))
                    .font(.callout.weight(.semibold).monospacedDigit())
                }

                Spacer()

                Button {
                    vm.startConversion()
                } label: {
                    Label(L10n.t("conv_start_btn"), systemImage: "bolt.fill")
                        .font(.system(size: 14, weight: .bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(VidowColors.brandBlue)
                .controlSize(.large)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 14)
        .background(.bar)
        .overlay(Divider(), alignment: .top)
    }

    @ViewBuilder
    private var conversionOverlay: some View {
        switch vm.conversionState {
        case .converting(let pct, let codecLabel):
            ZStack {
                Color.black.opacity(0.42).ignoresSafeArea()
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
                            .animation(.easeInOut(duration: 0.2), value: pct)
                        Text("\(pct)%")
                            .font(.system(size: 22, weight: .bold).monospacedDigit())
                    }
                    Text(L10n.f("conv_running", codecLabel))
                        .font(.headline)
                    Button(L10n.t("dl_cancel"), role: .destructive) {
                        vm.cancelConversion()
                    }
                    .buttonStyle(.bordered)
                }
                .frame(width: 340)
                .padding(24)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }

        case .success(let res):
            ZStack {
                Color.black.opacity(0.42).ignoresSafeArea()
                VStack(spacing: 14) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 46))
                        .foregroundStyle(.green)
                    Text(L10n.t("conv_done_title")).font(.title3.bold())
                    Text(res.outputFileName)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)

                    HStack(spacing: 20) {
                        VStack(spacing: 2) {
                            Text(L10n.t("conv_before")).font(.caption).foregroundStyle(.secondary)
                            Text(formatFileSize(res.originalSizeBytes)).font(.headline.monospacedDigit())
                        }
                        Image(systemName: "arrow.right").foregroundStyle(.secondary)
                        VStack(spacing: 2) {
                            Text(L10n.t("conv_after")).font(.caption).foregroundStyle(.secondary)
                            Text(formatFileSize(res.outputSizeBytes)).font(.headline.monospacedDigit()).foregroundStyle(VidowColors.brandBlue)
                        }
                    }
                    .padding(.vertical, 6)

                    let savedPct = res.originalSizeBytes > 0
                        ? Int(((Double(res.originalSizeBytes - res.outputSizeBytes) / Double(res.originalSizeBytes)) * 100.0).rounded())
                        : 0
                    HStack(spacing: 8) {
                        if savedPct > 0 {
                            SpecPill(text: L10n.f("conv_saved_pct", savedPct))
                        }
                        SpecPill(text: "\(res.outputWidth)×\(res.outputHeight)")
                        SpecPill(text: res.codecLabel)
                        SpecPill(text: L10n.f("conv_elapsed", String(format: "%.1fs", res.elapsedSeconds)))
                    }

                    HStack(spacing: 10) {
                        Button {
                            vm.revealInFinder(path: res.outputPath)
                        } label: {
                            Label(L10n.t("dl_reveal_finder"), systemImage: "folder")
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(VidowColors.brandBlue)

                        Button {
                            vm.openFile(path: res.outputPath)
                        } label: {
                            Label(L10n.t("dl_play"), systemImage: "play.fill")
                        }
                        .buttonStyle(.bordered)
                    }

                    Button(L10n.t("dl_close")) {
                        vm.conversionState = .idle
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                }
                .frame(width: 400)
                .padding(24)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }

        case .failed(let msg):
            ZStack {
                Color.black.opacity(0.42).ignoresSafeArea()
                VStack(spacing: 14) {
                    Image(systemName: "xmark.octagon.fill")
                        .font(.system(size: 42))
                        .foregroundStyle(.red)
                    Text(L10n.t("conv_failed_title")).font(.title3.bold())
                    Text(msg)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(5)
                    HStack(spacing: 10) {
                        Button(L10n.t("dl_retry")) { vm.startConversion() }
                            .buttonStyle(.borderedProminent)
                            .tint(VidowColors.brandBlue)
                        Button(L10n.t("dl_close")) { vm.conversionState = .idle }
                            .buttonStyle(.bordered)
                    }
                }
                .frame(width: 380)
                .padding(24)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }

        default:
            EmptyView()
        }
    }

    private func customBinding<T>(_ keyPath: WritableKeyPath<ConversionConfig, T>) -> Binding<T> {
        Binding(
            get: { vm.conversionConfig[keyPath: keyPath] },
            set: {
                vm.conversionConfig[keyPath: keyPath] = $0
                vm.conversionConfig.presetId = "custom"
            }
        )
    }

    private func handleFileDrop(_ providers: [NSItemProvider]) -> Bool {
        guard let provider = providers.first else { return false }
        provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
            var foundURL: URL?
            if let data = item as? Data {
                foundURL = URL(dataRepresentation: data, relativeTo: nil)
            } else if let url = item as? URL {
                foundURL = url
            }
            if let url = foundURL {
                Task { @MainActor in
                    vm.openInConverter(url: url)
                }
            }
        }
        return true
    }
}
