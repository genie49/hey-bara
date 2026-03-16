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
