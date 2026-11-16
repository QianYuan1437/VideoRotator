package com.videorotator.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.videorotator.MainActivity
import com.videorotator.R
import com.videorotator.utils.AppLocale

/**
 * 转换进行中的前台服务：只负责持有一条常驻通知、保持进程优先级。
 * 转换本身仍在 ConvertViewModel 的协程中执行；没有它时切到后台，
 * 进程会被系统冻结 / 回收，转换中断。
 */
class ConvertService : Service() {

    companion object {
        private const val CHANNEL_ID = "convert_running"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_ACTIVE = "active"

        /** 进行中的任务数变化时调用：>0 启动 / 更新通知，0 停止服务 */
        fun update(context: Context, activeJobs: Int) {
            val intent = Intent(context, ConvertService::class.java).putExtra(EXTRA_ACTIVE, activeJobs)
            try {
                if (activeJobs > 0) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            } catch (_: Exception) {
                // 后台启动前台服务受限（Android 12+）等情况：转换照常进行，只是没有保活
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = intent?.getIntExtra(EXTRA_ACTIVE, 1) ?: 1
        val res = AppLocale.localized(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(res.getString(R.string.notif_running_title))
            .setContentText(res.getString(R.string.notif_running_text, active))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
