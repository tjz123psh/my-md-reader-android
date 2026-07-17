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
)

object UpdateChecker {
    private const val REPO_API = "https://api.github.com/repos/tjz123psh/my-md-reader-android/releases/latest"
    /** Single source of truth for app version. Keep in sync with app/build.gradle.kts → versionName */
    const val CURRENT_VERSION = "1.3.0"

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val conn = URL(REPO_API).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != 200) {
                return@withContext UpdateInfo(false, CURRENT_VERSION, "", "")
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "").take(500)
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    val name = asset?.optString("name", "") ?: ""
                    // Prefer the debug APK (works on all devices without signing issues)
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

            UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tagName.ifEmpty { CURRENT_VERSION },
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
            )
        } catch (_: Exception) {
            UpdateInfo(false, CURRENT_VERSION, "", "")
        }
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
