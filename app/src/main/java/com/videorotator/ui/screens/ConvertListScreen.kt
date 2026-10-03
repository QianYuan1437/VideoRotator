package com.videorotator.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.videorotator.viewmodel.ConvertJob
import com.videorotator.viewmodel.ConvertViewModel
import com.videorotator.viewmodel.JobState

@Composable
fun ConvertListScreen(
    viewModel: ConvertViewModel,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.jobs.collectAsState()
    val hasFinished = jobs.any {
        it.state == JobState.SUCCESS || it.state == JobState.FAILED || it.state == JobState.CANCELLED
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PurpleBg)
    ) {
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
            if (hasFinished) {
                TextButton(onClick = { viewModel.clearFinished() }) {
                    Text("清空已完成", color = PurplePrimary, fontSize = 14.sp)
                }
            }
        }

        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayCircleOutline,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = PurplePrimary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无转换任务",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "从视频列表进入播放器，点\u201c旋转90°转竖屏\u201d开始",
                        fontSize = 14.sp,
                        color = Color.Gray.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(jobs, key = { it.id }) { job ->
                    ConvertJobCard(
                        job = job,
                        onCancel = { viewModel.cancelJob(job.id) },
                        onRemove = { viewModel.removeJob(job.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertJobCard(
    job: ConvertJob,
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
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING -> IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, "取消", tint = Color.Gray)
                    }
                    JobState.SUCCESS, JobState.FAILED, JobState.CANCELLED -> IconButton(onClick = onRemove) {
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