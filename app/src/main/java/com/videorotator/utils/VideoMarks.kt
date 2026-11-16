package com.videorotator.utils

import com.videorotator.viewmodel.ConvertJob
import com.videorotator.viewmodel.VideoFilter

/** 视频在列表中的转换标识 */
enum class ConvertMark {
    /** 未转换 */
    NONE,
    /** 原视频，已经转换过（存在成功任务或同名产物） */
    SOURCE_CONVERTED,
    /** 本身就是转换产物 */
    OUTPUT
}

data class MarkedVideo(
    val video: VideoInfo,
    val mark: ConvertMark,
    /** 转换角度（产物或已转换原视频的最近一次转换），未知为 null */
    val degrees: Int? = null
)

object VideoMarks {

    /**
     * 给列表中的视频打上转换标识，并按筛选 / 去重规则过滤。
     *
     * 识别规则（任务记录被清空后仍然有效）：
     *  - 产物：路径 / uri 与成功任务的输出一致，或文件名形如 <原名>_rotated_<度数>deg.mp4
     *  - 已转换原视频：uri 与成功任务的源视频一致，或列表里存在它的同名产物
     *
     * 去重：
     *  - 原视频与其产物同时出现时只保留原视频（卡片上显示「已转换」）
     *  - 同名、同大小、同时长的重复文件（不同目录里的拷贝）只保留一份
     */
    fun mark(
        videos: List<VideoInfo>,
        jobs: List<ConvertJob>,
        filter: VideoFilter,
        dedupe: Boolean
    ): List<MarkedVideo> {
        val success = jobs.filter { it.hasOutput }
        val outputPaths = success.mapNotNull { it.outputPath }.toHashSet()
        val outputUris = success.mapNotNull { it.outputUri?.toString() }.toSet()
        val degreesByOutput = HashMap<String, Int>()
        success.forEach { job ->
            job.outputPath?.let { degreesByOutput[it] = job.degrees }
            job.outputUri?.let { degreesByOutput[it.toString()] = job.degrees }
        }
        val degreesBySource = HashMap<String, Int>()
        success.sortedBy { it.createdAt }.forEach { degreesBySource[it.videoInfo.uri.toString()] = it.degrees }

        fun isOutput(v: VideoInfo): Boolean =
            (v.path != null && v.path in outputPaths) || v.uri.toString() in outputUris ||
                VideoUtils.sourceBaseNameOf(v.displayName) != null

        // 列表中产物对应的原视频名（同目录优先，退化为仅按文件名匹配）
        val outputSourceNames = HashSet<String>()
        videos.forEach { v ->
            VideoUtils.sourceBaseNameOf(v.displayName)?.let { outputSourceNames.add(it.lowercase()) }
        }

        val marked = videos.map { v ->
            val key = v.uri.toString()
            when {
                isOutput(v) -> MarkedVideo(
                    v, ConvertMark.OUTPUT,
                    // 文件名里是相对原视频的净角度，优先于任务角度（对产物反向时任务角度是 180）
                    Regex("""_rotated_(\d+)deg""").find(v.displayName)?.groupValues?.get(1)?.toIntOrNull()
                        ?: degreesByOutput[v.path] ?: degreesByOutput[key]
                )
                key in degreesBySource -> MarkedVideo(v, ConvertMark.SOURCE_CONVERTED, degreesBySource[key])
                v.displayName.substringBeforeLast('.').lowercase() in outputSourceNames ->
                    MarkedVideo(v, ConvertMark.SOURCE_CONVERTED)
                else -> MarkedVideo(v, ConvertMark.NONE)
            }
        }

        var result = when (filter) {
            VideoFilter.ALL -> marked
            VideoFilter.UNCONVERTED -> marked.filter { it.mark == ConvertMark.NONE }
            VideoFilter.CONVERTED -> marked.filter { it.mark != ConvertMark.NONE }
        }

        if (dedupe) {
            val sourceNames = marked.filter { it.mark != ConvertMark.OUTPUT }
                .map { it.video.displayName.substringBeforeLast('.').lowercase() }
                .toSet()
            val sourceUris = marked.filter { it.mark == ConvertMark.SOURCE_CONVERTED }
                .map { it.video.uri.toString() }.toSet()
            val sourceOfOutput = HashMap<String, String>()
            success.forEach { job ->
                job.outputPath?.let { sourceOfOutput[it] = job.videoInfo.uri.toString() }
                job.outputUri?.let { sourceOfOutput[it.toString()] = job.videoInfo.uri.toString() }
            }
            result = result.filter { m ->
                if (m.mark != ConvertMark.OUTPUT) return@filter true
                val src = sourceOfOutput[m.video.path] ?: sourceOfOutput[m.video.uri.toString()]
                val srcName = VideoUtils.sourceBaseNameOf(m.video.displayName)?.lowercase()
                // 原视频在列表中时隐藏产物
                !((src != null && src in sourceUris) || (srcName != null && srcName in sourceNames))
            }
            // 同名、同大小、同时长视为同一个文件的拷贝
            result = result.distinctBy { Triple(it.video.displayName.lowercase(), it.video.size, it.video.duration / 1000) }
        }
        return result
    }
}
