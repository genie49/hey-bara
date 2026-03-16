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
Porcupine (웨이크워드 "헤이 바라")        ~10MB
  → Sherpa Zipformer Korean (STT)        ~200MB
    → Koog (AI Agent 프레임워크)
      → NLP 엔진 (사용자 설정에서 선택)
        ├── [A] Gemma 3n E2B (온디바이스)  ~2GB  | 오프라인 | 무료
        └── [B] Gemini API (클라우드)      ~0MB  | 온라인  | 무료 티어
      → Tool 실행
        ├── CallManager (전화 걸기)
        ├── SmsManager (문자 조회/발송)
        ├── KakaoSender (카카오톡 자동 전송)
        ├── CalendarManager (일정 조회/생성)
        ├── TaskManager (할일 조회/추가/완료)
        └── NotificationReader (알림 조회)
    → Supertonic 2 (TTS 응답)             ~60MB
```

---

## 핵심 컴포넌트

### 1. 웨이크워드 - Porcupine

| 항목 | 내용 |
|------|------|
| 라이브러리 | `ai.picovoice:porcupine-android` |
| 웨이크워드 | "헤이 바라" |
| 동작 방식 | Foreground Service에서 마이크 상시 대기 |
| 모델 | Picovoice 콘솔에서 커스텀 웨이크워드 생성 (한국어 지원) |
| 크기 | ~10MB |

### 2. STT - Sherpa-ONNX (Zipformer Korean)

| 항목 | 내용 |
|------|------|
| 라이브러리 | `com.k2fsa.sherpa:sherpa-onnx` |
| 모델 | `sherpa-onnx-streaming-zipformer-korean-2024-06-16` |
| 크기 | ~60MB (encoder + decoder + joiner) |
| RAM | ~200MB |
| 지연 | ~160ms (실시간 스트리밍) |
| CER | ~9.91% (KsponSpeech eval_clean) |
| 특징 | 온디바이스, 오프라인, 한국어 전용 모델 |

### 3. NLP 엔진 (택 1)

#### 모드 A: Gemma 3n E2B (온디바이스)

| 항목 | 내용 |
|------|------|
| 라이브러리 | `com.google.mediapipe:tasks-genai` (Google AI Edge) |
| 파라미터 | 5B (실제) |
| RAM | ~2GB |
| 특징 | 오프라인, 무료, 한국어 지원 |
| NPU 가속 | Snapdragon 8 Elite Hexagon NPU 활용 |

#### 모드 B: Gemini API (클라우드)

| 항목 | 내용 |
|------|------|
| API | Google Gemini API |
| 비용 | 무료 티어 있음 |
| 특징 | 한국어 우수, Function Calling 지원 |
| 필요 | 인터넷 연결 |

### 4. AI Agent - Koog

| 항목 | 내용 |
|------|------|
| 라이브러리 | JetBrains Koog |
| 역할 | Tool 등록, 모델 연결, Function Calling 관리 |
| 플랫폼 | Kotlin Multiplatform (Android 지원) |
| 특징 | MCP 지원, 상태 관리, retry, persistence |

### 5. TTS - Supertonic 2

| 항목 | 내용 |
|------|------|
| 개발사 | Supertone (한국) |
| 파라미터 | 66M |
| 크기 | ~60MB |
| 속도 | 167x 실시간 (데스크탑), 모바일 3-5 step 추론 |
| 한국어 | 네이티브 지원 (한국 회사) |
| 런타임 | ONNX |

---

## 기능 목록

### 전화

| 기능 | 구현 방식 |
|------|----------|
| 전화 걸기 | `url_launcher` (tel: scheme) 또는 `Intent.ACTION_CALL` |
| 연락처 검색 | ContactsContract API |
| 권한 | `CALL_PHONE`, `READ_CONTACTS` |

### 문자 (SMS)

| 기능 | 구현 방식 |
|------|----------|
| SMS 조회 | Telephony API (`getInboxSms`) |
| SMS 발송 | `SmsManager.sendTextMessage()` |
| 권한 | `READ_SMS`, `SEND_SMS` |

### 카카오톡

| 기능 | 구현 방식 |
|------|----------|
| 메시지 전송 | AccessibilityService (UI 자동 조작) |
| 메시지 조회 | NotificationListenerService (알림 읽기) |
| 권한 | 접근성 서비스 허용, 알림 접근 허용 (수동 설정) |
| 주의 | 카카오톡 UI 업데이트 시 View ID 변경될 수 있음 |

### 캘린더 (일정 + 할일)

| 기능 | 구현 방식 |
|------|----------|
| 일정 조회 | Google Calendar API (`events.list`) |
| 일정 생성 | Google Calendar API (`events.insert`) |
| 일정 수정/삭제 | Google Calendar API (`events.update`, `events.delete`) |
| 할일 조회 | Google Tasks API (`tasks.list`) |
| 할일 추가 | Google Tasks API (`tasks.insert`) |
| 할일 완료 처리 | Google Tasks API (`tasks.update`, status: completed) |
| 인증 | Google Sign-In (OAuth 2.0) |
| 권한 scope | `calendar`, `tasks` |

### 알림 조회

| 기능 | 구현 방식 |
|------|----------|
| 현재 알림 조회 | NotificationListenerService (`activeNotifications`) |
| 실시간 알림 감지 | `onNotificationPosted` 콜백 |
| 앱별 필터링 | `packageName` 으로 필터 (예: `com.kakao.talk`) |
| 권한 | 알림 접근 허용 (설정에서 수동) |

---

## Android 권한 요약

```xml
<!-- AndroidManifest.xml -->

<!-- 마이크 (웨이크워드 + STT) -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>

<!-- 전화 -->
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>

<!-- 문자 -->
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.SEND_SMS"/>

<!-- 통화 기록 -->
<uses-permission android:name="android.permission.READ_CALL_LOG"/>

<!-- 인터넷 (Gemini API, Google Calendar/Tasks API) -->
<uses-permission android:name="android.permission.INTERNET"/>

<!-- Notification Listener (알림 조회, 카톡 메시지 읽기) -->
<!-- AndroidManifest에 서비스 등록 필요 -->

<!-- Accessibility Service (카톡 자동 전송) -->
<!-- AndroidManifest에 서비스 등록 필요 -->
```

---

## RAM 사용량 시나리오 (Galaxy S25 Edge, 12GB)

```
모드 A (Gemma 3n 온디바이스):
  Android OS + 기본 앱       ~4.0GB
  Porcupine                  ~0.01GB
  Sherpa Zipformer           ~0.2GB
  Gemma 3n E2B               ~2.0GB
  Supertonic 2               ~0.06GB
  기타 서비스                 ~0.5GB
  ─────────────────────────
  합계                       ~6.3GB / 12GB ✅

모드 B (Gemini API 클라우드):
  Android OS + 기본 앱       ~4.0GB
  Porcupine                  ~0.01GB
  Sherpa Zipformer           ~0.2GB
  Supertonic 2               ~0.06GB
  기타 서비스                 ~0.5GB
  ─────────────────────────
  합계                       ~4.3GB / 12GB ✅✅
```

---

## 사전 설정 필요 사항

1. **Picovoice 콘솔** - "헤이 바라" 커스텀 웨이크워드 모델 생성
2. **Google Cloud Console** - 프로젝트 생성, Google Calendar API / Google Tasks API 활성화, OAuth 2.0 클라이언트 ID
3. **Google AI Studio** - Gemini API 키 발급 (클라우드 모드용)
4. **Sherpa-ONNX 모델** - `sherpa-onnx-streaming-zipformer-korean-2024-06-16` 다운로드
5. **Supertonic 2 모델** - ONNX 모델 다운로드
6. **Gemma 3n** - Google AI Edge 모델 다운로드 (온디바이스 모드용)

---

## Google Play 정책 참고

- SMS/전화 권한: Google Play 심사 엄격 → **개인용 APK 사이드로딩 추천**
- Accessibility Service: 스토어 등록 사실상 불가 → **사이드로딩**
- Notification Listener: 사유 명시 필요
- **결론: 개인용 사이드로딩으로 사용**

---

## 명령 예시

```
"헤이 바라, 엄마한테 전화해"
  → STT → Koog Agent → CallManager → 연락처 "엄마" 검색 → 전화 걸기

"헤이 바라, 철수한테 밥 먹자고 카톡 보내줘"
  → STT → Koog Agent → KakaoSender → Accessibility로 자동 전송

"헤이 바라, 오늘 일정 알려줘"
  → STT → Koog Agent → CalendarManager → Calendar API 조회 → TTS로 안내

"헤이 바라, 내일 오후 3시에 치과 일정 추가해줘"
  → STT → Koog Agent → CalendarManager → 일정 생성 → TTS 확인

"헤이 바라, 장보기 할일 추가해줘"
  → STT → Koog Agent → TaskManager → Tasks API로 할일 추가 → TTS 확인

"헤이 바라, 남은 할일 뭐 있어?"
  → STT → Koog Agent → TaskManager → 미완료 할일 조회 → TTS로 읽어주기

"헤이 바라, 알림 뭐 왔어?"
  → STT → Koog Agent → NotificationReader → 전체 알림 조회 → TTS로 요약
```
