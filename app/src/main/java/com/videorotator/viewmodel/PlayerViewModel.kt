package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videorotator.utils.PlayerPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isLocked: Boolean = false,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = false,
    /** 亮度；-1 表示跟随系统（尚未手势调节过） */
    val brightness: Float = -1f,
    val error: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** 播放器实例复用：每次进入播放页不再重新构建 ExoPlayer（构建 + 解码器初始化是进入卡顿的主要来源） */
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _player.asStateFlow()
    val player: ExoPlayer? get() = _player.value

    private var controlsHideJob: Job? = null
    private var positionJob: Job? = null
    private var currentUri: Uri? = null

    // 进入转换页再返回时恢复到原来的播放位置
    private var resumeUri: Uri? = null
    private var resumePosition: Long = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = player ?: return
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    duration = if (playbackState == Player.STATE_READY) p.duration.coerceAtLeast(0L) else it.duration
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) scheduleHideControls()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.update { it.copy(error = error.localizedMessage ?: "", isBuffering = false) }
        }
    }

    private fun obtainPlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(getApplication()).build().apply { addListener(listener) }
        _player.value = p
        return p
    }

    fun initializePlayer(uri: Uri) {
        val context = getApplication<Application>()
        val speed = PlayerPrefs.getPlaybackSpeed(context)
        val autoPlay = PlayerPrefs.getAutoPlay(context)
        val p = obtainPlayer()
        if (currentUri != uri || p.mediaItemCount == 0) {
            currentUri = uri
            // 每个视频重新开始：之前全屏 / 锁定 / 进度等状态会带到下一个视频
            _state.value = PlayerState(playbackSpeed = speed)
            p.setMediaItem(MediaItem.fromUri(uri))
            p.playbackParameters = PlaybackParameters(speed)
            p.volume = 1f
            if (uri == resumeUri && resumePosition > 0) p.seekTo(resumePosition)
            p.prepare()
            p.playWhenReady = autoPlay
            resumeUri = null
        }
        startPositionUpdates()
        scheduleHideControls()
    }

    private fun startPositionUpdates() {
        // 只保留一个轮询协程；之前每次进入播放页都会再起一个永不结束的循环
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    _state.update {
                        it.copy(
                            currentPosition = p.currentPosition,
                            bufferedPosition = p.bufferedPosition,
                            duration = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        _state.update { it.copy(currentPosition = position) }
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setVolume(volume: Float) {
        player?.volume = volume
        _state.update { it.copy(volume = volume) }
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked) }
    }

    fun toggleControls() {
        _state.update { it.copy(showControls = !it.showControls) }
        if (_state.value.showControls) scheduleHideControls()
    }

    fun showControlsTemporarily() {
        if (_state.value.isLocked) return
        _state.update { it.copy(showControls = true) }
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            // 暂停时保持控制栏可见
            if (player?.isPlaying == true) _state.update { it.copy(showControls = false) }
        }
    }

    fun setBrightness(brightness: Float) {
        _state.update { it.copy(brightness = brightness) }
    }

    fun toggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /** 离开播放页：停止播放但保留播放器实例，下次进入直接复用 */
    fun stopPlayback() {
        positionJob?.cancel()
        controlsHideJob?.cancel()
        player?.let {
            resumeUri = currentUri
            resumePosition = it.currentPosition
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        currentUri = null
        _state.update { it.copy(isPlaying = false, isFullscreen = false, isLocked = false) }
    }

    /** Activity 进入后台时暂停 */
    fun pause() {
        player?.pause()
    }

    fun releasePlayer() {
        positionJob?.cancel()
        player?.removeListener(listener)
        player?.release()
        _player.value = null
        currentUri = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
