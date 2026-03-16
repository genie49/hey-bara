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
