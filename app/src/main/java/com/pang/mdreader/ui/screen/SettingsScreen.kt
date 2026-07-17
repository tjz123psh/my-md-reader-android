package com.pang.mdreader.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pang.mdreader.data.UpdateChecker
import com.pang.mdreader.data.UpdateInfo
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    readerViewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTheme by readerViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Theme section
            Text(
                text = "阅读主题",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column {
                    ReaderTheme.entries.forEachIndexed { index, theme ->
                        ThemeOption(
                            theme = theme,
                            isSelected = currentTheme.theme == theme,
                            onClick = { readerViewModel.setTheme(theme) },
                        )
                        if (index < ReaderTheme.entries.size - 1) {
                            // No divider needed with card containment
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About section
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MD Reader",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本 1.1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本地 Markdown 阅读器，支持 .md / .markdown / .mdown / .mkd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Check for updates
            Text(
                text = "检查更新",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !checkingUpdate) {
                            if (!checkingUpdate) {
                                checkingUpdate = true
                                scope.launch {
                                    updateInfo = UpdateChecker.check()
                                    checkingUpdate = false
                                }
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (checkingUpdate) "正在检查..." else "检查新版本",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "当前版本 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (checkingUpdate) {
                        Spacer(modifier = Modifier.size(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }

    // Update dialog
    if (updateInfo != null) {
        if (updateInfo!!.hasUpdate) {
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                title = { Text("有新版本 ${updateInfo!!.latestVersion}") },
                text = {
                    Text(updateInfo!!.releaseNotes.ifEmpty { "发现新版本，是否下载更新？" })
                },
                confirmButton = {
                    Button(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(updateInfo!!.downloadUrl)
                        )
                        context.startActivity(intent)
                        updateInfo = null
                    }) {
                        Text("下载")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) {
                        Text("取消")
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                title = { Text("已是最新版本") },
                text = { Text("当前版本 1.0.0 已是最新。") },
                confirmButton = {
                    Button(onClick = { updateInfo = null }) {
                        Text("确定")
                    }
                },
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: ReaderTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (theme) {
                    ReaderTheme.WARM_LIGHT -> "暖色纸张背景，适合白天阅读"
                    ReaderTheme.WARM_DARK -> "暖色暗色背景，保护眼睛"
                    ReaderTheme.GITHUB -> "简洁的浅色风格，参考 GitHub"
                    ReaderTheme.SYSTEM -> "跟随系统明暗设置"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
