# Phase 4: Calendar + Tasks Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google Calendar API + Google Tasks API를 연동하여 일정/할일 CRUD 기능을 음성/텍스트 비서에 추가한다.

**Architecture:** GoogleAuthManager(싱글톤)가 OAuth 인증을 관리하고, GoogleCalendarClient/GoogleTasksClient가 REST API를 직접 호출한다. 8개의 Koog SimpleTool이 KoogAgentEngine에 등록되어 LLM이 자연어 명령을 적절한 API 호출로 변환한다.

**Tech Stack:** play-services-auth (GoogleAuthUtil), HttpURLConnection + kotlinx.serialization, Koog SimpleTool

**Spec:** `docs/superpowers/specs/2026-03-17-calendar-tasks-design.md`

---

## File Structure

### 신규 파일

| 파일 | 역할 |
|------|------|
| `data/auth/GoogleAuthManager.kt` | OAuth 싱글톤 — signIn/signOut/getAccessToken |
| `data/calendar/GoogleCalendarClient.kt` | Calendar REST API CRUD |
| `data/tasks/GoogleTasksClient.kt` | Tasks REST API CRUD |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `app/build.gradle.kts` | play-services-auth 의존성 추가 |
| `data/agent/KoogAgentEngine.kt` | 8개 Tool 추가, 시스템 프롬프트에 현재 시각 주입, setGoogleClients() |
| `ui/SettingsActivity.kt` | Google 계정 연결 섹션 추가 |
| `ui/MainViewModel.kt` | initAgent에서 GoogleAuthManager → client 생성 → engine에 주입 |
| `service/VoiceAssistantService.kt` | GoogleAuthManager → client 생성 → engine에 주입 |
| `data/settings/SecurePreferences.kt` | Google 계정 이메일 저장/조회/삭제 |

---

## Chunk 1: 인증 + 의존성

### Task 1: 의존성 추가

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: play-services-auth 의존성 추가**

`app/build.gradle.kts` dependencies 블록에 추가:

```kotlin
// Google Auth
implementation("com.google.android.gms:play-services-auth:21.3.0")
```

- [ ] **Step 2: 빌드 확인**

Run: `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/build.gradle.kts
git commit -m "chore: play-services-auth 의존성 추가"
```

---

### Task 2: SecurePreferences에 Google 계정 저장 기능 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt`

- [ ] **Step 1: Google 계정 이메일 저장/조회/삭제 메서드 추가**

```kotlin
// Google 계정 이메일
fun getGoogleAccountEmail(): String? {
    return prefs.getString(KEY_GOOGLE_EMAIL, null)
}

fun setGoogleAccountEmail(email: String) {
    prefs.edit().putString(KEY_GOOGLE_EMAIL, email).apply()
}

fun clearGoogleAccount() {
    prefs.edit().remove(KEY_GOOGLE_EMAIL).apply()
}
```

companion object에 `private const val KEY_GOOGLE_EMAIL = "google_account_email"` 추가.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt
git commit -m "feat: SecurePreferences에 Google 계정 이메일 저장 기능 추가"
```

---

### Task 3: GoogleAuthManager 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/auth/GoogleAuthManager.kt`

- [ ] **Step 1: GoogleAuthManager 싱글톤 구현**

```kotlin
package com.bara.heybara.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.bara.heybara.data.settings.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    private var accountEmail: String? = null

    fun isAuthenticated(): Boolean = accountEmail != null

    fun getAccountEmail(): String? = accountEmail

    // 앱 시작 시 SecurePreferences에서 복원
    fun restore(context: Context) {
        accountEmail = SecurePreferences(context).getGoogleAccountEmail()
    }

    // 설정 화면에서 호출 — AuthorizationClient로 계정 선택 + scope 동의
    suspend fun signIn(activity: Activity): Boolean {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE)))
                .build()

            val authClient: AuthorizationClient = Identity.getAuthorizationClient(activity)
            val result = authClient.authorize(authRequest).await()

            // 결과에서 계정 가져오기
            val account = result.toGoogleSignInAccount()
            val email = account?.email
            if (email != null) {
                accountEmail = email
                SecurePreferences(activity).setGoogleAccountEmail(email)
                Log.d(TAG, "Google 로그인 성공: $email")
                true
            } else {
                Log.w(TAG, "Google 로그인: 이메일 없음")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 실패", e)
            false
        }
    }

    fun signOut(context: Context) {
        accountEmail = null
        SecurePreferences(context).clearGoogleAccount()
        Log.d(TAG, "Google 로그아웃")
    }

    // Tool에서 호출 — GoogleAuthUtil이 토큰 캐싱/갱신 자동 처리
    suspend fun getAccessToken(context: Context): String? {
        val email = accountEmail ?: return null
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    email,
                    "oauth2:$CALENDAR_SCOPE $TASKS_SCOPE"
                )
            } catch (e: Exception) {
                Log.e(TAG, "토큰 획득 실패", e)
                null
            }
        }
    }
}
```

> Note: `AuthorizationClient.authorize().await()`는 `Tasks.await()`를 사용. 실제 구현 시 `suspendCoroutine`으로 래핑이 필요할 수 있음. Intent 기반 consent가 필요한 경우 `ActivityResultLauncher` 패턴으로 처리.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/auth/GoogleAuthManager.kt
git commit -m "feat: GoogleAuthManager OAuth 싱글톤 구현"
```

---

### Task 4: 설정 화면에 Google 계정 섹션 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt`

- [ ] **Step 1: Google 계정 연결 섹션 추가**

SettingsScreen composable에서 `LaunchedEffect` 블록 뒤에 `GoogleAuthManager.restore(context)` 호출.

API Key 섹션과 음성 모델 섹션 사이에 Google 계정 섹션 추가:

```kotlin
// Google 계정 섹션
val googleEmail = GoogleAuthManager.getAccountEmail()
SettingsSection(label = "Google 계정") {
    if (googleEmail != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(googleEmail, color = BaraColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { GoogleAuthManager.signOut(context) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "연결 해제", tint = BaraColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        Button(
            onClick = {
                scope.launch {
                    // activity cast 필요
                    val activity = context as? Activity
                    if (activity != null) {
                        GoogleAuthManager.signIn(activity)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral)
        ) {
            Text("Google 계정 연결")
        }
    }
}
```

import 추가: `GoogleAuthManager`, `Activity`

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt
git commit -m "feat: 설정 화면에 Google 계정 연결 섹션 추가"
```

---

## Chunk 2: REST API 클라이언트

### Task 5: GoogleCalendarClient 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/calendar/GoogleCalendarClient.kt`

- [ ] **Step 1: Calendar REST API CRUD 구현**

`HttpURLConnection` + `kotlinx.serialization`으로 직접 REST 호출.
메서드: `listEvents(token, date, days)`, `createEvent(token, title, start, end, desc)`, `updateEvent(token, eventId, title, start, end)`, `deleteEvent(token, eventId)`

기본값: `calendarId = "primary"`, `timeZone = "Asia/Seoul"`, `maxResults = 20`

각 메서드는 suspend + Dispatchers.IO에서 실행.
반환값은 Tool이 LLM에 전달할 사람 읽기 쉬운 문자열.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/calendar/GoogleCalendarClient.kt
git commit -m "feat: GoogleCalendarClient REST API CRUD 구현"
```

---

### Task 6: GoogleTasksClient 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/tasks/GoogleTasksClient.kt`

- [ ] **Step 1: Tasks REST API CRUD 구현**

메서드: `listTasks(token, showCompleted)`, `createTask(token, title, dueDate, notes)`, `completeTask(token, taskId)`, `deleteTask(token, taskId)`

기본값: `taskListId = "@default"`, `maxResults = 20`

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/tasks/GoogleTasksClient.kt
git commit -m "feat: GoogleTasksClient REST API CRUD 구현"
```

---

## Chunk 3: Koog Tool 등록 + 시스템 프롬프트

### Task 7: KoogAgentEngine에 캘린더 Tool 4개 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: ListEventsTool, CreateEventTool, UpdateEventTool, DeleteEventTool 추가**

기존 `SearchContactsTool` 패턴과 동일하게 `object : SimpleTool<Args>`.
각 Tool에 `var calendarClient: GoogleCalendarClient? = null`.
미인증 시 `calendarClient`가 null → "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요" 반환.

- [ ] **Step 2: buildTools()에 4개 tool 등록**

```kotlin
tool(ListEventsTool)
tool(CreateEventTool)
tool(UpdateEventTool)
tool(DeleteEventTool)
```

- [ ] **Step 3: 빌드 확인**

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: KoogAgentEngine에 캘린더 Tool 4개 추가"
```

---

### Task 8: KoogAgentEngine에 할일 Tool 4개 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: ListTasksTool, CreateTaskTool, CompleteTaskTool, DeleteTaskTool 추가**

동일 패턴. `var tasksClient: GoogleTasksClient? = null`.

- [ ] **Step 2: buildTools()에 4개 tool 등록**

- [ ] **Step 3: 빌드 확인**

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: KoogAgentEngine에 할일 Tool 4개 추가"
```

---

### Task 9: 시스템 프롬프트에 현재 시각 + 캘린더/할일 가이드 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: buildSystemPrompt()에서 현재 날짜/시간/요일 동적 주입**

```kotlin
private fun buildSystemPrompt(): String {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm (E)", Locale.KOREAN).format(Date())
    val base = """
${BASE_SYSTEM_PROMPT.trimIndent()}

현재 시각: $now
타임존: Asia/Seoul (KST, +09:00)

일정 관련 요청이 오면 캘린더 도구(list_events, create_event, update_event, delete_event)를 사용해.
할일 관련 요청이 오면 할일 도구(list_tasks, create_task, complete_task, delete_task)를 사용해.

규칙:
- 일정 수정/삭제 전에 list_events로 eventId를 먼저 확인해
- 할일 완료/삭제 전에 list_tasks로 taskId를 먼저 확인해
- 날짜는 ISO 8601 형식으로 변환해 (예: 2026-03-18T15:00:00+09:00)
- "내일", "다음 주 월요일" 같은 상대 날짜는 현재 시각 기준으로 계산해
""".trimIndent()

    if (conversationHistory.isEmpty()) return base
    val history = conversationHistory.joinToString("\n") { (user, assistant) ->
        "사용자: $user\n바라: $assistant"
    }
    return "$base\n\n이전 대화:\n$history"
}
```

- [ ] **Step 2: setGoogleClients() 메서드 추가**

```kotlin
fun setGoogleClients(calendarClient: GoogleCalendarClient?, tasksClient: GoogleTasksClient?) {
    ListEventsTool.calendarClient = calendarClient
    CreateEventTool.calendarClient = calendarClient
    UpdateEventTool.calendarClient = calendarClient
    DeleteEventTool.calendarClient = calendarClient
    ListTasksTool.tasksClient = tasksClient
    CreateTaskTool.tasksClient = tasksClient
    CompleteTaskTool.tasksClient = tasksClient
    DeleteTaskTool.tasksClient = tasksClient
}
```

- [ ] **Step 3: 빌드 확인**

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: 시스템 프롬프트에 현재 시각 주입 + setGoogleClients 추가"
```

---

## Chunk 4: 통합 연결

### Task 10: MainViewModel에서 Google 클라이언트 연결

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/ui/MainViewModel.kt`

- [ ] **Step 1: initAgent()에서 GoogleAuthManager 확인 후 client 생성 및 주입**

```kotlin
fun initAgent(context: Context) {
    if (agentEngine != null) return
    appContext = context.applicationContext
    val apiKey = SecurePreferences(context).getGeminiApiKey() ?: return
    val contactResolver = DeviceContactResolver(context)
    agentEngine = KoogAgentEngine(apiKey, contactResolver).also {
        it.setContext(context)
        // Google 클라이언트 주입
        if (GoogleAuthManager.isAuthenticated()) {
            it.setGoogleClients(
                GoogleCalendarClient(context),
                GoogleTasksClient(context)
            )
        }
    }
    // ...
}
```

GoogleCalendarClient/GoogleTasksClient 생성자에 context를 전달하여 내부에서 `GoogleAuthManager.getAccessToken(context)`를 호출.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/ui/MainViewModel.kt
git commit -m "feat: MainViewModel에서 Google 캘린더/할일 클라이언트 연결"
```

---

### Task 11: VoiceAssistantService에서 Google 클라이언트 연결

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt`

- [ ] **Step 1: onCreate()에서 GoogleAuthManager.restore() + 클라이언트 주입**

KoogAgentEngine 초기화 후:

```kotlin
GoogleAuthManager.restore(this)
if (GoogleAuthManager.isAuthenticated()) {
    (agentEngine as? KoogAgentEngine)?.setGoogleClients(
        GoogleCalendarClient(this),
        GoogleTasksClient(this)
    )
}
```

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt
git commit -m "feat: VoiceAssistantService에서 Google 클라이언트 연결"
```

---

### Task 12: 최종 빌드 + 통합 테스트

- [ ] **Step 1: clean 빌드 확인**

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew clean assembleDebug
```

- [ ] **Step 2: 디바이스 설치 테스트**

테스트 시나리오:
1. 설정 → Google 계정 연결
2. "오늘 일정 알려줘"
3. "내일 오후 3시에 치과 추가해줘"
4. "장보기 할일 추가해줘"
5. "남은 할일 뭐 있어?"
6. Google 미연결 상태에서 "오늘 일정" → 안내 메시지

- [ ] **Step 3: 최종 커밋**

```bash
git add -A
git commit -m "feat: Phase 4 캘린더/할일 통합 완료"
```
