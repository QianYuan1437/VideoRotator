package com.videorotator.ui.screens

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.FileBrowserState
import com.videorotator.viewmodel.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileBrowserScreen(
    state: FileBrowserState,
    onVideoClick: (VideoInfo) -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToDirectory: (String) -> Unit,
    onSortKeySelected: (SortKey) -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExitSelectMode: () -> Unit,
    onBatchConvert: () -> Unit,
    onJumpToLastPlayed: () -> Unit,
    onPickDirectory: () -> Unit,
    onConsumeLastPlayedUri: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PurpleBg)
    ) {
        QuickDirectoryBar(
            currentDir = state.currentDirectory,
            isPickedDir = state.pickedTreeUri != null,
            lastPlayedParent = state.lastPlayedParent,
            onJumpToLastPlayed = onJumpToLastPlayed,
            onNavigateToParent = onNavigateToParent,
            onNavigateToDirectory = onNavigateToDirectory,
            onRefresh = onRefresh,
            onPickDirectory = onPickDirectory,
            sortKey = state.sortKey,
            onSortKeySelected = onSortKeySelected,
            isSelectMode = state.isSelectMode,
            onToggleSelectMode = onToggleSelectMode,
            selectedCount = state.selectedUris.size,
            totalCount = state.videos.size
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PurplePrimary)
            }
        } else if (state.videos.isEmpty()) {
            EmptyState(onRefresh = onRefresh)
        } else {
            if (state.isSelectMode) {
                SelectActionBar(
                    selectedCount = state.selectedUris.size,
                    totalCount = state.videos.size,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onBatchConvert = onBatchConvert,
                    onExitSelectMode = onExitSelectMode
                )
            }
            // 用 LazyListState 记住滚动位置；从播放器返回时回到原视频卡片
            val listState = rememberLazyListState()
            val lastPlayedUri = state.lastPlayedVideoUri
            LaunchedEffect(lastPlayedUri, state.videos.size) {
                val uri = lastPlayedUri ?: return@LaunchedEffect
                val idx = state.videos.indexOfFirst { it.uri.toString() == uri }
                if (idx >= 0) {
                    listState.animateScrollToItem(idx)
                    onConsumeLastPlayedUri()
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.videos, key = { it.uri.toString() }) { video ->
                    VideoCard(
                        video = video,
                        isSelectMode = state.isSelectMode,
                        isSelected = state.selectedUris.contains(video.uri.toString()),
                        onClick = {
                            if (state.isSelectMode) {
                                onToggleSelect(video.uri)
                            } else {
                                onVideoClick(video)
                            }
                        },
                        onToggleSelect = { onToggleSelect(video.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickDirectoryBar(
    currentDir: String,
    isPickedDir: Boolean,
    lastPlayedParent: String?,
    onJumpToLastPlayed: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToDirectory: (String) -> Unit,
    onRefresh: () -> Unit,
    onPickDirectory: () -> Unit,
    sortKey: SortKey,
    onSortKeySelected: (SortKey) -> Unit,
    isSelectMode: Boolean,
    onToggleSelectMode: () -> Unit,
    selectedCount: Int,
    totalCount: Int
) {
    // 不再使用 Surface + 投影包裹路径栏：去除白底卡片与底部分隔线，
    // 让视频列表 tab 顶部与其他三个 tab 视觉一致。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 当前路径 + 跳转到上次播放文件目录的入口；整行点击唤起系统文件夹选择器
                val canJump = lastPlayedParent != null && lastPlayedParent != currentDir
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPickDirectory)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "选择文件夹",
                        tint = PurplePrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        currentDir,
                        fontSize = 13.sp,
                        color = PurpleDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPickedDir) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PurplePrimary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                "已选",
                                fontSize = 10.sp,
                                color = PurplePrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (canJump) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = onJumpToLastPlayed,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.History,
                                null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "上次播放",
                                fontSize = 12.sp,
                                color = PurplePrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, "刷新", tint = PurpleDark)
                    }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.Sort, "排序", tint = PurpleDark)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            SortKey.values().forEach { key ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            key.label,
                                            fontWeight = if (key == sortKey) FontWeight.Bold else FontWeight.Normal,
                                            color = if (key == sortKey) PurplePrimary else PurpleDark
                                        )
                                    },
                                    onClick = {
                                        onSortKeySelected(key)
                                        menuOpen = false
                                    },
                                    leadingIcon = {
                                        if (key == sortKey) {
                                            Icon(
                                                Icons.Filled.CheckBox,
                                                null,
                                                tint = PurplePrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Spacer(Modifier.size(18.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onToggleSelectMode) {
                        Icon(
                            if (isSelectMode) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            "多选",
                            tint = if (isSelectMode) PurplePrimary else PurpleDark
                        )
                    }
                }
            }
            // 快捷目录按钮
            val quickScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(quickScroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickDirChip(Icons.Filled.SdStorage, "上级", onClick = onNavigateToParent)
                QuickDirChip(Icons.Filled.Movie, "Movies") {
                    onNavigateToDirectory(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_MOVIES
                        ).absolutePath
                    )
                }
                QuickDirChip(Icons.Filled.Folder, "DCIM") {
                    onNavigateToDirectory(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DCIM
                        ).absolutePath
                    )
                }
                QuickDirChip(Icons.Filled.Download, "下载") {
                    onNavigateToDirectory(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ).absolutePath
                    )
                }
            }
        }
}

/** 快捷目录小标签（图标 + 文本） */
@Composable
private fun QuickDirChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PurpleLight
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PurpleDark, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 13.sp, color = PurpleDark, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBatchConvert: () -> Unit,
    onExitSelectMode: () -> Unit
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
                Button(
                    onClick = onBatchConvert,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "批量转换$selectedCount",
                        color = White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onExitSelectMode) {
                    Text("退出", color = PurpleDark, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoInfo,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PurplePrimary.copy(alpha = 0.12f) else White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                modifier = Modifier
                    .size(100.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(
                    uri = video.uri,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    fontWeight = FontWeight.SemiBold,
                    // 缩小字号给 chip 留出空间，避免名称被截断
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 三个 chip + 一个方向指示器，强制单行（关闭 softWrap），
                    // 避免横屏 / 竖屏长方形被竖向拆字或被遮挡。
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight
                    ) {
                        Text(
                            video.durationText,
                            fontSize = 12.sp,
                            color = PurpleDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight
                    ) {
                        Text(
                            video.resolution,
                            fontSize = 12.sp,
                            color = PurpleDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // 方向指示器：用一块长方形代替"横屏"文字。
                    // 横屏 = 横长方形，竖屏 = 竖长方形，方向箭头长度与分辨率/时长 chip 视觉对齐。
                    OrientationIndicator(isLandscape = video.isLandscape)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    video.sizeText,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
        }
    }
}

/**
 * 视频方向指示器：用一块长方形表示横竖屏方向。
 * - 横屏（landscape）→ 横长方形
 * - 竖屏（portrait）→ 竖长方形
 *
 * 容器采用与分辨率/时长 chip 完全一致的尺寸（RoundedCornerShape=12dp、
 * padding=horizontal 8dp / vertical 2dp），让方向指示器在视觉上与其他
 * chip 等同，避免尺寸不一致导致的卡片宽度被撑大或遮挡。
 */
@Composable
private fun OrientationIndicator(isLandscape: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLandscape) PurplePrimary.copy(alpha = 0.2f) else PurpleLight
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .size(width = 18.dp, height = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = if (isLandscape) 18.dp else 5.dp,
                        height = if (isLandscape) 5.dp else 18.dp
                    )
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(PurpleDark)
            )
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Storage,
                null,
                tint = PurplePrimary.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "未找到视频文件",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请确认目录中包含视频文件",
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, tint = White)
                Spacer(Modifier.width(8.dp))
                Text("刷新", color = White)
            }
        }
    }
}

// 视频缩略图内存缓存（按字节计量，上限 32MB）
private val thumbnailCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/**
 * 视频首帧缩略图：Coil 无法解码视频帧，这里用 MediaMetadataRetriever
 * 在 IO 线程抽取首帧，降采样后放入内存缓存。
 */
@Composable
private fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(uri) {
        mutableStateOf(thumbnailCache.get(uri.toString()))
    }

    if (bitmap == null) {
        Icon(
            Icons.Filled.Movie,
            null,
            tint = PurplePrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
        LaunchedEffect(uri) {
            val frame = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            frame?.let { raw ->
                val scaled = raw.scaleForThumbnail(480)
                thumbnailCache.put(uri.toString(), scaled)
                bitmap = scaled
            }
        }
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

private fun Bitmap.scaleForThumbnail(maxDim: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxDim) return this
    val scale = maxDim.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
}