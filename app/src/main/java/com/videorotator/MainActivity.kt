package com.videorotator

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.themeColors
import com.videorotator.ui.screens.ConvertConfigScreen
import com.videorotator.ui.screens.ConvertListScreen
import com.videorotator.ui.screens.ConvertScreen
import com.videorotator.ui.screens.FileBrowserScreen
import com.videorotator.ui.screens.PlayerScreen
import com.videorotator.ui.screens.SettingsScreen
import com.videorotator.ui.theme.VideoRotatorTheme
import com.videorotator.utils.PermissionUtils
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.ConvertViewModel
import com.videorotator.viewmodel.FileBrowserViewModel
import com.videorotator.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        fileBrowserViewModel?.setPermissionGranted(allGranted)
    }

    private var fileBrowserViewModel: FileBrowserViewModel? = null

    // 主题状态
    private var themeColorIndex by mutableStateOf(0)
    private var backgroundMode by mutableStateOf(BackgroundMode.LIGHT)

    // 系统文件夹选择器选中的目录（SAF treeUri）
    private var pickedTreeUri by mutableStateOf<Uri?>(null)

    /** 系统 SAF 文件夹选择器 */
    private val pickDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 部分目录不支持持久化权限，忽略
            }
            pickedTreeUri = uri
            fileBrowserViewModel?.setPickedTreeUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 SharedPreferences 读取保存的设置
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        themeColorIndex = prefs.getInt("theme_color_index", 0)
        backgroundMode = BackgroundMode.values()[prefs.getInt("background_mode", 0)]

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

                    if (!PermissionUtils.hasPermissions(this)) {
                        requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                    } else {
                        fbViewModel.setPermissionGranted(true)
                    }

                    MainScreen(
                        fbViewModel = fbViewModel,
                        currentThemeColor = themeColors.getOrElse(themeColorIndex) { themeColors[0] },
                        currentBackgroundMode = backgroundMode,
                        pickedTreeUri = pickedTreeUri,
                        pickedTreeUriDescription = pickedTreeUri?.let {
                            com.videorotator.utils.VideoUtils.describeTreeUri(it)
                        },
                        onThemeColorChanged = { color ->
                            themeColorIndex = themeColors.indexOf(color).coerceAtLeast(0)
                            prefs.edit().putInt("theme_color_index", themeColorIndex).apply()
                        },
                        onBackgroundModeChanged = { mode ->
                            backgroundMode = mode
                            prefs.edit().putInt("background_mode", mode.ordinal).apply()
                        },
                        onPickDirectory = { pickDirectoryLauncher.launch(null) },
                        onClearPickedDirectory = {
                            pickedTreeUri = null
                            fileBrowserViewModel?.clearPickedTreeUri()
                        }
                    )
                }
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(
    fbViewModel: FileBrowserViewModel,
    currentThemeColor: com.videorotator.ui.components.ThemeColor,
    currentBackgroundMode: BackgroundMode,
    pickedTreeUri: Uri?,
    pickedTreeUriDescription: String?,
    onThemeColorChanged: (com.videorotator.ui.components.ThemeColor) -> Unit,
    onBackgroundModeChanged: (BackgroundMode) -> Unit,
    onPickDirectory: () -> Unit,
    onClearPickedDirectory: () -> Unit
) {
    val tabs = listOf(
        TabItem("视频列表", Icons.Filled.Movie),
        TabItem("转换列表", Icons.Filled.History),
        TabItem("转换配置", Icons.Filled.Rotate90DegreesCw),
        TabItem("设置", Icons.Filled.Settings)
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    // 全屏子页面状态：列表点击 -> 播放器 -> 转换页
    var playingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    var convertingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    val playerViewModel: PlayerViewModel = viewModel()
    val convertViewModel: ConvertViewModel = viewModel()

    val playing = playingVideo
    val converting = convertingVideo
    when {
        // 转换页优先（从播放器进入后，返回则回到播放器）
        converting != null -> ConvertScreen(
            videoInfo = converting,
            viewModel = convertViewModel,
            pickedOutputTreeUri = pickedTreeUri,
            onPickDirectory = onPickDirectory,
            onClearPickedDirectory = onClearPickedDirectory,
            onBack = { convertingVideo = null },
            onConvertAnother = {
                convertingVideo = null
                playingVideo = null
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
                            // 给最外层 Surface 增加水平 padding，
                            // 让最左侧 / 最右侧的 tab 离屏幕圆角有一段距离
                            .padding(horizontal = 12.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    },
                                    label = {
                                        Text(
                                            tab.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        // 实色浅色作为底色衬托，原图标颜色对下方，保持图标清晰
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
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
                        convertViewModel = convertViewModel,
                        pickedTreeUri = pickedTreeUri,
                        onPickDirectory = onPickDirectory,
                        onVideoClick = { video ->
                            // 记住该视频所在的目录（路径栏点击可跳回）
                            fbViewModel.rememberPlayedVideo(video.uri)
                            playingVideo = video
                        }
                    )
                    1 -> ConvertListTab(convertViewModel)
                    2 -> ConvertConfigTab(
                        pickedTreeUriDescription = pickedTreeUriDescription,
                        onPickDirectory = onPickDirectory,
                        onClearPickedDirectory = onClearPickedDirectory
                    )
                    3 -> SettingsTab(
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
    convertViewModel: ConvertViewModel,
    pickedTreeUri: Uri?,
    onPickDirectory: () -> Unit,
    onVideoClick: (VideoInfo) -> Unit
) {
    val state by fbViewModel.state.collectAsState()
    val defaultOutputDir = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    )
    FileBrowserScreen(
        state = state,
        onVideoClick = onVideoClick,
        onToggleSelect = { fbViewModel.toggleSelect(it) },
        onRefresh = { fbViewModel.loadVideos() },
        onNavigateToParent = { fbViewModel.navigateToParent() },
        onNavigateToDirectory = { path -> fbViewModel.navigateToDirectory(path) },
        onSortKeySelected = { fbViewModel.setSortKey(it) },
        onToggleSelectMode = { fbViewModel.toggleSelectMode() },
        onSelectAll = { fbViewModel.selectAll() },
        onDeselectAll = { fbViewModel.deselectAll() },
        onExitSelectMode = { fbViewModel.exitSelectMode() },
        onBatchConvert = {
            val selected = fbViewModel.selectedVideos()
            if (selected.isNotEmpty()) {
                selected.forEach { video ->
                    convertViewModel.startJob(video, defaultOutputDir, null, 90)
                }
                fbViewModel.exitSelectMode()
            }
        },
        onJumpToLastPlayed = { fbViewModel.jumpToLastPlayedParent() },
        onPickDirectory = onPickDirectory
    )
}

@Composable
fun ConvertConfigTab(
    pickedTreeUriDescription: String?,
    onPickDirectory: () -> Unit,
    onClearPickedDirectory: () -> Unit
) {
    ConvertConfigScreen(
        pickedTreeUriDescription = pickedTreeUriDescription,
        onPickDirectory = onPickDirectory,
        onClearPickedDirectory = onClearPickedDirectory
    )
}

@Composable
fun ConvertListTab(viewModel: ConvertViewModel) {
    ConvertListScreen(viewModel = viewModel)
}

@Composable
fun SettingsTab(
    currentThemeColor: com.videorotator.ui.components.ThemeColor,
    currentBackgroundMode: BackgroundMode,
    onThemeColorChanged: (com.videorotator.ui.components.ThemeColor) -> Unit,
    onBackgroundModeChanged: (BackgroundMode) -> Unit
) {
    SettingsScreen(
        currentThemeColor = currentThemeColor,
        currentBackgroundMode = currentBackgroundMode,
        onThemeColorChanged = onThemeColorChanged,
        onBackgroundModeChanged = onBackgroundModeChanged
    )
}
