package com.bara.heybara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.bara.heybara.domain.session.SessionState
import com.bara.heybara.service.VoiceAssistantService
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.ui.ChatMessage
import com.bara.heybara.ui.MainViewModel
import com.bara.heybara.ui.SettingsActivity
import com.bara.heybara.ui.theme.BaraColors
import com.bara.heybara.ui.theme.HeyBaraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 권한 요청 결과 처리
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionsAndStart()
        setContent {
            HeyBaraTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 돌아왔을 때 API Key 상태 갱신
        val hasKey = SecurePreferences(this).getGeminiApiKey() != null
        viewModel.updateApiKeyStatus(hasKey)
        if (hasKey) startVoiceService()
    }

    private fun requestPermissionsAndStart() {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        // Android 13+에서는 알림 권한도 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startVoiceService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startVoiceService() {
        // API Key가 있을 때만 서비스 시작
        val securePrefs = SecurePreferences(this)
        if (securePrefs.getGeminiApiKey() != null) {
            startService(Intent(this, VoiceAssistantService::class.java))
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, overrideHasApiKey: Boolean? = null) {
    val state by viewModel.sessionState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val context = LocalContext.current
    // Preview에서는 overrideHasApiKey 사용, 실제로는 ViewModel 상태
    val apiKeyAvailable = overrideHasApiKey ?: hasApiKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaraColors.Background)
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Hey Bara",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BaraColors.TextPrimary
                )
                StatusBadge(state)
            }
            Row {
                IconButton(onClick = { /* TODO: Phase 3에서 HistoryActivity 연결 */ }) {
                    Icon(Icons.Filled.History, contentDescription = "히스토리", tint = BaraColors.TextSecondary)
                }
                IconButton(onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }) {
                    Icon(Icons.Filled.Settings, contentDescription = "설정", tint = BaraColors.TextSecondary)
                }
            }
        }

        // 채팅 영역
        if (!apiKeyAvailable) {
            // API Key 미설정 안내
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("API Key가 설정되지 않았습니다", color = BaraColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Text("설정으로 이동")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
            }
        }

        // 입력 바
        var inputText by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("메시지를 입력하세요...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim(), context)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(BaraColors.Coral, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = BaraColors.Background)
            }
        }
    }
}

@Composable
fun StatusBadge(state: SessionState) {
    val (text, color, bgColor) = when (state) {
        SessionState.IDLE -> Triple("IDLE", BaraColors.Green, BaraColors.GreenBadgeBg)
        SessionState.LISTENING -> Triple("듣는 중", BaraColors.Coral, BaraColors.CoralBadgeBg)
        SessionState.PROCESSING -> Triple("처리 중", BaraColors.Indigo, BaraColors.IndigoBadgeBg)
        SessionState.CONFIRMING -> Triple("확인 대기", BaraColors.Indigo, BaraColors.IndigoBadgeBg)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun MainScreenPreview() {
    val viewModel = MainViewModel().apply {
        addMessage(ChatMessage("엄마한테 전화해", isUser = true, timestamp = "오후 2:30"))
        addMessage(ChatMessage("엄마한테 전화해라고 하셨나요?", isUser = false, timestamp = "오후 2:30"))
    }
    HeyBaraTheme {
        MainScreen(viewModel, overrideHasApiKey = true)
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // AI 메시지: 카피바라 아바타
        if (!message.isUser) {
            Image(
                painter = painterResource(R.drawable.bara_avatar),
                contentDescription = "바라",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
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
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    message.text,
                    color = if (message.isUser) BaraColors.Background else BaraColors.TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    message.timestamp,
                    color = if (message.isUser) BaraColors.Background.copy(alpha = 0.8f) else BaraColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
