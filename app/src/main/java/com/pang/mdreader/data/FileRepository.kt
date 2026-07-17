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
    /**
     * Scan markdown text for image references and replace them with base64 data URIs.
     * First tries the exact observed path, then searches the parent directory via SAF query.
     * @param rootTreeUri the original tree URI from folder picker (optional, for SAF queries)
     */
    suspend fun resolveImageUris(markdown: String, fileUri: Uri, rootTreeUri: Uri? = null): String = withContext(Dispatchers.IO) {
        if (markdown.isEmpty()) return@withContext markdown

        val authority = fileUri.authority ?: return@withContext markdown
        val docId = Uri.decode(fileUri.lastPathSegment ?: return@withContext markdown)
        val parentDocId = docId.substringBeforeLast("/", "")

        val regex = Regex("""!\[([^\]]*)\]\(([^)]+)\)|!\[\[([^\]\n]+)\]\]""")

        regex.replace(markdown) { match ->
            val rawPath = match.groupValues[2].ifEmpty {
                match.groupValues[3].split("|")[0].trim()
            }
            if (rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("data:")) {
                return@replace match.value
            }

            val alt = match.groupValues[1].ifEmpty {
                rawPath.substringAfterLast("/").substringBeforeLast(".")
            }

            // Strategy 1: try resolving path relative to file's directory
            var foundUri: Uri? = null
            for (candidateDocId in resolveImageCandidates(parentDocId, rawPath)) {
                val candUri = DocumentsContract.buildDocumentUri(authority, candidateDocId)
                try {
                    context.contentResolver.openInputStream(candUri)?.close()
                    android.util.Log.d("MDReader-IMG", "FOUND strategy1: $rawPath → $candidateDocId")
                    foundUri = candUri
                    break
                } catch (_: Exception) {
                    android.util.Log.d("MDReader-IMG", "strategy1 MISS: $candidateDocId")
                }
            }

            // Strategy 2: search SAF children of parent directory for the filename
            if (foundUri == null && rootTreeUri != null) {
                foundUri = searchImageInTree(rootTreeUri, authority, parentDocId, rawPath)
                if (foundUri != null) {
                    android.util.Log.d("MDReader-IMG", "FOUND strategy2: $rawPath → $foundUri")
                }
            }

            if (foundUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(foundUri) ?: return@replace match.value
                    val bytes = inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val mime = getImageMimeType(rawPath, foundUri)
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        return@replace "![$alt](data:$mime;base64,$b64)"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MDReader-IMG", "READ_ERROR: ${e.message} | $rawPath")
                }
            }

            android.util.Log.e("MDReader-IMG", "ALL_FAILED: $rawPath (parentDocId=$parentDocId)")
            // Don't return original — show helpful placeholder instead of raw syntax
            return@replace "![图片未找到: $alt]()"
        }
    }

    /**
     * Search for an image file by name in the given directory and its immediate subdirectories.
     * This handles Obsidian's attachment folder pattern where images are in a subdirectory.
     */
    private fun searchImageInTree(treeUri: Uri, authority: String, parentDocId: String, imageName: String): Uri? {
        val searchDirs = mutableSetOf(parentDocId)

        // Attachment subdirs of parent directory
        for (dir in listOf("附件", "附", "attachments", "assets", "images", "img", "_resources", "media")) {
            searchDirs.add("$parentDocId/$dir")
        }

        // Grandparent and its attachment subdirs (Obsidian vault root level)
        val grandparent = parentDocId.substringBeforeLast("/", "")
        if (grandparent.isNotEmpty() && grandparent != parentDocId) {
            searchDirs.add(grandparent)
            for (dir in listOf("附件", "附", "attachments", "assets", "images", "img", "_resources", "media")) {
                searchDirs.add("$grandparent/$dir")
            }
        }

        for (dirDocId in searchDirs) {
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
                val columns = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime.startsWith("image/") && name.equals(imageName, ignoreCase = true)) {
                            return DocumentsContract.buildDocumentUri(authority, id)
                        }
                    }
                }
            } catch (_: Exception) {
                // Directory may not exist
            }
        }
        return null
    }

    /**
     * Generate candidate document IDs for a relative image path.
     * Tries multiple levels to handle Obsidian's vault attachment layout:
     *   md = vault/subdir/note.md, img = vault/附件/image.png
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

        // 1. Same directory
        candidates.add(resolveSingle(parentPath, imagePath))

        // 2. Attachment subdirs of same directory
        for (dir in listOf("附件", "附", "attachments", "assets", "images", "_resources", "media")) {
            candidates.add("$parentPath/$dir/$imagePath")
        }

        // 3. Grandparent directory (Obsidian vault root is often one level up)
        val grandparent = parentPath.substringBeforeLast("/", "")
        if (grandparent.isNotEmpty()) {
            // 3a. Grandparent directory itself
            candidates.add("$grandparent/$imagePath")
            // 3b. Attachment subdirs of grandparent
            for (dir in listOf("附件", "附", "attachments", "assets", "images", "_resources", "media")) {
                candidates.add("$grandparent/$dir/$imagePath")
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
