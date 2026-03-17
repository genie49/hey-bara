package com.bara.heybara.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bara.heybara.BaraApp
import com.bara.heybara.R
import com.bara.heybara.data.action.DeviceContactResolver
import com.bara.heybara.data.auth.GoogleAuthManager
import com.bara.heybara.data.calendar.GoogleCalendarClient
import com.bara.heybara.data.model.ModelInstaller
import com.bara.heybara.data.agent.KoogAgentEngine
import com.bara.heybara.data.tasks.GoogleTasksClient
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.data.voice.AndroidTtsEngine
import com.bara.heybara.data.voice.SoundPoolBeepPlayer
import com.bara.heybara.data.voice.SherpaSpeechRecognizer
import com.bara.heybara.domain.agent.AgentEngine
import com.bara.heybara.domain.session.SessionState
import com.bara.heybara.domain.session.VoiceSession
import com.bara.heybara.domain.voice.WakeWordDetector
import com.bara.heybara.data.voice.SherpaKwsWakeWordDetector
import com.bara.heybara.ui.OverlayBubbleView
import kotlinx.coroutines.*
import java.io.File

class VoiceAssistantService : Service() {

    companion object {
        private const val TAG = "VoiceAssistantService"
    }

    private var wakeWordDetector: WakeWordDetector? = null
    private var session: VoiceSession? = null
    private var overlay: OverlayBubbleView? = null
    private var agentEngine: AgentEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // API Key + ContactResolver로 AgentEngine 초기화
        val apiKey = SecurePreferences(this).getGeminiApiKey()
        if (apiKey != null) {
            val contactResolver = DeviceContactResolver(this)
            GoogleAuthManager.restore(this)
            val engine = KoogAgentEngine(apiKey, contactResolver)
            engine.setContext(this, apiKey)
            if (GoogleAuthManager.isAuthenticated()) {
                engine.setGoogleClients(GoogleCalendarClient(this), GoogleTasksClient(this))
            }
            agentEngine = engine
            Log.d(TAG, "KoogAgentEngine 초기화 완료 (연락처 검색 활성화)")
        } else {
            Log.w(TAG, "API Key 없음, AgentEngine 미초기화")
        }

        startForeground(
            1,
            buildNotification("대기 중 — \"헤이 바라\"로 호출"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        startWakeWordDetection()
    }

    private fun startWakeWordDetection() {
        val kwsDir = File(filesDir, "models/kws")
        if (!ModelInstaller.isKwsInstalled(this)) {
            Log.w(TAG, "KWS 모델 미설치, 웨이크워드 감지 건너뜀")
            return
        }

        val sensitivity = SecurePreferences(this).getWakeWordSensitivity()
        wakeWordDetector = SherpaKwsWakeWordDetector(this, kwsDir.absolutePath, sensitivity)
        wakeWordDetector?.start {
            onWakeWordDetected()
        }
    }

    private fun onWakeWordDetected() {
        wakeWordDetector?.stop()

        // STT 모델 미설치 시 무시
        if (!ModelInstaller.isSttInstalled(this)) {
            updateNotification("음성 모델 미설치")
            startWakeWordDetection()
            return
        }

        updateNotification("듣고 있어요...")

        // 앱이 포그라운드가 아니면 오버레이 표시
        if (!isAppInForeground()) {
            overlay = OverlayBubbleView(this)
            overlay?.show()
        }

        val recognizer = SherpaSpeechRecognizer(getModelDir())
        val tts = if (ModelInstaller.isTtsInstalled(this)) {
            com.bara.heybara.data.tts.SupertonicTtsEngine(
                java.io.File(filesDir, "models/tts").absolutePath
            ).also { it.loadModels() }
        } else {
            AndroidTtsEngine(this)
        }
        val beep = SoundPoolBeepPlayer(this)

        session = VoiceSession(recognizer, tts, beep, agentEngine).apply {
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

    // STT 결과 수신 → AgentEngine으로 처리
    private fun onSpeechResult(text: String) {
        overlay?.updateState(SessionState.PROCESSING, text)
        serviceScope.launch {
            session?.processWithAgent()
        }
    }

    private fun getModelDir(): String {
        return File(filesDir, "models/stt").absolutePath
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, BaraApp.CHANNEL_ID)
            .setContentTitle("Hey Bara")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
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
        agentEngine?.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
