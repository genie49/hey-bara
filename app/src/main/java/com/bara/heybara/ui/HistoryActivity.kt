package com.bara.heybara.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                HistoryScreen(repo = repo, onBack = { finish() })
            }
        }
    }
}

@Composable
fun HistoryScreen(repo: RoomConversationRepository? = null, onBack: () -> Unit = {}, previewData: List<Conversation>? = null) {
    val conversations = remember { mutableStateOf<List<Conversation>>(emptyList()) }
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
            Text(
                "대화 히스토리",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BaraColors.TextPrimary
            )
        }

        if (conversations.value.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("대화 기록이 없습니다", color = BaraColors.TextTertiary)
            }
        } else {
            // 날짜별 그룹핑
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
                        HistoryItem(conversation)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(conversation: Conversation) {
    val (icon, iconColor) = getCategoryStyle(conversation.category)
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    val timeStr = timeFormat.format(Date(conversation.timestamp))
    val modeStr = if (conversation.inputMode == "voice") "음성" else "텍스트"

    Surface(
        color = BaraColors.CardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 카테고리 아이콘
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
        "sms" -> Icons.Filled.Sms to Color(0xFF14B8A6) // teal
        else -> Icons.Filled.Chat to BaraColors.TextSecondary
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

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun HistoryScreenPreview() {
    val now = System.currentTimeMillis()
    val sampleData = listOf(
        Conversation(1, "엄마한테 전화 걸기", "call", "음성", now - 3600000, ""),
        Conversation(2, "치과 일정 추가", "chat", "텍스트", now - 7200000, ""),
        Conversation(3, "철수한테 카톡 보내기", "sms", "음성", now - 90000000, ""),
        Conversation(4, "알림 확인", "chat", "음성", now - 100000000, ""),
    )
    HeyBaraTheme {
        HistoryScreen(previewData = sampleData)
    }
}
