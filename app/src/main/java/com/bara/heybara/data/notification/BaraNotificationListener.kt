package com.bara.heybara.data.notification

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

data class NotificationInfo(
    val appName: String,
    val title: String,
    val content: String,
    val time: Long
)

class BaraNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    companion object {
        private var instance: BaraNotificationListener? = null

        fun isEnabled(context: Context): Boolean {
            val packages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return packages.contains(context.packageName)
        }

        fun getActiveNotificationList(context: Context): List<NotificationInfo>? {
            val sbn: Array<StatusBarNotification> = instance?.activeNotifications ?: return null
            val pm = context.packageManager
            return sbn.mapNotNull { notification ->
                val extras = notification.notification.extras
                val title = extras.getCharSequence("android.title")?.toString() ?: ""
                val content = extras.getCharSequence("android.text")?.toString() ?: ""
                // 자기 자신(Hey Bara) 알림은 제외
                if (notification.packageName == context.packageName) return@mapNotNull null
                // 빈 알림 제외
                if (title.isBlank() && content.isBlank()) return@mapNotNull null

                val appName = try {
                    val appInfo = pm.getApplicationInfo(notification.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    notification.packageName
                }

                NotificationInfo(
                    appName = appName,
                    title = title,
                    content = content,
                    time = notification.postTime
                )
            }.sortedByDescending { it.time }
        }
    }
}
