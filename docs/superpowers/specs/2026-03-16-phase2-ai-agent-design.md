# Phase 2: AI Agent Connection — Design Spec

## 개요

STT 텍스트를 Koog Agent + Gemini API로 전달하여 명령을 파싱하고 응답을 생성한다.
Phase 2에서는 Actions를 실제 실행하지 않고 로그만 출력한다.

**핵심 목표:**
- Koog Agent + Gemini API Function Calling 동작 검증
- Clean Architecture 기반 AgentEngine 추상 레이어 구축
- SettingsActivity에서 API Key 입력/저장 (EncryptedSharedPreferences)
- CONFIRMING 상태를 통한 확인/취소 흐름 구현

---

## 아키텍처

```
사용자: "엄마한테 전화해"
    │
[VoiceAssistantService] STT 결과 수신
    │
[domain] AgentEngine.process("엄마한테 전화해")
    │
[data] KoogAgentEngine
    ├── Koog Agent + Gemini API로 명령 파싱
    ├── Tool 호출: CallTool("엄마")
    │   └── Phase 2에서는 로그만 출력
    └── 응답 텍스트 반환: "엄마한테 전화를 걸까요?"
    │
[VoiceAssistantService] TTS로 응답
```

---

## 패키지 구조 (추가분)

```
domain/
├── agent/
│   ├── AgentEngine.kt          ← 인터페이스
│   └── AgentResponse.kt        ← 응답 모델 (AgentResponse, AgentAction)

data/
├── agent/
│   └── KoogAgentEngine.kt      ← Koog + Gemini 구현체
├── settings/
│   └── SecurePreferences.kt    ← EncryptedSharedPreferences 래퍼

ui/
├── SettingsActivity.kt          ← API Key 입력 화면
```

---

## AgentEngine 인터페이스

```kotlin
// domain/agent/AgentEngine.kt
interface AgentEngine {
    suspend fun process(text: String): AgentResponse
    fun release()
}
```

```kotlin
// domain/agent/AgentResponse.kt
data class AgentResponse(
    val text: String,                   // TTS로 읽어줄 응답
    val action: AgentAction?,           // 실행할 액션 (없으면 null)
    val requiresConfirmation: Boolean   // 확인 필요 여부
)

sealed class AgentAction {
    data class Call(val contact: String) : AgentAction()
    // Phase 3에서 추가: Sms, Kakao, Calendar, Task, Notification
}
```

---

## KoogAgentEngine 구현

```kotlin
// data/agent/KoogAgentEngine.kt
class KoogAgentEngine(
    private val apiKey: String
) : AgentEngine
```

### 구성요소

- **LLM 백엔드**: Gemini API (apiKey로 인증)
- **Tool 정의**: CallTool 1개 (name: "make_call", param: contact)
- **시스템 프롬프트**: 한국어 음성 비서 역할 정의

### 시스템 프롬프트

```
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.
```

### Tool 정의 (Phase 2: Call만)

```
name: "make_call"
description: "연락처에게 전화를 건다"
parameter: contact (String) - 전화할 상대 이름

Phase 2에서는 실행 대신 로그만:
Log.d("AgentTool", "Call requested: contact=$contact")
```

---

## 설정 화면 + API Key 관리

### SecurePreferences

```kotlin
// data/settings/SecurePreferences.kt
// EncryptedSharedPreferences 래퍼
// - getGeminiApiKey(): String?
// - setGeminiApiKey(key: String)
// - clearGeminiApiKey()
```

- Android Keystore 기반 AES-256 암호화
- Jetpack Security 라이브러리 사용

### SettingsActivity

- API Key 입력 필드 + 저장 버튼
- 저장된 키가 있으면 마스킹 표시 (예: `sk-...xxxx`)
- 키 유효성 검증: 저장 시 Gemini API에 테스트 요청
- 실패 시 "유효하지 않은 API Key입니다" 안내

### VoiceAssistantService 연결 흐름

```
앱 시작
  → API Key 확인 (SecurePreferences)
    → 없음: 서비스 시작 안 함, UI에 "설정에서 API Key를 입력해주세요" 표시
    → 있음: VoiceAssistantService 시작 → KoogAgentEngine 초기화
```

### MainActivity에서 설정 진입

- 헤더에 설정 아이콘 추가
- 탭하면 SettingsActivity로 이동

---

## VoiceSession 상태 흐름 변경

### Phase 1 vs Phase 2

```
Phase 1: IDLE → LISTENING → PROCESSING → (에코백) → IDLE
Phase 2: IDLE → LISTENING → PROCESSING → (AgentEngine) → CONFIRMING → IDLE
```

### VoiceSession 변경사항

PROCESSING 상태에서:
1. `AgentEngine.process(recognizedText)` 호출
2. 응답에 따라:
   - `requiresConfirmation=true` → CONFIRMING 상태로 전이, TTS로 확인 요청
   - `requiresConfirmation=false` → TTS로 응답 후 IDLE

CONFIRMING 상태에서:
- STT로 "응"/"네" 감지 → action 실행 (Phase 2에서는 로그) → IDLE
- STT로 "아니"/"취소" 감지 → TTS "취소할게요" → IDLE
- 타임아웃 (5초) → TTS "취소할게요" → IDLE

---

## 에러 처리

| 에러 | 동작 |
|------|------|
| 네트워크 에러 | TTS "인터넷 연결이 안 돼요" → IDLE |
| LLM 파싱 실패 | TTS "이해하지 못했어요" → IDLE |
| API Key 무효 | TTS "API 키를 확인해주세요" → IDLE |

---

## 테스트 전략

### 유닛 테스트

1. **AgentEngine 인터페이스 테스트**
   - mock AgentEngine으로 VoiceSession 상태 전이 검증
   - PROCESSING → CONFIRMING 전이
   - 확인/취소 응답 처리
   - 에러 시 IDLE 복귀

2. **AgentResponse 처리 테스트**
   - `requiresConfirmation=true` → CONFIRMING
   - `requiresConfirmation=false` → 바로 TTS 후 IDLE
   - `action=null` (일반 대화) 처리

3. **SecurePreferences 테스트**
   - 저장/조회/삭제 동작 확인

### 통합 테스트 (에뮬레이터)

1. API Key 미설정 시 → 설정 안내 표시 확인
2. SettingsActivity에서 키 입력 → 저장 확인
3. 서비스 시작 → Gemini API 연결 확인

### 수동 E2E 테스트 (실 디바이스)

1. "헤이 바라" → "엄마한테 전화해" → TTS: "엄마한테 전화를 걸까요?" → "응" → 로그 출력 + TTS: "전화를 걸게요"
2. "헤이 바라" → "오늘 날씨 어때?" → TTS: 일반 대화 응답 (Tool 호출 없음)

---

## 의존성

### 추가 라이브러리

| 라이브러리 | 용도 |
|-----------|------|
| Koog (JetBrains) | AI Agent 프레임워크 |
| Gemini API SDK | LLM 백엔드 |
| Jetpack Security | EncryptedSharedPreferences |

---

## Phase 2 검증 게이트

Koog + Gemini API Function Calling이 안정적으로 동작하는지 검증:
- Tool calling JSON이 올바르게 생성되는지
- 한국어 명령을 정확히 파싱하는지
- 실패 시: cloud 모드 유지, Phase 3에서 on-device 검증 (Gemma 3n)
