import SwiftUI

enum VidowColors {
    static let brandBlue = Color(red: 0.145, green: 0.388, blue: 0.922)   // #2563EB
    static let brandCyan = Color(red: 0.024, green: 0.714, blue: 0.831)   // #06B6D4
    static let brandCyanLight = Color(red: 0.220, green: 0.741, blue: 0.973) // #38BDF8
    static let brandGradient = LinearGradient(
        colors: [brandBlue, brandCyan],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

/// Two-tone "Vidow" capsule wordmark matching the Android app & Scheme C logo identity.
struct VidowWordmarkBadge: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: 0) {
            Text("Vid")
                .font(.system(size: 22, weight: .heavy, design: .rounded))
                .foregroundStyle(colorScheme == .dark ? Color.white : Color(red: 0.06, green: 0.09, blue: 0.16))
            Text("ow")
                .font(.system(size: 22, weight: .heavy, design: .rounded))
                .foregroundStyle(colorScheme == .dark ? VidowColors.brandCyanLight : Color(red: 0.01, green: 0.52, blue: 0.78))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 5)
        .background(
            Capsule()
                .fill(colorScheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.05))
                .overlay(
                    Capsule()
                        .stroke(colorScheme == .dark ? Color.white.opacity(0.14) : Color.black.opacity(0.08), lineWidth: 1)
                )
        )
    }
}

/// Clean macOS Bento card container adapting gracefully to Light & Dark appearances.
struct BentoCard<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    var accentBorder: Bool = false
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(colorScheme == .dark ? Color(nsColor: .controlBackgroundColor).opacity(0.7) : Color(nsColor: .controlBackgroundColor))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(
                        accentBorder
                            ? VidowColors.brandBlue.opacity(0.55)
                            : Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.08),
                        lineWidth: 1
                    )
            )
    }
}

struct SpecPill: View {
    let text: String
    var warning: Bool = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text(text)
            .font(.system(size: 11.5, weight: .semibold))
            .foregroundStyle(
                warning
                    ? (colorScheme == .dark ? Color.orange : Color(red: 0.72, green: 0.36, blue: 0.0))
                    : Color.secondary
            )
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(
                        warning
                            ? Color.orange.opacity(colorScheme == .dark ? 0.18 : 0.14)
                            : Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.06)
                    )
            )
    }
}

struct SelectableChip: View {
    let label: String
    let isSelected: Bool
    var accentText: Bool = false
    let action: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12.5, weight: isSelected ? .bold : .medium))
                .foregroundStyle(
                    isSelected
                        ? Color.white
                        : (accentText ? VidowColors.brandBlue : Color.primary)
                )
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(
                            isSelected
                                ? VidowColors.brandBlue
                                : Color.primary.opacity(colorScheme == .dark ? 0.08 : 0.05)
                        )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .stroke(
                            isSelected
                                ? VidowColors.brandBlue
                                : Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.08),
                            lineWidth: 1
                        )
                )
        }
        .buttonStyle(.plain)
    }
}

/// Native SwiftUI wrapping layout for quality & subtitle chips.
struct WrapFlowLayout: Layout {
    var spacing: CGFloat = 8
    var rowSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var curX: CGFloat = 0
        var curY: CGFloat = 0
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if curX + size.width > maxWidth && curX > 0 {
                curX = 0
                curY += rowHeight + rowSpacing
                rowHeight = 0
            }
            curX += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth.isFinite ? maxWidth : curX, height: curY + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var curX = bounds.minX
        var curY = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if curX + size.width > bounds.maxX && curX > bounds.minX {
                curX = bounds.minX
                curY += rowHeight + rowSpacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: curX, y: curY), proposal: ProposedViewSize(size))
            curX += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
