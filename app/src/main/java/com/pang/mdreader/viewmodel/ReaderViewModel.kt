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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun loadFile(fileNode: FileNode) {
        if (fileNode.isDirectory) return
        if (fileNode.uri == currentFileUri && _state.value.content.isNotEmpty()) return

        currentFileUri = fileNode.uri
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                title = fileNode.name,
                outline = emptyList(),
                activeHeadingId = null,
            )

            settingsRepo.setLastDocument(fileNode.uri.toString())
            settingsRepo.setLastWorkspace(
                fileNode.uri.toString().substringBeforeLast("/")
            )

            val result = fileRepo.readFile(fileNode.uri)
            result.onSuccess { content ->
                _state.value = _state.value.copy(
                    content = content,
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
}
