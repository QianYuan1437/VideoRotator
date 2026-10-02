package com.videorotator.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import java.io.File

@Composable
fun ConvertConfigScreen() {
    var defaultOutputDir by remember {
        mutableStateOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
        )
    }
    var autoRotate by remember { mutableStateOf(true) }
    var keepOriginal by remember { mutableStateOf(true) }
    var highQuality by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            "转换配置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PurpleDark,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 输出目录设置
        ConfigSection(
            icon = Icons.Filled.Folder,
            title = "输出目录",
            description = "转换后视频的默认保存位置"
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PurpleLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    defaultOutputDir,
                    fontSize = 13.sp,
                    color = PurpleDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 旋转设置
        ConfigSection(
            icon = Icons.Filled.Rotate90DegreesCw,
            title = "旋转方向",
            description = "选择视频旋转的方向"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RotationOption(
                    icon = Icons.Filled.Rotate90DegreesCw,
                    label = "顺时针90°",
                    selected = autoRotate,
                    onClick = { autoRotate = true },
                    modifier = Modifier.weight(1f)
                )
                RotationOption(
                    icon = Icons.Filled.Rotate90DegreesCcw,
                    label = "逆时针90°",
                    selected = !autoRotate,
                    onClick = { autoRotate = false },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 质量设置
        ConfigSection(
            icon = Icons.Filled.Tune,
            title = "输出质量",
            description = "配置视频转换的质量选项"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchSettingItem(
                    title = "保留原始文件",
                    description = "转换后保留原始视频文件",
                    checked = keepOriginal,
                    onCheckedChange = { keepOriginal = it }
                )
                Divider(color = PurpleLight)
                SwitchSettingItem(
                    title = "高质量模式",
                    description = "使用更高质量的编码（速度较慢）",
                    checked = highQuality,
                    onCheckedChange = { highQuality = it }
                )
            }
        }

        // 存储信息
        ConfigSection(
            icon = Icons.Filled.SdStorage,
            title = "存储信息",
            description = "设备存储状态"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageInfoItem("内部存储", getStorageInfo())
            }
        }

        // 提示
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleLight.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info,
                    null,
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "提示：旋转转换通过修改元数据实现，不会重新编码视频，因此速度极快且画质无损。",
                    fontSize = 13.sp,
                    color = PurpleDark,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ConfigSection(
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
private fun RotationOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PurplePrimary else PurpleLight,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) White else PurplePrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) White else PurpleDark
            )
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                description,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = PurplePrimary,
                uncheckedThumbColor = White,
                uncheckedTrackColor = PurpleLight
            )
        )
    }
}

@Composable
private fun StorageInfoItem(label: String, info: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = PurpleDark)
        Text(info, fontSize = 14.sp, color = Color.Gray)
    }
}

private fun getStorageInfo(): String {
    return try {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
        val available = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val total = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
        "%.1f GB / %.1f GB 可用".format(available, total)
    } catch (e: Exception) {
        "无法获取"
    }
}
