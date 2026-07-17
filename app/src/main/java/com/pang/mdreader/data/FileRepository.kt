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
     * Uses direct ContentResolver queries for faster batch listing.
     */
    suspend fun scanDirectory(rootUri: Uri): List<FileNode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileNode>()
        scanRecursive(rootUri, "", result)
        result.sortedBy { it.relativePath }
    }

    private data class ChildInfo(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
        val lastModified: Long,
        val size: Long,
    )

    /**
     * List children via ContentResolver.query() — single cursor per directory
     * instead of per-child DocumentFile overhead.
     */
    private fun listChildren(treeUri: Uri): List<ChildInfo> {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
            val children = mutableListOf<ChildInfo>()
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val lastModified = c.getLong(3)
                    val size = c.getLong(4)
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mime
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    children.add(ChildInfo(childUri, name, isDir, lastModified, size))
                }
            }
            children
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scanRecursive(
        dirUri: Uri,
        prefix: String,
        result: MutableList<FileNode>,
    ) {
        val children = listChildren(dirUri)
        val filtered = children.filter { !it.name.startsWith('.') }

        // Add directories first, then recurse
        for (child in filtered) {
            if (child.isDirectory) {
                val relPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                result.add(
                    FileNode(
                        uri = child.uri,
                        name = child.name,
                        isDirectory = true,
                        lastModified = child.lastModified,
                        relativePath = relPath,
                    )
                )
                scanRecursive(child.uri, relPath, result)
            }
        }

        // Add markdown files
        for (child in filtered) {
            if (!child.isDirectory && isMarkdownFile(child.name)) {
                val relPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                result.add(
                    FileNode(
                        uri = child.uri,
                        name = child.name,
                        isDirectory = false,
                        lastModified = child.lastModified,
                        size = child.size,
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
