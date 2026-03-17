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
      → NLP 엔진
        ├── [A] Gemma 3n E2B (온디바이스)  ~2GB  | 오프라인 | 무료      [미구현]
        └── [B] Gemini API (클라우드)      ~0MB  | 온라인  | 무료 티어  [구현 완료]
      → Tool 실행
        ├── CallManager (전화 걸기)           — HITL 확인 모달          [구현 완료]
        ├── SmsManager (문자 발송)            — HITL 확인 모달          [구현 완료]
        ├── CalendarManager (일정 CRUD)       — Google Calendar API    [구현 완료]
        ├── TaskManager (할일 CRUD)           — Google Tasks API       [구현 완료]
        ├── KakaoSender (카카오톡 전송)        — RemoteInput 답장       [구현 완료]
        ├── NotificationReader (알림 조회)     — NotificationListener   [구현 완료]
        └── AppController (범용 앱 제어)       — AccessibilityService   [구현 완료]
    → Supertonic 2 TTS (한국어 고품질 음성)   ~263MB (앱 내 다운로드)    [구현 완료]
      → fallback: Android 기본 TTS
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
│   ├── tts/                   SupertonicTtsEngine, SupertonicInference, TextPreprocessor
│   ├── agent/                 KoogAgentEngine (Koog + Gemini API, 13개 Tool)
│   ├── auth/                  GoogleAuthManager (OAuth + UserInfo API)
│   ├── calendar/              GoogleCalendarClient (REST API)
│   ├── tasks/                 GoogleTasksClient (REST API)
│   ├── notification/          BaraNotificationListener (알림 조회 + 카카오톡 RemoteInput)
│   ├── accessibility/         BaraAccessibilityService, AppControlAgent (범용 앱 제어)
│   ├── action/                DeviceContactResolver
│   ├── history/               Room DB (ConversationEntity, ConversationDao, AppDatabase)
│   ├── model/                 ModelInstaller (STT/KWS/TTS 모델 다운로드 관리)
│   └── settings/              SecurePreferences (API Key, 감도, Google 계정, 등록 앱)
│
├── service/                   ← ForegroundService (VoiceAssistantService)
└── ui/                        ← Compose UI, ViewModel, SettingsActivity, HistoryActivity
```

---

## 핵심 컴포넌트

### 1. 웨이크워드 - Sherpa-ONNX KWS

| 항목 | 내용 |
|------|------|
| 모델 | zh-en 음소 기반 (phone+ppinyin), chunk-8 int8 |
| 웨이크워드 | "Hey Bara" (CMU phoneme: `HH EY1 B AA1 R AH0`) |
| 크기 | ~5MB (앱 내 다운로드) |
| 감도 | 설정에서 조절 가능 |

### 2. STT - Sherpa-ONNX (Zipformer Korean)

| 항목 | 내용 |
|------|------|
| 모델 | `sherpa-onnx-streaming-zipformer-korean-2024-06-16` |
| 크기 | ~300MB (HuggingFace 다운로드) |
| 특징 | 온디바이스, 오프라인, 한국어 전용 |

### 3. NLP 엔진 - Gemini API

| 항목 | 내용 |
|------|------|
| API | Google Gemini API (gemini-3.1-flash-lite-preview) |
| Agent | Koog AIAgent + 13개 SimpleTool |
| 특징 | Function Calling, 대화 히스토리 관리, 현재 시각 주입 |

### 4. Tool 목록 (13개)

| Tool | 용도 | 확인 모달 |
|------|------|:--------:|
| search_contacts | 연락처 검색 | - |
| make_call | 전화 걸기 | O |
| send_sms | 문자 보내기 | O |
| send_kakao | 카카오톡 답장 | O |
| list_events | 캘린더 일정 조회 | - |
| create_event | 일정 생성 | - |
| update_event | 일정 수정 | - |
| delete_event | 일정 삭제 | - |
| list_tasks | 할일 조회 | - |
| create_task | 할일 생성 | - |
| complete_task | 할일 완료 | - |
| delete_task | 할일 삭제 | - |
| list_notifications | 알림 조회 | - |
| control_app | 범용 앱 제어 | - |

### 5. 범용 앱 제어 - AppControlAgent

| 항목 | 내용 |
|------|------|
| 방식 | AccessibilityService + Gemini Tool 기반 UI 탐색 (DroidBot-GPT 방식) |
| Tool | click, long_click, type_text, scroll_down/up, press_enter, press_back, wait_and_get_screen |
| 제한 | 최대 30 iteration, 등록된 앱만 제어 |
| 설정 | 앱 등록 (설치 앱 목록에서 선택) + 접근성 권한 |

### 6. TTS - Supertonic 2

| 항목 | 내용 |
|------|------|
| 모델 | Supertonic 2 (66M params, ONNX) |
| 파이프라인 | Duration Predictor → Text Encoder → Vector Estimator (2스텝) → Vocoder |
| 크기 | ~263MB (HuggingFace 다운로드) |
| 출력 | 44100Hz, 16-bit PCM, mono |
| Fallback | Android 기본 TTS (모델 미설치 시) |

### 7. 모델 관리 - ModelInstaller

| 모델 | 크기 | 소스 |
|------|------|------|
| STT (Zipformer Korean) | ~300MB | HuggingFace 개별 파일 |
| KWS (zh-en 음소) | ~5MB | GitHub release tar.bz2 |
| TTS (Supertonic 2) | ~263MB | HuggingFace 개별 파일 |

### 8. 인증 - GoogleAuthManager

| 항목 | 내용 |
|------|------|
| 방식 | AuthorizationClient + GoogleAuthUtil + UserInfo API |
| Scope | calendar, tasks, email |
| 토큰 | 로그인 시 prefetch, 캐싱, 실패 시 전체 클리어 |

---

## 기능 목록

### 구현 완료

| 기능 | 구현 방식 |
|------|----------|
| 전화 걸기 | Intent.ACTION_CALL (HITL 확인 모달) |
| SMS 발송 | SmsManager (HITL 확인 모달) |
| 카카오톡 전송 | NotificationListener RemoteInput (HITL 확인 모달) |
| 연락처 검색 | ContentResolver LIKE 검색 |
| 캘린더 CRUD | Google Calendar REST API |
| 할일 CRUD | Google Tasks REST API |
| 알림 조회 | NotificationListenerService |
| 범용 앱 제어 | AccessibilityService + Gemini Tool 기반 |
| 대화 히스토리 | Room DB + LLM 요약 + sessionId upsert |
| 텍스트 채팅 | 메인 화면 입력창 |
| 모델 다운로드 | 설정 화면에서 STT/KWS/TTS 개별 다운로드 |

### 미구현

| 기능 | 비고 |
|------|------|
| 온디바이스 NLP (Gemma 3n) | 오프라인 모드, 설정에서 전환 |

---

## 설정 화면

| 섹션 | 내용 |
|------|------|
| Gemini API 키 | 입력/삭제 |
| Google 계정 | 연결/해제 (Calendar, Tasks, Email scope) |
| 음성 모델 | STT/KWS/TTS 설치 + TTS 듣기 버튼 |
| 알림 접근 | NotificationListener 권한 |
| 앱 제어 | 접근성 서비스 권한 + 앱 등록/삭제 |
| AI 엔진 | 클라우드/온디바이스 선택 (온디바이스 미구현) |
| 웨이크워드 감도 | 슬라이더 (0~1) |

---

## Android 권한

```xml
<!-- 사용 중 -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>

<!-- 서비스 -->
NotificationListenerService (알림 조회 + 카카오톡 RemoteInput)
AccessibilityService (범용 앱 제어)
```

---

## 사전 설정

1. **Google AI Studio** - Gemini API 키 발급 (앱 설정에서 입력)
2. **Google Cloud Console** - OAuth 클라이언트 ID (Android, SHA-1) + Calendar/Tasks API 활성화
3. **모델 설치** - 앱 설정에서 STT/KWS/TTS 다운로드
4. **권한** - 알림 접근, 접근성 서비스 (앱 설정에서 안내)
