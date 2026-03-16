package com.bara.heybara.data.history

import com.bara.heybara.domain.history.Conversation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class RoomConversationRepositoryTest {

    private lateinit var mockDao: ConversationDao
    private lateinit var repo: RoomConversationRepository

    @Before
    fun setup() {
        mockDao = mock()
        repo = RoomConversationRepository(mockDao)
    }

    @Test
    fun `save converts Conversation to Entity and inserts`() = runTest {
        val conversation = Conversation(
            topic = "엄마한테 전화 걸기",
            category = "call",
            inputMode = "voice",
            timestamp = 1000L,
            transcript = "사용자: 엄마한테 전화해\n바라: 네, 전화를 걸게요"
        )

        repo.save(conversation)

        verify(mockDao).insert(argThat<ConversationEntity> {
            topic == "엄마한테 전화 걸기" &&
            category == "call" &&
            inputMode == "voice" &&
            timestamp == 1000L &&
            transcript == "사용자: 엄마한테 전화해\n바라: 네, 전화를 걸게요"
        })
    }

    @Test
    fun `getAll converts Entities to Conversations`() = runTest {
        whenever(mockDao.getAll()).thenReturn(
            listOf(
                ConversationEntity(1, "전화 걸기", "call", "voice", 1000L, "transcript1"),
                ConversationEntity(2, "문자 보내기", "sms", "text", 2000L, "transcript2")
            )
        )

        val result = repo.getAll()

        assertEquals(2, result.size)
        assertEquals("전화 걸기", result[0].topic)
        assertEquals("call", result[0].category)
        assertEquals(1L, result[0].id)
        assertEquals("문자 보내기", result[1].topic)
        assertEquals("sms", result[1].category)
    }

    @Test
    fun `getAll returns empty list when no data`() = runTest {
        whenever(mockDao.getAll()).thenReturn(emptyList())
        val result = repo.getAll()
        assertTrue(result.isEmpty())
    }
}
