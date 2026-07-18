package com.pang.mdreader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.pang.mdreader.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

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
     * Resolve local image references to SAF content URIs. Exact relative paths are tried
     * first. Bare Obsidian links then fall back to a breadth-first search of the selected
     * workspace, so custom attachment directory names work without configuration.
     *
     * The image bytes are streamed by MarkdownView's WebViewClient instead of being copied
     * into the generated HTML as base64.
     */
    suspend fun resolveImageUris(markdown: String, fileUri: Uri, rootTreeUri: Uri? = null): String = withContext(Dispatchers.IO) {
        if (markdown.isEmpty()) return@withContext markdown

        val authority = fileUri.authority ?: return@withContext markdown
        val docId = getDocumentId(fileUri) ?: return@withContext markdown
        val parentDocId = docId.substringBeforeLast("/", "")
        val regex = Regex("""!\[([^\]]*)\]\(([^)]+)\)|!\[\[([^\]\n]+)\]\]""")
        val localPaths = regex.findAll(markdown)
            .mapNotNull { match -> extractLocalImagePath(match) }
            .distinct()
            .toList()
        if (localPaths.isEmpty()) return@withContext markdown

        // A document URI created from a tree carries the exact tree that granted access.
        // Prefer it over caller state, which may still be switching after a recent-file tap.
        val treeUri = treeUriFromDocumentUri(fileUri) ?: rootTreeUri
        val rootDocId = treeUri?.let { getTreeDocumentId(it) }
        Log.d(
            "MDReader-IMG",
            "resolve start: file=$docId parent=$parentDocId tree=${treeUri != null} images=${localPaths.size}",
        )

        val resolved = mutableMapOf<String, Uri>()
        for (path in localPaths) {
            val candidates = resolveImageCandidates(parentDocId, rootDocId, path)
            for (candidateDocId in candidates) {
                val candidateUri = buildDocumentUri(authority, treeUri, candidateDocId)
                if (canRead(candidateUri)) {
                    resolved[path] = candidateUri
                    Log.d("MDReader-IMG", "exact match: $path → $candidateDocId")
                    break
                }
            }
        }

        val unresolvedNames = localPaths
            .filterNot(resolved::containsKey)
            .associateBy { it.substringAfterLast("/").lowercase() }
        if (treeUri != null && unresolvedNames.isNotEmpty()) {
            val treeMatches = searchImageNamesInTree(treeUri, unresolvedNames.keys)
            for ((name, uri) in treeMatches) {
                unresolvedNames[name]?.let { resolved[it] = uri }
            }
        }

        regex.replace(markdown) { match ->
            val path = extractLocalImagePath(match) ?: return@replace match.value
            val imageUri = resolved[path]
            if (imageUri == null) {
                Log.e("MDReader-IMG", "not found: $path (tree=${treeUri != null})")
                return@replace match.value
            }

            val alt = match.groupValues[1].ifEmpty {
                path.substringAfterLast("/").substringBeforeLast(".")
            }.replace("]", "\\]")
            "![$alt](<$imageUri>)"
        }
    }

    /** Search every real directory ID returned by the provider, stopping once all names exist. */
    private fun searchImageNamesInTree(treeUri: Uri, targetNames: Set<String>): Map<String, Uri> {
        val rootDocId = getTreeDocumentId(treeUri) ?: return emptyMap()
        val pendingNames = targetNames.toMutableSet()
        val found = mutableMapOf<String, Uri>()
        val directories = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        directories.add(rootDocId)

        while (directories.isNotEmpty() && pendingNames.isNotEmpty()) {
            val directoryId = directories.removeFirst()
            if (!visited.add(directoryId)) continue
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
                val columns = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                context.contentResolver.query(childrenUri, columns, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directories.addLast(id)
                            continue
                        }

                        val key = name.lowercase()
                        if (key in pendingNames && (mime.startsWith("image/") || isImageFileName(name))) {
                            found[key] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            pendingNames.remove(key)
                            Log.d("MDReader-IMG", "tree match: $name → $id")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MDReader-IMG", "tree query failed: $directoryId | ${e.message}")
            }
        }
        return found
    }

    private fun extractLocalImagePath(match: MatchResult): String? {
        val standardPath = match.groupValues[2]
        val original = if (standardPath.isNotEmpty()) {
            standardPath.trim().let { value ->
                if (value.startsWith("<") && value.contains(">")) {
                    value.substring(1, value.indexOf('>'))
                } else {
                    // CommonMark titles follow the destination after whitespace.
                    value.replace(Regex("""\s+[\"'].*$"""), "")
                }
            }
        } else {
            match.groupValues[3].substringBefore("|").trim()
        }
        val lower = original.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) return null

        // A literal # or ? is a fragment/query delimiter. Percent-encoded versions remain part of the filename.
        val withoutSuffix = original.substringBefore("#").substringBefore("?")
        return Uri.decode(withoutSuffix).replace('\\', '/').trim().takeIf { it.isNotEmpty() }
    }

    private fun resolveImageCandidates(parentDocId: String, rootDocId: String?, imagePath: String): List<String> {
        val candidates = linkedSetOf<String>()
        if (imagePath.startsWith("/")) {
            if (rootDocId != null) {
                candidates.add(resolveWithinRoot(rootDocId, rootDocId, imagePath.removePrefix("/")))
            }
        } else {
            candidates.add(resolveWithinRoot(rootDocId, parentDocId, imagePath))
            if (rootDocId != null) {
                candidates.add(resolveWithinRoot(rootDocId, rootDocId, imagePath))
            }
        }
        return candidates.filter { it.isNotEmpty() }
    }

    private fun resolveWithinRoot(rootDocId: String?, baseDocId: String, relativePath: String): String {
        val isInsideRoot = rootDocId != null &&
            (baseDocId == rootDocId || baseDocId.startsWith("$rootDocId/"))
        if (!isInsideRoot) {
            return resolveSingle(baseDocId, relativePath)
        }

        val relativeBase = baseDocId.removePrefix(rootDocId!!).removePrefix("/")
        val parts = relativeBase.split("/").filter { it.isNotEmpty() }.toMutableList()
        for (segment in relativePath.split("/")) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return if (parts.isEmpty()) rootDocId else "$rootDocId/${parts.joinToString("/")}"
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

    private fun canRead(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun buildDocumentUri(authority: String, treeUri: Uri?, documentId: String): Uri {
        return if (treeUri != null) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        } else {
            DocumentsContract.buildDocumentUri(authority, documentId)
        }
    }

    private fun getDocumentId(uri: Uri): String? = try {
        DocumentsContract.getDocumentId(uri)
    } catch (_: Exception) {
        uri.lastPathSegment?.let(Uri::decode)
    }

    private fun getTreeDocumentId(uri: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (_: Exception) {
        null
    }

    private fun treeUriFromDocumentUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeDocumentId = getTreeDocumentId(uri) ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeDocumentId)
    }

    private fun isImageFileName(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in setOf(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif", "tiff", "tif",
        )
    }

    /** Recover the canonical tree URI even from an older, incorrectly truncated document URI. */
    fun normalizeTreeUri(uri: Uri): Uri {
        val authority = uri.authority ?: return uri
        val treeDocumentId = getTreeDocumentId(uri) ?: return uri
        return DocumentsContract.buildTreeDocumentUri(authority, treeDocumentId)
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
