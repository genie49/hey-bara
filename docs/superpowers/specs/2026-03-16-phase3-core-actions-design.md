# Phase 3: Core Actions — Design Spec

## 개요

연락처 검색, 전화 걸기, SMS 보내기 기능을 실제 연결하고, 대화 히스토리를 Room DB에 저장한다.

**범위:**
- 연락처 검색 (ContentResolver + LLM 매칭)
- 전화 걸기 (Intent.ACTION_CALL)
- SMS 보내기 (android.telephony.SmsManager)
- 대화 히스토리 (Room DB + LLM 요약 + HistoryActivity)

**범위 외 (Phase 4+):**
- 캘린더/할일 (Google API 연동)
- 카카오톡 (AccessibilityService)
- 알림 조회 (NotificationListener)

---

## 아키텍처

```
사용자: "엄마한테 지금 간다고 문자 보내줘"
    │
[KoogAgentEngine]
    ├── SearchContactsTool("엄마") → [김영희 010-1234-5678]
    ├── LLM 판단 → SendSms 액션 생성
    └── AI 응답: "엄마한테 '지금 간다'라고 문자를 보낼까요?"
    │
[VoiceSession] CONFIRMING 상태
    ├── 사용자 "응" → ActionExecutor.execute(SendSms)
    └── 사용자 "취소" → 취소
    │
[대화 종료] → LLM 요약 → Room DB 저장 (백그라운드)
```

---

## 패키지 구조 (추가분)

```
domain/
├── action/
│   ├── ActionExecutor.kt           ← 인터페이스
│   └── ContactResolver.kt         ← 인터페이스 + Contact 모델
├── history/
│   ├── Conversation.kt            ← 대화 모델
│   └── ConversationRepository.kt  ← 인터페이스

data/
├── action/
│   ├── ActionExecutorImpl.kt      ← Call/Sms 분기 실행
│   ├── CallExecutor.kt            ← Intent.ACTION_CALL
│   ├── SmsExecutor.kt             ← SmsManager API
│   └── DeviceContactResolver.kt   ← ContentResolver 검색
├── history/
│   ├── ConversationEntity.kt      ← Room @Entity
│   ├── ConversationDao.kt         ← Room @Dao
│   ├── AppDatabase.kt             ← Room Database
│   └── RoomConversationRepository.kt

ui/
├── HistoryActivity.kt             ← 대화 히스토리 화면
```

---

## AgentAction 확장

```kotlin
sealed class AgentAction {
    data class Call(val contact: String, val phoneNumber: String?) : AgentAction()
    data class SendSms(val contact: String, val phoneNumber: String?, val message: String) : AgentAction()
}
```

- `phoneNumber`은 nullable — LLM이 이름만 추출하면 ContactResolver가 번호 조회
- Phase 4에서 Kakao, Calendar, Task 등 추가

---

## ActionExecutor

```kotlin
// domain/action/ActionExecutor.kt
interface ActionExecutor {
    suspend fun execute(action: AgentAction): Boolean
}
```

- `true` → 실행 성공 (별도 TTS 안내 없음)
- `false` → 실행 실패 → AI가 에러 상황 판단하여 응답 생성
- TTS는 오직 AI의 응답과 확인 질문만 읽어줌

### ActionExecutorImpl

```kotlin
// data/action/ActionExecutorImpl.kt
class ActionExecutorImpl(
    private val callExecutor: CallExecutor,
    private val smsExecutor: SmsExecutor
) : ActionExecutor {

    override suspend fun execute(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.Call -> callExecutor.call(action.phoneNumber!!)
            is AgentAction.SendSms -> smsExecutor.sendSms(action.phoneNumber!!, action.message)
        }
    }
}
```

---

## ContactResolver (키워드 검색 + LLM 매칭)

```kotlin
// domain/action/ContactResolver.kt
interface ContactResolver {
    suspend fun searchContacts(query: String): List<Contact>
}

data class Contact(
    val name: String,
    val phoneNumber: String
)
```

### 동작 흐름

```
1. LLM이 사용자 발화에서 이름 추출 (예: "엄마")
2. SearchContactsTool이 ContactResolver.searchContacts("엄마") 호출
3. DeviceContactResolver: ContentResolver에서 DISPLAY_NAME LIKE '%엄마%' 검색
4. 소수의 결과만 LLM에 반환
5. LLM이 판단:
   - 1개 → 바로 액션 생성
   - 여러 개 → "김영희, 이순자 중 누구요?" 확인 질문
   - 0개 → "연락처에서 엄마를 찾을 수 없어요"
```

### DeviceContactResolver

```kotlin
// data/action/DeviceContactResolver.kt
// ContentResolver로 디바이스 연락처 검색
// READ_CONTACTS 권한 필요
```

---

## KoogAgentEngine Tool 확장

### Tool 정의 (3개)

```
1. SearchContactsTool
   - name: "search_contacts"
   - description: "연락처에서 이름으로 검색한다"
   - param: query (String)
   - 반환: 검색 결과 목록 (이름 + 번호)

2. MakeCallTool (기존 수정)
   - name: "make_call"
   - param: phoneNumber (String)
   - Tool 결과만 반환, 실제 실행은 CONFIRMING 후 ActionExecutor

3. SendSmsTool
   - name: "send_sms"
   - params: phoneNumber (String), message (String)
   - Tool 결과만 반환, 실제 실행은 CONFIRMING 후 ActionExecutor
```

### 흐름

- Tool은 LLM의 판단 결과를 반환만 함
- 실제 전화/SMS 실행은 사용자 확인 후 ActionExecutor가 담당

---

## 대화 히스토리

### 데이터 모델

```kotlin
data class Conversation(
    val id: Long = 0,
    val topic: String,          // LLM 요약 (예: "엄마한테 전화 걸기")
    val category: String,       // "call" | "sms" | "chat"
    val inputMode: String,      // "voice" | "text"
    val timestamp: Long,
    val transcript: String      // 대화 전문 JSON
)
```

### LLM 요약 흐름

```
대화 종료 시:
1. 대화 기록을 LLM에 전달
2. LLM이 요약: { topic: "엄마한테 전화 걸기", category: "call" }
3. Room DB에 저장
4. 백그라운드 코루틴 (IDLE 복귀를 블로킹하지 않음)
5. 요약 실패 시 기본값: topic="대화", category="chat"
```

### HistoryActivity (Pencil 디자인 기반)

- 날짜별 그룹핑 (오늘, 어제, ...)
- 카테고리 아이콘 + 색상 (call=coral, sms=teal, chat=gray)
- 시간 + 입력 모드 (음성/텍스트) 표시
- 헤더 히스토리 아이콘에서 진입

---

## 권한 추가

```xml
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
```

런타임 권한 요청은 MainActivity에서 기존 패턴 확장.

---

## 의존성 추가

| 라이브러리 | 용도 |
|-----------|------|
| Room | 대화 히스토리 로컬 DB |
| Room KSP | 어노테이션 프로세서 |

---

## 테스트 전략

### 유닛 테스트

1. ActionExecutor — Call/SendSms 분기 실행 검증
2. VoiceSession + ActionExecutor — CONFIRMING → confirmAction → execute 호출

### 에뮬레이터 테스트

1. 텍스트 채팅: "엄마한테 전화해" → 연락처 검색 → 확인 응답
2. 텍스트 채팅: "철수한테 밥 먹자고 문자 보내줘" → SMS 확인
3. 대화 종료 후 히스토리 저장 확인
4. HistoryActivity 날짜별 그룹핑 표시 확인
