# Phase 4: Calendar + Tasks — Design Spec

## 개요

Google Calendar API와 Google Tasks API를 연동하여 일정 및 할일 관리 기능을 추가한다.

**범위:**
- Google OAuth 2.0 인증 (Credential Manager)
- 캘린더 일정 CRUD (조회, 생성, 수정, 삭제)
- 할일 CRUD (조회, 생성, 완료, 삭제)

**범위 외:**
- 카카오톡 (AccessibilityService) — Phase 5
- 알림 조회 (NotificationListener) — Phase 5
- 온디바이스 NLP (Gemma 3n) — 별도
- TTS 교체 (Supertonic 2) — 별도

---

## 인증

### 방식

Google OAuth 2.0 + Credential Manager (Android 최신 권장 방식)

### 사전 설정

- Google Cloud Console에서 프로젝트 생성
- Calendar API + Tasks API 활성화
- OAuth 2.0 클라이언트 ID (Android 타입) 생성 — SHA-1 디버그 지문 등록

### OAuth Scopes

```
https://www.googleapis.com/auth/calendar
https://www.googleapis.com/auth/tasks
```

### 토큰 관리

- `GoogleAuthManager`가 Credential Manager를 래핑
- access token은 메모리 보관, 만료 시 자동 갱신
- 계정 이메일은 `SecurePreferences`에 저장 (연결 상태 표시용)

### 인증 시점

- 설정 화면에서 "Google 계정 연결" 버튼으로 미리 인증
- 메인 화면에서는 차단하지 않음 (캘린더 없이도 전화/문자/채팅 사용 가능)
- Tool 호출 시 미인증이면 "Google 계정이 연결되지 않았습니다" 반환

---

## Tool 정의

### 캘린더 Tool (4개)

#### list_events
- **description**: "캘린더에서 일정을 조회한다"
- **params**: `date` (String, YYYY-MM-DD), `days` (Int, 조회 범위 일수, 기본 1)
- **반환**: 일정 목록 (제목, 시작/종료 시간, eventId)
- **API**: `events.list` with timeMin/timeMax

#### create_event
- **description**: "캘린더에 새 일정을 추가한다"
- **params**: `title` (String), `startDateTime` (String, ISO 8601), `endDateTime` (String, optional — 기본 시작+1시간), `description` (String, optional)
- **반환**: 생성된 일정 정보
- **API**: `events.insert`

#### update_event
- **description**: "캘린더 일정을 수정한다. list_events로 eventId를 먼저 확인해야 한다"
- **params**: `eventId` (String), `title` (String, optional), `startDateTime` (String, optional), `endDateTime` (String, optional)
- **반환**: 수정된 일정 정보
- **API**: `events.patch`

#### delete_event
- **description**: "캘린더 일정을 삭제한다. list_events로 eventId를 먼저 확인해야 한다"
- **params**: `eventId` (String)
- **반환**: 삭제 결과
- **API**: `events.delete`

### 할일 Tool (4개)

#### list_tasks
- **description**: "할일 목록을 조회한다"
- **params**: `showCompleted` (Boolean, 기본 false)
- **반환**: 할일 목록 (제목, 상태, 기한, taskId)
- **API**: `tasks.list` (기본 task list "@default")

#### create_task
- **description**: "새 할일을 추가한다"
- **params**: `title` (String), `dueDate` (String, YYYY-MM-DD, optional), `notes` (String, optional)
- **반환**: 생성된 할일 정보
- **API**: `tasks.insert`

#### complete_task
- **description**: "할일을 완료 처리한다. list_tasks로 taskId를 먼저 확인해야 한다"
- **params**: `taskId` (String)
- **반환**: 완료 결과
- **API**: `tasks.patch` (status: "completed")

#### delete_task
- **description**: "할일을 삭제한다. list_tasks로 taskId를 먼저 확인해야 한다"
- **params**: `taskId` (String)
- **반환**: 삭제 결과
- **API**: `tasks.delete`

### 확인 모달

없음. 캘린더/할일은 실수해도 치명적이지 않으므로 AI가 바로 실행.

---

## 패키지 구조 (추가분)

```
data/
├── auth/
│   └── GoogleAuthManager.kt      ← Credential Manager 래핑, 토큰 관리
├── calendar/
│   └── GoogleCalendarClient.kt   ← Calendar API CRUD
└── tasks/
    └── GoogleTasksClient.kt      ← Tasks API CRUD
```

KoogAgentEngine에 Tool 8개 추가 (기존 SimpleTool 패턴).

---

## 시스템 프롬프트 변경

현재 날짜/시간을 동적으로 주입:

```
현재 시각: 2026-03-17 14:30 (월요일)

캘린더와 할일 관련 요청이 오면:
- 일정 수정/삭제 전에 list_events로 eventId를 먼저 확인해
- 할일 완료/삭제 전에 list_tasks로 taskId를 먼저 확인해
- 날짜는 ISO 8601 형식으로 변환해 (예: 2026-03-18T15:00:00+09:00)
- "내일", "다음 주 월요일" 같은 상대 날짜는 현재 시각 기준으로 계산해
```

---

## 데이터 흐름 예시

### "내일 오후 3시에 치과 추가해줘"

```
사용자 → KoogAgentEngine
  → LLM: 현재 시각 2026-03-17 → 내일 = 2026-03-18
  → create_event(title="치과", startDateTime="2026-03-18T15:00:00+09:00")
    → GoogleCalendarClient.createEvent(...)
      → Calendar API POST
    → "치과 일정을 2026-03-18 15:00에 추가했습니다"
  → LLM 응답: "내일 오후 3시에 치과 일정을 추가했어요"
```

### "오늘 일정 알려줘"

```
사용자 → KoogAgentEngine
  → list_events(date="2026-03-17", days=1)
    → GoogleCalendarClient.listEvents(...)
      → Calendar API GET
    → "1. 팀 미팅 10:00-11:00\n2. 점심 약속 12:30-13:30"
  → LLM 응답: "오늘 일정은 두 개예요. 오전 10시에 팀 미팅, 12시 반에 점심 약속이 있어요"
```

### "장보기 할일 추가해줘"

```
사용자 → KoogAgentEngine
  → create_task(title="장보기")
    → GoogleTasksClient.createTask(...)
    → "할일 '장보기'를 추가했습니다"
  → LLM 응답: "장보기 할일을 추가했어요"
```

---

## 설정 UI

### Google 계정 섹션 (SettingsActivity)

기존 API Key, 음성 모델 섹션과 동일한 `SettingsSection` 패턴:

**미연결 상태:**
- "연결" 버튼 → Credential Manager 계정 선택 팝업

**연결됨 상태:**
- "user@gmail.com" 표시 + X (연결 해제) 버튼

### 메인 화면

- Google 미연결이어도 채팅 활성화 (기존 조건 변경 없음)
- Tool에서 미인증 시 안내 메시지 반환으로 충분

---

## 에러 처리

| 상황 | Tool 반환값 |
|------|-----------|
| Google 미인증 | "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요" |
| 토큰 만료 | 자동 갱신 시도, 실패 시 "Google 인증이 만료되었습니다. 설정에서 다시 연결해 주세요" |
| API 오류 | "일정을 가져오는 데 실패했습니다: {에러 메시지}" |
| 일정/할일 없음 | "조회 기간에 일정이 없습니다" / "할일이 없습니다" |
| eventId/taskId 없음 | LLM이 먼저 list를 호출하도록 시스템 프롬프트에서 유도 |

---

## 의존성 추가

| 라이브러리 | 용도 |
|-----------|------|
| `com.google.android.gms:play-services-auth` | Credential Manager |
| `com.google.api-client:google-api-client-android` | Google API 호출 |
| `com.google.apis:google-api-services-calendar:v3` | Calendar API |
| `com.google.apis:google-api-services-tasks:v1` | Tasks API |

---

## 테스트 시나리오

1. 설정에서 Google 계정 연결/해제
2. "오늘 일정 알려줘" → list_events 호출 → 결과 표시
3. "내일 오후 3시에 치과 추가해줘" → create_event → 확인
4. "치과 일정 취소해줘" → list_events → delete_event
5. "남은 할일 뭐 있어?" → list_tasks
6. "장보기 추가해줘" → create_task
7. "장보기 완료" → list_tasks → complete_task
8. "장보기 삭제해줘" → list_tasks → delete_task
9. Google 미연결 상태에서 "오늘 일정" → 안내 메시지
