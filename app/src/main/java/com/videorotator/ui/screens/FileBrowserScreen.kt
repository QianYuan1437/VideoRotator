package com.videorotator.ui.screens

import com.videorotator.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.components.ConvertBadge
import com.videorotator.ui.components.InfoChip
import com.videorotator.ui.components.OrientationIndicator
import com.videorotator.ui.components.ScreenHorizontalPadding
import com.videorotator.ui.components.ScreenTitle
import com.videorotator.ui.components.ScreenTopPadding
import com.videorotator.ui.components.VideoThumbnail
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.utils.ConvertMark
import com.videorotator.utils.MarkedVideo
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.FileBrowserState
import com.videorotator.viewmodel.SortKey
import com.videorotator.viewmodel.VideoFilter

@Composable
fun FileBrowserScreen(
    state: FileBrowserState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    items: List<MarkedVideo>,
    filterCounts: Map<VideoFilter, Int>,
    autoScanEnabled: Boolean,
    onVideoClick: (VideoInfo) -> Unit,
    onVideoLongClick: (VideoInfo) -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToDirectory: (String) -> Unit,
    onSortKeySelected: (SortKey) -> Unit,
    onFilterSelected: (VideoFilter) -> Unit,
    onDedupeChanged: (Boolean) -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExitSelectMode: () -> Unit,
    onBatchConvert: () -> Unit,
    onJumpToLastPlayed: () -> Unit,
    onPickDirectory: () -> Unit,
    onRequestPermission: () -> Unit,
    onConsumeLastPlayedUri: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PurpleBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHorizontalPadding, end = 4.dp, top = ScreenTopPadding)
        ) {
            ScreenTitle(stringResource(R.string.tab_videos)) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.refresh), tint = PurpleDark)
                }
                SortMenuButton(sortKey = state.sortKey, onSortKeySelected = onSortKeySelected)
                IconButton(onClick = onToggleSelectMode) {
                    Icon(
                        if (state.isSelectMode) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        stringResource(R.string.multi_select),
                        tint = if (state.isSelectMode) PurplePrimary else PurpleDark
                    )
                }
            }
        }
        QuickDirectoryBar(
            currentDir = state.currentDirectory,
            isPickedDir = state.pickedTreeUri != null,
            lastPlayedParent = state.lastPlayedParent,
            onJumpToLastPlayed = onJumpToLastPlayed,
            onNavigateToParent = onNavigateToParent,
            onNavigateToDirectory = onNavigateToDirectory,
            onPickDirectory = onPickDirectory
        )
        FilterBar(
            filter = state.filter,
            counts = filterCounts,
            dedupe = state.dedupe,
            onFilterSelected = onFilterSelected,
            onDedupeChanged = onDedupeChanged
        )

        if (state.isSelectMode) {
            SelectActionBar(
                selectedCount = state.selectedUris.size,
                totalCount = items.size,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onBatchConvert = onBatchConvert,
                onExitSelectMode = onExitSelectMode
            )
        }

        when {
            !state.hasPermission -> MessageState(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.need_permission),
                subtitle = stringResource(R.string.need_permission_desc),
                buttonText = stringResource(R.string.grant_permission),
                onButton = onRequestPermission
            )
            state.isLoading && items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PurplePrimary)
            }
            !state.hasLoaded && !autoScanEnabled -> MessageState(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.auto_scan_off),
                subtitle = stringResource(R.string.auto_scan_off_desc),
                buttonText = stringResource(R.string.scan_videos),
                onButton = onRefresh
            )
            items.isEmpty() && state.videos.isNotEmpty() -> MessageState(
                icon = Icons.Filled.Movie,
                title = stringResource(R.string.no_filter_match),
                subtitle = stringResource(R.string.no_filter_match_desc, state.videos.size),
                buttonText = stringResource(R.string.show_all),
                onButton = { onFilterSelected(VideoFilter.ALL) }
            )
            items.isEmpty() -> MessageState(
                icon = Icons.Filled.Storage,
                title = stringResource(if (state.errorMessage != null) R.string.load_failed else R.string.no_videos),
                subtitle = state.errorMessage ?: stringResource(R.string.no_videos_desc),
                buttonText = stringResource(R.string.refresh),
                onButton = onRefresh
            )
            else -> {
                // listState 由 MainScreen 持有，从播放器返回时保持原滚动位置；
                // 若列表因重扫发生变化，再定位到刚播放的视频卡片
                val lastPlayedUri = state.lastPlayedVideoUri
                LaunchedEffect(lastPlayedUri, items.size) {
                    val uri = lastPlayedUri ?: return@LaunchedEffect
                    val idx = items.indexOfFirst { it.video.uri.toString() == uri }
                    if (idx >= 0) {
                        // 不在可见范围内才滚动，避免返回时列表跳动
                        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == idx }
                        if (!visible) listState.scrollToItem(idx)
                    }
                    onConsumeLastPlayedUri()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = ScreenHorizontalPadding, end = ScreenHorizontalPadding,
                            top = 4.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.video.uri.toString() }) { item ->
                            val video = item.video
                            VideoCard(
                                item = item,
                                isSelectMode = state.isSelectMode,
                                isSelected = state.selectedUris.contains(video.uri.toString()),
                                onClick = {
                                    if (state.isSelectMode) onToggleSelect(video.uri) else onVideoClick(video)
                                },
                                onLongClick = { onVideoLongClick(video) },
                                onToggleSelect = { onToggleSelect(video.uri) }
                            )
                        }
                    }
                    // 列表已有内容时刷新，不再整页切成转圈，只在顶部显示细进度
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = PurplePrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuButton(sortKey: SortKey, onSortKeySelected: (SortKey) -> Unit) {
    Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.Sort, stringResource(R.string.sort), tint = PurpleDark)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            SortKey.values().forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(key.labelRes),
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
                            Icon(Icons.Filled.Check, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    }
                )
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
    onPickDirectory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding)
    ) {
        // 当前路径（点击唤起系统文件夹选择器）+ 跳转到上次播放文件目录的入口
        val canJump = lastPlayedParent != null && (lastPlayedParent != currentDir || isPickedDir)
        Surface(
            onClick = onPickDirectory,
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = stringResource(R.string.pick_folder),
                    tint = PurplePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    displayDir(currentDir, stringResource(R.string.internal_storage)),
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
                            stringResource(R.string.picked),
                            fontSize = 10.sp,
                            color = PurpleDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (canJump) {
                    TextButton(
                        onClick = onJumpToLastPlayed,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.History, null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(stringResource(R.string.last_played), fontSize = 12.sp, color = PurplePrimary, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 快捷目录按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickDirChip(Icons.Filled.SubdirectoryArrowLeft, stringResource(R.string.dir_parent), onClick = onNavigateToParent)
            QuickDirChip(Icons.Filled.Movie, "Movies") {
                onNavigateToDirectory(publicDir(android.os.Environment.DIRECTORY_MOVIES))
            }
            QuickDirChip(Icons.Filled.Folder, "DCIM") {
                onNavigateToDirectory(publicDir(android.os.Environment.DIRECTORY_DCIM))
            }
            QuickDirChip(Icons.Filled.Download, stringResource(R.string.dir_download)) {
                onNavigateToDirectory(publicDir(android.os.Environment.DIRECTORY_DOWNLOADS))
            }
            QuickDirChip(Icons.Filled.Storage, stringResource(R.string.dir_all)) {
                onNavigateToDirectory(android.os.Environment.getExternalStorageDirectory().absolutePath)
            }
        }
    }
}

private fun publicDir(type: String): String =
    android.os.Environment.getExternalStoragePublicDirectory(type).absolutePath

/** /storage/emulated/0/Movies → 内部存储/Movies */
private fun displayDir(dir: String, internal: String): String {
    val root = android.os.Environment.getExternalStorageDirectory().absolutePath
    return when {
        dir == root -> internal
        dir.startsWith("$root/") -> "$internal/" + dir.removePrefix("$root/")
        dir.startsWith("primary:") -> "$internal/" + dir.removePrefix("primary:")
        else -> dir
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

/**
 * 筛选栏：全部 / 未转换 / 已转换（带数量）+ 去重开关。
 */
@Composable
private fun FilterBar(
    filter: VideoFilter,
    counts: Map<VideoFilter, Int>,
    dedupe: Boolean,
    onFilterSelected: (VideoFilter) -> Unit,
    onDedupeChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoFilter.values().forEach { f ->
            SelectChip(
                text = stringResource(f.labelRes) + (counts[f]?.let { " $it" } ?: ""),
                selected = f == filter,
                onClick = { onFilterSelected(f) }
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(PurpleDark.copy(alpha = 0.2f))
        )
        Spacer(Modifier.width(4.dp))
        SelectChip(
            text = stringResource(R.string.filter_dedupe),
            selected = dedupe,
            icon = Icons.Filled.ContentCopy,
            onClick = { onDedupeChanged(!dedupe) }
        )
    }
}

@Composable
private fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PurplePrimary else CardSurface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, PurplePrimary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected || icon != null) {
                Icon(
                    if (selected) Icons.Filled.Check else icon!!,
                    null,
                    tint = if (selected) OnPrimary else PurpleDark,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                fontSize = 13.sp,
                color = if (selected) OnPrimary else PurpleDark,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.selected_count, selectedCount, totalCount),
                color = PurpleDark,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val allSelected = totalCount > 0 && selectedCount >= totalCount
                TextButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                    Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all), color = PurplePrimary, fontSize = 13.sp)
                }
                Button(
                    onClick = onBatchConvert,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.batch_convert, selectedCount),
                        color = if (selectedCount > 0) OnPrimary else Color.Gray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onExitSelectMode) {
                    Text(stringResource(R.string.exit), color = PurpleDark, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    item: MarkedVideo,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val video = item.video
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) lerp(CardSurface, PurplePrimary, 0.25f) else CardSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图 + 左上角转换标识
            Box(
                modifier = Modifier
                    .size(100.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(uri = video.uri, modifier = Modifier.fillMaxSize())
                ConvertBadge(
                    mark = item.mark,
                    degrees = item.degrees,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(video.durationText)
                    Spacer(Modifier.width(6.dp))
                    InfoChip(video.resolution)
                    Spacer(Modifier.width(6.dp))
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
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = PurplePrimary, checkmarkColor = OnPrimary)
                )
            }
        }
    }
}

@Composable
private fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onButton: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp)
        ) {
            Icon(icon, null, tint = PurplePrimary.copy(alpha = 0.4f), modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 14.sp, color = Color.Gray.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onButton,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(buttonText, color = OnPrimary)
            }
        }
    }
}
