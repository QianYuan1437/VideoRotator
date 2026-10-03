package com.videorotator.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.ByteBuffer

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateAdded: Long,
    val rotation: Int = 0
) {
    val isLandscape: Boolean get() = width > height
    val resolution: String get() = "${width}×${height}"
    val durationText: String get() {
        val seconds = duration / 1000
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
    val sizeText: String get() {
        val mb = size / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }
}

object VideoUtils {

    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v", "3gp")

    fun scanVideos(context: Context, directory: String? = null): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val selection = if (directory != null) {
            "${MediaStore.Video.Media.DATA} LIKE ?"
        } else null
        val selectionArgs = if (directory != null) {
            arrayOf("$directory%")
        } else null

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

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in videoExtensions) continue

                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                val file = File(name)
                val path = file.parent

                if (directory != null && path != null && !path.startsWith(directory)) continue

                val rotation = getRotation(context, uri)

                videos.add(VideoInfo(
                    uri = uri,
                    displayName = name,
                    duration = cursor.getLong(durCol),
                    width = cursor.getInt(wCol),
                    height = cursor.getInt(hCol),
                    size = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(dateCol),
                    rotation = rotation
                ))
            }
        }
        return videos
    }

    fun getRotation(context: Context, uri: Uri): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } else 0
            retriever.release()
            rotation
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 旋转视频90度（通过修改容器元数据，不重新编码，秒级完成）
     * @param outputDir 自定义输出目录（File 形式），与 outputTreeUri 二选一
     * @param outputTreeUri SAF 选择的输出文件夹（content:// 形式），与 outputDir 二选一
     * 返回输出文件路径（SAF 时返回 content:// uri 字符串）
     */
    fun rotateVideo(
        context: Context,
        inputUri: Uri,
        degrees: Int = 90,
        outputDir: File? = null,
        outputTreeUri: Uri? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        if (outputTreeUri != null) {
            return rotateVideoToTree(context, inputUri, degrees, outputTreeUri, onProgress)
        }
        return try {
            val inputFile = getFileFromUri(context, inputUri)
                ?: return Result.failure(Exception("无法访问源文件"))

            val targetDir = outputDir ?: File(context.getExternalFilesDir(null), "rotated")
            if (!targetDir.exists()) targetDir.mkdirs()

            val baseName = inputFile.nameWithoutExtension
            val outputName = "${baseName}_rotated_${degrees}deg.mp4"
            val outputFile = File(targetDir, outputName)

            // 如果已存在则先删除
            if (outputFile.exists()) outputFile.delete()

            onProgress(0.1f)

            val result = doRotate(
                context = context,
                inputUri = inputUri,
                outputFileDescriptor = null,
                outputFilePath = outputFile.absolutePath,
                degrees = degrees,
                onProgress = onProgress
            )
            if (result.isSuccess) Result.success(outputFile.absolutePath) else result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 旋转到 SAF 选定的文件夹（content:// tree uri）
     */
    private fun rotateVideoToTree(
        context: Context,
        inputUri: Uri,
        degrees: Int,
        outputTreeUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<String> {
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            val tree = DocumentFile.fromTreeUri(context, outputTreeUri)
                ?: return Result.failure(Exception("无法访问所选文件夹"))

            val baseName = queryDisplayName(context, inputUri)?.substringBeforeLast('.')
                ?: inputUri.lastPathSegment?.substringBeforeLast('.')
                ?: "rotated"
            val outputName = "${baseName}_rotated_${degrees}deg.mp4"

            // 移除同名旧文件
            tree.findFile(outputName)?.delete()

            val outputDoc = tree.createFile("video/mp4", outputName)
                ?: return Result.failure(Exception("无法在所选文件夹中创建文件"))

            pfd = context.contentResolver.openFileDescriptor(outputDoc.uri, "w")
                ?: return Result.failure(Exception("无法打开输出文件流"))

            doRotate(
                context = context,
                inputUri = inputUri,
                outputFileDescriptor = pfd.fileDescriptor,
                outputFilePath = null,
                degrees = degrees,
                onProgress = onProgress
            ).also {
                if (it.isSuccess) {
                    // 返回 content uri 字符串
                    it.getOrNull()?.let { _ -> /* keep Result path */ }
                }
            }.let { r ->
                if (r.isSuccess) Result.success(outputDoc.uri.toString()) else r
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pfd?.close()
        }
    }

    /**
     * 实际执行 MediaExtractor + MediaMuxer 流程。
     * outputFileDescriptor 优先；为 null 时使用 outputFilePath。
     */
    private fun doRotate(
        context: Context,
        inputUri: Uri,
        outputFileDescriptor: java.io.FileDescriptor?,
        outputFilePath: String?,
        degrees: Int,
        onProgress: (Float) -> Unit
    ): Result<String> = try {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)

        val muxer = if (outputFileDescriptor != null) {
            MediaMuxer(outputFileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            MediaMuxer(outputFilePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        val newRotation = (getRotation(context, inputUri) + degrees) % 360

        var muxerStarted = false
        val trackIndexMap = mutableMapOf<Int, Int>()
        val buffer = ByteBuffer.allocate(1024 * 1024)

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                trackIndexMap[i] = muxer.addTrack(format)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            muxer.setOrientationHint(newRotation)
        }

        muxer.start()
        muxerStarted = true
        onProgress(0.3f)

        val info = android.media.MediaCodec.BufferInfo()
        var progress = 0.3f
        for (srcIndex in trackIndexMap.keys) {
            extractor.selectTrack(srcIndex)
            val dstIndex = trackIndexMap[srcIndex]!!
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstIndex, buffer, info)
                extractor.advance()
                progress += 0.001f
                if (progress < 0.95f) onProgress(progress)
            }
            extractor.unselectTrack(srcIndex)
        }
        onProgress(0.98f)
        if (muxerStarted) muxer.stop()
        muxer.release()
        extractor.release()
        onProgress(1.0f)
        Result.success("ok")
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 通过 ContentResolver 查询 OpenableColumns.DISPLAY_NAME */
    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    /**
     * 扫描应用私有目录 /Android/data/<pkg>/files/rotated/ 下的转换产物
     * （MediaStore 不会索引这些路径，所以需要单独扫描才能在列表中显示）
     */
    fun scanConvertedDir(context: Context): List<VideoInfo> {
        val out = mutableListOf<VideoInfo>()
        val dir = File(context.getExternalFilesDir(null), "rotated")
        if (!dir.exists() || !dir.isDirectory) return out
        val files = dir.listFiles() ?: return out
        for (file in files) {
            if (!file.isFile) continue
            val ext = file.extension.lowercase()
            if (ext.isEmpty() || ext !in videoExtensions) continue
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                retriever.release()
                out.add(
                    VideoInfo(
                        uri = Uri.fromFile(file),
                        displayName = file.name,
                        duration = duration,
                        width = width,
                        height = height,
                        size = file.length(),
                        dateAdded = file.lastModified() / 1000,
                        rotation = rotation
                    )
                )
            } catch (_: Exception) {
                // 单个文件无法读取时跳过
            }
        }
        // 按日期倒序
        return out.sortedByDescending { it.dateAdded }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val path = cursor.getString(nameIndex)
                    if (path != null) return File(path)
                }
            }
            // Fallback: try to get path from URI
            uri.path?.let { File(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** 通过 DATA 列查询内容 URI 对应的文件路径 */
    fun getParentPath(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val path = cursor.getString(nameIndex)
                    if (path != null) File(path).parent else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 默认扫描目录：用户设备的"下载"目录 */
    fun getDefaultVideoDirectory(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    /**
     * 扫描 SAF treeUri 下（含子目录）的所有视频文件
     */
    fun scanVideosFromTreeUri(context: Context, treeUri: Uri): List<VideoInfo> {
        val out = mutableListOf<VideoInfo>()
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
            walkDocumentFile(context, tree, out)
        } catch (e: Exception) {
            // 权限异常等情况静默忽略
        }
        return out
    }

    /** 解析 treeUri 得到供 UI 显示的友好路径（primary:DCIM/sub2 形式） */
    fun describeTreeUri(treeUri: Uri): String {
        return try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: treeUri.toString()
        }
    }

    private fun walkDocumentFile(
        context: Context,
        file: DocumentFile?,
        out: MutableList<VideoInfo>
    ) {
        if (file == null) return
        if (file.isDirectory) {
            file.listFiles().forEach { walkDocumentFile(context, it, out) }
            return
        }
        if (!file.isFile) return
        val mime = file.type ?: return
        if (!mime.startsWith("video/")) return

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, file.uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
            } else 0
            retriever.release()
            out.add(
                VideoInfo(
                    uri = file.uri,
                    displayName = file.name ?: "未命名",
                    duration = duration,
                    width = width,
                    height = height,
                    size = file.length(),
                    dateAdded = file.lastModified(),
                    rotation = rotation
                )
            )
        } catch (e: Exception) {
            // 单个文件无法读取时跳过
        }
    }
}
