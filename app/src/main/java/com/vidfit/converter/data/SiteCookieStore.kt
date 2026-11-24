package com.vidfit.converter.data

import android.content.Context
import android.webkit.CookieManager
import androidx.annotation.StringRes
import com.vidfit.converter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Built-in sites that commonly require login cookies for HD streams or anti-bot verification.
 */
enum class CookieSite(
    val id: String,
    @StringRes val displayNameRes: Int,
    @StringRes val hintRes: Int,
    val loginUrl: String,
    val cookieUrls: List<Pair<String, String>>
) {
    BILIBILI(
        id = "bilibili",
        displayNameRes = R.string.site_bilibili_name,
        hintRes = R.string.site_bilibili_hint,
        loginUrl = "https://passport.bilibili.com/login",
        cookieUrls = listOf(
            "https://www.bilibili.com" to ".bilibili.com",
            "https://api.bilibili.com" to ".bilibili.com",
            "https://m.bilibili.com" to ".bilibili.com"
        )
    ),
    XIAOHONGSHU(
        id = "xiaohongshu",
        displayNameRes = R.string.site_xhs_name,
        hintRes = R.string.site_xhs_hint,
        loginUrl = "https://www.xiaohongshu.com/explore",
        cookieUrls = listOf(
            "https://www.xiaohongshu.com" to ".xiaohongshu.com",
            "https://edith.xiaohongshu.com" to ".xiaohongshu.com"
        )
    ),
    DOUYIN(
        id = "douyin",
        displayNameRes = R.string.site_douyin_name,
        hintRes = R.string.site_douyin_hint,
        loginUrl = "https://www.douyin.com/",
        cookieUrls = listOf(
            "https://www.douyin.com" to ".douyin.com",
            "https://m.douyin.com" to ".douyin.com"
        )
    ),
    WEIBO(
        id = "weibo",
        displayNameRes = R.string.site_weibo_name,
        hintRes = R.string.site_weibo_hint,
        loginUrl = "https://m.weibo.cn/",
        cookieUrls = listOf(
            "https://m.weibo.cn" to ".weibo.cn",
            "https://weibo.com" to ".weibo.com"
        )
    ),
    YOUTUBE(
        id = "youtube",
        displayNameRes = R.string.site_youtube_name,
        hintRes = R.string.site_youtube_hint,
        loginUrl = "https://m.youtube.com/",
        cookieUrls = listOf(
            "https://www.youtube.com" to ".youtube.com",
            "https://m.youtube.com" to ".youtube.com"
        )
    ),
    TWITTER(
        id = "twitter",
        displayNameRes = R.string.site_twitter_name,
        hintRes = R.string.site_twitter_hint,
        loginUrl = "https://x.com/i/flow/login",
        cookieUrls = listOf(
            "https://x.com" to ".x.com",
            "https://twitter.com" to ".twitter.com"
        )
    )
}

/**
 * Manages locally stored Netscape-format `cookies.txt` files captured from the in-app WebView.
 * All cookies remain strictly inside app-private storage and are never uploaded anywhere.
 */
class SiteCookieStore(private val context: Context) {

    private val cookieDir: File
        get() = File(context.filesDir, "cookies").apply { mkdirs() }

    private fun siteFile(site: CookieSite): File = File(cookieDir, "${site.id}.txt")

    fun combinedCookiesFile(): File? {
        val combined = File(cookieDir, "combined_cookies.txt")
        return combined.takeIf { it.exists() && it.length() > 30L }
    }

    fun getLoggedInSiteIds(): Set<String> {
        return CookieSite.entries
            .filter { siteFile(it).let { f -> f.exists() && f.length() > 20L } }
            .map { it.id }
            .toSet()
    }

    /**
     * Reads current WebView cookies for [site], writes Netscape format to app-private storage,
     * and rebuilds the combined `cookies.txt` passed to `yt-dlp`.
     * Returns number of unique cookie pairs saved.
     */
    suspend fun captureFromWebView(site: CookieSite): Int = withContext(Dispatchers.IO) {
        val cm = CookieManager.getInstance()
        runCatching { cm.flush() }

        val entries = LinkedHashMap<Pair<String, String>, String>() // (domain, name) -> value
        for ((url, domain) in site.cookieUrls) {
            val rawHeader = cm.getCookie(url).orEmpty()
            parseCookieHeader(rawHeader).forEach { (name, value) ->
                entries[domain to name] = value
            }
        }

        if (entries.isEmpty()) return@withContext 0

        val lines = formatNetscapeLines(entries)
        siteFile(site).writeText(lines.joinToString("\n") + "\n")
        rebuildCombinedFile()
        entries.size
    }

    suspend fun clearSite(site: CookieSite) = withContext(Dispatchers.IO) {
        runCatching { siteFile(site).delete() }
        rebuildCombinedFile()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        CookieSite.entries.forEach { runCatching { siteFile(it).delete() } }
        runCatching { File(cookieDir, "combined_cookies.txt").delete() }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }

    private fun rebuildCombinedFile() {
        val allLines = mutableListOf(NETSCAPE_HEADER)
        CookieSite.entries.forEach { site ->
            val f = siteFile(site)
            if (f.exists()) {
                f.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { allLines.add(it) }
            }
        }
        val combined = File(cookieDir, "combined_cookies.txt")
        if (allLines.size <= 1) {
            runCatching { combined.delete() }
        } else {
            combined.writeText(allLines.joinToString("\n") + "\n")
        }
    }

    companion object {
        const val NETSCAPE_HEADER = "# Netscape HTTP Cookie File"
        // Far-future session expiry (2035-01-01 UTC) so yt-dlp treats captured cookies as valid
        private const val DEFAULT_EXPIRY_EPOCH = 2051222400L

        fun parseCookieHeader(rawHeader: String): List<Pair<String, String>> {
            if (rawHeader.isBlank()) return emptyList()
            return rawHeader.split(";")
                .mapNotNull { part ->
                    val trimmed = part.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx <= 0) null
                    else {
                        val name = trimmed.substring(0, eqIdx).trim()
                        val value = trimmed.substring(eqIdx + 1).trim()
                        if (name.isEmpty()) null else name to value
                    }
                }
        }

        fun formatNetscapeLines(
            entries: Map<Pair<String, String>, String>,
            expiryEpoch: Long = DEFAULT_EXPIRY_EPOCH
        ): List<String> {
            return entries.map { (domainAndName, value) ->
                val (domain, name) = domainAndName
                val includeSubdomains = if (domain.startsWith(".")) "TRUE" else "FALSE"
                "$domain\t$includeSubdomains\t/\tTRUE\t$expiryEpoch\t$name\t$value"
            }
        }
    }
}
