package com.pang.mdreader.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pang.mdreader.model.FileNode

/**
 * Tree view of markdown files and directories.
 */
@Composable
fun FileTree(
    files: List<FileNode>,
    expandedDirs: Set<String>,
    selectedFile: FileNode?,
    onToggleDir: (String) -> Unit,
    onSelectFile: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Build tree structure: only show root-level + expanded sub-items
    val visibleNodes = buildVisibleList(files, expandedDirs)

    LazyColumn(modifier = modifier) {
        items(visibleNodes, key = { it.second.relativePath }) { (depth, node) ->
            FileTreeItem(
                node = node,
                depth = depth,
                isExpanded = node.isDirectory && expandedDirs.contains(node.relativePath),
                isSelected = selectedFile?.uri == node.uri,
                onClick = {
                    if (node.isDirectory) {
                        onToggleDir(node.relativePath)
                    } else {
                        onSelectFile(node)
                    }
                },
            )
        }
    }
}

@Composable
private fun FileTreeItem(
    node: FileNode,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + depth * 20).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = if (isExpanded) "展开文件夹" else "文件夹",
                modifier = Modifier.size(20.dp),
                tint = if (isExpanded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = if (node.isMarkdown) Icons.Default.Description
                              else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = "文件",
                modifier = Modifier.size(20.dp),
                tint = if (node.isMarkdown) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Build a flat list of (depth, FileNode) from the tree.
 */
private fun buildVisibleList(
    files: List<FileNode>,
    expandedDirs: Set<String>,
): List<Pair<Int, FileNode>> {
    val result = mutableListOf<Pair<Int, FileNode>>()

    // Build a set of paths that are in the expanded tree
    val visiblePaths = mutableSetOf<String>()

    for (file in files) {
        val path = file.relativePath
        val parts = path.split("/")
        val parentPath = if (parts.size > 1) {
            parts.dropLast(1).joinToString("/")
        } else ""

        // Check if parent is expanded
        val isDirectChild = if (parts.size == 1) true
        else {
            // Find the parent directory in our file list
            val parentDir = files.find {
                it.isDirectory && it.relativePath == parentPath
            }
            parentDir != null && expandedDirs.contains(parentPath)
        }

        if (isDirectChild || parts.size == 1) {
            val depth = parts.size - 1
            result.add(depth to file)
            visiblePaths.add(path)
        }
    }

    return result
}
