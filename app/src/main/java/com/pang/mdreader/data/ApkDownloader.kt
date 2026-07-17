package com.pang.mdreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Directly downloads the APK to app cache using coroutines,
 * then launches system package installer via FileProvider.
 */
object ApkDownloader {

    private const val APK_FILENAME = "md-reader-update.apk"

    suspend fun downloadAndInstall(context: Context, downloadUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Clean previous download
                val apkFile = File(context.cacheDir, APK_FILENAME)
                if (apkFile.exists()) apkFile.delete()

                // Download APK
                val conn = URL(downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                if (conn.responseCode != 200) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "下载失败 (HTTP ${conn.responseCode})", Toast.LENGTH_LONG).show()
                    }
                    return@withContext false
                }

                val inputStream = conn.inputStream
                val outputStream = apkFile.outputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                val contentLength = conn.contentLengthLong

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                outputStream.close()
                inputStream.close()

                if (contentLength > 0 && totalBytes < contentLength) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "下载不完整，请重试", Toast.LENGTH_LONG).show()
                    }
                    apkFile.delete()
                    return@withContext false
                }

                // Install
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
                true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                false
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        context.startActivity(intent)
    }
}
