package com.videorotator.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videorotator.ui.components.VideoPlayer
import com.videorotator.ui.theme.PurpleDark
import com.videorotator.ui.theme.PurpleLight
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.utils.VideoInfo
import com.videorotator.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoInfo: VideoInfo,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onConvert: (VideoInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(videoInfo.uri) {
        viewModel.initializePlayer(videoInfo.uri)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    Scaffold(
        topBar = {
            if (!state.isFullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            videoInfo.displayName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "返回", tint = PurpleDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PurpleLight,
                        titleContentColor = PurpleDark
                    ),
                    actions = {
                        IconButton(onClick = { onConvert(videoInfo) }) {
                            Icon(
                                Icons.Filled.Rotate90DegreesCw,
                                "旋转转换",
                                tint = PurpleDark
                            )
                        }
                    }
                )
            }
        },
        containerColor = PurpleLight,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 视频播放器
            VideoPlayer(
                viewModel = viewModel,
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.isFullscreen) Modifier.fillMaxSize()
                        else Modifier.weight(1f)
                    )
            )

            // 底部信息栏
            if (!state.isFullscreen) {
                Surface(
                    color = White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    videoInfo.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${videoInfo.resolution} · ${videoInfo.durationText} · ${videoInfo.sizeText}",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onConvert(videoInfo) },
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Rotate90DegreesCw, null, tint = White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("旋转90°转竖屏", color = White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
