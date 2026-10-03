package com.videorotator.ui.screens

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.ConvertViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    videoInfo: VideoInfo,
    viewModel: ConvertViewModel,
    pickedOutputTreeUri: android.net.Uri?,
    onPickDirectory: () -> Unit,
    onClearPickedDirectory: () -> Unit,
    onBack: () -> Unit,
    onConvertAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 路径选择状态
    var showPathDialog by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var useCustomPath by remember { mutableStateOf(false) }

    // 预设路径选项
    val presetPaths = remember {
        listOf(
            PathOption("应用私有目录", File(context.getExternalFilesDir(null), "rotated").absolutePath, "默认保存位置"),
            PathOption("Movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath, "系统视频目录"),
            PathOption("DCIM", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath, "系统相册目录"),
            PathOption("Download", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath, "下载目录"),
            // 自定义路径不再有预设 path；选中后由 SAF 选择器给出
            PathOption(
                "自定义路径",
                pickedOutputTreeUri?.let { com.videorotator.utils.VideoUtils.describeTreeUri(it) }.orEmpty(),
                "从系统选择器挑选文件夹"
            )
        )
    }

    // 获取实际使用的输出目录：File 形式 vs SAF 形式互斥
    val outputDir = remember(selectedPath, useCustomPath) {
        when {
            useCustomPath -> null  // 自定义走 SAF
            selectedPath != null -> File(selectedPath!!)
            else -> null  // 默认走应用私有目录（File?）
        }
    }

    // 自动开始转换（使用默认路径）
    LaunchedEffect(Unit) {
        if (selectedPath == null && !useCustomPath) {
            // 首次进入，显示路径选择
            showPathDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频转换", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = PurpleDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurpleLight,
                    titleContentColor = PurpleDark
                )
            )
        },
        containerColor = PurpleBg,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 视频信息卡片
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        videoInfo.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${videoInfo.resolution} · ${videoInfo.durationText}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 保存路径选择卡片
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "保存位置",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        TextButton(onClick = { showPathDialog = true }) {
                            Text("更改", color = PurplePrimary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight
                    ) {
                        Text(
                            outputDir?.absolutePath
                                ?: pickedOutputTreeUri?.let { com.videorotator.utils.VideoUtils.describeTreeUri(it) }
                                ?: "应用私有目录/rotated",
                            fontSize = 13.sp,
                            color = PurpleDark,
                            maxLines = 2,
                            softWrap = false,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 开始转换按钮：派发到任务队列后跳转到"转换列表"查看实时进度
            Button(
                onClick = {
                    viewModel.startJob(
                        videoInfo = videoInfo,
                        outputDir = outputDir,
                        outputTreeUri = if (useCustomPath) pickedOutputTreeUri else null,
                        degrees = 90
                    )
                    onConvertAnother()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Rotate90DegreesCw, null, tint = White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("开始转换", color = White, fontWeight = FontWeight.Medium)
            }
        }
    }

    // 路径选择对话框
    if (showPathDialog) {
        PathSelectionDialog(
            presetPaths = presetPaths,
            selectedPath = selectedPath,
            useCustomPath = useCustomPath,
            pickedOutputTreeUri = pickedOutputTreeUri,
            onPickDirectory = {
                // 用户希望点选"自定义路径"后立刻唤起 SAF，而不是再走一次确定按钮
                useCustomPath = true
                showPathDialog = false
                onPickDirectory()
            },
            onPathSelected = { path, isCustom ->
                if (isCustom) {
                    useCustomPath = true
                } else {
                    useCustomPath = false
                    selectedPath = path
                }
            },
            onDismiss = { showPathDialog = false },
            onConfirm = {
                if (useCustomPath && pickedOutputTreeUri == null) {
                    onPickDirectory()
                } else {
                    showPathDialog = false
                }
            }
        )
    }
}

@Composable
private fun PathSelectionDialog(
    presetPaths: List<PathOption>,
    selectedPath: String?,
    useCustomPath: Boolean,
    pickedOutputTreeUri: android.net.Uri?,
    onPickDirectory: () -> Unit,
    onPathSelected: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val initialPath = selectedPath ?: presetPaths.first().path
    var tempSelectedPath by remember { mutableStateOf(initialPath) }
    var tempUseCustomPath by remember { mutableStateOf(useCustomPath) }
    val pickedDescription = pickedOutputTreeUri?.let { com.videorotator.utils.VideoUtils.describeTreeUri(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "选择保存位置",
                fontWeight = FontWeight.Bold,
                color = PurpleDark
            )
        },
        text = {
            Column {
                presetPaths.forEach { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (path.isCustom) {
                                    tempUseCustomPath = true
                                } else {
                                    tempUseCustomPath = false
                                    tempSelectedPath = path.path
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = if (path.isCustom) tempUseCustomPath else tempSelectedPath == path.path,
                            onClick = {
                                if (path.isCustom) {
                                    tempUseCustomPath = true
                                } else {
                                    tempUseCustomPath = false
                                    tempSelectedPath = path.path
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                path.name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            if (path.path.isNotEmpty()) {
                                Text(
                                    path.path,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // 自定义路径：点选单选项即弹出系统文件夹选择器
                AnimatedVisibility(visible = tempUseCustomPath) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        if (pickedDescription.isNullOrBlank()) {
                            // 还没挑过目录 → 显示"挑一个"按钮
                            Surface(
                                onClick = onPickDirectory,
                                shape = RoundedCornerShape(12.dp),
                                color = PurpleLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = "选择文件夹",
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "选择其他文件夹",
                                            fontSize = 14.sp,
                                            color = PurpleDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "通过系统选择器指定保存位置",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        } else {
                            // 已挑过 → 显示当前路径 + 重新选择
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PurpleLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pickedDescription,
                                            fontSize = 13.sp,
                                            color = PurpleDark,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            softWrap = false
                                        )
                                        Text(
                                            "已通过系统选择器指定",
                                            fontSize = 11.sp,
                                            color = PurplePrimary
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = onPickDirectory,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("重新选择文件夹", color = PurplePrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tempUseCustomPath) {
                        onPathSelected("", true)
                    } else {
                        onPathSelected(tempSelectedPath ?: "", false)
                    }
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("确定", color = White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = PurpleDark)
            }
        },
        containerColor = White,
        shape = RoundedCornerShape(20.dp)
    )
}

data class PathOption(
    val name: String,
    val path: String,
    val description: String = "",
    val isCustom: Boolean = false
)

@Composable
private fun ConvertingState(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = animatedProgress,
                color = PurplePrimary,
                strokeWidth = 8.dp,
                modifier = Modifier.size(120.dp)
            )
            Text(
                "${(animatedProgress * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = PurpleDark
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "正在转换视频...",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "旋转90°并重新封装，请稍候",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = animatedProgress,
            color = PurplePrimary,
            trackColor = PurpleLight,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun SuccessState(
    outputPath: String,
    onOpenFolder: () -> Unit,
    onConvertAnother: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.CheckCircle,
                    "成功",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "转换完成！",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "文件已保存至：",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PurpleLight
        ) {
            Text(
                outputPath,
                fontSize = 12.sp,
                color = PurpleDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenFolder,
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("打开所在文件夹", color = White, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onConvertAnother,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Rotate90DegreesCw, null, tint = PurplePrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("转换其他视频", color = PurplePrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Error,
            "错误",
            tint = Color(0xFFE57373),
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "转换失败",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color(0xFFE57373)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("重试", color = White, fontWeight = FontWeight.Medium)
        }
    }
}
