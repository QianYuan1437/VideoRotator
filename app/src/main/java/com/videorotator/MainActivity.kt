package com.videorotator

import com.videorotator.utils.AppLocale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.ThemeColor
import com.videorotator.ui.components.themeColors
import com.videorotator.ui.screens.CONVERT_TAB_CONVERTED
import com.videorotator.ui.screens.CONVERT_TAB_PROGRESS
import com.videorotator.ui.screens.ConvertConfigScreen
import com.videorotator.ui.screens.ConvertListScreen
import com.videorotator.ui.screens.ConvertScreen
import com.videorotator.ui.screens.FileBrowserScreen
import com.videorotator.ui.screens.PlayerScreen
import com.videorotator.ui.screens.SettingsScreen
import com.videorotator.ui.theme.VideoRotatorTheme
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.PermissionUtils
import com.videorotator.utils.PlayerPrefs
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoMarks
import com.videorotator.viewmodel.ConvertViewModel
import com.videorotator.viewmodel.FileBrowserViewModel
import com.videorotator.viewmodel.PlayerViewModel
import com.videorotator.viewmodel.VideoFilter

class MainActivity : AppCompatActivity() {

    companion object {
        /** 前台时不发转换完成通知 */
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        fileBrowserViewModel?.setPermissionGranted(PermissionUtils.hasPermissions(this))
    }

    private var fileBrowserViewModel: FileBrowserViewModel? = null

    // 主题状态
    private var themeColorIndex by mutableStateOf(0)
    private var backgroundMode by mutableStateOf(BackgroundMode.LIGHT)

    /**
     * 「转换配置 / 转换页」的自定义输出目录（持久化）。
     * 与视频列表浏览用的文件夹是两个独立的选择器 —— 之前共用一个，
     * 选浏览目录会顺带改掉输出目录，选输出目录又会让视频列表跳过去。
     */
    private var outputTreeUri by mutableStateOf<Uri?>(null)

    private fun takePersistable(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 部分目录不支持持久化权限，忽略
        }
    }

    /** 视频列表：选择要浏览的文件夹 */
    private val pickBrowseDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            takePersistable(uri)
            fileBrowserViewModel?.setPickedTreeUri(uri)
        }
    }

    /** 转换：选择输出文件夹 */
    private val pickOutputDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            takePersistable(uri)
            outputTreeUri = uri
            AppPrefs.setOutputTreeUri(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用语言由 AppCompat 持久化（API 33+ 交给系统，更早版本由 AppLocalesMetadataHolderService
        // 自动保存并在启动时恢复）。旧版本只存在 PlayerPrefs 里，这里迁移一次。
        if (AppLocale.currentTag().isEmpty()) {
            val legacy = PlayerPrefs.getLanguage(this)
            if (legacy == "zh" || legacy == "en") AppLocale.apply(legacy)
        }

        super.onCreate(savedInstanceState)

        // 从 SharedPreferences 读取保存的设置
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        themeColorIndex = prefs.getInt("theme_color_index", 0)
        backgroundMode = BackgroundMode.values().getOrElse(prefs.getInt("background_mode", 0)) { BackgroundMode.LIGHT }
        outputTreeUri = AppPrefs.getOutputTreeUri(this)

        setContent {
            VideoRotatorTheme(
                themeColor = themeColors.getOrElse(themeColorIndex) { themeColors[0] },
                backgroundMode = backgroundMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val fbViewModel: FileBrowserViewModel = viewModel()
                    fileBrowserViewModel = fbViewModel

                    // 只在首次进入时检查 / 申请权限（之前写在组合体里，每次重组都会触发一次全盘扫描）
                    LaunchedEffect(Unit) {
                        if (PermissionUtils.hasPermissions(this@MainActivity)) {
                            fbViewModel.setPermissionGranted(true)
                        } else {
                            requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                        }
                    }

                    MainScreen(
                        fbViewModel = fbViewModel,
                        currentThemeColor = themeColors.getOrElse(themeColorIndex) { themeColors[0] },
                        currentBackgroundMode = backgroundMode,
                        outputTreeUri = outputTreeUri,
                        onThemeColorChanged = { color ->
                            themeColorIndex = themeColors.indexOf(color).coerceAtLeast(0)
                            prefs.edit().putInt("theme_color_index", themeColorIndex).apply()
                        },
                        onBackgroundModeChanged = { mode ->
                            backgroundMode = mode
                            prefs.edit().putInt("background_mode", mode.ordinal).apply()
                        },
                        onPickBrowseDirectory = { pickBrowseDirectoryLauncher.launch(null) },
                        onPickOutputDirectory = { pickOutputDirectoryLauncher.launch(null) },
                        onClearOutputDirectory = {
                            outputTreeUri = null
                            AppPrefs.setOutputTreeUri(this, null)
                        },
                        onRequestPermission = {
                            requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isInForeground = true
        // 用户可能在系统设置里授予了权限后返回
        fileBrowserViewModel?.let { vm ->
            if (!vm.state.value.hasPermission && PermissionUtils.hasPermissions(this)) {
                vm.setPermissionGranted(true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    fbViewModel: FileBrowserViewModel,
    currentThemeColor: ThemeColor,
    currentBackgroundMode: BackgroundMode,
    outputTreeUri: Uri?,
    onThemeColorChanged: (ThemeColor) -> Unit,
    onBackgroundModeChanged: (BackgroundMode) -> Unit,
    onPickBrowseDirectory: () -> Unit,
    onPickOutputDirectory: () -> Unit,
    onClearOutputDirectory: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_videos), Icons.Filled.Movie),
        TabItem(stringResource(R.string.tab_jobs), Icons.Filled.History),
        TabItem(stringResource(R.string.nav_config), Icons.Filled.Rotate90DegreesCw),
        TabItem(stringResource(R.string.tab_settings), Icons.Filled.Settings)
    )
    // rememberSaveable：切换语言会重建 Activity，保持停留在当前 Tab
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // 「转换列表」二级 Tab 提到这里保存：从播放页返回时不会被重置
    var convertListTab by rememberSaveable { mutableIntStateOf(CONVERT_TAB_PROGRESS) }
    // 视频列表滚动位置：MainScreen 不随播放页切换销毁，返回列表时停在原处
    val videoListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // 全屏子页面状态：列表点击 -> 播放器 -> 转换页
    var playingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    var convertingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    val playerViewModel: PlayerViewModel = viewModel()
    val convertViewModel: ConvertViewModel = viewModel()
    val jobs by convertViewModel.jobs.collectAsState()
    val fbState by fbViewModel.state.collectAsState()
    val jobsSelectMode by convertViewModel.isSelectMode.collectAsState()

    val playing = playingVideo
    val converting = convertingVideo
    // 系统返回键：转换页 → 播放页 → 退出多选 → 回到第一个 Tab → 交给系统
    BackHandler(enabled = converting != null) {
        convertingVideo = null
    }
    BackHandler(enabled = playing != null && converting == null) {
        playingVideo = null
    }
    BackHandler(enabled = playing == null && converting == null && (fbState.isSelectMode || jobsSelectMode)) {
        fbViewModel.exitSelectMode()
        convertViewModel.exitSelectMode()
    }
    BackHandler(enabled = playing == null && converting == null && selectedTab != 0 &&
        !fbState.isSelectMode && !jobsSelectMode
    ) {
        selectedTab = 0
    }

    when {
        // 转换页优先（从播放器进入后，返回则回到播放器）
        converting != null -> ConvertScreen(
            videoInfo = converting,
            viewModel = convertViewModel,
            outputTreeUri = outputTreeUri,
            onPickOutputDirectory = onPickOutputDirectory,
            onBack = { convertingVideo = null },
            onStarted = {
                convertingVideo = null
                playingVideo = null
                convertListTab = CONVERT_TAB_PROGRESS
                selectedTab = 1
            }
        )

        playing != null -> PlayerScreen(
            videoInfo = playing,
            viewModel = playerViewModel,
            onBack = { playingVideo = null },
            onConvert = { convertingVideo = it }
        )

        else -> Scaffold(
            bottomBar = {
                // 底部导航栏：底部圆角裁剪 + 系统手势栏内边距，适配圆角屏幕底边
                Surface(
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            // 两侧各留 16dp，避免标签文字紧贴屏幕圆角
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                            ) {
                                val activeJobs = jobs.count { it.isActive }
                                tabs.forEachIndexed { index, tab ->
                                    NavigationBarItem(
                                        icon = {
                                            // 转换列表 Tab 显示进行中的任务数
                                            if (index == 1 && activeJobs > 0) {
                                                BadgedBox(badge = { Badge { Text("$activeJobs") } }) {
                                                    Icon(tab.icon, contentDescription = tab.title)
                                                }
                                            } else {
                                                Icon(tab.icon, contentDescription = tab.title)
                                            }
                                        },
                                        label = {
                                            Text(
                                                tab.title,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        colors = NavigationBarItemDefaults.colors(
                                            // 选中态：主题色底 + 自动对比色图标（柠檬黄等浅色主题下之前图标与底色同色看不见）
                                            selectedIconColor = com.videorotator.ui.theme.OnPrimary,
                                            selectedTextColor = com.videorotator.ui.theme.PurpleDark,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            indicatorColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                when (selectedTab) {
                    0 -> FileBrowserTab(
                        fbViewModel = fbViewModel,
                        listState = videoListState,
                        convertViewModel = convertViewModel,
                        onPickDirectory = onPickBrowseDirectory,
                        onRequestPermission = onRequestPermission,
                        onVideoClick = { video ->
                            // 记住该视频所在的目录（路径栏点击可跳回）
                            fbViewModel.rememberPlayedVideo(video)
                            playingVideo = video
                        },
                        onBatchStarted = {
                            convertListTab = CONVERT_TAB_PROGRESS
                            selectedTab = 1
                        }
                    )
                    1 -> ConvertListScreen(
                        viewModel = convertViewModel,
                        currentTab = convertListTab,
                        onTabSelected = { convertListTab = it },
                        onPlay = { playingVideo = it }
                    )
                    2 -> ConvertConfigScreen(
                        outputTreeUri = outputTreeUri,
                        onPickOutputDirectory = onPickOutputDirectory,
                        onClearOutputDirectory = onClearOutputDirectory
                    )
                    3 -> SettingsScreen(
                        currentThemeColor = currentThemeColor,
                        currentBackgroundMode = currentBackgroundMode,
                        onThemeColorChanged = onThemeColorChanged,
                        onBackgroundModeChanged = onBackgroundModeChanged
                    )
                }
            }
        }
    }
}

@Composable
fun FileBrowserTab(
    fbViewModel: FileBrowserViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    convertViewModel: ConvertViewModel,
    onPickDirectory: () -> Unit,
    onRequestPermission: () -> Unit,
    onVideoClick: (VideoInfo) -> Unit,
    onBatchStarted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by fbViewModel.state.collectAsState()
    val jobs by convertViewModel.jobs.collectAsState()
    // 产物增删后刷新列表，让新产物出现并打上标识、已删除的文件消失
    val outputRevision by convertViewModel.outputRevision.collectAsState()
    LaunchedEffect(outputRevision) { fbViewModel.syncOutputRevision(outputRevision) }

    // 标识 / 筛选 / 去重只依赖列表与任务记录，记忆计算结果，避免每次重组重复遍历
    val successJobs = remember(jobs) { jobs.filter { it.hasOutput } }
    // 筛选数量与去重开关保持一致
    val all = remember(state.videos, successJobs, state.dedupe) {
        VideoMarks.mark(state.videos, successJobs, VideoFilter.ALL, state.dedupe)
    }
    val visible = remember(state.videos, successJobs, state.filter, state.dedupe) {
        VideoMarks.mark(state.videos, successJobs, state.filter, state.dedupe)
    }
    val counts = remember(all) {
        mapOf(
            VideoFilter.ALL to all.size,
            VideoFilter.UNCONVERTED to all.count { it.mark == com.videorotator.utils.ConvertMark.NONE },
            VideoFilter.CONVERTED to all.count { it.mark != com.videorotator.utils.ConvertMark.NONE }
        )
    }
    val autoScan = remember { AppPrefs.getAutoScan(context) }

    FileBrowserScreen(
        state = state,
        listState = listState,
        items = visible,
        filterCounts = counts,
        autoScanEnabled = autoScan,
        onVideoClick = onVideoClick,
        onVideoLongClick = { video ->
            if (state.isSelectMode) fbViewModel.toggleSelect(video.uri) else fbViewModel.enterSelectMode(video.uri)
        },
        onToggleSelect = { fbViewModel.toggleSelect(it) },
        onRefresh = { fbViewModel.loadVideos() },
        onNavigateToParent = { fbViewModel.navigateToParent() },
        onNavigateToDirectory = { path -> fbViewModel.navigateToDirectory(path) },
        onSortKeySelected = { fbViewModel.setSortKey(it) },
        onFilterSelected = { fbViewModel.setFilter(it) },
        onDedupeChanged = { fbViewModel.setDedupe(it) },
        onToggleSelectMode = { fbViewModel.toggleSelectMode() },
        onSelectAll = { fbViewModel.selectAll(visible.map { it.video }) },
        onDeselectAll = { fbViewModel.deselectAll() },
        onExitSelectMode = { fbViewModel.exitSelectMode() },
        onBatchConvert = {
            // 批量转换使用「转换配置」中的输出目录和旋转方向（之前写死为 Download + 顺时针）
            val selected = fbViewModel.selectedVideos()
            if (selected.isNotEmpty()) {
                selected.forEach { video -> convertViewModel.startJobWithDefaults(video) }
                fbViewModel.exitSelectMode()
                Toast.makeText(context, context.getString(R.string.batch_added, selected.size), Toast.LENGTH_SHORT).show()
                onBatchStarted()
            }
        },
        onJumpToLastPlayed = { fbViewModel.jumpToLastPlayedParent() },
        onPickDirectory = onPickDirectory,
        onRequestPermission = onRequestPermission,
        onConsumeLastPlayedUri = { fbViewModel.consumeLastPlayedUri() }
    )
}
