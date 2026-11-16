package com.videorotator.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videorotator.R
import com.videorotator.utils.AppPrefs
import com.videorotator.utils.JobErrors
import com.videorotator.utils.Notifier
import com.videorotator.utils.OutputTarget
import com.videorotator.utils.RotateOutput
import com.videorotator.service.ConvertService
import com.videorotator.utils.VideoInfo
import com.videorotator.utils.VideoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class JobState { QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }

data class ConvertJob(
    val id: String,
    val videoInfo: VideoInfo,
    val target: OutputTarget = OutputTarget.APP_PRIVATE,
    val outputTreeUri: Uri? = null,
    val degrees: Int = 90,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    /** 输出文件路径（展示 / 与视频列表匹配用；SAF 非本地存储时为 null） */
    val outputPath: String? = null,
    /** 输出文件 uri（播放 / 删除用） */
    val outputUri: Uri? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isFinished: Boolean get() = state == JobState.SUCCESS || state == JobState.FAILED || state == JobState.CANCELLED
    val isActive: Boolean get() = state == JobState.QUEUED || state == JobState.RUNNING
    val hasOutput: Boolean get() = state == JobState.SUCCESS && (outputUri != null || outputPath != null)
}

/** 「已转视频」列表项：转换产物 + 产生它的任务（私有目录里扫到的旧文件可能没有任务记录） */
data class ConvertedVideo(
    val video: VideoInfo,
    val job: ConvertJob?
)

class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val _jobs = MutableStateFlow<List<ConvertJob>>(emptyList())
    val jobs: StateFlow<List<ConvertJob>> = _jobs.asStateFlow()

    private val _selectedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedJobIds: StateFlow<Set<String>> = _selectedJobIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    private val _converted = MutableStateFlow<List<ConvertedVideo>>(emptyList())
    val converted: StateFlow<List<ConvertedVideo>> = _converted.asStateFlow()

    private val _convertedLoading = MutableStateFlow(false)
    val convertedLoading: StateFlow<Boolean> = _convertedLoading.asStateFlow()

    /** 产物文件增删的版本号：视频列表据此重扫（反向重转时任务数不变，不能只看成功数） */
    private val _outputRevision = MutableStateFlow(0)
    val outputRevision: StateFlow<Int> = _outputRevision.asStateFlow()

    private val coroutineJobs = mutableMapOf<String, Job>()

    /** 转换串行执行：之前批量转换会同时开 N 个 Muxer 抢 IO，「等待中」状态也形同虚设 */
    private val workerLock = Mutex()

    private val context: Context get() = getApplication()

    // 必须声明在 init 之前：属性按书写顺序初始化，init 中的收集会同步读取它
    private val prefs by lazy {
        context.getSharedPreferences("convert_jobs", Context.MODE_PRIVATE)
    }

    init {
        // 启动时从 SharedPreferences 恢复历史任务；进程被杀时仍在进行的任务标记为中断
        _jobs.value = loadPersistedJobs().map {
            if (it.isActive) it.copy(state = JobState.FAILED, error = JobErrors.INTERRUPTED) else it
        }
        // 持久化忽略进行中的进度：之前每 1% 进度都整表写一次 SharedPreferences
        viewModelScope.launch {
            _jobs
                .map { list -> list.map { if (it.isActive) it.copy(progress = 0f) else it } }
                .distinctUntilChanged()
                .collect { list ->
                    if (list.isNotEmpty() || prefs.contains("jobs")) {
                        withContext(Dispatchers.IO) { persistJobs(list) }
                    }
                }
        }
        // 有任务排队 / 进行中时启动前台服务，切到后台转换也不会被系统冻结
        viewModelScope.launch {
            _jobs
                .map { list -> list.count { it.isActive } }
                .distinctUntilChanged()
                .collect { active -> ConvertService.update(context, active) }
        }
    }

    /** 按「转换配置」中的输出位置派发任务 */
    fun startJobWithDefaults(videoInfo: VideoInfo, degrees: Int = AppPrefs.defaultDegrees(context)): String {
        val target = AppPrefs.getOutputTarget(context)
        val tree = if (target == OutputTarget.CUSTOM) AppPrefs.getOutputTreeUri(context) else null
        return startJob(videoInfo, if (target == OutputTarget.CUSTOM && tree == null) OutputTarget.MOVIES else target, tree, degrees)
    }

    fun startJob(
        videoInfo: VideoInfo,
        target: OutputTarget,
        outputTreeUri: Uri?,
        degrees: Int,
        replaces: ConvertJob? = null
    ): String {
        val job = ConvertJob(
            id = UUID.randomUUID().toString(),
            videoInfo = videoInfo,
            target = target,
            outputTreeUri = if (target == OutputTarget.CUSTOM) outputTreeUri else null,
            degrees = degrees
        )
        _jobs.update { it + job }
        runJob(job, replaces)
        return job.id
    }

    /**
     * 反向重转：用原视频按相反方向重新转换，成功后删除旧的输出文件和旧记录。
     * （之前直接删掉旧记录、旧文件却留在磁盘上；对「已转视频」点反向旋转则是在
     * 产物上再叠 270°，结果其实转回了原方向。）
     */
    fun reverseRotateJob(id: String) {
        val orig = _jobs.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val sourceExists = withContext(Dispatchers.IO) {
                VideoUtils.outputExists(context, orig.videoInfo.uri, orig.videoInfo.path)
            }
            if (!sourceExists && orig.hasOutput) {
                // 原视频已不存在（被删除，或本任务本身就是对产物做的反向）：在产物上再转 180°
                outputInfo(orig)?.let { out ->
                    startJob(out, orig.target, orig.outputTreeUri, 180, replaces = orig)
                }
                return@launch
            }
            val reverseDegrees = (360 - orig.degrees) % 360
            startJob(
                videoInfo = orig.videoInfo,
                target = orig.target,
                outputTreeUri = orig.outputTreeUri,
                degrees = if (reverseDegrees == 0) 180 else reverseDegrees,
                replaces = orig.takeIf { it.state == JobState.SUCCESS }
            )
            if (orig.state != JobState.SUCCESS) removeJob(id)
        }
    }

    /** 失败 / 取消的任务按原参数重新转换 */
    fun retryJob(id: String) {
        val orig = _jobs.value.firstOrNull { it.id == id } ?: return
        removeJob(id)
        startJob(orig.videoInfo, orig.target, orig.outputTreeUri, orig.degrees)
    }

    /**
     * 对没有任务记录的产物（私有目录中扫描到的旧文件）做反向：在产物上再转 180°，
     * 得到相反方向的视频，写回私有目录并删除旧文件。
     */
    fun reverseRotateFile(video: VideoInfo) {
        val fake = ConvertJob(
            id = "file:" + video.uri,
            videoInfo = video,
            state = JobState.SUCCESS,
            outputPath = video.path,
            outputUri = video.uri
        )
        startJob(video, OutputTarget.APP_PRIVATE, null, 180, replaces = fake)
    }

    private fun runJob(job: ConvertJob, replaces: ConvertJob?) {
        val coroutineJob = viewModelScope.launch {
            val self = coroutineContext.job
            try {
                workerLock.withLock {
                    updateJob(job.id) { it.copy(state = JobState.RUNNING, progress = 0f) }
                    // 写文件期间不可取消：取消通过 checkCancelled 在复制循环中检查并清理半成品。
                    // 否则在最后一次检查之后取消，已完整写出的文件会被丢弃结果、变成无主文件。
                    val result = withContext(Dispatchers.IO + NonCancellable) {
                        VideoUtils.rotateVideo(
                            context = context,
                            input = job.videoInfo,
                            degrees = job.degrees,
                            target = job.target,
                            outputTreeUri = job.outputTreeUri,
                            checkCancelled = { if (!self.isActive) throw CancellationException("cancelled") }
                        ) { progress ->
                            updateJob(job.id) { it.copy(progress = progress) }
                        }
                    }
                    withContext(NonCancellable) { handleResult(job, replaces, result) }
                }
            } catch (e: CancellationException) {
                // 取消：cancelJob 已经把状态置为 CANCELLED；半成品文件由 VideoUtils 清理
                updateJob(job.id) { if (it.state == JobState.CANCELLED) it else it.copy(state = JobState.CANCELLED) }
            } catch (e: Exception) {
                updateJob(job.id) {
                    it.copy(state = JobState.FAILED, error = e.message ?: JobErrors.FAILED)
                }
            }
            refreshConverted()
        }
        coroutineJobs[job.id] = coroutineJob
        coroutineJob.invokeOnCompletion { coroutineJobs.remove(job.id) }
    }

    private suspend fun handleResult(job: ConvertJob, replaces: ConvertJob?, result: Result<RotateOutput>) {
        result.fold(
            onSuccess = { out ->
                if (_jobs.value.none { it.id == job.id }) {
                    // 转换过程中任务记录已被删除：不留下无主文件
                    withContext(Dispatchers.IO) { VideoUtils.deleteOutput(context, out.uri, out.path) }
                    return
                }
                updateJob(job.id) {
                    it.copy(
                        state = JobState.SUCCESS, progress = 1f,
                        outputPath = out.path, outputUri = out.uri, error = null
                    )
                }
                if (replaces != null) {
                    val same = replaces.outputUri == out.uri ||
                        (replaces.outputPath != null && replaces.outputPath == out.path)
                    if (!same && replaces.hasOutput) {
                        withContext(Dispatchers.IO) {
                            VideoUtils.deleteOutput(context, replaces.outputUri, replaces.outputPath)
                        }
                    }
                    _jobs.update { list -> list.filter { it.id != replaces.id } }
                }
                Notifier.notifyResult(context, job.id, R.string.notif_done, job.videoInfo.displayName)
            },
            onFailure = { e ->
                updateJob(job.id) {
                    it.copy(state = JobState.FAILED, error = e.message ?: JobErrors.FAILED)
                }
                Notifier.notifyResult(context, job.id, R.string.notif_failed, job.videoInfo.displayName)
            }
        )
    }

    fun cancelJob(id: String) {
        updateJob(id) { if (it.isActive) it.copy(state = JobState.CANCELLED) else it }
        coroutineJobs[id]?.cancel()
    }

    fun removeJob(id: String) {
        coroutineJobs[id]?.cancel()
        _jobs.update { list -> list.filter { it.id != id } }
        _selectedJobIds.update { it - id }
    }

    /** 删除任务记录并删除已生成的输出文件 */
    fun removeJobAndFile(id: String) {
        removeJobsAndFiles(setOf(id))
    }

    /** 删除一组任务（多选）及其输出文件 */
    fun removeJobsAndFiles(ids: Set<String>) {
        val targets = _jobs.value.filter { it.id in ids }
        targets.forEach { coroutineJobs[it.id]?.cancel() }
        _jobs.update { list -> list.filter { it.id !in ids } }
        _selectedJobIds.value = emptySet()
        if (_jobs.value.isEmpty()) _isSelectMode.value = false
        viewModelScope.launch(Dispatchers.IO) {
            targets.filter { it.hasOutput }.forEach { VideoUtils.deleteOutput(context, it.outputUri, it.outputPath) }
            refreshConverted()
        }
    }

    /** 仅删文件，保留任务记录 */
    fun removeFileOnly(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        updateJob(id) {
            it.copy(outputPath = null, outputUri = null, state = JobState.FAILED, error = JobErrors.FILE_DELETED)
        }
        viewModelScope.launch(Dispatchers.IO) {
            VideoUtils.deleteOutput(context, job.outputUri, job.outputPath)
            refreshConverted()
        }
    }

    /** 删除「已转视频」中的文件；有对应任务时同步把任务标记为文件已删除 */
    fun deleteConverted(item: ConvertedVideo) {
        item.job?.let { job ->
            updateJob(job.id) {
                it.copy(outputPath = null, outputUri = null, state = JobState.FAILED, error = JobErrors.FILE_DELETED)
            }
        }
        _converted.update { list -> list.filter { it.video.uri != item.video.uri } }
        viewModelScope.launch(Dispatchers.IO) {
            VideoUtils.deleteOutput(context, item.video.uri, item.video.path)
            refreshConverted()
        }
    }

    fun reverseConverted(item: ConvertedVideo) {
        if (item.job != null) reverseRotateJob(item.job.id) else reverseRotateFile(item.video)
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.isActive } }
        _selectedJobIds.value = emptySet()
        _isSelectMode.value = false
    }

    /**
     * 汇总「已转视频」：成功任务的输出（任意保存位置）+ 私有目录中扫描到的产物，
     * 过滤掉已被删除的文件。之前只扫描私有目录，保存到 Movies / DCIM / 自定义路径的产物都看不到。
     */
    fun refreshConverted() {
        viewModelScope.launch {
            _convertedLoading.value = true
            val jobsSnapshot = _jobs.value
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<ConvertedVideo>()
                val seenPaths = mutableSetOf<String>()
                val seenUris = mutableSetOf<String>()
                jobsSnapshot.filter { it.hasOutput }.forEach { job ->
                    if (!VideoUtils.outputExists(context, job.outputUri, job.outputPath)) return@forEach
                    val info = outputInfo(job) ?: return@forEach
                    val uri = info.uri
                    result.add(ConvertedVideo(info, job))
                    job.outputPath?.let { seenPaths.add(it) }
                    seenUris.add(uri.toString())
                }
                VideoUtils.scanConvertedDir(context).forEach { v ->
                    if (v.path !in seenPaths && v.uri.toString() !in seenUris) result.add(ConvertedVideo(v, null))
                }
                result
            }
            _converted.value = list
            _convertedLoading.value = false
            _outputRevision.update { it + 1 }
        }
    }

    /**
     * 由任务推算产物的 VideoInfo：宽高不变、旋转角 + degrees，无需再解析文件。
     */
    fun outputInfo(job: ConvertJob): VideoInfo? {
        if (!job.hasOutput) return null
        val uri = job.outputUri ?: Uri.fromFile(java.io.File(job.outputPath!!))
        val name = job.outputPath?.substringAfterLast('/')
            ?: VideoUtils.outputNameFor(job.videoInfo.displayName.substringBeforeLast('.'), job.degrees)
        val src = job.videoInfo
        val file = job.outputPath?.let { java.io.File(it) }?.takeIf { it.exists() }
        return VideoInfo(
            uri = uri,
            displayName = name,
            duration = src.duration,
            width = src.width,
            height = src.height,
            size = file?.length()?.takeIf { it > 0 } ?: src.size,
            dateAdded = file?.lastModified()?.takeIf { it > 0 }?.div(1000) ?: (job.createdAt / 1000),
            rotation = (src.rotation.coerceAtLeast(0) + job.degrees) % 360,
            path = job.outputPath
        )
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
        _selectedJobIds.value = _jobs.value.map { it.id }.toSet()
    }

    fun deselectAllJobs() {
        _selectedJobIds.value = emptySet()
    }

    private fun updateJob(id: String, fn: (ConvertJob) -> ConvertJob) {
        _jobs.update { list -> list.map { if (it.id == id) fn(it) else it } }
    }

    // ---- 持久化 ----


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

    private fun persistJobs(jobs: List<ConvertJob>) {
        try {
            val arr = JSONArray()
            jobs.forEach { arr.put(it.toJson()) }
            prefs.edit().putString("jobs", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun JSONObject.toConvertJob(): ConvertJob {
        val info = optJSONObject("videoInfo") ?: JSONObject(getString("videoInfo"))
        val outputPath = optString("outputPath").takeIf { it.isNotEmpty() }
        val outputUri = optString("outputUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            // 兼容旧版本：SAF 输出时 outputPath 存的是 content:// 字符串
            ?: outputPath?.let { if (it.startsWith("content://")) Uri.parse(it) else Uri.fromFile(java.io.File(it)) }
        val treeUri = optString("outputTreeUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
        val target = optString("target").takeIf { it.isNotEmpty() }
            ?.let { runCatching { OutputTarget.valueOf(it) }.getOrNull() }
            ?: legacyTarget(optString("outputDir"), treeUri)
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
                rotation = info.optInt("rotation"),
                path = info.optString("path").takeIf { it.isNotEmpty() }
            ),
            target = target,
            outputTreeUri = treeUri,
            degrees = optInt("degrees", 90),
            state = JobState.valueOf(getString("state")),
            progress = optDouble("progress", 0.0).toFloat(),
            outputPath = outputPath?.takeIf { !it.startsWith("content://") },
            outputUri = outputUri,
            error = optString("error").takeIf { it.isNotEmpty() },
            createdAt = optLong("createdAt", 0L)
        )
    }

    /** 旧版本只存了 outputDir 路径，按路径推断保存位置 */
    private fun legacyTarget(outputDir: String, treeUri: Uri?): OutputTarget {
        if (treeUri != null) return OutputTarget.CUSTOM
        if (outputDir.isEmpty()) return OutputTarget.APP_PRIVATE
        return OutputTarget.values().firstOrNull { t ->
            t.publicDir != null && outputDir.trimEnd('/') ==
                android.os.Environment.getExternalStoragePublicDirectory(t.publicDir).absolutePath
        } ?: OutputTarget.APP_PRIVATE
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
            put("path", videoInfo.path ?: "")
        }
        return JSONObject().apply {
            put("id", id)
            put("videoInfo", info)
            put("target", target.name)
            put("outputTreeUri", outputTreeUri?.toString() ?: "")
            put("degrees", degrees)
            put("state", state.name)
            put("progress", progress.toDouble())
            put("outputPath", outputPath ?: "")
            put("outputUri", outputUri?.toString() ?: "")
            put("error", error ?: "")
            put("createdAt", createdAt)
        }
    }
}
