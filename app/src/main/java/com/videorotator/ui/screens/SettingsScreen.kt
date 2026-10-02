package com.videorotator.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.videorotator.ui.components.BackgroundMode
import com.videorotator.ui.components.BackgroundModeSelector
import com.videorotator.ui.components.ThemeColor
import com.videorotator.ui.components.ThemeColorSelector
import com.videorotator.ui.components.themeColors
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White

@Composable
fun SettingsScreen(
    currentThemeColor: ThemeColor = themeColors[0],
    currentBackgroundMode: BackgroundMode = BackgroundMode.LIGHT,
    onThemeColorChanged: (ThemeColor) -> Unit = {},
    onBackgroundModeChanged: (BackgroundMode) -> Unit = {}
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var autoScan by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            "设置",
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

        // 播放设置
        SettingsSection(
            icon = Icons.Filled.Brightness6,
            title = "播放",
            description = "配置播放器行为"
        ) {
            SettingsItem(
                title = "默认倍速",
                subtitle = "1.0x",
                onClick = { }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "自动播放",
                subtitle = "打开视频后自动播放",
                onClick = { }
            )
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
                onClick = { }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "开源许可",
                subtitle = "查看第三方库许可",
                onClick = { }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "隐私政策",
                subtitle = "查看隐私政策",
                onClick = { }
            )
            Divider(color = PurpleLight)
            SettingsItem(
                title = "检查更新",
                subtitle = "当前已是最新版本",
                onClick = { }
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
