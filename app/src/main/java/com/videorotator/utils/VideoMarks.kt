package com.videorotator.utils

import com.videorotator.viewmodel.VideoFilter
import kotlin.math.abs

/** 视频在列表中的转换标识 */
enum class ConvertMark {
    /** 未转换 */
    NONE,
    /** 在转换队列中（等待 / 转换中） */
    QUEUED,
    /** 原视频，已经转换过（存在对应的产物） */
    SOURCE_CONVERTED,
    /** 本身就是转换产物 */
    OUTPUT
}

data class MarkedVideo(
    val video: VideoInfo,
    val mark: ConvertMark,
    /** 转换角度（产物相对原视频的净角度，或原视频最近一次转换的角度），未知为 null */
    val degrees: Int? = null,
    /** 对应的原视频：产物 → 它的原视频；已转换的原视频 → 自身 */
    val original: FileRef? = null,
    /** 对应的产物：已转换的原视频 → 它的产物；产物 → 自身 */
    val outputs: List<FileRef> = emptyList()
) {
    /** 可以删除原视频以释放空间（删除前还会再确认产物仍在） */
    val canDeleteOriginal: Boolean
        get() = original != null && outputs.isNotEmpty() && outputs.none { it.uri == original.uri }
}

fun VideoInfo.toFileRef() = FileRef(uri, path, displayName, size)

object VideoMarks {

    private val DEGREES_IN_NAME = Regex("""_rotated_(\d+)deg""")

    /** 已完成转换（筛选「已转换」）：原视频已转过，或本身是产物 */
    val ConvertMark.isConverted: Boolean get() = this == ConvertMark.SOURCE_CONVERTED || this == ConvertMark.OUTPUT

    /**
     * 同一视频的原文件与产物时长一致（元数据转换不改变时长）。
     * 按文件名推断时要求两者时长都已知：否则两个不同的同名视频可能被误认作原视频与产物，
     * 进而被提供「删除原视频」
     */
    private fun sameDuration(a: Long, b: Long) = a > 0 && b > 0 && abs(a - b) <= 1500

    private fun baseName(v: VideoInfo) = v.displayName.substringBeforeLast('.').lowercase()

    /**
     * 给列表中的视频打上转换标识，并对应上原视频与产物。
     *
     * 对应规则（按可靠程度）：
     *  1. 产物 → 原视频对应表（每次转换成功时记录，清空任务记录后仍然有效）
     *  2. 文件名 <原名>_rotated_<度数>deg.mp4 且时长一致（对应表之外的旧文件 / 拷贝）
     * 队列中：有等待 / 进行中的任务以它为源。
     */
    fun mark(
        videos: List<VideoInfo>,
        links: List<SourceLink>,
        activeSourceUris: Set<String>
    ): List<MarkedVideo> {
        val linkByOutUri = links.associateBy { it.outputUri }
        val linkByOutPath = links.filter { it.outputPath != null }.associateBy { it.outputPath!! }
        val linksBySourceUri = links.groupBy { it.source.uri.toString() }
        val linksBySourcePath = links.filter { it.source.path != null }.groupBy { it.source.path!! }

        // 列表里按文件名推断的产物（按原视频名分组）与原视频（按名称分组）
        val listOutputsByBase = HashMap<String, MutableList<VideoInfo>>()
        val listSourcesByBase = HashMap<String, MutableList<VideoInfo>>()
        videos.forEach { v ->
            val src = VideoUtils.sourceBaseNameOf(v.displayName)
            if (src != null) listOutputsByBase.getOrPut(src.lowercase()) { mutableListOf() }.add(v)
            else listSourcesByBase.getOrPut(baseName(v)) { mutableListOf() }.add(v)
        }

        return videos.map { v ->
            val key = v.uri.toString()
            val link = linkByOutUri[key] ?: v.path?.let { linkByOutPath[it] }
            val srcBase = VideoUtils.sourceBaseNameOf(v.displayName)
            when {
                link != null || srcBase != null -> {
                    val original = link?.source?.takeIf { !link.sourceMissing }
                        ?: listSourcesByBase[srcBase!!.lowercase()]
                            ?.firstOrNull { sameDuration(it.duration, v.duration) }?.toFileRef()
                    MarkedVideo(
                        v, ConvertMark.OUTPUT,
                        // 文件名里是相对原视频的净角度
                        DEGREES_IN_NAME.find(v.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: link?.degrees,
                        original = original,
                        outputs = listOf(v.toFileRef())
                    )
                }
                key in activeSourceUris -> MarkedVideo(v, ConvertMark.QUEUED)
                else -> {
                    val linked = (linksBySourceUri[key].orEmpty() + v.path?.let { linksBySourcePath[it] }.orEmpty())
                        .distinctBy { it.outputUri }
                    val byName = listOutputsByBase[baseName(v)].orEmpty()
                        .filter { it.uri != v.uri && sameDuration(it.duration, v.duration) }
                    val outputs = (linked.map { it.output } + byName.map { it.toFileRef() }).distinctBy { it.uri }
                    if (outputs.isEmpty()) {
                        MarkedVideo(v, ConvertMark.NONE)
                    } else {
                        MarkedVideo(
                            v, ConvertMark.SOURCE_CONVERTED,
                            linked.maxByOrNull { it.createdAt }?.degrees,
                            original = v.toFileRef(),
                            outputs = outputs
                        )
                    }
                }
            }
        }
    }

    /**
     * 去重：
     *  - 原视频与其产物同时出现时只保留原视频（卡片上显示「已转换」）
     *  - 同名、同大小、同时长的重复文件（不同目录里的拷贝）只保留一份
     */
    fun dedupe(marked: List<MarkedVideo>, enabled: Boolean): List<MarkedVideo> {
        if (!enabled) return marked
        val uris = marked.map { it.video.uri }.toHashSet()
        val paths = marked.mapNotNull { it.video.path }.toHashSet()
        return marked
            .filter { m ->
                val o = m.original
                m.mark != ConvertMark.OUTPUT || o == null || (o.uri !in uris && (o.path == null || o.path !in paths))
            }
            .distinctBy { Triple(it.video.displayName.lowercase(), it.video.size, it.video.duration / 1000) }
    }

    fun filter(marked: List<MarkedVideo>, filter: VideoFilter): List<MarkedVideo> = when (filter) {
        VideoFilter.ALL -> marked
        VideoFilter.UNCONVERTED -> marked.filter { !it.mark.isConverted }
        VideoFilter.CONVERTED -> marked.filter { it.mark.isConverted }
    }
}
