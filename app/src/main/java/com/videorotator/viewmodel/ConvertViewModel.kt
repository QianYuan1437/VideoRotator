package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class JobState { QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }

data class ConvertJob(
    val id: String,
    val videoInfo: VideoInfo,
    val outputDir: File?,
    val degrees: Int = 90,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val outputPath: String? = null,
    val error: String? = null
)

class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val _jobs = MutableStateFlow<List<ConvertJob>>(emptyList())
    val jobs: StateFlow<List<ConvertJob>> = _jobs.asStateFlow()

    private val coroutineJobs = mutableMapOf<String, Job>()

    fun startJob(videoInfo: VideoInfo, outputDir: File?, degrees: Int = 90): String {
        val id = UUID.randomUUID().toString()
        val job = ConvertJob(id, videoInfo, outputDir, degrees, JobState.QUEUED, 0f)
        _jobs.value = _jobs.value + job
        runJob(job)
        return id
    }

    private fun runJob(job: ConvertJob) {
        val coroutineJob = viewModelScope.launch {
            updateJob(job.id) { it.copy(state = JobState.RUNNING, progress = 0f) }
            try {
                val context = getApplication<Application>()
                val result = withContext(Dispatchers.IO) {
                    VideoUtils.rotateVideo(context, job.videoInfo.uri, job.degrees, job.outputDir) { progress ->
                        updateJob(job.id) { it.copy(progress = progress) }
                    }
                }
                result.fold(
                    onSuccess = { path ->
                        updateJob(job.id) {
                            it.copy(state = JobState.SUCCESS, progress = 1f, outputPath = path)
                        }
                    },
                    onFailure = { e ->
                        updateJob(job.id) {
                            it.copy(state = JobState.FAILED, error = e.message ?: "转换失败")
                        }
                    }
                )
            } catch (e: Exception) {
                updateJob(job.id) {
                    it.copy(state = JobState.FAILED, error = e.message ?: "转换失败")
                }
            }
        }
        coroutineJobs[job.id] = coroutineJob
        coroutineJob.invokeOnCompletion { coroutineJobs.remove(job.id) }
    }

    fun cancelJob(id: String) {
        coroutineJobs[id]?.cancel()
        updateJob(id) { it.copy(state = JobState.CANCELLED) }
    }

    fun removeJob(id: String) {
        _jobs.value = _jobs.value.filter { it.id != id }
    }

    fun clearFinished() {
        _jobs.value = _jobs.value.filter {
            it.state == JobState.QUEUED || it.state == JobState.RUNNING
        }
    }

    private fun updateJob(id: String, fn: (ConvertJob) -> ConvertJob) {
        _jobs.value = _jobs.value.map { if (it.id == id) fn(it) else it }
    }
}