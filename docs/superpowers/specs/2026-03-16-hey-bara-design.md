# Hey Bara - Design Specification

## Overview

Hey Bara is an on-device voice AI assistant for Android, inspired by capybaras. It listens for the wake word "Hey Bara", recognizes Korean speech commands, parses them with AI, executes device actions, and responds via TTS.

- **Target device**: Galaxy S25 Edge (Snapdragon 8 Elite, 12GB RAM)
- **Language**: Kotlin (Android Studio)
- **Distribution**: Personal sideloading (APK)

---

## Requirements

### Functional

- Wake word "헤이 바라" detection, always listening in background
- Korean speech-to-text after wake word trigger
- AI-powered natural language command parsing (on-device or cloud, user selectable)
- Execute actions: call, SMS, KakaoTalk, calendar events, tasks, notification reading
- TTS voice responses in natural, friendly Korean tone
- Confirmation before executing actions; queries execute immediately
- Text chat mode: type commands directly in-app, responses streamed as text (no TTS)
- Conversation history: LLM summarizes topic after each session, stored locally, browsable in UI

### Non-Functional

- Battery efficient: only wake word engine runs during idle
- Hands-free primary use case with always-on availability
- Minimal UI: settings screen + conversation log + status display + history screen
- Offline capable (on-device mode)

---

## Architecture

### State Machine

```
[IDLE] ──wake word──→ [LISTENING] ──recognized──→ [PROCESSING] ──action──→ [CONFIRMING]
  ↑                     │ 5s timeout                │ read query     │ "응"/"취소"
  │                     ↓                           ↓                ↓
  │                  TTS "네?"                   execute           execute/cancel
  │                     │ 3s timeout              + TTS              + TTS
  │                     ↓                           │                │
  └─────────────────[IDLE]←─────────────────────────┘←───────────────┘
```

- **IDLE**: Porcupine only (~10MB RAM), low power
- **LISTENING**: Beep → Sherpa-ONNX STT active (5s timeout, then "네?" + 3s extra)
- **PROCESSING**: Koog Agent parses command, classifies as read/action
- **CONFIRMING**: TTS asks confirmation → STT listens for "응"/"취소" (5s timeout)

### Session Mutual Exclusion

Only one session (voice or text) can be active at a time. If a wake word triggers during active text chat, the text session is paused and voice takes priority. After voice session ends, text chat resumes.

### On-Demand Session Pattern

Idle state runs only the wake word detector. All other components (STT, NLP, TTS) are loaded on-demand when wake word is detected, and released after the session completes.

```
Idle RAM:   ~10MB  (Porcupine only)
Active RAM: ~500MB (cloud mode) / ~2.5GB (on-device mode)
```

### Component Structure

```
app/
├── service/
│   └── BaraService.kt              # ForegroundService (always running)
├── voice/
│   ├── WakeWordDetector.kt          # Porcupine wrapper
│   ├── SpeechRecognizer.kt          # Sherpa-ONNX wrapper
│   ├── TtsEngine.kt                 # Supertonic 2 wrapper (fallback: Android TTS)
│   └── ContactResolver.kt          # Fuzzy contact name matching
├── agent/
│   ├── BaraAgent.kt                 # Koog Agent configuration
│   ├── LLMFactory.kt                # Gemma 3n / Gemini API switching
│   └── tools/                       # Koog Tool definitions
│       ├── CallTool.kt
│       ├── SmsTool.kt
│       ├── KakaoTool.kt
│       ├── CalendarTool.kt
│       ├── TaskTool.kt
│       └── NotificationTool.kt
├── action/
│   ├── CallManager.kt               # Phone calls
│   ├── SmsManager.kt                # SMS read/send
│   ├── KakaoSender.kt               # KakaoTalk via Accessibility
│   ├── CalendarManager.kt           # Google Calendar API
│   ├── TaskManager.kt               # Google Tasks API
│   └── NotificationReader.kt        # Notification reading
├── session/
│   └── VoiceSession.kt              # On-demand session lifecycle
├── data/
│   ├── ConversationHistory.kt       # Room entity for conversation history
│   ├── HistoryDao.kt                # Room DAO
│   └── AppDatabase.kt               # Room database
├── ui/
│   ├── MainActivity.kt              # Status display + chat + text input
│   ├── HistoryActivity.kt           # Conversation history list
│   ├── SettingsActivity.kt          # Settings screen
│   └── OverlayBubbleView.kt         # Floating overlay for other app context
├── accessibility/
│   └── BaraAccessibilityService.kt  # KakaoTalk UI automation
├── notification/
│   └── BaraNotificationListener.kt  # Notification listener
├── config/
│   ├── SettingsManager.kt           # NLP mode, TTS settings
│   └── ResponseStrings.kt           # All TTS response strings (centralized)
└── util/
    └── PermissionManager.kt         # Permission request/check/fallback
```

---

## Voice Pipeline Sequence

```
1. [IDLE] Porcupine listening (~10MB)
2. "헤이 바라" detected
3. VoiceSession created
   ├── Load Sherpa-ONNX model
   ├── Play beep sound
   └── Switch mic input to STT
4. [LISTENING] Wait for speech (5s timeout)
5. Speech recognized → text
6. [PROCESSING] Send text to Koog Agent
   ├── LLMFactory → Gemma 3n or Gemini API
   ├── Tool selection
   └── Parameter extraction
7. Action classification
   ├── Read: execute immediately
   └── Action: TTS confirmation → wait for response
8. (If confirmation needed) STT listens for response
   ├── "응" / "해줘" → execute
   └── "아니" / "취소" → cancel
9. Execute + TTS result announcement
10. Release VoiceSession → [IDLE]
11. (Async) LLM summarizes conversation topic → save to local DB
    - Runs in background coroutine, does not block return to IDLE
    - If summarization fails, save with default topic "대화" + timestamp
```

### Text Chat Mode

When the user types in the text input field instead of using voice:

```
1. User types message in input field → taps send
2. [PROCESSING] Send text to Koog Agent (same pipeline as voice)
3. Response streamed as text to conversation log (no TTS)
4. Action classification same as voice mode
5. If confirmation needed → show confirmation button in UI (not voice)
6. On session end → LLM summarizes topic → save to local DB
```

- No wake word, STT, or TTS involved
- Same Koog Agent and tools as voice mode
- Response appears as streaming text in real-time
- Input source tagged as "텍스트" in history

### Conversation History

After each conversation session completes:

```
1. Send conversation transcript to LLM
2. LLM generates: topic summary (1 line), category (call/sms/kakao/calendar/task/notification)
3. Save to local Room DB:
   - id (auto)
   - topic: "엄마한테 전화 걸기"
   - category: "call"
   - inputMode: "voice" | "text"
   - timestamp: ISO datetime
   - transcript: full conversation JSON
4. History screen displays grouped by date, with category icon + color
```

### Timeout Policy

| Situation | Timeout | Behavior |
|-----------|---------|----------|
| No speech after wake word | 5s | TTS "네?" → 3s extra wait → IDLE |
| Waiting for confirmation | 5s | TTS "취소할게요" → IDLE |

Note: The system supports limited multi-turn patterns (confirmation dialogue, clarification questions) but not open-ended continuous conversation. Extensible later.

### Contact Resolution

When a command references a person (e.g. "엄마한테 전화해"):

1. LLM extracts the name/relationship from the command
2. `ContactResolver` performs fuzzy matching against device contacts
3. If single match → use it
4. If multiple matches → TTS "엄마가 여러 명 있어요. 김영희, 이순자 중 누구요?" → CONFIRMING state
5. If no match → TTS "연락처에서 엄마를 찾을 수 없어요"

### Error Handling

| Error | Behavior |
|-------|----------|
| STT returns empty/garbage | TTS "잘 못 들었어요, 다시 말해주세요" → LISTENING (1회 재시도 후 IDLE) |
| LLM returns unparseable output | TTS "이해하지 못했어요" → IDLE |
| Network failure (cloud mode) | TTS "인터넷 연결이 안 돼요" → IDLE |
| Google API auth expired | TTS "구글 계정을 다시 연결해주세요" → IDLE |
| Action execution failure | TTS "실행에 실패했어요" + reason → IDLE |
| KakaoTalk UI changed | TTS "카카오톡 전송을 실패했어요. 앱이 업데이트됐을 수 있어요" → IDLE |
| Accessibility service disconnected | TTS "접근성 서비스가 꺼져 있어요" → IDLE |

---

## Action Classification

### Read (no confirmation)

| Action | Example |
|--------|---------|
| Calendar query | "오늘 일정 알려줘" |
| Task query | "남은 할일 뭐 있어?" |
| Notification query | "알림 뭐 왔어?" |
| SMS query | "읽지 않은 문자 알려줘" |

### Action (confirmation required)

| Action | Example |
|--------|---------|
| Make call | "엄마한테 전화해" |
| Send SMS | "철수한테 지금 간다고 문자 보내" |
| Send KakaoTalk | "영희한테 밥 먹자고 카톡 보내" |
| Add calendar event | "내일 3시에 치과 일정 추가해줘" |
| Add task | "장보기 할일 추가해줘" |
| Complete task | "장보기 완료 처리해줘" |

### Failed Recognition

When the command is unclear, AI guesses and asks for confirmation:
```
User: "음... 그... 카톡..."
AI: "혹시 누군가한테 카톡 보내라는 건가요?"
User: "응, 철수한테"
AI: "철수한테 뭐라고 보낼까요?"
```

---

## AI Engine

### NLP Mode Switching (manual in settings)

| Mode | Engine | RAM | Internet | Cost |
|------|--------|-----|----------|------|
| On-device | Gemma 3n E2B | ~2GB | Not required | Free |
| Cloud | Gemini API | ~0 | Required | Free tier available |

### Koog Agent

- Framework: JetBrains Koog
- Role: Tool registration, model connection, function calling management
- Tools registered for each action (call, SMS, kakao, calendar, tasks, notifications)
- Same tool definitions work with both on-device and cloud engines

### Koog + Gemma 3n Validation

Phase 2에서 Koog + Gemma 3n E2B의 tool calling 호환성을 반드시 검증한다.
- Gemma 3n이 structured tool-call JSON을 안정적으로 생성하는지 확인
- 실패 시 폴백 전략: custom prompt-based parsing 또는 cloud 모드 필수 안내

### TTS Engine Details

| 항목 | 내용 |
|------|------|
| Primary | Supertonic 2 (on-device, ONNX, ~60MB) |
| Fallback | Android 기본 TTS (`TextToSpeech` API) |
| Latency | ~200ms (모바일 3-5 step inference) |
| Voice | 한국어 자연스러운 톤 |
| Trigger | Supertonic 로드 실패 시 자동으로 Android TTS 폴백 |

### Beep Sound

- Short (~200ms) bundled audio asset
- Played via `SoundPool` for low-latency playback
- Located at `res/raw/beep.wav`

---

## Models

| Role | Model | Size | RAM |
|------|-------|------|-----|
| Wake word | Porcupine (custom "헤이 바라") | ~10MB | ~10MB |
| STT | Sherpa-ONNX Zipformer Korean | ~60MB | ~200MB |
| NLP (on-device) | Gemma 3n E2B | ~2GB | ~2GB |
| NLP (cloud) | Gemini API | 0 | 0 |
| TTS | Supertonic 2 | ~60MB | ~60MB |

---

## UI

### MainActivity

- Header: "Hey Bara" title + IDLE status badge + history/settings icons
- Conversation log (chat-style bubbles, user=coral, AI=gray with capybara avatar)
- Text input bar at bottom: message field + send button
- Navigation: history icon (top-right) → HistoryActivity, settings icon → SettingsActivity

### HistoryActivity

- Grouped by date (오늘, 어제, etc.)
- Each item shows: category icon (color-coded), topic summary, time, input mode (음성/텍스트)
- Tap to view full conversation transcript
- Category colors: call=coral, calendar=green, kakao=coral, sms=teal, notification=amber, task=purple

### SettingsActivity

- NLP mode selection (on-device / cloud) with radio buttons
- Google account connection (Calendar / Tasks)
- Gemini API key input
- Wake word sensitivity slider

### Overlay UI (other app context)

When wake word is triggered while using another app, a floating overlay bubble appears on top via `SYSTEM_ALERT_WINDOW` permission.

**Overlay - Listening state:**
- White card with shadow, top of screen
- Capybara icon + "Hey Bara" + "듣고 있어요" badge (coral)
- Audio waveform animation
- Real-time STT text display

**Overlay - Confirm state:**
- White card with shadow, top of screen
- Capybara icon + "Hey Bara" + "확인 대기" badge (indigo)
- AI question text (e.g. "엄마한테 전화 걸까요?")
- Hint: "'응' 또는 '취소'로 답해주세요"

**Overlay dismissal:**
- Auto-dismiss after action completes + TTS response
- Auto-dismiss on timeout (5s no response)
- Session ends → overlay closes → return to previous app

### Foreground Service Notification

Idle:
```
🦫 Hey Bara
대기 중 — "헤이 바라"로 호출
```

Active:
```
🦫 Hey Bara
듣고 있어요...
```

---

## Android Permissions

### Mandatory (앱 실행에 필수)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

거부 시: 앱 핵심 기능 불가, 권한 재요청 안내

### Optional (기능별, 거부해도 다른 기능 사용 가능)

```xml
<uses-permission android:name="android.permission.CALL_PHONE"/>        <!-- 전화 -->
<uses-permission android:name="android.permission.READ_CONTACTS"/>     <!-- 연락처 -->
<uses-permission android:name="android.permission.READ_SMS"/>          <!-- 문자 조회 -->
<uses-permission android:name="android.permission.SEND_SMS"/>          <!-- 문자 발송 -->
<uses-permission android:name="android.permission.READ_CALL_LOG"/>     <!-- 통화 기록 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/> <!-- 오버레이 -->
```

거부 시: 해당 기능 비활성화, TTS로 "이 기능을 사용하려면 권한이 필요해요" 안내

### Special Permissions (설정에서 수동 활성화)

- NotificationListenerService: 알림 조회
- AccessibilityService: 카톡 자동 전송

### Foreground Service Type

IDLE 상태에서도 Porcupine이 마이크를 사용하므로 `FOREGROUND_SERVICE_MICROPHONE` 타입이 적절하다. Android 14+ 에서 continuous microphone access로 분류됨. 사이드로딩 앱이라 Play Store 정책 미적용.

Plus service declarations for NotificationListenerService and AccessibilityService.

---

## Development Phases

### Phase 1: Voice Pipeline (MVP)

ForegroundService + Porcupine → beep → Sherpa-ONNX STT → TTS echo-back.
Goal: Validate end-to-end voice loop without AI/actions.

### Phase 2: AI Agent Connection

STT text → Koog Agent + LLMFactory → Gemma 3n / Gemini API.
Goal: Natural language command parsing works. Actions log-only (not yet connected).
**Validation gate**: Koog + Gemma 3n tool calling 호환성 검증. 실패 시 cloud 모드 우선 또는 custom parsing 폴백.

### Phase 3: Core Actions

- 3-1. Phone calls (CallManager)
- 3-2. SMS read/send (SmsManager)
- 3-3. Calendar events (CalendarManager + Google Calendar API)
- 3-4. Tasks (TaskManager + Google Tasks API)

### Phase 4: Extended Actions

- 4-1. KakaoTalk sending (AccessibilityService)
  - UI 요소 식별: resource ID 우선, 실패 시 text matching 폴백
  - 카카오톡 버전 호환성 체크 로직 포함
  - UI 변경 감지 시 사용자에게 기능 비활성 안내
- 4-2. Notification reading (NotificationListener)

### Phase 5: Stabilization

- 5-1. Battery optimization (VoiceSession release timing)
- 5-2. Error handling / retry logic
- 5-3. UI polish

---

## Google API Integration

- **Google Calendar API**: Event CRUD (list, insert, update, delete)
- **Google Tasks API**: Task CRUD (list, insert, update status to completed)
- **Auth**: Android Credential Manager (One Tap sign-in) for OAuth 2.0, scopes: `calendar`, `tasks`
- **Setup**: Google Cloud Console project with Calendar API + Tasks API enabled

---

## RAM Budget (Galaxy S25 Edge, 12GB)

### Idle (wake word only)

```
Android OS + apps    ~4.0GB
Porcupine            ~0.01GB
─────────────────────────
Total                ~4.0GB / 12GB
```

### Active - Cloud Mode

```
Android OS + apps    ~4.0GB
Porcupine            ~0.01GB
Sherpa-ONNX          ~0.2GB
Supertonic 2         ~0.06GB
Misc                 ~0.5GB
─────────────────────────
Total                ~4.8GB / 12GB
```

### Active - On-Device Mode

```
Android OS + apps    ~4.0GB
Porcupine            ~0.01GB
Sherpa-ONNX          ~0.2GB
Gemma 3n E2B         ~2.0GB
Supertonic 2         ~0.06GB
App process + Koog   ~0.2GB
Room DB + framework  ~0.1GB
─────────────────────────
Total                ~6.6GB / 12GB
```

### Battery Impact

- **IDLE (Porcupine 상시 대기)**: ~1-3% per hour (Picovoice 공식 벤치마크 기준)
- **Active session**: 짧은 시간(~10-30초)이므로 무시 가능
- NPU 활용 시 CPU 대비 3.7x 배터리 효율

### Data Retention

- 대화 히스토리 기본 90일 보관
- 설정에서 수동 전체 삭제 가능
- 90일 초과 기록 자동 삭제 (앱 시작 시 cleanup)
