package com.videorotator.ui.components

import com.videorotator.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videorotator.ui.theme.PurplePrimary
import com.videorotator.ui.theme.White
import com.videorotator.viewmodel.PlayerState
import com.videorotator.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

private val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    viewModel: PlayerViewModel,
    state: PlayerState,
    /** 视频是否横屏：全屏时据此选择横屏 / 竖屏（之前一律强制横屏，竖屏视频全屏反而变小） */
    isLandscapeVideo: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player by viewModel.playerFlow.collectAsState()
    // 手势回调里读取最新状态：之前 pointerInput 只以 isLocked 为 key，闭包里的 state 是旧值，
    // 第二次拖动音量 / 亮度会从第一次的初值重新算起，进度拖动也基于过期的播放位置
    val latestState by rememberUpdatedState(state)

    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableStateOf(DragType.NONE) }
    var seekDelta by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var lockHintVisible by remember { mutableStateOf(false) }

    // 全屏处理：按视频方向锁定屏幕方向；退出全屏恢复为跟随系统
    LaunchedEffect(state.isFullscreen, isLandscapeVideo) {
        val act = activity ?: return@LaunchedEffect
        if (state.isFullscreen) {
            act.requestedOrientation = if (isLandscapeVideo) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            setSystemBarsVisible(act, false)
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setSystemBarsVisible(act, true)
        }
    }

    // 播放时保持屏幕常亮（之前只有全屏才常亮）
    LaunchedEffect(state.isPlaying) {
        val window = activity?.window ?: return@LaunchedEffect
        if (state.isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 离开播放页时恢复：屏幕方向、系统栏、常亮、手势调过的亮度（之前会一直残留到其他页面）
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setSystemBarsVisible(act, true)
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                setWindowBrightness(act, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }
    }

    // 锁定状态下点击屏幕显示解锁按钮，3 秒后自动隐藏
    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(3000)
            lockHintVisible = false
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
                if (state.isLocked) {
                    detectTapGestures(onTap = { lockHintVisible = true })
                } else {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { viewModel.togglePlayPause() }
                    )
                }
            }
            .pointerInput(state.isLocked) {
                if (!state.isLocked) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragType = DragType.SEEK
                            seekDelta = 0f
                        },
                        onDragEnd = {
                            val s = latestState
                            if (dragType == DragType.SEEK && kotlin.math.abs(seekDelta) >= 1f && s.duration > 0) {
                                val newPos = (s.currentPosition + (seekDelta * 1000).toLong())
                                    .coerceIn(0L, s.duration)
                                viewModel.seekTo(newPos)
                            }
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onDragCancel = {
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragType == DragType.SEEK) {
                                // 横向滑满一屏约等于 90 秒
                                seekDelta += dragAmount / size.width * 90f
                            }
                        }
                    )
                }
            }
            .pointerInput(state.isLocked) {
                if (!state.isLocked) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            // 左半屏亮度，右半屏音量
                            dragType = if (offset.x < size.width / 2) DragType.BRIGHTNESS else DragType.VOLUME
                            dragAccum = 0f
                            dragStartValue = if (dragType == DragType.VOLUME) {
                                latestState.volume
                            } else {
                                activity?.let { currentBrightness(it) } ?: 0.5f
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onDragCancel = {
                            isDragging = false
                            dragType = DragType.NONE
                        },
                        onVerticalDrag = { _, dragAmount ->
                            // 竖向滑过 2/3 高度对应 0→100%
                            dragAccum -= dragAmount / (size.height * 0.66f)
                            val value = (dragStartValue + dragAccum).coerceIn(0f, 1f)
                            when (dragType) {
                                DragType.VOLUME -> viewModel.setVolume(value)
                                DragType.BRIGHTNESS -> {
                                    viewModel.setBrightness(value)
                                    activity?.let { setWindowBrightness(it, value.coerceAtLeast(0.01f)) }
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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(false)
                }
            },
            update = { playerView ->
                if (playerView.player !== player) playerView.player = player
            },
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isBuffering && state.error == null) {
            CircularProgressIndicator(
                color = White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(44.dp)
            )
        }

        state.error?.let { err ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Icon(Icons.Filled.ErrorOutline, null, tint = White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.err_play), color = White, fontSize = 14.sp)
                if (err.isNotBlank()) {
                    Text(err, color = White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = formatSeekTime(seekDelta),
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val target = (state.currentPosition + (seekDelta * 1000).toLong()).coerceIn(0L, state.duration)
                    Text(
                        "${formatTime(target)} / ${formatTime(state.duration)}",
                        color = White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isDragging && (dragType == DragType.VOLUME || dragType == DragType.BRIGHTNESS),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val isVolume = dragType == DragType.VOLUME
            val value = if (isVolume) state.volume else state.brightness.coerceAtLeast(0f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Icon(
                    if (isVolume) (if (value > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff)
                    else Icons.Filled.BrightnessHigh,
                    null, tint = White, modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(value * 100).toInt()}%",
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
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleLock() }) {
                            Icon(Icons.Filled.Lock, stringResource(R.string.lock), tint = White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeedButton(
                                currentSpeed = state.playbackSpeed,
                                onSpeedChange = { viewModel.setSpeed(it) }
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { viewModel.toggleFullscreen() }) {
                                Icon(
                                    if (state.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    stringResource(R.string.fullscreen),
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
                        stringResource(R.string.play_pause),
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
                    // 进度条：拖动时只更新本地值，松手再 seek，避免拖动过程中反复解码
                    var scrubbing by remember { mutableStateOf<Float?>(null) }
                    val fraction = scrubbing
                        ?: if (state.duration > 0) state.currentPosition.toFloat() / state.duration else 0f
                    Slider(
                        value = fraction.coerceIn(0f, 1f),
                        onValueChange = {
                            scrubbing = it
                            viewModel.showControlsTemporarily()
                        },
                        onValueChangeFinished = {
                            scrubbing?.let { viewModel.seekTo((it * state.duration).toLong()) }
                            scrubbing = null
                        },
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
                        val shownPos = scrubbing?.let { (it * state.duration).toLong() } ?: state.currentPosition
                        Text(
                            text = "${formatTime(shownPos)} / ${formatTime(state.duration)}",
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
                                    stringResource(R.string.volume),
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

        // 锁定状态：之前点屏幕任意位置都会直接解锁，锁定形同虚设；
        // 现在点屏幕只显示解锁按钮，需要再点按钮才解锁
        AnimatedVisibility(
            visible = state.isLocked && lockHintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            IconButton(
                onClick = {
                    viewModel.toggleLock()
                    lockHintVisible = false
                    viewModel.showControlsTemporarily()
                },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.LockOpen, stringResource(R.string.unlock), tint = White)
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

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.5f)
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

        // 之前倍速列表是在按钮 Box 里直接展开的，会撑大顶栏、被视频区域裁掉；改为弹出菜单
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PLAYER_SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${speed}x",
                            fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                            color = if (speed == currentSpeed) PurplePrimary else Color.Unspecified
                        )
                    },
                    onClick = {
                        onSpeedChange(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}

private enum class DragType { NONE, SEEK, VOLUME, BRIGHTNESS }

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatSeekTime(deltaSeconds: Float): String {
    val sign = if (deltaSeconds >= 0) "+" else "-"
    val absMs = kotlin.math.abs(deltaSeconds * 1000).toLong()
    return "$sign${formatTime(absMs)}"
}

/** 当前窗口亮度；未覆盖时读系统亮度 */
private fun currentBrightness(activity: Activity): Float {
    val b = activity.window.attributes.screenBrightness
    if (b >= 0f) return b
    return try {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }
}

private fun setWindowBrightness(activity: Activity, brightness: Float) {
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness
    activity.window.attributes = layoutParams
}

private fun setSystemBarsVisible(activity: Activity, visible: Boolean) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (visible) {
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
