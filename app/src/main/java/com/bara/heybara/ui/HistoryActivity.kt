package com.bara.heybara.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.heybara.R
import com.bara.heybara.data.history.AppDatabase
import com.bara.heybara.data.history.RoomConversationRepository
import com.bara.heybara.domain.history.Conversation
import com.bara.heybara.ui.theme.BaraColors
import com.bara.heybara.ui.theme.HeyBaraTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = RoomConversationRepository(
            AppDatabase.getInstance(this).conversationDao()
        )
        setContent {
            HeyBaraTheme {
                var selectedConversation by remember { mutableStateOf<Conversation?>(null) }

                BackHandler(enabled = selectedConversation != null) {
                    selectedConversation = null
                }

                if (selectedConversation != null) {
                    HistoryDetailScreen(
                        conversation = selectedConversation!!,
                        onBack = { selectedConversation = null }
                    )
                } else {
                    HistoryListScreen(
                        repo = repo,
                        onBack = { finish() },
                        onSelect = { selectedConversation = it }
                    )
                }
            }
        }
    }
}

// 히스토리 목록 화면
@Composable
fun HistoryListScreen(
    repo: RoomConversationRepository? = null,
    onBack: () -> Unit = {},
    onSelect: (Conversation) -> Unit = {},
    previewData: List<Conversation>? = null
) {
    val conversations = remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (previewData != null) {
            conversations.value = previewData
        } else {
            scope.launch {
                conversations.value = repo?.getAll() ?: emptyList()
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("히스토리 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("모든 대화 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo?.deleteAll()
                        conversations.value = emptyList()
                    }
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = BaraColors.Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaraColors.Background)
            .statusBarsPadding()
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = BaraColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "대화 히스토리",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BaraColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (conversations.value.isNotEmpty()) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "전체 삭제",
                        tint = BaraColors.TextSecondary
                    )
                }
            }
        }

        if (conversations.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("대화 기록이 없습니다", color = BaraColors.TextTertiary)
            }
        } else {
            val grouped = groupByDate(conversations.value)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (dateLabel, items) ->
                    item {
                        Text(
                            dateLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BaraColors.TextTertiary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(items) { conversation ->
                        HistoryItem(conversation, onTap = { onSelect(conversation) })
                    }
                }
            }
        }
    }
}

// 히스토리 상세 화면 — 메인 채팅 UI와 동일한 Read-only 뷰
@Composable
fun HistoryDetailScreen(conversation: Conversation, onBack: () -> Unit) {
    val messages = parseTranscript(conversation.transcript)
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    val timeStr = timeFormat.format(Date(conversation.timestamp))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaraColors.Background)
            .statusBarsPadding()
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = BaraColors.TextPrimary
                )
            }
            Column {
                Text(
                    conversation.topic,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BaraColors.TextPrimary,
                    maxLines = 1
                )
                Text(
                    timeStr,
                    fontSize = 12.sp,
                    color = BaraColors.TextTertiary
                )
            }
        }

        // 채팅 버블 목록
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                HistoryChatBubble(msg)
            }
        }
    }
}

data class TranscriptMessage(val text: String, val isUser: Boolean)

// "사용자: ...\n바라: ..." 형식의 transcript를 파싱
fun parseTranscript(transcript: String): List<TranscriptMessage> {
    if (transcript.isBlank()) return emptyList()
    val messages = mutableListOf<TranscriptMessage>()
    val lines = transcript.split("\n")
    for (line in lines) {
        when {
            line.startsWith("사용자: ") -> {
                messages.add(TranscriptMessage(line.removePrefix("사용자: "), isUser = true))
            }
            line.startsWith("바라: ") -> {
                messages.add(TranscriptMessage(line.removePrefix("바라: "), isUser = false))
            }
        }
    }
    return messages
}

@Composable
fun HistoryChatBubble(message: TranscriptMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            Image(
                painter = painterResource(R.drawable.bara_avatar),
                contentDescription = "바라",
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            color = if (message.isUser) BaraColors.Coral else BaraColors.CardSurface,
            shape = if (message.isUser)
                RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            else
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        ) {
            Text(
                message.text,
                color = if (message.isUser) BaraColors.Background else BaraColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun HistoryItem(conversation: Conversation, onTap: () -> Unit = {}) {
    val (icon, iconColor) = getCategoryStyle(conversation.category)
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    val timeStr = timeFormat.format(Date(conversation.timestamp))
    val modeStr = if (conversation.inputMode == "voice") "음성" else "텍스트"

    Surface(
        color = BaraColors.CardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onTap
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation.topic,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = BaraColors.TextPrimary
                )
                Text(
                    "$timeStr · $modeStr",
                    fontSize = 12.sp,
                    color = BaraColors.TextTertiary
                )
            }
        }
    }
}

fun getCategoryStyle(category: String): Pair<ImageVector, Color> {
    return when (category) {
        "call" -> Icons.Filled.Call to BaraColors.Coral
        "sms" -> Icons.Filled.Sms to Color(0xFF14B8A6)
        else -> Icons.AutoMirrored.Filled.Chat to BaraColors.TextSecondary
    }
}

fun groupByDate(conversations: List<Conversation>): List<Pair<String, List<Conversation>>> {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = today - 86400000

    val groups = mutableMapOf<String, MutableList<Conversation>>()
    for (conv in conversations) {
        val label = when {
            conv.timestamp >= today -> "오늘"
            conv.timestamp >= yesterday -> "어제"
            else -> "이전"
        }
        groups.getOrPut(label) { mutableListOf() }.add(conv)
    }

    val order = listOf("오늘", "어제", "이전")
    return order.mapNotNull { label ->
        groups[label]?.let { label to it }
    }
}
