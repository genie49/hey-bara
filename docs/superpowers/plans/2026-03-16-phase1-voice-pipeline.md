# Phase 1: Voice Pipeline MVP — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the end-to-end voice loop: wake word detection → beep → STT → TTS echo-back, with on-demand session management and overlay UI.

**Architecture:** A single ForegroundService (`BaraService`) runs Porcupine for wake word detection. On detection, it creates a `VoiceSession` that loads Sherpa-ONNX (STT) and TTS on-demand. The session handles the IDLE→LISTENING→PROCESSING→IDLE state machine. When the app is not in foreground, an overlay bubble shows the current state. All voice components are accessed through interfaces for testability.

**Tech Stack:** Kotlin, Android Studio, Porcupine SDK, Sherpa-ONNX AAR, Android TTS (Supertonic 2 deferred to Phase 5), Jetpack Compose for UI, Room (for later phases), Coroutines.

**Note on TTS:** Phase 1 uses Android's built-in TTS as the primary engine. Supertonic 2 integration is deferred to Phase 5 (Stabilization) since its Android SDK maturity needs validation. The `TtsEngine` interface allows swapping later without code changes.

---

## File Structure

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── res/
│   │   ├── raw/beep.wav
│   │   └── xml/accessibility_config.xml
│   └── java/com/bara/heybara/
│       ├── BaraApp.kt                          # Application class
│       ├── service/
│       │   └── BaraService.kt                  # ForegroundService
│       ├── voice/
│       │   ├── WakeWordDetector.kt             # Porcupine wrapper
│       │   ├── SpeechRecognizer.kt             # Sherpa-ONNX wrapper
│       │   ├── TtsEngine.kt                    # TTS interface + Android TTS impl
│       │   └── BeepPlayer.kt                   # SoundPool beep player
│       ├── session/
│       │   ├── SessionState.kt                 # State enum (IDLE, LISTENING, PROCESSING, CONFIRMING)
│       │   └── VoiceSession.kt                 # On-demand session lifecycle
│       ├── ui/
│       │   ├── MainActivity.kt                 # Main screen (Compose)
│       │   ├── MainViewModel.kt                # ViewModel for state
│       │   ├── OverlayBubbleView.kt            # Floating overlay
│       │   └── theme/
│       │       └── Theme.kt                    # Color/typography definitions
│       └── config/
│           └── ResponseStrings.kt              # Centralized TTS strings
├── src/test/java/com/bara/heybara/
│   ├── session/
│   │   └── VoiceSessionTest.kt                 # State machine logic tests
│   └── config/
│       └── ResponseStringsTest.kt              # String completeness test
└── src/androidTest/java/com/bara/heybara/
    └── service/
        └── BaraServiceTest.kt                  # Service lifecycle test
```

---

## Chunk 1: Project Scaffolding + Core Interfaces

### Task 1: Create Android Project

**Files:**
- Create: `build.gradle.kts` (project-level)
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create Android project via Android Studio**

Open Android Studio → New Project → Empty Activity (Compose)
- Package: `com.bara.heybara`
- Min SDK: 26 (Android 8)
- Target SDK: 35
- Language: Kotlin
- Build: Kotlin DSL (Gradle)

- [ ] **Step 2: Add dependencies to `app/build.gradle.kts`**

```kotlin
dependencies {
    // Porcupine wake word
    implementation("ai.picovoice:porcupine-android:3.0.3")

    // Sherpa-ONNX STT
    implementation("com.k2fsa.sherpa:sherpa-onnx:1.10.31")

    // Jetpack Compose (already added by template)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
```

- [ ] **Step 3: Configure AndroidManifest.xml permissions**

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

- [ ] **Step 4: Sync Gradle and verify build succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: initialize Android project with dependencies"
```

---

### Task 2: Define Core Interfaces and State Enum

**Files:**
- Create: `app/src/main/java/com/bara/heybara/session/SessionState.kt`
- Create: `app/src/main/java/com/bara/heybara/voice/WakeWordDetector.kt` (interface only)
- Create: `app/src/main/java/com/bara/heybara/voice/SpeechRecognizer.kt` (interface only)
- Create: `app/src/main/java/com/bara/heybara/voice/TtsEngine.kt` (interface only)
- Create: `app/src/main/java/com/bara/heybara/voice/BeepPlayer.kt` (interface only)
- Create: `app/src/main/java/com/bara/heybara/config/ResponseStrings.kt`

- [ ] **Step 1: Create SessionState enum**

```kotlin
// session/SessionState.kt
package com.bara.heybara.session

enum class SessionState {
    IDLE,       // Porcupine only, low power
    LISTENING,  // STT active, waiting for speech
    PROCESSING, // AI parsing command
    CONFIRMING  // Waiting for user confirmation
}
```

- [ ] **Step 2: Create voice component interfaces**

```kotlin
// voice/WakeWordDetector.kt
package com.bara.heybara.voice

interface WakeWordDetector {
    fun start(onDetected: () -> Unit)
    fun stop()
    fun release()
}
```

```kotlin
// voice/SpeechRecognizer.kt
package com.bara.heybara.voice

interface SpeechRecognizer {
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    )
    fun stop()
    fun release()
}
```

```kotlin
// voice/TtsEngine.kt
package com.bara.heybara.voice

interface TtsEngine {
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun release()
}
```

```kotlin
// voice/BeepPlayer.kt
package com.bara.heybara.voice

interface BeepPlayer {
    fun playBeep(onDone: () -> Unit = {})
    fun release()
}
```

- [ ] **Step 3: Create ResponseStrings**

```kotlin
// config/ResponseStrings.kt
package com.bara.heybara.config

object ResponseStrings {
    const val WAKE_PROMPT = "네?"
    const val CANCEL = "취소할게요"
    const val LISTEN_FAIL = "잘 못 들었어요, 다시 말해주세요"
    const val UNDERSTAND_FAIL = "이해하지 못했어요"
    const val NETWORK_FAIL = "인터넷 연결이 안 돼요"
    const val PERMISSION_NEEDED = "이 기능을 사용하려면 권한이 필요해요"
    const val ECHO_PREFIX = "라고 하셨나요?"
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: define core interfaces and session state"
```

---

### Task 3: Write VoiceSession State Machine Tests

**Files:**
- Create: `app/src/test/java/com/bara/heybara/session/VoiceSessionTest.kt`

- [ ] **Step 1: Write failing tests for state transitions**

```kotlin
// session/VoiceSessionTest.kt
package com.bara.heybara.session

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import com.bara.heybara.voice.*

class VoiceSessionTest {

    private lateinit var mockRecognizer: SpeechRecognizer
    private lateinit var mockTts: TtsEngine
    private lateinit var mockBeep: BeepPlayer
    private lateinit var session: VoiceSession

    @Before
    fun setup() {
        mockRecognizer = mock()
        mockTts = mock()
        mockBeep = mock()
        session = VoiceSession(mockRecognizer, mockTts, mockBeep)
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `onWakeWordDetected transitions to LISTENING`() {
        session.onWakeWordDetected()
        assertEquals(SessionState.LISTENING, session.currentState)
    }

    @Test
    fun `onWakeWordDetected plays beep`() {
        session.onWakeWordDetected()
        verify(mockBeep).playBeep(any())
    }

    @Test
    fun `onSpeechRecognized transitions to PROCESSING`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        assertEquals(SessionState.PROCESSING, session.currentState)
    }

    @Test
    fun `onSpeechRecognized stores recognized text`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        assertEquals("테스트", session.lastRecognizedText)
    }

    @Test
    fun `endSession transitions back to IDLE`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        session.endSession()
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `endSession releases recognizer`() {
        session.onWakeWordDetected()
        session.endSession()
        verify(mockRecognizer).release()
    }

    @Test
    fun `cannot transition from IDLE to PROCESSING directly`() {
        session.onSpeechRecognized("test")
        assertEquals(SessionState.IDLE, session.currentState)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test`
Expected: FAIL — `VoiceSession` class does not exist

- [ ] **Step 3: Commit failing tests**

```bash
git add -A
git commit -m "test: add VoiceSession state machine tests (red)"
```

---

### Task 4: Implement VoiceSession State Machine

**Files:**
- Create: `app/src/main/java/com/bara/heybara/session/VoiceSession.kt`

- [ ] **Step 1: Implement VoiceSession**

```kotlin
// session/VoiceSession.kt
package com.bara.heybara.session

import com.bara.heybara.voice.*

class VoiceSession(
    private val recognizer: SpeechRecognizer,
    private val tts: TtsEngine,
    private val beep: BeepPlayer
) {
    var currentState: SessionState = SessionState.IDLE
        private set

    var lastRecognizedText: String = ""
        private set

    var onStateChanged: ((SessionState) -> Unit)? = null

    fun onWakeWordDetected() {
        if (currentState != SessionState.IDLE) return
        transitionTo(SessionState.LISTENING)
        beep.playBeep {
            recognizer.start(
                onPartialResult = { /* update UI */ },
                onFinalResult = { text -> onSpeechRecognized(text) },
                onError = { endSession() }
            )
        }
    }

    fun onSpeechRecognized(text: String) {
        if (currentState != SessionState.LISTENING) return
        lastRecognizedText = text
        recognizer.stop()
        transitionTo(SessionState.PROCESSING)
    }

    fun endSession() {
        recognizer.release()
        transitionTo(SessionState.IDLE)
        lastRecognizedText = ""
    }

    fun speakAndEnd(text: String) {
        tts.speak(text) { endSession() }
    }

    private fun transitionTo(state: SessionState) {
        currentState = state
        onStateChanged?.invoke(state)
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: implement VoiceSession state machine"
```

---

## Chunk 2: Voice Component Implementations

### Task 5: Implement BeepPlayer

**Files:**
- Create: `app/src/main/java/com/bara/heybara/voice/BeepPlayerImpl.kt`
- Add: `app/src/main/res/raw/beep.wav` (200ms short beep sound file)

- [ ] **Step 1: Add beep.wav to res/raw**

Download or generate a short 200ms beep sound. Place at `app/src/main/res/raw/beep.wav`.
Can generate with: `ffmpeg -f lavfi -i "sine=frequency=880:duration=0.2" -ar 44100 app/src/main/res/raw/beep.wav`

- [ ] **Step 2: Implement BeepPlayerImpl**

```kotlin
// voice/BeepPlayerImpl.kt
package com.bara.heybara.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.bara.heybara.R

class BeepPlayerImpl(context: Context) : BeepPlayer {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val beepId: Int = soundPool.load(context, R.raw.beep, 1)

    override fun playBeep(onDone: () -> Unit) {
        soundPool.setOnLoadCompleteListener { _, _, _ ->
            soundPool.play(beepId, 1f, 1f, 1, 0, 1f)
        }
        // SoundPool doesn't have completion callback, use delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onDone()
        }, 300) // beep duration + small buffer
    }

    override fun release() {
        soundPool.release()
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: implement BeepPlayer with SoundPool"
```

---

### Task 6: Implement Android TTS Engine

**Files:**
- Create: `app/src/main/java/com/bara/heybara/voice/AndroidTtsEngine.kt`

- [ ] **Step 1: Implement AndroidTtsEngine**

```kotlin
// voice/AndroidTtsEngine.kt
package com.bara.heybara.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidTtsEngine(context: Context) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                isReady = true
            }
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!isReady) {
            onDone()
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { onDone() }
            override fun onError(id: String?) { onDone() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: implement Android TTS engine with Korean locale"
```

---

### Task 7: Implement Porcupine WakeWordDetector

**Files:**
- Create: `app/src/main/java/com/bara/heybara/voice/PorcupineWakeWordDetector.kt`

- [ ] **Step 1: Implement PorcupineWakeWordDetector**

```kotlin
// voice/PorcupineWakeWordDetector.kt
package com.bara.heybara.voice

import ai.picovoice.porcupine.*

class PorcupineWakeWordDetector(
    private val accessKey: String,
    private val keywordPath: String  // path to custom "헤이 바라" .ppn file
) : WakeWordDetector {

    private var porcupineManager: PorcupineManager? = null

    override fun start(onDetected: () -> Unit) {
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setKeywordPath(keywordPath)
            .setSensitivity(0.5f)
            .build { keywordIndex ->
                if (keywordIndex >= 0) {
                    onDetected()
                }
            }
        porcupineManager?.start()
    }

    override fun stop() {
        porcupineManager?.stop()
    }

    override fun release() {
        porcupineManager?.delete()
        porcupineManager = null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: implement Porcupine wake word detector"
```

---

### Task 8: Implement Sherpa-ONNX SpeechRecognizer

**Files:**
- Create: `app/src/main/java/com/bara/heybara/voice/SherpaSpeechRecognizer.kt`

- [ ] **Step 1: Implement SherpaSpeechRecognizer**

```kotlin
// voice/SherpaSpeechRecognizer.kt
package com.bara.heybara.voice

import com.k2fsa.sherpa.onnx.*

class SherpaSpeechRecognizer(
    private val modelDir: String  // path to Zipformer Korean model files
) : SpeechRecognizer {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var isListening = false

    private fun initRecognizer() {
        val config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder-epoch-99-avg-1.onnx",
                    decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                    joiner = "$modelDir/joiner-epoch-99-avg-1.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                modelType = "zipformer",
            ),
            enableEndpoint = true,
        )
        recognizer = OnlineRecognizer(config)
    }

    override fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (recognizer == null) initRecognizer()
            stream = recognizer?.createStream()
            isListening = true
            // Audio recording and feeding to stream will be handled
            // by BaraService's audio thread
        } catch (e: Exception) {
            onError(e.message ?: "STT initialization failed")
        }
    }

    fun feedAudio(samples: FloatArray) {
        if (!isListening) return
        stream?.acceptWaveform(samples, 16000)
        recognizer?.let { rec ->
            stream?.let { s ->
                while (rec.isReady(s)) {
                    rec.decode(s)
                }
            }
        }
    }

    fun getPartialResult(): String {
        return stream?.let { recognizer?.getResult(it)?.text } ?: ""
    }

    override fun stop() {
        isListening = false
    }

    override fun release() {
        stream = null
        recognizer = null
        isListening = false
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: implement Sherpa-ONNX speech recognizer"
```

---

## Chunk 3: ForegroundService + Overlay UI

### Task 9: Implement BaraService (ForegroundService)

**Files:**
- Create: `app/src/main/java/com/bara/heybara/service/BaraService.kt`
- Create: `app/src/main/java/com/bara/heybara/BaraApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add service declaration)

- [ ] **Step 1: Create BaraApp Application class**

```kotlin
// BaraApp.kt
package com.bara.heybara

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class BaraApp : Application() {

    companion object {
        const val CHANNEL_ID = "hey_bara_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hey Bara 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "음성 비서 대기 상태"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

- [ ] **Step 2: Implement BaraService**

```kotlin
// service/BaraService.kt
package com.bara.heybara.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bara.heybara.BaraApp
import com.bara.heybara.R
import com.bara.heybara.session.SessionState
import com.bara.heybara.session.VoiceSession
import com.bara.heybara.voice.*
import com.bara.heybara.config.ResponseStrings

class BaraService : Service() {

    private var wakeWordDetector: WakeWordDetector? = null
    private var session: VoiceSession? = null

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
        // TODO: Initialize with actual Porcupine access key and model path
        // For MVP testing, this will be configured via build config
        wakeWordDetector?.start {
            onWakeWordDetected()
        }
    }

    private fun onWakeWordDetected() {
        wakeWordDetector?.stop()
        updateNotification("듣고 있어요...")

        val recognizer = SherpaSpeechRecognizer(getModelDir())
        val tts = AndroidTtsEngine(this)
        val beep = BeepPlayerImpl(this)

        session = VoiceSession(recognizer, tts, beep).apply {
            onStateChanged = { state ->
                when (state) {
                    SessionState.IDLE -> {
                        updateNotification("대기 중 — \"헤이 바라\"로 호출")
                        startWakeWordDetection()
                    }
                    SessionState.LISTENING -> updateNotification("듣고 있어요...")
                    SessionState.PROCESSING -> updateNotification("처리 중...")
                    SessionState.CONFIRMING -> updateNotification("확인 대기 중...")
                }
            }
        }
        session?.onWakeWordDetected()
    }

    private fun getModelDir(): String {
        // Model files stored in app's internal storage
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeWordDetector?.release()
        session?.endSession()
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Register in AndroidManifest.xml**

```xml
<application android:name=".BaraApp" ...>
    <service
        android:name=".service.BaraService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
</application>
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: implement BaraService foreground service with wake word flow"
```

---

### Task 10: Implement Theme and Basic MainActivity

**Files:**
- Create: `app/src/main/java/com/bara/heybara/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/bara/heybara/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/bara/heybara/ui/MainActivity.kt`

- [ ] **Step 1: Define theme colors and typography**

```kotlin
// ui/theme/Theme.kt
package com.bara.heybara.ui.theme

import androidx.compose.ui.graphics.Color

object BaraColors {
    val Background = Color(0xFFFFFFFF)
    val CardSurface = Color(0xFFF6F7F8)
    val Coral = Color(0xFFFF6B6B)
    val Green = Color(0xFF22C55E)
    val Indigo = Color(0xFF6366F1)
    val Amber = Color(0xFFFCD34D)
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF6B7280)
    val TextTertiary = Color(0xFF9CA3AF)
    val TextDisabled = Color(0xFFD1D5DB)
    val GreenBadgeBg = Color(0xFFF0FDF4)
    val CoralBadgeBg = Color(0xFFFEF2F2)
    val IndigoBadgeBg = Color(0xFFF0F5FF)
}
```

- [ ] **Step 2: Create MainViewModel**

```kotlin
// ui/MainViewModel.kt
package com.bara.heybara.ui

import androidx.lifecycle.ViewModel
import com.bara.heybara.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String
)

class MainViewModel : ViewModel() {
    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sttPartialText = MutableStateFlow("")
    val sttPartialText: StateFlow<String> = _sttPartialText

    fun updateState(state: SessionState) {
        _sessionState.value = state
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun updatePartialText(text: String) {
        _sttPartialText.value = text
    }
}
```

- [ ] **Step 3: Implement MainActivity with Compose**

```kotlin
// ui/MainActivity.kt
package com.bara.heybara.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.bara.heybara.service.BaraService
import com.bara.heybara.session.SessionState
import com.bara.heybara.ui.theme.BaraColors

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, BaraService::class.java))
        setContent { MainScreen(viewModel) }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.sessionState.collectAsState()
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaraColors.Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Hey Bara",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BaraColors.TextPrimary
                )
                StatusBadge(state)
            }
        }

        // Chat area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("메시지를 입력하세요...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp)
            )
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(44.dp)
                    .background(BaraColors.Coral, CircleShape)
            ) {
                Text("→", color = BaraColors.Background)
            }
        }
    }
}

@Composable
fun StatusBadge(state: SessionState) {
    val (text, color, bgColor) = when (state) {
        SessionState.IDLE -> Triple("IDLE", BaraColors.Green, BaraColors.GreenBadgeBg)
        SessionState.LISTENING -> Triple("듣는 중", BaraColors.Coral, BaraColors.CoralBadgeBg)
        SessionState.PROCESSING -> Triple("처리 중", BaraColors.Indigo, BaraColors.IndigoBadgeBg)
        SessionState.CONFIRMING -> Triple("확인 대기", BaraColors.Indigo, BaraColors.IndigoBadgeBg)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) BaraColors.Coral else BaraColors.CardSurface,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    message.text,
                    color = if (message.isUser) BaraColors.Background else BaraColors.TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    message.timestamp,
                    color = if (message.isUser) BaraColors.Background.copy(alpha = 0.8f) else BaraColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: implement MainActivity with Compose UI"
```

---

### Task 11: Implement Overlay Bubble View

**Files:**
- Create: `app/src/main/java/com/bara/heybara/ui/OverlayBubbleView.kt`

- [ ] **Step 1: Implement OverlayBubbleView**

```kotlin
// ui/OverlayBubbleView.kt
package com.bara.heybara.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bara.heybara.session.SessionState

class OverlayBubbleView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var statusText: TextView? = null
    private var contentText: TextView? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 100
    }

    fun show() {
        if (overlayView != null) return

        overlayView = FrameLayout(context).apply {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                setBackgroundColor(0xFFFFFFFF.toInt())
                elevation = 16f

                statusText = TextView(context).apply {
                    text = "Hey Bara · 듣고 있어요"
                    textSize = 14f
                    setTextColor(0xFF1A1A1A.toInt())
                }
                addView(statusText)

                contentText = TextView(context).apply {
                    text = ""
                    textSize = 16f
                    setTextColor(0xFF1A1A1A.toInt())
                    setPadding(0, 16, 0, 0)
                }
                addView(contentText)
            }
            addView(card)
        }

        windowManager.addView(overlayView, layoutParams)
    }

    fun updateState(state: SessionState, text: String = "") {
        statusText?.text = when (state) {
            SessionState.LISTENING -> "Hey Bara · 듣고 있어요"
            SessionState.CONFIRMING -> "Hey Bara · 확인 대기"
            else -> "Hey Bara"
        }
        contentText?.text = text
    }

    fun dismiss() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: implement overlay bubble view for other app context"
```

---

## Chunk 4: Integration and End-to-End Testing

### Task 12: Wire Everything Together in BaraService

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/service/BaraService.kt`

- [ ] **Step 1: Update BaraService to use overlay and echo-back**

Add overlay management and TTS echo-back to BaraService. When wake word is detected:
1. Check if app is in foreground → update MainActivity UI, else → show overlay
2. After STT completes, TTS echo-back: "OO라고 하셨나요?"
3. End session and return to IDLE

```kotlin
// Add to BaraService.kt - onWakeWordDetected()
private var overlay: OverlayBubbleView? = null

private fun onWakeWordDetected() {
    wakeWordDetector?.stop()
    updateNotification("듣고 있어요...")

    // Show overlay if app is not in foreground
    if (!isAppInForeground()) {
        overlay = OverlayBubbleView(this)
        overlay?.show()
    }

    val recognizer = SherpaSpeechRecognizer(getModelDir())
    val tts = AndroidTtsEngine(this)
    val beep = BeepPlayerImpl(this)

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
    }
    session?.onWakeWordDetected()
}

// MVP: echo-back after speech recognized
private fun onSpeechResult(text: String) {
    overlay?.updateState(SessionState.PROCESSING, text)
    val echoText = "${text}${ResponseStrings.ECHO_PREFIX}"
    session?.speakAndEnd(echoText)
}

private fun isAppInForeground(): Boolean {
    val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
    val tasks = manager.appTasks
    return tasks.isNotEmpty() && tasks[0].taskInfo.isVisible
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: wire overlay and echo-back into BaraService"
```

---

### Task 13: Manual End-to-End Test on Device

- [ ] **Step 1: Prepare model files**

Download Sherpa-ONNX Korean model and push to device:
```bash
# Download model
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/encoder-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/decoder-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/joiner-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/tokens.txt

# Push to device (after app install)
adb push *.onnx /data/data/com.bara.heybara/files/models/stt/
adb push tokens.txt /data/data/com.bara.heybara/files/models/stt/
```

- [ ] **Step 2: Prepare Porcupine wake word**

1. Go to https://console.picovoice.ai
2. Create custom keyword "헤이 바라" (Korean)
3. Download .ppn file
4. Push to device: `adb push hey-bara.ppn /data/data/com.bara.heybara/files/models/wakeword/`
5. Add Picovoice access key to BuildConfig

- [ ] **Step 3: Install and test**

```bash
./gradlew installDebug
```

Test flow:
1. App launches → foreground notification appears "대기 중"
2. Say "헤이 바라" → beep sound plays
3. Say "엄마한테 전화해" → TTS responds "엄마한테 전화해라고 하셨나요?"
4. Returns to IDLE → notification shows "대기 중"
5. Switch to another app → say "헤이 바라" → overlay appears

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: end-to-end testing adjustments"
```

---

### Task 14: Final Cleanup and Phase 1 Complete

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Final commit and tag**

```bash
git add -A
git commit -m "milestone: Phase 1 Voice Pipeline MVP complete"
git tag v0.1.0-mvp
git push origin main --tags
```

Phase 1 delivers:
- ForegroundService with Porcupine wake word detection
- On-demand VoiceSession (IDLE→LISTENING→PROCESSING→IDLE)
- Sherpa-ONNX Korean STT
- Android TTS echo-back
- Overlay bubble for other app context
- Basic Compose UI with status badge and chat bubbles
