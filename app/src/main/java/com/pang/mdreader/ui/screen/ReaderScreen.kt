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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.ui.component.MarkdownView
import com.pang.mdreader.ui.component.OutlinePanel
import com.pang.mdreader.viewmodel.ReaderViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showSearchBar by remember { mutableStateOf(false) }

    // Scroll-to-line/heading via incrementing counters (avoids Compose batching issue)
    var scrollTargetLine by remember { mutableStateOf<Int?>(null) }
    var scrollLineCounter by remember { mutableStateOf(0) }
    var scrollTargetHeading by remember { mutableStateOf<String?>(null) }
    var scrollHeadingCounter by remember { mutableStateOf(0) }

    fun requestScrollToLine(line: Int?) {
        scrollTargetLine = line
        scrollLineCounter++
    }

    fun requestScrollToHeading(headingId: String?) {
        scrollTargetHeading = headingId
        scrollHeadingCounter++
    }

    // Controls auto-hide for fullscreen
    var controlsVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleAutoHide() {
        hideJob?.cancel()
        if (state.isFullscreen) {
            hideJob = scope.launch {
                delay(3000)
                controlsVisible = false
            }
        }
    }

    fun showControls() {
        controlsVisible = true
        scheduleAutoHide()
    }

    // Start/stop auto-hide when fullscreen state changes
    LaunchedEffect(state.isFullscreen) {
        if (state.isFullscreen) {
            controlsVisible = false
        } else {
            hideJob?.cancel()
            controlsVisible = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Tap detection for fullscreen mode — watch without consuming
            .pointerInput(state.isFullscreen) {
                if (!state.isFullscreen) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed && !change.previousPressed) {
                            val up = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val upChange = up.changes.firstOrNull()
                            if (upChange != null && !upChange.pressed) {
                                scope.launch {
                                    showControls()
                                }
                            }
                        }
                    }
                }
            },
    ) {
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
                // In non-fullscreen mode, add top padding to avoid content behind status bar
                val contentModifier = if (!state.isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                        )
                        .padding(top = 48.dp)
                } else {
                    Modifier.fillMaxSize()
                }
                MarkdownView(
                    markdownContent = state.content,
                    theme = state.theme,
                    zoom = state.zoom,
                    activeHeadingId = state.activeHeadingId,
                    onDocumentLoaded = { viewModel.onDocumentLoaded() },
                    onActiveHeadingChanged = { viewModel.setActiveHeading(it) },
                    onZoomChanged = { viewModel.setZoom(it) },
                    onHeadingsReady = { viewModel.setOutline(it) },
                    onScrollToHeading = scrollTargetHeading,
                    onScrollToLine = scrollTargetLine,
                    modifier = contentModifier,
                )
                // Use LaunchedEffect keyed by counters (handled after return)
            }
        }

        // Top bar — compact: only back + title
        val topBarVisible = if (state.isFullscreen) controlsVisible else true
        AnimatedVisibility(
            visible = topBarVisible && state.content.isNotEmpty() && !state.isLoading,
            enter = slideInVertically(spring()) { -it },
            exit = slideOutVertically(spring()) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Bottom toolbar — all controls here
        val bottomBarVisible = if (state.isFullscreen) controlsVisible else true
        AnimatedVisibility(
            visible = bottomBarVisible && state.content.isNotEmpty() && !state.isLoading,
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
                    SearchIconButton(
                        showSearchBar = showSearchBar,
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) viewModel.performSearch("")
                            showControls()
                        },
                    )

                    // Zoom out
                    IconButton(onClick = { viewModel.setZoom(state.zoom - 5); showControls() }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "缩小")
                    }

                    Text(
                        text = "${state.zoom}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Zoom in
                    IconButton(onClick = { viewModel.setZoom(state.zoom + 5); showControls() }) {
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
                        showControls()
                    }) {
                        Icon(Icons.Default.BrightnessMedium, contentDescription = "切换主题")
                    }

                    // Outline
                    IconButton(onClick = { showOutline = !showOutline; showControls() }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
                    }

                    // Fullscreen toggle
                    IconButton(onClick = {
                        viewModel.toggleFullscreen()
                        controlsVisible = false
                    }) {
                        Icon(
                            if (state.isFullscreen) Icons.Default.Close else Icons.Default.Fullscreen,
                            contentDescription = if (state.isFullscreen) "退出全屏" else "全屏",
                        )
                    }
                }
            }
        }

        // Search panel
        AnimatedVisibility(
            visible = showSearchBar || state.searchQuery.isNotEmpty(),
            enter = fadeIn(spring()),
            exit = fadeOut(spring()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (!state.isFullscreen || topBarVisible)
                        Modifier.padding(top = 64.dp)
                    else
                        Modifier
                ),
        ) {
            SearchResultsPanel(
                query = state.searchQuery,
                results = state.searchResults,
                currentIndex = state.searchCurrentIndex,
                onQueryChange = { viewModel.performSearch(it) },
                onNext = {
                    requestScrollToLine(viewModel.goToNextSearchResult())
                    showControls()
                },
                onPrev = {
                    requestScrollToLine(viewModel.goToPrevSearchResult())
                    showControls()
                },
                onClose = {
                    showSearchBar = false
                    viewModel.performSearch("")
                },
            )
        }

        // Scroll-to-line via LaunchedEffect with counter key
        LaunchedEffect(scrollLineCounter) {
            val line = scrollTargetLine ?: return@LaunchedEffect
            if (line > 0) {
                // Will be picked up by MarkdownView DisposableEffect
            }
        }

        // Scroll-to-heading via LaunchedEffect with counter key
        LaunchedEffect(scrollHeadingCounter) {
            val heading = scrollTargetHeading ?: return@LaunchedEffect
            if (heading.isNotEmpty()) {
                // Will be picked up by MarkdownView DisposableEffect
            }
        }

        // Outline overlay panel
        if (showOutline && outline.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.7f)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
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
                                requestScrollToHeading(it)
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
private fun RowScope.SearchIconButton(
    showSearchBar: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
            contentDescription = "搜索",
        )
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
                    items(results.size) { index ->
                        val result = results[index]
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
