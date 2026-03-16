# Hey Bara - 사전 설정 가이드

## 1. Picovoice (웨이크워드)

1. https://console.picovoice.ai 가입
2. Porcupine → Create Keyword
3. "헤이 바라" 입력, Language: Korean
4. `.ppn` 파일 다운로드
5. Access Key 복사

## 2. Google Cloud Console (Calendar, Tasks)

1. https://console.cloud.google.com 프로젝트 생성
2. API 라이브러리에서 활성화:
   - Google Calendar API
   - Google Tasks API
3. OAuth 2.0 클라이언트 ID 생성 (Android 타입)
   - 패키지명: `com.bara.heybara` (예시)
   - SHA-1 지문 등록
4. OAuth 동의 화면 설정
   - 테스트 사용자에 본인 계정 추가
   - Scope: `calendar`, `tasks`

## 3. Google AI Studio (Gemini API)

1. https://aistudio.google.com 접속
2. API 키 생성
3. 앱 설정에 저장

## 4. 모델 다운로드

### Sherpa-ONNX Zipformer Korean

```bash
# HuggingFace에서 다운로드
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/encoder-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/decoder-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/joiner-epoch-99-avg-1.onnx
wget https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main/tokens.txt
```

### Supertonic 2

```bash
# HuggingFace에서 다운로드
# https://huggingface.co/Supertone/supertonic-2
```

### Gemma 3n E2B (온디바이스 모드 사용 시)

```bash
# Google AI Edge에서 다운로드
# https://ai.google.dev/gemma/docs/integrations/mobile
```

## 5. Android Studio 프로젝트 설정

### build.gradle (app)

```gradle
dependencies {
    // 웨이크워드
    implementation 'ai.picovoice:porcupine-android:3.x.x'

    // STT
    implementation 'com.k2fsa.sherpa:sherpa-onnx:1.10.x'

    // AI Agent
    implementation 'ai.koog:koog-agents:x.x.x'

    // NLP (온디바이스)
    implementation 'com.google.mediapipe:tasks-genai:latest'

    // Google API (Calendar, Tasks)
    implementation 'com.google.android.gms:play-services-auth:latest'
    implementation 'com.google.api-client:google-api-client-android:latest'
    implementation 'com.google.apis:google-api-services-calendar:latest'
    implementation 'com.google.apis:google-api-services-tasks:latest'

    // HTTP (Gemini API)
    implementation 'com.squareup.okhttp3:okhttp:4.x.x'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:latest'
}
```

### AndroidManifest.xml 권한

```xml
<!-- 마이크 -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>

<!-- 전화 -->
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>

<!-- 문자 -->
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.SEND_SMS"/>

<!-- 통화 기록 -->
<uses-permission android:name="android.permission.READ_CALL_LOG"/>

<!-- 인터넷 -->
<uses-permission android:name="android.permission.INTERNET"/>
```

## 6. 디바이스 설정 (Galaxy S25 Edge)

1. **알림 접근 허용**: 설정 → 알림 → 알림 접근 허용 → Hey Bara 활성화
2. **접근성 서비스 허용**: 설정 → 접근성 → 설치된 앱 → Hey Bara 활성화
3. **배터리 최적화 제외**: 설정 → 배터리 → Hey Bara → 제한 없음
4. **마이크 권한**: 앱 최초 실행 시 허용
