# Hey Bara — 온보딩 가이드

프로젝트를 클론한 후 빌드/실행까지 필요한 모든 설정 과정.

---

## 1. 사전 요구사항

| 항목 | 버전/요구 |
|------|----------|
| Android Studio | 최신 (Ladybug 이상) |
| JDK | 11 이상 (Android Studio 내장) |
| Kotlin | 2.2+ (Gradle에서 자동 관리) |
| 디바이스 | Android 8.0+ (API 26), USB 디버깅 활성화 |

---

## 2. 프로젝트 클론

```bash
git clone https://github.com/genie49/hey-bara.git
cd hey-bara
```

---

## 3. Sherpa-ONNX AAR 다운로드

Sherpa-ONNX는 Maven에 배포되지 않아 AAR을 직접 다운로드해야 합니다:

```bash
mkdir -p app/libs
curl -L -o app/libs/sherpa-onnx-1.12.29.aar \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-1.12.29.aar"
```

> `app/libs/` 디렉토리에 38MB 파일이 생기면 성공. `.gitignore`에 `*.aar` 포함되어 git 미추적.

---

## 4. 빌드 및 설치

### Android Studio에서

1. Android Studio로 프로젝트 열기
2. Gradle Sync 실행 (자동 또는 File → Sync Project with Gradle Files)
3. Build → Make Project (`⌘F9`)
4. USB로 디바이스 연결 → Run (`⌘R`)

### CLI에서

```bash
./gradlew assembleDebug
./gradlew installDebug
```

---

## 5. 앱 초기 설정

앱 설치 후 **설정 화면**에서 다음을 완료해야 합니다:

### 5-1. Gemini API 키 등록

1. https://aistudio.google.com 에서 API Key 발급
2. 앱 설정 → Gemini API 키 → 키 입력 → 저장

### 5-2. 음성 모델 설치 (앱 내 다운로드)

앱 설정 화면에서 두 모델을 설치합니다 (Wi-Fi 권장):

| 모델 | 크기 | 용도 |
|------|------|------|
| 음성 인식 (Korean STT) | ~300MB | 한국어 음성 인식 |
| 웨이크워드 (Hey Bara) | ~5MB | 음성 호출 감지 |

각 모델의 "설치" 버튼을 눌러 다운로드합니다. 진행률 바로 상태 확인 가능.

---

## 6. 디바이스 권한 설정

앱 실행 시 자동으로 요청되는 권한:

| 권한 | 용도 |
|------|------|
| 마이크 | 웨이크워드 + STT |
| 알림 | ForegroundService 알림 |
| 전화 | 전화 걸기 |
| SMS | 문자 보내기 |
| 연락처 | 연락처 검색 |

수동 설정 필요:

| 권한 | 설정 경로 | 용도 |
|------|----------|------|
| 다른 앱 위에 표시 | 설정 → 앱 → Hey Bara → 다른 앱 위에 표시 | 오버레이 버블 |

---

## 7. 동작 확인

1. 앱 실행 → 상태바에 "대기 중 — 헤이 바라로 호출" 알림 확인
2. "Hey Bara" 발화 → 비프음 재생
3. 아무 말 → TTS 응답
4. 메인 화면에서 텍스트 입력으로도 명령 가능

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| 빌드 실패: sherpa-onnx not found | AAR 미다운로드 | Step 3 실행 |
| 메인 화면에 "음성 모델이 설치되지 않았습니다" | 모델 미다운로드 | Step 5-2 실행 |
| 메인 화면에 "API Key가 설정되지 않았습니다" | API Key 미설정 | Step 5-1 실행 |
| 웨이크워드 무반응 | 마이크 권한 미허용 | 설정에서 권한 확인 |
| 오버레이 안 뜸 | "다른 앱 위에 표시" 미허용 | Step 6 확인 |

---

## 파일 구조 요약 (git 미추적 파일)

```
hey-bara/
├── local.properties              ← SDK 경로 등 (git 미추적)
├── app/
│   └── libs/
│       └── sherpa-onnx-1.12.29.aar   ← AAR (git 미추적)
```

> STT/KWS 모델은 더 이상 assets에 번들하지 않습니다. 앱 설정에서 다운로드됩니다.
