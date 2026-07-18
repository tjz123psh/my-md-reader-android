package com.pang.mdreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pang.mdreader.data.FileRepository
import com.pang.mdreader.data.SettingsRepo
import com.pang.mdreader.model.FileNode
import com.pang.mdreader.model.OutlineItem
import com.pang.mdreader.model.ReaderState
import com.pang.mdreader.model.ReaderTheme
import com.pang.mdreader.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)
    private val settingsRepo = SettingsRepo(application)

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineItem>>(emptyList())
    val outline: StateFlow<List<OutlineItem>> = _outline.asStateFlow()

    val savedTheme: StateFlow<ReaderTheme> = settingsRepo.themeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.WARM_LIGHT)

    val savedZoom: StateFlow<Int> = settingsRepo.zoomFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepo.DEFAULT_ZOOM)

    private var currentFileUri: Uri? = null
    private var currentRootTreeUri: Uri? = null

    init {
        viewModelScope.launch {
            val saved = settingsRepo.getTheme()
            _state.value = _state.value.copy(theme = saved)
        }
        viewModelScope.launch {
            val saved = settingsRepo.getZoom()
            _state.value = _state.value.copy(zoom = saved)
        }
    }

    fun loadFile(fileNode: FileNode, rootTreeUri: Uri? = null) {
        if (fileNode.isDirectory) return
        if (
            fileNode.uri == currentFileUri &&
            rootTreeUri == currentRootTreeUri &&
            _state.value.content.isNotEmpty()
        ) return

        currentFileUri = fileNode.uri
        currentRootTreeUri = rootTreeUri
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                title = fileNode.name,
                outline = emptyList(),
                activeHeadingId = null,
                searchResults = emptyList(),
                searchCurrentIndex = -1,
                searchQuery = "",
                isFullscreen = false,
            )

            settingsRepo.setLastDocument(fileNode.uri.toString())
            // Never derive a workspace by truncating a document URI: encoded SAF document
            // IDs contain no path separators, so that produces an invalid tree URI.
            if (rootTreeUri != null) {
                settingsRepo.setLastWorkspace(rootTreeUri.toString())
            }

            val result = fileRepo.readFile(fileNode.uri)
            result.onSuccess { content ->
                // Resolve image references to SAF content URIs streamed by the WebView.
                val processed = fileRepo.resolveImageUris(content, fileNode.uri, rootTreeUri)
                _state.value = _state.value.copy(
                    content = processed,
                    isLoading = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "无法读取文件: ${error.message}",
                )
            }
        }
    }

    fun setZoom(zoom: Int) {
        _state.value = _state.value.copy(zoom = zoom.coerceIn(75, 200))
        viewModelScope.launch {
            settingsRepo.setZoom(zoom)
        }
    }

    fun setTheme(theme: ReaderTheme) {
        _state.value = _state.value.copy(theme = theme)
        viewModelScope.launch {
            settingsRepo.setTheme(theme)
        }
    }

    fun setActiveHeading(id: String?) {
        _state.value = _state.value.copy(activeHeadingId = id)
    }

    fun setOutline(items: List<OutlineItem>) {
        _outline.value = items
        _state.value = _state.value.copy(outline = items)
    }

    fun onDocumentLoaded() {
        // WebView is ready
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                searchCurrentIndex = -1,
                searchQuery = "",
            )
            return
        }
        _state.value = _state.value.copy(searchQuery = query)
        viewModelScope.launch {
            val results = withContext(Dispatchers.Default) {
                val lines = _state.value.content.lines()
                lines.mapIndexedNotNull { index, line ->
                    if (line.contains(query, ignoreCase = true)) {
                        SearchResult(line = index + 1, text = line.trim())
                    } else null
                }
            }
            _state.value = _state.value.copy(
                searchResults = results,
                searchCurrentIndex = if (results.isNotEmpty()) 0 else -1,
            )
        }
    }

    fun goToNextSearchResult(): Int? {
        val s = _state.value
        if (s.searchResults.isEmpty()) return null
        val nextIndex = (s.searchCurrentIndex + 1).coerceAtMost(s.searchResults.size - 1)
        _state.value = s.copy(searchCurrentIndex = nextIndex)
        return s.searchResults[nextIndex].line
    }

    fun goToPrevSearchResult(): Int? {
        val s = _state.value
        if (s.searchResults.isEmpty()) return null
        val prevIndex = (s.searchCurrentIndex - 1).coerceAtLeast(0)
        _state.value = s.copy(searchCurrentIndex = prevIndex)
        return s.searchResults[prevIndex].line
    }
}
