package com.pang.mdreader.model

import android.net.Uri

/**
 * Represents a file or directory in the workspace tree.
 */
data class FileNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long = 0L,
    val size: Long = 0L,
    /** Relative path from workspace root, e.g. "docs/api/guide.md" */
    val relativePath: String = "",
) {
    val isMarkdown: Boolean
        get() = !isDirectory && MARKDOWN_EXTENSIONS.contains(name.substringAfterLast('.', "").lowercase())

    val extension: String
        get() = name.substringAfterLast('.', "")

    companion object {
        val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
        val SUPPORTED_EXTENSIONS = MARKDOWN_EXTENSIONS + setOf(
            "txt", "text", "json", "yaml", "yml", "toml", "xml",
            "csv", "log", "ini", "cfg", "conf",
        )
    }
}
