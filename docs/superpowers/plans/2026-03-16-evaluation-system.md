# Koog Agent 평가 시스템 구현 계획

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Koog Agent의 tool 호출 정확성, 응답 품질, 대화 흐름을 자동으로 평가하는 독립 Kotlin 모듈을 구축한다.

**Architecture:** 순수 JVM Gradle 모듈(`evaluation/`)로 Android 앱과 분리. Koog SDK로 에이전트를 생성하고, Mock 데이터로 tool을 실행하며, 6종 Grader로 결과를 채점한다. Single-turn과 Roleplay(멀티턴) 두 가지 평가 모드를 지원한다.

**Tech Stack:** Kotlin 2.2, Koog SDK 0.6.4, Gemini API, kaml (YAML), Clikt (CLI), kotlinx-coroutines, kotlinx-serialization

**Spec:** `docs/superpowers/specs/2026-03-16-evaluation-system-design.md`

---

## Chunk 1: 프로젝트 스캐폴딩 + Spike

Gradle 모듈 설정과 Koog SDK JVM 동작 검증.

### Task 1: Gradle 모듈 생성

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts` (루트)
- Modify: `gradle/libs.versions.toml`
- Modify: `.gitignore`
- Create: `evaluation/build.gradle.kts`

- [ ] **Step 1: version catalog에 평가 모듈 의존성 추가**

`gradle/libs.versions.toml`에 추가:

```toml
[versions]
# 기존 유지...
kaml = "0.77.0"
clikt = "5.0.3"
serialization = "1.8.1"

[libraries]
# 기존 유지...
kaml = { group = "com.charleskorn.kaml", name = "kaml", version.ref = "kaml" }
clikt = { group = "com.github.ajalt.clikt", name = "clikt", version.ref = "clikt" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
koog-prompt = { group = "ai.koog", name = "koog-prompt", version.ref = "koog" }
google-genai = { group = "com.google.genai", name = "google-genai", version = "1.5.0" }

[plugins]
# 기존 유지...
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: 루트 build.gradle.kts에 JVM 플러그인 선언**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 3: settings.gradle.kts에 evaluation 모듈 추가**

```kotlin
include(":app")
include(":evaluation")
```

- [ ] **Step 4: evaluation/build.gradle.kts 생성**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.bara.evaluation.MainKt")
}

dependencies {
    // Koog Agent SDK
    implementation(libs.koog.agents)
    implementation(libs.koog.prompt)

    // Google Generative AI (평가용 LLM)
    implementation(libs.google.genai)

    // YAML
    implementation(libs.kaml)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // CLI
    implementation(libs.clikt)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 5: .gitignore에 평가 캐시/결과 추가**

```
# Evaluation
evaluation/cache/
evaluation/results/
.env
```

- [ ] **Step 6: 최소 Main.kt 생성**

Create: `evaluation/src/main/kotlin/com/bara/evaluation/Main.kt`

```kotlin
package com.bara.evaluation

fun main(args: Array<String>) {
    println("Hey Bara Evaluation System")
}
```

- [ ] **Step 7: Gradle sync 및 빌드 확인**

Run: `./gradlew :evaluation:run`
Expected: "Hey Bara Evaluation System" 출력

- [ ] **Step 8: app 빌드가 영향받지 않는지 확인**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (evaluation 모듈 빌드 안 됨)

- [ ] **Step 9: 커밋**

```bash
git add evaluation/ settings.gradle.kts build.gradle.kts gradle/libs.versions.toml .gitignore
git commit -m "feat: evaluation 모듈 스캐폴딩"
```

### Task 2: Koog SDK JVM Spike

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/Spike.kt`

- [ ] **Step 1: Koog SDK로 최소 에이전트 생성 테스트**

```kotlin
package com.bara.evaluation

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: error("GEMINI_API_KEY 환경변수를 설정하세요")

    val executor = simpleGoogleAIExecutor(apiKey)
    val model = LLModel(
        provider = LLMProvider.Google,
        id = "gemini-3.1-flash-lite-preview",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
    )
    val agent = AIAgent(
        promptExecutor = executor,
        systemPrompt = "너는 테스트 에이전트야. 짧게 답해.",
        llmModel = model,
        toolRegistry = ToolRegistry {},
        maxIterations = 3
    )

    runBlocking {
        val result = agent.run("안녕")
        println("Agent 응답: $result")
    }
}
```

- [ ] **Step 2: Spike 실행**

Run: `GEMINI_API_KEY=<key> ./gradlew :evaluation:run`
Expected: Agent가 한국어 응답 출력. 실패 시 Koog SDK가 Android 의존성을 가진 것이므로 대안(Gemini API 직접 호출) 검토 필요.

- [ ] **Step 3: Spike 파일 삭제, Main.kt 원복**

Spike 확인 후 `Spike.kt` 삭제.

- [ ] **Step 4: 커밋**

```bash
git commit -m "spike: Koog SDK JVM 동작 확인"
```

---

## Chunk 2: Core 타입 + Mock 인프라

평가 시스템의 기본 타입 정의와 Mock 데이터 인프라.

### Task 3: Core 타입 정의

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/Types.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/core/TypesTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test

class TypesTest {
    @Test
    fun `Outcome passed when all graders pass`() {
        val results = listOf(
            GraderResult("tool_calls", passed = true, score = 1.0),
            GraderResult("output_contains", passed = true, score = 1.0),
        )
        val outcome = Outcome(
            taskId = "test-001",
            graderResults = results,
        )
        assertEquals("passed", outcome.status)
    }

    @Test
    fun `Outcome failed when any grader fails`() {
        val results = listOf(
            GraderResult("tool_calls", passed = true, score = 1.0),
            GraderResult("output_contains", passed = false, score = 0.0),
        )
        val outcome = Outcome(
            taskId = "test-001",
            graderResults = results,
        )
        assertEquals("failed", outcome.status)
    }

    @Test
    fun `Outcome error when exception present`() {
        val outcome = Outcome(
            taskId = "test-001",
            graderResults = emptyList(),
            error = "API timeout",
        )
        assertEquals("error", outcome.status)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.core.TypesTest"`
Expected: FAIL — 클래스 미존재

- [ ] **Step 3: Types.kt 구현**

```kotlin
package com.bara.evaluation.core

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val input: String,
    val dataset: String = "default-contacts",
    val expected: Expected = Expected(),
    val metadata: TaskMetadata = TaskMetadata(),
)

@Serializable
data class Expected(
    val toolCalls: ToolCallsExpected? = null,
    val outputContains: List<String>? = null,
    val outputContainsAny: List<String>? = null,
    val constraints: ConstraintsExpected? = null,
    val stateCheck: List<StateCheckExpected>? = null,
    val llmGrader: LlmGraderExpected? = null,
)

@Serializable
data class ToolCallsExpected(
    val mode: String = "superset",
    val calls: List<ToolCallExpected>,
)

@Serializable
data class ToolCallExpected(
    val tool: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class ConstraintsExpected(
    val maxTurns: Int? = null,
    val maxToolcalls: Int? = null,
    val timeout: Long? = null,
)

@Serializable
data class StateCheckExpected(
    val key: String,
    val expect: String,
)

@Serializable
data class LlmGraderExpected(
    val rubric: String,
    val minScore: Double = 0.7,
)

@Serializable
data class TaskMetadata(
    val category: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class Scenario(
    val id: String,
    val type: String = "roleplay",
    val persona: String,
    val goal: String,
    val initialMessage: String? = null,
    val dataset: String = "default-contacts",
    val maxTurns: Int = 10,
    val completionCriteria: CompletionCriteria,
    val metadata: TaskMetadata = TaskMetadata(),
)

@Serializable
data class CompletionCriteria(
    val rubric: String,
    val minScore: Double = 0.7,
)

data class ToolCall(
    val tool: String,
    val params: Map<String, String>,
)

data class Transcript(
    val messages: List<Message>,
    val toolCalls: List<ToolCall>,
    val metrics: Metrics,
)

data class Message(
    val role: String,  // "user" | "assistant"
    val content: String,
)

data class Metrics(
    val turns: Int = 0,
    val toolCallCount: Int = 0,
    val durationMs: Long = 0,
)

@Serializable
data class GraderResult(
    val grader: String,
    val passed: Boolean,
    val score: Double = if (passed) 1.0 else 0.0,
    val details: Map<String, String> = emptyMap(),
)

data class Outcome(
    val taskId: String,
    val graderResults: List<GraderResult>,
    val transcript: Transcript? = null,
    val error: String? = null,
    val cached: Boolean = false,
) {
    val status: String
        get() = when {
            cached -> "cached"
            error != null -> "error"
            graderResults.all { it.passed } -> "passed"
            else -> "failed"
        }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.core.TypesTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: evaluation core 타입 정의"
```

### Task 4: Mock 연락처 + StateStore

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/datasets/Contacts.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockContactResolver.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/mocks/MockStateStore.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/mocks/MockStateStoreTest.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/mocks/MockContactResolverTest.kt`

- [ ] **Step 1: MockStateStore 테스트 작성**

```kotlin
package com.bara.evaluation.mocks

import org.junit.Assert.*
import org.junit.Test

class MockStateStoreTest {
    @Test
    fun `get calls count returns number of calls`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals(2, store.get("calls.count"))
    }

    @Test
    fun `get calls last contact returns last call contact`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("아빠", store.get("calls.last.contact"))
    }

    @Test
    fun `get calls first contact returns first call contact`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("엄마", store.get("calls.first.contact"))
    }

    @Test
    fun `get calls by index`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("엄마", store.get("calls.0.contact"))
        assertEquals("아빠", store.get("calls.1.contact"))
    }

    @Test
    fun `get sms last message`() {
        val store = MockStateStore()
        store.recordSms("엄마", "010-1234-5678", "잘 잔다")
        assertEquals("잘 잔다", store.get("sms.last.message"))
    }

    @Test
    fun `get nonexistent path returns null`() {
        val store = MockStateStore()
        assertNull(store.get("calls.last.contact"))
    }
}
```

- [ ] **Step 2: MockContactResolver 테스트 작성**

```kotlin
package com.bara.evaluation.mocks

import com.bara.evaluation.datasets.DEFAULT_CONTACTS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MockContactResolverTest {
    @Test
    fun `search by exact name`() = runTest {
        val resolver = MockContactResolver(DEFAULT_CONTACTS)
        val results = resolver.searchContacts("엄마")
        assertEquals(1, results.size)
        assertEquals("010-1234-5678", results[0].phoneNumber)
    }

    @Test
    fun `search returns multiple for duplicate names`() = runTest {
        val resolver = MockContactResolver(DEFAULT_CONTACTS)
        val results = resolver.searchContacts("김철수")
        assertEquals(2, results.size)
    }

    @Test
    fun `search returns empty for unknown name`() = runTest {
        val resolver = MockContactResolver(DEFAULT_CONTACTS)
        val results = resolver.searchContacts("홍길동")
        assertTrue(results.isEmpty())
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :evaluation:test`
Expected: FAIL

- [ ] **Step 4: Contacts.kt 구현**

```kotlin
package com.bara.evaluation.datasets

data class Contact(
    val name: String,
    val phoneNumber: String,
)

val DEFAULT_CONTACTS = listOf(
    Contact("엄마", "010-1234-5678"),
    Contact("아빠", "010-2345-6789"),
    Contact("김철수", "010-3456-7890"),
    Contact("김영희", "010-4567-8901"),
    Contact("이민수", "010-5678-9012"),
    Contact("박지영", "010-6789-0123"),
    Contact("김철수", "010-7890-1234"),
)

val DATASETS: Map<String, List<Contact>> = mapOf(
    "default-contacts" to DEFAULT_CONTACTS,
)
```

- [ ] **Step 5: MockContactResolver 구현**

```kotlin
package com.bara.evaluation.mocks

import com.bara.evaluation.datasets.Contact

class MockContactResolver(
    private val contacts: List<Contact>
) {
    suspend fun searchContacts(query: String): List<Contact> {
        return contacts.filter { it.name.contains(query) }
    }
}
```

- [ ] **Step 6: MockStateStore 구현**

```kotlin
package com.bara.evaluation.mocks

data class CallRecord(val contact: String, val phoneNumber: String)
data class SmsRecord(val contact: String, val phoneNumber: String, val message: String)

class MockStateStore {
    val calls = mutableListOf<CallRecord>()
    val sms = mutableListOf<SmsRecord>()
    val searchQueries = mutableListOf<String>()

    fun recordCall(contact: String, phoneNumber: String) {
        calls.add(CallRecord(contact, phoneNumber))
    }

    fun recordSms(contact: String, phoneNumber: String, message: String) {
        sms.add(SmsRecord(contact, phoneNumber, message))
    }

    fun recordSearch(query: String) {
        searchQueries.add(query)
    }

    fun get(path: String): Any? {
        val parts = path.split(".")
        if (parts.isEmpty()) return null

        val collection: List<Any> = when (parts[0]) {
            "calls" -> calls
            "sms" -> sms
            "searchQueries" -> searchQueries
            else -> return null
        }

        return resolvePath(collection, parts.drop(1))
    }

    private fun resolvePath(collection: List<Any>, parts: List<String>): Any? {
        if (parts.isEmpty()) return collection

        val accessor = parts[0]
        val element: Any? = when (accessor) {
            "count" -> return collection.size
            "last" -> collection.lastOrNull()
            "first" -> collection.firstOrNull()
            else -> {
                val index = accessor.toIntOrNull() ?: return null
                collection.getOrNull(index)
            }
        }

        if (element == null) return null
        if (parts.size == 1) return element

        val field = parts[1]
        return when (element) {
            is CallRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                else -> null
            }
            is SmsRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                "message" -> element.message
                else -> null
            }
            is String -> element  // searchQueries
            else -> null
        }
    }

    fun reset() {
        calls.clear()
        sms.clear()
        searchQueries.clear()
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :evaluation:test`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: Mock 연락처 + StateStore 구현"
```

---

## Chunk 3: 평가용 에이전트

EvalTools와 EvalAgentFactory — 프로덕션 에이전트를 평가 환경에서 재현.

### Task 5: 평가용 Tool 구현

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalTools.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/agent/EvalToolsTest.kt`

- [ ] **Step 1: EvalTools 테스트 작성**

```kotlin
package com.bara.evaluation.agent

import com.bara.evaluation.datasets.DEFAULT_CONTACTS
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvalToolsTest {
    private lateinit var stateStore: MockStateStore
    private lateinit var resolver: MockContactResolver

    @Before
    fun setup() {
        stateStore = MockStateStore()
        resolver = MockContactResolver(DEFAULT_CONTACTS)
    }

    @Test
    fun `SearchContactsTool records query and returns results`() = runTest {
        val tool = createSearchContactsTool(resolver, stateStore)
        val result = tool.execute(SearchContactsArgs("엄마"))
        assertTrue(result.contains("010-1234-5678"))
        assertEquals(listOf("엄마"), stateStore.searchQueries)
    }

    @Test
    fun `MakeCallTool records call in state store`() = runTest {
        val tool = createMakeCallTool(stateStore)
        tool.execute(MakeCallArgs("엄마", "010-1234-5678"))
        assertEquals(1, stateStore.calls.size)
        assertEquals("엄마", stateStore.calls[0].contact)
    }

    @Test
    fun `SendSmsTool records sms in state store`() = runTest {
        val tool = createSendSmsTool(stateStore)
        tool.execute(SendSmsArgs("엄마", "010-1234-5678", "잘 잔다"))
        assertEquals(1, stateStore.sms.size)
        assertEquals("잘 잔다", stateStore.sms[0].message)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.agent.EvalToolsTest"`
Expected: FAIL

- [ ] **Step 3: EvalTools.kt 구현**

프로덕션 `KoogAgentEngine.kt`의 tool 정의를 복제하되:
- `android.util.Log` → `println`
- Tool execute 시 `MockStateStore`에 이력 기록
- `MockContactResolver`로 연락처 검색

```kotlin
package com.bara.evaluation.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore
import kotlinx.serialization.Serializable

// -- Args --

@Serializable
data class SearchContactsArgs(
    @property:LLMDescription("검색할 이름 또는 별명")
    val query: String,
)

@Serializable
data class MakeCallArgs(
    @property:LLMDescription("전화할 사람 이름")
    val contact: String,
    @property:LLMDescription("전화번호 (예: 010-1234-5678)")
    val phoneNumber: String,
)

@Serializable
data class SendSmsArgs(
    @property:LLMDescription("받는 사람 이름")
    val contact: String,
    @property:LLMDescription("전화번호 (예: 010-1234-5678)")
    val phoneNumber: String,
    @property:LLMDescription("보낼 메시지 내용")
    val message: String,
)

// -- Tool Factories --

fun createSearchContactsTool(
    resolver: MockContactResolver,
    stateStore: MockStateStore,
) = object : SimpleTool<SearchContactsArgs>(
    argsSerializer = SearchContactsArgs.serializer(),
    name = "search_contacts",
    description = "연락처에서 이름으로 검색한다. 전화나 문자를 보내기 전에 반드시 먼저 호출해야 한다.",
) {
    override suspend fun execute(args: SearchContactsArgs): String {
        stateStore.recordSearch(args.query)
        val contacts = resolver.searchContacts(args.query)
        return if (contacts.isEmpty()) {
            "연락처에서 '${args.query}'을(를) 찾을 수 없습니다."
        } else {
            contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
        }
    }
}

fun createMakeCallTool(
    stateStore: MockStateStore,
) = object : SimpleTool<MakeCallArgs>(
    argsSerializer = MakeCallArgs.serializer(),
    name = "make_call",
    description = "전화번호로 전화를 건다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다.",
) {
    override suspend fun execute(args: MakeCallArgs): String {
        stateStore.recordCall(args.contact, args.phoneNumber)
        return "${args.contact}(${args.phoneNumber})한테 전화를 겁니다."
    }
}

fun createSendSmsTool(
    stateStore: MockStateStore,
) = object : SimpleTool<SendSmsArgs>(
    argsSerializer = SendSmsArgs.serializer(),
    name = "send_sms",
    description = "문자 메시지를 보낸다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다.",
) {
    override suspend fun execute(args: SendSmsArgs): String {
        stateStore.recordSms(args.contact, args.phoneNumber, args.message)
        return "${args.contact}(${args.phoneNumber})한테 '${args.message}'라고 문자를 보냅니다."
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.agent.EvalToolsTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: 평가용 EvalTools 구현"
```

### Task 6: EvalAgentFactory

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/agent/EvalAgentFactory.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/agent/AgentConfig.kt`

- [ ] **Step 1: AgentConfig + EvalAgentFactory 구현**

```kotlin
package com.bara.evaluation.agent

data class AgentConfig(
    val apiKey: String,
    val modelId: String = "gemini-3.1-flash-lite-preview",
    val maxIterations: Int = 10,
)
```

```kotlin
package com.bara.evaluation.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

// 프로덕션 KoogAgentEngine.kt의 시스템 프롬프트와 동일하게 유지
const val SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.

전화를 걸거나 문자를 보내라는 요청이 오면:
1. 먼저 search_contacts로 연락처를 검색해
2. 검색 결과가 1개면 바로 make_call 또는 send_sms를 호출해
3. 검색 결과가 여러 개면 사용자에게 누구인지 물어봐
4. 검색 결과가 없으면 연락처를 찾을 수 없다고 말해

make_call과 send_sms 호출 시 반드시 전화번호를 사용해.
"""

class EvalAgentFactory(
    private val config: AgentConfig,
) {
    private val executor = simpleGoogleAIExecutor(config.apiKey)
    private val model = LLModel(
        provider = LLMProvider.Google,
        id = config.modelId,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
    )

    fun create(
        resolver: MockContactResolver,
        stateStore: MockStateStore,
    ): AIAgent {
        val toolRegistry = ToolRegistry {
            tool(createSearchContactsTool(resolver, stateStore))
            tool(createMakeCallTool(stateStore))
            tool(createSendSmsTool(stateStore))
        }
        return AIAgent(
            promptExecutor = executor,
            systemPrompt = SYSTEM_PROMPT.trimIndent(),
            llmModel = model,
            toolRegistry = toolRegistry,
            maxIterations = config.maxIterations,
        )
    }

    fun createWithHistory(
        resolver: MockContactResolver,
        stateStore: MockStateStore,
        conversationHistory: String,
    ): AIAgent {
        val toolRegistry = ToolRegistry {
            tool(createSearchContactsTool(resolver, stateStore))
            tool(createMakeCallTool(stateStore))
            tool(createSendSmsTool(stateStore))
        }
        val promptWithHistory = if (conversationHistory.isBlank()) {
            SYSTEM_PROMPT.trimIndent()
        } else {
            "${SYSTEM_PROMPT.trimIndent()}\n\n이전 대화:\n$conversationHistory"
        }
        return AIAgent(
            promptExecutor = executor,
            systemPrompt = promptWithHistory,
            llmModel = model,
            toolRegistry = toolRegistry,
            maxIterations = config.maxIterations,
        )
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: EvalAgentFactory 구현"
```

---

## Chunk 4: Graders

6종 Grader 구현. 각 grader는 독립적이므로 개별 테스트 가능.

### Task 7: CompareUtils (공유 매칭 유틸)

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/CompareUtils.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/graders/CompareUtilsTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.graders

import org.junit.Assert.*
import org.junit.Test

class CompareUtilsTest {
    @Test
    fun `wildcard matches any non-null value`() {
        assertTrue(matchExpect("*", "anything"))
        assertFalse(matchExpect("*", null))
    }

    @Test
    fun `regex pattern matches`() {
        assertTrue(matchExpect("regex:잘.*자", "잘 잔다"))
        assertFalse(matchExpect("regex:잘.*자", "못 잔다"))
    }

    @Test
    fun `numeric comparison`() {
        assertTrue(matchExpect(">=1", 2))
        assertTrue(matchExpect(">=1", 1))
        assertFalse(matchExpect(">=1", 0))
        assertTrue(matchExpect("<5", 3))
        assertFalse(matchExpect("<5", 5))
    }

    @Test
    fun `exact match`() {
        assertTrue(matchExpect("엄마", "엄마"))
        assertFalse(matchExpect("엄마", "아빠"))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.graders.CompareUtilsTest"`
Expected: FAIL

- [ ] **Step 3: CompareUtils.kt 구현**

```kotlin
package com.bara.evaluation.graders

fun matchExpect(expect: String, actual: Any?): Boolean {
    if (expect == "*") return actual != null

    if (expect.startsWith("regex:")) {
        val pattern = expect.removePrefix("regex:")
        return actual?.toString()?.let { Regex(pattern).containsMatchIn(it) } ?: false
    }

    val numericOps = listOf(">=", "<=", ">", "<")
    for (op in numericOps) {
        if (expect.startsWith(op)) {
            val threshold = expect.removePrefix(op).toDoubleOrNull() ?: return false
            val value = when (actual) {
                is Number -> actual.toDouble()
                else -> actual?.toString()?.toDoubleOrNull() ?: return false
            }
            return when (op) {
                ">=" -> value >= threshold
                "<=" -> value <= threshold
                ">" -> value > threshold
                "<" -> value < threshold
                else -> false
            }
        }
    }

    return actual?.toString() == expect
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.graders.CompareUtilsTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: CompareUtils 매칭 유틸 구현"
```

### Task 8: ToolCallsGrader

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/ToolCallsGrader.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/graders/ToolCallsGraderTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import org.junit.Assert.*
import org.junit.Test

class ToolCallsGraderTest {
    private val grader = ToolCallsGrader()

    @Test
    fun `superset mode passes when expected calls are subset of actual`() {
        val expected = ToolCallsExpected(
            mode = "superset",
            calls = listOf(ToolCallExpected("search_contacts", mapOf("query" to "엄마")))
        )
        val actual = listOf(
            ToolCall("search_contacts", mapOf("query" to "엄마")),
            ToolCall("make_call", mapOf("contact" to "엄마", "phoneNumber" to "010-1234-5678")),
        )
        val result = grader.grade(expected, actual)
        assertTrue(result.passed)
    }

    @Test
    fun `superset mode fails when expected call missing`() {
        val expected = ToolCallsExpected(
            mode = "superset",
            calls = listOf(
                ToolCallExpected("search_contacts", mapOf("query" to "엄마")),
                ToolCallExpected("make_call", mapOf("contact" to "엄마")),
            )
        )
        val actual = listOf(
            ToolCall("search_contacts", mapOf("query" to "엄마")),
        )
        val result = grader.grade(expected, actual)
        assertFalse(result.passed)
    }

    @Test
    fun `strict mode fails on wrong order`() {
        val expected = ToolCallsExpected(
            mode = "strict",
            calls = listOf(
                ToolCallExpected("search_contacts"),
                ToolCallExpected("make_call"),
            )
        )
        val actual = listOf(
            ToolCall("make_call", emptyMap()),
            ToolCall("search_contacts", emptyMap()),
        )
        val result = grader.grade(expected, actual)
        assertFalse(result.passed)
    }

    @Test
    fun `wildcard param matches any value`() {
        val expected = ToolCallsExpected(
            mode = "superset",
            calls = listOf(ToolCallExpected("make_call", mapOf("contact" to "*")))
        )
        val actual = listOf(
            ToolCall("make_call", mapOf("contact" to "아무나", "phoneNumber" to "010-0000-0000")),
        )
        val result = grader.grade(expected, actual)
        assertTrue(result.passed)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인 → 구현 → 통과 확인**

- [ ] **Step 3: ToolCallsGrader.kt 구현**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*

class ToolCallsGrader {
    fun grade(expected: ToolCallsExpected, actual: List<ToolCall>): GraderResult {
        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()

        when (expected.mode) {
            "strict" -> {
                if (expected.calls.size != actual.size) {
                    return fail(expected.calls.map { it.tool }, emptyList())
                }
                expected.calls.zip(actual).forEach { (exp, act) ->
                    if (matchesCall(exp, act)) matched.add(exp.tool)
                    else missing.add(exp.tool)
                }
            }
            "unordered" -> {
                val remaining = actual.toMutableList()
                for (exp in expected.calls) {
                    val found = remaining.indexOfFirst { matchesCall(exp, it) }
                    if (found >= 0) {
                        matched.add(exp.tool)
                        remaining.removeAt(found)
                    } else {
                        missing.add(exp.tool)
                    }
                }
            }
            "subset" -> {
                // actual ⊆ expected: 실제 호출이 기대 목록 안에 있어야 함
                val remaining = expected.calls.toMutableList()
                for (act in actual) {
                    val found = remaining.indexOfFirst { matchesCall(it, act) }
                    if (found >= 0) remaining.removeAt(found)
                    else missing.add(act.tool)
                }
            }
            "superset" -> {
                // expected ⊆ actual: 기대 호출이 실제 목록 안에 있어야 함
                val remaining = actual.toMutableList()
                for (exp in expected.calls) {
                    val found = remaining.indexOfFirst { matchesCall(exp, it) }
                    if (found >= 0) {
                        matched.add(exp.tool)
                        remaining.removeAt(found)
                    } else {
                        missing.add(exp.tool)
                    }
                }
            }
        }

        val passed = missing.isEmpty()
        val score = if (expected.calls.isEmpty()) 1.0
            else matched.size.toDouble() / expected.calls.size
        return GraderResult(
            grader = "tool_calls",
            passed = passed,
            score = score,
            details = mapOf(
                "matched" to matched.joinToString(","),
                "missing" to missing.joinToString(","),
            ),
        )
    }

    private fun matchesCall(expected: ToolCallExpected, actual: ToolCall): Boolean {
        if (expected.tool != actual.tool) return false
        for ((key, expectedValue) in expected.params) {
            val actualValue = actual.params[key]
            if (!matchExpect(expectedValue, actualValue)) return false
        }
        return true
    }

    private fun fail(expected: List<String>, actual: List<String>) = GraderResult(
        grader = "tool_calls", passed = false, score = 0.0,
        details = mapOf("expected" to expected.joinToString(","), "actual" to actual.joinToString(",")),
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :evaluation:test --tests "com.bara.evaluation.graders.ToolCallsGraderTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: ToolCallsGrader 구현"
```

### Task 9: OutputContains + OutputContainsAny Graders

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/OutputContainsGrader.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/OutputContainsAnyGrader.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/graders/OutputGradersTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.graders

import org.junit.Assert.*
import org.junit.Test

class OutputGradersTest {
    @Test
    fun `OutputContains passes when all keywords present`() {
        val grader = OutputContainsGrader()
        val result = grader.grade(listOf("전화", "엄마"), "엄마한테 전화를 걸까요?")
        assertTrue(result.passed)
    }

    @Test
    fun `OutputContains fails when keyword missing`() {
        val grader = OutputContainsGrader()
        val result = grader.grade(listOf("전화", "문자"), "엄마한테 전화를 걸까요?")
        assertFalse(result.passed)
    }

    @Test
    fun `OutputContainsAny passes when at least one present`() {
        val grader = OutputContainsAnyGrader()
        val result = grader.grade(listOf("걸까요?", "할까요?"), "엄마한테 전화를 걸까요?")
        assertTrue(result.passed)
    }

    @Test
    fun `OutputContainsAny fails when none present`() {
        val grader = OutputContainsAnyGrader()
        val result = grader.grade(listOf("문자", "SMS"), "엄마한테 전화를 걸까요?")
        assertFalse(result.passed)
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.GraderResult

class OutputContainsGrader {
    fun grade(expected: List<String>, output: String): GraderResult {
        val matched = expected.filter { output.contains(it) }
        val missing = expected.filter { !output.contains(it) }
        return GraderResult(
            grader = "output_contains",
            passed = missing.isEmpty(),
            score = if (expected.isEmpty()) 1.0 else matched.size.toDouble() / expected.size,
            details = mapOf("matched" to matched.joinToString(","), "missing" to missing.joinToString(",")),
        )
    }
}

class OutputContainsAnyGrader {
    fun grade(expected: List<String>, output: String): GraderResult {
        val matched = expected.filter { output.contains(it) }
        return GraderResult(
            grader = "output_contains_any",
            passed = matched.isNotEmpty(),
            score = if (matched.isNotEmpty()) 1.0 else 0.0,
            details = mapOf("matched" to matched.joinToString(",")),
        )
    }
}
```

- [ ] **Step 3: 테스트 통과 확인 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: OutputContains + OutputContainsAny Grader 구현"
```

### Task 10: ConstraintsGrader

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/ConstraintsGrader.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/graders/ConstraintsGraderTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import org.junit.Assert.*
import org.junit.Test

class ConstraintsGraderTest {
    private val grader = ConstraintsGrader()

    @Test
    fun `passes when within all constraints`() {
        val expected = ConstraintsExpected(maxTurns = 5, maxToolcalls = 3, timeout = 10000)
        val metrics = Metrics(turns = 3, toolCallCount = 2, durationMs = 5000)
        assertTrue(grader.grade(expected, metrics).passed)
    }

    @Test
    fun `fails when turns exceeded`() {
        val expected = ConstraintsExpected(maxTurns = 3)
        val metrics = Metrics(turns = 5)
        assertFalse(grader.grade(expected, metrics).passed)
    }

    @Test
    fun `fails when timeout exceeded`() {
        val expected = ConstraintsExpected(timeout = 5000)
        val metrics = Metrics(durationMs = 6000)
        assertFalse(grader.grade(expected, metrics).passed)
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*

class ConstraintsGrader {
    fun grade(expected: ConstraintsExpected, metrics: Metrics): GraderResult {
        val violations = mutableListOf<String>()
        expected.maxTurns?.let {
            if (metrics.turns > it) violations.add("turns: ${metrics.turns} > $it")
        }
        expected.maxToolcalls?.let {
            if (metrics.toolCallCount > it) violations.add("toolcalls: ${metrics.toolCallCount} > $it")
        }
        expected.timeout?.let {
            if (metrics.durationMs > it) violations.add("timeout: ${metrics.durationMs}ms > ${it}ms")
        }
        return GraderResult(
            grader = "constraints",
            passed = violations.isEmpty(),
            score = if (violations.isEmpty()) 1.0 else 0.0,
            details = if (violations.isNotEmpty()) mapOf("violations" to violations.joinToString("; ")) else emptyMap(),
        )
    }
}
```

- [ ] **Step 3: 테스트 통과 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: ConstraintsGrader 구현"
```

### Task 11: StateCheckGrader

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/StateCheckGrader.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/graders/StateCheckGraderTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import com.bara.evaluation.mocks.MockStateStore
import org.junit.Assert.*
import org.junit.Test

class StateCheckGraderTest {
    private val grader = StateCheckGrader()

    @Test
    fun `passes when all checks match`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        val checks = listOf(
            StateCheckExpected("calls.last.contact", "엄마"),
            StateCheckExpected("calls.count", ">=1"),
        )
        assertTrue(grader.grade(checks, store).passed)
    }

    @Test
    fun `fails when value mismatch`() {
        val store = MockStateStore()
        store.recordCall("아빠", "010-2345-6789")
        val checks = listOf(
            StateCheckExpected("calls.last.contact", "엄마"),
        )
        assertFalse(grader.grade(checks, store).passed)
    }

    @Test
    fun `wildcard passes when value exists`() {
        val store = MockStateStore()
        store.recordSms("엄마", "010-1234-5678", "잘 잔다")
        val checks = listOf(StateCheckExpected("sms.last.message", "*"))
        assertTrue(grader.grade(checks, store).passed)
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import com.bara.evaluation.mocks.MockStateStore

class StateCheckGrader {
    fun grade(checks: List<StateCheckExpected>, store: MockStateStore): GraderResult {
        val failures = mutableListOf<String>()
        for (check in checks) {
            val actual = store.get(check.key)
            if (!matchExpect(check.expect, actual)) {
                failures.add("${check.key}: expected=${check.expect}, actual=$actual")
            }
        }
        return GraderResult(
            grader = "state_check",
            passed = failures.isEmpty(),
            score = if (checks.isEmpty()) 1.0 else (checks.size - failures.size).toDouble() / checks.size,
            details = if (failures.isNotEmpty()) mapOf("failures" to failures.joinToString("; ")) else emptyMap(),
        )
    }
}
```

- [ ] **Step 3: 테스트 통과 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: StateCheckGrader 구현"
```

### Task 12: LlmGrader

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/graders/LlmGrader.kt`
- Create: `evaluation/src/main/resources/rubrics/response-quality.md`
- Create: `evaluation/src/main/resources/rubrics/conversation-quality.md`

- [ ] **Step 1: LlmGrader 구현**

LLM 호출이 필요하므로 단위 테스트 대신 통합 테스트로 검증한다. rubric 파일 로딩과 프롬프트 구성 로직만 구현.

```kotlin
package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.serialization.json.Json

class LlmGrader(
    private val apiKey: String,
    private val defaultModel: String = "gemini-3.1-pro-preview",
) {
    private val client = Client.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun grade(
        expected: LlmGraderExpected,
        input: String,
        output: String,
        toolCalls: List<ToolCall>,
    ): GraderResult {
        val rubric = loadRubric(expected.rubric)

        val prompt = buildString {
            appendLine("# 평가 요청")
            appendLine()
            appendLine("## Rubric")
            appendLine(rubric)
            appendLine()
            appendLine("## 사용자 입력")
            appendLine(input)
            appendLine()
            appendLine("## 에이전트 응답")
            appendLine(output)
            appendLine()
            if (toolCalls.isNotEmpty()) {
                appendLine("## Tool 호출")
                toolCalls.forEach { appendLine("- ${it.tool}(${it.params})") }
                appendLine()
            }
            appendLine("## 출력 형식")
            appendLine("JSON으로 응답하세요: {\"score\": 0.0~1.0, \"reasoning\": \"이유\"}")
        }

        return try {
            val response = client.models.generateContent(
                defaultModel,
                prompt,
                GenerateContentConfig.builder()
                    .temperature(0.0)
                    .build()
            )
            val text = response.text().orEmpty()
            val jsonStr = extractJson(text)
            val parsed = json.decodeFromString<LlmGraderOutput>(jsonStr)
            GraderResult(
                grader = "llm_grader",
                passed = parsed.score >= expected.minScore,
                score = parsed.score,
                details = mapOf("reasoning" to parsed.reasoning),
            )
        } catch (e: Exception) {
            GraderResult(
                grader = "llm_grader",
                passed = false,
                score = 0.0,
                details = mapOf("error" to (e.message ?: "unknown error")),
            )
        }
    }

    suspend fun gradeConversation(
        rubricFile: String,
        minScore: Double,
        messages: List<Message>,
    ): GraderResult {
        val rubric = loadRubric(rubricFile)

        val prompt = buildString {
            appendLine("# 대화 품질 평가")
            appendLine()
            appendLine("## Rubric")
            appendLine(rubric)
            appendLine()
            appendLine("## 대화 내용")
            messages.forEach { appendLine("${it.role}: ${it.content}") }
            appendLine()
            appendLine("## 출력 형식")
            appendLine("JSON으로 응답하세요: {\"score\": 0.0~1.0, \"reasoning\": \"이유\"}")
        }

        return try {
            val response = client.models.generateContent(
                defaultModel,
                prompt,
                GenerateContentConfig.builder()
                    .temperature(0.0)
                    .build()
            )
            val text = response.text().orEmpty()
            val jsonStr = extractJson(text)
            val parsed = json.decodeFromString<LlmGraderOutput>(jsonStr)
            GraderResult(
                grader = "llm_grader",
                passed = parsed.score >= minScore,
                score = parsed.score,
                details = mapOf("reasoning" to parsed.reasoning),
            )
        } catch (e: Exception) {
            GraderResult(
                grader = "llm_grader",
                passed = false,
                score = 0.0,
                details = mapOf("error" to (e.message ?: "unknown error")),
            )
        }
    }

    private fun loadRubric(filename: String): String {
        return this::class.java.classLoader
            .getResource("rubrics/$filename")
            ?.readText()
            ?: error("Rubric not found: $filename")
    }

    private fun extractJson(text: String): String {
        // ```json ... ``` 또는 { ... } 추출
        val codeBlock = Regex("```json\\s*\\n(.*?)\\n\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
        if (codeBlock != null) return codeBlock.trim()

        val jsonObj = Regex("\\{.*}", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.value
        return jsonObj ?: error("JSON not found in LLM response: $text")
    }
}

@kotlinx.serialization.Serializable
data class LlmGraderOutput(
    val score: Double,
    val reasoning: String,
)
```

- [ ] **Step 2: rubric 파일 생성**

Create: `evaluation/src/main/resources/rubrics/response-quality.md`
(spec에 정의된 내용 그대로)

Create: `evaluation/src/main/resources/rubrics/conversation-quality.md`
(spec에 정의된 내용 그대로)

- [ ] **Step 3: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: LlmGrader + rubric 파일 구현"
```

### Task 13: GraderEngine (Grader 조합)

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/GraderEngine.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/core/GraderEngineTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.mocks.MockStateStore
import org.junit.Assert.*
import org.junit.Test

class GraderEngineTest {
    @Test
    fun `runs only specified graders`() {
        val engine = GraderEngine(apiKey = "dummy")
        val expected = Expected(
            outputContains = listOf("전화"),
            constraints = ConstraintsExpected(maxTurns = 5),
        )
        val transcript = Transcript(
            messages = listOf(Message("assistant", "엄마한테 전화를 걸까요?")),
            toolCalls = emptyList(),
            metrics = Metrics(turns = 3),
        )
        val results = engine.gradeSync(
            expected = expected,
            transcript = transcript,
            stateStore = MockStateStore(),
            input = "엄마한테 전화해줘",
        )
        // tool_calls와 llm_grader는 expected에 없으므로 실행 안 됨
        assertEquals(2, results.size)
        assertTrue(results.all { it.passed })
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.graders.*
import com.bara.evaluation.mocks.MockStateStore

class GraderEngine(
    private val apiKey: String = "",
    private val graderFilter: Set<String>? = null,
) {
    private val toolCallsGrader = ToolCallsGrader()
    private val outputContainsGrader = OutputContainsGrader()
    private val outputContainsAnyGrader = OutputContainsAnyGrader()
    private val constraintsGrader = ConstraintsGrader()
    private val stateCheckGrader = StateCheckGrader()
    private val llmGrader: LlmGrader? = if (apiKey.isNotEmpty()) LlmGrader(apiKey) else null

    private fun shouldRun(name: String): Boolean =
        graderFilter == null || name in graderFilter

    fun gradeSync(
        expected: Expected,
        transcript: Transcript,
        stateStore: MockStateStore,
        input: String,
    ): List<GraderResult> {
        val results = mutableListOf<GraderResult>()
        val output = transcript.messages.lastOrNull { it.role == "assistant" }?.content ?: ""

        if (expected.toolCalls != null && shouldRun("tool_calls")) {
            results.add(toolCallsGrader.grade(expected.toolCalls, transcript.toolCalls))
        }
        if (expected.outputContains != null && shouldRun("output_contains")) {
            results.add(outputContainsGrader.grade(expected.outputContains, output))
        }
        if (expected.outputContainsAny != null && shouldRun("output_contains_any")) {
            results.add(outputContainsAnyGrader.grade(expected.outputContainsAny, output))
        }
        if (expected.constraints != null && shouldRun("constraints")) {
            results.add(constraintsGrader.grade(expected.constraints, transcript.metrics))
        }
        if (expected.stateCheck != null && shouldRun("state_check")) {
            results.add(stateCheckGrader.grade(expected.stateCheck, stateStore))
        }

        return results
    }

    suspend fun gradeFull(
        expected: Expected,
        transcript: Transcript,
        stateStore: MockStateStore,
        input: String,
    ): List<GraderResult> {
        val results = gradeSync(expected, transcript, stateStore, input).toMutableList()
        val output = transcript.messages.lastOrNull { it.role == "assistant" }?.content ?: ""

        if (expected.llmGrader != null && shouldRun("llm_grader") && llmGrader != null) {
            results.add(llmGrader.grade(expected.llmGrader, input, output, transcript.toolCalls))
        }

        return results
    }
}
```

- [ ] **Step 3: 테스트 통과 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: GraderEngine 구현"
```

---

## Chunk 5: Task 로딩 + EvalRunner + CLI

YAML 태스크 로딩, Single-turn 평가 실행, CLI 인터페이스.

### Task 14: TaskManager (YAML 로딩)

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/TaskManager.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/core/TaskManagerTest.kt`
- Create: `evaluation/src/test/resources/tasks/voice-agent/test-task-001.yaml`

- [ ] **Step 1: 테스트용 YAML 파일 생성**

Create: `evaluation/src/test/resources/tasks/voice-agent/test-task-001.yaml`

```yaml
id: voice-agent/test-task-001
input: "엄마한테 전화해줘"
dataset: default-contacts
expected:
  tool_calls:
    mode: superset
    calls:
      - tool: search_contacts
        params:
          query: "엄마"
  output_contains:
    - "전화"
metadata:
  category: call
  description: "테스트용 태스크"
  tags: [test]
```

- [ ] **Step 2: TaskManager 테스트 작성**

```kotlin
package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test

class TaskManagerTest {
    @Test
    fun `loads tasks from resources`() {
        val manager = TaskManager("tasks")
        val tasks = manager.loadAll()
        assertTrue(tasks.isNotEmpty())
    }

    @Test
    fun `parses task yaml correctly`() {
        val manager = TaskManager("tasks")
        val tasks = manager.loadAll()
        val task = tasks.find { it.id == "voice-agent/test-task-001" }
        assertNotNull(task)
        assertEquals("엄마한테 전화해줘", task!!.input)
        assertEquals("superset", task.expected.toolCalls!!.mode)
    }
}
```

- [ ] **Step 3: TaskManager 구현**

```kotlin
package com.bara.evaluation.core

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.io.File

class TaskManager(
    private val basePath: String = "tasks",
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
        ),
    )

    fun loadAll(): List<Task> {
        val resource = this::class.java.classLoader.getResource(basePath)
            ?: return emptyList()
        val dir = File(resource.toURI())
        return dir.walkTopDown()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .filter { !it.path.contains("roleplay") }
            .map { loadTask(it) }
            .toList()
    }

    fun loadByAgent(agent: String): List<Task> {
        return loadAll().filter { it.id.startsWith(agent) }
    }

    fun loadById(id: String): Task? {
        return loadAll().find { it.id == id }
    }

    private fun loadTask(file: File): Task {
        return yaml.decodeFromString(Task.serializer(), file.readText())
    }
}
```

- [ ] **Step 4: 테스트 통과 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: TaskManager YAML 로딩 구현"
```

### Task 15: AgentRunner

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/AgentRunner.kt`

- [ ] **Step 1: 구현**

AgentRunner는 Koog Agent를 실행하고 Transcript를 수집한다. LLM 호출이 필요하므로 통합 테스트로 검증.

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import com.bara.evaluation.agent.EvalAgentFactory
import com.bara.evaluation.datasets.DATASETS
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

class AgentRunner(
    private val config: AgentConfig,
) {
    suspend fun run(task: Task): AgentRunResult {
        val contacts = DATASETS[task.dataset]
            ?: error("Unknown dataset: ${task.dataset}")
        val resolver = MockContactResolver(contacts)
        val stateStore = MockStateStore()
        val factory = EvalAgentFactory(config)
        val agent = factory.create(resolver, stateStore)

        val toolCalls = mutableListOf<ToolCall>()
        val startTime = System.currentTimeMillis()

        val result = agent.run(task.input)

        val durationMs = System.currentTimeMillis() - startTime

        // TODO: Koog SDK에서 tool call 이력 추출 방법 확인 필요
        // 현재는 stateStore에서 역추론
        stateStore.searchQueries.forEach { query ->
            toolCalls.add(ToolCall("search_contacts", mapOf("query" to query)))
        }
        stateStore.calls.forEach { call ->
            toolCalls.add(ToolCall("make_call", mapOf("contact" to call.contact, "phoneNumber" to call.phoneNumber)))
        }
        stateStore.sms.forEach { sms ->
            toolCalls.add(ToolCall("send_sms", mapOf("contact" to sms.contact, "phoneNumber" to sms.phoneNumber, "message" to sms.message)))
        }

        val transcript = Transcript(
            messages = listOf(
                Message("user", task.input),
                Message("assistant", result),
            ),
            toolCalls = toolCalls,
            metrics = Metrics(
                turns = 1,
                toolCallCount = toolCalls.size,
                durationMs = durationMs,
            ),
        )

        return AgentRunResult(transcript, stateStore)
    }
}

data class AgentRunResult(
    val transcript: Transcript,
    val stateStore: MockStateStore,
)
```

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: AgentRunner 구현"
```

### Task 16: EvalRunner (오케스트레이터)

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/EvalRunner.kt`

- [ ] **Step 1: 구현**

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class EvalRunner(
    private val apiKey: String,
    private val concurrency: Int = 4,
    private val graderFilter: Set<String>? = null,
    private val cache: Cache? = null,
) {
    suspend fun run(tasks: List<Task>): List<Outcome> {
        val semaphore = Semaphore(concurrency)
        val config = AgentConfig(apiKey)
        val agentRunner = AgentRunner(config)
        val graderEngine = GraderEngine(apiKey, graderFilter)

        return coroutineScope {
            tasks.map { task ->
                async {
                    semaphore.withPermit {
                        runTask(task, agentRunner, graderEngine)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun runTask(
        task: Task,
        agentRunner: AgentRunner,
        graderEngine: GraderEngine,
    ): Outcome {
        // 캐시 확인
        if (cache?.has(task) == true) {
            return Outcome(taskId = task.id, graderResults = emptyList(), cached = true)
        }

        return try {
            val result = agentRunner.run(task)
            val graderResults = graderEngine.gradeFull(
                expected = task.expected,
                transcript = result.transcript,
                stateStore = result.stateStore,
                input = task.input,
            )
            val outcome = Outcome(
                taskId = task.id,
                graderResults = graderResults,
                transcript = result.transcript,
            )
            if (outcome.status == "passed") {
                cache?.add(task)
            }
            outcome
        } catch (e: Exception) {
            Outcome(
                taskId = task.id,
                graderResults = emptyList(),
                error = e.message ?: "Unknown error",
            )
        }
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: EvalRunner 오케스트레이터 구현"
```

### Task 17: Cache

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/Cache.kt`
- Create: `evaluation/src/test/kotlin/com/bara/evaluation/core/CacheTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CacheTest {
    @Test
    fun `generates consistent hash for same task`() {
        val task = Task(id = "test-001", input = "hello")
        val hash1 = Cache.computeHash(task)
        val hash2 = Cache.computeHash(task)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `generates different hash for different input`() {
        val task1 = Task(id = "test-001", input = "hello")
        val task2 = Task(id = "test-001", input = "world")
        assertNotEquals(Cache.computeHash(task1), Cache.computeHash(task2))
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.agent.SYSTEM_PROMPT
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class Cache(
    private val cacheDir: String = "evaluation/cache",
) {
    private val cacheFile = File(cacheDir, "passed_tasks.json")
    private val json = Json { prettyPrint = true }
    private val entries: MutableMap<String, String> = loadEntries()

    fun has(task: Task): Boolean {
        val hash = computeHash(task)
        return entries[task.id] == hash
    }

    fun add(task: Task) {
        val hash = computeHash(task)
        entries[task.id] = hash
        save()
    }

    fun clear() {
        entries.clear()
        save()
    }

    fun clearTask(taskId: String) {
        entries.remove(taskId)
        save()
    }

    fun status(): Map<String, String> = entries.toMap()

    private fun loadEntries(): MutableMap<String, String> {
        if (!cacheFile.exists()) return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(cacheFile.readText()).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    @Synchronized
    private fun save() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                kotlinx.serialization.builtins.serializer<String>(),
                kotlinx.serialization.builtins.serializer<String>(),
            ),
            entries
        ))
    }

    companion object {
        fun computeHash(task: Task): String {
            val content = buildString {
                append(task.id)
                append(task.input)
                append(task.dataset)
                append(Json.encodeToString(Expected.serializer(), task.expected))
                append(SYSTEM_PROMPT)
                append("gemini-3.1-flash-lite-preview") // model ID
            }
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(content.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }
    }
}
```

- [ ] **Step 3: 테스트 통과 → 커밋**

```bash
git add evaluation/src/
git commit -m "feat: Cache 해시 기반 캐싱 구현"
```

### Task 18: Reporter

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/reporters/ConsoleReporter.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/reporters/JsonReporter.kt`

- [ ] **Step 1: ConsoleReporter 구현**

```kotlin
package com.bara.evaluation.reporters

import com.bara.evaluation.core.Outcome

class ConsoleReporter {
    fun report(outcomes: List<Outcome>) {
        println("\n=== Hey Bara Evaluation Results ===\n")
        for (outcome in outcomes) {
            val graders = outcome.graderResults.joinToString(" ") { result ->
                val icon = if (result.passed) "✓" else "✗"
                val score = if (result.grader == "llm_grader") " %.2f".format(result.score) else ""
                "[${result.grader} $icon$score]"
            }
            val status = outcome.status.uppercase().padEnd(6)
            println("${outcome.taskId.padEnd(45)} $status $graders")
            if (outcome.error != null) {
                println("  ERROR: ${outcome.error}")
            }
        }

        val total = outcomes.size
        val passed = outcomes.count { it.status == "passed" }
        val failed = outcomes.count { it.status == "failed" }
        val errors = outcomes.count { it.status == "error" }
        val cached = outcomes.count { it.status == "cached" }
        val passRate = if (total > 0) (passed + cached) * 100.0 / total else 0.0
        println("\nTotal: $total | Passed: $passed | Failed: $failed | Errors: $errors | Cached: $cached | Pass Rate: ${"%.1f".format(passRate)}%")
    }
}
```

- [ ] **Step 2: JsonReporter 구현**

```kotlin
package com.bara.evaluation.reporters

import com.bara.evaluation.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class EvalSummary(
    val runId: String,
    val timestamp: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val errors: Int,
    val cached: Int,
    val passRate: Double,
)

class JsonReporter(
    private val outputDir: String = "evaluation/results",
) {
    private val json = Json { prettyPrint = true }

    fun report(outcomes: List<Outcome>): String {
        val now = LocalDateTime.now()
        val runId = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val dir = File(outputDir, runId)
        dir.mkdirs()

        val summary = EvalSummary(
            runId = runId,
            timestamp = now.toString(),
            total = outcomes.size,
            passed = outcomes.count { it.status == "passed" },
            failed = outcomes.count { it.status == "failed" },
            errors = outcomes.count { it.status == "error" },
            cached = outcomes.count { it.status == "cached" },
            passRate = if (outcomes.isNotEmpty())
                outcomes.count { it.status in listOf("passed", "cached") } * 100.0 / outcomes.size
            else 0.0,
        )

        val file = File(dir, "summary.json")
        file.writeText(json.encodeToString(EvalSummary.serializer(), summary))
        return file.absolutePath
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: Console + JSON Reporter 구현"
```

### Task 19: CLI (Clikt)

**Files:**
- Modify: `evaluation/src/main/kotlin/com/bara/evaluation/Main.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/cli/Commands.kt`

- [ ] **Step 1: CLI 커맨드 구현**

```kotlin
package com.bara.evaluation.cli

import com.bara.evaluation.core.*
import com.bara.evaluation.reporters.ConsoleReporter
import com.bara.evaluation.reporters.JsonReporter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

class EvalCli : CliktCommand(name = "eval") {
    override fun run() = Unit
}

class TasksCommand : CliktCommand(name = "tasks", help = "태스크 목록 조회") {
    private val agent by option("--agent", "-a", help = "에이전트 필터")
    private val verbose by option("--verbose", "-v", help = "상세 출력").flag()

    override fun run() {
        val manager = TaskManager()
        val tasks = if (agent != null) manager.loadByAgent(agent!!) else manager.loadAll()
        echo("총 ${tasks.size}개 태스크\n")
        for (task in tasks) {
            echo("  ${task.id}")
            if (verbose) {
                echo("    input: ${task.input}")
                echo("    dataset: ${task.dataset}")
                echo("    tags: ${task.metadata.tags}")
                echo("")
            }
        }
    }
}

class RunCommand : CliktCommand(name = "run", help = "Single-turn 평가 실행") {
    private val task by option("--task", "-t", help = "단일 태스크 ID")
    private val agent by option("--agent", "-a", help = "에이전트 필터")
    private val graders by option("--graders", "-g", help = "grader 필터 (쉼표 구분)")
    private val concurrency by option("--concurrency", "-c", help = "동시 실행 수").int().default(4)
    private val noCache by option("--no-cache", help = "캐시 무시").flag()
    private val noSave by option("--no-save", help = "결과 저장 안 함").flag()

    override fun run() = runBlocking {
        val apiKey = System.getenv("GEMINI_API_KEY")
            ?: error("GEMINI_API_KEY 환경변수를 설정하세요")

        val manager = TaskManager()
        val tasks = when {
            task != null -> listOfNotNull(manager.loadById(task!!))
            agent != null -> manager.loadByAgent(agent!!)
            else -> manager.loadAll()
        }

        if (tasks.isEmpty()) {
            echo("실행할 태스크가 없습니다")
            return@runBlocking
        }

        val graderFilter = graders?.split(",")?.map { it.trim() }?.toSet()
        val cache = if (noCache) null else Cache()

        val runner = EvalRunner(
            apiKey = apiKey,
            concurrency = concurrency,
            graderFilter = graderFilter,
            cache = cache,
        )

        echo("${tasks.size}개 태스크 실행 중... (concurrency=$concurrency)")
        val outcomes = runner.run(tasks)

        ConsoleReporter().report(outcomes)

        if (!noSave) {
            val path = JsonReporter().report(outcomes)
            echo("\n결과 저장: $path")
        }
    }
}

class CacheStatusCommand : CliktCommand(name = "cache-status", help = "캐시 상태 조회") {
    override fun run() {
        val cache = Cache()
        val entries = cache.status()
        if (entries.isEmpty()) {
            echo("캐시가 비어있습니다")
        } else {
            echo("캐시된 태스크: ${entries.size}개")
            entries.forEach { (id, hash) -> echo("  $id ($hash)") }
        }
    }
}

class ClearCacheCommand : CliktCommand(name = "clear-cache", help = "캐시 삭제") {
    private val task by option("--task", "-t", help = "단일 태스크 캐시만 삭제")

    override fun run() {
        val cache = Cache()
        if (task != null) {
            cache.clearTask(task!!)
            echo("캐시 삭제: $task")
        } else {
            cache.clear()
            echo("전체 캐시 삭제 완료")
        }
    }
}

fun buildCli(): EvalCli {
    return EvalCli().subcommands(
        TasksCommand(),
        RunCommand(),
        CacheStatusCommand(),
        ClearCacheCommand(),
    )
}
```

- [ ] **Step 2: Main.kt 수정**

```kotlin
package com.bara.evaluation

import com.bara.evaluation.cli.buildCli

fun main(args: Array<String>) {
    buildCli().main(args)
}
```

- [ ] **Step 3: CLI 동작 확인**

Run: `./gradlew :evaluation:run --args="tasks"`
Expected: 태스크 목록 출력 (test-task-001 포함)

- [ ] **Step 4: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: CLI 인터페이스 구현 (Clikt)"
```

---

## Chunk 6: Roleplay 시스템

멀티턴 평가를 위한 Roleplayer, Judge, RoleplayRunner.

### Task 20: ScenarioManager

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/ScenarioManager.kt`

- [ ] **Step 1: 구현**

TaskManager와 유사하되 Scenario 타입을 로딩. roleplay/ 하위 디렉토리의 YAML만 파싱.

```kotlin
package com.bara.evaluation.core

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.io.File

class ScenarioManager(
    private val basePath: String = "tasks",
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
        ),
    )

    fun loadAll(): List<Scenario> {
        val resource = this::class.java.classLoader.getResource(basePath)
            ?: return emptyList()
        val dir = File(resource.toURI())
        return dir.walkTopDown()
            .filter { it.path.contains("roleplay") }
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .map { yaml.decodeFromString(Scenario.serializer(), it.readText()) }
            .toList()
    }

    fun loadByAgent(agent: String): List<Scenario> {
        return loadAll().filter { it.id.startsWith(agent) }
    }

    fun loadById(id: String): Scenario? {
        return loadAll().find { it.id == id }
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: ScenarioManager 구현"
```

### Task 21: Roleplayer + Judge

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/Roleplayer.kt`
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/Judge.kt`

- [ ] **Step 1: Roleplayer 구현**

```kotlin
package com.bara.evaluation.core

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig

class Roleplayer(
    apiKey: String,
    private val model: String = "gemini-3.1-pro-preview",
) {
    private val client = Client.builder().apiKey(apiKey).build()

    suspend fun generateMessage(
        persona: String,
        conversationHistory: List<Message>,
    ): String {
        val prompt = buildString {
            appendLine("# 역할")
            appendLine("당신은 아래 페르소나에 맞는 사용자 역할을 합니다.")
            appendLine("음성 비서 '바라'와 대화하고 있습니다.")
            appendLine("자연스러운 한국어로 짧게 답하세요.")
            appendLine()
            appendLine("## 페르소나")
            appendLine(persona)
            appendLine()
            appendLine("## 대화 기록")
            conversationHistory.forEach { appendLine("${it.role}: ${it.content}") }
            appendLine()
            appendLine("## 지시")
            appendLine("위 대화에 이어서 사용자로서 한 마디 하세요. 사용자: 로 시작하지 말고 말만 하세요.")
        }

        val response = client.models.generateContent(
            model, prompt,
            GenerateContentConfig.builder().temperature(0.7).build()
        )
        return response.text().orEmpty().trim()
    }
}
```

- [ ] **Step 2: Judge 구현**

```kotlin
package com.bara.evaluation.core

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class JudgeVerdict(
    val verdict: String,  // "continue" | "goal_achieved" | "cannot_continue"
    val reasoning: String,
    val goalProgress: String = "",
)

class Judge(
    apiKey: String,
    private val model: String = "gemini-3.1-pro-preview",
) {
    private val client = Client.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun evaluate(
        goal: String,
        conversationHistory: List<Message>,
    ): JudgeVerdict {
        val prompt = buildString {
            appendLine("# 판단 요청")
            appendLine("음성 비서 '바라'와 사용자의 대화를 보고 목표 달성 여부를 판단하세요.")
            appendLine()
            appendLine("## 목표")
            appendLine(goal)
            appendLine()
            appendLine("## 대화 기록")
            conversationHistory.forEach { appendLine("${it.role}: ${it.content}") }
            appendLine()
            appendLine("## 판단 기준")
            appendLine("- goal_achieved: 목표의 모든 항목이 달성됨")
            appendLine("- cannot_continue: 대화가 막혔거나 에이전트가 실패함")
            appendLine("- continue: 아직 진행 중")
            appendLine()
            appendLine("## 출력 형식")
            appendLine("JSON: {\"verdict\": \"continue|goal_achieved|cannot_continue\", \"reasoning\": \"이유\", \"goalProgress\": \"진행 상황\"}")
        }

        val response = client.models.generateContent(
            model, prompt,
            GenerateContentConfig.builder().temperature(0.0).build()
        )
        val text = response.text().orEmpty()
        val jsonStr = Regex("\\{.*}", RegexOption.DOT_MATCHES_ALL).find(text)?.value
            ?: return JudgeVerdict("cannot_continue", "Judge 응답 파싱 실패: $text")
        return try {
            json.decodeFromString<JudgeVerdict>(jsonStr)
        } catch (e: Exception) {
            JudgeVerdict("cannot_continue", "JSON 파싱 실패: ${e.message}")
        }
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: Roleplayer + Judge 구현"
```

### Task 22: RoleplayRunner

**Files:**
- Create: `evaluation/src/main/kotlin/com/bara/evaluation/core/RoleplayRunner.kt`

- [ ] **Step 1: 구현**

```kotlin
package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import com.bara.evaluation.agent.EvalAgentFactory
import com.bara.evaluation.datasets.DATASETS
import com.bara.evaluation.graders.LlmGrader
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

class RoleplayRunner(
    private val apiKey: String,
) {
    suspend fun run(scenario: Scenario): Outcome {
        val contacts = DATASETS[scenario.dataset]
            ?: error("Unknown dataset: ${scenario.dataset}")
        val resolver = MockContactResolver(contacts)
        val stateStore = MockStateStore()
        val config = AgentConfig(apiKey)
        val factory = EvalAgentFactory(config)
        val agent = factory.create(resolver, stateStore)
        val roleplayer = Roleplayer(apiKey)
        val judge = Judge(apiKey)
        val llmGrader = LlmGrader(apiKey)

        val messages = mutableListOf<Message>()

        // 첫 메시지
        val firstMessage = scenario.initialMessage
            ?: roleplayer.generateMessage(scenario.persona, emptyList())
        messages.add(Message("user", firstMessage))

        var finalVerdict = "max_turns_reached"

        for (turn in 1..scenario.maxTurns) {
            // Agent 응답 — 대화 히스토리를 시스템 프롬프트에 포함하여 컨텍스트 유지
            // 매 턴마다 새 에이전트를 생성하되 이전 대화를 시스템 프롬프트에 주입
            val historyPrompt = messages.dropLast(1).joinToString("\n") { "${it.role}: ${it.content}" }
            val agentWithHistory = factory.createWithHistory(resolver, stateStore, historyPrompt)
            val agentResponse = try {
                agentWithHistory.run(messages.last().content)
            } catch (e: Exception) {
                return Outcome(
                    taskId = scenario.id,
                    graderResults = emptyList(),
                    error = "Agent error at turn $turn: ${e.message}",
                )
            }
            messages.add(Message("assistant", agentResponse))

            // Judge 판단
            val verdict = judge.evaluate(scenario.goal, messages)
            when (verdict.verdict) {
                "goal_achieved" -> {
                    finalVerdict = "goal_achieved"
                    break
                }
                "cannot_continue" -> {
                    finalVerdict = "cannot_continue"
                    break
                }
            }

            // 마지막 턴이면 종료
            if (turn >= scenario.maxTurns) break

            // Roleplayer 다음 메시지
            val userMessage = roleplayer.generateMessage(scenario.persona, messages)
            messages.add(Message("user", userMessage))
        }

        // Rubric 채점
        val rubricResult = llmGrader.gradeConversation(
            rubricFile = scenario.completionCriteria.rubric,
            minScore = scenario.completionCriteria.minScore,
            messages = messages,
        )

        // cannot_continue면 자동 실패
        val graderResults = if (finalVerdict == "cannot_continue") {
            listOf(rubricResult.copy(passed = false, details = rubricResult.details + ("verdict" to "cannot_continue")))
        } else {
            listOf(rubricResult)
        }

        val transcript = Transcript(
            messages = messages,
            toolCalls = emptyList(),
            metrics = Metrics(turns = messages.count { it.role == "user" }),
        )

        return Outcome(
            taskId = scenario.id,
            graderResults = graderResults,
            transcript = transcript,
        )
    }
}
```

- [ ] **Step 2: CLI에 roleplay 커맨드 추가**

Commands.kt에 추가:

```kotlin
class ScenariosCommand : CliktCommand(name = "scenarios", help = "Roleplay 시나리오 목록") {
    private val agent by option("--agent", "-a")
    override fun run() {
        val manager = ScenarioManager()
        val scenarios = if (agent != null) manager.loadByAgent(agent!!) else manager.loadAll()
        echo("총 ${scenarios.size}개 시나리오\n")
        scenarios.forEach { echo("  ${it.id} — ${it.metadata.description}") }
    }
}

class RoleplayCommand : CliktCommand(name = "roleplay", help = "Roleplay 평가 실행") {
    private val scenario by option("--scenario", "-s")
    private val agent by option("--agent", "-a")
    override fun run() = runBlocking {
        val apiKey = System.getenv("GEMINI_API_KEY")
            ?: error("GEMINI_API_KEY 환경변수를 설정하세요")
        val manager = ScenarioManager()
        val scenarios = when {
            scenario != null -> listOfNotNull(manager.loadById(scenario!!))
            agent != null -> manager.loadByAgent(agent!!)
            else -> manager.loadAll()
        }
        if (scenarios.isEmpty()) {
            echo("실행할 시나리오가 없습니다")
            return@runBlocking
        }
        val runner = RoleplayRunner(apiKey)
        echo("${scenarios.size}개 시나리오 실행 중...")
        val outcomes = scenarios.map { runner.run(it) }
        ConsoleReporter().report(outcomes)
    }
}
```

buildCli()에 추가: `ScenariosCommand()`, `RoleplayCommand()`

- [ ] **Step 3: 커밋**

```bash
git add evaluation/src/
git commit -m "feat: RoleplayRunner + CLI roleplay 커맨드 구현"
```

---

## Chunk 7: 초기 테스트 케이스

실제 평가용 YAML 태스크 파일 작성.

### Task 23: Single-turn 태스크 YAML 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/call-basic-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/call-basic-002.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/sms-basic-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/contact-not-found-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/general-chat-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/general-chat-002.yaml`

- [ ] **Step 1: 6개 YAML 파일 작성**

각 파일은 spec의 "초기 테스트 케이스" 섹션에 정의된 내용을 따른다.

예시 — `call-basic-001.yaml`:
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
  output_contains_any:
    - "전화"
    - "걸"
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
  tags: [call, basic]
```

`contact-not-found-001.yaml`:
```yaml
id: voice-agent/contact-not-found-001
input: "홍길동한테 전화해줘"
dataset: default-contacts
expected:
  tool_calls:
    mode: superset
    calls:
      - tool: search_contacts
        params:
          query: "홍길동"
  output_contains_any:
    - "찾을 수 없"
    - "없는"
    - "못 찾"
  constraints:
    max_turns: 5
    max_toolcalls: 2
  llm_grader:
    rubric: response-quality.md
    min_score: 0.7
metadata:
  category: contact
  description: "존재하지 않는 연락처 처리"
  tags: [contact, not-found]
```

`general-chat-001.yaml`:
```yaml
id: voice-agent/general-chat-001
input: "오늘 날씨 어때?"
dataset: default-contacts
expected:
  constraints:
    max_turns: 3
    max_toolcalls: 0
  llm_grader:
    rubric: response-quality.md
    min_score: 0.6
metadata:
  category: general
  description: "기능 범위 밖 요청 처리"
  tags: [general, out-of-scope]
```

나머지 파일도 동일한 패턴으로 작성.

- [ ] **Step 2: 테스트 리소스에서 기존 test-task-001.yaml 제거**

- [ ] **Step 3: 커밋**

```bash
git add evaluation/src/main/resources/tasks/
git commit -m "feat: Single-turn 평가 태스크 6개 작성"
```

### Task 24: Roleplay 시나리오 YAML 작성

**Files:**
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/confirm-call-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/disambiguate-contact-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/multi-step-001.yaml`
- Create: `evaluation/src/main/resources/tasks/voice-agent/roleplay/cancel-001.yaml`

- [ ] **Step 1: 4개 Roleplay YAML 파일 작성**

각 파일은 spec의 Roleplay 테스트 케이스 섹션을 따른다.

예시 — `confirm-call-001.yaml`:
```yaml
id: voice-agent/roleplay-confirm-call-001
type: roleplay
persona: |
  당신은 바쁜 직장인입니다.
  짧고 간결하게 말합니다.
  전화를 걸어달라고 요청한 뒤 확인 질문에 "응"으로 답합니다.
goal: |
  1. 에이전트가 연락처를 검색한다 (search_contacts 호출)
  2. 에이전트가 확인 질문을 한다 ("전화를 걸까요?" 또는 유사 표현)
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

`disambiguate-contact-001.yaml`:
```yaml
id: voice-agent/roleplay-disambiguate-001
type: roleplay
persona: |
  당신은 김철수에게 전화를 걸고 싶은 사용자입니다.
  동명이인이 있으면 "010-3456으로 끝나는 번호"라고 답합니다.
goal: |
  1. 에이전트가 연락처를 검색한다
  2. 동명이인이 발견되어 에이전트가 누구인지 물어본다
  3. 사용자가 특정 번호를 선택한다
  4. 에이전트가 해당 번호로 전화를 건다
initial_message: "김철수한테 전화해줘"
dataset: default-contacts
max_turns: 8
completion_criteria:
  rubric: conversation-quality.md
  min_score: 0.7
metadata:
  category: roleplay
  description: "동명이인 해소 흐름"
  tags: [contact, disambiguate, roleplay]
```

나머지도 동일 패턴.

- [ ] **Step 2: 커밋**

```bash
git add evaluation/src/main/resources/tasks/voice-agent/roleplay/
git commit -m "feat: Roleplay 시나리오 4개 작성"
```

### Task 25: E2E 통합 테스트

**Files:** (기존 파일 수정 없음, CLI로 실행)

- [ ] **Step 1: Single-turn 전체 실행 확인**

Run: `GEMINI_API_KEY=<key> ./gradlew :evaluation:run --args="run --no-cache"`
Expected: 6개 태스크 실행, 결과 콘솔 출력

- [ ] **Step 2: 단일 태스크 실행 확인**

Run: `GEMINI_API_KEY=<key> ./gradlew :evaluation:run --args="run --task voice-agent/call-basic-001 --no-cache"`
Expected: 1개 태스크 실행, PASSED 또는 결과 확인 가능

- [ ] **Step 3: Roleplay 실행 확인**

Run: `GEMINI_API_KEY=<key> ./gradlew :evaluation:run --args="roleplay --scenario voice-agent/roleplay-confirm-call-001"`
Expected: 멀티턴 대화 실행, 결과 출력

- [ ] **Step 4: 캐시 동작 확인**

Run: `./gradlew :evaluation:run --args="cache-status"`
Expected: passed된 태스크가 캐시에 기록됨

- [ ] **Step 5: 최종 커밋**

```bash
git add -A
git commit -m "feat: evaluation 시스템 완성 + E2E 테스트 확인"
```
