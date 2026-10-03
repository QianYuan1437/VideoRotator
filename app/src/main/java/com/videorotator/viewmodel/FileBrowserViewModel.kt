package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
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

enum class SortKey(val label: String) {
    DATE_DESC("最新优先"),
    DATE_ASC("最旧优先"),
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A"),
    SIZE_DESC("最大优先"),
    DURATION_DESC("时长最长")
}

data class FileBrowserState(
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val currentDirectory: String = "",
    val errorMessage: String? = null,
    val hasPermission: Boolean = false,
    val sortKey: SortKey = SortKey.DATE_DESC,
    val isSelectMode: Boolean = false,
    val selectedUris: Set<String> = emptySet(),
    val lastPlayedParent: String? = null,
    val pickedTreeUri: Uri? = null
)

class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private var rawVideos: List<VideoInfo> = emptyList()

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
                val mediaStoreVideos = withContext(Dispatchers.IO) {
                    VideoUtils.scanVideos(context, dir)
                }
                // 合并应用私有 /rotated 目录下的转换产物
                // （MediaStore 默认不索引 Android/data/<pkg>/files/，需单独扫描）
                val convertedVideos = withContext(Dispatchers.IO) {
                    VideoUtils.scanConvertedDir(context)
                }
                // 按 uri 去重（同文件可能被两条路径都找到）
                rawVideos = (mediaStoreVideos + convertedVideos)
                    .distinctBy { it.uri.toString() }
                applySort()
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败"
                )
            }
        }
    }

    fun setSortKey(key: SortKey) {
        _state.value = _state.value.copy(sortKey = key)
        applySort()
    }

    private fun applySort() {
        val key = _state.value.sortKey
        val sorted = when (key) {
            SortKey.DATE_DESC -> rawVideos.sortedByDescending { it.dateAdded }
            SortKey.DATE_ASC -> rawVideos.sortedBy { it.dateAdded }
            SortKey.NAME_ASC -> rawVideos.sortedBy { it.displayName.lowercase() }
            SortKey.NAME_DESC -> rawVideos.sortedByDescending { it.displayName.lowercase() }
            SortKey.SIZE_DESC -> rawVideos.sortedByDescending { it.size }
            SortKey.DURATION_DESC -> rawVideos.sortedByDescending { it.duration }
        }
        _state.value = _state.value.copy(videos = sorted)
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

    fun toggleSelectMode() {
        _state.value = _state.value.copy(
            isSelectMode = !_state.value.isSelectMode,
            selectedUris = emptySet()
        )
    }

    fun exitSelectMode() {
        _state.value = _state.value.copy(isSelectMode = false, selectedUris = emptySet())
    }

    fun toggleSelect(uri: Uri) {
        val s = _state.value.selectedUris.toMutableSet()
        val key = uri.toString()
        if (s.contains(key)) s.remove(key) else s.add(key)
        _state.value = _state.value.copy(selectedUris = s)
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedUris = _state.value.videos.map { it.uri.toString() }.toSet()
        )
    }

    fun deselectAll() {
        _state.value = _state.value.copy(selectedUris = emptySet())
    }

    fun selectedVideos(): List<VideoInfo> {
        val keys = _state.value.selectedUris
        return _state.value.videos.filter { keys.contains(it.uri.toString()) }
    }

    /** 记录最近播放的视频所在目录，供路径栏点击跳转 */
    fun rememberPlayedVideo(uri: Uri) {
        val parent = VideoUtils.getParentPath(getApplication(), uri) ?: return
        if (_state.value.lastPlayedParent != parent) {
            _state.value = _state.value.copy(lastPlayedParent = parent)
        }
    }

    /** 跳转到上次播放视频所在的目录（如与当前相同则忽略） */
    fun jumpToLastPlayedParent() {
        val target = _state.value.lastPlayedParent ?: return
        if (target != _state.value.currentDirectory) {
            setDirectory(target)
        }
    }

    /**
     * 用户在系统文件选择器里选了一个文件夹（SAF treeUri）。
     * 清空 lastPlayedParent 提示，扫描该子树下的视频。
     */
    fun setPickedTreeUri(uri: Uri) {
        _state.value = _state.value.copy(pickedTreeUri = uri)
        loadVideosFromTreeUri(uri)
    }

    /** 清除已选的 SAF 目录，回到默认 MediaStore 扫描 */
    fun clearPickedTreeUri() {
        _state.value = _state.value.copy(pickedTreeUri = null, currentDirectory = VideoUtils.getDefaultVideoDirectory())
        loadVideos()
    }

    private fun loadVideosFromTreeUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val context = getApplication<Application>()
                val displayPath = VideoUtils.describeTreeUri(uri)
                val videos = withContext(Dispatchers.IO) {
                    VideoUtils.scanVideosFromTreeUri(context, uri)
                }
                rawVideos = videos
                applySort()
                _state.value = _state.value.copy(
                    isLoading = false,
                    currentDirectory = displayPath
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败"
                )
            }
        }
    }
}