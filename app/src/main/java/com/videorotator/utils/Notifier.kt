package com.videorotator.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.videorotator.MainActivity
import com.videorotator.R

/** 「转换完成通知」：设置页开关之前没有任何实际行为 */
object Notifier {

    private const val CHANNEL_ID = "convert_result"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private const val RESULT_ID = 1002

    /** 一批任务全部结束：单个任务显示文件名，多个显示成功 / 失败数量 */
    fun notifyQueueDone(appContext: Context, succeeded: List<String>, failed: List<String>) {
        val context = AppLocale.localized(appContext)
        val (title, text) = when {
            succeeded.size == 1 && failed.isEmpty() -> context.getString(R.string.notif_done) to succeeded[0]
            succeeded.isEmpty() && failed.size == 1 -> context.getString(R.string.notif_failed) to failed[0]
            failed.isEmpty() -> context.getString(R.string.notif_done) to
                context.getString(R.string.snack_done_many, succeeded.size)
            else -> context.getString(R.string.notif_done) to
                context.getString(R.string.snack_done_with_failed, succeeded.size, failed.size)
        }
        // 应用在前台时用户能直接看到进度，不打扰
        if (MainActivity.isInForeground) return
        if (!AppPrefs.getNotifyOnComplete(context) || !canPost(context)) return
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_result), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(RESULT_ID, notification)
        } catch (_: SecurityException) {}
    }
}
