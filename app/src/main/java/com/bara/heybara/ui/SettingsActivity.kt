package com.bara.heybara.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import com.bara.heybara.data.auth.GoogleAuthManager
import com.bara.heybara.data.model.ModelInstaller
import com.bara.heybara.data.notification.BaraNotificationListener
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.ui.theme.BaraColors
import com.bara.heybara.ui.theme.HeyBaraTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    // Google 동의 화면 결과를 전달할 콜백
    private var onGoogleConsentResult: ((Boolean) -> Unit)? = null

    private val googleConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val success = if (result.resultCode == RESULT_OK) {
            val authResult = com.google.android.gms.auth.api.identity.Identity
                .getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)
            GoogleAuthManager.handleAuthResult(this, authResult)
        } else false
        onGoogleConsentResult?.invoke(success)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val securePrefs = SecurePreferences(this)
        setContent {
            HeyBaraTheme {
                SettingsScreen(
                    securePrefs = securePrefs,
                    onBack = { finish() },
                    onGoogleSignIn = { onResult ->
                        onGoogleConsentResult = onResult
                        kotlinx.coroutines.MainScope().launch {
                            when (val signInResult = GoogleAuthManager.signIn(this@SettingsActivity)) {
                                is GoogleAuthManager.SignInResult.Success -> onResult(true)
                                is GoogleAuthManager.SignInResult.NeedsConsent -> {
                                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest
                                        .Builder(signInResult.pendingIntent).build()
                                    googleConsentLauncher.launch(intentSenderRequest)
                                }
                                is GoogleAuthManager.SignInResult.Failed -> onResult(false)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    securePrefs: SecurePreferences,
    onBack: () -> Unit,
    onGoogleSignIn: ((Boolean) -> Unit) -> Unit = {}
) {
    val existingKey = securePrefs.getGeminiApiKey()
    var apiKeyInput by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var hasSavedKey by remember { mutableStateOf(existingKey != null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sttState by ModelInstaller.sttState.collectAsState()
    val sttProgress by ModelInstaller.sttProgress.collectAsState()
    val kwsState by ModelInstaller.kwsState.collectAsState()
    val kwsProgress by ModelInstaller.kwsProgress.collectAsState()

    LaunchedEffect(Unit) {
        ModelInstaller.checkInstalled(context)
        GoogleAuthManager.restore(context)
    }

    var googleEmail by remember { mutableStateOf(GoogleAuthManager.getAccountEmail()) }

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
                "설정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BaraColors.TextPrimary
            )
        }

        // 스크롤 가능한 콘텐츠
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Gemini API 키 섹션
            SettingsSection(label = "Gemini API 키") {
                if (hasSavedKey) {
                    // 키가 있으면: 마스킹 표시 + X 삭제 버튼
                    val key = securePrefs.getGeminiApiKey() ?: ""
                    val masked = if (key.length > 8) key.take(4) + "..." + key.takeLast(4) else "****"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDD11", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(masked, color = BaraColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                securePrefs.clearGeminiApiKey()
                                hasSavedKey = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "삭제", tint = BaraColors.TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    // 키가 없으면: 입력 필드 + 저장 버튼
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = { Text("API Key 입력", color = BaraColors.TextTertiary) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    securePrefs.setGeminiApiKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                    hasSavedKey = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral)
                        ) {
                            Text("저장")
                        }
                    }
                }
            }

            // Google 계정 섹션
            SettingsSection(label = "Google 계정 (캘린더/할일)") {
                if (googleEmail != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(googleEmail!!, color = BaraColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                GoogleAuthManager.signOut(context)
                                googleEmail = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "연결 해제", tint = BaraColors.TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onGoogleSignIn { success ->
                                if (success) googleEmail = GoogleAuthManager.getAccountEmail()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral)
                    ) {
                        Text("Google 계정 연결")
                    }
                }
            }

            // 음성 모델 섹션
            SettingsSection(label = "음성 모델") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // STT 모델
                    ModelInstallRow(
                        title = "음성 인식 (Korean STT)",
                        subtitle = "~300MB · 한국어 음성 인식",
                        state = sttState,
                        progress = sttProgress,
                        onInstall = { scope.launch { ModelInstaller.installStt(context) } }
                    )

                    HorizontalDivider(color = BaraColors.Background, thickness = 1.dp)

                    // KWS 모델
                    ModelInstallRow(
                        title = "웨이크워드 (Hey Bara)",
                        subtitle = "~5MB · 음성 호출 감지",
                        state = kwsState,
                        progress = kwsProgress,
                        onInstall = { scope.launch { ModelInstaller.installKws(context) } }
                    )
                }
            }

            // 알림 접근 권한 섹션
            var notificationEnabled by remember { mutableStateOf(false) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        notificationEnabled = BaraNotificationListener.isEnabled(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
            }
            SettingsSection(label = "알림 접근") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "알림 조회 권한",
                            color = BaraColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "\"알림 뭐 왔어?\" 기능에 필요",
                            color = BaraColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                    if (notificationEnabled) {
                        Text("허용됨", color = BaraColors.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("설정", fontSize = 13.sp)
                        }
                    }
                }
            }

            // AI 엔진 섹션 (Phase 3+)
            SettingsSection(label = "AI 엔진") {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsOptionRow(
                        title = "온디바이스 (Gemma 3n)",
                        subtitle = "오프라인, 무료, 프라이버시 보호",
                        enabled = false
                    )
                    HorizontalDivider(color = BaraColors.Background, thickness = 1.dp)
                    SettingsOptionRow(
                        title = "클라우드 (Gemini API)",
                        subtitle = "더 정확, 인터넷 필요, 무료 티어",
                        enabled = true
                    )
                }
            }

            // 웨이크워드 감도 섹션
            var sensitivity by remember { mutableFloatStateOf(securePrefs.getWakeWordSensitivity()) }
            val sensitivityLabel = when {
                sensitivity < 0.25f -> "낮음"
                sensitivity < 0.75f -> "보통"
                else -> "높음"
            }
            SettingsSection(label = "웨이크워드 감도") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("\"헤이 바라\" 감도", color = BaraColors.TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(sensitivityLabel, color = BaraColors.Coral, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            securePrefs.setWakeWordSensitivity(it)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = BaraColors.Coral,
                            activeTrackColor = BaraColors.Coral
                        )
                    )
                    Text(
                        "감도를 변경하면 다음 웨이크워드 감지부터 적용됩니다",
                        color = BaraColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// 모델 설치 행 컴포넌트
@Composable
fun ModelInstallRow(
    title: String,
    subtitle: String,
    state: ModelInstaller.InstallState,
    progress: Int,
    onInstall: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = BaraColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = BaraColors.TextTertiary, fontSize = 12.sp)
            }
            when (state) {
                ModelInstaller.InstallState.INSTALLED -> {
                    Text("설치 완료", color = BaraColors.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                ModelInstaller.InstallState.NOT_INSTALLED,
                ModelInstaller.InstallState.ERROR -> {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BaraColors.Coral),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(if (state == ModelInstaller.InstallState.ERROR) "재시도" else "설치", fontSize = 13.sp)
                    }
                }
                ModelInstaller.InstallState.DOWNLOADING -> {
                    Text("${progress}%", color = BaraColors.Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state == ModelInstaller.InstallState.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = BaraColors.Coral,
                trackColor = BaraColors.CardSurface,
            )
        }
        if (state == ModelInstaller.InstallState.ERROR) {
            Text("다운로드에 실패했습니다. 네트워크를 확인해 주세요.", color = BaraColors.Coral, fontSize = 12.sp)
        }
    }
}

// 섹션 카드 컴포넌트
@Composable
fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = BaraColors.TextTertiary,
            letterSpacing = 0.5.sp
        )
        Surface(
            color = BaraColors.CardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

// AI 엔진 옵션 행
@Composable
fun SettingsOptionRow(title: String, subtitle: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = BaraColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = BaraColors.TextTertiary, fontSize = 12.sp)
        }
        RadioButton(
            selected = enabled,
            onClick = { /* Phase 3+ */ },
            colors = RadioButtonDefaults.colors(selectedColor = BaraColors.Coral)
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun SettingsScreenPreview() {
    HeyBaraTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BaraColors.Background)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = BaraColors.TextPrimary)
                Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BaraColors.TextPrimary)
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // API 키
                SettingsSection(label = "Gemini API 키") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("AIza...7x9Q", color = BaraColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, contentDescription = "삭제", tint = BaraColors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // Google 계정
                SettingsSection(label = "Google 계정 (캘린더/할일)") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("user@gmail.com", color = BaraColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, contentDescription = "연결 해제", tint = BaraColors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // 음성 모델
                SettingsSection(label = "음성 모델") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("음성 인식 (Korean STT)", color = BaraColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("~300MB · 한국어 음성 인식", color = BaraColors.TextTertiary, fontSize = 12.sp)
                            }
                            Text("설치 완료", color = BaraColors.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(color = BaraColors.Background, thickness = 1.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("웨이크워드 (Hey Bara)", color = BaraColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("~5MB · 음성 호출 감지", color = BaraColors.TextTertiary, fontSize = 12.sp)
                            }
                            Text("설치 완료", color = BaraColors.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // AI 엔진
                SettingsSection(label = "AI 엔진") {
                    Column {
                        SettingsOptionRow("온디바이스 (Gemma 3n)", "오프라인, 무료, 프라이버시 보호", false)
                        HorizontalDivider(color = BaraColors.Background, thickness = 1.dp)
                        SettingsOptionRow("클라우드 (Gemini API)", "더 정확, 인터넷 필요, 무료 티어", true)
                    }
                }

                // 웨이크워드 감도
                SettingsSection(label = "웨이크워드 감도") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("\"헤이 바라\" 감도", color = BaraColors.TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("보통", color = BaraColors.Coral, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Slider(
                            value = 0.5f,
                            onValueChange = {},
                            colors = SliderDefaults.colors(thumbColor = BaraColors.Coral, activeTrackColor = BaraColors.Coral)
                        )
                    }
                }
            }
        }
    }
}
