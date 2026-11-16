package com.videorotator.utils

import androidx.annotation.StringRes
import com.videorotator.R

/**
 * 转换错误码。任务记录会持久化，错误若存成文字，切换语言后旧记录仍是原语言；
 * 因此存「@代码」，显示时再按当前语言取字符串。旧版本存下的中文文字原样显示。
 */
object JobErrors {
    const val INTERRUPTED = "@interrupted"
    const val FAILED = "@failed"
    const val FILE_DELETED = "@file_deleted"
    const val NO_OUTPUT_DIR = "@no_output_dir"
    const val CREATE_FILE = "@create_file"
    const val OPEN_OUTPUT = "@open_output"
    const val TREE_ACCESS = "@tree_access"
    const val TREE_WRITE = "@tree_write"
    const val NO_TRACKS = "@no_tracks"

    @StringRes
    fun resOf(code: String?): Int? = when (code) {
        INTERRUPTED -> R.string.err_interrupted
        FAILED -> R.string.err_failed
        FILE_DELETED -> R.string.err_file_deleted
        NO_OUTPUT_DIR -> R.string.err_no_output_dir
        CREATE_FILE -> R.string.err_create_file
        OPEN_OUTPUT -> R.string.err_open_output
        TREE_ACCESS -> R.string.err_tree_access
        TREE_WRITE -> R.string.err_tree_write
        NO_TRACKS -> R.string.err_no_tracks
        else -> null
    }
}

/** 已知错误：message 即错误码 */
class ConvertError(code: String) : Exception(code)
