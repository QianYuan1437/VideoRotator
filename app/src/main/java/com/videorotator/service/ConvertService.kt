package com.videorotator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.videorotator.MainActivity
import com.videorotator.R
import com.videorotator.utils.AppLocale
import com.videorotator.viewmodel.ConvertEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 转换进行中的前台服务：保持进程存活、CPU 不休眠，并在通知栏显示总进度。
 * 转换本身在进程级的 [ConvertEngine] 中执行，与界面无关：切到后台、关闭界面、
 * 锁屏后任务都会继续，全部完成后服务自行停止。
 */
class ConvertService : Service() {

    companion object {
        private const val CHANNEL_ID = "convert_running"
        private const val NOTIFICATION_ID = 1001

        /**
         * 进行中的任务数变化时调用：>0 时启动服务。停止由服务自己在 startForeground 之后判断 ——
         * 任务瞬间结束时若这里直接 stopService，可能赶在服务调用 startForeground 之前，
         * 系统会因「startForegroundService 后未调用 startForeground」而让应用崩溃。
         */
        fun update(context: Context, activeJobs: Int) {
            if (activeJobs <= 0) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, ConvertService::class.java))
            } catch (_: Exception) {
                // 后台启动前台服务受限（Android 12+）等情况：转换照常进行，只是没有保活
            }
        }
    }

    /** 通知显示的状态：剩余任务数 + 总进度百分比 */
    private data class Status(val active: Int, val percent: Int)

    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var status = Status(1, 0)
    /** 已调用过 startForeground：此后才允许自行停止 */
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val res = AppLocale.localized(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        // 锁屏后 CPU 休眠会让转换停住；超时兜底，防止异常情况下一直持有
        wakeLock = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoRotator:convert")
            ?.apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        scope = MainScope().apply {
            launch {
                ConvertEngine.get(this@ConvertService).jobs
                    .map { list ->
                        val active = list.filter { it.isActive }
                        val percent = if (active.isEmpty()) 100 else (active.sumOf { it.progress.toDouble() } / active.size * 100).toInt()
                        Status(active.size, percent)
                    }
                    .distinctUntilChanged()
                    .collect { s ->
                        status = s
                        if (s.active == 0) {
                            if (foregroundStarted) stopSelf()
                        } else {
                            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
                        }
                    }
            }
        }
    }

    private fun buildNotification(): Notification {
        val res = AppLocale.localized(this)
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(res.getString(R.string.notif_running_title))
            .setContentText(res.getString(R.string.notif_running_text, status.active))
            .setProgress(100, status.percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次 startForegroundService 都必须先 startForeground，再判断是否已无任务
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            foregroundStarted = true
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ConvertEngine.get(this).activeCount.value == 0) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}
