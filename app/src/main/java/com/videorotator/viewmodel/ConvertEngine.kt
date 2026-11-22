package com.videorotator.viewmodel

import com.videorotator.utils.PermissionUtils
import kotlinx.coroutines.ensureActive
import android.provider.MediaStore
import android.os.Build
import android.content.IntentSender
import android.app.RecoverableSecurityException
import com.videorotator.utils.SourceLink
import com.videorotator.utils.OutputLinks
import com.videorotator.utils.FileRef
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import android.util.Log
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** 队列跑空时的汇总：前台用 Snackbar 提示（后台由通知提示） */
data class QueueSummary(val succeeded: List<ConvertJob>, val failed: List<ConvertJob>)

/** 「已转视频」列表项：转换产物 + 产生它的任务（私有目录里扫到的旧文件可能没有任务记录） */
data class ConvertedVideo(
    val video: VideoInfo,
    val job: ConvertJob?,
    /** 仍在设备上的原视频（可删除以释放空间）；已删除 / 未知为 null */
    val original: FileRef? = null
)

/** 批量删除的结果 */
data class FilesDeleted(val kind: ConvertEngine.DeleteKind, val count: Int, val freedBytes: Long, val skipped: Int)

/**
 * 转换引擎：进程级单例，任务队列、产物列表都在这里。
 * 之前放在 ViewModel 里，Activity 被销毁（返回键退出、被系统回收界面）时任务随之取消；
 * 现在只要进程在（转换期间由前台服务保活），切到后台、关掉界面任务都会继续。
 */
class ConvertEngine private constructor(private val app: Context) {

    companion object {
        private const val TAG = "ConvertEngine"

        @Volatile
        private var instance: ConvertEngine? = null

        fun get(context: Context): ConvertEngine =
            instance ?: synchronized(this) {
                instance ?: ConvertEngine(context.applicationContext).also { instance = it }
            }

        /** 同时进行的转换数：转换只是复制数据，2 路并行即可占满存储带宽，再多反而互相抢 IO */
        private const val MAX_PARALLEL = 2
        private const val KEY_PENDING_OUTPUTS = "pending_outputs"
        private const val MAX_FINISHED_HISTORY = 200
    }

    /** 引擎自己的协程作用域：不随任何界面销毁 */
    // 后台任务里未预料到的异常只记录日志：引擎是进程级的，任何一个协程抛出异常都会让整个应用闪退
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "background task failed", e) }
    )


    private val _jobs = MutableStateFlow<List<ConvertJob>>(emptyList())
    val jobs: StateFlow<List<ConvertJob>> = _jobs.asStateFlow()

    // 以下派生流只在内容变化时发射：转换进度每 1% 更新一次 jobs，
    // 视频列表标识、底栏角标若直接依赖 jobs 会跟着整表重算
    /** 有产物的成功任务 */
    val successJobs: StateFlow<List<ConvertJob>> = _jobs
        .map { list -> list.filter { it.hasOutput } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 等待 / 进行中的任务数 */
    val activeCount: StateFlow<Int> = _jobs
        .map { list -> list.count { it.isActive } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** 等待 / 进行中任务的源视频 uri（视频列表标「转换中」，批量转换跳过） */
    val activeSourceUris: StateFlow<Set<String>> = _jobs
        .map { list -> list.filter { it.isActive }.map { it.videoInfo.uri.toString() }.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _queueDone = MutableSharedFlow<QueueSummary>(extraBufferCapacity = 4)
    val queueDone: SharedFlow<QueueSummary> = _queueDone.asSharedFlow()

    /** 本轮队列（从有任务到跑空）中结束的任务 id */
    private val finishedThisRound = mutableListOf<String>()

    private val _converted = MutableStateFlow<List<ConvertedVideo>>(emptyList())
    val converted: StateFlow<List<ConvertedVideo>> = _converted.asStateFlow()

    private val _convertedLoading = MutableStateFlow(false)
    val convertedLoading: StateFlow<Boolean> = _convertedLoading.asStateFlow()

    /** 产物文件增删的版本号：视频列表据此重扫（反向重转时任务数不变，不能只看成功数） */
    private val _outputRevision = MutableStateFlow(0)
    val outputRevision: StateFlow<Int> = _outputRevision.asStateFlow()

    private val coroutineJobs = mutableMapOf<String, Job>()

    /** 限制并行数：批量转换时其余任务保持「等待中」 */
    private val workers = Semaphore(MAX_PARALLEL)

    private val context: Context get() = app

    // 必须声明在 init 之前：属性按书写顺序初始化，init 中的收集会同步读取它
    private val prefs by lazy {
        context.getSharedPreferences("convert_jobs", Context.MODE_PRIVATE)
    }

    /** 产物 → 原视频 对应表（独立于任务记录，清空任务后仍有效） */
    val outputLinks = OutputLinks(app)
    val links: StateFlow<List<SourceLink>> get() = outputLinks.links

    init {
        scope.launch {
            // 启动时在 IO 线程恢复历史任务；进程被杀时仍在进行的任务标记为中断，并清理其半成品文件
            val restored = withContext(Dispatchers.IO) {
                cleanupInterruptedOutput()
                loadPersistedJobs().map {
                    if (it.isActive) it.copy(state = JobState.FAILED, error = JobErrors.INTERRUPTED) else it
                }
            }
            // 恢复期间可能已有新任务加入
            _jobs.update { current -> restored.filter { r -> current.none { it.id == r.id } } + current }
            startPersisting()
            // 旧版本只有任务记录：补建对应关系；再清掉产物已不存在的记录
            restored.filter { it.hasOutput }.forEach { job ->
                if (outputLinks.linkOf(job.outputUri, job.outputPath) == null) recordLink(job, job.outputUri!!, job.outputPath)
            }
            withContext(Dispatchers.IO) { outputLinks.prune(context) }
        }
        // 有任务排队 / 进行中时启动前台服务，切到后台转换也不会被系统冻结
        scope.launch {
            activeCount.collect { active -> ConvertService.update(context, active) }
        }
    }

    /** 持久化忽略进行中的进度：之前每 1% 进度都整表写一次 SharedPreferences */
    private fun startPersisting() {
        scope.launch {
            _jobs
                .map { list -> list.map { if (it.isActive) it.copy(progress = 0f) else it } }
                .distinctUntilChanged()
                .collect { list ->
                    if (list.isNotEmpty() || prefs.contains("jobs")) {
                        withContext(Dispatchers.IO) { persistJobs(list) }
                    }
                }
        }
    }

    /** 删除上次进程被杀时正在写的输出（MediaStore 待定条目 / SAF 文档 / .part 文件） */
    private fun cleanupInterruptedOutput() {
        prefs.getStringSet(KEY_PENDING_OUTPUTS, null)?.toList()?.forEach { raw ->
            VideoUtils.deleteOutput(context, Uri.parse(raw), null)
        }
        prefs.edit().remove(KEY_PENDING_OUTPUTS).commit()
        VideoUtils.cleanupLeftovers(context)
    }

    /** 正在写入的输出（多个任务并行，同步写入：进程在转换中被杀，下次启动据此删除半成品） */
    private val pendingLock = Any()

    private fun markPending(uri: String, add: Boolean) = synchronized(pendingLock) {
        val set = prefs.getStringSet(KEY_PENDING_OUTPUTS, emptySet())!!.toMutableSet()
        if (add) set.add(uri) else set.remove(uri)
        prefs.edit().putStringSet(KEY_PENDING_OUTPUTS, set).commit()
    }

    /** 按「转换配置」中的输出位置派发任务；同一任务已在队列中时返回 null */
    fun startJobWithDefaults(videoInfo: VideoInfo, degrees: Int = AppPrefs.defaultDegrees(context)): String? {
        val target = AppPrefs.getOutputTarget(context)
        val tree = if (target == OutputTarget.CUSTOM) AppPrefs.getOutputTreeUri(context) else null
        return startJob(videoInfo, if (target == OutputTarget.CUSTOM && tree == null) OutputTarget.MOVIES else target, tree, degrees)
    }

    /**
     * 派发转换任务。
     *  - 同一视频、同方向、同保存位置的任务已在排队 / 转换中：不重复加入，返回 null（防连点、重复批量）
     *  - 之前已有相同参数的成功产物：新产物替换它（同名文件覆盖、旧记录移除），不留失效记录
     */
    fun startJob(
        videoInfo: VideoInfo,
        target: OutputTarget,
        outputTreeUri: Uri?,
        degrees: Int,
        replaces: ConvertJob? = null
    ): String? {
        val tree = if (target == OutputTarget.CUSTOM) outputTreeUri else null
        val source = videoInfo.uri
        fun ConvertJob.sameTask() =
            this.videoInfo.uri == source && this.degrees == degrees && this.target == target && this.outputTreeUri == tree
        if (_jobs.value.any { it.isActive && it.sameTask() }) return null
        val job = ConvertJob(
            id = UUID.randomUUID().toString(),
            videoInfo = videoInfo,
            target = target,
            outputTreeUri = tree,
            degrees = degrees
        )
        val previous = replaces ?: _jobs.value.lastOrNull { it.hasOutput && it.sameTask() }
        _jobs.update { it + job }
        runJob(job, previous)
        return job.id
    }

    /** 该视频按此方向、保存位置是否已有转换产物（转换页提示「将替换」） */
    fun hasSameOutput(videoInfo: VideoInfo, target: OutputTarget, outputTreeUri: Uri?, degrees: Int): Boolean {
        val tree = if (target == OutputTarget.CUSTOM) outputTreeUri else null
        return successJobs.value.any {
            it.videoInfo.uri == videoInfo.uri && it.degrees == degrees && it.target == target && it.outputTreeUri == tree
        }
    }

    /**
     * 反向重转：用原视频按相反方向重新转换，成功后删除旧的输出文件和旧记录。
     * （之前直接删掉旧记录、旧文件却留在磁盘上；对「已转视频」点反向旋转则是在
     * 产物上再叠 270°，结果其实转回了原方向。）
     */
    fun reverseRotateJob(id: String) {
        val orig = _jobs.value.firstOrNull { it.id == id } ?: return
        scope.launch {
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
        // 写回产物原来所在的位置（之前一律写到应用私有目录：保存在 Movies 的产物反向后
        // 会被挪进私有目录，卸载应用时一并删除）
        val link = outputLinks.linkOf(video.uri, video.path)
        val target = link?.target?.let { runCatching { OutputTarget.valueOf(it) }.getOrNull() }
            ?: inferTarget(video.path)
        val tree = link?.treeUri?.let { Uri.parse(it) }
            ?: if (target == OutputTarget.CUSTOM) AppPrefs.getOutputTreeUri(context) else null
        val finalTarget = if (target == OutputTarget.CUSTOM && tree == null) OutputTarget.MOVIES else target
        startJob(video, finalTarget, tree, 180, replaces = fake)
    }

    /** 按文件路径推断保存位置（旧的对应记录没有保存位置） */
    private fun inferTarget(path: String?): OutputTarget {
        if (path == null) return OutputTarget.MOVIES
        if (path.startsWith(VideoUtils.privateOutputDir(context).absolutePath)) return OutputTarget.APP_PRIVATE
        val parent = java.io.File(path).parent?.trimEnd('/')
        return OutputTarget.values().firstOrNull { t ->
            t.publicDir != null && parent ==
                android.os.Environment.getExternalStoragePublicDirectory(t.publicDir).absolutePath
        } ?: OutputTarget.MOVIES
    }

    private fun runJob(job: ConvertJob, replaces: ConvertJob?) {
        val coroutineJob = scope.launch {
            val self = coroutineContext.job
            try {
                workers.withPermit {
                    updateJob(job.id) { it.copy(state = JobState.RUNNING, progress = 0f) }
                    // 写文件与登记结果整体不可取消：取消通过 checkCancelled 在复制循环中检查并清理半成品。
                    // 在最后一次检查之后才取消时，文件已完整写出 —— 照常登记为成功，不留下无主文件。
                    withContext(NonCancellable) {
                        val result = withContext(Dispatchers.IO) {
                            var pending: String? = null
                            try {
                                VideoUtils.rotateVideo(
                                    context = context,
                                    input = job.videoInfo,
                                    degrees = job.degrees,
                                    target = job.target,
                                    outputTreeUri = job.outputTreeUri,
                                    checkCancelled = { if (!self.isActive) throw CancellationException("cancelled") },
                                    onOutputCreated = { uri -> pending = uri.toString().also { markPending(it, true) } },
                                    canReplace = { uri, path -> canReplaceOutput(job, replaces, uri, path) }
                                ) { progress ->
                                    updateJob(job.id) { it.copy(progress = progress) }
                                }
                            } finally {
                                pending?.let { markPending(it, false) }
                            }
                        }
                        handleResult(job, replaces, result)
                    }
                }
            } catch (e: CancellationException) {
                // 取消：cancelJob 已经把状态置为 CANCELLED；半成品文件由 VideoUtils 清理。
                // 已登记成功（取消晚于写完）的任务保持成功
                updateJob(job.id) { if (it.isActive) it.copy(state = JobState.CANCELLED) else it }
            } catch (e: Exception) {
                updateJob(job.id) {
                    it.copy(state = JobState.FAILED, error = e.message ?: JobErrors.FAILED)
                }
            }
            onJobFinished(job.id)
        }
        coroutineJobs[job.id] = coroutineJob
        coroutineJob.invokeOnCompletion { coroutineJobs.remove(job.id) }
    }

    /** 队列跑空时汇总本轮结果（取消的不计） */
    private fun onJobFinished(id: String) {
        finishedThisRound.add(id)
        val list = _jobs.value
        if (list.any { it.isActive }) return
        val finished = finishedThisRound.mapNotNull { fid -> list.firstOrNull { it.id == fid } }
        finishedThisRound.clear()
        val summary = QueueSummary(
            succeeded = finished.filter { it.state == JobState.SUCCESS },
            failed = finished.filter { it.state == JobState.FAILED }
        )
        if (summary.succeeded.isEmpty() && summary.failed.isEmpty()) return
        _queueDone.tryEmit(summary)
        // 应用在后台时：整批结果一条通知（之前每个任务一条，批量转换会刷屏）
        Notifier.notifyQueueDone(context, summary.succeeded.map { it.videoInfo.displayName }, summary.failed.map { it.videoInfo.displayName })
    }

    /**
     * 记录产物对应的原视频。对产物再转换（反向旋转等）时，源本身是产物：
     * 沿对应关系追溯到真正的原视频，角度为相对原视频的净角度。
     */
    private fun recordLink(job: ConvertJob, outUri: Uri, outPath: String?) {
        val src = job.videoInfo
        val parent = outputLinks.linkOf(src.uri, src.path)
        outputLinks.put(
            SourceLink(
                outputUri = outUri.toString(),
                outputPath = outPath,
                source = rootSourceOf(src),
                sourceDuration = src.duration,
                degrees = ((parent?.degrees ?: 0) + job.degrees) % 360,
                createdAt = job.createdAt,
                target = job.target.name,
                treeUri = job.outputTreeUri?.toString(),
                // 对产物再转换时继承：原视频已删除的仍视为已删除
                sourceMissing = parent?.sourceMissing ?: false
            )
        )
    }

    /** 追溯到真正的原视频（源本身是产物时沿对应关系向上） */
    private fun rootSourceOf(src: VideoInfo): FileRef =
        outputLinks.linkOf(src.uri, src.path)?.source ?: FileRef(src.uri, src.path, src.displayName, src.size)

    /**
     * 目标位置的同名文件能否被本任务替换：只有它是同一原视频的旧产物（或本次明确要替换的产物）时才可以。
     * 不同视频恰好同名（如 DCIM/a.mp4 与 Download/a.mp4）时保留旧文件，新产物另起名。
     */
    private fun canReplaceOutput(job: ConvertJob, replaces: ConvertJob?, uri: Uri, path: String?): Boolean {
        if (replaces != null && (replaces.outputUri == uri || (path != null && replaces.outputPath == path))) return true
        val existing = outputLinks.linkOf(uri, path) ?: return false
        return existing.source.uri == rootSourceOf(job.videoInfo).uri
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
                    // 包括取消晚于写完的任务：文件已完整写出，按成功登记
                    it.copy(
                        state = JobState.SUCCESS, progress = 1f,
                        outputPath = out.path, outputUri = out.uri, error = null
                    )
                }
                recordLink(job, out.uri, out.path)
                if (replaces != null) {
                    val same = replaces.outputUri == out.uri ||
                        (replaces.outputPath != null && replaces.outputPath == out.path)
                    if (!same && replaces.hasOutput) {
                        withContext(Dispatchers.IO) {
                            VideoUtils.deleteOutput(context, replaces.outputUri, replaces.outputPath)
                        }
                        outputLinks.removeOutput(replaces.outputUri, replaces.outputPath)
                    }
                    _jobs.update { list -> list.filter { it.id != replaces.id } }
                }
                _outputRevision.update { it + 1 }
            },
            onFailure = { e ->
                updateJob(job.id) {
                    it.copy(state = JobState.FAILED, error = e.message ?: JobErrors.FAILED)
                }
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
        val withOutput = targets.filter { it.hasOutput }
        if (withOutput.isEmpty()) return
        withOutput.forEach { outputLinks.removeOutput(it.outputUri, it.outputPath) }
        scope.launch {
            withContext(Dispatchers.IO) {
                withOutput.forEach { VideoUtils.deleteOutput(context, it.outputUri, it.outputPath) }
            }
            onOutputsDeleted()
        }
    }

    /** 仅删文件，保留任务记录 */
    fun removeFileOnly(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        updateJob(id) {
            it.copy(outputPath = null, outputUri = null, state = JobState.FAILED, error = JobErrors.FILE_DELETED)
        }
        outputLinks.removeOutput(job.outputUri, job.outputPath)
        scope.launch {
            withContext(Dispatchers.IO) { VideoUtils.deleteOutput(context, job.outputUri, job.outputPath) }
            onOutputsDeleted()
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
        outputLinks.removeOutput(item.video.uri, item.video.path)
        scope.launch {
            withContext(Dispatchers.IO) { VideoUtils.deleteOutput(context, item.video.uri, item.video.path) }
            onOutputsDeleted()
        }
    }

    /** 产物文件被删除后：视频列表重扫、「已转视频」刷新 */
    private fun onOutputsDeleted() {
        _outputRevision.update { it + 1 }
        refreshConverted()
    }

    fun reverseConverted(item: ConvertedVideo) {
        if (item.job != null) reverseRotateJob(item.job.id) else reverseRotateFile(item.video)
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.isActive } }
    }

    // ---- 删除原视频 / 产物（释放空间） ----

    /** 一个可删除的原视频，及它已转换出的产物（删除前确认至少一个产物仍在） */
    data class DeleteCandidate(val original: FileRef, val outputs: List<FileRef>)

    enum class DeleteKind { ORIGINALS, OUTPUTS }

    private class PendingConsent(val refs: List<FileRef>, val sender: IntentSender, val retryAfterGrant: Boolean)

    private val consentQueue = ArrayDeque<PendingConsent>()
    private var deleteKind = DeleteKind.ORIGINALS
    private val deletedRefs = mutableListOf<FileRef>()
    private var deleteSkipped = 0

    /** 正在删除（检查文件 / 等待系统确认）：界面据此禁用删除按钮，避免重复提交 */
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /** 需要用户在系统对话框中确认删除的请求（界面负责弹出） */
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent: StateFlow<IntentSender?> = _deleteConsent.asStateFlow()
    private var launchedConsent: IntentSender? = null

    /** 界面弹出确认框前调用：同一请求只弹一次（Activity 重建后不会重复弹出） */
    fun claimDeleteConsent(sender: IntentSender): Boolean {
        if (launchedConsent === sender) return false
        launchedConsent = sender
        return true
    }

    private val _filesDeleted = MutableSharedFlow<FilesDeleted>(extraBufferCapacity = 4)
    val filesDeleted: SharedFlow<FilesDeleted> = _filesDeleted.asSharedFlow()

    /**
     * 删除已转换视频的原视频。只删除「至少有一个产物仍在设备上」的原视频，避免误删唯一的副本。
     */
    fun deleteOriginals(candidates: List<DeleteCandidate>) {
        if (_deleting.value || candidates.isEmpty()) return
        _deleting.value = true
        scope.launch {
            val checked = runCatching {
                withContext(Dispatchers.IO) {
                    // 记录里的媒体库 uri 可能已失效：先按路径换成当前的；同一文件只删一次
                    candidates
                        .map { it.copy(original = currentRef(it.original)) }
                        .groupBy { it.original.fileKey }
                        .map { (_, same) -> DeleteCandidate(same[0].original, same.flatMap { it.outputs }) }
                        .partition { c ->
                            val o = c.original
                            c.outputs.any { out ->
                                out.uri != o.uri && out.path != o.path && VideoUtils.outputExists(context, out.uri, out.path)
                            } && VideoUtils.outputExists(context, o.uri, o.path)
                        }
                }
            }.getOrNull()
            if (checked == null) {
                _deleting.value = false
                _filesDeleted.tryEmit(FilesDeleted(DeleteKind.ORIGINALS, 0, 0, candidates.size))
                return@launch
            }
            startDelete(DeleteKind.ORIGINALS, checked.first.map { it.original }, checked.second.size, locked = true)
        }
    }

    /**
     * 原视频的当前引用（需在 IO 线程调用）：文件被重新扫描 / 移走又移回后媒体库 _ID 会变，
     * 按路径查出新的 uri（同时更新对应表）；SAF 浏览到的共享存储文件也换成媒体库 uri，删除时可走系统确认。
     */
    private fun currentRef(ref: FileRef): FileRef {
        if (ref.uri.scheme == "file") return ref
        val path = ref.path?.takeIf { it.startsWith("/") && java.io.File(it).exists() } ?: return ref
        val fresh = VideoUtils.findVideoUriByPath(context, path) ?: return ref
        if (fresh == ref.uri) return ref
        outputLinks.updateSourceUri(path, fresh)
        return ref.copy(uri = fresh)
    }

    /** 批量删除「已转视频」中的产物文件（之前版本 / 重装前生成的文件同样需要系统确认） */
    fun deleteOutputs(items: List<ConvertedVideo>) {
        if (_deleting.value || items.isEmpty()) return
        val refs = items.map { FileRef(it.video.uri, it.video.path, it.video.displayName, it.video.size) }
        refs.forEach { evictThumbnailCache(it.uri) }
        startDelete(DeleteKind.OUTPUTS, refs.distinctBy { it.uri }, 0)
    }

    /** [locked]：调用方已置上 deleting（删除原视频先检查文件，再进入这里） */
    private fun startDelete(kind: DeleteKind, refs: List<FileRef>, skippedBefore: Int, locked: Boolean = false) {
        if (_deleting.value && !locked) return
        _deleting.value = true
        deleteKind = kind
        deletedRefs.clear()
        consentQueue.clear()
        deleteSkipped = skippedBefore
        scope.launch {
            try {
                val needConsent = mutableListOf<FileRef>()
                withContext(Dispatchers.IO) {
                    refs.forEach { ref ->
                        try {
                            if (deleteFile(ref)) synchronized(deletedRefs) { deletedRefs.add(ref) } else deleteSkipped++
                        } catch (e: SecurityException) {
                            val recoverable = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) e as? RecoverableSecurityException else null
                            when {
                                recoverable != null ->
                                    consentQueue.add(PendingConsent(listOf(ref), recoverable.userAction.actionIntent.intentSender, true))
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && VideoUtils.isMediaItemUri(ref.uri) ->
                                    needConsent.add(ref)
                                else -> deleteSkipped++
                            }
                        } catch (e: Exception) {
                            // 其他异常（文件所在存储已移除、提供方报错等）只跳过这一个，不能让整批删除连同应用一起崩溃
                            deleteSkipped++
                        }
                    }
                }
                if (needConsent.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+：一次系统确认即可删除全部
                    val request = withContext(Dispatchers.IO) { createDeleteRequest(needConsent) }
                    deleteSkipped += needConsent.size - (request?.first?.size ?: 0)
                    request?.let { (accepted, sender) -> consentQueue.addFirst(PendingConsent(accepted, sender, false)) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "delete failed", e)
            }
            nextConsentOrFinish()
        }
    }

    /**
     * 为一批媒体库文件创建系统删除确认。其中任何一个 uri 不被系统接受（已失效、不是具体条目等），
     * createDeleteRequest 都会让整批失败并抛异常 —— 之前这里没有捕获，「全部删除」时只要混进一个
     * 这样的文件应用就会闪退（逐个删除时只有那一个会失败）。现在整批失败时逐个检验，剔除不被接受的再重试。
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun createDeleteRequest(refs: List<FileRef>): Pair<List<FileRef>, IntentSender>? {
        fun request(list: List<FileRef>) = runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, list.map { it.uri }).intentSender
        }.onFailure { Log.w(TAG, "createDeleteRequest rejected ${list.size} item(s)", it) }.getOrNull()

        request(refs)?.let { return refs to it }
        if (refs.size == 1) return null
        val accepted = refs.filter { request(listOf(it)) != null }
        if (accepted.isEmpty()) return null
        return request(accepted)?.let { accepted to it }
    }

    /** 系统删除确认对话框的结果 */
    fun onDeleteConsentResult(granted: Boolean) {
        val pending = consentQueue.removeFirstOrNull() ?: return
        _deleteConsent.value = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pending.refs.forEach { ref ->
                        val gone = when {
                            !granted -> false
                            pending.retryAfterGrant -> runCatching { deleteFile(ref) }.getOrDefault(false)
                            // Android 11+ 由系统完成删除
                            else -> !VideoUtils.outputExists(context, ref.uri, ref.path)
                        }
                        if (gone) synchronized(deletedRefs) { deletedRefs.add(ref) } else deleteSkipped++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "delete consent handling failed", e)
            }
            nextConsentOrFinish()
        }
    }

    /** 系统确认框无法弹出（个别系统上找不到对应界面等）：按用户拒绝处理，继续后面的流程 */
    fun onDeleteConsentFailed() = onDeleteConsentResult(false)

    private fun nextConsentOrFinish() {
        val next = consentQueue.firstOrNull()
        if (next != null) {
            _deleteConsent.value = next.sender
            return
        }
        val deleted = synchronized(deletedRefs) { deletedRefs.toList().also { deletedRefs.clear() } }
        try {
            when (deleteKind) {
                DeleteKind.ORIGINALS -> outputLinks.markSourcesMissing(deleted)
                DeleteKind.OUTPUTS -> deleted.forEach { ref ->
                    outputLinks.removeOutput(ref.uri, ref.path)
                    _jobs.update { list ->
                        list.map { j ->
                            if (j.hasOutput && (j.outputUri == ref.uri || (ref.path != null && j.outputPath == ref.path))) {
                                j.copy(outputPath = null, outputUri = null, state = JobState.FAILED, error = JobErrors.FILE_DELETED)
                            } else j
                        }
                    }
                }
            }
            if (deleteKind == DeleteKind.OUTPUTS) {
                val gone = deleted.map { it.uri }.toSet()
                _converted.update { list -> list.filter { it.video.uri !in gone } }
            } else {
                // 原视频删除后立即把对应卡片标为「源文件已删除」，不等重新扫描
                val keys = deleted.map { it.fileKey }.toSet()
                _converted.update { list ->
                    list.map { if (it.original != null && it.original.fileKey in keys) it.copy(original = null) else it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "delete bookkeeping failed", e)
        }
        _deleting.value = false
        _filesDeleted.tryEmit(FilesDeleted(deleteKind, deleted.size, deleted.sumOf { it.size }, deleteSkipped))
        if (deleted.isNotEmpty()) onOutputsDeleted()
    }

    /** 删除一个文件；无权限时抛出 SecurityException（交由系统确认） */
    private fun deleteFile(ref: FileRef): Boolean {
        val uri = ref.uri
        return when {
            uri.scheme == "file" -> VideoUtils.deleteOutput(context, uri, ref.path)
            uri.authority == MediaStore.AUTHORITY -> {
                val ok = context.contentResolver.delete(uri, null, null) > 0
                ok || (ref.path != null && !java.io.File(ref.path).exists())
            }
            else -> VideoUtils.deleteOutput(context, uri, ref.path)
        }
    }

    private fun evictThumbnailCache(uri: Uri) = com.videorotator.ui.components.evictThumbnail(uri)

    private var refreshJob: Job? = null

    /** 本次运行中已确认找不到原视频的旧产物，避免每次刷新重复查询 */
    private val orphanOutputs = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * 给没有对应记录的产物找回原视频并补建对应关系（需在 IO 线程调用）：
     * 文件名 <原名>_rotated_<角度>deg.mp4 → 媒体库中名为 <原名>.* 、时长一致的视频。
     * 有多个候选（同名拷贝）时取同目录的，否则取第一个。
     */
    private fun adoptLegacyOutputs(outputs: List<VideoInfo>) {
        outputs.forEach { out ->
            val key = out.uri.toString()
            if (key in orphanOutputs || outputLinks.linkOf(out.uri, out.path) != null) return@forEach
            val base = VideoUtils.sourceBaseNameOf(out.displayName) ?: return@forEach
            val candidates = runCatching { VideoUtils.findVideosByBaseName(context, base) }.getOrDefault(emptyList())
                .filter { c ->
                    c.uri != out.uri && VideoUtils.sourceBaseNameOf(c.displayName) == null &&
                        c.duration > 0 && out.duration > 0 && kotlin.math.abs(c.duration - out.duration) <= 1500
                }
            val outDir = out.path?.substringBeforeLast('/')
            val src = candidates.firstOrNull { it.path?.substringBeforeLast('/') == outDir } ?: candidates.firstOrNull()
            if (src == null) {
                orphanOutputs.add(key)
                return@forEach
            }
            val degrees = Regex("""_rotated_(\d+)deg""").find(out.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: 90
            outputLinks.put(
                SourceLink(
                    outputUri = key,
                    outputPath = out.path,
                    source = FileRef(src.uri, src.path, src.displayName, src.size),
                    sourceDuration = src.duration,
                    degrees = degrees,
                    createdAt = out.dateAdded * 1000,
                    target = inferTarget(out.path).name
                )
            )
        }
    }

    /**
     * 汇总「已转视频」：成功任务的输出（任意保存位置）+ 私有目录中扫描到的产物，
     * 过滤掉已被删除的文件。之前只扫描私有目录，保存到 Movies / DCIM / 自定义路径的产物都看不到。
     */
    fun refreshConverted() {
        // 新的刷新取代进行中的旧刷新，避免旧结果后到覆盖新列表
        refreshJob?.cancel()
        refreshJob = scope.launch {
            // 合并短时间内的多次刷新（批量转换时每完成一个都会触发）
            kotlinx.coroutines.delay(250)
            _convertedLoading.value = true
            val jobsSnapshot = _jobs.value
            // 产物已在应用外被删除的成功任务：标记为「输出文件已删除」，不再显示为已完成
            val missingJobs = mutableListOf<String>()
            val canCheck = PermissionUtils.hasPermissions(context)
            val list = try {
                scanConverted(jobsSnapshot, canCheck, missingJobs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refresh converted failed", e)
                null
            } finally {
                _convertedLoading.value = false
            }
            if (missingJobs.isNotEmpty()) {
                val ids = missingJobs.toSet()
                _jobs.update { all ->
                    all.map {
                        if (it.id in ids && it.hasOutput) {
                            it.copy(outputPath = null, outputUri = null, state = JobState.FAILED, error = JobErrors.FILE_DELETED)
                        } else it
                    }
                }
            }
            // 内容不变时不发射，列表不重组；视频列表的重扫由真正的文件增删（outputRevision）触发
            if (list != null && list != _converted.value) _converted.value = list
        }
    }

    private suspend fun scanConverted(
        jobsSnapshot: List<ConvertJob>,
        canCheck: Boolean,
        missingJobs: MutableList<String>
    ): List<ConvertedVideo> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<ConvertedVideo>()
            val seenPaths = mutableSetOf<String>()
            val seenUris = mutableSetOf<String>()
            jobsSnapshot.filter { it.hasOutput }.forEach { job ->
                ensureActive()  // 被新的刷新取代时尽早停止
                if (!VideoUtils.outputExists(context, job.outputUri, job.outputPath)) {
                    if (canCheck) missingJobs.add(job.id)
                    return@forEach
                }
                val info = outputInfo(job) ?: return@forEach
                val uri = info.uri
                result.add(ConvertedVideo(info, job))
                job.outputPath?.let { seenPaths.add(it) }
                seenUris.add(uri.toString())
            }
            val privateFiles = VideoUtils.scanConvertedDir(context)
            // 之前版本（或重装前）转换的产物：没有任何记录，按命名规则从媒体库找出来，并对应回原视频
            val legacy = runCatching { VideoUtils.scanRotatedOutputs(context) }.getOrDefault(emptyList())
            adoptLegacyOutputs(privateFiles + legacy)
            privateFiles.forEach { v ->
                if (v.path !in seenPaths && v.uri.toString() !in seenUris) {
                    result.add(ConvertedVideo(v, null))
                    v.path?.let { seenPaths.add(it) }
                    seenUris.add(v.uri.toString())
                }
            }
            // 清理产物已被外部删除的对应关系，视频列表的「已转换」标识随之更新
            outputLinks.prune(context)
            // 任务记录已清空、但对应关系还在的产物（保存在 Movies / DCIM / 自定义路径的）
            val legacyByUri = legacy.associateBy { it.uri.toString() }
            outputLinks.links.value.forEach { link ->
                ensureActive()
                if (link.outputUri in seenUris || (link.outputPath != null && link.outputPath in seenPaths)) return@forEach
                // 媒体库里已查到的直接用，不再逐个解析文件
                legacyByUri[link.outputUri]?.let { v ->
                    result.add(ConvertedVideo(v, null))
                    seenUris.add(link.outputUri)
                    v.path?.let { seenPaths.add(it) }
                    return@forEach
                }
                val out = link.output
                if (!VideoUtils.outputExists(context, out.uri, out.path)) return@forEach
                val file = out.path?.let { java.io.File(it) }?.takeIf { it.exists() }
                VideoUtils.readVideoInfo(
                    context, out.uri, out.name, file?.length() ?: link.source.size,
                    (file?.lastModified() ?: link.createdAt) / 1000, out.path
                )?.let {
                    result.add(ConvertedVideo(it, null))
                    seenUris.add(out.uri.toString())
                    out.path?.let { p -> seenPaths.add(p) }
                }
            }
            // 找不到原视频的旧产物也列出来，便于统一管理
            legacy.forEach { v ->
                if (v.uri.toString() !in seenUris && (v.path == null || v.path !in seenPaths)) {
                    result.add(ConvertedVideo(v, null))
                }
            }
            // 关联仍在设备上的原视频（可删除以释放空间）；媒体库 uri 已变的换成当前的，
            // 同一原视频的多个产物共用一次查询
            val resolved = HashMap<String, FileRef?>()
            result
                // 列表以 uri 作 key，重复会让 LazyColumn 直接崩溃
                .distinctBy { it.video.uri.toString() }
                .map { item ->
                    val src = outputLinks.linkOf(item.video.uri, item.video.path)?.source
                        ?: item.job?.videoInfo?.let { FileRef(it.uri, it.path, it.displayName, it.size) }
                    val alive = src?.takeIf { it.uri != item.video.uri }?.let { ref ->
                        resolved.getOrPut(ref.fileKey) {
                            if (VideoUtils.outputExists(context, ref.uri, ref.path)) currentRef(ref) else null
                        }
                    }
                    item.copy(original = alive)
                }
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
            // 只保留最近的已结束记录，历史不无限增长（产物文件不受影响，仍在「已转视频」中）
            val keepFinished = jobs.filter { it.isFinished }
                .sortedByDescending { it.createdAt }
                .take(MAX_FINISHED_HISTORY)
                .map { it.id }.toSet()
            jobs.filter { it.isActive || it.id in keepFinished }.forEach { arr.put(it.toJson()) }
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
