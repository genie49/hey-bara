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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bara.heybara.domain.action.ActionConfirmation
import com.bara.heybara.domain.action.ActionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.bara.heybara.domain.session.SessionState
import com.bara.heybara.service.VoiceAssistantService
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.ui.ChatMessage
import com.bara.heybara.ui.MainViewModel
import com.bara.heybara.ui.HistoryActivity
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
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
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
    val focusManager = LocalFocusManager.current
    // Preview에서는 overrideHasApiKey 사용, 실제로는 ViewModel 상태
    val apiKeyAvailable = overrideHasApiKey ?: hasApiKey

    // 액션 확인 모달
    val confirmationRequest by ActionConfirmation.pendingRequest.collectAsState()
    confirmationRequest?.let { request ->
        ActionConfirmationDialog(
            description = request.description,
            type = request.type,
            onConfirm = { ActionConfirmation.confirm() },
            onDeny = { ActionConfirmation.deny() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaraColors.Background)
            .imePadding()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
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
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(Icons.Filled.Add, contentDescription = "새 채팅", tint = BaraColors.TextSecondary)
                }
                IconButton(onClick = {
                    context.startActivity(Intent(context, HistoryActivity::class.java))
                }) {
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
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // 메시지 추가 시 자동 스크롤
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
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
                placeholder = { Text("메시지를 입력하세요...", color = BaraColors.TextTertiary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BaraColors.CardSurface,
                    unfocusedContainerColor = BaraColors.CardSurface,
                    focusedTextColor = BaraColors.TextPrimary,
                    unfocusedTextColor = BaraColors.TextPrimary,
                    cursorColor = BaraColors.Coral,
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
fun ActionConfirmationDialog(
    description: String,
    type: ActionType,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(10) }

    // 10초 카운트다운 → 자동 실행
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        onConfirm()
    }

    Dialog(
        onDismissRequest = { onDeny() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BaraColors.Background,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 아이콘
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (type == ActionType.CALL) BaraColors.GreenBadgeBg else BaraColors.IndigoBadgeBg,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (type == ActionType.CALL) Icons.Filled.Call else Icons.Filled.Sms,
                        contentDescription = null,
                        tint = if (type == ActionType.CALL) BaraColors.Green else BaraColors.Indigo,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 설명
                Text(
                    description,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = BaraColors.TextPrimary
                )

                // 카운트다운
                Text(
                    "${remainingSeconds}초 후 자동 실행",
                    fontSize = 13.sp,
                    color = BaraColors.TextTertiary
                )

                // 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDeny,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral)
                    ) {
                        Text("실행", color = BaraColors.Background)
                    }
                }
            }
        }
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
