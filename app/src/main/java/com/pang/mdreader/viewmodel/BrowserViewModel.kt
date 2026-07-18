package com.pang.mdreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pang.mdreader.data.FileRepository
import com.pang.mdreader.data.RecentFilesRepo
import com.pang.mdreader.data.SettingsRepo
import com.pang.mdreader.model.FileNode
import com.pang.mdreader.model.RecentFile
import kotlinx.coroutines.Job
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
    val loadedDirs: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)
    private val settingsRepo = SettingsRepo(application)
    private val recentFilesRepo = RecentFilesRepo(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val recentFiles: StateFlow<List<RecentFile>> = recentFilesRepo.recentFilesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _lastWorkspaceUri = MutableStateFlow<String?>(null)
    val lastWorkspaceUri: StateFlow<String?> = _lastWorkspaceUri.asStateFlow()

    init {
        viewModelScope.launch {
            val uriStr = settingsRepo.lastWorkspaceFlow.stateIn(viewModelScope).value
            _lastWorkspaceUri.value = uriStr
            if (uriStr != null && uriStr.isNotEmpty()) {
                openWorkspace(Uri.parse(uriStr))
            }
        }
    }

    private var loadJob: Job? = null

    fun openWorkspace(uri: Uri) {
        val treeUri = fileRepo.normalizeTreeUri(uri)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = BrowserUiState(
                workspaceUri = treeUri,
                workspaceName = fileRepo.getFileName(treeUri),
                isLoading = true,
            )
            settingsRepo.setLastWorkspace(treeUri.toString())

            try {
                val children = fileRepo.listDirectory(treeUri, treeUri)
                _uiState.value = _uiState.value.copy(
                    files = children,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无法打开文件夹: ${e.message}",
                )
            }
        }
    }

    fun toggleDirectory(relativePath: String) {
        val state = _uiState.value
        if (state.expandedDirs.contains(relativePath)) {
            // Collapse: remove children and grandchildren
            val prefix = "$relativePath/"
            _uiState.value = state.copy(
                files = state.files.filter { !it.relativePath.startsWith(prefix) },
                expandedDirs = state.expandedDirs - relativePath,
                loadedDirs = state.loadedDirs - relativePath,
            )
        } else {
            if (state.loadedDirs.contains(relativePath)) {
                // Already loaded, just expand
                _uiState.value = state.copy(
                    expandedDirs = state.expandedDirs + relativePath,
                )
            } else {
                // Load children lazily
                val dirNode = state.files.find { it.relativePath == relativePath } ?: return
                val rootUri = state.workspaceUri ?: return
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    try {
                        val children = fileRepo.listDirectory(dirNode.uri, rootUri)
                        val prefixed = children.map { child ->
                            child.copy(relativePath = "$relativePath/${child.relativePath}")
                        }
                        _uiState.value = _uiState.value.copy(
                            files = _uiState.value.files + prefixed,
                            expandedDirs = _uiState.value.expandedDirs + relativePath,
                            loadedDirs = _uiState.value.loadedDirs + relativePath,
                        )
                    } catch (_: Exception) {
                        // Silently fail — directory won't expand
                    }
                }
            }
        }
    }

    fun clearWorkspace() {
        loadJob?.cancel()
        _uiState.value = BrowserUiState()
        viewModelScope.launch {
            settingsRepo.setLastWorkspace(null)
        }
    }

    fun addRecentFile(fileNode: FileNode) {
        viewModelScope.launch {
            recentFilesRepo.addRecent(
                RecentFile(
                    uri = fileNode.uri.toString(),
                    name = fileNode.name,
                    workspaceUri = (_uiState.value.workspaceUri?.toString() ?: ""),
                )
            )
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch {
            recentFilesRepo.clearAll()
        }
    }
}
