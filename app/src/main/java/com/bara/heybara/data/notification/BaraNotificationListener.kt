package com.bara.heybara.data.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import android.app.RemoteInput
import java.util.LinkedList

data class NotificationInfo(
    val appName: String,
    val title: String,
    val content: String,
    val time: Long
)

class BaraNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        Log.d(TAG, "NotificationListener 연결됨")
    }

    override fun onListenerDisconnected() {
        instance = null
        Log.d(TAG, "NotificationListener 연결 해제")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 알림 히스토리 저장 (자기 앱 제외)
        if (sbn.packageName != applicationContext.packageName) {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val content = extras.getCharSequence("android.text")?.toString() ?: ""
            if (title.isNotBlank() || content.isNotBlank()) {
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    sbn.packageName
                }
                val info = NotificationInfo(appName, title, content, sbn.postTime)
                synchronized(notificationHistory) {
                    notificationHistory.addFirst(info)
                    if (notificationHistory.size > MAX_HISTORY_SIZE) {
                        notificationHistory.removeLast()
                    }
                }
                Log.d(TAG, "알림 저장: $appName - $title")
            }
        }

        // 카카오톡 답장 Action 저장
        if (sbn.packageName != KAKAO_PACKAGE) return
        val roomName = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: return
        val action = findReplyAction(sbn.notification)
        if (action != null) {
            replyActions[roomName] = action
            Log.d(TAG, "카카오톡 답장 Action 저장: $roomName")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 알림이 제거되어도 Action은 유지 (답장 가능하도록)
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        // 1. notification.actions에서 RemoteInput 찾기
        notification.actions?.forEach { action ->
            if (action.remoteInputs?.isNotEmpty() == true) return action
        }
        // 2. WearableExtender에서 찾기 (일부 카톡 버전)
        try {
            val wearableActions = Notification.WearableExtender(notification).actions
            wearableActions.forEach { action ->
                if (action.remoteInputs?.isNotEmpty() == true) return action
            }
        } catch (e: Exception) {
            Log.w(TAG, "WearableExtender 파싱 실패 (무시)", e)
        }
        return null
    }

    companion object {
        private const val TAG = "BaraNotification"
        private const val KAKAO_PACKAGE = "com.kakao.talk"
        private const val MAX_HISTORY_SIZE = 50

        private var instance: BaraNotificationListener? = null

        // 최근 알림 히스토리 (최신순)
        private val notificationHistory = LinkedList<NotificationInfo>()

        // 채팅방 이름 → 답장 Action
        val replyActions = mutableMapOf<String, Notification.Action>()

        fun isEnabled(context: Context): Boolean {
            val packages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return packages.contains(context.packageName)
        }

        fun getAvailableRooms(): List<String> = replyActions.keys.toList()

        // 카카오톡 메시지 전송
        fun sendKakaoMessage(context: Context, roomName: String, message: String): Result<Unit> {
            // 채팅방 매칭: 정확 매치 → 부분 매치
            val action = replyActions[roomName]
                ?: replyActions.entries.firstOrNull { it.key.contains(roomName) }?.value
                ?: return Result.failure(Exception("'$roomName' 채팅방을 찾을 수 없습니다. 최근 카톡 알림이 필요합니다."))

            val remoteInputs = action.remoteInputs
                ?: return Result.failure(Exception("답장 기능을 찾을 수 없습니다."))

            return try {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(remoteInputs[0].resultKey, message)
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(context, 0, intent)
                Log.d(TAG, "카카오톡 전송: $roomName → $message")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "카카오톡 전송 실패", e)
                Result.failure(e)
            }
        }

        fun getRecentNotifications(count: Int = 10): List<NotificationInfo> {
            synchronized(notificationHistory) {
                return notificationHistory.take(count)
            }
        }

        fun getActiveNotificationList(context: Context): List<NotificationInfo>? {
            val sbn: Array<StatusBarNotification> = instance?.activeNotifications ?: return null
            val pm = context.packageManager
            return sbn.mapNotNull { notification ->
                val extras = notification.notification.extras
                val title = extras.getCharSequence("android.title")?.toString() ?: ""
                val content = extras.getCharSequence("android.text")?.toString() ?: ""
                if (notification.packageName == context.packageName) return@mapNotNull null
                if (title.isBlank() && content.isBlank()) return@mapNotNull null

                val appName = try {
                    val appInfo = pm.getApplicationInfo(notification.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    notification.packageName
                }

                NotificationInfo(appName = appName, title = title, content = content, time = notification.postTime)
            }.sortedByDescending { it.time }
        }
    }
}
