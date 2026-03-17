# Evaluation: Calendar + Tasks — Design Spec

## 개요

기존 evaluation 시스템에 캘린더/할일 Tool 평가를 추가한다.

**범위:**
- MockStateStore에 events/tasks 상태 추가
- EvalTools에 8개 Mock Tool 추가 (calendar CRUD + tasks CRUD)
- 별도 데이터셋 파일 (default-events.yaml, default-tasks.yaml)
- 12개 테스트 태스크 (기본 8 + 복잡 2 + roleplay 2)
- 시스템 프롬프트에 고정 시각 + 캘린더/할일 가이드 추가

---

## Mock 확장

### MockStateStore

기존 `calls`, `sms`, `searchQueries`에 추가:

```kotlin
data class EventRecord(
    val id: String,
    val title: String,
    val startDateTime: String,
    val endDateTime: String,
    val description: String = ""
)

data class TaskRecord(
    val id: String,
    val title: String,
    val status: String,       // "needsAction" | "completed"
    val dueDate: String = "",
    val notes: String = ""
)
```

메서드:
- `loadEvents(list)`, `loadTasks(list)` — 데이터셋에서 초기 데이터 주입
- `recordCreateEvent(...)`, `recordUpdateEvent(...)`, `recordDeleteEvent(...)`
- `recordCreateTask(...)`, `recordCompleteTask(...)`, `recordDeleteTask(...)`
- `get()` 경로에 `events.*`, `tasks.*` 추가

### EvalTools (8개 Mock Tool 추가)

기존 `createSearchContactsTool` 패턴과 동일한 factory 함수:

**캘린더:**
- `createListEventsTool(stateStore)` — events를 date/days로 필터링하여 반환
- `createCreateEventTool(stateStore)` — events에 추가
- `createUpdateEventTool(stateStore)` — events에서 찾아 수정
- `createDeleteEventTool(stateStore)` — events에서 삭제

**할일:**
- `createListTasksTool(stateStore)` — tasks 반환 (showCompleted 필터)
- `createCreateTaskTool(stateStore)` — tasks에 추가
- `createCompleteTaskTool(stateStore)` — status를 completed로 변경
- `createDeleteTaskTool(stateStore)` — tasks에서 삭제

각 Tool의 Args는 실제 KoogAgentEngine의 Tool Args와 동일한 스키마.

---

## 데이터셋

### default-events.yaml

고정 시각 `2026-03-17 14:00 (화)` 기준으로 구성:

```yaml
events:
  - id: "evt-001"
    title: "팀 미팅"
    startDateTime: "2026-03-17T10:00:00+09:00"
    endDateTime: "2026-03-17T11:00:00+09:00"
  - id: "evt-002"
    title: "점심 약속"
    startDateTime: "2026-03-17T12:30:00+09:00"
    endDateTime: "2026-03-17T13:30:00+09:00"
  - id: "evt-003"
    title: "치과"
    startDateTime: "2026-03-18T15:00:00+09:00"
    endDateTime: "2026-03-18T16:00:00+09:00"
  - id: "evt-004"
    title: "프로젝트 마감"
    startDateTime: "2026-03-20T09:00:00+09:00"
    endDateTime: "2026-03-20T18:00:00+09:00"
  - id: "evt-005"
    title: "주간 회의"
    startDateTime: "2026-03-24T14:00:00+09:00"
    endDateTime: "2026-03-24T15:00:00+09:00"
```

### default-tasks.yaml

```yaml
tasks:
  - id: "task-001"
    title: "장보기"
    status: "needsAction"
    dueDate: "2026-03-18"
  - id: "task-002"
    title: "세탁소 옷 찾기"
    status: "needsAction"
    dueDate: "2026-03-17"
  - id: "task-003"
    title: "보고서 작성"
    status: "needsAction"
  - id: "task-004"
    title: "운동"
    status: "completed"
    dueDate: "2026-03-16"
```

---

## 시스템 프롬프트 변경

EvalAgentFactory의 SYSTEM_PROMPT에 추가:

```
현재 시각: 2026-03-17 14:00 (화요일)
타임존: Asia/Seoul (KST, +09:00)

일정 관련 요청이 오면 캘린더 도구(list_events, create_event, update_event, delete_event)를 사용해.
할일 관련 요청이 오면 할일 도구(list_tasks, create_task, complete_task, delete_task)를 사용해.

규칙:
- 일정 수정/삭제 전에 list_events로 eventId를 먼저 확인해
- 할일 완료/삭제 전에 list_tasks로 taskId를 먼저 확인해
- 날짜는 ISO 8601 형식으로 변환해 (예: 2026-03-18T15:00:00+09:00)
- "내일", "다음 주 월요일" 같은 상대 날짜는 현재 시각 기준으로 계산해
```

---

## 테스트 태스크 (12개)

### 기본 (8개, single-turn)

#### calendar-list-001.yaml
- input: "오늘 일정 알려줘"
- expected: list_events 호출, 출력에 "팀 미팅", "점심 약속" 포함
- dataset: default-events

#### calendar-create-001.yaml
- input: "금요일 오전 10시에 회의 추가해줘"
- expected: create_event 호출, state에 event 추가됨
- dataset: default-events

#### calendar-delete-001.yaml
- input: "치과 일정 삭제해줘"
- expected: list_events → delete_event 호출, eventId=evt-003
- dataset: default-events

#### calendar-update-001.yaml
- input: "치과 일정을 4시로 변경해줘"
- expected: list_events → update_event 호출, eventId=evt-003
- dataset: default-events

#### tasks-list-001.yaml
- input: "할일 뭐 있어?"
- expected: list_tasks 호출, 출력에 "장보기", "세탁소" 포함
- dataset: default-tasks

#### tasks-create-001.yaml
- input: "약 사기 할일 추가해줘"
- expected: create_task 호출, title에 "약" 포함
- dataset: default-tasks

#### tasks-complete-001.yaml
- input: "장보기 완료"
- expected: list_tasks → complete_task 호출, taskId=task-001
- dataset: default-tasks

#### tasks-delete-001.yaml
- input: "보고서 작성 할일 삭제해줘"
- expected: list_tasks → delete_task 호출, taskId=task-003
- dataset: default-tasks

### 복잡한 시나리오 (2개, single-turn 멀티스텝)

#### calendar-list-002.yaml
- input: "이번 주 일정 전부 알려줘"
- expected: list_events(days>=5), 여러 일정 포함
- dataset: default-events

#### calendar-create-002.yaml
- input: "내일 오후 3시부터 5시까지 스터디 추가해줘"
- expected: create_event, start=2026-03-18T15:00, end=2026-03-18T17:00
- dataset: default-events

### Roleplay (2개, multi-turn)

#### roleplay/calendar-modify-001.yaml
- 시나리오: 사용자가 모호하게 일정 수정 요청 → AI가 질문 → 구체화
- turns: "일정 하나 수정해줘" → (AI: 어떤 일정?) → "치과" → (AI: 뭘 변경?) → "4시로"
- expected: update_event 호출
- dataset: default-events

#### roleplay/tasks-workflow-001.yaml
- 시나리오: 할일 추가 시 기한 질문
- turns: "할일 추가해줘" → (AI: 뭘 추가?) → "청소" → (AI: 기한은?) → "이번 주 금요일"
- expected: create_task(title=청소, dueDate=2026-03-21)
- dataset: default-tasks

---

## EvalAgentFactory 변경

`create()` 및 `createWithHistory()`에서:
- 8개 캘린더/할일 Tool을 ToolRegistry에 등록
- MockStateStore에 데이터셋 로드 (events, tasks)
- dataset 필드: `default-contacts`, `default-events`, `default-tasks` (복수 지정 가능)

---

## 수정 파일 요약

| 파일 | 변경 |
|------|------|
| `mocks/MockStateStore.kt` | EventRecord, TaskRecord, load/record 메서드, get() 경로 확장 |
| `agent/EvalTools.kt` | 8개 Mock Tool factory 함수 + Args 클래스 |
| `agent/EvalAgentFactory.kt` | Tool 등록, 시스템 프롬프트, 데이터셋 로딩 |
| `datasets/default-events.yaml` | 캘린더 초기 데이터 (5건) |
| `datasets/default-tasks.yaml` | 할일 초기 데이터 (4건) |
| `tasks/voice-agent/calendar-*.yaml` | 캘린더 태스크 6개 |
| `tasks/voice-agent/tasks-*.yaml` | 할일 태스크 4개 |
| `tasks/voice-agent/roleplay/calendar-modify-001.yaml` | Roleplay 1개 |
| `tasks/voice-agent/roleplay/tasks-workflow-001.yaml` | Roleplay 1개 |
