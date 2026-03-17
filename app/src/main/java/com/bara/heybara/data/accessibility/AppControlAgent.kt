package com.bara.heybara.data.accessibility

import android.content.Context
import android.content.Intent
import android.util.Log
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class AppControlAgent(private val apiKey: String) {

    companion object {
        private const val TAG = "AppControlAgent"
        private const val MAX_ITERATIONS = 30  // Tool 호출 + 응답 왕복 포함
        private const val MODEL_ID = "gemini-3.1-flash-lite-preview"
        private const val ACTION_DELAY_MS = 800L

        private const val SYSTEM_PROMPT = """
너는 Android 앱을 조작하는 에이전트야.
사용자의 목표를 달성하기 위해 제공된 도구들을 사용해 앱을 조작해.

도구를 호출하면 액션이 실행되고, 실행 결과와 함께 현재 화면의 UI 상태가 반환돼.
UI 상태를 분석하고 다음 액션을 결정해.

규칙:
- 한 번에 하나의 도구만 호출해
- 목표를 달성하면 결과를 텍스트로 응답해 (도구 호출 없이)
- 달성 불가능하면 이유를 텍스트로 응답해
- 현재 UI에 보이는 요소의 id만 사용해
"""
    }

    private val executor = simpleGoogleAIExecutor(apiKey)
    private val model = LLModel(
        provider = LLMProvider.Google,
        id = MODEL_ID,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
    )

    // ── Action Tools (각 Tool이 액션 실행 + UI 상태 반환) ──

    object ClickTool : SimpleTool<ClickTool.Args>(
        argsSerializer = Args.serializer(),
        name = "click",
        description = "화면의 UI 요소를 클릭한다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        data class Args(@property:LLMDescription("클릭할 요소의 id 번호") val id: Int)
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.performClick(args.id)
            delay(ACTION_DELAY_MS)
            return actionResult("click(${args.id})", result)
        }
    }

    object LongClickTool : SimpleTool<LongClickTool.Args>(
        argsSerializer = Args.serializer(),
        name = "long_click",
        description = "화면의 UI 요소를 길게 누른다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        data class Args(@property:LLMDescription("길게 누를 요소의 id 번호") val id: Int)
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.performLongClick(args.id)
            delay(ACTION_DELAY_MS)
            return actionResult("long_click(${args.id})", result)
        }
    }

    object TypeTextTool : SimpleTool<TypeTextTool.Args>(
        argsSerializer = Args.serializer(),
        name = "type_text",
        description = "UI 요소에 텍스트를 입력한다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("텍스트를 입력할 요소의 id 번호") val id: Int,
            @property:LLMDescription("입력할 텍스트") val text: String
        )
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.performType(args.id, args.text)
            delay(ACTION_DELAY_MS)
            return actionResult("type_text(${args.id}, \"${args.text}\")", result)
        }
    }

    object ScrollDownTool : SimpleTool<ScrollDownTool.Args>(
        argsSerializer = Args.serializer(),
        name = "scroll_down",
        description = "화면을 아래로 스크롤한다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        class Args
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.performScroll("down")
            delay(ACTION_DELAY_MS)
            return actionResult("scroll_down", result)
        }
    }

    object ScrollUpTool : SimpleTool<ScrollUpTool.Args>(
        argsSerializer = Args.serializer(),
        name = "scroll_up",
        description = "화면을 위로 스크롤한다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        class Args
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.performScroll("up")
            delay(ACTION_DELAY_MS)
            return actionResult("scroll_up", result)
        }
    }

    object PressEnterTool : SimpleTool<PressEnterTool.Args>(
        argsSerializer = Args.serializer(),
        name = "press_enter",
        description = "엔터키를 누른다 (검색 실행 등). 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        class Args
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.pressEnter()
            delay(ACTION_DELAY_MS)
            return actionResult("press_enter", result)
        }
    }

    object PressBackTool : SimpleTool<PressBackTool.Args>(
        argsSerializer = Args.serializer(),
        name = "press_back",
        description = "뒤로가기 버튼을 누른다. 실행 후 현재 화면 상태를 반환한다."
    ) {
        @Serializable
        class Args
        override suspend fun execute(args: Args): String {
            val result = BaraAccessibilityService.pressBack()
            delay(ACTION_DELAY_MS)
            return actionResult("press_back", result)
        }
    }

    object WaitAndGetScreenTool : SimpleTool<WaitAndGetScreenTool.Args>(
        argsSerializer = Args.serializer(),
        name = "wait_and_get_screen",
        description = "지정된 시간(밀리초) 동안 대기한 후 현재 화면 상태를 반환한다. 0이면 즉시 화면을 조회한다. 화면 로딩 대기나 현재 상태 확인에 사용한다."
    ) {
        @Serializable
        data class Args(@property:LLMDescription("대기 시간 (밀리초, 0이면 즉시 조회, 예: 1000 = 1초)") val ms: Long = 0)
        override suspend fun execute(args: Args): String {
            if (args.ms > 0) {
                val waitTime = args.ms.coerceAtMost(5000)
                Log.d(LOG_TAG, "Tool 실행: wait(${waitTime}ms)")
                delay(waitTime)
            } else {
                Log.d(LOG_TAG, "Tool 실행: get_screen")
            }
            val uiTree = BaraAccessibilityService.captureUiTree() ?: "UI 캡처 실패"
            Log.d(LOG_TAG, "UI 상태: ${uiTree.take(200)}...")
            return uiTree
        }
    }

    private fun buildToolRegistry() = ToolRegistry {
        tool(ClickTool)
        tool(LongClickTool)
        tool(TypeTextTool)
        tool(ScrollDownTool)
        tool(ScrollUpTool)
        tool(PressEnterTool)
        tool(PressBackTool)
        tool(WaitAndGetScreenTool)
    }

    suspend fun execute(context: Context, packageName: String, goal: String): String {
        Log.d(TAG, "앱 제어 시작: $packageName, 목표: $goal")

        // 앱 실행
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "'$packageName' 앱을 실행할 수 없습니다."
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(1500)

        // 초기 UI 상태 캡처
        val initialUi = BaraAccessibilityService.captureUiTree()
            ?: return "화면 정보를 가져올 수 없습니다. 접근성 서비스를 확인해 주세요."

        // 에이전트 생성 및 실행
        val agent = AIAgent(
            promptExecutor = executor,
            systemPrompt = SYSTEM_PROMPT.trimIndent(),
            llmModel = model,
            toolRegistry = buildToolRegistry(),
            maxIterations = MAX_ITERATIONS
        )

        val prompt = "목표: $goal\n\n현재 화면:\n$initialUi"

        return try {
            val result = agent.run(prompt)
            Log.d(TAG, "앱 제어 완료: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "앱 제어 실패", e)
            "앱 조작에 실패했습니다: ${e.message}"
        }
    }
}

private const val LOG_TAG = "AppControlAgent"

// 액션 실행 결과 + 현재 UI 상태를 합쳐서 반환
private fun actionResult(action: String, success: Boolean): String {
    val status = if (success) "성공" else "실패"
    Log.d(LOG_TAG, "Tool 실행: $action → $status")
    val uiTree = BaraAccessibilityService.captureUiTree() ?: "UI 캡처 실패"
    Log.d(LOG_TAG, "UI 상태: ${uiTree.take(200)}...")
    return "액션 '$action' 실행: $status\n\n현재 화면:\n$uiTree"
}
