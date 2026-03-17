# Supertonic 2 TTS 포팅 — Design Spec

## 개요

Supertonic 2 (66M params) ONNX 모델을 Android에 포팅하여 한국어 고품질 TTS를 구현한다.

**범위:**
- 4단계 추론 파이프라인 (Duration Predictor → Text Encoder → Vector Estimator → Vocoder)
- 텍스트 전처리 (NFKD 정규화, 유니코드 토큰화)
- 모델 다운로드 (~263MB, HuggingFace)
- 설정 화면에 TTS 모델 설치 + 듣기 버튼
- AndroidTtsEngine fallback

**범위 외:**
- 보이스 스타일 선택 (한국어 여성 1개 고정)
- 디노이징 스텝 설정 (2스텝 고정)
- 롱 텍스트 청킹 (120자 이상 분할) — Phase 2

---

## 추론 파이프라인

```
텍스트 입력
  → 전처리 (NFKD 정규화, <ko>text</ko> 래핑)
  → 유니코드 토큰화 (unicode_indexer.json → int64[B, T])
  → Duration Predictor → 예상 길이(초)
  → Text Encoder → 텍스트 임베딩 [B, ?, T]
  → Vector Estimator × 2스텝 → 디노이징된 latent [B, 144, L]
  → Vocoder → float32 PCM [B, T_audio]
  → 16-bit PCM (44100Hz, mono)
  → AudioTrack 재생
```

### 모델 I/O

#### Duration Predictor (1.5MB)
- Input: `text_ids[B,T]` int64, `style_dp[B,8,16]` float32, `text_mask[B,1,T]` float32
- Output: `duration[B]` float32 (초)

#### Text Encoder (27MB)
- Input: `text_ids[B,T]` int64, `style_ttl[B,50,256]` float32, `text_mask[B,1,T]` float32
- Output: `text_emb[B,?,T]` float32

#### Vector Estimator (132MB) — 2스텝 루프
- Input: `noisy_latent[B,144,L]`, `text_emb`, `style_ttl`, `latent_mask[B,1,L]`, `text_mask`, `current_step[B]`, `total_step[B]`
- Output: `denoised_latent[B,144,L]`
- L = ceil(wav_length / 3072)

#### Vocoder (101MB)
- Input: `latent[B,144,L]`
- Output: `wav_tts[B,T_audio]` float32

### 상수
- sample_rate = 44100
- chunk_size = 3072 (512 * 6)
- latent_dim = 144 (24 * 6)
- denoise_steps = 2
- speed = 1.05

---

## 텍스트 전처리

1. NFKD 유니코드 정규화
2. 이모지 제거
3. 특수문자 치환 (대시, 스마트 따옴표 등)
4. 공백 정리
5. 끝에 마침표 없으면 추가
6. `<ko>text</ko>` 래핑

### 유니코드 토큰화
- `unicode_indexer.json`: 4096개 int 배열 (Unicode codepoint → token ID)
- 각 문자: `token_id = indexer[codepoint]`, -1이면 무시
- 결과: `text_ids[1, T]` int64, `text_mask[1, 1, T]` float32 (1.0/0.0)

---

## 보이스 스타일

한국어 여성 보이스 1개 고정. HuggingFace에서 보이스 JSON 다운로드.

- `style_ttl[1, 50, 256]` float32
- `style_dp[1, 8, 16]` float32

---

## 모델 다운로드 (ModelInstaller 확장)

### TTS 상태/진행률 추가

```kotlin
val ttsState: StateFlow<InstallState>
val ttsProgress: StateFlow<Int>

suspend fun installTts(context: Context)
fun isTtsInstalled(context: Context): Boolean
```

### 다운로드 파일 (HuggingFace resolve/main)

| 파일 | 크기 |
|------|------|
| `onnx/duration_predictor.onnx` | 1.5MB |
| `onnx/text_encoder.onnx` | 27MB |
| `onnx/vector_estimator.onnx` | 132MB |
| `onnx/vocoder.onnx` | 101MB |
| `onnx/tts.json` | 8.7KB |
| `onnx/unicode_indexer.json` | 262KB |

보이스 스타일 JSON은 별도 URL에서 다운로드.

저장 경로: `filesDir/models/tts/`

---

## 파일 구조

| 파일 | 역할 |
|------|------|
| `data/tts/SupertonicTtsEngine.kt` | TtsEngine 구현, 파이프라인 오케스트레이션 |
| `data/tts/SupertonicInference.kt` | ONNX Runtime 4모델 로드 + 추론 |
| `data/tts/TextPreprocessor.kt` | 정규화 + 토큰화 |
| `data/model/ModelInstaller.kt` | TTS 다운로드 추가 |
| `ui/SettingsActivity.kt` | TTS 모델 섹션 + 듣기 버튼 |
| `service/VoiceAssistantService.kt` | TTS 엔진 자동 전환 |

---

## 설정 UI

음성 모델 섹션에 TTS 행 추가:

```
음성 합성 (Supertonic 2)
~263MB · 한국어 고품질 TTS
                          [🔊] [X]   (설치 후)
                          [설치]      (미설치)
```

- 🔊 듣기 버튼: "카피바라는 귀여워" 재생 (SupertonicTtsEngine으로)
- X 삭제 버튼: 모델 파일 삭제
- 설치 중: 진행률 바

---

## TTS 엔진 전환

```kotlin
// VoiceAssistantService / VoiceSession에서
val tts: TtsEngine = if (ModelInstaller.isTtsInstalled(context)) {
    SupertonicTtsEngine(ttsModelDir)
} else {
    AndroidTtsEngine(context)
}
```

---

## 의존성

```
com.microsoft.onnxruntime:onnxruntime-android:1.23.1
```

---

## 에러 처리

| 상황 | 동작 |
|------|------|
| TTS 모델 미설치 | AndroidTtsEngine fallback |
| ONNX 추론 실패 | AndroidTtsEngine fallback + 에러 로그 |
| 빈 텍스트 | onDone() 즉시 호출 |
| 모델 로딩 실패 | AndroidTtsEngine fallback |
