package com.videorotator.ui.screens

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
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
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
    var notificationsEnabled by remember { mutableStateOf(true) }
    var autoScan by remember { mutableStateOf(true) }
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            "软件设置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PurpleDark,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 外观设置
        SettingsSection(
            icon = Icons.Filled.Palette,
            title = "外观",
            description = "个性化应用外观"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 主题色选择
                Column {
                    Text(
                        "主题颜色",
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
                        "背景模式",
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
                            "当前模式：${currentBackgroundMode.displayName}",
                            fontSize = 13.sp,
                            color = PurpleDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 语言设置
        val context = LocalContext.current
        var appLanguage by remember { mutableStateOf(PlayerPrefs.getLanguage(context)) }
        SettingsSection(
            icon = Icons.Filled.Language,
            title = "语言",
            description = "切换应用显示语言"
        ) {
            Column {
                Text(
                    "选择界面语言",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = PurpleDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "切换后会立即生效；选择「跟随系统」则使用设备当前语言",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                LanguageSelector(
                    current = appLanguage,
                    onSelect = { tag ->
                        if (tag == appLanguage) return@LanguageSelector
                        appLanguage = tag
                        PlayerPrefs.setLanguage(context, tag)
                        applyLanguageAndRecreate(context, tag)
                    }
                )
            }
        }

        // 播放设置
        SettingsSection(
            icon = Icons.Filled.Brightness6,
            title = "播放",
            description = "配置播放器行为"
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
                        "默认倍速",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        "打开播放器时使用的初始速度",
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
                            contentDescription = "选择倍速",
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
                        "自动播放",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        if (autoPlay) "打开视频后立即播放" else "打开视频后手动点播放",
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
                        checkedThumbColor = White,
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
            title = "通知",
            description = "管理应用通知"
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
                        "转换完成通知",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        "视频转换完成时发送通知",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
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
            title = "存储",
            description = "管理应用存储"
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
                        "自动扫描",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = PurpleDark
                    )
                    Text(
                        "启动时自动扫描视频文件",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = autoScan,
                    onCheckedChange = { autoScan = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
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
            title = "关于",
            description = "应用信息"
        ) {
            SettingsItem(
                title = "版本",
                subtitle = "1.0.0",
                onClick = { showVersionDialog = true }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "开源许可",
                subtitle = "查看第三方库许可",
                onClick = { showLicensesDialog = true }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "隐私政策",
                subtitle = "查看隐私政策",
                onClick = { showPrivacyDialog = true }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "检查更新",
                subtitle = "查看最新发布版本",
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
                    "视频旋转助手 v1.0.0",
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
            title = { Text("应用版本", fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column {
                    InfoRow("应用名称", "VideoRotator")
                    InfoRow("版本号", versionName)
                    InfoRow("构建号", versionCode.toString())
                    InfoRow("包名", context.packageName)
                    InfoRow("目标 SDK", "34")
                    InfoRow("最低 SDK", "24")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("确定", color = PurplePrimary)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ===== 关于 - 开源许可对话框 =====
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("开源许可", fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "本应用使用以下开源库，遵循各自许可证：",
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
                        "完整许可文本可在项目仓库的 LICENSE / NOTICE 文件查看。",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("关闭", color = PurplePrimary)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ===== 关于 - 隐私政策对话框 =====
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("隐私政策", fontWeight = FontWeight.Bold, color = PurpleDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "VideoRotator 重视您的隐私，承诺如下：",
                        fontSize = 14.sp,
                        color = PurpleDark,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    PrivacySection(
                        title = "1. 本地处理",
                        body = "所有视频旋转操作均在您的设备上完成，原始视频和转换结果不会上传到任何服务器。"
                    )
                    PrivacySection(
                        title = "2. 不收集数据",
                        body = "本应用不包含任何分析、统计或跟踪 SDK，不会上传您的使用数据。"
                    )
                    PrivacySection(
                        title = "3. 文件访问权限",
                        body = "应用仅通过 Android 媒体库或您主动选择的文件夹读取视频；Android 13 及以上使用 READ_MEDIA_VIDEO，更早版本使用 READ_EXTERNAL_STORAGE。系统文件夹选择器（SAF）选择目录时仅您主动授权的目录可被访问。"
                    )
                    PrivacySection(
                        title = "4. 输出位置",
                        body = "转换后的视频默认保存到应用私有目录（Android/data/com.videorotator/files）或您指定的文件夹。"
                    )
                    PrivacySection(
                        title = "5. 开源",
                        body = "本应用为开源项目，源代码可在 GitHub 仓库查看，便于您审查全部实现。"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("我知道了", color = PurplePrimary)
                }
            },
            containerColor = White,
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
        colors = CardDefaults.cardColors(containerColor = White),
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

private data class LanguageOption(val tag: String, val label: String)

private val languageOptions = listOf(
    LanguageOption("zh", "中文"),
    LanguageOption("en", "English"),
    LanguageOption("", "跟随系统")
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
                    color = if (selected) PurplePrimary else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) White else PurpleDark
                        )
                    }
                }
            }
        }
    }
}

/**
 * 应用语言切换：写入 PlayerPrefs + 通过 AppCompatDelegate 设置应用级 locale，
 * Activity.recreate() 触发 Compose 重新加载字符串资源。
 */
private fun applyLanguageAndRecreate(context: android.content.Context, tag: String) {
    AppCompatDelegate.setApplicationLocales(
        androidx.core.os.LocaleListCompat.forLanguageTags(
            when (tag) {
                "zh" -> "zh-CN"
                "en" -> "en"
                else -> ""  // 跟随系统
            }
        )
    )
    (context as? ComponentActivity)?.recreate()
}
