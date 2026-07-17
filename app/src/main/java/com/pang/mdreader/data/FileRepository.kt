package com.pang.mdreader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.pang.mdreader.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class FileRepository(private val context: Context) {

    companion object {
        private val MARKDOWN_EXTS = setOf("md", "markdown", "mdown", "mkd")

        fun isMarkdownFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in MARKDOWN_EXTS
        }
    }

    /**
     * List immediate children of a directory — one query, no recursion.
     * Returns FileNodes for both directories and markdown files inside this directory only.
     * @param rootTreeUri the original tree URI from folder picker (needed for SAF subdirectory queries)
     */
    suspend fun listDirectory(uri: Uri, rootTreeUri: Uri): List<FileNode> = withContext(Dispatchers.IO) {
        listChildren(uri, rootTreeUri)
    }

    private fun listChildren(parentUri: Uri, rootTreeUri: Uri): List<FileNode> {
        return try {
            // Use getDocumentId for subdirectories, getTreeDocumentId for root
            val docId = try {
                DocumentsContract.getDocumentId(parentUri)
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(parentUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, docId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)

            val result = mutableListOf<FileNode>()
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val isDir = DocumentsContract.Document.MIME_TYPE_DIR == mime
                    if (!isDir && !isMarkdownFile(name)) continue
                    if (name.startsWith('.')) continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, id)
                    result.add(
                        FileNode(
                            uri = childUri,
                            name = name,
                            isDirectory = isDir,
                            relativePath = name,
                        )
                    )
                }
            }
            result.sortedBy { it.relativePath }
        } catch (_: Exception) {
            emptyList()
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
     * Scan markdown text for image references and replace them with [content://] URIs
     * (readable by the app via SAF tree permission). The actual bytes are streamed
     * via WebViewClient.shouldInterceptRequest — no base64 encoding needed.
     *
     * Handles both standard markdown ![...](...) and Obsidian ![[...]] syntax.
     */
    suspend fun resolveImageUris(markdown: String, fileUri: Uri): String = withContext(Dispatchers.IO) {
        if (markdown.isEmpty()) return@withContext markdown

        val authority = fileUri.authority ?: return@withContext markdown
        val docId = Uri.decode(fileUri.lastPathSegment ?: return@withContext markdown)
        val parentDocId = docId.substringBeforeLast("/", "")

        val regex = Regex("""!\[([^\]]*)\]\(([^)]+)\)|!\[\[([^\]\n]+)\]\]""")

        regex.replace(markdown) { match ->
            val rawPath = match.groupValues[2].ifEmpty {
                match.groupValues[3].split("|")[0].trim()
            }
            // Skip URLs and data URIs
            if (rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("data:")) {
                return@replace match.value
            }

            // Try multiple candidate paths (same dir, Obsidian attachment subdirs, etc.)
            var foundStream: java.io.InputStream? = null
            var foundMime = "image/png"
            var foundAlt = match.groupValues[1].ifEmpty {
                rawPath.substringAfterLast("/").substringBeforeLast(".")
            }

            for (candidateDocId in resolveImageCandidates(parentDocId, rawPath)) {
                val candUri = DocumentsContract.buildDocumentUri(authority, candidateDocId)
                try {
                    val stream = context.contentResolver.openInputStream(candUri)
                    if (stream != null) {
                        android.util.Log.d("MDReader-IMG", "FOUND: $rawPath → $candidateDocId")
                        foundStream = stream
                        foundMime = getImageMimeType(rawPath, candUri)
                        break
                    }
                } catch (_: java.io.FileNotFoundException) {
                    android.util.Log.d("MDReader-IMG", "NOT_FOUND: $candidateDocId ($rawPath)")
                } catch (_: SecurityException) {
                    android.util.Log.d("MDReader-IMG", "PERM_DENIED: $candidateDocId ($rawPath)")
                }
            }

            if (foundStream != null) {
                try {
                    val bytes = foundStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        return@replace "![$foundAlt](data:$foundMime;base64,$b64)"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MDReader-IMG", "READ_ERROR: ${e.message} | $rawPath")
                }
            } else {
                android.util.Log.e("MDReader-IMG", "ALL_FAILED: $rawPath (parent=$parentDocId)")
            }
            match.value
        }
    }

    /**
     * Generate candidate document IDs for a relative image path.
     * Handles: same directory, Obsidian attachment subdirectories.
     */
    private fun resolveImageCandidates(parentPath: String, imagePath: String): List<String> {
        val candidates = mutableListOf<String>()

        if (imagePath.startsWith("/")) {
            candidates.add(imagePath.removePrefix("/"))
            return candidates
        }

        if (parentPath.isEmpty()) {
            candidates.add(imagePath)
            return candidates
        }

        // Candidate 1: directly resolve in parent (handles . and ..)
        candidates.add(resolveSingle(parentPath, imagePath))

        // Candidate 2+: try Obsidian attachment folder conventions
        val obsidianAttachDirs = listOf("附件", "attachments", "assets", "images", "_resources")
        for (dir in obsidianAttachDirs) {
            val attachCandidate = if (parentPath.contains("/$dir/") || parentPath.endsWith("/$dir")) {
                // Image is in a parent + attachment dir; file ref is just the filename
                resolveSingle(parentPath, "$dir/$imagePath")
            } else {
                // Try parent/attachment-subdir/ directly
                "$parentPath/$dir/$imagePath"
            }
            if (attachCandidate != candidates.first()) {
                candidates.add(attachCandidate)
            }
        }
        return candidates
    }

    private fun resolveSingle(parentPath: String, imagePath: String): String {
        val parts = mutableListOf<String>()
        for (segment in (parentPath.split("/") + imagePath.split("/"))) {
            when (segment) {
                ".", "" -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }



    private fun getImageMimeType(path: String, uri: Uri): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "avif" -> "image/avif"
            "tiff", "tif" -> "image/tiff"
            else -> context.contentResolver.getType(uri) ?: "image/png"
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
