package com.pang.mdreader.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pang.mdreader.model.OutlineItem
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.ui.component.MarkdownView
import com.pang.mdreader.ui.component.OutlinePanel
import com.pang.mdreader.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    fileNode: com.pang.mdreader.model.FileNode?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val outline by viewModel.outline.collectAsState()

    var showOutline by remember { mutableStateOf(false) }
    var scrollToHeading by remember { mutableStateOf<String?>(null) }
    var scrollToLine by remember { mutableStateOf<Int?>(null) }

    // React to search navigation
    LaunchedEffect(scrollToLine) {
        // Consumed by MarkdownView
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Reader content fills the whole screen
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

        // Top bar (animated)
        AnimatedVisibility(
            visible = !state.isFullscreen && state.content.isNotEmpty() && !state.isLoading,
            enter = slideInVertically(spring()) { -it },
            exit = slideOutVertically(spring()) { -it },
        ) {
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
                        Icon(Icons.Default.BrightnessMedium, contentDescription = "切换主题")
                    }
                    IconButton(onClick = { viewModel.setZoom(state.zoom - 5) }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "缩小")
                    }
                    Text(
                        text = "${state.zoom}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { viewModel.setZoom(state.zoom + 5) }) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "放大")
                    }
                    IconButton(onClick = { viewModel.toggleFullscreen() }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "全屏")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        // Bottom toolbar
        AnimatedVisibility(
            visible = !state.isFullscreen && state.content.isNotEmpty() && !state.isLoading,
            enter = slideInVertically(spring()) { it },
            exit = slideOutVertically(spring()) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Search
                    IconButton(onClick = {
                        viewModel.performSearch(state.searchQuery)
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }

                    // Zoom out
                    IconButton(onClick = { viewModel.setZoom(state.zoom - 5) }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "缩小")
                    }

                    Text(
                        text = "${state.zoom}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Zoom in
                    IconButton(onClick = { viewModel.setZoom(state.zoom + 5) }) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "放大")
                    }

                    // Theme
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
                        Icon(Icons.Default.BrightnessMedium, contentDescription = "切换主题")
                    }

                    // Outline
                    IconButton(onClick = { showOutline = !showOutline }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "目录",
                        )
                    }

                    // Fullscreen
                    IconButton(onClick = { viewModel.toggleFullscreen() }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "全屏")
                    }
                }
            }
        }

        // Search panel (drop-down from top)
        AnimatedVisibility(
            visible = state.searchQuery.isNotEmpty(),
            enter = fadeIn(spring()),
            exit = fadeOut(spring()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SearchResultsPanel(
                query = state.searchQuery,
                results = state.searchResults,
                currentIndex = state.searchCurrentIndex,
                onQueryChange = { viewModel.performSearch(it) },
                onNext = {
                    scrollToLine = viewModel.goToNextSearchResult()
                },
                onPrev = {
                    scrollToLine = viewModel.goToPrevSearchResult()
                },
                onClose = { viewModel.performSearch("") },
            )
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

@Composable
private fun SearchResultsPanel(
    query: String,
    results: List<com.pang.mdreader.model.SearchResult>,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onPrev, enabled = results.isNotEmpty()) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onNext, enabled = results.isNotEmpty()) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                }
            }

            if (results.isNotEmpty()) {
                Text(
                    text = "${currentIndex + 1} / ${results.size} 个结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 4.dp),
                    state = rememberLazyListState(),
                ) {
                    items(results) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${result.line}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.width(32.dp),
                            )
                            Text(
                                text = result.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
