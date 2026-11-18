package com.videorotator.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** 一个视频文件的引用（删除 / 存在性检查用） */
data class FileRef(
    val uri: Uri,
    val path: String?,
    val name: String,
    val size: Long
)

/** 转换产物 → 原视频 的对应关系 */
data class SourceLink(
    val outputUri: String,
    val outputPath: String?,
    val source: FileRef,
    val sourceDuration: Long,
    /** 相对原视频的净旋转角 */
    val degrees: Int,
    val createdAt: Long,
    /** 产物的保存位置（OutputTarget 名称）与自定义文件夹，反向旋转时写回同一位置 */
    val target: String? = null,
    val treeUri: String? = null,
    /** 原视频已不在设备上（被删除）：不再提供「删除原视频」 */
    val sourceMissing: Boolean = false
) {
    val output: FileRef
        get() = FileRef(
            Uri.parse(outputUri), outputPath,
            outputPath?.substringAfterLast('/') ?: Uri.parse(outputUri).lastPathSegment.orEmpty(),
            source.size
        )
}

/**
 * 产物与原视频的对应表，独立于任务记录持久化：清空 / 删除任务记录、任务历史被裁剪后，
 * 视频列表仍能准确知道每个产物来自哪个原视频（据此标识「已转换」、删除原视频释放空间）。
 */
class OutputLinks(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("output_links", Context.MODE_PRIVATE)

    private val _links = MutableStateFlow(load())
    val links: StateFlow<List<SourceLink>> = _links.asStateFlow()

    /** 记录一个新产物；同一输出的旧记录被替换 */
    fun put(link: SourceLink) {
        _links.update { list ->
            (list.filterNot { it.sameOutput(link.outputUri, link.outputPath) } + link)
                .sortedByDescending { it.createdAt }
                .take(MAX_LINKS)
        }
        persist()
    }

    /** 产物被删除 */
    fun removeOutput(uri: Uri?, path: String?) {
        val before = _links.value
        _links.update { list -> list.filterNot { it.sameOutput(uri?.toString(), path) } }
        if (_links.value.size != before.size) persist()
    }

    /** 按产物查找对应的原视频 */
    fun linkOf(uri: Uri?, path: String?): SourceLink? =
        _links.value.firstOrNull { it.sameOutput(uri?.toString(), path) }

    /**
     * 清除产物已不存在的记录，并更新原视频是否还在（需在 IO 线程调用）。
     * 只改动检查出的项：检查期间新加入的记录不受影响。
     */
    fun prune(context: Context) {
        // 没有读取权限时无法判断别的应用 / 重装前创建的文件是否还在，不能据此删除记录
        if (!PermissionUtils.hasPermissions(context)) return
        val snapshot = _links.value
        val dead = snapshot
            .filterNot { VideoUtils.outputExists(context, Uri.parse(it.outputUri), it.outputPath) }
            .map { it.outputUri }.toSet()
        val sourceAlive = snapshot.map { it.source }.distinctBy { it.uri }
            .associate { it.uri.toString() to VideoUtils.outputExists(context, it.uri, it.path) }
        val changed = dead.isNotEmpty() || snapshot.any { sourceAlive[it.source.uri.toString()] == it.sourceMissing }
        if (!changed) return
        _links.update { list ->
            list.filterNot { it.outputUri in dead }.map { l ->
                sourceAlive[l.source.uri.toString()]?.let { alive -> l.copy(sourceMissing = !alive) } ?: l
            }
        }
        persist()
    }

    /** 原视频被删除后调用 */
    fun markSourcesMissing(sourceUris: Set<String>) {
        if (_links.value.none { it.source.uri.toString() in sourceUris }) return
        _links.update { list ->
            list.map { if (it.source.uri.toString() in sourceUris) it.copy(sourceMissing = true) else it }
        }
        persist()
    }

    private fun SourceLink.sameOutput(uri: String?, path: String?): Boolean =
        (uri != null && outputUri == uri) || (path != null && outputPath == path)

    private fun persist() {
        val arr = JSONArray()
        _links.value.forEach { l ->
            arr.put(JSONObject().apply {
                put("outUri", l.outputUri)
                put("outPath", l.outputPath ?: "")
                put("srcUri", l.source.uri.toString())
                put("srcPath", l.source.path ?: "")
                put("srcName", l.source.name)
                put("srcSize", l.source.size)
                put("srcDuration", l.sourceDuration)
                put("degrees", l.degrees)
                put("createdAt", l.createdAt)
                put("target", l.target ?: "")
                put("treeUri", l.treeUri ?: "")
                put("sourceMissing", l.sourceMissing)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<SourceLink> = try {
        val arr = JSONArray(prefs.getString(KEY, null) ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                SourceLink(
                    outputUri = o.getString("outUri"),
                    outputPath = o.optString("outPath").takeIf { it.isNotEmpty() },
                    source = FileRef(
                        uri = Uri.parse(o.getString("srcUri")),
                        path = o.optString("srcPath").takeIf { it.isNotEmpty() },
                        name = o.optString("srcName"),
                        size = o.optLong("srcSize")
                    ),
                    sourceDuration = o.optLong("srcDuration"),
                    degrees = o.optInt("degrees", 90),
                    createdAt = o.optLong("createdAt"),
                    target = o.optString("target").takeIf { it.isNotEmpty() },
                    treeUri = o.optString("treeUri").takeIf { it.isNotEmpty() },
                    sourceMissing = o.optBoolean("sourceMissing")
                )
            }.getOrNull()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY = "links"
        const val MAX_LINKS = 2000
    }
}
