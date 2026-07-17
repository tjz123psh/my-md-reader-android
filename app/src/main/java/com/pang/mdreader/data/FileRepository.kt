package com.pang.mdreader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.pang.mdreader.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class FileRepository(private val context: Context) {

    /**
     * Recursively scan a directory and return all markdown files.
     */
    suspend fun scanDirectory(rootUri: Uri): List<FileNode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileNode>()
        scanRecursive(rootUri, "", result)
        result.sortedBy { it.relativePath }
    }

    private fun scanRecursive(
        dirUri: Uri,
        prefix: String,
        result: MutableList<FileNode>,
    ) {
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return
        val files = dir.listFiles().orEmpty()

        // Skip hidden directories
        val filtered = files.filter { !it.name.orEmpty().startsWith('.') }

        // Add directories first
        for (file in filtered) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                result.add(
                    FileNode(
                        uri = file.uri,
                        name = name,
                        isDirectory = true,
                        lastModified = file.lastModified(),
                        relativePath = relPath,
                    )
                )
                scanRecursive(file.uri, relPath, result)
            }
        }

        // Add markdown files
        for (file in filtered) {
            if (file.isFile && isMarkdownFile(file.name.orEmpty())) {
                val name = file.name ?: continue
                val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                result.add(
                    FileNode(
                        uri = file.uri,
                        name = name,
                        isDirectory = false,
                        lastModified = file.lastModified(),
                        size = file.length(),
                        relativePath = relPath,
                    )
                )
            }
        }
    }

    /**
     * Read the content of a file.
     */
    suspend fun readFile(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val text = reader.readText()
            reader.close()
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a DocumentFile from a Uri (works for both tree and document Uris).
     */
    fun getDocumentFile(uri: Uri): DocumentFile? {
        return DocumentFile.fromSingleUri(context, uri)
            ?: DocumentFile.fromTreeUri(context, uri)
    }

    /**
     * Get the display name of a file from Uri.
     */
    fun getFileName(uri: Uri): String {
        return DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Unknown"
    }

    companion object {
        private val MARKDOWN_EXTS = setOf("md", "markdown", "mdown", "mkd")

        fun isMarkdownFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in MARKDOWN_EXTS
        }
    }
}
