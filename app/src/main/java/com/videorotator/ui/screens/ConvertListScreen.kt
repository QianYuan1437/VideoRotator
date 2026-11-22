package com.videorotator.ui.screens

import com.videorotator.ui.components.OnFilterEmptied
import com.videorotator.utils.VideoUtils
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import com.videorotator.ui.components.formatSize
import com.videorotator.ui.components.DeleteOutputsDialog
import com.videorotator.ui.components.FreeSpaceBanner
import com.videorotator.ui.components.Bullet
import com.videorotator.ui.components.BulletDialog
import com.videorotator.ui.components.FilterPill
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.annotation.StringRes
import com.videorotator.utils.toFileRef
import com.videorotator.viewmodel.ConvertEngine
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import com.videorotator.utils.JobErrors
import com.videorotator.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.components.ConvertBadge
import com.videorotator.ui.components.InfoChip
import com.videorotator.ui.components.OrientationBadge
import com.videorotator.ui.components.ListJumpButtons
import com.videorotator.ui.components.DeleteOriginalDialog
import com.videorotator.ui.components.ScreenHorizontalPadding
import com.videorotator.ui.components.ScreenTitle
import com.videorotator.ui.components.ScreenTopPadding
import com.videorotator.ui.components.VideoThumbnail
import com.videorotator.ui.components.evictThumbnail
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.ConvertedGreen
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.utils.ConvertMark
import com.videorotator.utils.OutputTarget
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.ConvertJob
import com.videorotator.viewmodel.ConvertViewModel
import com.videorotator.viewmodel.ConvertedVideo
import com.videorotator.viewmodel.JobState
import com.videorotator.viewmodel.SortKey

const val CONVERT_TAB_PROGRESS = 1
const val CONVERT_TAB_CONVERTED = 2

private val ErrorRed = Color(0xFFD32F2F)

/** 「转换进度」筛选 */
private enum class JobFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    ACTIVE(R.string.jobs_active),
    DONE(R.string.jobs_done),
    FAILED(R.string.jobs_failed);

    fun matches(job: ConvertJob) = when (this) {
        ALL -> true
        ACTIVE -> job.isActive
        DONE -> job.state == JobState.SUCCESS
        FAILED -> job.state == JobState.FAILED || job.state == JobState.CANCELLED
    }
}

/** 「已转视频」筛选：原视频是否还在（还在的可删除以释放空间） */
private enum class SourceFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    HAS_SOURCE(R.string.src_present),
    SOURCE_GONE(R.string.src_gone);

    fun matches(item: ConvertedVideo) = when (this) {
        ALL -> true
        HAS_SOURCE -> item.original != null
        SOURCE_GONE -> item.original == null
    }
}

@Composable
fun ConvertListScreen(
    viewModel: ConvertViewModel,
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onPlay: (VideoInfo) -> Unit,
    /** 两个子列表的滚动位置由 MainScreen 持有：切换页面后回来仍停在原处 */
    progressListState: LazyListState = rememberLazyListState(),
    convertedListState: LazyListState = rememberLazyListState(),
    /** 上次浏览（播放）的视频 uri，悬浮「定位」按钮据此跳转 */
    lastViewedUri: String? = null,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    val selectedIds by viewModel.selectedJobIds.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val converted by viewModel.converted.collectAsState()
    val convertedLoading by viewModel.convertedLoading.collectAsState()

    // 「已转视频」排序（之前是占位代码，选了也不生效）
    var sortKey by rememberSaveable { mutableStateOf(SortKey.DATE_DESC) }
    var jobFilter by rememberSaveable { mutableStateOf(JobFilter.ALL) }
    var sourceFilter by rememberSaveable { mutableStateOf(SourceFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val convertedSelectMode by viewModel.convertedSelectMode.collectAsState()

    // 进入页面及成功产物变化时刷新「已转视频」（产物流只在成功任务变化时发射，进度不触发）
    val successJobs by viewModel.successJobs.collectAsState()
    LaunchedEffect(successJobs) { viewModel.refreshConverted() }

    Column(modifier = modifier.fillMaxSize().background(PurpleBg)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHorizontalPadding, end = 4.dp, top = ScreenTopPadding)
        ) {
            ScreenTitle(stringResource(R.string.tab_jobs)) {
                if (currentTab == CONVERT_TAB_PROGRESS) {
                    if (jobs.any { it.isFinished }) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, stringResource(R.string.clear_finished), tint = PurpleDark)
                        }
                    }
                    if (jobs.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isSelectMode) viewModel.exitSelectMode() else viewModel.enterSelectMode()
                        }) {
                            Icon(
                                if (isSelectMode) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                stringResource(R.string.multi_select),
                                tint = if (isSelectMode) PurplePrimary else PurpleDark
                            )
                        }
                    }
                } else {
                    IconButton(onClick = { viewModel.refreshConverted() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.refresh), tint = PurpleDark)
                    }
                    ConvertedSortButton(sortKey = sortKey, onSelected = { sortKey = it })
                    if (converted.isNotEmpty()) {
                        IconButton(onClick = {
                            if (convertedSelectMode) viewModel.exitConvertedSelectMode() else viewModel.enterConvertedSelectMode()
                        }) {
                            Icon(
                                if (convertedSelectMode) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                stringResource(R.string.multi_select),
                                tint = if (convertedSelectMode) PurplePrimary else PurpleDark
                            )
                        }
                    }
                }
            }
        }
        TabHeader(
            currentTab = currentTab,
            progressCount = jobs.count { it.isActive },
            convertedCount = converted.size,
            onTabSelected = {
                onTabSelected(it)
                viewModel.exitSelectMode()
                viewModel.exitConvertedSelectMode()
            }
        )

        when (currentTab) {
            CONVERT_TAB_PROGRESS -> {
                val sorted = remember(jobs) { jobs.sortedByDescending { it.createdAt } }
                val jobCounts = remember(jobs) { JobFilter.values().associateWith { f -> jobs.count { f.matches(it) } } }
                OnFilterEmptied(jobFilter, jobCounts[jobFilter] ?: 0) {
                    if (jobFilter != JobFilter.ALL) jobFilter = JobFilter.ALL
                }
                PillRow {
                    JobFilter.values().forEach { f ->
                        FilterPill(
                            text = stringResource(f.labelRes) + " ${jobCounts[f] ?: 0}",
                            selected = f == jobFilter,
                            onClick = {
                                jobFilter = f
                                viewModel.deselectAllJobs()
                            }
                        )
                    }
                }
                ProgressTab(
                jobs = remember(sorted, jobFilter) { sorted.filter { jobFilter.matches(it) } },
                selectedIds = selectedIds,
                isSelectMode = isSelectMode,
                viewModel = viewModel,
                onPlay = onPlay,
                listState = progressListState,
                lastViewedUri = lastViewedUri
            )
            }
            else -> ConvertedVideosTab(
                items = remember(converted, sortKey, sourceFilter) {
                    sortConverted(converted.filter { sourceFilter.matches(it) }, sortKey)
                },
                allItems = converted,
                sourceFilter = sourceFilter,
                onSourceFilter = {
                    sourceFilter = it
                    viewModel.setSelectedConverted(emptySet())
                },
                loading = convertedLoading,
                viewModel = viewModel,
                onPlay = onPlay,
                listState = convertedListState,
                lastViewedUri = lastViewedUri
            )
        }
    }

    if (confirmClear) {
        BulletDialog(
            title = stringResource(R.string.clear_finished_title),
            bullets = listOf(
                Bullet(stringResource(R.string.b_clear_records, jobs.count { it.isFinished })),
                Bullet(stringResource(R.string.b_files_kept))
            ),
            confirmText = stringResource(R.string.clear_action),
            onConfirm = {
                viewModel.clearFinished()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/** 横向可滚动的筛选标签行 */
@Composable
private fun PillRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

private fun sortConverted(list: List<ConvertedVideo>, key: SortKey): List<ConvertedVideo> = when (key) {
    SortKey.DATE_DESC -> list.sortedByDescending { it.video.dateAdded }
    SortKey.DATE_ASC -> list.sortedBy { it.video.dateAdded }
    SortKey.NAME_ASC -> list.sortedBy { it.video.displayName.lowercase() }
    SortKey.NAME_DESC -> list.sortedByDescending { it.video.displayName.lowercase() }
    SortKey.SIZE_DESC -> list.sortedByDescending { it.video.size }
    SortKey.DURATION_DESC -> list.sortedByDescending { it.video.duration }
}

@Composable
private fun ConvertedSortButton(sortKey: SortKey, onSelected: (SortKey) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Sort, stringResource(R.string.sort), tint = PurpleDark)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortKey.values().forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(key.labelRes),
                            fontWeight = if (key == sortKey) FontWeight.Bold else FontWeight.Normal,
                            color = if (key == sortKey) PurplePrimary else PurpleDark
                        )
                    },
                    leadingIcon = {
                        if (key == sortKey) {
                            Icon(Icons.Filled.Check, null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        onSelected(key)
                        open = false
                    }
                )
            }
        }
    }
}

/**
 * 二级 Tab：转换进度 / 已转视频
 */
@Composable
private fun TabHeader(
    currentTab: Int,
    progressCount: Int,
    convertedCount: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 4.dp)
    ) {
        TabPill(
            text = stringResource(R.string.tab_progress) + if (progressCount > 0) " ($progressCount)" else "",
            selected = currentTab == CONVERT_TAB_PROGRESS,
            onClick = { onTabSelected(CONVERT_TAB_PROGRESS) },
            modifier = Modifier.weight(1f)
        )
        TabPill(
            text = stringResource(R.string.tab_converted) + if (convertedCount > 0) " ($convertedCount)" else "",
            selected = currentTab == CONVERT_TAB_CONVERTED,
            onClick = { onTabSelected(CONVERT_TAB_CONVERTED) },
            modifier = Modifier.weight(1f)
        )
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) PurpleDark else Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        // 选中指示器：底部圆角横条
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(36.dp)
                .clip(RoundedCornerShape(2.dp))
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
    viewModel: ConvertViewModel,
    onPlay: (VideoInfo) -> Unit,
    listState: LazyListState,
    lastViewedUri: String?
) {
    var menuJob by remember { mutableStateOf<ConvertJob?>(null) }
    // 删除视频文件前确认（first = 任务，second = 是否同时删除记录）
    var pendingFileDelete by remember { mutableStateOf<Pair<ConvertJob, Boolean>?>(null) }
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectMode) {
            SelectActionBar(
                selectedCount = selectedIds.size,
                totalCount = jobs.size,
                // 全选当前筛选下可见的任务
                onSelectAll = { viewModel.setSelectedJobs(jobs.map { it.id }.toSet()) },
                onDeselectAll = { viewModel.deselectAllJobs() },
                onDeleteSelected = { deleteIds = selectedIds },
                retryableCount = jobs.count { it.id in selectedIds && (it.state == JobState.FAILED || it.state == JobState.CANCELLED) },
                onRetrySelected = {
                    viewModel.retryJobs(jobs.filter {
                        it.id in selectedIds && (it.state == JobState.FAILED || it.state == JobState.CANCELLED)
                    }.map { it.id })
                },
                onExit = { viewModel.exitSelectMode() }
            )
        }

        if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.no_jobs),
                subtitle = stringResource(R.string.no_jobs_desc)
            )
        } else Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 8.dp, bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(jobs, key = { it.id }) { job ->
                    ConvertJobCard(
                        job = job,
                        isSelectMode = isSelectMode,
                        isSelected = selectedIds.contains(job.id),
                        onClick = {
                            when {
                                isSelectMode -> viewModel.toggleSelect(job.id)
                                job.hasOutput -> viewModel.outputInfo(job)?.let(onPlay)
                                else -> menuJob = job
                            }
                        },
                        onLongClick = {
                            if (!isSelectMode) viewModel.enterSelectMode(job.id) else viewModel.toggleSelect(job.id)
                        },
                        onMore = { menuJob = job },
                        onCancel = { viewModel.cancelJob(job.id) }
                    )
                }
            }
            val lastViewedIndex = remember(jobs, lastViewedUri) {
                lastViewedUri?.let { uri ->
                    jobs.indexOfFirst { it.outputUri?.toString() == uri || it.videoInfo.uri.toString() == uri }
                }
            }
            ListJumpButtons(
                listState = listState,
                lastViewedIndex = lastViewedIndex,
                lastViewedKey = lastViewedUri,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }

    menuJob?.let { job ->
        JobActionDialog(
            job = job,
            onDismiss = { menuJob = null },
            onPlay = {
                viewModel.outputInfo(job)?.let(onPlay)
                menuJob = null
            },
            onRetry = {
                viewModel.retryJob(job.id)
                menuJob = null
            },
            onReverse = {
                viewModel.reverseRotateJob(job.id)
                menuJob = null
            },
            onDeleteRecord = {
                viewModel.removeJob(job.id)
                menuJob = null
            },
            onDeleteFile = {
                pendingFileDelete = job to false
                menuJob = null
            },
            onDeleteBoth = {
                pendingFileDelete = job to true
                menuJob = null
            }
        )
    }

    pendingFileDelete?.let { (job, withRecord) ->
        val name = viewModel.outputInfo(job)?.displayName ?: job.videoInfo.displayName
        BulletDialog(
            title = stringResource(if (withRecord) R.string.action_delete_both else R.string.delete_file_title),
            bullets = listOfNotNull(
                Bullet(stringResource(R.string.b_delete_target, name)),
                Bullet(stringResource(if (withRecord) R.string.b_record_too else R.string.b_record_kept)),
                Bullet(stringResource(R.string.b_irreversible))
            ),
            confirmText = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                job.outputUri?.let { evictThumbnail(it) }
                if (withRecord) viewModel.removeJobAndFile(job.id) else viewModel.removeFileOnly(job.id)
                pendingFileDelete = null
            },
            onDismiss = { pendingFileDelete = null }
        )
    }

    deleteIds?.let { ids ->
        var alsoFiles by remember { mutableStateOf(false) }
        BulletDialog(
            title = stringResource(R.string.delete_jobs_title, ids.size),
            bullets = listOf(
                Bullet(stringResource(R.string.b_delete_records, ids.size)),
                Bullet(stringResource(if (alsoFiles) R.string.b_files_deleted_too else R.string.b_files_kept))
            ),
            confirmText = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                if (alsoFiles) viewModel.removeJobsAndFiles(ids) else ids.forEach { viewModel.removeJob(it) }
                viewModel.exitSelectMode()
                deleteIds = null
            },
            onDismiss = { deleteIds = null },
            extra = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { alsoFiles = !alsoFiles }
                ) {
                    Checkbox(
                        checked = alsoFiles,
                        onCheckedChange = { alsoFiles = it },
                        colors = CheckboxDefaults.colors(checkedColor = PurplePrimary, checkmarkColor = OnPrimary)
                    )
                    Text(stringResource(R.string.delete_jobs_also_files), fontSize = 14.sp, color = PurpleDark)
                }
            }
        )
    }
}

/** 「转换进度」多选操作栏（两行）：数量 / 全选 / 退出；重试 / 删除 */
@Composable
private fun SelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    retryableCount: Int,
    onRetrySelected: () -> Unit,
    onExit: () -> Unit
) {
    Surface(color = PurplePrimary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.selected_count, selectedCount, totalCount),
                    color = PurpleDark, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                val allSelected = totalCount > 0 && selectedCount >= totalCount
                TextButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                    Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all), color = PurplePrimary, fontSize = 13.sp)
                }
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.exit), color = PurpleDark, fontSize = 13.sp)
                }
            }
            if (selectedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    // 失败 / 取消的任务可批量重新转换
                    if (retryableCount > 0) {
                        Button(
                            onClick = onRetrySelected,
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Replay, null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.retry_n, retryableCount), color = OnPrimary, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = onDeleteSelected,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_n, selectedCount), color = ErrorRed, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun directionLabel(degrees: Int): String = when (degrees) {
    90 -> stringResource(R.string.dir_cw)
    270 -> stringResource(R.string.dir_ccw)
    else -> stringResource(R.string.dir_other, degrees)
}

@Composable
private fun targetLabel(job: ConvertJob): String = stringResource(job.target.labelRes)

/** 错误码按当前语言显示；旧版本记录里的文字原样显示 */
@Composable
private fun errorText(error: String?): String {
    val res = JobErrors.resOf(error)
    return when {
        res != null -> stringResource(res)
        error.isNullOrBlank() -> stringResource(R.string.unknown)
        else -> error
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvertJobCard(
    job: ConvertJob,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    onCancel: () -> Unit
) {
    val (stateText, stateColor) = when (job.state) {
        JobState.QUEUED -> stringResource(R.string.state_queued) to Color.Gray
        JobState.RUNNING -> stringResource(R.string.state_running, (job.progress * 100).toInt()) to PurpleDark
        JobState.SUCCESS -> stringResource(R.string.state_success) to ConvertedGreen
        JobState.FAILED -> stringResource(R.string.state_failed, errorText(job.error)) to ErrorRed
        JobState.CANCELLED -> stringResource(R.string.state_cancelled) to Color.Gray
    }

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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图：完成后显示产物（竖屏），否则显示原视频
            Box(
                modifier = Modifier
                    .size(72.dp, 54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(uri = job.outputUri ?: job.videoInfo.uri, modifier = Modifier.fillMaxSize())
                if (job.hasOutput) {
                    Icon(
                        Icons.Filled.PlayArrow, stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    job.videoInfo.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stateText,
                    fontSize = 12.sp,
                    color = stateColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.job_subtitle, directionLabel(job.degrees), targetLabel(job)),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                isSelectMode -> Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = PurplePrimary, checkmarkColor = OnPrimary)
                )
                job.isActive -> IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, stringResource(R.string.cancel), tint = Color.Gray)
                }
                else -> IconButton(onClick = onMore) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions), tint = Color.Gray)
                }
            }
        }
        if (job.isActive) {
            LinearProgressIndicator(
                progress = job.progress.coerceIn(0f, 1f),
                color = PurplePrimary,
                trackColor = PurpleLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        } else if (job.hasOutput) {
            Text(
                job.outputPath ?: job.outputUri.toString(),
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            )
        }
    }
}

/**
 * 任务操作菜单（点「⋮」或点击未完成的任务弹出）。
 */
@Composable
private fun JobActionDialog(
    job: ConvertJob,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onReverse: () -> Unit,
    onDeleteRecord: () -> Unit,
    onDeleteFile: () -> Unit,
    onDeleteBoth: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                job.videoInfo.displayName,
                fontWeight = FontWeight.Bold,
                color = PurpleDark,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                if (job.hasOutput) {
                    DialogActionRow(Icons.Filled.PlayArrow, stringResource(R.string.action_play_output), onPlay)
                    DialogActionRow(
                        Icons.Filled.Rotate90DegreesCcw,
                        stringResource(R.string.action_reverse_wrong, directionLabel((360 - job.degrees) % 360)),
                        onReverse
                    )
                } else if (job.isFinished) {
                    DialogActionRow(Icons.Filled.Replay, stringResource(R.string.action_retry, directionLabel(job.degrees)), onRetry)
                    DialogActionRow(
                        Icons.Filled.Rotate90DegreesCcw,
                        stringResource(R.string.action_reverse, directionLabel((360 - job.degrees) % 360)),
                        onReverse
                    )
                }
                Divider(color = PurpleLight, modifier = Modifier.padding(vertical = 4.dp))
                DialogActionRow(Icons.Filled.History, stringResource(R.string.action_delete_record), onDeleteRecord)
                if (job.hasOutput) {
                    DialogActionRow(Icons.Filled.Delete, stringResource(R.string.action_delete_file), onDeleteFile, danger = true)
                    DialogActionRow(Icons.Filled.DeleteSweep, stringResource(R.string.action_delete_both), onDeleteBoth, danger = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = PurpleDark) }
        },
        containerColor = CardSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DialogActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) ErrorRed else PurplePrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = if (danger) ErrorRed else PurpleDark)
    }
}

// ============== Tab 2: 已转视频 ==============

@Composable
private fun ConvertedVideosTab(
    items: List<ConvertedVideo>,
    allItems: List<ConvertedVideo>,
    sourceFilter: SourceFilter,
    onSourceFilter: (SourceFilter) -> Unit,
    loading: Boolean,
    viewModel: ConvertViewModel,
    onPlay: (VideoInfo) -> Unit,
    listState: LazyListState,
    lastViewedUri: String?
) {
    val isSelectMode by viewModel.convertedSelectMode.collectAsState()
    val selected by viewModel.selectedConverted.collectAsState()
    // 删除确认：产物文件（单个 / 批量）、原视频（单个 / 批量）、反向旋转
    var pendingOutputs by remember { mutableStateOf<List<ConvertedVideo>?>(null) }
    var pendingOriginals by remember { mutableStateOf<List<ConvertedVideo>?>(null) }
    var pendingReverse by remember { mutableStateOf<ConvertedVideo?>(null) }

    fun keyOf(item: ConvertedVideo) = item.video.uri.toString()
    val sourceCounts = remember(allItems) { SourceFilter.values().associateWith { f -> allItems.count { f.matches(it) } } }
    OnFilterEmptied(sourceFilter, sourceCounts[sourceFilter] ?: 0) {
        if (sourceFilter != SourceFilter.ALL) onSourceFilter(SourceFilter.ALL)
    }

    Column(Modifier.fillMaxSize()) {
        PillRow {
            SourceFilter.values().forEach { f ->
                FilterPill(
                    text = stringResource(f.labelRes) + " ${sourceCounts[f] ?: 0}",
                    selected = f == sourceFilter,
                    onClick = { onSourceFilter(f) }
                )
            }
        }
        // 原视频仍在的产物：汇总可释放的空间，一键删除全部原视频
        // 多个产物可能来自同一个原视频：数量与大小都按原视频去重，与删除确认框一致
        val withSource = remember(items) { items.filter { it.original != null } }
        val sourceFiles = remember(withSource) { withSource.mapNotNull { it.original }.distinctBy { it.fileKey } }
        val deleting by viewModel.deleting.collectAsState()
        if (withSource.isNotEmpty() && !isSelectMode) {
            FreeSpaceBanner(
                text = stringResource(
                    R.string.banner_sources, sourceFiles.size,
                    formatSize(sourceFiles.sumOf { it.size })
                ),
                actionText = stringResource(R.string.delete_all),
                onAction = { pendingOriginals = withSource },
                modifier = Modifier.padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = 6.dp),
                busy = deleting
            )
        }
        if (isSelectMode) {
            val chosen = items.filter { keyOf(it) in selected }
            ConvertedSelectBar(
                selectedCount = chosen.size,
                totalCount = items.size,
                originalsCount = chosen.mapNotNull { it.original?.fileKey }.distinct().size,
                onSelectAll = { viewModel.setSelectedConverted(items.map { keyOf(it) }.toSet()) },
                onDeselectAll = { viewModel.setSelectedConverted(emptySet()) },
                onDeleteOriginals = { pendingOriginals = chosen.filter { it.original != null } },
                onDeleteFiles = { pendingOutputs = chosen },
                onExit = { viewModel.exitConvertedSelectMode() }
            )
        }

        when {
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PurplePrimary)
            }
            items.isEmpty() -> EmptyState(
                icon = Icons.Filled.VideoLibrary,
                title = stringResource(R.string.no_converted),
                subtitle = stringResource(R.string.no_converted_desc)
            )
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 4.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { keyOf(it) }) { item ->
                        ConvertedVideoCard(
                            item = item,
                            isSelectMode = isSelectMode,
                            isSelected = keyOf(item) in selected,
                            onClick = {
                                if (isSelectMode) viewModel.toggleConverted(keyOf(item)) else onPlay(item.video)
                            },
                            onLongClick = {
                                if (isSelectMode) viewModel.toggleConverted(keyOf(item))
                                else viewModel.enterConvertedSelectMode(keyOf(item))
                            },
                            onDelete = { pendingOutputs = listOf(item) },
                            onReverse = { pendingReverse = item },
                            onDeleteOriginal = { pendingOriginals = listOf(item) }
                        )
                    }
                }
                val lastViewedIndex = remember(items, lastViewedUri) {
                    lastViewedUri?.let { uri -> items.indexOfFirst { it.video.uri.toString() == uri } }
                }
                ListJumpButtons(
                    listState = listState,
                    lastViewedIndex = lastViewedIndex,
                    lastViewedKey = lastViewedUri,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }

    pendingOutputs?.let { list ->
        // 原视频已不在的产物是仅存的副本：在确认框里醒目提示
        val onlyCopies = list.count { item ->
            item.original == null && viewModel.engine.outputLinks.linkOf(item.video.uri, item.video.path) != null
        }
        DeleteOutputsDialog(
            names = list.map { it.video.displayName },
            totalBytes = list.sumOf { it.video.size },
            onlyCopies = onlyCopies,
            onConfirm = {
                viewModel.deleteOutputs(list)
                pendingOutputs = null
            },
            onDismiss = { pendingOutputs = null }
        )
    }
    pendingOriginals?.let { list ->
        val candidates = list.mapNotNull { item ->
            item.original?.let { ConvertEngine.DeleteCandidate(it, listOf(item.video.toFileRef())) }
        }
        if (candidates.isEmpty()) {
            pendingOriginals = null
        } else {
            DeleteOriginalDialog(
                candidates = candidates,
                onConfirm = {
                    candidates.forEach { evictThumbnail(it.original.uri) }
                    viewModel.deleteOriginals(candidates)
                    viewModel.exitConvertedSelectMode()
                    pendingOriginals = null
                },
                onDismiss = { pendingOriginals = null }
            )
        }
    }
    pendingReverse?.let { item ->
        val degrees = item.job?.degrees
        BulletDialog(
            title = stringResource(R.string.reverse_title),
            bullets = if (degrees != null) {
                listOf(
                    Bullet(stringResource(R.string.b_current_dir, directionLabel(degrees))),
                    Bullet(stringResource(R.string.b_new_dir, directionLabel((360 - degrees) % 360))),
                    Bullet(stringResource(R.string.b_reverse_from_source))
                )
            } else {
                listOf(
                    Bullet(stringResource(R.string.b_reverse_on_file)),
                    Bullet(stringResource(R.string.b_replace_this))
                )
            },
            confirmText = stringResource(R.string.start),
            onConfirm = {
                evictThumbnail(item.video.uri)
                viewModel.reverseConverted(item)
                pendingReverse = null
            },
            onDismiss = { pendingReverse = null }
        )
    }
}

/** 「已转视频」多选操作栏：第一行数量 / 全选 / 退出，第二行删除原视频 / 删除文件 */
@Composable
private fun ConvertedSelectBar(
    selectedCount: Int,
    totalCount: Int,
    originalsCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteOriginals: () -> Unit,
    onDeleteFiles: () -> Unit,
    onExit: () -> Unit
) {
    Surface(color = PurplePrimary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.selected_count, selectedCount, totalCount),
                    color = PurpleDark, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                val allSelected = totalCount > 0 && selectedCount >= totalCount
                TextButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                    Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all), color = PurplePrimary, fontSize = 13.sp)
                }
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.exit), color = PurpleDark, fontSize = 13.sp)
                }
            }
            if (selectedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    if (originalsCount > 0) {
                        Button(
                            onClick = onDeleteOriginals,
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete_originals_n, originalsCount), color = Color.White, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = onDeleteFiles,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_files_n, selectedCount), color = ErrorRed, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvertedVideoCard(
    item: ConvertedVideo,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onReverse: () -> Unit,
    onDeleteOriginal: () -> Unit
) {
    val video = item.video
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) lerp(CardSurface, PurplePrimary, 0.25f) else CardSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(uri = video.uri, modifier = Modifier.fillMaxSize())
                ConvertBadge(
                    mark = ConvertMark.OUTPUT,
                    // 文件名记录的是相对原视频的净角度（对产物再旋转时任务角度≠净角度）；
                    // 之前版本转换、没有任务记录的产物也能读出
                    degrees = VideoUtils.degreesInName(video.displayName) ?: item.job?.degrees,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                OrientationBadge(video, Modifier.align(Alignment.BottomEnd))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(video.durationText)
                    Spacer(Modifier.width(6.dp))
                    InfoChip(video.resolution)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${video.sizeText} · ${video.path?.substringBeforeLast('/')?.substringAfterLast('/') ?: stringResource(R.string.target_custom)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 原视频是否还在：一眼看出能否删除原视频释放空间
                Text(
                    item.original?.let { stringResource(R.string.source_at, it.name) } ?: stringResource(R.string.source_missing),
                    fontSize = 11.sp,
                    color = if (item.original != null) ConvertedGreen else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = PurplePrimary, checkmarkColor = OnPrimary)
                )
            }
        }
        if (!isSelectMode) {
            Divider(color = PurpleLight, thickness = 1.dp)
            // 底部操作区：删除文件 | 反向旋转 | 删除原视频（原视频仍在设备上时）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stacked = item.original != null
                CardAction(Icons.Filled.Delete, stringResource(R.string.delete_file), ErrorRed, onDelete, Modifier.weight(1f), stacked)
                ActionDivider()
                CardAction(Icons.Filled.Rotate90DegreesCcw, stringResource(R.string.reverse_title), PurpleDark, onReverse, Modifier.weight(1f), stacked)
                if (stacked) {
                    ActionDivider()
                    CardAction(Icons.Filled.DeleteSweep, stringResource(R.string.delete_original), ErrorRed, onDeleteOriginal, Modifier.weight(1f), true)
                }
            }
        }
    }
}


@Composable
private fun ActionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(PurpleLight)
    )
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 三个操作并排时改为图标在上、文字在下，英文等较长文案也放得下 */
    stacked: Boolean = false
) {
    if (stacked) {
        Column(
            modifier = modifier
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============== Empty states ==============

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(80.dp), tint = PurplePrimary.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
