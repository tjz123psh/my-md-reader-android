package com.pang.mdreader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.pang.mdreader.model.FileNode
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class FileRepository(private val context: Context) {

    companion object {
        private val MARKDOWN_EXTS = setOf("md", "markdown", "mdown", "mkd")
        private const val MAX_FILES = 3000
        private const val MAX_DEPTH = 20
        private const val BATCH_SIZE = 100

        fun isMarkdownFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in MARKDOWN_EXTS
        }
    }

    /**
     * Scan a directory and return markdown files.
     * Calls onBatch() every ~100 files so the UI can show progress.
     * Returns the full sorted list (capped at MAX_FILES).
     */
    suspend fun scanDirectory(
        rootUri: Uri,
        onBatch: (List<FileNode>) -> Unit = {},
    ): List<FileNode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileNode>()
        val batchBuffer = mutableListOf<FileNode>()
        scanRecursive(rootUri, "", result, batchBuffer, 0, onBatch)
        if (batchBuffer.isNotEmpty()) {
            onBatch(batchBuffer.toList())
        }
        result.sortedBy { it.relativePath }
    }

    private data class ChildInfo(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
    )

    private fun listChildren(treeUri: Uri): List<ChildInfo> {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
            val children = mutableListOf<ChildInfo>()
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mime
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    children.add(ChildInfo(childUri, name, isDir))
                }
            }
            children
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun scanRecursive(
        dirUri: Uri,
        prefix: String,
        result: MutableList<FileNode>,
        batchBuffer: MutableList<FileNode>,
        depth: Int,
        onBatch: (List<FileNode>) -> Unit,
    ) {
        if (depth > MAX_DEPTH || !coroutineContext.isActive) return
        if (result.size >= MAX_FILES) return

        val children = listChildren(dirUri)
        val filtered = children.filter { !it.name.startsWith('.') }

        // First pass: add directories
        var dirCount = 0
        for (child in filtered) {
            if (!child.isDirectory) continue
            dirCount++
            val relPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            result.add(
                FileNode(
                    uri = child.uri,
                    name = child.name,
                    isDirectory = true,
                    relativePath = relPath,
                )
            )
        }

        // Second pass: add markdown files
        var fileCount = 0
        for (child in filtered) {
            if (result.size >= MAX_FILES) break
            if (!child.isDirectory && isMarkdownFile(child.name)) {
                fileCount++
                val relPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                result.add(
                    FileNode(
                        uri = child.uri,
                        name = child.name,
                        isDirectory = false,
                        relativePath = relPath,
                    )
                )
                batchBuffer.add(result.last())
                if (batchBuffer.size >= BATCH_SIZE) {
                    onBatch(batchBuffer.toList())
                    batchBuffer.clear()
                    kotlinx.coroutines.yield()
                }
            }
        }

        // Then recurse into directories
        for (child in filtered) {
            if (result.size >= MAX_FILES || !coroutineContext.isActive) break
            if (child.isDirectory) {
                val relPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                scanRecursive(child.uri, relPath, result, batchBuffer, depth + 1, onBatch)
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
            val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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

}
