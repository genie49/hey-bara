package com.bara.evaluation.mocks

import com.bara.evaluation.datasets.Contact

/** Mock 연락처 검색기 — 이름 포함 여부로 검색 */
class MockContactResolver(private val contacts: List<Contact>) {
    suspend fun searchContacts(query: String): List<Contact> {
        return contacts.filter { it.name.contains(query) }
    }
}
