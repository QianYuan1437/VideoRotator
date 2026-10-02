package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConvertState(
    val isConverting: Boolean = false,
    val progress: Float = 0f,
    val outputPath: String? = null,
    val errorMessage: String? = null,
    val success: Boolean = false
)

class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ConvertState())
    val state: StateFlow<ConvertState> = _state.asStateFlow()

    fun convertVideo(uri: Uri, degrees: Int = 90, outputDir: java.io.File? = null) {
        viewModelScope.launch {
            _state.value = ConvertState(isConverting = true, progress = 0f)
            try {
                val context = getApplication<Application>()
                val result = withContext(Dispatchers.IO) {
                    VideoUtils.rotateVideo(context, uri, degrees, outputDir) { progress ->
                        _state.value = _state.value.copy(progress = progress)
                    }
                }
                result.fold(
                    onSuccess = { path ->
                        _state.value = _state.value.copy(
                            isConverting = false,
                            progress = 1f,
                            outputPath = path,
                            success = true
                        )
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isConverting = false,
                            errorMessage = e.message ?: "转换失败"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isConverting = false,
                    errorMessage = e.message ?: "转换失败"
                )
            }
        }
    }

    fun resetState() {
        _state.value = ConvertState()
    }
}
