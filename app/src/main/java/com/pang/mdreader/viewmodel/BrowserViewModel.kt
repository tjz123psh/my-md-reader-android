package com.pang.mdreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pang.mdreader.data.FileRepository
import com.pang.mdreader.data.SettingsRepo
import com.pang.mdreader.model.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowserUiState(
    val workspaceUri: Uri? = null,
    val workspaceName: String = "",
    val files: List<FileNode> = emptyList(),
    val expandedDirs: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)
    private val settingsRepo = SettingsRepo(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val lastWorkspaceUri: StateFlow<String?> = settingsRepo.lastWorkspaceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Restore last workspace
        viewModelScope.launch {
            val uriStr = settingsRepo.lastWorkspaceFlow.stateIn(viewModelScope).value
            if (uriStr != null && uriStr.isNotEmpty()) {
                val uri = Uri.parse(uriStr)
                openWorkspace(uri)
            }
        }
    }

    fun openWorkspace(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                workspaceUri = uri,
                workspaceName = fileRepo.getFileName(uri),
                isLoading = true,
                error = null,
            )

            settingsRepo.setLastWorkspace(uri.toString())

            try {
                val files = fileRepo.scanDirectory(uri)
                _uiState.value = _uiState.value.copy(
                    files = files,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无法扫描文件夹: ${e.message}",
                )
            }
        }
    }

    fun toggleDirectory(relativePath: String) {
        val current = _uiState.value.expandedDirs.toMutableSet()
        if (current.contains(relativePath)) {
            current.remove(relativePath)
        } else {
            current.add(relativePath)
        }
        _uiState.value = _uiState.value.copy(expandedDirs = current)
    }

    fun isDirectoryExpanded(relativePath: String): Boolean {
        return _uiState.value.expandedDirs.contains(relativePath)
    }

    fun clearWorkspace() {
        _uiState.value = BrowserUiState()
        viewModelScope.launch {
            settingsRepo.setLastWorkspace(null)
        }
    }
}
