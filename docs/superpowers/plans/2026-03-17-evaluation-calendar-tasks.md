# Evaluation: Calendar + Tasks Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 evaluation 시스템에 캘린더/할일 Mock Tool 8개와 테스트 태스크 12개를 추가한다.

**Architecture:** MockStateStore에 events/tasks 상태를 추가하고, EvalTools에 8개 Mock Tool factory를 추가한다. EvalAgentFactory에서 Tool을 등록하고 시스템 프롬프트에 고정 시각을 주입한다. 데이터셋은 별도 YAML 파일로 관리한다.

**Tech Stack:** Koog SimpleTool, kotlinx.serialization, YAML (kaml)

**Spec:** `docs/superpowers/specs/2026-03-17-evaluation-calendar-tasks-design.md`

---

## File Structure

### 신규 파일

| 파일 | 역할 |
|------|------|
| `evaluation/src/main/resources/datasets/default-events.yaml` | 캘린더 초기 데이터 5건 |
| `evaluation/src/main/resources/datasets/default-tasks.yaml` | 할일 초기 데이터 4건 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-list-001.yaml` | 일정 조회 기본 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-create-001.yaml` | 일정 생성 기본 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-delete-001.yaml` | 일정 삭제 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-update-001.yaml` | 일정 수정 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-list-002.yaml` | 이번 주 일정 조회 |
| `evaluation/src/main/resources/tasks/voice-agent/calendar-create-002.yaml` | 시간 범위 지정 생성 |
| `evaluation/src/main/resources/tasks/voice-agent/tasks-list-001.yaml` | 할일 조회 |
| `evaluation/src/main/resources/tasks/voice-agent/tasks-create-001.yaml` | 할일 생성 |
| `evaluation/src/main/resources/tasks/voice-agent/tasks-complete-001.yaml` | 할일 완료 |
| `evaluation/src/main/resources/tasks/voice-agent/tasks-delete-001.yaml` | 할일 삭제 |
| `evaluation/src/main/resources/tasks/voice-agent/roleplay/calendar-modify-001.yaml` | 일정 수정 roleplay |
| `evaluation/src/main/resources/tasks/voice-agent/roleplay/tasks-workflow-001.yaml` | 할일 추가 roleplay |

### 수정 파일

| 파일 | 변경 |
|------|------|
| `evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockStateStore.kt` | EventRecord, TaskRecord, load/record 메서드, get() 확장 |
| `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalTools.kt` | 8개 Mock Tool Args + factory 함수 |
| `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalAgentFactory.kt` | Tool 등록, 시스템 프롬프트, 데이터셋 로딩 |
| `evaluation/src/main/kotlin/com/bara/evaluation/datasets/Contacts.kt` | Events/Tasks 데이터셋 로딩 추가 |
| `evaluation/src/main/kotlin/com/bara/evaluation/core/AgentRunner.kt` | events/tasks tool call 기록 재구성 |

---

## Chunk 1: Mock + Tools

### Task 1: MockStateStore에 events/tasks 추가

**Files:**
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockStateStore.kt`

- [ ] **Step 1: EventRecord, TaskRecord 데이터 클래스 추가**

```kotlin
data class EventRecord(
    val id: String, val title: String,
    val startDateTime: String, val endDateTime: String,
    val description: String = ""
)

data class TaskRecord(
    val id: String, val title: String,
    val status: String, // "needsAction" | "completed"
    val dueDate: String = "", val notes: String = ""
)
```

- [ ] **Step 2: MockStateStore에 events/tasks 필드 및 메서드 추가**

```kotlin
val events = mutableListOf<EventRecord>()
val tasks = mutableListOf<TaskRecord>()

fun loadEvents(list: List<EventRecord>) { events.clear(); events.addAll(list) }
fun loadTasks(list: List<TaskRecord>) { tasks.clear(); tasks.addAll(list) }

fun recordCreateEvent(id: String, title: String, start: String, end: String, desc: String = "") {
    events.add(EventRecord(id, title, start, end, desc))
}
fun recordUpdateEvent(id: String, title: String?, start: String?, end: String?) {
    val idx = events.indexOfFirst { it.id == id }
    if (idx >= 0) {
        val old = events[idx]
        events[idx] = old.copy(
            title = title ?: old.title,
            startDateTime = start ?: old.startDateTime,
            endDateTime = end ?: old.endDateTime
        )
    }
}
fun recordDeleteEvent(id: String) { events.removeAll { it.id == id } }

fun recordCreateTask(id: String, title: String, dueDate: String = "", notes: String = "") {
    tasks.add(TaskRecord(id, title, "needsAction", dueDate, notes))
}
fun recordCompleteTask(id: String) {
    val idx = tasks.indexOfFirst { it.id == id }
    if (idx >= 0) tasks[idx] = tasks[idx].copy(status = "completed")
}
fun recordDeleteTask(id: String) { tasks.removeAll { it.id == id } }
```

- [ ] **Step 3: get() 경로에 events/tasks 추가**

`get()` 메서드의 when 분기에 `"events" -> events`, `"tasks" -> tasks` 추가.
`resolvePath`에서 `EventRecord`, `TaskRecord` 필드 접근 추가.

- [ ] **Step 4: reset()에 events/tasks clear 추가**

- [ ] **Step 5: 빌드 확인**

Run: `cd evaluation && ../gradlew compileKotlin`

- [ ] **Step 6: 커밋**

```bash
git add evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockStateStore.kt
git commit -m "feat(eval): MockStateStore에 events/tasks 상태 추가"
```

---

### Task 2: EvalTools에 캘린더/할일 Mock Tool 추가

**Files:**
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalTools.kt`

- [ ] **Step 1: 캘린더 Args 클래스 4개 추가**

```kotlin
@Serializable
data class ListEventsArgs(
    @property:LLMDescription("조회 시작 날짜 (YYYY-MM-DD)") val date: String,
    @property:LLMDescription("조회 범위 일수 (기본 1)") val days: Int = 1,
)

@Serializable
data class CreateEventArgs(
    @property:LLMDescription("일정 제목") val title: String,
    @property:LLMDescription("시작 시간 (ISO 8601)") val startDateTime: String,
    @property:LLMDescription("종료 시간 (ISO 8601, 생략하면 시작+1시간)") val endDateTime: String = "",
    @property:LLMDescription("설명 (선택)") val description: String = "",
)

@Serializable
data class UpdateEventArgs(
    @property:LLMDescription("일정 ID") val eventId: String,
    @property:LLMDescription("변경할 제목 (선택)") val title: String = "",
    @property:LLMDescription("변경할 시작 시간 (선택)") val startDateTime: String = "",
    @property:LLMDescription("변경할 종료 시간 (선택)") val endDateTime: String = "",
)

@Serializable
data class DeleteEventArgs(
    @property:LLMDescription("삭제할 일정 ID") val eventId: String,
)
```

- [ ] **Step 2: 할일 Args 클래스 4개 추가**

```kotlin
@Serializable
data class ListTasksArgs(
    @property:LLMDescription("완료된 할일도 포함할지 여부 (기본 false)") val showCompleted: Boolean = false,
)

@Serializable
data class CreateTaskArgs(
    @property:LLMDescription("할일 제목") val title: String,
    @property:LLMDescription("기한 (YYYY-MM-DD, 선택)") val dueDate: String = "",
    @property:LLMDescription("메모 (선택)") val notes: String = "",
)

@Serializable
data class CompleteTaskArgs(
    @property:LLMDescription("완료할 할일 ID") val taskId: String,
)

@Serializable
data class DeleteTaskArgs(
    @property:LLMDescription("삭제할 할일 ID") val taskId: String,
)
```

- [ ] **Step 3: 캘린더 Tool factory 함수 4개 추가**

list_events: stateStore.events를 date/days로 필터링하여 반환 (날짜 비교는 startDateTime의 날짜 부분).
create_event: stateStore.recordCreateEvent() 호출, UUID로 id 생성.
update_event: stateStore.recordUpdateEvent() 호출.
delete_event: stateStore.recordDeleteEvent() 호출.

- [ ] **Step 4: 할일 Tool factory 함수 4개 추가**

list_tasks: showCompleted 필터링, 최대 20건.
create_task: stateStore.recordCreateTask() 호출.
complete_task: stateStore.recordCompleteTask() 호출.
delete_task: stateStore.recordDeleteTask() 호출.

- [ ] **Step 5: 빌드 확인**

- [ ] **Step 6: 커밋**

```bash
git add evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalTools.kt
git commit -m "feat(eval): 캘린더/할일 Mock Tool 8개 추가"
```

---

## Chunk 2: Factory + Runner + 데이터셋

### Task 3: 데이터셋 파일 생성 + 로딩

**Files:**
- Create: `evaluation/src/main/resources/datasets/default-events.yaml`
- Create: `evaluation/src/main/resources/datasets/default-tasks.yaml`
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/datasets/Contacts.kt`

- [ ] **Step 1: default-events.yaml 작성**

고정 시각 2026-03-17 14:00 (화) 기준, 5건 (스펙 참조).

- [ ] **Step 2: default-tasks.yaml 작성**

4건 (스펙 참조).

- [ ] **Step 3: Contacts.kt를 Datasets.kt로 확장**

파일명은 유지하되 Events/Tasks 데이터 로딩 추가:

```kotlin
// YAML에서 로드하는 데이터셋
data class EventData(val id: String, val title: String, val startDateTime: String, val endDateTime: String, val description: String = "")
data class TaskData(val id: String, val title: String, val status: String, val dueDate: String = "", val notes: String = "")

fun loadDefaultEvents(): List<EventData> { /* YAML 파싱 */ }
fun loadDefaultTasks(): List<TaskData> { /* YAML 파싱 */ }
```

또는 간결하게 코드에 하드코딩 (기존 `DEFAULT_CONTACTS` 패턴과 동일):

```kotlin
val DEFAULT_EVENTS = listOf(
    EventData("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00"),
    // ...
)
val DEFAULT_TASKS = listOf(
    TaskData("task-001", "장보기", "needsAction", "2026-03-18"),
    // ...
)
```

기존 패턴이 코드 하드코딩이므로 동일하게 진행. YAML 파일은 참조용으로 유지.

- [ ] **Step 4: 빌드 확인**

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/main/resources/datasets/ evaluation/src/main/kotlin/com/bara/evaluation/datasets/Contacts.kt
git commit -m "feat(eval): 캘린더/할일 데이터셋 추가"
```

---

### Task 4: EvalAgentFactory 확장

**Files:**
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalAgentFactory.kt`

- [ ] **Step 1: 시스템 프롬프트에 고정 시각 + 캘린더/할일 가이드 추가**

SYSTEM_PROMPT 끝에 추가:

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

- [ ] **Step 2: create()에서 8개 Tool 등록 + stateStore에 데이터셋 로드**

```kotlin
fun create(resolver: MockContactResolver, stateStore: MockStateStore): AIAgent<String, String> {
    // 데이터셋 로드
    stateStore.loadEvents(DEFAULT_EVENTS.map { EventRecord(it.id, it.title, it.startDateTime, it.endDateTime) })
    stateStore.loadTasks(DEFAULT_TASKS.map { TaskRecord(it.id, it.title, it.status, it.dueDate) })

    val toolRegistry = ToolRegistry {
        tool(createSearchContactsTool(resolver, stateStore))
        tool(createMakeCallTool(stateStore))
        tool(createSendSmsTool(stateStore))
        tool(createListEventsTool(stateStore))
        tool(createCreateEventTool(stateStore))
        tool(createUpdateEventTool(stateStore))
        tool(createDeleteEventTool(stateStore))
        tool(createListTasksTool(stateStore))
        tool(createCreateTaskTool(stateStore))
        tool(createCompleteTaskTool(stateStore))
        tool(createDeleteTaskTool(stateStore))
    }
    // ...
}
```

- [ ] **Step 3: createWithHistory()도 동일하게 확장**

- [ ] **Step 4: 빌드 확인**

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalAgentFactory.kt
git commit -m "feat(eval): EvalAgentFactory에 캘린더/할일 Tool 등록 + 시스템 프롬프트 확장"
```

---

### Task 5: AgentRunner에 events/tasks tool call 기록 추가

**Files:**
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/core/AgentRunner.kt`

- [ ] **Step 1: run()에서 events/tasks 관련 tool call 재구성 추가**

기존 searchQueries/calls/sms 뒤에:

```kotlin
// 초기 events/tasks 크기 기록 (생성/삭제/완료 판별용)
val initialEventCount = stateStore.events.size
val initialTaskCount = stateStore.tasks.size

// ... agent.run(task.input) ...

// events 변화 감지 — 단순히 stateStore 상태 변화를 기반으로 tool call 추론
// (Mock Tool이 이미 기록하므로 stateStore 자체가 ground truth)
```

실제로는 Mock Tool factory 내에서 `stateStore`에 직접 기록하고 있으므로, AgentRunner에서 별도 재구성이 필요 없을 수 있음. 하지만 기존 패턴을 따라 tool call 목록에 추가:

Tool call 기록은 Mock Tool 내에서 별도 리스트로 관리하거나, 기존 `toolCalls` 재구성 패턴 유지. 가장 간단한 방법: MockStateStore에 `toolCallLog` 리스트를 추가하여 모든 Tool 호출을 기록.

- [ ] **Step 2: MockStateStore에 toolCallLog 추가**

```kotlin
val toolCallLog = mutableListOf<Pair<String, Map<String, String>>>()
fun recordToolCall(name: String, params: Map<String, String>) {
    toolCallLog.add(name to params)
}
```

각 Mock Tool factory에서 `stateStore.recordToolCall(...)` 호출.
AgentRunner에서 `stateStore.toolCallLog`를 `toolCalls`로 변환.

- [ ] **Step 3: 빌드 확인**

- [ ] **Step 4: 커밋**

```bash
git add evaluation/src/main/kotlin/com/bara/evaluation/core/AgentRunner.kt evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockStateStore.kt
git commit -m "feat(eval): AgentRunner에 캘린더/할일 tool call 기록 연결"
```

---

## Chunk 3: 테스트 태스크 YAML

### Task 6: 기본 캘린더 태스크 4개 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-list-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-create-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-delete-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-update-001.yaml`

- [ ] **Step 1: 4개 YAML 파일 작성**

기존 call-basic-001.yaml 패턴과 동일한 구조. 각 태스크에 `dataset: default-contacts` (기존 호환) + events/tasks 데이터는 EvalAgentFactory에서 자동 로드.

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/main/resources/tasks/voice-agent/calendar-*.yaml
git commit -m "feat(eval): 캘린더 기본 테스트 태스크 4개 추가"
```

---

### Task 7: 기본 할일 태스크 4개 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/tasks-list-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/tasks-create-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/tasks-complete-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/tasks-delete-001.yaml`

- [ ] **Step 1: 4개 YAML 파일 작성**

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/main/resources/tasks/voice-agent/tasks-*.yaml
git commit -m "feat(eval): 할일 기본 테스트 태스크 4개 추가"
```

---

### Task 8: 복잡한 시나리오 태스크 2개 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-list-002.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/calendar-create-002.yaml`

- [ ] **Step 1: 2개 YAML 파일 작성**

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/main/resources/tasks/voice-agent/calendar-list-002.yaml evaluation/src/main/resources/tasks/voice-agent/calendar-create-002.yaml
git commit -m "feat(eval): 복잡한 캘린더 테스트 태스크 2개 추가"
```

---

### Task 9: Roleplay 태스크 2개 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/calendar-modify-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/tasks-workflow-001.yaml`

- [ ] **Step 1: 2개 YAML 파일 작성** (기존 roleplay/confirm-call-001.yaml 패턴 참조)

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/main/resources/tasks/voice-agent/roleplay/
git commit -m "feat(eval): 캘린더/할일 roleplay 태스크 2개 추가"
```

---

### Task 10: 전체 빌드 + 실행 확인

- [ ] **Step 1: evaluation 빌드 확인**

```bash
cd evaluation && ../gradlew compileKotlin
```

- [ ] **Step 2: 태스크 로딩 확인**

```bash
cd evaluation && ../gradlew run --args="list"
```

새로 추가한 12개 태스크가 목록에 표시되는지 확인.

- [ ] **Step 3: 최종 커밋**

```bash
git add -A
git commit -m "feat(eval): 캘린더/할일 평가 시스템 통합 완료"
```
