package com.pang.mdreader.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.pang.mdreader.model.FileNode
import com.pang.mdreader.model.OutlineItem
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.ui.component.MarkdownView
import com.pang.mdreader.ui.component.OutlinePanel
import com.pang.mdreader.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    fileNode: FileNode?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val outline by viewModel.outline.collectAsState()

    var showOutline by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var scrollToHeading by remember { mutableStateOf<String?>(null) }
    var scrollToLine by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    // Theme toggle
                    IconButton(onClick = {
                        viewModel.setTheme(
                            when (state.theme) {
                                ReaderTheme.WARM_LIGHT -> ReaderTheme.WARM_DARK
                                ReaderTheme.WARM_DARK -> ReaderTheme.GITHUB
                                ReaderTheme.GITHUB -> ReaderTheme.WARM_LIGHT
                                ReaderTheme.SYSTEM -> ReaderTheme.WARM_LIGHT
                            }
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "切换主题",
                        )
                    }

                    // Zoom controls
                    IconButton(onClick = { viewModel.setZoom(state.zoom - 5) }) {
                        Icon(
                            imageVector = Icons.Default.TextDecrease,
                            contentDescription = "缩小",
                        )
                    }

                    Text(
                        text = "${state.zoom}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    IconButton(onClick = { viewModel.setZoom(state.zoom + 5) }) {
                        Icon(
                            imageVector = Icons.Default.TextIncrease,
                            contentDescription = "放大",
                        )
                    }

                    // Toggle search
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                        )
                    }

                    // Toggle outline
                    IconButton(onClick = { showOutline = !showOutline }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "目录",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically(),
                exit = slideOutVertically(),
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        showSearch = false
                        searchQuery = ""
                    },
                )
            }

            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    state.error != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.error ?: "未知错误",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("返回")
                            }
                        }
                    }

                    state.content.isNotEmpty() -> {
                        // Markdown reader WebView
                        MarkdownView(
                            markdownContent = state.content,
                            theme = state.theme,
                            zoom = state.zoom,
                            activeHeadingId = state.activeHeadingId,
                            onDocumentLoaded = { viewModel.onDocumentLoaded() },
                            onActiveHeadingChanged = { viewModel.setActiveHeading(it) },
                            onZoomChanged = { viewModel.setZoom(it) },
                            onHeadingsReady = { viewModel.setOutline(it) },
                            onScrollToHeading = scrollToHeading,
                            onScrollToLine = scrollToLine,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Clear scroll triggers after consumption
                        scrollToHeading = null
                        scrollToLine = null
                    }
                }

                // Outline overlay panel (right side)
                if (showOutline && outline.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.7f)
                            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "目录",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    IconButton(onClick = { showOutline = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭")
                                    }
                                }
                                HorizontalDivider()
                                OutlinePanel(
                                    outline = outline,
                                    activeHeadingId = state.activeHeadingId,
                                    onHeadingClick = {
                                        scrollToHeading = it
                                        showOutline = false
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清除")
                    }
                }
            },
        )
    }
}
