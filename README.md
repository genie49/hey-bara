# Hey Bara 🦫

**"헤이 바라"** — 음성으로 호출하는 온디바이스 AI 비서

---

## 한 줄 요약

웨이크워드로 깨우고, 말로 명령하면, AI가 알아서 실행해주는 Android 음성 비서.

---

## 할 수 있는 것들

| 기능 | 예시 |
|------|------|
| **전화** | "헤이 바라, 엄마한테 전화해" |
| **문자** | "헤이 바라, 철수한테 지금 간다고 문자 보내줘" |
| **카카오톡** | "헤이 바라, 영희한테 밥 먹자고 카톡 보내줘" |
| **일정 관리** | "헤이 바라, 내일 오후 3시에 치과 일정 추가해줘" |
| **할일 관리** | "헤이 바라, 장보기 할일 추가해줘" |
| **알림 조회** | "헤이 바라, 알림 뭐 왔어?" |

---

## 동작 흐름

```
"헤이 바라" (웨이크워드)
  → 음성 인식 (STT)
    → AI가 명령 파싱 (NLP)
      → 기능 실행
        → 음성으로 결과 안내 (TTS)
```

---

## AI 엔진 선택

사용자가 설정에서 두 가지 모드 중 선택 가능:

| 모드 | 엔진 | 특징 |
|------|------|------|
| **온디바이스** | Gemma 3n E2B | 오프라인 동작, 무료, 프라이버시 |
| **클라우드** | Gemini API | 더 똑똑함, 인터넷 필요, 무료 티어 |

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| 언어 | Kotlin |
| 웨이크워드 | Porcupine (Picovoice) |
| 음성 인식 (STT) | Sherpa-ONNX Zipformer Korean |
| AI Agent | Koog (JetBrains) |
| NLP (온디바이스) | Gemma 3n E2B |
| NLP (클라우드) | Gemini API |
| 음성 합성 (TTS) | Supertonic 2 |
| 카톡 자동 전송 | Android Accessibility Service |
| 알림 읽기 | Android Notification Listener |
| 일정/할일 | Google Calendar API (OAuth 2.0) |

---

## 타겟 디바이스

**Galaxy S25 Edge** (Snapdragon 8 Elite, 12GB RAM)

- 온디바이스 모드: ~6.3GB 사용
- 클라우드 모드: ~4.3GB 사용

---

## 배포

개인용 APK 사이드로딩 (Google Play 미등록)

SMS/전화/접근성 권한 정책상 스토어 등록 불가.

---

## 빌드 준비

### Sherpa-ONNX AAR

Sherpa-ONNX는 Maven에 배포되지 않아 AAR을 직접 다운로드해야 합니다:

```bash
curl -L -o app/libs/sherpa-onnx-1.12.29.aar \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-1.12.29.aar"
```

---

## 문서

- [architecture.md](docs/architecture.md) — 전체 아키텍처 및 기능 상세
- [models.md](docs/models.md) — 사용 모델 스펙 및 벤치마크
- [setup.md](docs/setup.md) — 사전 설정 및 환경 구성 가이드
