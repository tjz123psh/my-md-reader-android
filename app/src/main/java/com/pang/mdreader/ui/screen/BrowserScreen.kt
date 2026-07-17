package com.pang.mdreader.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pang.mdreader.model.FileNode
import com.pang.mdreader.ui.component.FileTree
import com.pang.mdreader.viewmodel.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onFileSelected: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.openWorkspace(uri)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            uiState.workspaceUri == null -> {
                EmptyWorkspaceState(
                    onOpenFolder = { folderPicker.launch(null) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.files.isEmpty() -> {
                EmptyFilesState(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                FileTree(
                    files = uiState.files,
                    expandedDirs = uiState.expandedDirs,
                    selectedFile = null,
                    onToggleDir = { viewModel.toggleDirectory(it) },
                    onSelectFile = { onFileSelected(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkspaceState(
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "MD Reader",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "选择一个包含 Markdown 文件的文件夹开始阅读",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onOpenFolder,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "打开文件夹",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun EmptyFilesState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "未找到 Markdown 文件",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "工作区中没有 .md 文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
