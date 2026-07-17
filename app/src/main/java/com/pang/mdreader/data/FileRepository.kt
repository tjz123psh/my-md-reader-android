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
     * Scan markdown text for image references and replace them with base64 data URIs.
     * Handles both standard markdown ![...](...) and Obsidian ![[...]] syntax.
     */
    suspend fun embedImages(markdown: String, fileUri: Uri): String = withContext(Dispatchers.IO) {
        val authority = fileUri.authority ?: return@withContext markdown
        val fileDocId = DocumentsContract.getDocumentId(fileUri)
        val parentPath = fileDocId.substringBeforeLast("/", "")

        val regex = Regex("""!\[([^\]]*)\]\(([^)]+)\)|!\[\[([^\]\n]+)\]\]""")

        regex.replace(markdown) { match ->
            // Extract image path — group 2 is standard, group 3 is Obsidian
            val rawPath = match.groupValues[2].ifEmpty {
                match.groupValues[3].split("|")[0].trim()
            }
            // Skip URLs and data URIs
            if (rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("data:")) {
                return@replace match.value
            }
            try {
                // Resolve relative to the markdown file's directory
                val resolved = resolveImagePath(parentPath, rawPath)
                val imageUri = DocumentsContract.buildDocumentUri(authority, resolved)
                val inputStream = context.contentResolver.openInputStream(imageUri)
                if (inputStream != null) {
                    val bytes = inputStream.use { it.readBytes() }
                    val mime = getImageMimeType(rawPath, imageUri)
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val alt = match.groupValues[1].ifEmpty {
                        rawPath.substringAfterLast("/").substringBeforeLast(".")
                    }
                    "![$alt](data:$mime;base64,$base64)"
                } else {
                    match.value // file not found, keep original
                }
            } catch (_: Exception) {
                // If reading fails, keep the original reference
                match.value
            }
        }
    }

    /**
     * Resolve a relative image path against the markdown file's parent directory ID.
     */
    private fun resolveImagePath(parentPath: String, imagePath: String): String {
        return if (imagePath.startsWith("/")) {
            // Absolute path relative to SAF root
            imagePath.removePrefix("/")
        } else if (parentPath.isEmpty()) {
            imagePath
        } else {
            // Resolve relative path, handling . and ..
            val parts = mutableListOf<String>()
            for (segment in parentPath.split("/") + imagePath.split("/")) {
                when (segment) {
                    ".", "" -> { /* skip */ }
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(segment)
                }
            }
            parts.joinToString("/")
        }
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
