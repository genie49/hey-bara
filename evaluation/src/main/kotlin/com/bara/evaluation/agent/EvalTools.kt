package com.bara.evaluation.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore
import kotlinx.serialization.Serializable

// ── Args ─────────────────────────────────────────────

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

// ── Factory functions ────────────────────────────────

fun createSearchContactsTool(
    resolver: MockContactResolver,
    stateStore: MockStateStore,
): SimpleTool<SearchContactsArgs> = object : SimpleTool<SearchContactsArgs>(
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
): SimpleTool<MakeCallArgs> = object : SimpleTool<MakeCallArgs>(
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
): SimpleTool<SendSmsArgs> = object : SimpleTool<SendSmsArgs>(
    argsSerializer = SendSmsArgs.serializer(),
    name = "send_sms",
    description = "문자 메시지를 보낸다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다.",
) {
    override suspend fun execute(args: SendSmsArgs): String {
        stateStore.recordSms(args.contact, args.phoneNumber, args.message)
        return "${args.contact}(${args.phoneNumber})한테 '${args.message}'라고 문자를 보냅니다."
    }
}
