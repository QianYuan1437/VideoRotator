package com.videorotator.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闪退记录：未捕获的异常写入私有目录，下次打开时可在「设置 → 关于」复制，便于反馈问题
 * （之前闪退后什么都留不下，只能靠复现来定位）。
 */
object CrashLog {

    private const val FILE = "last_crash.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file(app).writeText(describe(app, thread, e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun describe(context: Context, thread: Thread, e: Throwable): String {
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("VideoRotator $version")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " · thread ${thread.name}")
            appendLine()
            append(trace.take(16_000))
        }
    }
}
