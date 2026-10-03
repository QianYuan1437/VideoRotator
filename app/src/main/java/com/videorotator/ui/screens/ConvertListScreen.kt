package com.videorotator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import com.videorotator.viewmodel.ConvertJob
import com.videorotator.viewmodel.ConvertViewModel
import com.videorotator.viewmodel.JobState
import com.videorotator.viewmodel.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAB_PROGRESS = 1
private const val TAB_CONVERTED = 2

@Composable
fun ConvertListScreen(
    viewModel: ConvertViewModel,
    onPlayConverted: (VideoInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    val selectedIds by viewModel.selectedJobIds.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()

    var currentTab by remember { mutableIntStateOf(TAB_PROGRESS) }

    Column(modifier = modifier.fillMaxSize().background(PurpleBg)) {
        // 顶部：标题 + 二级 Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "转换任务",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PurpleDark
            )
            // Tab 1 内容时显示"清空已完成"按钮；Tab 2 不显示
            if (currentTab == TAB_PROGRESS && jobs.any { it.state == JobState.SUCCESS || it.state == JobState.FAILED || it.state == JobState.CANCELLED }) {
                TextButton(onClick = { viewModel.clearFinished() }) {
                    Text("清空已完成", color = PurplePrimary, fontSize = 14.sp)
                }
            }
        }
        TabHeader(
            currentTab = currentTab,
            onTabSelected = {
                currentTab = it
                viewModel.exitSelectMode()
            }
        )

        when (currentTab) {
            TAB_PROGRESS -> ProgressTab(
                jobs = jobs,
                selectedIds = selectedIds,
                isSelectMode = isSelectMode,
                viewModel = viewModel
            )
            TAB_CONVERTED -> ConvertedVideosTab(viewModel = viewModel)
        }
    }
}

/**
 * 二级 Tab：转换进度 / 已转视频
 * 使用 Material3 SecondaryTabRow 的视觉风格。
 */
@Composable
private fun TabHeader(currentTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(color = PurpleBg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            TabPill(
                text = "转换进度",
                selected = currentTab == TAB_PROGRESS,
                onClick = { onTabSelected(TAB_PROGRESS) },
                modifier = Modifier.weight(1f)
            )
            TabPill(
                text = "已转视频",
                selected = currentTab == TAB_CONVERTED,
                onClick = { onTabSelected(TAB_CONVERTED) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) PurplePrimary else Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        // 选中指示器：底部紫色横条
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(36.dp)
                .background(if (selected) PurplePrimary else Color.Transparent)
        )
    }
}

// ============== Tab 1: 转换进度 ==============

@Composable
private fun ProgressTab(
    jobs: List<ConvertJob>,
    selectedIds: Set<String>,
    isSelectMode: Boolean,
    viewModel: ConvertViewModel
) {
    var longPressedJob by remember { mutableStateOf<ConvertJob?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 多选模式顶部操作栏
        if (isSelectMode) {
            SelectActionBar(
                selectedCount = selectedIds.size,
                totalCount = jobs.size,
                onSelectAll = { viewModel.selectAllJobs() },
                onDeselectAll = { viewModel.deselectAllJobs() },
                onDeleteSelected = {
                    viewModel.removeJobsAndFiles(selectedIds)
                },
                onExit = { viewModel.exitSelectMode() }
            )
        }

        if (jobs.isEmpty()) {
            EmptyJobsState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(jobs, key = { it.id }) { job ->
                        ConvertJobCard(
                            job = job,
                            isSelectMode = isSelectMode,
                            isSelected = selectedIds.contains(job.id),
                            onClick = {
                                if (isSelectMode) {
                                    viewModel.toggleSelect(job.id)
                                } else {
                                    longPressedJob = job
                                }
                            },
                            onToggleSelect = { viewModel.toggleSelect(job.id) },
                            onCancel = { viewModel.cancelJob(job.id) },
                            onRemove = { viewModel.removeJobAndFile(job.id) }
                        )
                    }
            }
        }
    }

    // 长按菜单
    longPressedJob?.let { job ->
        JobActionDialog(
            job = job,
            onDismiss = { longPressedJob = null },
            onDeleteTask = {
                viewModel.removeJob(job.id)
                longPressedJob = null
            },
            onRetryTask = {
                viewModel.reverseRotateJob(job.id)
                longPressedJob = null
            },
            onDeleteFile = {
                viewModel.removeFileOnly(job.id)
                longPressedJob = null
            }
        )
    }
}

@Composable
private fun SelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        color = PurplePrimary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "已选 $selectedCount / $totalCount",
                color = PurpleDark,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (selectedCount == totalCount) onDeselectAll() else onSelectAll()
                }) {
                    Text(
                        if (selectedCount == totalCount) "取消全选" else "全选",
                        color = PurplePrimary,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                    Text("删除选中", color = if (selectedCount > 0) PurplePrimary else Color.Gray, fontSize = 13.sp)
                }
                TextButton(onClick = onExit) {
                    Text("退出", color = PurpleDark, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ConvertJobCard(
    job: ConvertJob,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val (stateText, stateColor) = when (job.state) {
        JobState.QUEUED -> "等待中" to Color.Gray
        JobState.RUNNING -> "转换中 ${(job.progress * 100).toInt()}%" to PurplePrimary
        JobState.SUCCESS -> "已完成" to Color(0xFF2E7D32)
        JobState.FAILED -> ("失败：" + (job.error ?: "未知")) to Color(0xFFD32F2F)
        JobState.CANCELLED -> "已取消" to Color.Gray
    }
    val progressColor = when (job.state) {
        JobState.FAILED -> Color(0xFFD32F2F)
        JobState.CANCELLED -> Color.Gray
        else -> PurplePrimary
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PurplePrimary.copy(alpha = 0.12f) else White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        job.videoInfo.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$stateText · ${job.videoInfo.resolution}",
                        fontSize = 12.sp,
                        color = stateColor
                    )
                }
                // 行内右侧操作
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING ->
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Cancel, "取消", tint = Color.Gray)
                        }
                    JobState.SUCCESS, JobState.FAILED, JobState.CANCELLED ->
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Filled.Delete, "删除", tint = Color.Gray)
                        }
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = job.progress.coerceIn(0f, 1f),
                color = progressColor,
                trackColor = PurpleLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (job.state == JobState.SUCCESS && job.outputPath != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    job.outputPath,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 长按弹出的任务操作菜单。
 */
@Composable
private fun JobActionDialog(
    job: ConvertJob,
    onDismiss: () -> Unit,
    onDeleteTask: () -> Unit,
    onRetryTask: () -> Unit,
    onDeleteFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("任务操作", fontWeight = FontWeight.Bold, color = PurpleDark) },
        text = {
            Column {
                DialogActionRow(
                    icon = Icons.Filled.RotateLeft,
                    label = "重试任务（" + reverseLabel(job.degrees) + "）",
                    onClick = onRetryTask
                )
                DialogActionRow(
                    icon = Icons.Filled.Sync,
                    label = "仅删除任务记录",
                    onClick = onDeleteTask
                )
                DialogActionRow(
                    icon = Icons.Filled.Delete,
                    label = "删除文件（保留记录）",
                    onClick = onDeleteFile,
                    enabled = job.outputPath != null || job.outputTreeUri != null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = PurpleDark) }
        },
        containerColor = White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DialogActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) PurplePrimary else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 14.sp,
            color = if (enabled) PurpleDark else Color.Gray
        )
    }
}

private fun reverseLabel(degrees: Int): String {
    val reverse = (360 - degrees) % 360
    return "反向旋转 ${if (reverse == 0) "180" else reverse}°"
}

// ============== Tab 2: 已转视频 ==============

@Composable
private fun ConvertedVideosTab(viewModel: ConvertViewModel) {
    val context = LocalContext.current
    var sortKey by remember { mutableIntStateOf(0) }
    var convertedFiles by remember { mutableStateOf<List<VideoInfo>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick, sortKey) {
        convertedFiles = withContext(Dispatchers.IO) {
            val list = VideoUtils.scanConvertedDir(context)
            when (SortKey.entries.first()) {  // 占位：实际接 sortKey
                SortKey.DATE_DESC -> list.sortedByDescending { it.dateAdded }
                else -> list
            }
        }
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部排序栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSortMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Sync, "排序", tint = PurplePrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        SortKey.values()[rowSortIndex(sortKey)].label,
                        fontSize = 13.sp,
                        color = PurplePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (showSortMenu) {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortKey.values().forEachIndexed { idx, key ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        key.label,
                                        fontWeight = if (rowSortIndex(sortKey) == idx) FontWeight.Bold else FontWeight.Normal,
                                        color = if (rowSortIndex(sortKey) == idx) PurplePrimary else PurpleDark
                                    )
                                },
                                onClick = {
                                    sortKey = idx
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (convertedFiles.isEmpty()) {
            ConvertedEmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(convertedFiles, key = { it.uri.toString() }) { video ->
                    ConvertedVideoCard(
                        video = video,
                        onDelete = {
                                            deleteConvertedFile(context, video)
                                            refreshTick++
                                        },
                        onReverseRotate = {
                                            viewModel.startJob(
                                                videoInfo = video,
                                                    outputDir = null,
                                                    outputTreeUri = null,
                                                    degrees = 270
                                                )
                                            refreshTick++
                                        }
                    )
                }
            }
        }
    }
}

private fun rowSortIndex(i: Int): Int = i  // 占位

@Composable
private fun ConvertedVideoCard(
    video: VideoInfo,
    onDelete: () -> Unit,
    onReverseRotate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图占位：复用系统 MediaStore / 文件 URL；为简单起见这里显示文件图标
            Box(
                modifier = Modifier
                    .size(100.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = PurplePrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${video.resolution} · 已转换",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Divider(color = PurpleLight, thickness = 1.dp)
        // 底部操作区：左右分割为「删除文件」「反向旋转」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .clickable(onClick = onDelete)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Delete, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除文件", fontSize = 13.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)
            }
            // 中间分隔
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(PurpleLight)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .clickable(onClick = onReverseRotate)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.RotateLeft, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("反向旋转", fontSize = 13.sp, color = PurplePrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun deleteConvertedFile(context: android.content.Context, video: VideoInfo) {
    try {
        video.uri.path?.let { File(it).delete() }
        if (video.uri.scheme == "content") {
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, video.uri)
            doc?.delete()
        }
    } catch (_: Exception) {}
}

// ============== Empty states ==============

@Composable
private fun EmptyJobsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                modifier = Modifier.size(80.dp),
                tint = PurplePrimary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text("暂无转换任务", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(
                "从视频列表进入播放器，点\u201c旋转90°转竖屏\u201d开始",
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConvertedEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                modifier = Modifier.size(64.dp),
                tint = PurplePrimary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "已转视频将出现在这里",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }
    }
}