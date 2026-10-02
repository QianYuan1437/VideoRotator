package com.videorotator.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.PurpleSecondary
import com.videorotator.ui.theme.White
import com.videorotator.viewmodel.PlayerState
import com.videorotator.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    viewModel: PlayerViewModel,
    state: PlayerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableStateOf(DragType.NONE) }
    var seekDelta by remember { mutableFloatStateOf(0f) }
    var volumeDelta by remember { mutableFloatStateOf(0f) }
    var brightnessDelta by remember { mutableFloatStateOf(0f) }

    // 全屏处理
    LaunchedEffect(state.isFullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (state.isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemUI(activity)
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showSystemUI(activity)
        }
    }

    // 返回键处理
    BackHandler(enabled = state.isFullscreen) {
        viewModel.toggleFullscreen()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.isLocked) {
                if (!state.isLocked) {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { viewModel.togglePlayPause() }
                    )
                }
            }
            .pointerInput(state.isLocked) {
                if (!state.isLocked) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            isDragging = true
                            dragType = DragType.SEEK
                            seekDelta = 0f
                        },
                        onDragEnd = {
                            if (dragType == DragType.SEEK && kotlin.math.abs(seekDelta) > 50) {
                                val newPos = (state.currentPosition + (seekDelta * 1000).toLong())
                                    .coerceIn(0L, state.duration)
                                viewModel.seekTo(newPos)
                            }
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragType == DragType.SEEK) {
                                seekDelta += dragAmount / 5
                            }
                        }
                    )
                }
            }
            .pointerInput(state.isLocked) {
                if (!state.isLocked) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartY = offset.y
                            isDragging = true
                            // 左半屏亮度，右半屏音量
                            dragType = if (offset.x < size.width / 2) DragType.BRIGHTNESS else DragType.VOLUME
                            volumeDelta = 0f
                            brightnessDelta = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onVerticalDrag = { _, dragAmount ->
                            when (dragType) {
                                DragType.VOLUME -> {
                                    volumeDelta -= dragAmount / 300f
                                    val newVol = (state.volume + volumeDelta).coerceIn(0f, 1f)
                                    viewModel.setVolume(newVol)
                                }
                                DragType.BRIGHTNESS -> {
                                    brightnessDelta -= dragAmount / 300f
                                    val newBright = (state.brightness + brightnessDelta).coerceIn(0f, 1f)
                                    viewModel.setBrightness(newBright)
                                    setBrightness(context, newBright)
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
    ) {
        // 视频渲染层
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { playerView ->
                playerView.player = viewModel.player
            },
            modifier = Modifier.fillMaxSize()
        )

        // 亮度遮罩
        if (state.brightness < 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.5f - state.brightness) * 1.2f))
            )
        }

        // 拖动提示
        AnimatedVisibility(
            visible = isDragging && dragType == DragType.SEEK,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = formatSeekTime(seekDelta),
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isDragging && dragType == DragType.VOLUME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(end = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.VolumeUp, null, tint = White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(state.volume * 100).toInt()}%",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = isDragging && dragType == DragType.BRIGHTNESS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(start = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.BrightnessHigh, null, tint = White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(state.brightness * 100).toInt()}%",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 控制层
        AnimatedVisibility(
            visible = state.showControls && !state.isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变 + 控制栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleLock() }) {
                            Icon(Icons.Filled.Lock, "锁定", tint = White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 倍速
                            SpeedButton(
                                currentSpeed = state.playbackSpeed,
                                onSpeedChange = { viewModel.setSpeed(it) }
                            )
                            Spacer(Modifier.width(8.dp))
                            // 全屏
                            IconButton(onClick = { viewModel.toggleFullscreen() }) {
                                Icon(
                                    if (state.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    "全屏",
                                    tint = White
                                )
                            }
                        }
                    }
                }

                // 中央播放/暂停
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "播放/暂停",
                        tint = White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // 底部控制栏
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 进度条
                    Slider(
                        value = if (state.duration > 0) state.currentPosition.toFloat() / state.duration else 0f,
                        onValueChange = { viewModel.seekTo((it * state.duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = PurplePrimary,
                            activeTrackColor = PurplePrimary,
                            inactiveTrackColor = White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(state.currentPosition)} / ${formatTime(state.duration)}",
                            color = White,
                            fontSize = 12.sp
                        )
                        // 音量
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                viewModel.setVolume(if (state.volume > 0f) 0f else 1f)
                            }) {
                                Icon(
                                    if (state.volume > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    "音量",
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Slider(
                                value = state.volume,
                                onValueChange = { viewModel.setVolume(it) },
                                colors = SliderDefaults.colors(
                                    thumbColor = White,
                                    activeTrackColor = White,
                                    inactiveTrackColor = White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.width(80.dp)
                            )
                        }
                    }
                }
            }
        }

        // 锁定状态下的解锁按钮
        AnimatedVisibility(
            visible = state.isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.toggleLock() }
            ) {
                IconButton(
                    onClick = { viewModel.toggleLock() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 16.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, "解锁", tint = White)
                }
            }
        }
    }
}

@Composable
private fun SpeedButton(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Speed, null, tint = White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${currentSpeed}x",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 倍速选择弹出
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    speeds.forEach { speed ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (speed == currentSpeed) PurplePrimary else Color.Transparent,
                            modifier = Modifier.clickable {
                                onSpeedChange(speed)
                                expanded = false
                            }
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (speed == currentSpeed) White else White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class DragType { NONE, SEEK, VOLUME, BRIGHTNESS }

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

private fun formatSeekTime(deltaSeconds: Float): String {
    val sign = if (deltaSeconds >= 0) "+" else ""
    val absMs = kotlin.math.abs(deltaSeconds * 1000).toLong()
    return "$sign${formatTime(absMs)}"
}

private fun setBrightness(context: android.content.Context, brightness: Float) {
    val activity = context as? Activity ?: return
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness
    activity.window.attributes = layoutParams
}

private fun hideSystemUI(activity: Activity) {
    activity.window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    )
}

private fun showSystemUI(activity: Activity) {
    activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
}
