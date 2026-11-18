package com.videorotator

import com.videorotator.ui.components.OnFilterEmptied
import com.videorotator.viewmodel.ConvertedKind
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import com.videorotator.ui.components.evictThumbnail
import com.videorotator.ui.components.formatSize
import com.videorotator.ui.components.DeleteOriginalDialog
import com.videorotator.viewmodel.ConvertEngine
import androidx.activity.result.IntentSenderRequest
import com.videorotator.utils.ConvertMark
import com.videorotator.utils.Notifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
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

    /**
     * 申请读取视频权限。用户主动点「授予权限」时（[fromUser]），若已被永久拒绝则改为打开
     * 应用设置页 —— 否则系统不再弹框，按钮点了没有任何反应。
     */
    private fun requestVideoPermission(fromUser: Boolean) {
        val perm = PermissionUtils.getRequiredPermissions().first()
        val permanentlyDenied = AppPrefs.wasPermissionAsked(this, perm) &&
            !shouldShowRequestPermissionRationale(perm)
        if (permanentlyDenied) {
            if (fromUser) {
                Toast.makeText(this, R.string.perm_open_settings, Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
            return
        }
        AppPrefs.setPermissionAsked(this, perm)
        requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }

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

                    // 只在首次进入时检查 / 申请权限（之前写在组合体里，每次重组都会触发一次全盘扫描）；
                    // rememberSaveable：切换语言 / 深色模式重建 Activity 时不再重复弹权限框
                    var permissionChecked by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (PermissionUtils.hasPermissions(this@MainActivity)) {
                            fbViewModel.setPermissionGranted(true)
                        } else if (!permissionChecked) {
                            requestVideoPermission(fromUser = false)
                        }
                        permissionChecked = true
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
                        onRequestPermission = { requestVideoPermission(fromUser = true) }
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
    // 各列表滚动位置：MainScreen 不随播放页切换销毁，返回列表时停在原处
    val videoListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val progressListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val convertedListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = LocalContext.current

    // 上次浏览（播放）的视频：各列表的悬浮「定位」按钮据此跳转，重启应用后仍有效
    var lastViewedUri by rememberSaveable { mutableStateOf(AppPrefs.getLastViewedUri(context)) }

    // 全屏子页面状态：列表点击 -> 播放器 -> 转换页
    var playingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    fun play(video: VideoInfo) {
        playingVideo = video
        lastViewedUri = video.uri.toString()
        AppPrefs.setLastViewedUri(context, lastViewedUri)
    }
    var convertingVideo by remember { mutableStateOf<VideoInfo?>(null) }
    val playerViewModel: PlayerViewModel = viewModel()
    val convertViewModel: ConvertViewModel = viewModel()
    // 只订阅任务数：转换进度每 1% 更新一次任务列表，订阅整个列表会让整个主界面跟着重组
    val activeJobs by convertViewModel.activeCount.collectAsState()
    val fbState by fbViewModel.state.collectAsState()
    val jobsSelectMode by convertViewModel.isSelectMode.collectAsState()
    val convertedSelectMode by convertViewModel.convertedSelectMode.collectAsState()
    // 有读取权限后识别之前版本转换的视频（补建原视频对应关系），视频列表标识随之更新
    LaunchedEffect(fbState.hasPermission) { if (fbState.hasPermission) convertViewModel.refreshConverted() }

    // 队列跑完时在应用内提示结果（应用在后台时由通知提示），可直接播放 / 重试 / 查看
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(convertViewModel) {
        convertViewModel.queueDone.collect { summary ->
            if (!MainActivity.isInForeground) return@collect
            val ok = summary.succeeded
            val failed = summary.failed
            val (message, action) = when {
                ok.size == 1 && failed.isEmpty() ->
                    context.getString(R.string.snack_done_one, ok[0].videoInfo.displayName) to
                        context.getString(R.string.play)
                ok.isEmpty() && failed.size == 1 ->
                    context.getString(R.string.snack_failed_one, failed[0].videoInfo.displayName) to
                        context.getString(R.string.retry)
                failed.isEmpty() -> context.getString(R.string.snack_done_many, ok.size) to
                    context.getString(R.string.view)
                else -> context.getString(R.string.snack_done_with_failed, ok.size, failed.size) to
                    context.getString(R.string.view)
            }
            val result = snackbarHostState.showSnackbar(
                message, actionLabel = action, withDismissAction = true, duration = SnackbarDuration.Long
            )
            if (result != SnackbarResult.ActionPerformed) return@collect
            when {
                ok.size == 1 && failed.isEmpty() -> convertViewModel.outputInfo(ok[0])?.let {
                    convertingVideo = null
                    play(it)
                }
                ok.isEmpty() && failed.size == 1 -> convertViewModel.retryJob(failed[0].id)
                else -> {
                    convertingVideo = null
                    playingVideo = null
                    convertListTab = if (failed.isEmpty()) CONVERT_TAB_CONVERTED else CONVERT_TAB_PROGRESS
                    selectedTab = 1
                }
            }
        }
    }

    // 删除原视频：不属于本应用的文件需要用户在系统对话框中确认
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> convertViewModel.onDeleteConsentResult(result.resultCode == android.app.Activity.RESULT_OK) }
    val deleteConsent by convertViewModel.deleteConsent.collectAsState()
    LaunchedEffect(deleteConsent) {
        deleteConsent?.let { sender ->
            if (convertViewModel.engine.claimDeleteConsent(sender)) {
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }
    LaunchedEffect(convertViewModel) {
        convertViewModel.filesDeleted.collect { r ->
            val originals = r.kind == ConvertEngine.DeleteKind.ORIGINALS
            val message = when {
                r.count == 0 -> context.getString(if (originals) R.string.originals_none_deleted else R.string.files_none_deleted)
                r.skipped > 0 -> context.getString(
                    if (originals) R.string.originals_deleted_skipped else R.string.files_deleted_skipped,
                    r.count, formatSize(r.freedBytes), r.skipped
                )
                else -> context.getString(
                    if (originals) R.string.originals_deleted else R.string.files_deleted, r.count, formatSize(r.freedBytes)
                )
            }
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    // 首次加入任务时申请通知权限（Android 13+），否则默认开启的「转换完成通知」实际发不出来
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(activeJobs > 0) {
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (activeJobs > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            AppPrefs.getNotifyOnComplete(context) && !Notifier.canPost(context) &&
            !AppPrefs.wasPermissionAsked(context, perm)
        ) {
            AppPrefs.setPermissionAsked(context, perm)
            notificationPermissionLauncher.launch(perm)
        }
    }

    val playing = playingVideo
    val converting = convertingVideo
    // 系统返回键：转换页 → 播放页 → 退出多选 → 回到第一个 Tab → 交给系统
    BackHandler(enabled = converting != null) {
        convertingVideo = null
    }
    BackHandler(enabled = playing != null && converting == null) {
        playingVideo = null
    }
    val anySelectMode = fbState.isSelectMode || jobsSelectMode || convertedSelectMode
    BackHandler(enabled = playing == null && converting == null && anySelectMode) {
        fbViewModel.exitSelectMode()
        convertViewModel.exitSelectMode()
        convertViewModel.exitConvertedSelectMode()
    }
    BackHandler(enabled = playing == null && converting == null && selectedTab != 0 && !anySelectMode) {
        selectedTab = 0
    }

    Box(Modifier.fillMaxSize()) {
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
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                play(video)
                            },
                            onBatchStarted = {
                                convertListTab = CONVERT_TAB_PROGRESS
                                selectedTab = 1
                            },
                            lastViewedUri = lastViewedUri
                        )
                        1 -> ConvertListScreen(
                            viewModel = convertViewModel,
                            currentTab = convertListTab,
                            onTabSelected = { convertListTab = it },
                            onPlay = { play(it) },
                            progressListState = progressListState,
                            convertedListState = convertedListState,
                            lastViewedUri = lastViewedUri
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
        // 播放页 / 转换页没有 Scaffold，结果提示浮在底部
        if (playing != null || converting != null) {
            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
            )
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
    onBatchStarted: () -> Unit,
    lastViewedUri: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by fbViewModel.state.collectAsState()
    val links by convertViewModel.links.collectAsState()
    val activeSourceUris by convertViewModel.activeSourceUris.collectAsState()
    // 产物增删后刷新列表，让新产物出现并打上标识、已删除的文件消失
    val outputRevision by convertViewModel.outputRevision.collectAsState()
    LaunchedEffect(outputRevision) { fbViewModel.syncOutputRevision(outputRevision) }

    // 标识 / 去重 / 筛选分步记忆：标识只在列表或任务结果变化时计算一次，切换筛选只做过滤；
    // 任务流只在成功产物 / 队列内容变化时发射，转换进度不会触发重算
    // 标识计算放到后台线程：视频多时不卡界面；首次组合同步算一次，避免闪出「没有视频」
    val marked by produceState(
        initialValue = remember { VideoMarks.mark(state.videos, links, activeSourceUris) },
        state.videos, links, activeSourceUris
    ) {
        value = withContext(Dispatchers.Default) { VideoMarks.mark(state.videos, links, activeSourceUris) }
    }
    // 筛选数量与去重开关保持一致
    val all = remember(marked, state.dedupe) { VideoMarks.dedupe(marked, state.dedupe) }
    val visible = remember(all, state.filter, state.kind) { VideoMarks.filter(all, state.filter, state.kind) }
    val kindCounts = remember(all) {
        mapOf(
            ConvertedKind.SOURCES to all.count { it.mark == ConvertMark.SOURCE_CONVERTED },
            ConvertedKind.OUTPUTS to all.count { it.mark == ConvertMark.OUTPUT }
        )
    }
    // 「源文件 / 转换产物」被全部删除后自动取消该筛选，避免停留在空列表
    state.kind?.let { kind ->
        OnFilterEmptied(kind, kindCounts[kind] ?: 0) {
            if (!state.isLoading && state.kind == kind) fbViewModel.setKind(kind)
        }
    }
    val counts = remember(all) {
        val converted = all.count { with(VideoMarks) { it.mark.isConverted } }
        mapOf(
            VideoFilter.ALL to all.size,
            VideoFilter.UNCONVERTED to all.size - converted,
            VideoFilter.CONVERTED to converted
        )
    }
    val autoScan = remember { AppPrefs.getAutoScan(context) }

    // 删除原视频（单个 / 多选批量），确认后交给转换引擎
    var pendingDeleteOriginals by remember { mutableStateOf<List<ConvertEngine.DeleteCandidate>?>(null) }
    fun candidatesOf(items: List<com.videorotator.utils.MarkedVideo>) = items
        .filter { it.canDeleteOriginal }
        .map { ConvertEngine.DeleteCandidate(it.original!!, it.outputs) }
        .distinctBy { it.original.uri }
    val selectedDeletable = remember(all, state.selectedUris) {
        candidatesOf(all.filter { it.video.uri.toString() in state.selectedUris })
    }
    val selectedConvertible = remember(all, state.selectedUris) {
        all.count { it.video.uri.toString() in state.selectedUris && it.mark == ConvertMark.NONE }
    }
    // 「源文件」筛选下：当前列出的原视频可一键全部删除
    val shownDeletable = remember(visible, state.kind) {
        if (state.kind == ConvertedKind.SOURCES) candidatesOf(visible) else emptyList()
    }
    pendingDeleteOriginals?.let { candidates ->
        DeleteOriginalDialog(
            candidates = candidates,
            onConfirm = {
                candidates.forEach { evictThumbnail(it.original.uri) }
                convertViewModel.deleteOriginals(candidates)
                fbViewModel.exitSelectMode()
                pendingDeleteOriginals = null
            },
            onDismiss = { pendingDeleteOriginals = null }
        )
    }

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
            // 跳过转换产物、已转换过和已在队列中的视频：全选后批量转换不会把产物再转一遍、
            // 也不会给同一视频重复排队（需要重转可在播放页单独转换）
            val selected = fbViewModel.selectedVideos()
            if (selected.isNotEmpty()) {
                val markOf = marked.associate { it.video.uri to it.mark }
                val added = selected.count { video ->
                    (markOf[video.uri] ?: ConvertMark.NONE) == ConvertMark.NONE &&
                        convertViewModel.startJobWithDefaults(video) != null
                }
                val skipped = selected.size - added
                fbViewModel.exitSelectMode()
                val message = when {
                    added == 0 -> context.getString(R.string.batch_none)
                    skipped > 0 -> context.getString(R.string.batch_added_skipped, added, skipped)
                    else -> context.getString(R.string.batch_added, added)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                if (added > 0) onBatchStarted()
            }
        },
        onJumpToLastPlayed = { fbViewModel.jumpToLastPlayedParent() },
        onPickDirectory = onPickDirectory,
        onRequestPermission = onRequestPermission,
        onConsumeLastPlayedUri = { fbViewModel.consumeLastPlayedUri() },
        lastViewedUri = lastViewedUri,
        onDeleteOriginal = { item -> pendingDeleteOriginals = candidatesOf(listOf(item)) },
        selectedDeletableCount = selectedDeletable.size,
        onDeleteOriginalsSelected = { pendingDeleteOriginals = selectedDeletable },
        selectedConvertibleCount = selectedConvertible,
        kindCounts = kindCounts,
        onKindSelected = { fbViewModel.setKind(it) },
        deletableSummary = shownDeletable.takeIf { it.isNotEmpty() }?.let { list -> list.size to list.sumOf { it.original.size } },
        onDeleteAllShown = { pendingDeleteOriginals = shownDeletable }
    )
}
