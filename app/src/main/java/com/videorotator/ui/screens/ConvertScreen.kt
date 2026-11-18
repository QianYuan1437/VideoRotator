package com.videorotator.ui.screens

import androidx.compose.runtime.collectAsState
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.components.InfoChip
import com.videorotator.ui.components.OrientationChip
import com.videorotator.ui.components.VideoThumbnail
import com.videorotator.ui.theme.CardSurface
import com.videorotator.ui.theme.OnPrimary
import com.videorotator.ui.theme.PurpleBg
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.OutputTarget
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import com.videorotator.viewmodel.ConvertViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    videoInfo: VideoInfo,
    viewModel: ConvertViewModel,
    outputTreeUri: Uri?,
    onPickOutputDirectory: () -> Unit,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 默认值取自「转换配置」；这里的修改只对本次转换生效
    var target by remember {
        mutableStateOf(
            AppPrefs.getOutputTarget(context).let {
                if (it == OutputTarget.CUSTOM && outputTreeUri == null) OutputTarget.MOVIES else it
            }
        )
    }
    var clockwise by remember { mutableStateOf(AppPrefs.isClockwise(context)) }
    var showPathDialog by remember { mutableStateOf(false) }
    // 在对话框中点了「自定义路径」并唤起选择器：选完后自动切到自定义
    var waitingForPick by remember { mutableStateOf(false) }
    LaunchedEffect(outputTreeUri) {
        if (waitingForPick && outputTreeUri != null) {
            target = OutputTarget.CUSTOM
            waitingForPick = false
        }
    }

    val targetPath = targetPath(context, target, outputTreeUri)
    // 已有相同参数的产物时提示「将替换」，避免以为会多出一份
    val successJobs by viewModel.successJobs.collectAsState()
    val willReplace = remember(successJobs, target, outputTreeUri, clockwise) {
        viewModel.hasSameOutput(videoInfo, target, outputTreeUri, if (clockwise) 90 else 270)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.convert_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back), tint = PurpleDark)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 视频信息卡片
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp, 70.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PurpleLight),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoThumbnail(uri = videoInfo.uri, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            videoInfo.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoChip(videoInfo.durationText)
                            Spacer(Modifier.width(6.dp))
                            InfoChip(videoInfo.resolution)
                            Spacer(Modifier.width(6.dp))
                            OrientationChip(videoInfo)
                        }
                    }
                }
            }

            // 旋转方向
            SectionCard(title = stringResource(R.string.rotate_direction)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DirectionOption(
                        icon = Icons.Filled.Rotate90DegreesCw,
                        label = stringResource(R.string.cw_90),
                        selected = clockwise,
                        onClick = { clockwise = true },
                        modifier = Modifier.weight(1f)
                    )
                    DirectionOption(
                        icon = Icons.Filled.Rotate90DegreesCcw,
                        label = stringResource(R.string.ccw_90),
                        selected = !clockwise,
                        onClick = { clockwise = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 保存路径
            SectionCard(
                title = stringResource(R.string.save_location),
                action = {
                    TextButton(onClick = { showPathDialog = true }) {
                        Text(stringResource(R.string.change), color = PurplePrimary)
                    }
                }
            ) {
                Surface(
                    onClick = { showPathDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = PurpleLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = PurplePrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(target.labelRes), fontSize = 14.sp, color = PurpleDark, fontWeight = FontWeight.Medium)
                            Text(
                                targetPath,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (willReplace) {
                Text(
                    stringResource(R.string.convert_will_replace),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }

            // 开始转换：派发到任务队列后跳转到「转换列表」查看实时进度；
            // 同一任务已在队列中（如连点）时不重复加入
            Button(
                onClick = {
                    val id = viewModel.startJob(
                        videoInfo = videoInfo,
                        target = target,
                        outputTreeUri = if (target == OutputTarget.CUSTOM) outputTreeUri else null,
                        degrees = if (clockwise) 90 else 270
                    )
                    if (id == null) {
                        Toast.makeText(context, R.string.already_queued, Toast.LENGTH_SHORT).show()
                    }
                    onStarted()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(
                    if (clockwise) Icons.Filled.Rotate90DegreesCw else Icons.Filled.Rotate90DegreesCcw,
                    null, tint = OnPrimary, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.start_convert), color = OnPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }

    if (showPathDialog) {
        PathSelectionDialog(
            current = target,
            outputTreeUri = outputTreeUri,
            onSelect = { t ->
                if (t == OutputTarget.CUSTOM && outputTreeUri == null) {
                    waitingForPick = true
                    onPickOutputDirectory()
                } else {
                    target = t
                }
                showPathDialog = false
            },
            onRepick = {
                waitingForPick = true
                showPathDialog = false
                onPickOutputDirectory()
            },
            onDismiss = { showPathDialog = false }
        )
    }
}

private fun targetPath(context: android.content.Context, target: OutputTarget, tree: Uri?): String = when (target) {
    OutputTarget.APP_PRIVATE -> VideoUtils.privateOutputDir(context).absolutePath
    OutputTarget.CUSTOM -> tree?.let { VideoUtils.describeTreeUri(it) } ?: context.getString(R.string.not_selected)
    else -> Environment.getExternalStoragePublicDirectory(target.publicDir).absolutePath
}

@Composable
private fun SectionCard(
    title: String,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PurpleDark)
                action()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DirectionOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PurplePrimary else PurpleLight,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 14.dp)
        ) {
            Icon(icon, null, tint = if (selected) OnPrimary else PurplePrimary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
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
private fun PathSelectionDialog(
    current: OutputTarget,
    outputTreeUri: Uri?,
    onSelect: (OutputTarget) -> Unit,
    onRepick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_save_location), fontWeight = FontWeight.Bold, color = PurpleDark) },
        text = {
            Column {
                OutputTarget.values().forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (t == current) PurpleLight else Color.Transparent)
                            .clickable { onSelect(t) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = t == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary),
                            modifier = Modifier.padding(12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(t.labelRes), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = PurpleDark)
                            Text(
                                targetPath(context, t, outputTreeUri),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (t == OutputTarget.CUSTOM && outputTreeUri != null) {
                            TextButton(onClick = onRepick) {
                                Text(stringResource(R.string.replace), color = PurplePrimary, fontSize = 13.sp)
                            }
                        }
                    }
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
