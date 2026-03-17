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
        if (sbn.packageName != KAKAO_PACKAGE) return

        val roomName = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: return

        // RemoteInput이 있는 Action 찾기 (답장 기능)
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
        } catch (_: Exception) {}
        return null
    }

    companion object {
        private const val TAG = "BaraNotification"
        private const val KAKAO_PACKAGE = "com.kakao.talk"

        private var instance: BaraNotificationListener? = null

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
