package com.bara.evaluation.graders

import org.junit.Assert.*
import org.junit.Test

class CompareUtilsTest {
    @Test fun `wildcard matches any non-null value`() {
        assertTrue(matchExpect("*", "anything"))
        assertFalse(matchExpect("*", null))
    }
    @Test fun `regex pattern matches`() {
        assertTrue(matchExpect("regex:잘.*자", "잘 자"))
        assertFalse(matchExpect("regex:잘.*자", "못 잔다"))
    }
    @Test fun `numeric comparison`() {
        assertTrue(matchExpect(">=1", 2))
        assertTrue(matchExpect(">=1", 1))
        assertFalse(matchExpect(">=1", 0))
        assertTrue(matchExpect("<5", 3))
        assertFalse(matchExpect("<5", 5))
    }
    @Test fun `exact match`() {
        assertTrue(matchExpect("엄마", "엄마"))
        assertFalse(matchExpect("엄마", "아빠"))
    }
}
