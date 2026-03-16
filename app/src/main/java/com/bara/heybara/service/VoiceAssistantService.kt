package com.bara.heybara.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bara.heybara.BaraApp
import com.bara.heybara.R
import com.bara.heybara.config.SystemMessages
import com.bara.heybara.data.voice.AndroidTtsEngine
import com.bara.heybara.data.voice.SoundPoolBeepPlayer
import com.bara.heybara.data.voice.SherpaSpeechRecognizer
import com.bara.heybara.domain.session.SessionState
import com.bara.heybara.domain.session.VoiceSession
import com.bara.heybara.domain.voice.WakeWordDetector
import com.bara.heybara.ui.OverlayBubbleView

class VoiceAssistantService : Service() {

    private var wakeWordDetector: WakeWordDetector? = null
    private var session: VoiceSession? = null
    private var overlay: OverlayBubbleView? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            1,
            buildNotification("대기 중 — \"헤이 바라\"로 호출"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        startWakeWordDetection()
    }

    private fun startWakeWordDetection() {
        // TODO: Porcupine access key와 모델 경로 설정 (BuildConfig에서 가져올 예정)
        wakeWordDetector?.start {
            onWakeWordDetected()
        }
    }

    private fun onWakeWordDetected() {
        wakeWordDetector?.stop()
        updateNotification("듣고 있어요...")

        // 앱이 포그라운드가 아니면 오버레이 표시
        if (!isAppInForeground()) {
            overlay = OverlayBubbleView(this)
            overlay?.show()
        }

        val recognizer = SherpaSpeechRecognizer(getModelDir())
        val tts = AndroidTtsEngine(this)
        val beep = SoundPoolBeepPlayer(this)

        session = VoiceSession(recognizer, tts, beep).apply {
            onStateChanged = { state ->
                when (state) {
                    SessionState.IDLE -> {
                        overlay?.dismiss()
                        overlay = null
                        updateNotification("대기 중 — \"헤이 바라\"로 호출")
                        startWakeWordDetection()
                    }
                    SessionState.LISTENING -> {
                        overlay?.updateState(state)
                        updateNotification("듣고 있어요...")
                    }
                    SessionState.PROCESSING -> {
                        updateNotification("처리 중...")
                    }
                    SessionState.CONFIRMING -> {
                        overlay?.updateState(state)
                        updateNotification("확인 대기 중...")
                    }
                }
            }
            onSpeechResult = { text -> this@VoiceAssistantService.onSpeechResult(text) }
        }
        session?.onWakeWordDetected()
    }

    // MVP: 인식된 텍스트를 에코백
    private fun onSpeechResult(text: String) {
        overlay?.updateState(SessionState.PROCESSING, text)
        val echoText = "${text}${SystemMessages.ECHO_PREFIX}"
        session?.speakAndEnd(echoText)
    }

    private fun getModelDir(): String {
        return "${filesDir.absolutePath}/models/stt"
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, BaraApp.CHANNEL_ID)
            .setContentTitle("Hey Bara")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1, notification)
    }

    private fun isAppInForeground(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val tasks = manager.appTasks
        return tasks.isNotEmpty() && tasks[0].taskInfo.isVisible
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeWordDetector?.release()
        session?.endSession()
        super.onDestroy()
    }
}
