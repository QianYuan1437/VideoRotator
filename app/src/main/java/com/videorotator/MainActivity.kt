package com.videorotator

import android.Manifest
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
import com.videorotator.ui.screens.FileBrowserScreen
import com.videorotator.ui.screens.SettingsScreen
import com.videorotator.ui.theme.VideoRotatorTheme
import com.videorotator.utils.PermissionUtils
import com.videorotator.viewmodel.FileBrowserViewModel

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
                        onThemeColorChanged = { color ->
                            themeColorIndex = themeColors.indexOf(color).coerceAtLeast(0)
                            prefs.edit().putInt("theme_color_index", themeColorIndex).apply()
                        },
                        onBackgroundModeChanged = { mode ->
                            backgroundMode = mode
                            prefs.edit().putInt("background_mode", mode.ordinal).apply()
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
    onThemeColorChanged: (com.videorotator.ui.components.ThemeColor) -> Unit,
    onBackgroundModeChanged: (BackgroundMode) -> Unit
) {
    val tabs = listOf(
        TabItem("视频列表", Icons.Filled.Movie),
        TabItem("转换配置", Icons.Filled.Rotate90DegreesCw),
        TabItem("设置", Icons.Filled.Settings)
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            // 底部导航栏：底部圆角裁剪 + 系统手势栏内边距，适配圆角屏幕底边
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
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
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                0 -> FileBrowserTab(fbViewModel = fbViewModel)
                1 -> ConvertConfigTab()
                2 -> SettingsTab(
                    currentThemeColor = currentThemeColor,
                    currentBackgroundMode = currentBackgroundMode,
                    onThemeColorChanged = onThemeColorChanged,
                    onBackgroundModeChanged = onBackgroundModeChanged
                )
            }
        }
    }
}

@Composable
fun FileBrowserTab(fbViewModel: FileBrowserViewModel) {
    val state by fbViewModel.state.collectAsState()
    FileBrowserScreen(
        state = state,
        onVideoClick = { /* TODO: 导航到播放器 */ },
        onRefresh = { fbViewModel.loadVideos() },
        onNavigateToParent = { fbViewModel.navigateToParent() },
        onNavigateToDirectory = { path -> fbViewModel.navigateToDirectory(path) }
    )
}

@Composable
fun ConvertConfigTab() {
    ConvertConfigScreen()
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
