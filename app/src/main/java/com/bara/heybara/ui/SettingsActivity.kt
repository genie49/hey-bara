package com.bara.heybara.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.ui.theme.BaraColors
import com.bara.heybara.ui.theme.HeyBaraTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val securePrefs = SecurePreferences(this)
        setContent {
            HeyBaraTheme {
                SettingsScreen(
                    securePrefs = securePrefs,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(securePrefs: SecurePreferences, onBack: () -> Unit) {
    val existingKey = securePrefs.getGeminiApiKey()
    var apiKeyInput by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var hasSavedKey by remember { mutableStateOf(existingKey != null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        // 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("\u2190 뒤로", color = BaraColors.TextPrimary)
            }
            Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BaraColors.TextPrimary)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Gemini API Key 섹션
        Text(
            "Gemini API Key",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = BaraColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (hasSavedKey) {
            val key = securePrefs.getGeminiApiKey() ?: ""
            val masked = if (key.length > 8) key.take(4) + "..." + key.takeLast(4) else "****"
            Text(
                "현재 저장된 키: $masked",
                color = BaraColors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            placeholder = { Text("API Key 입력") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (apiKeyInput.isNotBlank()) {
                    securePrefs.setGeminiApiKey(apiKeyInput.trim())
                    apiKeyInput = ""
                    hasSavedKey = true
                    savedMessage = "API Key가 저장되었습니다"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("저장")
        }

        savedMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = BaraColors.Green, fontSize = 14.sp)
        }

        if (hasSavedKey) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    securePrefs.clearGeminiApiKey()
                    hasSavedKey = false
                    savedMessage = "API Key가 삭제되었습니다"
                }
            ) {
                Text("API Key 삭제", color = BaraColors.Coral)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun SettingsScreenPreview() {
    HeyBaraTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = {}) {
                    Text("\u2190 뒤로", color = BaraColors.TextPrimary)
                }
                Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BaraColors.TextPrimary)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Gemini API Key", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = BaraColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("현재 저장된 키: AIza...7x9Q", color = BaraColors.TextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("API Key 입력") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("저장")
            }
        }
    }
}
