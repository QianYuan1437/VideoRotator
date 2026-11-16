package com.videorotator.ui.screens

import com.videorotator.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
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
import com.videorotator.ui.components.ScreenHorizontalPadding
import com.videorotator.ui.components.ScreenTitle
import com.videorotator.ui.components.ScreenTopPadding
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.OutputTarget
import com.videorotator.utils.VideoUtils

@Composable
fun ConvertConfigScreen(
    outputTreeUri: Uri? = null,
    onPickOutputDirectory: () -> Unit = {},
    onClearOutputDirectory: () -> Unit = {}
) {
    val context = LocalContext.current
    // 之前这些选项只存在 remember 里：切 Tab 就丢失，转换时也从不读取
    var outputTarget by remember { mutableStateOf(AppPrefs.getOutputTarget(context)) }
    var clockwise by remember { mutableStateOf(AppPrefs.isClockwise(context)) }
    val storageInfo = remember { getStorageInfo(context) }

    // 点「自定义路径」时若还没选过文件夹，先唤起选择器，选完再切换
    var waitingForPick by remember { mutableStateOf(false) }
    LaunchedEffect(outputTreeUri) {
        if (outputTreeUri != null && waitingForPick) {
            waitingForPick = false
            outputTarget = OutputTarget.CUSTOM
            AppPrefs.setOutputTarget(context, OutputTarget.CUSTOM)
        } else if (outputTarget == OutputTarget.CUSTOM && outputTreeUri == null) {
            // 自定义文件夹被清除：回退到 Movies
            outputTarget = OutputTarget.MOVIES
            AppPrefs.setOutputTarget(context, OutputTarget.MOVIES)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = ScreenTopPadding, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.tab_config))

        // 输出目录设置
        ConfigSection(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.output_dir),
            description = stringResource(R.string.output_dir_desc)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutputTarget.values().forEach { target ->
                    val selected = target == outputTarget
                    val path = when (target) {
                        OutputTarget.APP_PRIVATE -> VideoUtils.privateOutputDir(context).absolutePath
                        OutputTarget.CUSTOM -> outputTreeUri?.let { VideoUtils.describeTreeUri(it) } ?: stringResource(R.string.not_selected)
                        else -> Environment.getExternalStoragePublicDirectory(target.publicDir).absolutePath
                    }
                    Surface(
                        onClick = {
                            if (target == OutputTarget.CUSTOM && outputTreeUri == null) {
                                waitingForPick = true
                                onPickOutputDirectory()
                            } else {
                                outputTarget = target
                                AppPrefs.setOutputTarget(context, target)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) PurpleLight else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary),
                                modifier = Modifier.padding(12.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(
                                    stringResource(target.labelRes),
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = PurpleDark
                                )
                                Text(
                                    path,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (target == OutputTarget.CUSTOM && outputTreeUri != null) {
                                TextButton(onClick = onPickOutputDirectory) {
                                    Text(stringResource(R.string.replace), color = PurplePrimary, fontSize = 13.sp)
                                }
                                TextButton(onClick = onClearOutputDirectory) {
                                    Text(stringResource(R.string.clear), color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 旋转设置
        ConfigSection(
            icon = Icons.Filled.Rotate90DegreesCw,
            title = stringResource(R.string.rotate_direction),
            description = stringResource(R.string.default_direction_desc)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RotationOption(
                    icon = Icons.Filled.Rotate90DegreesCw,
                    label = stringResource(R.string.cw_90),
                    selected = clockwise,
                    onClick = {
                        clockwise = true
                        AppPrefs.setClockwise(context, true)
                    },
                    modifier = Modifier.weight(1f)
                )
                RotationOption(
                    icon = Icons.Filled.Rotate90DegreesCcw,
                    label = stringResource(R.string.ccw_90),
                    selected = !clockwise,
                    onClick = {
                        clockwise = false
                        AppPrefs.setClockwise(context, false)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 存储信息
        ConfigSection(
            icon = Icons.Filled.SdStorage,
            title = stringResource(R.string.storage_info),
            description = stringResource(R.string.storage_info_desc)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageInfoItem(stringResource(R.string.internal_storage), storageInfo)
            }
        }

        // 提示
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleLight.copy(alpha = 0.6f)),
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
                    stringResource(R.string.config_tip),
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
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) OnPrimary else PurplePrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) OnPrimary else PurpleDark
            )
        }
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

private fun getStorageInfo(context: android.content.Context): String {
    return try {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
        val available = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val total = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)
        context.getString(R.string.storage_available, available, total)
    } catch (e: Exception) {
        context.getString(R.string.storage_unavailable)
    }
}
