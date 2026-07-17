package com.pang.mdreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple update checker that compares local version against the latest GitHub release.
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val error: String = "",       // non-empty means check failed
    val fallbackUrl: String = "", // direct GitHub releases page if API fails
)

object UpdateChecker {
    private const val REPO_API = "https://api.github.com/repos/tjz123psh/my-md-reader-android/releases/latest"
    private const val LATEST_PAGE = "https://github.com/tjz123psh/my-md-reader-android/releases/latest"
    private const val RELEASES_URL = "https://github.com/tjz123psh/my-md-reader-android/releases"
    private const val DOWNLOAD_BASE = "https://github.com/tjz123psh/my-md-reader-android/releases/download"
    /** Single source of truth for app version. Keep in sync with app/build.gradle.kts → versionName */
    const val CURRENT_VERSION = "1.3.3"

    /**
     * Check for updates using two strategies:
     * 1. GitHub API (fast but may be blocked in China)
     * 2. GitHub releases page redirect (slower but more reliable)
     */
    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        // Strategy 1: GitHub API
        try {
            val result = checkViaApi()
            if (result.latestVersion.isNotEmpty() && result.latestVersion != CURRENT_VERSION) {
                return@withContext result
            }
            // If API says no update but page says otherwise, trust page
            // (API may be stale after release recreation)
        } catch (_: Exception) {
            // Fall through to strategy 2
        }

        // Strategy 2: Follow redirect from github.com/releases/latest
        try {
            return@withContext checkViaRedirect()
        } catch (_: Exception) {
            // Both failed
        }

        UpdateInfo(
            hasUpdate = false,
            latestVersion = CURRENT_VERSION,
            downloadUrl = "",
            releaseNotes = "",
            error = "无法连接到更新服务器",
            fallbackUrl = RELEASES_URL,
        )
    }

    /** Check using GitHub API (api.github.com). */
    private fun checkViaApi(): UpdateInfo {
        val conn = URL(REPO_API).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        if (conn.responseCode != 200) {
            throw Exception("API ${conn.responseCode}")
        }

        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "").removePrefix("v")
        val releaseNotes = json.optString("body", "").take(500)
        val assets = json.optJSONArray("assets")
        var downloadUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i)
                val name = asset?.optString("name", "") ?: ""
                if (name.endsWith("debug.apk")) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            if (downloadUrl.isEmpty()) {
                downloadUrl = assets.optJSONObject(0)
                    ?.optString("browser_download_url", "") ?: ""
            }
        }

        val hasUpdate = compareVersions(tagName, CURRENT_VERSION) > 0

        return UpdateInfo(
            hasUpdate = hasUpdate,
            latestVersion = tagName.ifEmpty { CURRENT_VERSION },
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
            fallbackUrl = RELEASES_URL,
        )
    }

    /** Fallback: follow redirect from github.com/releases/latest page. */
    private fun checkViaRedirect(): UpdateInfo {
        val conn = URL(LATEST_PAGE).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10000
        conn.readTimeout = 5000

        // Read Location header from redirect (e.g. /releases/tag/v1.3.3)
        val location = conn.getHeaderField("Location")
        conn.disconnect()

        if (location.isNullOrEmpty()) {
            throw Exception("no redirect")
        }

        val tag = location.substringAfterLast("/v").removePrefix("v").substringBefore("?")
        if (tag.isEmpty()) throw Exception("no tag in redirect: $location")

        val hasUpdate = compareVersions(tag, CURRENT_VERSION) > 0
        val downloadUrl = "$DOWNLOAD_BASE/v$tag/app-debug.apk"

        return UpdateInfo(
            hasUpdate = hasUpdate,
            latestVersion = tag,
            downloadUrl = downloadUrl,
            releaseNotes = "",
            fallbackUrl = RELEASES_URL,
        )
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
