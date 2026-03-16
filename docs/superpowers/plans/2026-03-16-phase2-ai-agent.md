# Phase 2: AI Agent Connection — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** STT 텍스트를 Koog Agent + Gemini API로 전달하여 명령을 파싱하고, CONFIRMING 상태를 통한 확인/취소 흐름을 구현한다. Actions는 로그만 출력.

**Architecture:** AgentEngine 인터페이스를 domain에, KoogAgentEngine 구현체를 data에 배치하는 Clean Architecture. VoiceSession에 AgentEngine을 주입하여 PROCESSING→CONFIRMING 상태 전이를 추가. SettingsActivity에서 Gemini API Key를 EncryptedSharedPreferences로 안전 저장.

**Tech Stack:** Koog 0.6.4 (JetBrains), Gemini API (gemini-3.1-flash-lite-preview), Jetpack Security (EncryptedSharedPreferences), Jetpack Compose, Coroutines.

---

## File Structure

```
app/
├── build.gradle.kts                                          (수정: 의존성 추가)
├── src/main/
│   ├── AndroidManifest.xml                                   (수정: SettingsActivity 등록)
│   └── java/com/bara/heybara/
│       ├── domain/
│       │   ├── agent/
│       │   │   ├── AgentEngine.kt                            (생성: 인터페이스)
│       │   │   └── AgentResponse.kt                          (생성: 응답 모델)
│       │   └── session/
│       │       └── VoiceSession.kt                           (수정: AgentEngine 연결 + CONFIRMING)
│       ├── data/
│       │   ├── agent/
│       │   │   └── KoogAgentEngine.kt                        (생성: Koog + Gemini 구현체)
│       │   └── settings/
│       │       └── SecurePreferences.kt                      (생성: 암호화 저장소)
│       ├── config/
│       │   └── SystemMessages.kt                             (수정: 에러 메시지 추가)
│       ├── service/
│       │   └── VoiceAssistantService.kt                      (수정: AgentEngine 초기화)
│       ├── ui/
│       │   └── SettingsActivity.kt                           (생성: API Key 설정 화면)
│       └── MainActivity.kt                                   (수정: 설정 아이콘 + API Key 체크)
├── src/test/java/com/bara/heybara/
│   └── domain/session/
│       └── VoiceSessionTest.kt                               (수정: AgentEngine 테스트 추가)
```

---

## Chunk 1: Domain 레이어 + 테스트

### Task 1: AgentEngine 인터페이스 + AgentResponse 모델 생성

**Files:**
- Create: `app/src/main/java/com/bara/heybara/domain/agent/AgentEngine.kt`
- Create: `app/src/main/java/com/bara/heybara/domain/agent/AgentResponse.kt`

- [ ] **Step 1: AgentEngine 인터페이스 생성**

```kotlin
// domain/agent/AgentEngine.kt
package com.bara.heybara.domain.agent

interface AgentEngine {
    suspend fun process(text: String): AgentResponse
    fun release()
}
```

- [ ] **Step 2: AgentResponse + AgentAction 모델 생성**

```kotlin
// domain/agent/AgentResponse.kt
package com.bara.heybara.domain.agent

data class AgentResponse(
    val text: String,
    val action: AgentAction?,
    val requiresConfirmation: Boolean
)

sealed class AgentAction {
    data class Call(val contact: String) : AgentAction()
}
```

- [ ] **Step 3: 빌드 확인**

Run: `⌘F9` (Build → Make Project)
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/bara/heybara/domain/agent/
git commit -m "feat: AgentEngine 인터페이스 + AgentResponse 모델 정의"
```

---

### Task 2: VoiceSession에 AgentEngine 연결 — 테스트 먼저

**Files:**
- Modify: `app/src/test/java/com/bara/heybara/domain/session/VoiceSessionTest.kt`
- Modify: `app/src/main/java/com/bara/heybara/domain/session/VoiceSession.kt`

- [ ] **Step 1: VoiceSession 생성자에 AgentEngine 추가 (테스트 먼저 수정)**

VoiceSessionTest.kt의 setup에 mock AgentEngine 추가:

```kotlin
import com.bara.heybara.domain.agent.*
import kotlinx.coroutines.test.runTest

// 기존 멤버에 추가
private lateinit var mockAgent: AgentEngine

@Before
fun setup() {
    mockRecognizer = mock()
    mockTts = mock()
    mockBeep = mock()
    mockAgent = mock()
    session = VoiceSession(mockRecognizer, mockTts, mockBeep, mockAgent)
}
```

- [ ] **Step 2: AgentEngine 관련 테스트 추가**

```kotlin
@Test
fun `processWithAgent transitions to CONFIRMING when confirmation required`() = runTest {
    whenever(mockAgent.process("엄마한테 전화해")).thenReturn(
        AgentResponse("엄마한테 전화를 걸까요?", AgentAction.Call("엄마"), true)
    )
    session.onWakeWordDetected()
    session.onSpeechRecognized("엄마한테 전화해")
    session.processWithAgent()
    assertEquals(SessionState.CONFIRMING, session.currentState)
}

@Test
fun `processWithAgent goes to IDLE when no confirmation needed`() = runTest {
    whenever(mockAgent.process("오늘 날씨")).thenReturn(
        AgentResponse("오늘 서울은 맑아요", null, false)
    )
    session.onWakeWordDetected()
    session.onSpeechRecognized("오늘 날씨")
    session.processWithAgent()
    verify(mockTts).speak(eq("오늘 서울은 맑아요"), any())
}

@Test
fun `confirmAction executes action and ends session`() {
    session.onWakeWordDetected()
    session.onSpeechRecognized("엄마한테 전화해")
    // 수동으로 CONFIRMING 상태 + action 설정을 위해 processWithAgent 호출 필요
    // 실제로는 processWithAgent 이후 confirmAction 호출
    session.confirmAction()
    assertEquals(SessionState.IDLE, session.currentState)
}

@Test
fun `cancelAction returns to IDLE with cancel message`() {
    session.onWakeWordDetected()
    session.onSpeechRecognized("테스트")
    session.cancelAction()
    verify(mockTts).speak(eq("취소할게요"), any())
}

@Test
fun `processWithAgent handles error and returns to IDLE`() = runTest {
    whenever(mockAgent.process(any())).thenThrow(RuntimeException("Network error"))
    session.onWakeWordDetected()
    session.onSpeechRecognized("테스트")
    session.processWithAgent()
    assertEquals(SessionState.IDLE, session.currentState)
}
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

Expected: FAIL — VoiceSession 생성자 변경 필요, processWithAgent/confirmAction/cancelAction 미존재

- [ ] **Step 4: VoiceSession 구현 수정**

```kotlin
// domain/session/VoiceSession.kt
package com.bara.heybara.domain.session

import com.bara.heybara.domain.agent.*
import com.bara.heybara.domain.voice.*

class VoiceSession(
    private val recognizer: SpeechRecognizer,
    private val tts: TtsEngine,
    private val beep: BeepPlayer,
    private val agent: AgentEngine? = null  // Phase 1 호환을 위해 optional
) {
    var currentState: SessionState = SessionState.IDLE
        private set

    var lastRecognizedText: String = ""
        private set

    var onStateChanged: ((SessionState) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null

    // 현재 대기 중인 AgentResponse (CONFIRMING 상태에서 사용)
    private var pendingResponse: AgentResponse? = null

    fun onWakeWordDetected() {
        if (currentState != SessionState.IDLE) return
        transitionTo(SessionState.LISTENING)
        beep.playBeep {
            recognizer.start(
                onPartialResult = { /* UI 업데이트 */ },
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
        onSpeechResult?.invoke(text)
    }

    // Phase 2: AgentEngine으로 명령 처리
    suspend fun processWithAgent() {
        if (currentState != SessionState.PROCESSING) return
        if (agent == null) {
            // Phase 1 폴백: 에코백
            speakAndEnd("${lastRecognizedText}라고 하셨나요?")
            return
        }
        try {
            val response = agent.process(lastRecognizedText)
            pendingResponse = response
            if (response.requiresConfirmation) {
                transitionTo(SessionState.CONFIRMING)
                tts.speak(response.text) {}
            } else {
                tts.speak(response.text) { endSession() }
            }
        } catch (e: Exception) {
            tts.speak("이해하지 못했어요") { endSession() }
        }
    }

    // CONFIRMING 상태에서 확인
    fun confirmAction() {
        pendingResponse?.action?.let { action ->
            // Phase 2: 로그만 출력
            android.util.Log.d("AgentAction", "Action confirmed: $action")
        }
        pendingResponse = null
        endSession()
    }

    // CONFIRMING 상태에서 취소
    fun cancelAction() {
        pendingResponse = null
        tts.speak("취소할게요") { endSession() }
    }

    fun endSession() {
        recognizer.release()
        transitionTo(SessionState.IDLE)
        lastRecognizedText = ""
        pendingResponse = null
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

> **주의:** `android.util.Log`는 유닛 테스트에서 사용 불가. 테스트에서는 confirmAction의 상태 전이만 검증.

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/bara/heybara/domain/session/VoiceSession.kt
git add app/src/test/java/com/bara/heybara/domain/session/VoiceSessionTest.kt
git commit -m "feat: VoiceSession에 AgentEngine 연결 + CONFIRMING 상태 구현"
```

---

### Task 3: SystemMessages 에러 메시지 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/config/SystemMessages.kt`

- [ ] **Step 1: 에러 메시지 추가**

```kotlin
// config/SystemMessages.kt
package com.bara.heybara.config

object SystemMessages {
    // 시스템 레벨 메시지 (에이전트 개입 전)
    const val LISTEN_FAIL = "잘 못 들었어요, 다시 말해주세요"
    const val NETWORK_FAIL = "인터넷 연결이 안 돼요"
    const val PERMISSION_NEEDED = "이 기능을 사용하려면 권한이 필요해요"
    const val UNDERSTAND_FAIL = "이해하지 못했어요"
    const val API_KEY_INVALID = "API 키를 확인해주세요"
    const val API_KEY_NEEDED = "설정에서 API Key를 입력해주세요"
    const val ACTION_CANCELLED = "취소할게요"

    // 임시 MVP 문자열 (추후 에이전트 응답으로 대체)
    const val ECHO_PREFIX = "라고 하셨나요?"
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/bara/heybara/config/SystemMessages.kt
git commit -m "feat: Phase 2 에러/시스템 메시지 추가"
```

---

## Chunk 2: Data 레이어 (SecurePreferences + KoogAgentEngine)

### Task 4: 의존성 추가

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: libs.versions.toml에 버전 추가**

```toml
# [versions] 섹션에 추가
koog = "0.6.4"
securityCrypto = "1.1.0-alpha06"

# [libraries] 섹션에 추가
koog-agents = { group = "ai.koog", name = "koog-agents", version.ref = "koog" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

- [ ] **Step 2: app/build.gradle.kts에 의존성 추가**

```kotlin
// dependencies 블록에 추가
// Koog AI Agent
implementation(libs.koog.agents)

// Jetpack Security (EncryptedSharedPreferences)
implementation(libs.security.crypto)
```

- [ ] **Step 3: Gradle Sync 확인**

Android Studio에서 Sync Now 클릭.
Expected: Sync 성공

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: Koog, Jetpack Security 의존성 추가"
```

---

### Task 5: SecurePreferences 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt`

- [ ] **Step 1: SecurePreferences 구현**

```kotlin
// data/settings/SecurePreferences.kt
package com.bara.heybara.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hey_bara_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getGeminiApiKey(): String? {
        return prefs.getString(KEY_GEMINI_API, null)
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt
git commit -m "feat: SecurePreferences (EncryptedSharedPreferences) 구현"
```

---

### Task 6: KoogAgentEngine 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: KoogAgentEngine 구현**

```kotlin
// data/agent/KoogAgentEngine.kt
package com.bara.heybara.data.agent

import android.util.Log
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.clients.google.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLModel
import com.bara.heybara.domain.agent.AgentAction
import com.bara.heybara.domain.agent.AgentEngine
import com.bara.heybara.domain.agent.AgentResponse
import kotlinx.serialization.Serializable

class KoogAgentEngine(
    private val apiKey: String
) : AgentEngine {

    companion object {
        private const val TAG = "KoogAgentEngine"
        private const val MODEL_ID = "gemini-3.1-flash-lite-preview"
        private const val SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.
"""
    }

    // 전화 걸기 Tool 정의
    object MakeCallTool : SimpleTool<MakeCallTool.Args>(
        argsSerializer = Args.serializer(),
        name = "make_call",
        description = "연락처에게 전화를 건다"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("전화할 상대 이름")
            val contact: String
        )

        override suspend fun execute(args: Args): String {
            // Phase 2: 로그만 출력
            Log.d("AgentTool", "Call requested: contact=${args.contact}")
            return "${args.contact}한테 전화를 걸게요"
        }
    }

    private val agent = AIAgent(
        promptExecutor = simpleGoogleAIExecutor(apiKey),
        systemPrompt = SYSTEM_PROMPT.trimIndent(),
        llmModel = LLModel(MODEL_ID),
        toolRegistry = ToolRegistry {
            tool(MakeCallTool)
        },
        maxIterations = 5
    )

    override suspend fun process(text: String): AgentResponse {
        Log.d(TAG, "Processing: $text")
        return try {
            val result = agent.run(text)
            Log.d(TAG, "Result: $result")

            // Tool이 호출됐는지 판단 (결과 텍스트에서 추론)
            // TODO: Koog API에서 tool call 정보를 직접 가져오는 방법 확인 필요
            if (result.contains("전화를 걸")) {
                val contact = extractContact(text)
                AgentResponse(
                    text = "${contact}한테 전화를 걸까요?",
                    action = AgentAction.Call(contact),
                    requiresConfirmation = true
                )
            } else {
                AgentResponse(
                    text = result,
                    action = null,
                    requiresConfirmation = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent error", e)
            throw e
        }
    }

    // 텍스트에서 연락처 이름 추출 (간단한 패턴)
    private fun extractContact(text: String): String {
        val pattern = "(.+?)한테|(.+?)에게".toRegex()
        val match = pattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() && it != match.value } ?: "상대방"
    }

    override fun release() {
        // Koog agent 정리
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

> Koog import 경로가 정확하지 않을 수 있음. 빌드 에러 시 실제 Koog 라이브러리의 패키지 구조에 맞춰 수정.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: KoogAgentEngine (Koog + Gemini API) 구현"
```

---

## Chunk 3: UI 레이어 (SettingsActivity + MainActivity 수정)

### Task 7: SettingsActivity 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: SettingsActivity 구현 (Compose)**

```kotlin
// ui/SettingsActivity.kt
package com.bara.heybara.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.ui.theme.BaraColors
import com.bara.heybara.ui.theme.HeyBaraTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val securePrefs = SecurePreferences(this)
        setContent {
            HeyBaraTheme {
                SettingsScreen(
                    securePrefs = securePrefs,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(securePrefs: SecurePreferences, onBack: () -> Unit) {
    val existingKey = securePrefs.getGeminiApiKey()
    var apiKeyInput by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val hasSavedKey = existingKey != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        // 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Gemini API Key 섹션
        Text("Gemini API Key", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (hasSavedKey) {
            val masked = existingKey!!.take(4) + "..." + existingKey.takeLast(4)
            Text("현재 저장된 키: $masked", color = BaraColors.TextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            placeholder = { Text("API Key 입력") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (apiKeyInput.isNotBlank()) {
                    securePrefs.setGeminiApiKey(apiKeyInput.trim())
                    apiKeyInput = ""
                    savedMessage = "API Key가 저장되었습니다"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("저장")
        }

        savedMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = BaraColors.Green, fontSize = 14.sp)
        }

        if (hasSavedKey) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    securePrefs.clearGeminiApiKey()
                    savedMessage = "API Key가 삭제되었습니다"
                }
            ) {
                Text("API Key 삭제", color = BaraColors.Coral)
            }
        }
    }
}
```

- [ ] **Step 2: AndroidManifest.xml에 SettingsActivity 등록**

```xml
<!-- <application> 안에 추가 -->
<activity
    android:name=".ui.SettingsActivity"
    android:exported="false" />
```

- [ ] **Step 3: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: SettingsActivity (API Key 입력/저장/삭제)"
```

---

### Task 8: MainActivity에 설정 아이콘 + API Key 체크 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/MainActivity.kt`

- [ ] **Step 1: 헤더에 설정 아이콘 추가**

MainActivity.kt의 `MainScreen` 헤더 Row에 설정 아이콘 추가:

```kotlin
// import 추가
import android.content.Intent
import com.bara.heybara.ui.SettingsActivity

// 헤더 Row의 horizontalArrangement을 SpaceBetween으로 유지하고
// 오른쪽에 설정 아이콘 추가
IconButton(onClick = {
    context.startActivity(Intent(context, SettingsActivity::class.java))
}) {
    Text("⚙", fontSize = 20.sp)
}
```

- [ ] **Step 2: API Key 미설정 시 안내 표시**

MainScreen에서 API Key가 없으면 채팅 영역에 안내 메시지 표시:

```kotlin
// MainScreen 내부, LazyColumn 대신 조건부 표시
val context = LocalContext.current
val securePrefs = remember { SecurePreferences(context) }
val hasApiKey = securePrefs.getGeminiApiKey() != null

if (!hasApiKey) {
    // API Key 미설정 안내
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("API Key가 설정되지 않았습니다", color = BaraColors.TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }) {
                Text("설정으로 이동")
            }
        }
    }
} else {
    // 기존 LazyColumn
}
```

- [ ] **Step 3: API Key 있을 때만 서비스 시작**

`startVoiceService()`를 API Key 존재 시에만 호출:

```kotlin
private fun startVoiceService() {
    val securePrefs = SecurePreferences(this)
    if (securePrefs.getGeminiApiKey() != null) {
        startService(Intent(this, VoiceAssistantService::class.java))
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bara/heybara/MainActivity.kt
git commit -m "feat: 설정 아이콘 + API Key 미설정 시 안내 표시"
```

---

## Chunk 4: Service 연결 + E2E 테스트

### Task 9: VoiceAssistantService에 AgentEngine 연결

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt`

- [ ] **Step 1: AgentEngine 초기화 + processWithAgent 호출**

```kotlin
// VoiceAssistantService에 추가
import com.bara.heybara.data.agent.KoogAgentEngine
import com.bara.heybara.data.settings.SecurePreferences
import kotlinx.coroutines.*

class VoiceAssistantService : Service() {
    // 기존 필드에 추가
    private var agentEngine: AgentEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // API Key로 AgentEngine 초기화
        val apiKey = SecurePreferences(this).getGeminiApiKey()
        if (apiKey != null) {
            agentEngine = KoogAgentEngine(apiKey)
        }

        startForeground(...)
        startWakeWordDetection()
    }
}
```

- [ ] **Step 2: onSpeechResult에서 processWithAgent 호출**

기존 에코백 대신 AgentEngine 사용:

```kotlin
private fun onSpeechResult(text: String) {
    overlay?.updateState(SessionState.PROCESSING, text)
    serviceScope.launch {
        session?.processWithAgent()
    }
}
```

- [ ] **Step 3: onDestroy에서 정리**

```kotlin
override fun onDestroy() {
    wakeWordDetector?.release()
    session?.endSession()
    agentEngine?.release()
    serviceScope.cancel()
    super.onDestroy()
}
```

- [ ] **Step 4: VoiceSession 생성 시 agentEngine 전달**

`onWakeWordDetected()`에서 VoiceSession 생성 부분 수정:

```kotlin
session = VoiceSession(recognizer, tts, beep, agentEngine).apply { ... }
```

- [ ] **Step 5: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt
git commit -m "feat: VoiceAssistantService에 KoogAgentEngine 연결"
```

---

### Task 10: 에뮬레이터 통합 테스트

- [ ] **Step 1: 에뮬레이터에서 앱 실행**

Run: `⌘R`

확인 사항:
1. API Key 미설정 → "API Key가 설정되지 않았습니다" 표시
2. 설정 아이콘 → SettingsActivity 진입
3. API Key 입력 → 저장 → 마스킹 표시

- [ ] **Step 2: API Key 설정 후 서비스 동작 확인**

1. SettingsActivity에서 Gemini API Key 입력/저장
2. 앱 재시작
3. 상태바에 "대기 중" 알림 표시 확인
4. Logcat에서 KoogAgentEngine 초기화 로그 확인

- [ ] **Step 3: 수정사항 있으면 커밋**

```bash
git add -A
git commit -m "fix: 에뮬레이터 통합 테스트 수정사항"
```

---

### Task 11: 최종 정리

- [ ] **Step 1: 모든 유닛 테스트 통과 확인**

Android Studio에서 VoiceSessionTest 실행 → 전체 통과 확인

- [ ] **Step 2: 최종 커밋**

```bash
git add -A
git commit -m "milestone: Phase 2 AI Agent Connection 완료"
```

Phase 2 결과물:
- AgentEngine 인터페이스 (domain) + KoogAgentEngine 구현 (data)
- Koog + Gemini API Function Calling (CallTool)
- SettingsActivity + SecurePreferences (API Key 관리)
- VoiceSession CONFIRMING 상태 흐름
- VoiceSession 테스트 확장
