package com.videorotator.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileBrowserState(
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val currentDirectory: String = "",
    val errorMessage: String? = null,
    val hasPermission: Boolean = false
)

class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            currentDirectory = VideoUtils.getDefaultVideoDirectory()
        )
    }

    fun setDirectory(path: String) {
        _state.value = _state.value.copy(currentDirectory = path)
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val context = getApplication<Application>()
                val dir = _state.value.currentDirectory
                val videos = withContext(Dispatchers.IO) {
                    VideoUtils.scanVideos(context, dir)
                }
                _state.value = _state.value.copy(
                    videos = videos,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败"
                )
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
        if (granted) loadVideos()
    }

    fun navigateToParent(): Boolean {
        val current = _state.value.currentDirectory
        val parent = File(current).parent
        if (parent != null) {
            setDirectory(parent)
            return true
        }
        return false
    }

    fun navigateToDirectory(path: String) {
        setDirectory(path)
    }
}
