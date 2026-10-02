package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isLocked: Boolean = false,
    val showControls: Boolean = true,
    val isFullscreen: Boolean = false,
    val brightness: Float = 0.5f
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var controlsHideJob: kotlinx.coroutines.Job? = null

    fun initializePlayer(uri: Uri) {
        releasePlayer()
        val context = getApplication<Application>()
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _state.value = _state.value.copy(duration = duration)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                }
            })
        }
        startPositionUpdates()
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    _state.value = _state.value.copy(
                        currentPosition = p.currentPosition,
                        bufferedPosition = p.bufferedPosition,
                        duration = p.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        _state.value = _state.value.copy(currentPosition = position)
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setVolume(volume: Float) {
        player?.volume = volume
        _state.value = _state.value.copy(volume = volume)
    }

    fun toggleLock() {
        _state.value = _state.value.copy(isLocked = !_state.value.isLocked)
    }

    fun toggleControls() {
        _state.value = _state.value.copy(showControls = !_state.value.showControls)
        if (_state.value.showControls) scheduleHideControls()
    }

    fun showControlsTemporarily() {
        if (_state.value.isLocked) return
        _state.value = _state.value.copy(showControls = true)
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            _state.value = _state.value.copy(showControls = false)
        }
    }

    fun setBrightness(brightness: Float) {
        _state.value = _state.value.copy(brightness = brightness)
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
