package com.videorotator.viewmodel

import android.app.Application
import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class JobState { QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }

data class ConvertJob(
    val id: String,
    val videoInfo: VideoInfo,
    val outputDir: File? = null,
    val outputTreeUri: Uri? = null,
    val degrees: Int = 90,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val outputPath: String? = null,
    val error: String? = null
)

class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val _jobs = MutableStateFlow<List<ConvertJob>>(emptyList())
    val jobs: StateFlow<List<ConvertJob>> = _jobs.asStateFlow()

    private val _selectedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedJobIds: StateFlow<Set<String>> = _selectedJobIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    private val coroutineJobs = mutableMapOf<String, Job>()

    init {
        // 启动时从 SharedPreferences 恢复历史任务，避免进程被杀后丢失
        _jobs.value = loadPersistedJobs()
    }

    fun startJob(
        videoInfo: VideoInfo,
        outputDir: File? = null,
        outputTreeUri: Uri? = null,
        degrees: Int = 90
    ): String {
        val id = UUID.randomUUID().toString()
        val job = ConvertJob(
            id = id,
            videoInfo = videoInfo,
            outputDir = outputDir,
            outputTreeUri = outputTreeUri,
            degrees = degrees,
            state = JobState.QUEUED,
            progress = 0f
        )
        appendJob(job)
        runJob(job)
        return id
    }

    /**
     * 反向旋转：基于已有的成功任务，把度数取反后重新派发。
     * 输出目录沿用原任务设置（File 或 SAF 树 URI）。
     */
    fun reverseRotateJob(id: String) {
        val orig = _jobs.value.firstOrNull { it.id == id } ?: return
        val reverseDegrees = (360 - orig.degrees) % 360
        if (reverseDegrees == 0) reverseDegrees.coerceAtLeast(270) // 360 -> 270
        // 派发新任务
        startJob(
            videoInfo = orig.videoInfo,
            outputDir = orig.outputDir,
            outputTreeUri = orig.outputTreeUri,
            degrees = reverseDegrees.takeIf { it > 0 } ?: 270
        )
        // 删除原任务（避免列表里同时存在已转和反向转两条记录）
        removeJob(id)
    }

    private fun runJob(job: ConvertJob) {
        val coroutineJob = viewModelScope.launch {
            updateJob(job.id) { it.copy(state = JobState.RUNNING, progress = 0f) }
            try {
                val context = getApplication<Application>()
                val result = withContext(Dispatchers.IO) {
                    VideoUtils.rotateVideo(
                        context = context,
                        inputUri = job.videoInfo.uri,
                        degrees = job.degrees,
                        outputDir = job.outputDir,
                        outputTreeUri = job.outputTreeUri
                    ) { progress ->
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
        if (_selectedJobIds.value.contains(id)) {
            _selectedJobIds.value = _selectedJobIds.value - id
        }
    }

    /** 删除任务记录并删除已生成的输出文件 */
    fun removeJobAndFile(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id }
        if (job != null) {
            deleteOutputFile(job)
        }
        removeJob(id)
    }

    /** 删除一组任务（多选） */
    fun removeJobsAndFiles(ids: Set<String>) {
        _jobs.value.filter { it.id in ids }.forEach { deleteOutputFile(it) }
        _jobs.value = _jobs.value.filter { it.id !in ids }
        _selectedJobIds.value = emptySet()
    }

    /** 仅删文件，保留任务记录（标 FAIL，方便用户追溯） */
    fun removeFileOnly(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        deleteOutputFile(job)
        updateJob(id) { it.copy(outputPath = null, state = JobState.FAILED, error = "输出文件已删除") }
    }

    private fun deleteOutputFile(job: ConvertJob) {
        try {
            job.outputPath?.let { File(it).delete() }
            job.outputTreeUri?.let { uri ->
                val context = getApplication<Application>()
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                doc?.delete()
            }
        } catch (_: Exception) {}
    }

    fun clearFinished() {
        _jobs.value = _jobs.value.filter {
            it.state == JobState.QUEUED || it.state == JobState.RUNNING
        }
        _selectedJobIds.value = emptySet()
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
        val s = _selectedJobIds.value.toMutableSet()
        if (s.contains(id)) s.remove(id) else s.add(id)
        _selectedJobIds.value = s
    }

    fun selectAllJobs() {
        _selectedJobIds.value = _jobs.value.map { it.id }.toSet()
    }

    fun deselectAllJobs() {
        _selectedJobIds.value = emptySet()
    }

    private fun appendJob(job: ConvertJob) {
        _jobs.value = _jobs.value + job
    }

    private fun updateJob(id: String, fn: (ConvertJob) -> ConvertJob) {
        _jobs.value = _jobs.value.map { if (it.id == id) fn(it) else it }
    }

    // ---- 持久化 ----

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("convert_jobs", Context.MODE_PRIVATE)
    }

    private fun loadPersistedJobs(): List<ConvertJob> {
        return try {
            val raw = prefs.getString("jobs", null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i).toConvertJob() }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistJobs() {
        try {
            val arr = JSONArray()
            _jobs.value.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("jobs", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun JSONObject.toConvertJob(): ConvertJob {
        val info = JSONObject(getString("videoInfo"))
        return ConvertJob(
            id = getString("id"),
            videoInfo = VideoInfo(
                uri = Uri.parse(info.getString("uri")),
                displayName = info.getString("displayName"),
                duration = info.optLong("duration"),
                width = info.optInt("width"),
                height = info.optInt("height"),
                size = info.optLong("size"),
                dateAdded = info.optLong("dateAdded"),
                rotation = info.optInt("rotation")
            ),
            outputDir = optString("outputDir").takeIf { it.isNotEmpty() }?.let { File(it) },
            outputTreeUri = optString("outputTreeUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
            degrees = optInt("degrees", 90),
            state = JobState.valueOf(getString("state")),
            progress = optDouble("progress", 0.0).toFloat(),
            outputPath = optString("outputPath").takeIf { it.isNotEmpty() },
            error = optString("error").takeIf { it.isNotEmpty() }
        )
    }

    private fun ConvertJob.toJson(): JSONObject {
        val info = JSONObject().apply {
            put("uri", videoInfo.uri.toString())
            put("displayName", videoInfo.displayName)
            put("duration", videoInfo.duration)
            put("width", videoInfo.width)
            put("height", videoInfo.height)
            put("size", videoInfo.size)
            put("dateAdded", videoInfo.dateAdded)
            put("rotation", videoInfo.rotation)
        }
        return JSONObject().apply {
            put("id", id)
            put("videoInfo", info)
            put("outputDir", outputDir?.absolutePath ?: "")
            put("outputTreeUri", outputTreeUri?.toString() ?: "")
            put("degrees", degrees)
            put("state", state.name)
            put("progress", progress.toDouble())
            put("outputPath", outputPath ?: "")
            put("error", error ?: "")
        }
    }

    // 监听 _jobs 变化并自动持久化（在 AndroidViewModel 内 init 用 viewModelScope 启动）
    init {
        viewModelScope.launch {
            _jobs.collect {
                if (it.isNotEmpty() || prefs.contains("jobs")) persistJobs()
            }
        }
    }
}