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

### Non-Functional

- Battery efficient: only wake word engine runs during idle
- Hands-free primary use case with always-on availability
- Minimal UI: settings screen + conversation log + status display
- Offline capable (on-device mode)

---

## Architecture

### State Machine

```
[IDLE] ──wake word detected──→ [LISTENING] ──speech recognized──→ [PROCESSING]
  ↑                              │ timeout                         │
  │                              ↓                                 │
  │                           [IDLE]                                │
  │                                                                 │
  └──────────── action complete + TTS response ────────────────────┘
```

- **IDLE**: Porcupine only (~10MB RAM), low power
- **LISTENING**: Beep → Sherpa-ONNX STT active (5s timeout)
- **PROCESSING**: Koog Agent parses → execute → TTS → return to IDLE

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
│   └── TtsEngine.kt                 # Supertonic 2 wrapper
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
├── ui/
│   ├── MainActivity.kt              # Settings + status display
│   └── ConversationLog.kt           # Chat-style conversation log
├── accessibility/
│   └── BaraAccessibilityService.kt  # KakaoTalk UI automation
├── notification/
│   └── BaraNotificationListener.kt  # Notification listener
└── config/
    └── SettingsManager.kt           # NLP mode, TTS settings
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
```

### Timeout Policy

| Situation | Timeout | Behavior |
|-----------|---------|----------|
| No speech after wake word | 5s | TTS "네?" → 3s extra wait → IDLE |
| Waiting for confirmation | 5s | TTS "취소할게요" → IDLE |
| Continuous conversation | N/A | Single-turn only (extensible later) |

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

- Status display: IDLE / LISTENING / PROCESSING
- Current NLP mode indicator (on-device / cloud)
- Conversation log (chat-style)
- Settings button

### SettingsActivity

- NLP mode selection (on-device / cloud)
- Google account connection (Calendar / Tasks)
- Gemini API key input
- Wake word sensitivity adjustment

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

```xml
<!-- Microphone -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>

<!-- Phone -->
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>

<!-- SMS -->
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.SEND_SMS"/>

<!-- Call log -->
<uses-permission android:name="android.permission.READ_CALL_LOG"/>

<!-- Internet -->
<uses-permission android:name="android.permission.INTERNET"/>
```

Plus service declarations for NotificationListenerService and AccessibilityService.

---

## Development Phases

### Phase 1: Voice Pipeline (MVP)

ForegroundService + Porcupine → beep → Sherpa-ONNX STT → TTS echo-back.
Goal: Validate end-to-end voice loop without AI/actions.

### Phase 2: AI Agent Connection

STT text → Koog Agent + LLMFactory → Gemma 3n / Gemini API.
Goal: Natural language command parsing works. Actions log-only (not yet connected).

### Phase 3: Core Actions

- 3-1. Phone calls (CallManager)
- 3-2. SMS read/send (SmsManager)
- 3-3. Calendar events (CalendarManager + Google Calendar API)
- 3-4. Tasks (TaskManager + Google Tasks API)

### Phase 4: Extended Actions

- 4-1. KakaoTalk sending (AccessibilityService)
- 4-2. Notification reading (NotificationListener)

### Phase 5: Stabilization

- 5-1. Battery optimization (VoiceSession release timing)
- 5-2. Error handling / retry logic
- 5-3. UI polish

---

## Google API Integration

- **Google Calendar API**: Event CRUD (list, insert, update, delete)
- **Google Tasks API**: Task CRUD (list, insert, update status to completed)
- **Auth**: Google Sign-In (OAuth 2.0), scopes: `calendar`, `tasks`
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
Misc                 ~0.5GB
─────────────────────────
Total                ~6.8GB / 12GB
```
