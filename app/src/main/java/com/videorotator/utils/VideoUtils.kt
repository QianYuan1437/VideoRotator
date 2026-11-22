package com.videorotator.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.videorotator.R
import java.io.File
import java.nio.ByteBuffer

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    /** 秒级时间戳（MediaStore DATE_ADDED / 文件修改时间统一为秒） */
    val dateAdded: Long,
    /** 旋转元数据（0/90/180/270）；ROTATION_UNKNOWN 表示 MediaStore 没提供、尚待读取 */
    val rotation: Int = 0,
    /** 文件绝对路径（MediaStore DATA 列 / 私有目录文件），SAF 文档为 null */
    val path: String? = null
) {
    /** 计入旋转元数据后的实际显示宽高：转换产物原始宽高不变、只带 90° 旋转标记 */
    private val quarterTurn: Boolean get() = rotation == 90 || rotation == 270
    val displayWidth: Int get() = if (quarterTurn) height else width
    val displayHeight: Int get() = if (quarterTurn) width else height
    val isLandscape: Boolean get() = displayWidth > displayHeight
    val resolution: String get() = "${displayWidth}×${displayHeight}"
    val durationText: String get() {
        val seconds = duration / 1000
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
    val sizeText: String get() = VideoUtils.formatSize(size)
}

/** 一次转换的输出：path 供展示 / 匹配，uri 供播放 / 删除 */
data class RotateOutput(val path: String?, val uri: Uri)

/** 转换输出位置 */
enum class OutputTarget(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    APP_PRIVATE(R.string.target_private, R.string.target_private_desc),
    MOVIES(R.string.target_movies, R.string.target_movies_desc),
    DCIM(R.string.target_dcim, R.string.target_dcim_desc),
    DOWNLOAD(R.string.target_download, R.string.target_download_desc),
    CUSTOM(R.string.target_custom, R.string.target_custom_desc);

    /** 公共目录对应的 Environment 常量（APP_PRIVATE / CUSTOM 为 null） */
    val publicDir: String?
        get() = when (this) {
            MOVIES -> Environment.DIRECTORY_MOVIES
            DCIM -> Environment.DIRECTORY_DCIM
            DOWNLOAD -> Environment.DIRECTORY_DOWNLOADS
            else -> null
        }
}

const val ROTATION_UNKNOWN = -1

object VideoUtils {

    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v", "3gp")

    /** 转换产物命名：<原名>_rotated_<度数>deg.mp4 */
    private val ROTATED_NAME = Regex("""^(.*)_rotated_(\d+)deg(?: \(\d+\))?\.mp4$""", RegexOption.IGNORE_CASE)

    fun outputNameFor(baseName: String, degrees: Int) = "${baseName}_rotated_${degrees}deg.mp4"

    /** 若文件名是转换产物，返回其原视频的不带扩展名的名字 */
    fun sourceBaseNameOf(outputName: String): String? = ROTATED_NAME.find(outputName)?.groupValues?.get(1)

    /** 产物文件名里的旋转角度（相对原视频的净角度） */
    fun degreesInName(outputName: String): Int? = ROTATED_NAME.find(outputName)?.groupValues?.get(2)?.toIntOrNull()

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /**
     * 从 MediaStore 一次性读出目录下的视频。
     * 旋转角度直接读 ORIENTATION 列（Android 10+），不再逐个打开 MediaMetadataRetriever
     * —— 之前每个视频都要解析一次文件头，视频一多扫描就要好几秒。
     */
    fun scanVideos(context: Context, directory: String? = null): List<VideoInfo> {
        val dir = directory?.trimEnd('/')
        return queryVideos(
            context,
            if (dir != null) "${MediaStore.Video.Media.DATA} LIKE ?" else null,
            if (dir != null) arrayOf("$dir/%") else null
        )
    }

    /** 设备上所有按本应用命名规则生成的转换产物（包括之前版本 / 重装前转换的） */
    fun scanRotatedOutputs(context: Context): List<VideoInfo> =
        queryVideos(context, "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?", arrayOf("%rotated%deg%"))
            .filter { sourceBaseNameOf(it.displayName) != null }

    /** 按文件名（不含扩展名）查找视频，用于给旧产物找回原视频 */
    fun findVideosByBaseName(context: Context, baseName: String): List<VideoInfo> =
        // LIKE 里 _ 和 % 是通配符：先宽松查询，再精确比对文件名
        queryVideos(context, "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?", arrayOf("$baseName.%"))
            .filter { it.displayName.substringBeforeLast('.').equals(baseName, ignoreCase = true) }

    private fun queryVideos(context: Context, selection: String?, selectionArgs: Array<String>?): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()
        val collection = videoCollection()
        // Android 11 起的媒体扫描器才会为视频填 ORIENTATION；更早的版本按「未知」处理，
        // 由 FileBrowserViewModel 在列表显示后再后台补读
        val hasOrientation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        ).apply {
            if (hasOrientation) add(MediaStore.Video.Media.ORIENTATION)
        }.toTypedArray()

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val oriCol = if (hasOrientation) cursor.getColumnIndex(MediaStore.Video.Media.ORIENTATION) else -1

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in videoExtensions) continue

                val id = cursor.getLong(idCol)
                videos.add(
                    VideoInfo(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        displayName = name,
                        duration = cursor.getLong(durCol),
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        size = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(dateCol),
                        rotation = if (oriCol >= 0 && !cursor.isNull(oriCol)) cursor.getInt(oriCol) else ROTATION_UNKNOWN,
                        path = cursor.getString(dataCol)
                    )
                )
            }
        }
        return videos
    }

    private val ISO6709 = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

    /** 一次解析读出旋转角（needRotation 时）与拍摄地点（ISO 6709，如 +37.4219-122.0840/） */
    private fun readRotationAndLocation(context: Context, uri: Uri, needRotation: Boolean): Pair<Int, Pair<Float, Float>?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rotation = if (needRotation) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } else 0
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                ?.let { ISO6709.find(it) }
                ?.let { m -> m.groupValues[1].toFloatOrNull()?.let { lat -> m.groupValues[2].toFloatOrNull()?.let { lat to it } } }
                ?.takeIf { (lat, lon) -> lat in -90f..90f && lon in -180f..180f }
            rotation to location
        } catch (_: Exception) {
            0 to null
        } finally {
            retriever.release()
        }
    }

    fun getRotation(context: Context, uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    /**
     * 旋转视频（通过修改容器元数据，不重新编码，秒级完成）
     *
     * 输出位置：
     *  - outputTreeUri 非空：写入 SAF 选择的文件夹
     *  - target 为 MOVIES / DCIM / DOWNLOAD：Android 10+ 通过 MediaStore 写入（无需存储权限，
     *    写完立即出现在视频列表 / 相册），更早版本直接写文件后通知媒体扫描
     *  - 其他：应用私有目录 /Android/data/<pkg>/files/rotated
     *
     * 新产物先写在旁边（临时名 / 系统自动改名），成功后才删除同名旧产物并改回正式名：
     * 重复转换失败或被取消时，之前的产物不受影响。
     *
     * @param checkCancelled 在复制样本的循环中调用，抛出 CancellationException 即中止并清理半成品
     * @param onOutputCreated 输出文件一创建就回调其 uri，调用方据此在进程被杀后清理半成品
     */
    fun rotateVideo(
        context: Context,
        input: VideoInfo,
        degrees: Int,
        target: OutputTarget,
        outputTreeUri: Uri? = null,
        checkCancelled: () -> Unit = {},
        onOutputCreated: (Uri) -> Unit = {},
        /**
         * 目标位置已有同名文件时，是否可以替换它（只允许替换同一原视频的旧产物）。
         * 不可替换时新产物另起名「xxx (1).mp4」—— 两个不同视频恰好同名时，
         * 不能互相覆盖（之前后转的会删掉先转的产物，若其原视频已删除就彻底丢失）。
         */
        canReplace: (uri: Uri, path: String?) -> Boolean = { _, _ -> false },
        onProgress: (Float) -> Unit = {}
    ): Result<RotateOutput> {
        val inputName = queryDisplayName(context, input.uri) ?: input.displayName
        // 输入本身是转换产物（如对已转视频反向旋转）：按相对原视频的净角度命名，
        // 避免出现 xxx_rotated_90deg_rotated_180deg.mp4
        val outputName = ROTATED_NAME.find(inputName)?.let { m ->
            val net = ((m.groupValues[2].toIntOrNull() ?: 0) + degrees) % 360
            outputNameFor(m.groupValues[1], net)
        } ?: outputNameFor(inputName.substringBeforeLast('.').ifBlank { "video" }, degrees)
        val io = RotateIo(checkCancelled, onOutputCreated, canReplace, onProgress, queryDateTaken(context, input.uri))
        return try {
            when {
                outputTreeUri != null ->
                    rotateToTree(context, input.uri, degrees, outputTreeUri, outputName, io)
                target.publicDir != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    rotateToMediaStore(context, input.uri, degrees, target, outputName, io)
                target.publicDir != null -> {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(target.publicDir)
                    rotateToFile(context, input.uri, degrees, dir, outputName, true, io)
                }
                else -> rotateToFile(context, input.uri, degrees, privateOutputDir(context), outputName, false, io)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun privateOutputDir(context: Context): File = File(context.getExternalFilesDir(null), "rotated")

    private class RotateIo(
        val checkCancelled: () -> Unit,
        val onOutputCreated: (Uri) -> Unit,
        val canReplace: (Uri, String?) -> Boolean,
        val onProgress: (Float) -> Unit,
        /** 原视频的拍摄时间（毫秒）：写入产物，相册里按时间排序时与原视频在一起 */
        val dateTaken: Long?
    )

    /** a.mp4 → a (1).mp4 */
    private fun numberedName(name: String, n: Int): String =
        "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "mp4")}"

    private fun queryDateTaken(context: Context, uri: Uri): Long? = try {
        if (uri.authority != MediaStore.AUTHORITY) null
        else context.contentResolver.query(uri, arrayOf(MediaStore.Video.VideoColumns.DATE_TAKEN), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null }
    } catch (_: Exception) { null }

    private const val PART_SUFFIX = ".part"
    private const val LEGACY_TMP_NAME = "rotate_tmp.mp4"

    /**
     * 清理进程被杀时遗留的半成品：私有 / 公共目录中的 .part 文件、Android 7 的临时文件。
     * MediaStore / SAF 中的半成品由调用方按 onOutputCreated 记录的 uri 删除。
     */
    fun cleanupLeftovers(context: Context) {
        File(context.cacheDir, LEGACY_TMP_NAME).delete()
        privateOutputDir(context).listFiles()?.forEach { if (it.name.endsWith(PART_SUFFIX)) it.delete() }
    }

    private fun rotateToFile(
        context: Context,
        inputUri: Uri,
        degrees: Int,
        targetDir: File,
        outputName: String,
        scanAfter: Boolean,
        io: RotateIo
    ): Result<RotateOutput> {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return Result.failure(ConvertError(JobErrors.NO_OUTPUT_DIR))
        }
        // 临时文件名每个任务唯一：两个任务并行写同名产物时互不干扰
        val partFile = File(targetDir, "$outputName.${System.nanoTime()}$PART_SUFFIX")
        io.onOutputCreated(Uri.fromFile(partFile))
        var ok = false
        var outputFile = File(targetDir, outputName)
        try {
            doRotate(context, inputUri, null, partFile.absolutePath, degrees, io)
            synchronized(this) {
                if (outputFile.exists() && io.canReplace(Uri.fromFile(outputFile), outputFile.absolutePath)) {
                    outputFile.delete()
                }
                var n = 1
                while (outputFile.exists()) outputFile = File(targetDir, numberedName(outputName, n++))
                ok = partFile.renameTo(outputFile)
            }
        } finally {
            if (!ok) partFile.delete()
        }
        if (!ok) return Result.failure(ConvertError(JobErrors.CREATE_FILE))
        if (scanAfter) {
            // 等媒体扫描完成再返回，否则视频列表随即重扫时还查不到新文件
            val latch = java.util.concurrent.CountDownLatch(1)
            MediaScannerConnection.scanFile(
                context, arrayOf(outputFile.absolutePath), arrayOf("video/mp4")
            ) { _, _ -> latch.countDown() }
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        return Result.success(RotateOutput(outputFile.absolutePath, Uri.fromFile(outputFile)))
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun rotateToMediaStore(
        context: Context,
        inputUri: Uri,
        degrees: Int,
        target: OutputTarget,
        outputName: String,
        io: RotateIo
    ): Result<RotateOutput> {
        val resolver = context.contentResolver
        val isDownload = target == OutputTarget.DOWNLOAD
        val collection = if (isDownload) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = target.publicDir + "/"

        // 同一原视频之前生成的同名产物：新文件写成功后再删除（同名时 MediaStore 会先给新文件自动改名）；
        // 其他视频的同名产物保留，新文件沿用自动改名
        val sameName = try {
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(relativePath, outputName), null
            )?.use { c ->
                buildList { while (c.moveToNext()) add(Uri.withAppendedPath(collection, c.getLong(0).toString())) }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val oldUris = sameName.filter { uri ->
            val playUri = Uri.withAppendedPath(videoCollection(), android.content.ContentUris.parseId(uri).toString())
            io.canReplace(playUri, queryDataPath(context, uri))
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (!isDownload) io.dateTaken?.let { put(MediaStore.Video.VideoColumns.DATE_TAKEN, it) }
        }
        val outUri = resolver.insert(collection, values)
            ?: return Result.failure(ConvertError(JobErrors.CREATE_FILE))
        io.onOutputCreated(outUri)
        var ok = false
        try {
            resolver.openFileDescriptor(outUri, "rw")?.use { pfd ->
                doRotate(context, inputUri, pfd.fileDescriptor, null, degrees, io)
            } ?: return Result.failure(ConvertError(JobErrors.OPEN_OUTPUT))
            oldUris.forEach { runCatching { resolver.delete(it, null, null) } }
            if (oldUris.isNotEmpty() && oldUris.size == sameName.size) {
                // 旧文件删除后改回正式文件名；失败时保留系统给的自动改名
                runCatching {
                    resolver.update(outUri, ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
                    }, null, null)
                }
            }
            resolver.update(outUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            ok = true
        } finally {
            if (!ok) runCatching { resolver.delete(outUri, null, null) }
        }
        // 视频列表按 DATA 路径匹配转换产物，这里查出实际落盘路径（可能被自动改名）
        @Suppress("DEPRECATION")
        val path = queryDataPath(context, outUri)
            ?: File(Environment.getExternalStoragePublicDirectory(target.publicDir), outputName).absolutePath
        // 视频列表扫描用的是 external 卷 + Video 集合的 uri，这里统一成同样的形式，便于按 uri 匹配产物
        val playUri = if (isDownload) {
            findVideoUriByPath(context, path) ?: outUri
        } else {
            Uri.withAppendedPath(videoCollection(), android.content.ContentUris.parseId(outUri).toString())
        }
        return Result.success(RotateOutput(path, playUri))
    }

    private fun rotateToTree(
        context: Context,
        inputUri: Uri,
        degrees: Int,
        outputTreeUri: Uri,
        outputName: String,
        io: RotateIo
    ): Result<RotateOutput> {
        val tree = DocumentFile.fromTreeUri(context, outputTreeUri)
            ?: return Result.failure(ConvertError(JobErrors.TREE_ACCESS))
        if (!tree.canWrite()) return Result.failure(ConvertError(JobErrors.TREE_WRITE))

        // 同名旧产物在新文件写成功后再删除（同名时文档提供方会先给新文件自动改名）
        val oldDoc = tree.findFile(outputName)
        val outputDoc = tree.createFile("video/mp4", outputName)
            ?: return Result.failure(ConvertError(JobErrors.CREATE_FILE))
        io.onOutputCreated(outputDoc.uri)
        fun replaceOld() {
            if (oldDoc != null && oldDoc.uri != outputDoc.uri &&
                io.canReplace(oldDoc.uri, treeDocumentPath(oldDoc.uri)) && oldDoc.delete()
            ) {
                runCatching { outputDoc.renameTo(outputName) }
            }
        }
        var ok = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7.x 的 MediaMuxer 不支持写入 FileDescriptor：先写临时文件再拷贝到所选文件夹
            val tmp = File(context.cacheDir, LEGACY_TMP_NAME)
            try {
                doRotate(context, inputUri, null, tmp.absolutePath, degrees, io)
                context.contentResolver.openOutputStream(outputDoc.uri, "w")?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                } ?: return Result.failure(ConvertError(JobErrors.OPEN_OUTPUT))
                ok = true
            } finally {
                tmp.delete()
                if (!ok) runCatching { outputDoc.delete() }
            }
            replaceOld()
            return Result.success(RotateOutput(treeDocumentPath(outputDoc.uri), outputDoc.uri))
        }
        try {
            val outPfd = try {
                context.contentResolver.openFileDescriptor(outputDoc.uri, "rw")
            } catch (_: Exception) {
                // 部分文档提供方只支持 "w"
                context.contentResolver.openFileDescriptor(outputDoc.uri, "w")
            }
            outPfd?.use { pfd ->
                doRotate(context, inputUri, pfd.fileDescriptor, null, degrees, io)
            } ?: return Result.failure(ConvertError(JobErrors.OPEN_OUTPUT))
            ok = true
        } finally {
            if (!ok) runCatching { outputDoc.delete() }
        }
        replaceOld()
        val path = treeDocumentPath(outputDoc.uri)
        return Result.success(RotateOutput(path, outputDoc.uri))
    }

    /**
     * 实际执行 MediaExtractor + MediaMuxer 流程。
     * 所有轨道一起选中、按文件内顺序交错复制（之前逐轨复制，产物音视频不交错，
     * 部分播放器拖动进度时会卡顿）；进度按时间戳 / 总时长计算。
     */
    private fun doRotate(
        context: Context,
        inputUri: Uri,
        outputFileDescriptor: java.io.FileDescriptor?,
        outputFilePath: String?,
        degrees: Int,
        io: RotateIo
    ) {
        val checkCancelled = io.checkCancelled
        val onProgress = io.onProgress
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(context, inputUri, null)
            muxer = if (outputFileDescriptor != null) {
                // FileDescriptor 构造器需要 Android 8.0；更早版本由调用方改走临时文件
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) throw IllegalStateException("MediaMuxer(fd) 需要 Android 8.0+")
                MediaMuxer(outputFileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                MediaMuxer(outputFilePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val trackIndexMap = mutableMapOf<Int, Int>()
            var sourceRotation: Int? = null
            var maxSampleSize = 1024 * 1024
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackIndexMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxSampleSize = maxOf(maxSampleSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                    // 视频轨格式里已带旋转角，无需再开一个 MediaMetadataRetriever 解析
                    if (mime.startsWith("video/") && sourceRotation == null && format.containsKey(MediaFormat.KEY_ROTATION)) {
                        sourceRotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                }
            }
            if (trackIndexMap.isEmpty()) throw ConvertError(JobErrors.NO_TRACKS)

            val (metaRotation, location) = readRotationAndLocation(context, inputUri, needRotation = sourceRotation == null)
            val baseRotation = sourceRotation ?: metaRotation
            muxer.setOrientationHint(((baseRotation + degrees) % 360 + 360) % 360)
            // 保留拍摄地点（MediaMuxer 只复制音视频轨，其余元数据需单独写回）
            location?.let { (lat, lon) -> runCatching { muxer.setLocation(lat, lon) } }
            muxer.start()
            muxerStarted = true
            onProgress(0.02f)

            // 4K 关键帧可能超过 1MB，按轨道声明的最大样本尺寸分配缓冲区
            val buffer = ByteBuffer.allocate(maxOf(maxSampleSize, 4 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            var lastReported = 0f
            var counter = 0
            while (true) {
                if (++counter % 64 == 0) checkCancelled()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val dst = trackIndexMap[track]
                if (dst != null) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    // 只保留关键帧标记（MediaExtractor 的其他标记 MediaMuxer 不认）
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(dst, buffer, info)
                }
                extractor.advance()
                if (durationUs > 0) {
                    val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.99f)
                    if (p - lastReported >= 0.01f) {
                        lastReported = p
                        onProgress(p)
                    }
                }
            }
            checkCancelled()
            muxer.stop()
            muxerStarted = false
            onProgress(1.0f)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    /** 通过 ContentResolver 查询 OpenableColumns.DISPLAY_NAME */
    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun queryDataPath(context: Context, uri: Uri): String? = try {
        @Suppress("DEPRECATION")
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    /** 按文件路径查出当前的媒体库 uri（文件被重新扫描后 _ID 会变，之前记下的 uri 随之失效） */
    fun findVideoUriByPath(context: Context, path: String): Uri? = try {
        val collection = videoCollection()
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DATA}=?", arrayOf(path), null
        )?.use { c ->
            if (c.moveToFirst()) Uri.withAppendedPath(collection, c.getLong(0).toString()) else null
        }
    } catch (_: Exception) { null }

    /**
     * 能否交给 MediaStore.createDeleteRequest：只接受图片 / 视频 / 音频集合中带具体 _ID 的条目，
     * 其余（Downloads / Files 集合、SAF 文档等）混进去一个，整批请求都会抛异常。
     */
    fun isMediaItemUri(uri: Uri): Boolean {
        if (uri.authority != MediaStore.AUTHORITY) return false
        val seg = uri.pathSegments
        return seg.size >= 4 && seg[seg.size - 3] in setOf("video", "images", "audio") &&
            seg[seg.size - 2] == "media" && seg.last().toLongOrNull() != null
    }

    /** 文件大小：不足 1 MB 显示 KB（之前小文件一律显示「0.0 MB」） */
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1024 -> "%.2f GB".format(mb / 1024)
            mb >= 1 -> "%.1f MB".format(mb)
            else -> "%.0f KB".format(kb.coerceAtLeast(if (bytes > 0) 1.0 else 0.0))
        }
    }

    /** 删除一个转换产物（文件路径 / MediaStore / SAF 文档均可） */
    fun deleteOutput(context: Context, uri: Uri?, path: String?): Boolean {
        var deleted = false
        try {
            when {
                uri == null -> {}
                uri.scheme == "file" -> {
                    deleted = uri.path?.let { File(it).delete() } == true
                    // 公共目录里的文件（Android 9 及以下直接写文件）需通知媒体库移除
                    if (deleted) MediaScannerConnection.scanFile(context, arrayOf(uri.path), null, null)
                }
                uri.authority == MediaStore.AUTHORITY ->
                    deleted = context.contentResolver.delete(uri, null, null) > 0
                DocumentsContract.isDocumentUri(context, uri) ->
                    deleted = DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }
        } catch (_: Exception) {}
        if (!deleted && path != null) {
            val f = File(path)
            if (f.exists()) {
                deleted = runCatching { f.delete() }.getOrDefault(false)
                if (deleted) MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        }
        return deleted
    }

    /** 输出文件是否仍然存在 */
    fun outputExists(context: Context, uri: Uri?, path: String?): Boolean {
        if (path != null && path.startsWith("/") && File(path).exists()) return true
        if (uri == null) return false
        if (uri.scheme == "file") return uri.path?.let { File(it).exists() } == true
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 读取单个视频文件的元数据（用于私有目录 / SAF 文件，这些不在 MediaStore 里） */
    fun readVideoInfo(context: Context, uri: Uri, name: String, size: Long, modifiedSec: Long, path: String?): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            VideoInfo(
                uri = uri,
                displayName = name,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                size = size,
                dateAdded = modifiedSec,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                path = path
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * 扫描应用私有目录 /Android/data/<pkg>/files/rotated/ 下的转换产物
     * （MediaStore 不会索引这些路径，所以需要单独扫描才能在列表中显示）
     */
    fun scanConvertedDir(context: Context): List<VideoInfo> {
        val dir = privateOutputDir(context)
        val files = dir.listFiles() ?: return emptyList()
        val result = files
            .filter { it.isFile && it.extension.lowercase() in videoExtensions }
            .mapNotNull { file ->
                // 按 (大小, 修改时间) 缓存元数据：每次刷新「已转视频」不再逐个重新解析未变化的文件
                val stamp = file.length() to file.lastModified()
                val cached = synchronized(convertedInfoCache) { convertedInfoCache[file.absolutePath] }
                if (cached != null && cached.first == stamp) return@mapNotNull cached.second
                readVideoInfo(context, Uri.fromFile(file), file.name, stamp.first, stamp.second / 1000, file.absolutePath)
                    ?.also { info -> synchronized(convertedInfoCache) { convertedInfoCache[file.absolutePath] = stamp to info } }
            }
            .sortedByDescending { it.dateAdded }
        synchronized(convertedInfoCache) { convertedInfoCache.keys.retainAll(result.mapNotNull { it.path }.toSet()) }
        return result
    }

    private val convertedInfoCache = HashMap<String, Pair<Pair<Long, Long>, VideoInfo>>()

    /** 通过 DATA 列查询内容 URI 对应的文件所在目录 */
    fun getParentPath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let { File(it).parent }
        return queryDataPath(context, uri)?.let { File(it).parent }
    }

    /** 默认扫描目录：用户设备的"下载"目录 */
    fun getDefaultVideoDirectory(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    /** 解析 treeUri 得到供 UI 显示的友好路径（primary:DCIM/sub2 形式） */
    fun describeTreeUri(treeUri: Uri): String {
        return try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: treeUri.toString()
        }
    }

    /**
     * 外部存储 SAF 文档 ID（primary:DCIM/sub）→ 绝对路径（/storage/emulated/0/DCIM/sub）。
     * 非 externalstorage 提供方（网盘等）返回 null。
     */
    fun treeDocIdToPath(docId: String): String? {
        val volume = docId.substringBefore(':', "")
        if (volume.isEmpty()) return null
        val rel = docId.substringAfter(':', "")
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    private fun treeDocumentPath(docUri: Uri): String? = try {
        if (docUri.authority == "com.android.externalstorage.documents") {
            treeDocIdToPath(DocumentsContract.getDocumentId(docUri))
        } else null
    } catch (_: Exception) { null }

    /**
     * 扫描 SAF treeUri 下（含子目录）的所有视频文件。
     * 优先把外部存储的目录映射成路径直接查 MediaStore（毫秒级）；
     * 查不到时再逐目录查询 DocumentsContract（每个目录一次查询，而不是每个文件多次）。
     */
    fun scanVideosFromTreeUri(context: Context, treeUri: Uri): List<VideoInfo> {
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return emptyList()
        }
        if (treeUri.authority == "com.android.externalstorage.documents") {
            val path = treeDocIdToPath(rootDocId)
            if (path != null) {
                val viaMediaStore = runCatching { scanVideos(context, path) }.getOrDefault(emptyList())
                if (viaMediaStore.isNotEmpty()) {
                    // MediaStore 可能漏掉未索引的文件（.nomedia 目录、刚拷入的文件）：
                    // 再按目录列一遍文件名（每个目录一次查询，很快），只对漏掉的文件读取元数据
                    val known = viaMediaStore.mapNotNull { it.path }.toHashSet()
                    val missing = listTreeVideos(context, treeUri, rootDocId).filter { (uri, _, _) ->
                        val p = runCatching { treeDocIdToPath(DocumentsContract.getDocumentId(uri)) }.getOrNull()
                        p == null || p !in known
                    }
                    return viaMediaStore + readTreeVideos(context, treeUri, missing)
                }
            }
        }
        return readTreeVideos(context, treeUri, listTreeVideos(context, treeUri, rootDocId))
    }

    /** 逐目录列出 SAF 子树下的视频文件：(文档 uri, 文件名, 大小 to 修改时间秒) */
    private fun listTreeVideos(
        context: Context,
        treeUri: Uri,
        rootDocId: String
    ): List<Triple<Uri, String, Pair<Long, Long>>> {
        val found = mutableListOf<Triple<Uri, String, Pair<Long, Long>>>()
        val pending = ArrayDeque<String>().apply { add(rootDocId) }
        val resolver = context.contentResolver
        while (pending.isNotEmpty()) {
            val docId = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            try {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(id)
                        } else if (mime.startsWith("video/") ||
                            name.substringAfterLast('.', "").lowercase() in videoExtensions
                        ) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            found.add(Triple(uri, name, c.getLong(3) to c.getLong(4) / 1000))
                        }
                    }
                }
            } catch (_: Exception) {
                // 单个目录无权限等情况跳过
            }
        }
        return found
    }

    private fun readTreeVideos(
        context: Context,
        treeUri: Uri,
        files: List<Triple<Uri, String, Pair<Long, Long>>>
    ): List<VideoInfo> {
        return files.mapNotNull { (uri, name, meta) ->
            val path = if (treeUri.authority == "com.android.externalstorage.documents") {
                runCatching { treeDocIdToPath(DocumentsContract.getDocumentId(uri)) }.getOrNull()
            } else null
            readVideoInfo(context, uri, name, meta.first, meta.second, path)
        }
    }

    /**
     * 视频缩略图：Android 10+ 优先用系统缩略图缓存（MediaStore / DocumentsProvider，毫秒级），
     * 失败再用 MediaMetadataRetriever 抽取一帧并直接按目标尺寸解码。
     */
    fun loadThumbnail(context: Context, uri: Uri, maxDim: Int = 320): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
            } catch (_: Exception) {}
        }
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDim, maxDim
                ) ?: retriever.getFrameAtTime(0)
            } else {
                retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
