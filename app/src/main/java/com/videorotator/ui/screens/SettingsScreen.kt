package com.videorotator.ui.screens

import com.videorotator.utils.AppLocale
import androidx.annotation.StringRes
import com.videorotator.R
import com.videorotator.utils.CrashLog
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.BackgroundModeSelector
import com.videorotator.ui.components.ThemeColor
import com.videorotator.ui.components.ThemeColorSelector
import com.videorotator.ui.components.themeColors
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.ui.components.ScreenHorizontalPadding
import com.videorotator.ui.components.ScreenTitle
import com.videorotator.ui.components.ScreenTopPadding
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.Notifier
import com.videorotator.utils.PlayerPrefs

private val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
fun SettingsScreen(
    currentThemeColor: ThemeColor = themeColors[0],
    currentBackgroundMode: BackgroundMode = BackgroundMode.LIGHT,
    onThemeColorChanged: (ThemeColor) -> Unit = {},
    onBackgroundModeChanged: (BackgroundMode) -> Unit = {}
) {
    val context = LocalContext.current
    // 之前这两个开关只存在 remember 里，没有任何实际作用
    var notificationsEnabled by remember {
        mutableStateOf(AppPrefs.getNotifyOnComplete(context) && Notifier.canPost(context))
    }
    var autoScan by remember { mutableStateOf(AppPrefs.getAutoScan(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        AppPrefs.setNotifyOnComplete(context, granted)
    }
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    var playbackSpeed by remember {
        mutableStateOf(PlayerPrefs.getPlaybackSpeed(context))
    }
    var autoPlay by remember {
        mutableStateOf(PlayerPrefs.getAutoPlay(context))
    }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = ScreenTopPadding, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.tab_settings))

        // 外观设置
        SettingsSection(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.appearance),
            description = stringResource(R.string.appearance_desc)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 主题色选择
                Column {
                    Text(
                        stringResource(R.string.theme_color),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeColorSelector(
                        currentColor = currentThemeColor,
                        onColorSelected = onThemeColorChanged
                    )
                }
                Divider(color = PurpleLight)
                // 背景模式选择
                Column {
                    Text(
                        stringResource(R.string.background_mode),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Spacer(Modifier.height(8.dp))
                    BackgroundModeSelector(
                        currentMode = currentBackgroundMode,
                        onModeSelected = onBackgroundModeChanged
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight
                    ) {
                        Text(
                            stringResource(R.string.current_mode, stringResource(currentBackgroundMode.labelRes)),
                            fontSize = 13.sp,
                            color = PurpleDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 语言设置
        // 以 AppCompat 保存的应用语言为准（API 33+ 也可能在系统设置里被改掉）
        val appLanguage = remember { AppLocale.currentTag() }
        SettingsSection(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.language),
            description = stringResource(R.string.language_desc)
        ) {
            Column {
                Text(
                    stringResource(R.string.language_choose),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = PurpleDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.language_hint),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                LanguageSelector(
                    current = appLanguage,
                    onSelect = { tag ->
                        if (tag == appLanguage) return@LanguageSelector
                        PlayerPrefs.setLanguage(context, tag)
                        // AppCompatActivity 会自动重建，Compose 随之按新语言加载字符串
                        AppLocale.apply(tag)
                    }
                )
            }
        }

        // 播放设置
        SettingsSection(
            icon = Icons.Filled.Brightness6,
            title = stringResource(R.string.playback),
            description = stringResource(R.string.playback_desc)
        ) {
            // 默认倍速（点击整行展开下拉）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { speedMenuOpen = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.default_speed),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        stringResource(R.string.default_speed_desc),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${playbackSpeed}x",
                            fontSize = 15.sp,
                            color = PurplePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = stringResource(R.string.choose_speed),
                            tint = PurplePrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = speedMenuOpen,
                        onDismissRequest = { speedMenuOpen = false }
                    ) {
                        PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${speed}x",
                                        fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (speed == playbackSpeed) PurplePrimary else PurpleDark
                                    )
                                },
                                onClick = {
                                    playbackSpeed = speed
                                    PlayerPrefs.setPlaybackSpeed(context, speed)
                                    speedMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
            Divider(color = PurpleLight)
            // 自动播放（开关）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.auto_play),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        if (autoPlay) stringResource(R.string.auto_play_on) else stringResource(R.string.auto_play_off),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = autoPlay,
                    onCheckedChange = {
                        autoPlay = it
                        PlayerPrefs.setAutoPlay(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnPrimary,
                        checkedTrackColor = PurplePrimary,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = PurpleLight
                    )
                )
            }
        }

        // 通知设置
        SettingsSection(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.notifications),
            description = stringResource(R.string.notifications_desc)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.notify_done),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        stringResource(R.string.notify_done_desc),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Notifier.canPost(context) &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsEnabled = enabled
                            AppPrefs.setNotifyOnComplete(context, enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnPrimary,
                        checkedTrackColor = PurplePrimary,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = PurpleLight
                    )
                )
            }
        }

        // 存储设置
        SettingsSection(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.storage),
            description = stringResource(R.string.storage_desc)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.auto_scan),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        stringResource(R.string.auto_scan_desc),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = autoScan,
                    onCheckedChange = {
                        autoScan = it
                        AppPrefs.setAutoScan(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnPrimary,
                        checkedTrackColor = PurplePrimary,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = PurpleLight
                    )
                )
            }
        }

        // 关于
        SettingsSection(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.about),
            description = stringResource(R.string.about_desc)
        ) {
            SettingsItem(
                title = stringResource(R.string.version),
                subtitle = appVersion,
                onClick = { showVersionDialog = true }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = stringResource(R.string.licenses),
                subtitle = stringResource(R.string.licenses_desc),
                onClick = { showLicensesDialog = true }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = stringResource(R.string.privacy),
                subtitle = stringResource(R.string.privacy_desc),
                onClick = { showPrivacyDialog = true }
            )
            // 上次闪退的记录：复制后清除，便于反馈问题
            var crashLog by remember { mutableStateOf(CrashLog.read(context)) }
            crashLog?.let { log ->
                Divider(color = PurpleLight)
                SettingsItem(
                    title = stringResource(R.string.crash_log),
                    subtitle = stringResource(R.string.crash_log_desc),
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VideoRotator crash", log))
                        android.widget.Toast.makeText(context, R.string.crash_log_copied, android.widget.Toast.LENGTH_SHORT).show()
                        CrashLog.clear(context)
                        crashLog = null
                    }
                )
            }
            Divider(color = PurpleLight)
            SettingsItem(
                title = stringResource(R.string.check_update),
                subtitle = stringResource(R.string.check_update_desc),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://github.com/QianYuan1437/VideoRotator/releases"
                    ))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // 浏览器不可用时忽略
                    }
                }
            )
        }

        // 底部信息
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleLight.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "VideoRotator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PurpleDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.app_tagline, appVersion),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }

    // ===== 关于 - 版本号对话框 =====
    if (showVersionDialog) {
        val versionName: String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
        val versionCode: Long = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text(stringResource(R.string.app_version), fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column {
                    InfoRow(stringResource(R.string.info_app_name), "VideoRotator")
                    InfoRow(stringResource(R.string.info_version_name), versionName)
                    InfoRow(stringResource(R.string.info_version_code), versionCode.toString())
                    InfoRow(stringResource(R.string.info_package), context.packageName)
                    InfoRow(stringResource(R.string.info_target_sdk), context.applicationInfo.targetSdkVersion.toString())
                    InfoRow(stringResource(R.string.info_min_sdk), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.applicationInfo.minSdkVersion.toString() else "24")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text(stringResource(R.string.confirm), color = PurplePrimary)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ===== 关于 - 开源许可对话框 =====
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(R.string.licenses), fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.licenses_intro),
                        fontSize = 13.sp,
                        color = PurpleDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LicenseRow(
                        name = "AndroidX Core / AppCompat",
                        license = "Apache License 2.0"
                    )
                    LicenseRow(
                        name = "Jetpack Compose (Material 3 / Foundation)",
                        license = "Apache License 2.0"
                    )
                    LicenseRow(
                        name = "AndroidX Media3 (ExoPlayer)",
                        license = "Apache License 2.0",
                        note = "1.2.1"
                    )
                    LicenseRow(
                        name = "AndroidX Lifecycle / ViewModel",
                        license = "Apache License 2.0"
                    )
                    LicenseRow(
                        name = "AndroidX DocumentFile",
                        license = "Apache License 2.0"
                    )
                    LicenseRow(
                        name = "Kotlin Coroutines",
                        license = "Apache License 2.0",
                        note = "1.7.3"
                    )
                    Text(
                        stringResource(R.string.licenses_footer),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text(stringResource(R.string.close), color = PurplePrimary)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ===== 关于 - 隐私政策对话框 =====
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.privacy), fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.privacy_intro),
                        fontSize = 14.sp,
                        color = PurpleDark,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    PrivacySection(
                        title = stringResource(R.string.privacy_1_title),
                        body = stringResource(R.string.privacy_1_body)
                    )
                    PrivacySection(
                        title = stringResource(R.string.privacy_2_title),
                        body = stringResource(R.string.privacy_2_body)
                    )
                    PrivacySection(
                        title = stringResource(R.string.privacy_3_title),
                        body = stringResource(R.string.privacy_3_body)
                    )
                    PrivacySection(
                        title = stringResource(R.string.privacy_4_title),
                        body = stringResource(R.string.privacy_4_body)
                    )
                    PrivacySection(
                        title = stringResource(R.string.privacy_5_title),
                        body = stringResource(R.string.privacy_5_body)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.got_it), color = PurplePrimary)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PurpleLight,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = PurplePrimary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = PurpleDark
                    )
                    Text(
                        description,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = PurpleDark
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Icon(
            Icons.Filled.Info,
            null,
            tint = PurplePrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 通用键值对行（用于版本号对话框） */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = PurpleDark,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 开源许可项 */
@Composable
private fun LicenseRow(name: String, license: String, note: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                fontSize = 14.sp,
                color = PurpleDark,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (note != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PurpleLight
                ) {
                    Text(
                        note,
                        fontSize = 11.sp,
                        color = PurplePrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(
            license,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/** 隐私政策段落 */
@Composable
private fun PrivacySection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            color = PurplePrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            body,
            fontSize = 13.sp,
            color = PurpleDark,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ============== 语言切换 ==============

private data class LanguageOption(val tag: String, @StringRes val labelRes: Int)

private val languageOptions = listOf(
    LanguageOption("zh", R.string.language_zh),
    LanguageOption("en", R.string.language_en),
    LanguageOption("", R.string.language_system)
)

@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PurpleLight.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            languageOptions.forEach { opt ->
                val selected = opt.tag.equals(current, ignoreCase = true)
                Surface(
                    onClick = { onSelect(opt.tag) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) PurplePrimary else CardSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(opt.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) OnPrimary else PurpleDark
                        )
                    }
                }
            }
        }
    }
}
