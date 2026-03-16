# Hey Bara — Koog Agent 평가 시스템 설계

## 개요

Koog Agent의 의도 파악, tool 호출 정확성, 응답 품질을 자동으로 평가하는 시스템.
Kotlin 독립 Gradle 모듈로 구성하며, Android 앱 빌드에 포함되지 않는다.

## 결정 사항

| 항목 | 결정 |
|------|------|
| 언어/환경 | Kotlin 독립 모듈 (`evaluation/`) |
| 에이전트 호출 | Koog SDK 직접 사용 (순수 JVM, Android 의존성 없음) |
| 에이전트 모델 | gemini-3.1-flash-lite-preview (프로덕션과 동일) |
| 평가용 LLM | Gemini 3.1 Pro Preview (Grader, Judge, Roleplayer) |
| 연락처 | Mock 데이터셋 (고정 연락처 목록) |
| 평가 모드 | Single-turn + Roleplay (멀티턴) |
| Grader | 6종 (tool_calls, output_contains, output_contains_any, constraints, state_check, llm_grader) |
| 빌드 격리 | app 모듈과 의존 관계 없음, assembleDebug에 미포함 |

---

## 사전 검증 (구현 전 spike)

Koog SDK가 순수 JVM에서 동작하는지 검증해야 한다.
app 모듈의 KoogAgentEngine은 `android.util.Log`를 사용하므로 평가 모듈에서는 이를 제거한 버전을 사용한다.

**검증 방법:**
1. 최소한의 `kotlin("jvm")` 프로젝트 생성
2. `ai.koog:koog-agents` 의존성 추가
3. `AIAgent` 인스턴스 생성 + Gemini API 호출 확인
4. 실패 시 대안: Gemini API 직접 호출 (function calling)

---

## API 키 관리

평가 모듈은 Android `BuildConfig`가 없으므로 환경변수로 API 키를 로딩한다.

**우선순위:**
1. 환경변수: `GEMINI_API_KEY`
2. 프로젝트 루트 `.env` 파일 (gitignore 대상)
3. CLI 인자: `--api-key`

```bash
# 실행 예시
GEMINI_API_KEY=xxx ./gradlew :evaluation:run --args="run"

# 또는 .env 파일
echo "GEMINI_API_KEY=xxx" > .env
```

에이전트용(flash-lite)과 평가용(pro) 모두 동일한 Gemini API 키를 사용한다.

---

## Tool 동기화 전략

평가 모듈의 tool 정의(EvalTools.kt)는 app 모듈의 KoogAgentEngine.kt와 수동으로 동기화한다.

**규칙:**
- app에서 tool을 추가/수정하면 evaluation의 EvalTools.kt도 반드시 업데이트
- 시스템 프롬프트도 동일하게 유지 (EvalAgentFactory.kt에서 관리)
- 차이점: `android.util.Log` 대신 `println` 또는 SLF4J 사용
- 차이점: Tool 실행 시 MockStateStore에 이력 기록

향후 tool이 많아지면 공유 인터페이스 모듈(`:shared`) 분리를 검토한다.

---

## 모듈 구조

```
evaluation/                         # 독립 Gradle 모듈 (순수 JVM)
├── build.gradle.kts                # Koog SDK, Google AI SDK, kotlinx-serialization 의존성
├── src/main/kotlin/com/bara/evaluation/
│   ├── Main.kt                     # CLI 진입점
│   ├── cli/
│   │   └── Commands.kt             # tasks, run, roleplay, cache, clear 커맨드
│   ├── core/
│   │   ├── Types.kt                # Task, Outcome, Transcript, GraderResult 타입 정의
│   │   ├── TaskManager.kt          # YAML 태스크 로딩/관리
│   │   ├── ScenarioManager.kt      # Roleplay 시나리오 YAML 로딩
│   │   ├── AgentRunner.kt          # Koog Agent 생성/실행, Transcript 수집
│   │   ├── EvalRunner.kt           # Single-turn 평가 오케스트레이터
│   │   ├── RoleplayRunner.kt       # 멀티턴 Roleplay 오케스트레이터
│   │   ├── Roleplayer.kt           # 사용자 역할 시뮬레이션 LLM
│   │   ├── Judge.kt                # Goal 달성 판단 LLM
│   │   ├── GraderEngine.kt         # Grader 조합/실행
│   │   └── Cache.kt                # YAML 해시 기반 캐싱
│   ├── graders/
│   │   ├── ToolCallsGrader.kt      # Tool 호출 검증 (strict/unordered/subset/superset)
│   │   ├── OutputContainsGrader.kt # 응답 키워드 포함 (AND)
│   │   ├── OutputContainsAnyGrader.kt  # 응답 키워드 포함 (OR)
│   │   ├── ConstraintsGrader.kt    # 실행 제약 (max_turns, max_toolcalls, timeout)
│   │   ├── StateCheckGrader.kt     # Mock 상태 검증 (dot-notation)
│   │   └── LlmGrader.kt           # LLM rubric 기반 채점 (0.0~1.0)
│   ├── agent/
│   │   ├── EvalAgentFactory.kt     # 평가용 Koog Agent 생성 (시스템 프롬프트, tool 등록)
│   │   └── EvalTools.kt            # 평가용 tool 구현 (search_contacts, make_call, send_sms)
│   ├── mocks/
│   │   ├── MockContactResolver.kt  # Mock 연락처 검색
│   │   └── MockStateStore.kt       # 상태 추적 (tool 호출 이력, 상태 변경)
│   ├── datasets/
│   │   ├── Contacts.kt             # Mock 연락처 데이터셋 정의
│   │   └── DatasetRegistry.kt      # 데이터셋 등록/조회
│   ├── reporters/
│   │   ├── ConsoleReporter.kt      # 콘솔 출력
│   │   └── JsonReporter.kt         # JSON 결과 저장
│   └── rubrics/                    # LLM 채점 기준 (Markdown)
│       ├── response-quality.md     # 응답 품질 (정확성, 자연스러움, 적절성)
│       └── conversation-quality.md # 멀티턴 대화 품질
├── src/main/resources/
│   └── tasks/                      # YAML 테스트 케이스
│       └── voice-agent/
│           ├── call-basic-001.yaml
│           ├── call-basic-002.yaml
│           ├── sms-basic-001.yaml
│           ├── contact-not-found-001.yaml
│           ├── general-chat-001.yaml
│           ├── general-chat-002.yaml
│           └── roleplay/
│               ├── confirm-call-001.yaml
│               ├── disambiguate-contact-001.yaml
│               ├── multi-step-001.yaml
│               └── cancel-001.yaml
├── cache/                          # 캐시 (gitignore)
└── results/                        # 실행 결과 (gitignore)
```

---

## Gradle 설정

### settings.gradle.kts (프로젝트 루트)

```kotlin
include(":app")
include(":evaluation")
```

### 루트 build.gradle.kts

`kotlin("jvm")` 및 `kotlin("plugin.serialization")` 플러그인을 version catalog 또는 루트 빌드스크립트에 선언해야 한다.

### evaluation/build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// 순수 JVM 모듈 — Android 플러그인 없음
// app 모듈과 의존 관계 없음 → assembleDebug에 미포함

dependencies {
    // Koog Agent SDK (순수 Kotlin)
    implementation("ai.koog:koog-agents:...")
    implementation("ai.koog:koog-prompt:...")

    // Google AI SDK (Gemini API) — 평가용 LLM 호출
    implementation("com.google.ai:generativeai:...")

    // YAML 파싱
    implementation("com.charleskorn.kaml:kaml:...")

    // kotlinx-serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:...")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:...")

    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:...")
}
```

`app` 모듈은 `evaluation`을 의존하지 않으므로:
- `./gradlew assembleDebug` → app만 빌드
- `./gradlew :evaluation:run` → 평가만 실행

---

## 평가 모드

### Single-turn 평가

하나의 입력에 대해 에이전트 응답을 검증한다.

**실행 흐름:**

```
YAML Task 로딩
  → Mock 데이터셋 초기화
  → Koog Agent 생성 (EvalAgentFactory, model=gemini-3.1-flash-lite-preview)
  → Agent.run(input)
  → Transcript 수집 (messages, tool_calls, metrics)
  → 6종 Grader 실행
  → Outcome (passed/failed/error/cached)
```

**Task YAML 스키마:**

```yaml
id: voice-agent/call-basic-001
input: "엄마한테 전화해줘"
dataset: default-contacts
expected:
  tool_calls:
    mode: superset
    calls:
      - tool: search_contacts
        params:
          query: "엄마"
      - tool: make_call
        params:
          contact: "엄마"
          phoneNumber: "010-1234-5678"
  output_contains:
    - "전화"
  constraints:
    max_turns: 5
    max_toolcalls: 3
  state_check:
    - key: "calls.last.contact"
      expect: "엄마"
    - key: "calls.last.phoneNumber"
      expect: "010-1234-5678"
  llm_grader:
    rubric: response-quality.md
    min_score: 0.7
metadata:
  category: call
  description: "기본 전화 걸기 — 연락처 1건 매칭"
  tags: [call, basic, single-contact]
```

### Roleplay 평가

멀티턴 대화를 시뮬레이션하여 에이전트의 대화 흐름을 검증한다.

**실행 흐름:**

```
Scenario YAML 로딩
  → Mock 데이터셋 초기화
  → Turn 반복:
    1. Roleplayer LLM (Gemini Pro, persona 기반) → 사용자 메시지 생성
    2. Koog Agent (flash-lite) → 응답
    3. Judge LLM (Gemini Pro) → goal 달성 여부 판단
       - goal_achieved → 종료
       - cannot_continue → 종료 (자동 실패)
       - continue → 다음 턴
    4. max_turns 도달 → 종료
  → Rubric LLM (Gemini Pro) → 전체 대화 채점 (0.0~1.0)
  → Outcome
```

**Scenario YAML 스키마:**

```yaml
id: voice-agent/roleplay-confirm-call-001
type: roleplay
persona: |
  당신은 바쁜 직장인입니다.
  짧고 간결하게 말합니다.
  전화를 걸어달라고 요청한 뒤 확인 질문에 "응"으로 답합니다.
goal: |
  1. 에이전트가 연락처를 검색한다
  2. 에이전트가 확인 질문을 한다 ("전화를 걸까요?")
  3. 사용자가 확인한다
  4. 에이전트가 전화를 건다 (make_call 호출)
initial_message: "엄마한테 전화 좀 해줘"
dataset: default-contacts
max_turns: 6
completion_criteria:
  rubric: conversation-quality.md
  min_score: 0.7
metadata:
  category: roleplay
  description: "전화 걸기 확인 흐름"
  tags: [call, confirm, roleplay]
```

---

## Grader 상세

### 1. tool_calls

Tool 호출 순서와 파라미터를 검증한다.

**매칭 모드:**
- `strict`: 순서 + 개수 + 내용 모두 일치
- `unordered`: 내용만 일치, 순서 무관
- `subset`: 실제 호출 ⊆ 기대 (추가 호출 없어야 함)
- `superset`: 기대 ⊆ 실제 (추가 호출 허용, 기본값)

**파라미터 매칭:**
- `"*"` → 값이 존재하면 통과
- `"regex:패턴"` → 정규식 매칭
- 정확한 값 → 일치 비교

### 2. output_contains

응답에 지정된 문자열이 **모두** 포함되어야 통과 (AND 로직).

### 3. output_contains_any

응답에 지정된 문자열 중 **하나라도** 포함되면 통과 (OR 로직).

### 4. constraints

실행 메트릭 제약 조건을 검증한다.
- `max_turns`: 최대 대화 턴 수
- `max_toolcalls`: 최대 tool 호출 횟수
- `timeout`: 최대 실행 시간 (ms)

### 5. state_check

MockStateStore의 상태를 dot-notation 경로로 검증한다.

```yaml
state_check:
  - key: "calls.last.contact"
    expect: "엄마"
  - key: "calls.count"
    expect: ">=1"
  - key: "sms.last.message"
    expect: "*"
```

**경로 문법:**
- `calls.last` → 마지막 요소
- `calls.first` → 첫 번째 요소
- `calls.0`, `calls.1` → 인덱스 접근
- `calls.count` → 리스트 크기
- 중첩: `calls.last.contact` → 마지막 호출의 contact 필드

**지원 연산:**
- `"*"` → 값 존재
- `">=N"`, `"<=N"`, `">N"`, `"<N"` → 수치 비교
- `"regex:패턴"` → 정규식
- 정확한 값 → 일치 비교

### 6. llm_grader

Gemini 3.1 Pro Preview가 rubric 기반으로 0.0~1.0 점수를 매긴다.

**출력 형식:**
```json
{
  "passed": true,
  "score": 0.85,
  "reasoning": "자연스러운 한국어로 응답하며..."
}
```

통과 조건: `score >= min_score` (기본값 0.7)

---

## Mock 데이터

### 기본 연락처 데이터셋 (default-contacts)

```kotlin
val DEFAULT_CONTACTS = listOf(
    Contact("엄마", "010-1234-5678"),
    Contact("아빠", "010-2345-6789"),
    Contact("김철수", "010-3456-7890"),
    Contact("김영희", "010-4567-8901"),
    Contact("이민수", "010-5678-9012"),
    Contact("박지영", "010-6789-0123"),
    Contact("김철수", "010-7890-1234"),  // 동명이인
)
```

### MockStateStore

Tool 호출 이력과 상태 변경을 추적한다.

```kotlin
class MockStateStore {
    val calls: MutableList<CallRecord> = mutableListOf()
    val sms: MutableList<SmsRecord> = mutableListOf()
    val searchQueries: MutableList<String> = mutableListOf()

    // dot-notation 경로로 상태 조회
    // 지원: last, first, 인덱스(0,1,...), count
    fun get(path: String): Any?
}

data class CallRecord(val contact: String, val phoneNumber: String)
data class SmsRecord(val contact: String, val phoneNumber: String, val message: String)
```

---

## Rubric 정의

### response-quality.md

```markdown
---
minimum_score: 0.7
model: gemini-3.1-pro-preview
temperature: 0
---

# 응답 품질 평가

## 평가 기준

### 정확성 (0.4)
- 사용자 의도를 정확히 파악했는가
- 올바른 tool을 호출했는가
- 올바른 파라미터를 전달했는가

### 자연스러움 (0.3)
- 한국어가 자연스러운가
- 음성 비서답게 간결한가
- 불필요한 정보 없이 핵심만 전달하는가

### 적절성 (0.3)
- 확인이 필요한 상황에서 확인을 요청하는가
- 에러 상황에서 적절히 안내하는가
- 대화 맥락을 유지하는가
```

### conversation-quality.md (Roleplay용)

```markdown
---
minimum_score: 0.7
model: gemini-3.1-pro-preview
temperature: 0
---

# 대화 품질 평가

## 평가 기준

### 목표 달성 (0.4)
- 사용자의 최종 목표를 달성했는가
- 불필요한 턴 없이 효율적으로 진행했는가

### 대화 흐름 (0.3)
- 확인 → 실행 흐름이 자연스러운가
- 모호한 상황에서 적절히 질문하는가
- 이전 맥락을 기억하는가

### 응답 품질 (0.3)
- 한국어가 자연스러운가
- 간결하고 명확한가
```

---

## 에러 처리 및 재시도

### LLM API 호출 실패

```
실패 시 → 최대 3회 재시도 (exponential backoff: 1초, 2초, 4초)
3회 실패 → Outcome.status = "error", 에러 메시지 기록
```

### Rate Limiting

- 동시 실행 수 기본값: 4 (CLI로 조절 가능)
- Gemini API rate limit 초과 시 429 응답 → 자동 backoff 후 재시도
- Roleplay는 턴당 3회 LLM 호출이므로 concurrency를 낮추는 것을 권장

---

## CLI 인터페이스

```bash
# 태스크 목록
./gradlew :evaluation:run --args="tasks"
./gradlew :evaluation:run --args="tasks --verbose"

# Single-turn 평가
./gradlew :evaluation:run --args="run"                        # 전체 실행
./gradlew :evaluation:run --args="run --task voice-agent/call-basic-001"  # 단일 태스크
./gradlew :evaluation:run --args="run --graders tool_calls,constraints"   # grader 필터
./gradlew :evaluation:run --args="run --concurrency 2"        # 동시 실행 수
./gradlew :evaluation:run --args="run --no-cache"             # 캐시 무시

# Roleplay 평가
./gradlew :evaluation:run --args="scenarios"                  # 시나리오 목록
./gradlew :evaluation:run --args="roleplay"                   # 전체 실행
./gradlew :evaluation:run --args="roleplay --scenario voice-agent/roleplay-confirm-call-001"

# 캐시 관리
./gradlew :evaluation:run --args="cache-status"
./gradlew :evaluation:run --args="clear-cache"
```

---

## 캐싱 전략

- **해시 기반**: task ID + input + dataset + expected + 시스템 프롬프트 + 모델 ID를 해시하여 캐시 키 생성
- **자동 무효화**: YAML 파일 변경, 시스템 프롬프트 변경, 모델 변경 시 해시 불일치로 자동 재실행
- **passed만 캐시**: 실패한 태스크는 매번 재실행
- **Roleplay는 캐시 미적용**: 멀티턴은 비결정적이므로 매번 실행
- **캐시 저장**: `evaluation/cache/passed_tasks.json`

---

## 결과 리포트

### 콘솔 출력

```
=== Hey Bara Evaluation Results ===

voice-agent/call-basic-001      PASSED  [tool_calls ✓] [output ✓] [constraints ✓] [llm 0.92]
voice-agent/sms-basic-001       PASSED  [tool_calls ✓] [output ✓] [constraints ✓] [llm 0.88]
voice-agent/contact-not-found   PASSED  [output ✓] [llm 0.85]
voice-agent/general-chat-001    PASSED  [output ✓] [llm 0.80]

Total: 6 | Passed: 5 | Failed: 1 | Cached: 2 | Pass Rate: 83.3%
```

### JSON 결과

`evaluation/results/{timestamp}/summary.json`에 저장:

```json
{
  "runId": "20260316_150000",
  "timestamp": "2026-03-16T15:00:00",
  "total": 6,
  "passed": 5,
  "failed": 1,
  "cached": 2,
  "passRate": 83.3,
  "outcomes": [
    {
      "taskId": "voice-agent/call-basic-001",
      "status": "passed",
      "graderResults": [...],
      "transcript": {...}
    }
  ]
}
```

---

## 초기 테스트 케이스 (예정)

### Single-turn

| ID | 입력 | 검증 포인트 |
|----|------|------------|
| call-basic-001 | "엄마한테 전화해줘" | search_contacts → make_call |
| call-basic-002 | "아빠한테 전화 걸어" | search_contacts → make_call |
| sms-basic-001 | "엄마한테 잘 잔다고 문자 보내줘" | search_contacts → send_sms(message=`regex:잘.*자`) |
| contact-not-found-001 | "홍길동한테 전화해줘" | search_contacts → tool 호출 없이 연락처 없음 안내 |
| general-chat-001 | "오늘 날씨 어때?" | tool 호출 없이 응답 (기능 범위 밖 안내) |
| general-chat-002 | "고마워" | tool 호출 없이 일반 응답 |

### Roleplay

| ID | 시나리오 | 검증 포인트 |
|----|---------|------------|
| roleplay-confirm-call-001 | 전화 확인 흐름 | 검색 → 확인 → 실행 |
| roleplay-disambiguate-001 | 동명이인 해소 | 검색 → 질문 → 선택 → 실행 |
| roleplay-multi-step-001 | 전화 + 문자 연속 | 전화 걸고 → 문자도 보내기 |
| roleplay-cancel-001 | 요청 취소 | 확인에서 "아니" → 취소 처리 |
