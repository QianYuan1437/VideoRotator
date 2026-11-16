package com.videorotator.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * 转换配置 / 软件设置的持久化。
 * 之前「转换配置」页的选项只存在 remember 里，切 Tab 就丢，也从没被转换流程读取过。
 */
object AppPrefs {

    private const val FILE = "app_settings"
    private const val KEY_OUTPUT_TARGET = "output_target"
    private const val KEY_OUTPUT_TREE = "output_tree_uri"
    private const val KEY_CLOCKWISE = "rotate_clockwise"
    private const val KEY_AUTO_SCAN = "auto_scan"
    private const val KEY_NOTIFY = "notify_on_complete"
    private const val KEY_LIST_FILTER = "video_list_filter"
    private const val KEY_LIST_DEDUPE = "video_list_dedupe"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getOutputTarget(context: Context): OutputTarget =
        prefs(context).getString(KEY_OUTPUT_TARGET, null)
            ?.let { runCatching { OutputTarget.valueOf(it) }.getOrNull() }
            ?: OutputTarget.MOVIES

    fun setOutputTarget(context: Context, target: OutputTarget) {
        prefs(context).edit().putString(KEY_OUTPUT_TARGET, target.name).apply()
    }

    /** 「自定义路径」对应的 SAF 文件夹（与视频列表浏览的文件夹相互独立） */
    fun getOutputTreeUri(context: Context): Uri? =
        prefs(context).getString(KEY_OUTPUT_TREE, null)?.let { Uri.parse(it) }

    fun setOutputTreeUri(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_OUTPUT_TREE, uri?.toString()).apply()
    }

    fun isClockwise(context: Context): Boolean = prefs(context).getBoolean(KEY_CLOCKWISE, true)

    fun setClockwise(context: Context, clockwise: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOCKWISE, clockwise).apply()
    }

    /** 默认旋转角度：顺时针 90 / 逆时针 270 */
    fun defaultDegrees(context: Context): Int = if (isClockwise(context)) 90 else 270

    fun getAutoScan(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SCAN, true)

    fun setAutoScan(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
    }

    fun getNotifyOnComplete(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFY, true)

    fun setNotifyOnComplete(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFY, enabled).apply()
    }

    fun getListFilter(context: Context): String? = prefs(context).getString(KEY_LIST_FILTER, null)

    fun setListFilter(context: Context, name: String) {
        prefs(context).edit().putString(KEY_LIST_FILTER, name).apply()
    }

    fun getListDedupe(context: Context): Boolean = prefs(context).getBoolean(KEY_LIST_DEDUPE, false)

    fun setListDedupe(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LIST_DEDUPE, enabled).apply()
    }
}
