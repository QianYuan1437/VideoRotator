package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.videorotator.R
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.ROTATION_UNKNOWN
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortKey(@StringRes val labelRes: Int) {
    DATE_DESC(R.string.sort_date_desc),
    DATE_ASC(R.string.sort_date_asc),
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    SIZE_DESC(R.string.sort_size_desc),
    DURATION_DESC(R.string.sort_duration_desc)
}

/** 视频列表筛选：全部 / 未转换 / 已转换（含已转换的原视频和转换产物） */
enum class VideoFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    UNCONVERTED(R.string.filter_unconverted),
    CONVERTED(R.string.filter_converted)
}

data class FileBrowserState(
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    /** 是否已经扫描过（关闭「自动扫描」时，启动后需要手动刷新） */
    val hasLoaded: Boolean = false,
    val currentDirectory: String = "",
    val errorMessage: String? = null,
    val hasPermission: Boolean = false,
    val sortKey: SortKey = SortKey.DATE_DESC,
    val filter: VideoFilter = VideoFilter.ALL,
    val dedupe: Boolean = false,
    val isSelectMode: Boolean = false,
    val selectedUris: Set<String> = emptySet(),
    val lastPlayedParent: String? = null,
    val pickedTreeUri: Uri? = null,
    // 记录最近播放的视频 URI，回到列表时定位滚动到该卡片
    val lastPlayedVideoUri: String? = null
)

class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private var rawVideos: List<VideoInfo> = emptyList()
    private var loadJob: Job? = null
    private var rotationJob: Job? = null

    init {
        val ctx = getApplication<Application>()
        _state.value = _state.value.copy(
            currentDirectory = VideoUtils.getDefaultVideoDirectory(),
            filter = AppPrefs.getListFilter(ctx)
                ?.let { runCatching { VideoFilter.valueOf(it) }.getOrNull() } ?: VideoFilter.ALL,
            dedupe = AppPrefs.getListDedupe(ctx)
        )
    }

    fun setDirectory(path: String) {
        _state.update { it.copy(currentDirectory = path, pickedTreeUri = null) }
        loadVideos()
    }

    /** 重新扫描当前目录（或当前选中的 SAF 文件夹） */
    fun loadVideos() {
        val tree = _state.value.pickedTreeUri
        if (tree != null) {
            loadVideosFromTreeUri(tree)
            return
        }
        launchLoad {
            val context = getApplication<Application>()
            val dir = _state.value.currentDirectory
            // MediaStore 查询与私有目录扫描并行
            val mediaStore = async(Dispatchers.IO) { VideoUtils.scanVideos(context, dir) }
            // 合并应用私有 /rotated 目录下的转换产物（MediaStore 不索引 Android/data/<pkg>/files/）
            val converted = async(Dispatchers.IO) { VideoUtils.scanConvertedDir(context) }
            (mediaStore.await() + converted.await()).distinctBy { it.uri.toString() }
        }
    }

    private fun launchLoad(block: suspend kotlinx.coroutines.CoroutineScope.() -> List<VideoInfo>) {
        // 新的扫描开始前取消上一次，避免快速切换目录时旧结果覆盖新结果
        loadJob?.cancel()
        rotationJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                rawVideos = block()
                applySort()
                _state.update { it.copy(isLoading = false, hasLoaded = true) }
                fillMissingRotation()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, hasLoaded = true, errorMessage = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /**
     * Android 10 及以下 MediaStore 不提供视频旋转角：列表先按未知显示，
     * 再在后台（限 4 并发）逐个读取，读完一次性刷新 —— 不阻塞首屏。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun fillMissingRotation() {
        val unknown = rawVideos.filter { it.rotation == ROTATION_UNKNOWN }
        if (unknown.isEmpty()) return
        val context = getApplication<Application>()
        rotationJob = viewModelScope.launch {
            val io = Dispatchers.IO.limitedParallelism(4)
            val resolved = unknown.map { v ->
                async(io) { v.uri.toString() to VideoUtils.getRotation(context, v.uri) }
            }.map { it.await() }.toMap()
            rawVideos = rawVideos.map { v ->
                resolved[v.uri.toString()]?.let { v.copy(rotation = it) } ?: v
            }
            applySort()
        }
    }

    private var lastOutputRevision = -1

    /**
     * 转换产物增删（转换完成、反向重转、删除文件）后重扫，让列表与磁盘一致。
     * 只在版本号真正变化时触发 —— 页面重新进入（如从播放页返回）不会重扫。
     */
    fun syncOutputRevision(revision: Int) {
        val changed = lastOutputRevision >= 0 && revision != lastOutputRevision
        lastOutputRevision = revision
        if (changed && _state.value.hasLoaded) loadVideos()
    }

    fun setSortKey(key: SortKey) {
        _state.update { it.copy(sortKey = key) }
        applySort()
    }

    fun setFilter(filter: VideoFilter) {
        _state.update { it.copy(filter = filter, selectedUris = emptySet()) }
        AppPrefs.setListFilter(getApplication(), filter.name)
    }

    fun setDedupe(enabled: Boolean) {
        _state.update { it.copy(dedupe = enabled, selectedUris = emptySet()) }
        AppPrefs.setListDedupe(getApplication(), enabled)
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
        _state.update { it.copy(videos = sorted) }
    }

    /**
     * 权限结果回调。只在状态变化时触发扫描 —— 之前 MainActivity 每次重组都会调用一次，
     * 切主题 / 返回列表都会整盘重扫。
     */
    fun setPermissionGranted(granted: Boolean) {
        val before = _state.value
        _state.update { it.copy(hasPermission = granted) }
        if (granted && !before.hasLoaded && !before.isLoading &&
            AppPrefs.getAutoScan(getApplication())
        ) {
            loadVideos()
        }
    }

    fun navigateToParent(): Boolean {
        if (_state.value.pickedTreeUri != null) {
            // 当前浏览的是 SAF 文件夹：外部存储的文件夹回到其上级路径，其他提供方回到默认目录
            val path = _state.value.pickedTreeUri
                ?.takeIf { it.authority == "com.android.externalstorage.documents" }
                ?.let { VideoUtils.treeDocIdToPath(VideoUtils.describeTreeUri(it)) }
            setDirectory(path?.let { File(it).parent } ?: VideoUtils.getDefaultVideoDirectory())
            return true
        }
        val parent = File(_state.value.currentDirectory).parent
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
        _state.update { it.copy(isSelectMode = !it.isSelectMode, selectedUris = emptySet()) }
    }

    fun exitSelectMode() {
        _state.update { it.copy(isSelectMode = false, selectedUris = emptySet()) }
    }

    fun enterSelectMode(initial: Uri) {
        _state.update { it.copy(isSelectMode = true, selectedUris = setOf(initial.toString())) }
    }

    fun toggleSelect(uri: Uri) {
        val key = uri.toString()
        _state.update { s -> s.copy(selectedUris = if (key in s.selectedUris) s.selectedUris - key else s.selectedUris + key) }
    }

    /** 全选当前可见（已筛选 / 去重后）的视频 */
    fun selectAll(visible: List<VideoInfo>) {
        _state.update { it.copy(selectedUris = visible.map { v -> v.uri.toString() }.toSet()) }
    }

    fun deselectAll() {
        _state.update { it.copy(selectedUris = emptySet()) }
    }

    fun selectedVideos(): List<VideoInfo> {
        val keys = _state.value.selectedUris
        return _state.value.videos.filter { keys.contains(it.uri.toString()) }
    }

    /**
     * 记录最近播放的视频所在目录，供路径栏点击跳转。
     * 直接用扫描时拿到的路径，不再在点击时同步查询 ContentResolver（会卡住进入播放页）。
     */
    fun rememberPlayedVideo(video: VideoInfo) {
        val parent = video.path?.let { File(it).parent }
        _state.update {
            it.copy(
                lastPlayedParent = parent ?: it.lastPlayedParent,
                lastPlayedVideoUri = video.uri.toString()
            )
        }
    }

    /** UI 已消费完滚动定位，清除标记避免重复滚动 */
    fun consumeLastPlayedUri() {
        if (_state.value.lastPlayedVideoUri != null) {
            _state.update { it.copy(lastPlayedVideoUri = null) }
        }
    }

    /** 跳转到上次播放视频所在的目录（如与当前相同则忽略） */
    fun jumpToLastPlayedParent() {
        val target = _state.value.lastPlayedParent ?: return
        if (target != _state.value.currentDirectory || _state.value.pickedTreeUri != null) {
            setDirectory(target)
        }
    }

    /**
     * 用户在系统文件选择器里选了一个要浏览的文件夹（SAF treeUri），扫描该子树下的视频。
     * 注意：这与「转换配置」里的自定义输出目录是两回事，之前两者共用一个状态，
     * 选浏览目录会顺带改掉输出目录，反之亦然。
     */
    fun setPickedTreeUri(uri: Uri) {
        _state.update {
            it.copy(pickedTreeUri = uri, currentDirectory = VideoUtils.describeTreeUri(uri))
        }
        loadVideosFromTreeUri(uri)
    }

    /** 清除已选的 SAF 目录，回到默认 MediaStore 扫描 */
    fun clearPickedTreeUri() {
        setDirectory(VideoUtils.getDefaultVideoDirectory())
    }

    private fun loadVideosFromTreeUri(uri: Uri) {
        launchLoad {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { VideoUtils.scanVideosFromTreeUri(context, uri) }
        }
    }
}
