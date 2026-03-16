# Hey Bara - 모델 상세 정보

## 모델 목록

| 역할 | 모델 | 파라미터 | 크기 | 출시 |
|------|------|---------|------|------|
| 웨이크워드 | Porcupine (Picovoice) | - | ~10MB | - |
| STT | Sherpa-ONNX Zipformer Korean | 79M | ~60MB | 2024.06 |
| NLP (온디바이스) | Gemma 3n E2B | 5B | ~2GB | 2025 |
| NLP (클라우드) | Gemini API | - | - | - |
| TTS | Supertonic 2 | 66M | ~60MB | 2026.01 |

---

## STT: Sherpa-ONNX Streaming Zipformer Korean

### 모델 정보

- **이름**: `sherpa-onnx-streaming-zipformer-korean-2024-06-16`
- **원본**: `icefall-asr-ksponspeech-pruned-transducer-stateless7-streaming-2024-06-12`
- **아키텍처**: Zipformer v1 + Pruned Transducer
- **파라미터**: 79,022,891 (79M)
- **학습 데이터**: KsponSpeech (969시간 한국어 자발적 대화)
- **양자화**: fp32 / int8

### 모델 파일

```
encoder.onnx    (~55MB)
decoder.onnx    (~5MB)
joiner.onnx     (~3MB)
tokens.txt      (토큰 사전)
```

### 성능 (CER, 낮을수록 좋음)

#### 320ms Chunk Size

| 디코딩 방법 | eval_clean | eval_other |
|------------|-----------|-----------|
| Greedy Search | 10.21% | 11.07% |
| Fast Beam Search | 10.21% | 11.04% |
| Modified Beam Search | 10.13% | 10.88% |

#### 640ms Chunk Size

| 디코딩 방법 | eval_clean | eval_other |
|------------|-----------|-----------|
| Greedy Search | 9.94% | 10.82% |
| Fast Beam Search | 10.01% | 10.81% |
| **Modified Beam Search** | **9.91%** | **10.72%** |

### Gradle

```gradle
implementation 'com.k2fsa.sherpa:sherpa-onnx:1.10.x'
```

### 대안 모델 (추후 테스트 고려)

| 모델 | CER (Fleurs) | 파라미터 | 비고 |
|------|-------------|---------|------|
| Moonshine Tiny KO | 8.9% | 27M | 더 가벼움, ONNX 변환 필요 |
| SenseVoice (ko) INT8 | - | - | 2025.09, 다국어 |

---

## NLP (온디바이스): Gemma 3n E2B

### 모델 정보

- **파라미터**: 5B (실제), Per-Layer Embedding으로 RAM 절감
- **RAM**: ~2GB
- **성능**: Gemma 3 4B급, 1.5x 빠른 응답
- **멀티모달**: 텍스트, 이미지, 비디오, 오디오
- **NPU 가속**: Snapdragon 8 Elite Hexagon NPU 지원
- **오프라인**: 완전 오프라인 동작

### S25 Edge NPU 성능 참고 (Snapdragon 8 Elite)

```
NPU: ~90 tokens/sec (일정 유지, 36-38°C)
CPU: ~37 tokens/sec → 3분 후 ~19 t/s (42°C, 스로틀링)
NPU 배터리 효율: CPU 대비 3.7x
```

### Gradle

```gradle
implementation 'com.google.mediapipe:tasks-genai:latest'
```

---

## NLP (클라우드): Gemini API

### 정보

- **무료 티어**: 있음
- **Function Calling**: 지원
- **한국어**: 우수
- **Context Window**: 대용량

### Google AI Studio에서 API 키 발급

1. https://aistudio.google.com 접속
2. API 키 생성
3. 앱에 설정

---

## TTS: Supertonic 2

### 모델 정보

- **개발사**: Supertone (한국)
- **파라미터**: 66M
- **크기**: ~60MB
- **아키텍처**: 2-step diffusion + ONNX 양자화
- **속도**: 167x 실시간 (M4 Pro), 모바일 3-5 step
- **지원 언어**: 한국어, 영어, 스페인어, 포르투갈어, 프랑스어
- **출시**: 2026.01

### 리소스

- GitHub: https://github.com/supertone-inc/supertonic
- HuggingFace: https://huggingface.co/Supertone/supertonic-2

---

## 웨이크워드: Porcupine

### 정보

- **개발사**: Picovoice
- **커스텀 워드**: "헤이 바라" (Picovoice 콘솔에서 생성)
- **동작**: 온디바이스, 인터넷 불필요
- **크기**: ~10MB
- **한국어**: 지원

### Gradle

```gradle
implementation 'ai.picovoice:porcupine-android:3.x.x'
```

### 커스텀 웨이크워드 생성

1. https://console.picovoice.ai 접속
2. Porcupine → Create Keyword
3. "헤이 바라" 입력, 한국어 선택
4. .ppn 파일 다운로드 → 앱 assets에 포함

---

## AI Agent: Koog

### 정보

- **개발사**: JetBrains
- **역할**: LangChain의 Kotlin 네이티브 버전
- **플랫폼**: JVM, Android, iOS, JS
- **GitHub**: https://github.com/JetBrains/koog

### 주요 기능

- Tool/Function Calling 등록 및 관리
- MCP (Model Context Protocol) 지원
- Agent 상태 관리 (retry, persistence)
- History compression (토큰 최적화)
- 멀티 모델 지원 (로컬/클라우드 스위칭)
