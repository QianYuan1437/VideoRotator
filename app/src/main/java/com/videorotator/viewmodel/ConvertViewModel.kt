package com.videorotator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.videorotator.utils.OutputTarget
import com.videorotator.utils.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 界面层：任务与产物状态来自进程级的 [ConvertEngine]（界面销毁后转换照常进行），
 * 这里只保存多选等纯界面状态。
 */
class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ConvertEngine.get(application)

    val jobs = engine.jobs
    val successJobs = engine.successJobs
    val activeCount = engine.activeCount
    val activeSourceUris = engine.activeSourceUris
    val queueDone = engine.queueDone
    val converted = engine.converted
    val convertedLoading = engine.convertedLoading
    val outputRevision = engine.outputRevision
    val links = engine.links
    val deleteConsent = engine.deleteConsent
    val filesDeleted = engine.filesDeleted

    private val _selectedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedJobIds: StateFlow<Set<String>> = _selectedJobIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    fun startJobWithDefaults(videoInfo: VideoInfo): String? = engine.startJobWithDefaults(videoInfo)

    fun startJob(videoInfo: VideoInfo, target: OutputTarget, outputTreeUri: Uri?, degrees: Int): String? =
        engine.startJob(videoInfo, target, outputTreeUri, degrees)

    fun hasSameOutput(videoInfo: VideoInfo, target: OutputTarget, outputTreeUri: Uri?, degrees: Int) =
        engine.hasSameOutput(videoInfo, target, outputTreeUri, degrees)

    fun reverseRotateJob(id: String) = engine.reverseRotateJob(id)
    fun retryJob(id: String) = engine.retryJob(id)
    fun cancelJob(id: String) = engine.cancelJob(id)
    fun outputInfo(job: ConvertJob) = engine.outputInfo(job)
    fun refreshConverted() = engine.refreshConverted()
    fun deleteConverted(item: ConvertedVideo) = engine.deleteConverted(item)
    fun reverseConverted(item: ConvertedVideo) = engine.reverseConverted(item)
    fun deleteOriginals(candidates: List<ConvertEngine.DeleteCandidate>) = engine.deleteOriginals(candidates)
    fun onDeleteConsentResult(granted: Boolean) = engine.onDeleteConsentResult(granted)
    fun deleteOutputs(items: List<ConvertedVideo>) {
        engine.deleteOutputs(items)
        exitConvertedSelectMode()
    }

    // ---- 「已转视频」多选 ----

    private val _convertedSelectMode = MutableStateFlow(false)
    val convertedSelectMode: StateFlow<Boolean> = _convertedSelectMode.asStateFlow()

    private val _selectedConverted = MutableStateFlow<Set<String>>(emptySet())
    val selectedConverted: StateFlow<Set<String>> = _selectedConverted.asStateFlow()

    fun enterConvertedSelectMode(initial: String? = null) {
        _convertedSelectMode.value = true
        _selectedConverted.value = setOfNotNull(initial)
    }

    fun exitConvertedSelectMode() {
        _convertedSelectMode.value = false
        _selectedConverted.value = emptySet()
    }

    fun toggleConverted(key: String) {
        _selectedConverted.update { if (key in it) it - key else it + key }
    }

    fun setSelectedConverted(keys: Set<String>) {
        _selectedConverted.value = keys
    }

    fun removeJob(id: String) {
        engine.removeJob(id)
        _selectedJobIds.update { it - id }
    }

    fun removeJobAndFile(id: String) = removeJobsAndFiles(setOf(id))

    fun removeJobsAndFiles(ids: Set<String>) {
        engine.removeJobsAndFiles(ids)
        _selectedJobIds.value = emptySet()
        if (jobs.value.isEmpty()) _isSelectMode.value = false
    }

    fun removeFileOnly(id: String) = engine.removeFileOnly(id)

    fun clearFinished() {
        engine.clearFinished()
        exitSelectMode()
    }

    fun enterSelectMode(initialId: String? = null) {
        _isSelectMode.value = true
        _selectedJobIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun exitSelectMode() {
        _isSelectMode.value = false
        _selectedJobIds.value = emptySet()
    }

    fun toggleSelect(id: String) {
        _selectedJobIds.update { if (id in it) it - id else it + id }
    }

    fun selectAllJobs() {
        _selectedJobIds.value = jobs.value.map { it.id }.toSet()
    }

    fun setSelectedJobs(ids: Set<String>) {
        _selectedJobIds.value = ids
    }

    fun retryJobs(ids: List<String>) {
        ids.forEach { engine.retryJob(it) }
        exitSelectMode()
    }

    fun deselectAllJobs() {
        _selectedJobIds.value = emptySet()
    }
}
