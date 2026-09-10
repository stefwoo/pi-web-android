package dev.piweb.android.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 前台保活服务：当用户选中会话后启动，保持后台常驻并展示通知，
 * 避免移动端在锁屏 / 切后台时 WebSocket 长连接被系统回收。
 */
class PiKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Pi Agent"
        startAsForeground(label)
        return START_STICKY
    }

    private fun startAsForeground(label: String) {
        NotificationHelper.ensureChannel(this)

        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Pi Agent 保活中")
            .setContentText("会话：$label")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_LABEL = "extra_label"

        fun start(context: Context, label: String) {
            val intent = Intent(context, PiKeepAliveService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PiKeepAliveService::class.java))
        }
    }
}