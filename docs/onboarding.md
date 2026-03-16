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
| 디스크 여유 | ~500MB (모델 파일 포함) |

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

## 4. STT 모델 다운로드

한국어 Zipformer 모델 (~300MB, git 미추적):

```bash
BASE="https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main"
DEST="app/src/main/assets/models/stt"
mkdir -p "$DEST"

curl -L -o "$DEST/encoder-epoch-99-avg-1.onnx" "$BASE/encoder-epoch-99-avg-1.onnx"
curl -L -o "$DEST/decoder-epoch-99-avg-1.onnx" "$BASE/decoder-epoch-99-avg-1.onnx"
curl -L -o "$DEST/joiner-epoch-99-avg-1.onnx" "$BASE/joiner-epoch-99-avg-1.onnx"
curl -L -o "$DEST/tokens.txt" "$BASE/tokens.txt"
```

다운로드 후 확인:

```bash
ls -lh app/src/main/assets/models/stt/
# encoder ~279MB, decoder ~11MB, joiner ~10MB, tokens ~59KB
```

---

## 5. Porcupine 웨이크워드 설정

### 5-1. Access Key 발급

1. https://console.picovoice.ai 가입/로그인
2. 대시보드에서 **Access Key** 복사
3. 무료 플랜: 월 3시간 음성 처리 (개인 사용 충분)

### 5-2. Access Key 등록

`local.properties` (프로젝트 루트, 이미 존재)에 추가:

```properties
PORCUPINE_ACCESS_KEY=여기에_키_붙여넣기
```

> `local.properties`는 `.gitignore`에 포함되어 git에 올라가지 않습니다.

### 5-3. 한국어 모델 파일 다운로드

Porcupine 기본 모델은 영어이므로, 한국어 모델(.pv)을 별도로 다운로드해야 합니다 (~1MB, git 미추적):

```bash
curl -L -o app/src/main/assets/models/wakeword/porcupine_params_ko.pv \
  "https://github.com/Picovoice/porcupine/raw/master/lib/common/porcupine_params_ko.pv"
```

### 5-4. 커스텀 웨이크워드 모델

`hey-bara.ppn` 파일은 이미 `app/src/main/assets/models/wakeword/`에 포함되어 있습니다 (git 추적).

직접 생성하려면:
1. Picovoice 콘솔 → **Porcupine** → **Custom Wake Word**
2. Phrase: `헤이 바라`, Language: `Korean`, Platform: `Android`
3. Train → `.ppn` 다운로드
4. `app/src/main/assets/models/wakeword/hey-bara.ppn`에 교체

---

## 6. 빌드 및 설치

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

## 7. 디바이스 권한 설정

앱 설치 후 다음 권한을 수동 허용해야 합니다:

| 권한 | 설정 경로 | 용도 |
|------|----------|------|
| 마이크 | 앱 실행 시 자동 요청 | 웨이크워드 + STT |
| 알림 | 앱 실행 시 자동 요청 | ForegroundService 알림 |
| 다른 앱 위에 표시 | 설정 → 앱 → Hey Bara → 다른 앱 위에 표시 | 오버레이 버블 |

---

## 8. 동작 확인

1. 앱 실행 → 상태바에 "대기 중 — 헤이 바라로 호출" 알림 확인
2. "헤이 바라" 발화 → 비프음 재생
3. 아무 말 → TTS 에코백 ("OO라고 하셨나요?")
4. 다른 앱으로 전환 → "헤이 바라" → 오버레이 버블 표시

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| 빌드 실패: sherpa-onnx not found | AAR 미다운로드 | Step 3 실행 |
| 빌드 실패: model files missing | STT 모델 미다운로드 | Step 4 실행 |
| 앱 크래시: PorcupineInvalidArgumentException | Access Key 미설정 또는 만료 | Step 5 확인 |
| 웨이크워드 무반응 | 마이크 권한 미허용 | 설정에서 권한 확인 |
| 오버레이 안 뜸 | "다른 앱 위에 표시" 미허용 | Step 7 확인 |

---

## 파일 구조 요약 (git 미추적 파일)

```
hey-bara/
├── local.properties              ← Access Key (git 미추적)
├── app/
│   ├── libs/
│   │   └── sherpa-onnx-1.12.29.aar   ← AAR (git 미추적)
│   └── src/main/assets/models/
│       ├── wakeword/
│       │   └── hey-bara.ppn          ← 웨이크워드 (git 추적)
│       └── stt/
│           ├── encoder-*.onnx        ← STT 모델 (git 미추적)
│           ├── decoder-*.onnx
│           ├── joiner-*.onnx
│           └── tokens.txt
```
