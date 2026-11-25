import Foundation
import WebKit

final class SiteCookieManager {
    static let shared = SiteCookieManager()

    private let cookiesDir: URL
    private let combinedFile: URL

    private init() {
        cookiesDir = BinaryManager.shared.appSupportDir.appendingPathComponent("cookies", isDirectory: true)
        combinedFile = cookiesDir.appendingPathComponent("combined_cookies.txt")
        try? FileManager.default.createDirectory(at: cookiesDir, withIntermediateDirectories: true)
    }

    private func siteFile(for site: CookieSite) -> URL {
        cookiesDir.appendingPathComponent("site_\(site.rawValue).txt")
    }

    func loggedInSiteIDs() -> Set<String> {
        let fm = FileManager.default
        var ids = Set<String>()
        for site in CookieSite.allCases {
            let f = siteFile(for: site)
            if fm.fileExists(atPath: f.path),
               let content = try? String(contentsOf: f, encoding: .utf8),
               content.contains("\t") {
                ids.insert(site.id)
            }
        }
        return ids
    }

    func combinedCookiesFile() -> URL? {
        let fm = FileManager.default
        var lines: [String] = ["# Netscape HTTP Cookie File"]
        var count = 0
        for site in CookieSite.allCases {
            let f = siteFile(for: site)
            guard fm.fileExists(atPath: f.path),
                  let content = try? String(contentsOf: f, encoding: .utf8) else { continue }
            for line in content.components(separatedBy: .newlines) {
                let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty && !trimmed.hasPrefix("#") {
                    lines.append(trimmed)
                    count += 1
                }
            }
        }
        guard count > 0 else {
            try? fm.removeItem(at: combinedFile)
            return nil
        }
        let body = lines.joined(separator: "\n") + "\n"
        try? body.write(to: combinedFile, atomically: true, encoding: .utf8)
        return combinedFile
    }

    @MainActor
    func captureFromWebView(site: CookieSite) async -> Int {
        let allCookies = await WKWebsiteDataStore.default().httpCookieStore.allCookies()
        let matched = allCookies.filter { cookie in
            let dom = cookie.domain.lowercased()
            return site.domainSuffixes.contains { suffix in
                dom == suffix || dom.hasSuffix(".\(suffix)")
            }
        }
        let lines = Self.formatNetscapeLines(cookies: matched)
        guard !lines.isEmpty else { return 0 }
        let content = "# Netscape HTTP Cookie File\n" + lines.joined(separator: "\n") + "\n"
        try? content.write(to: siteFile(for: site), atomically: true, encoding: .utf8)
        _ = combinedCookiesFile()
        return lines.count
    }

    func clearSite(_ site: CookieSite) {
        try? FileManager.default.removeItem(at: siteFile(for: site))
        _ = combinedCookiesFile()
    }

    @MainActor
    func clearAll() async {
        for site in CookieSite.allCases {
            try? FileManager.default.removeItem(at: siteFile(for: site))
        }
        try? FileManager.default.removeItem(at: combinedFile)
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        await WKWebsiteDataStore.default().removeData(ofTypes: types, modifiedSince: .distantPast)
    }

    static func formatNetscapeLines(cookies: [HTTPCookie]) -> [String] {
        var unique: [String: String] = [:]
        let defaultExpiry = Int(Date().timeIntervalSince1970) + 60 * 60 * 24 * 365
        for c in cookies {
            let domain = c.domain.hasPrefix(".") ? c.domain : ".\(c.domain)"
            let includeSub = "TRUE"
            let path = c.path.isEmpty ? "/" : c.path
            let secure = c.isSecure ? "TRUE" : "FALSE"
            let expiry = c.expiresDate.map { Int($0.timeIntervalSince1970) } ?? defaultExpiry
            let key = "\(domain)|\(c.name)"
            unique[key] = "\(domain)\t\(includeSub)\t\(path)\t\(secure)\t\(expiry)\t\(c.name)\t\(c.value)"
        }
        return unique.keys.sorted().compactMap { unique[$0] }
    }
}
