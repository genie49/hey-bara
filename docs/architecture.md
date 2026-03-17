# Hey Bara - 음성 AI 비서 프로젝트

## 개요

카피바라에서 영감을 받은 온디바이스 음성 AI 비서.
웨이크워드로 호출하면 음성 명령을 인식하고, AI가 명령을 파싱하여 다양한 기능을 실행한다.

## 기술 스택

- **언어**: Kotlin
- **IDE**: Android Studio
- **타겟 디바이스**: Galaxy S25 Edge (Snapdragon 8 Elite, 12GB RAM)
- **AI Agent 프레임워크**: Koog (JetBrains)

---

## 아키텍처

```
Sherpa-ONNX KWS (웨이크워드 "Hey Bara")   ~5MB  (zh-en 음소 모델)
  → Sherpa-ONNX Zipformer Korean (STT)    ~300MB (앱 내 다운로드)
    → Koog (AI Agent 프레임워크)
      → NLP 엔진 (사용자 설정에서 선택)
        ├── [A] Gemma 3n E2B (온디바이스)  ~2GB  | 오프라인 | 무료      [미구현]
        └── [B] Gemini API (클라우드)      ~0MB  | 온라인  | 무료 티어  [구현 완료]
      → Tool 실행 (Human-in-the-Loop 확인 모달)
        ├── CallManager (전화 걸기)                                    [구현 완료]
        ├── SmsManager (문자 발송)                                     [구현 완료]
        ├── CalendarManager (일정 조회/생성)                            [미구현]
        ├── TaskManager (할일 조회/추가/완료)                           [미구현]
        ├── KakaoSender (카카오톡 자동 전송)                            [미구현]
        └── NotificationReader (알림 조회)                             [미구현]
    → Android TTS (한국어 음성 응답)                                    [Supertonic 2 교체 예정]
```

---

## 패키지 구조 (Clean Architecture)

의존성 방향: `ui/service` → `data` → `domain`

```
com/bara/heybara/
├── domain/                    ← 순수 Kotlin, 외부 의존성 없음
│   ├── voice/                 인터페이스 (BeepPlayer, SpeechRecognizer, TtsEngine, WakeWordDetector)
│   ├── session/               상태 정의 (SessionState) + 비즈니스 로직 (VoiceSession)
│   ├── agent/                 AgentEngine 인터페이스
│   ├── action/                ActionConfirmation (Tool ↔ UI 확인 브릿지), ContactResolver
│   └── history/               Conversation 모델 + ConversationRepository
│
├── data/                      ← 구체적 기술 구현, 외부 SDK 의존
│   ├── voice/                 SherpaKwsWakeWordDetector, SherpaSpeechRecognizer, AndroidTtsEngine
│   ├── agent/                 KoogAgentEngine (Koog + Gemini API)
│   ├── action/                DeviceContactResolver
│   ├── history/               Room DB (ConversationEntity, ConversationDao, AppDatabase)
│   ├── model/                 ModelInstaller (STT/KWS 모델 다운로드 관리)
│   └── settings/              SecurePreferences (API Key, 웨이크워드 감도)
│
├── service/                   ← ForegroundService (VoiceAssistantService)
├── ui/                        ← Compose UI, ViewModel, SettingsActivity, HistoryActivity
└── util/                      ← AssetCopier 등 유틸
```

### 레이어 규칙

| 레이어 | 역할 | 의존 가능 대상 | 금지 |
|--------|------|---------------|------|
| **domain** | 인터페이스, 상태, 비즈니스 로직 정의 | 없음 (순수 Kotlin) | Android SDK, 외부 라이브러리 |
| **data** | domain 인터페이스의 실제 구현 | domain | ui, service |
| **ui / service** | 사용자 상호작용, 서비스 생명주기 | domain, data | — |

### 구현체 교체 예시

| 인터페이스 (domain) | 현재 구현 (data) | 추후 교체 가능 |
|---------------------|------------------|---------------|
| `TtsEngine` | `AndroidTtsEngine` (기본 TTS) | `SupertonicTtsEngine` |
| `SpeechRecognizer` | `SherpaSpeechRecognizer` | Google STT 등 |
| `WakeWordDetector` | `SherpaKwsWakeWordDetector` | 다른 웨이크워드 엔진 |

---

## 핵심 컴포넌트

### 1. 웨이크워드 - Sherpa-ONNX KWS

| 항목 | 내용 |
|------|------|
| 라이브러리 | `com.k2fsa.sherpa:sherpa-onnx` (AAR) |
| 모델 | zh-en 음소 기반 (phone+ppinyin), chunk-8 int8 |
| 웨이크워드 | "Hey Bara" (CMU phoneme: `HH EY1 B AA1 R AH0`) |
| 동작 방식 | Foreground Service에서 마이크 상시 대기 |
| 크기 | ~5MB (앱 내 다운로드) |
| 감도 | 설정에서 조절 가능 (keywordsScore/keywordsThreshold 매핑) |

### 2. STT - Sherpa-ONNX (Zipformer Korean)

| 항목 | 내용 |
|------|------|
| 라이브러리 | `com.k2fsa.sherpa:sherpa-onnx` (AAR) |
| 모델 | `sherpa-onnx-streaming-zipformer-korean-2024-06-16` |
| 크기 | ~300MB (HuggingFace에서 앱 내 다운로드) |
| 지연 | ~160ms (실시간 스트리밍) |
| 특징 | 온디바이스, 오프라인, 한국어 전용 |

### 3. NLP 엔진

#### 모드 B: Gemini API (클라우드) — 현재 사용 중

| 항목 | 내용 |
|------|------|
| API | Google Gemini API (gemini-3.1-flash-lite-preview) |
| 비용 | 무료 티어 |
| Agent | Koog AIAgent + SimpleTool |
| 특징 | Function Calling으로 Tool 실행 |

#### 모드 A: Gemma 3n E2B (온디바이스) — 미구현

| 항목 | 내용 |
|------|------|
| 라이브러리 | `com.google.mediapipe:tasks-genai` (Google AI Edge) |
| RAM | ~2GB |
| 특징 | 오프라인, 무료, 한국어 지원 |

### 4. 액션 실행 - Human-in-the-Loop

| 항목 | 내용 |
|------|------|
| 패턴 | Tool 내부에서 `ActionConfirmation.requestConfirmation()` 호출 |
| UI | 확인 모달 다이얼로그 (10초 카운트다운 + 자동 실행) |
| 흐름 | AI가 Tool 호출 → 모달 표시 → 확인/취소 → Tool이 실제 실행 또는 "취소됨" 반환 |

### 5. TTS - Android 기본 TTS

| 항목 | 내용 |
|------|------|
| 구현 | `android.speech.tts.TextToSpeech` |
| 언어 | `Locale.KOREAN` |
| 추후 | Supertonic 2 (한국어 네이티브 TTS) 교체 예정 |

### 6. 모델 관리 - ModelInstaller

| 항목 | 내용 |
|------|------|
| STT | HuggingFace에서 개별 파일 다운로드 (~300MB) |
| KWS | GitHub releases에서 tar.bz2 다운로드 후 추출 (~5MB) |
| UI | 설정 화면에서 각 모델별 설치/진행률/완료 상태 표시 |
| keywords.txt | 코드에서 직접 생성 (커스텀 웨이크워드) |

---

## 기능 목록

### 구현 완료

| 기능 | 구현 방식 |
|------|----------|
| 전화 걸기 | `Intent.ACTION_CALL` (Tool 내 직접 실행) |
| SMS 발송 | `SmsManager.sendTextMessage()` (Tool 내 직접 실행) |
| 연락처 검색 | ContentResolver LIKE 검색 (SearchContactsTool) |
| 대화 히스토리 | Room DB + LLM 요약 (별도 에이전트) + sessionId 기반 upsert |
| 텍스트 채팅 | 메인 화면 입력창에서 직접 명령 |

### 미구현 (Phase 4+)

| 기능 | 구현 방식 |
|------|----------|
| 캘린더 일정 조회/생성 | Google Calendar API |
| 할일 조회/추가/완료 | Google Tasks API |
| 카카오톡 전송 | AccessibilityService (UI 자동 조작) |
| 알림 조회 | NotificationListenerService |

---

## Android 권한 요약

```xml
<!-- 현재 사용 중 -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>

<!-- Phase 4+ 추가 예정 -->
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.READ_CALL_LOG"/>
<!-- + Google Calendar/Tasks OAuth, NotificationListener, AccessibilityService -->
```

---

## RAM 사용량 시나리오 (Galaxy S25 Edge, 12GB)

```
모드 B (Gemini API 클라우드) — 현재:
  Android OS + 기본 앱       ~4.0GB
  Sherpa-ONNX KWS            ~0.005GB
  Sherpa-ONNX Zipformer      ~0.2GB
  Android TTS                ~0.01GB
  기타 서비스                 ~0.5GB
  ─────────────────────────
  합계                       ~4.7GB / 12GB ✅✅

모드 A (Gemma 3n 온디바이스) — 추후:
  + Gemma 3n E2B             ~2.0GB
  ─────────────────────────
  합계                       ~6.7GB / 12GB ✅
```

---

## 사전 설정 필요 사항

1. **Google AI Studio** - Gemini API 키 발급 (앱 설정 화면에서 입력)
2. **모델 설치** - 앱 설정 화면에서 STT/KWS 모델 다운로드 (Wi-Fi 권장)

---

## Google Play 정책 참고

- SMS/전화 권한: Google Play 심사 엄격 → **개인용 APK 사이드로딩**
- Accessibility Service: 스토어 등록 사실상 불가 → **사이드로딩**
- **결론: 개인용 사이드로딩으로 사용**
