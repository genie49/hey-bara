# 범용 앱 제어 에이전트 — Design Spec

## 개요

AccessibilityService + Gemini API로 등록된 앱을 자율 제어하는 에이전트를 추가한다.
DroidBot-GPT 방식: UI 트리를 텍스트로 직렬화 → LLM이 액션 결정 → 실행 → 반복.

**범위:**
- BaraAccessibilityService (UI 트리 캡처 + 액션 실행)
- AppControlAgent (별도 Gemini 세션 UI 탐색 루프, 최대 15스텝)
- control_app Tool (메인 에이전트에서 위임)
- 설정에서 제어할 앱 등록 (설치된 앱 목록에서 선택)

**범위 외:**
- 스크린샷/비전 기반 UI 인식
- 실시간 진행 상황 표시
- Android AppFunctions 연동

---

## 전체 흐름

```
사용자: "스포티파이에서 내 플레이리스트 틀어줘"
  → 메인 KoogAgentEngine
    → control_app(packageName="com.spotify.music", goal="내 플레이리스트 재생")
      → 등록된 앱인지 확인
      → AppControlAgent (별도 Gemini 세션) 시작
        1. 스포티파이 실행 (Intent)
        2. UI 트리 캡처 (BaraAccessibilityService)
        3. Gemini에 전송: "목표: 내 플레이리스트 재생\n현재 UI:\n[1] Button '검색'..."
        4. Gemini 응답: "CLICK(5)"
        5. BaraAccessibilityService로 클릭 실행
        6. 2~5 반복 (최대 15스텝)
        7. Gemini "DONE" 응답 → 결과 반환
      → "스포티파이에서 플레이리스트를 재생했습니다"
  → 메인 에이전트가 사용자에게 응답
```

---

## BaraAccessibilityService

`AccessibilityService` 구현. 싱글톤 companion으로 외부 접근.

```kotlin
class BaraAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: BaraAccessibilityService? = null

        fun isEnabled(context: Context): Boolean
        fun captureUiTree(): String?
        fun performClick(nodeIndex: Int): Boolean
        fun performLongClick(nodeIndex: Int): Boolean
        fun performType(nodeIndex: Int, text: String): Boolean
        fun performScroll(direction: String): Boolean  // "up" | "down"
        fun pressBack(): Boolean
    }
}
```

### UI 트리 직렬화

`getRootInActiveWindow()`에서 노드 트리를 순회하여 텍스트로 변환:

```
현재 앱: Spotify (com.spotify.music)
[1] Button: '검색' (clickable)
[2] Button: '홈' (clickable, selected)
[3] Button: '내 라이브러리' (clickable)
[4] TextView: '최근 재생'
[5] Button: '내 플레이리스트 #1' (clickable)
[6] Button: '좋아하는 노래' (clickable)
[7] EditText: '' (editable, clickable)
```

규칙:
- 빈 라벨이고 clickable/editable도 아닌 노드는 제외 (노이즈 감소)
- 노드에 순차 인덱스 부여 (LLM이 참조할 ID)
- 캡처된 노드 목록을 내부에 보관 (액션 실행 시 인덱스로 접근)

### AndroidManifest

```xml
<service
    android:name=".data.accessibility.BaraAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### accessibility_service_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews"
    android:notificationTimeout="100" />
```

---

## AppControlAgent

별도 Gemini 세션으로 UI 탐색 루프를 실행하는 에이전트.

```kotlin
class AppControlAgent(private val apiKey: String) {
    suspend fun execute(context: Context, packageName: String, goal: String): String
}
```

### 시스템 프롬프트

```
너는 Android 앱을 조작하는 에이전트야.
사용자의 목표를 달성하기 위해 현재 화면의 UI 요소를 분석하고 적절한 액션을 선택해.

사용할 수 있는 액션:
- CLICK(id) — 요소 클릭
- LONG_CLICK(id) — 길게 누르기
- TYPE(id, "text") — 텍스트 입력
- SCROLL_DOWN — 아래로 스크롤
- SCROLL_UP — 위로 스크롤
- BACK — 뒤로가기
- DONE — 목표 달성 완료
- FAIL(이유) — 목표 달성 불가

규칙:
- 반드시 하나의 액션만 응답해
- 현재 UI에 보이는 요소만 사용해
- 목표를 달성하면 바로 DONE 응답해
- 달성 불가능하면 FAIL(이유) 응답해
```

### 루프 로직

```
1. Intent로 앱 실행
2. 500ms 대기 (UI 로드)
3. for step in 1..15:
   a. captureUiTree()
   b. Gemini에 전송: 시스템 프롬프트 + "목표: {goal}\n\n현재 UI:\n{uiTree}"
   c. 응답 파싱
   d. DONE → 성공 반환
   e. FAIL(reason) → 실패 반환
   f. 액션 실행 (click/type/scroll/back)
   g. 500ms 대기 (UI 갱신)
4. 15스텝 초과 → 실패 반환
```

### 액션 파싱

Gemini 응답에서 정규식으로 파싱:
- `CLICK\((\d+)\)` → performClick
- `LONG_CLICK\((\d+)\)` → performLongClick
- `TYPE\((\d+),\s*"(.+?)"\)` → performType
- `SCROLL_DOWN` → performScroll("down")
- `SCROLL_UP` → performScroll("up")
- `BACK` → pressBack
- `DONE` → 성공
- `FAIL\((.+)\)` → 실패 + 이유

---

## control_app Tool

메인 KoogAgentEngine에 추가되는 Tool.

```kotlin
object ControlAppTool : SimpleTool<Args>(...) {
    name = "control_app"
    description = "등록된 앱을 실행하고 목표를 달성한다. 스포티파이, 유튜브 등 등록된 앱을 조작할 때 사용한다."

    params:
      - packageName: String  // 앱 패키지명
      - goal: String         // 달성할 목표 (자연어)

    execute:
      1. 등록된 앱인지 확인
      2. AccessibilityService 활성 확인
      3. AppControlAgent.execute(context, packageName, goal)
      4. 결과 반환
}
```

시스템 프롬프트에 추가:
```
앱을 직접 조작해야 하는 요청이 오면 control_app을 사용해.
등록된 앱만 조작할 수 있다.
```

---

## 설정 UI

### 앱 제어 섹션

```
앱 제어
┌──────────────────────────────────┐
│  Spotify          com.spotify... X │
│  YouTube          com.google...  X │
│                                    │
│        [+ 앱 추가]                  │
└──────────────────────────────────┘

접근성 서비스
┌──────────────────────────────────┐
│  접근성 권한          [허용됨]      │
│  앱 조작 기능에 필요                │
└──────────────────────────────────┘
```

- "앱 추가" 버튼 → 설치된 앱 리스트 다이얼로그 (아이콘 + 앱 이름)
- 앱 선택 → 리스트에 추가 (SecurePreferences에 패키지명 저장)
- X 버튼 → 제거
- 접근성 서비스 미활성 → "설정" 버튼으로 시스템 설정 이동

### SecurePreferences

```kotlin
fun getRegisteredApps(): Set<String>
fun addRegisteredApp(packageName: String)
fun removeRegisteredApp(packageName: String)
```

---

## 에러 처리

| 상황 | 반환 |
|------|------|
| AccessibilityService 미활성 | "접근성 서비스 권한이 필요합니다. 설정에서 허용해 주세요" |
| 미등록 앱 | "'{앱이름}'은(는) 등록되지 않은 앱입니다. 설정에서 추가해 주세요" |
| 앱 미설치 | "'{앱이름}'이(가) 설치되어 있지 않습니다" |
| 15스텝 초과 | "앱 조작에 실패했습니다. 목표를 달성하지 못했습니다" |
| FAIL 응답 | "앱 조작에 실패했습니다: {이유}" |
| UI 트리 캡처 실패 | "화면 정보를 가져올 수 없습니다" |

### 확인 모달

없음. 등록된 앱만 제어 가능하므로 안전장치 충분.

---

## 파일 구조

| 파일 | 역할 |
|------|------|
| `data/accessibility/BaraAccessibilityService.kt` | AccessibilityService + UI 트리 캡처 + 액션 실행 |
| `data/accessibility/AppControlAgent.kt` | 별도 Gemini 세션 UI 탐색 루프 |
| `data/agent/KoogAgentEngine.kt` | ControlAppTool 추가 |
| `data/settings/SecurePreferences.kt` | 등록 앱 목록 저장 |
| `ui/SettingsActivity.kt` | 앱 제어 섹션 + 앱 선택 다이얼로그 |
| `AndroidManifest.xml` | AccessibilityService 등록 |
| `res/xml/accessibility_service_config.xml` | 서비스 설정 |

---

## 테스트 시나리오

1. 설정에서 스포티파이 앱 등록/삭제
2. "스포티파이에서 음악 틀어줘" → control_app → 앱 실행 → UI 탐색 → 재생
3. 미등록 앱 요청 → 에러 메시지
4. AccessibilityService 미활성 → 안내 메시지
5. 15스텝 초과 → 실패 메시지
