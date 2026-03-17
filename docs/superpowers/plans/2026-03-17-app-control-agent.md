# App Control Agent Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AccessibilityService + Gemini API로 등록된 앱을 자율 제어하는 범용 에이전트를 구현한다.

**Architecture:** BaraAccessibilityService가 UI 트리 캡처 + 액션 실행을 담당하고, AppControlAgent가 별도 Gemini 세션으로 UI 탐색 루프(최대 15스텝)를 실행한다. 메인 KoogAgentEngine의 control_app Tool이 AppControlAgent에 위임한다.

**Tech Stack:** Android AccessibilityService, Koog AIAgent (Gemini API), kotlinx.serialization

**Spec:** `docs/superpowers/specs/2026-03-17-app-control-agent-design.md`

---

## File Structure

### 신규 파일

| 파일 | 역할 |
|------|------|
| `data/accessibility/BaraAccessibilityService.kt` | AccessibilityService + UI 트리 캡처 + 액션 실행 |
| `data/accessibility/AppControlAgent.kt` | 별도 Gemini 세션 UI 탐색 루프 |
| `res/xml/accessibility_service_config.xml` | AccessibilityService 설정 |

### 수정 파일

| 파일 | 변경 |
|------|------|
| `data/agent/KoogAgentEngine.kt` | ControlAppTool 추가 |
| `data/settings/SecurePreferences.kt` | 등록 앱 목록 저장/조회 |
| `ui/SettingsActivity.kt` | 앱 제어 섹션 + 앱 선택 다이얼로그 |
| `AndroidManifest.xml` | AccessibilityService 등록 |

---

## Chunk 1: AccessibilityService + UI 트리

### Task 1: AccessibilityService 설정 파일

**Files:**
- Create: `app/src/main/res/xml/accessibility_service_config.xml`

- [ ] **Step 1: 설정 XML 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews"
    android:notificationTimeout="100" />
```

- [ ] **Step 2: 커밋**

```bash
git add app/src/main/res/xml/accessibility_service_config.xml
git commit -m "chore: AccessibilityService 설정 XML 추가"
```

---

### Task 2: BaraAccessibilityService 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/accessibility/BaraAccessibilityService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: BaraAccessibilityService 구현**

싱글톤 companion으로 외부 접근. 주요 메서드:
- `captureUiTree(): String?` — 현재 화면 노드를 텍스트 직렬화, 노드 목록을 내부 보관
- `performClick(nodeIndex: Int): Boolean`
- `performLongClick(nodeIndex: Int): Boolean`
- `performType(nodeIndex: Int, text: String): Boolean`
- `performScroll(direction: String): Boolean`
- `pressBack(): Boolean`
- `isEnabled(context: Context): Boolean`

UI 트리 직렬화 규칙:
- 순차 인덱스 부여 `[1]`, `[2]`, ...
- 노드 타입 + 라벨 + 속성 (clickable, editable, scrollable, selected, checked)
- 빈 라벨이고 상호작용 불가능한 노드는 제외
- 현재 포그라운드 앱 패키지명 표시

- [ ] **Step 2: AndroidManifest에 서비스 등록**

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

- [ ] **Step 3: 빌드 확인**

Run: `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew assembleDebug`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/accessibility/BaraAccessibilityService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: BaraAccessibilityService 구현 (UI 트리 캡처 + 액션 실행)"
```

---

## Chunk 2: AppControlAgent + Tool

### Task 3: AppControlAgent 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/accessibility/AppControlAgent.kt`

- [ ] **Step 1: AppControlAgent 구현**

```kotlin
class AppControlAgent(private val apiKey: String) {
    suspend fun execute(context: Context, packageName: String, goal: String): String
}
```

내부 로직:
1. Intent로 앱 실행
2. 500ms 대기
3. 루프 (최대 15스텝):
   a. `BaraAccessibilityService.captureUiTree()`
   b. Koog AIAgent에 시스템 프롬프트 + "목표: {goal}\n\n현재 UI:\n{uiTree}" 전송
   c. 응답 파싱 (CLICK/LONG_CLICK/TYPE/SCROLL/BACK/DONE/FAIL)
   d. DONE → 성공, FAIL → 실패
   e. 액션 실행
   f. 500ms 대기
4. 15스텝 초과 → 실패

시스템 프롬프트: 스펙 참조.
응답 파싱: 정규식으로 액션 추출.

AppControlAgent는 Koog AIAgent를 내부에서 생성하여 별도 세션으로 실행.
단, UI 탐색은 Tool이 아닌 단순 LLM 호출 (requestLLM → 텍스트 응답 → 파싱).
가장 간단한 구현: `AIAgent`의 `run()` 대신 직접 `simpleGoogleAIExecutor`로 단일 프롬프트 호출을 루프.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/accessibility/AppControlAgent.kt
git commit -m "feat: AppControlAgent 구현 (Gemini UI 탐색 루프)"
```

---

### Task 4: KoogAgentEngine에 ControlAppTool 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: ControlAppTool 추가**

```kotlin
object ControlAppTool : SimpleTool<ControlAppTool.Args>(...) {
    var appContext: Context? = null
    var apiKey: String? = null

    name = "control_app"
    description = "등록된 앱을 실행하고 목표를 달성한다."

    data class Args(
        val packageName: String,  // 앱 패키지명
        val goal: String          // 달성할 목표
    )

    execute:
      1. 등록된 앱인지 SecurePreferences로 확인
      2. AccessibilityService 활성 확인
      3. AppControlAgent(apiKey).execute(context, packageName, goal)
}
```

- [ ] **Step 2: buildTools()에 등록**

- [ ] **Step 3: setContext()에서 ControlAppTool에도 context + apiKey 주입**

- [ ] **Step 4: 시스템 프롬프트에 가이드 추가**

```
앱을 직접 조작해야 하는 요청이 오면 control_app을 사용해. 등록된 앱만 조작할 수 있다.
```

- [ ] **Step 5: 빌드 확인**

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: KoogAgentEngine에 ControlAppTool 추가"
```

---

## Chunk 3: 설정 UI + 연결

### Task 5: SecurePreferences에 등록 앱 관리

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt`

- [ ] **Step 1: 등록 앱 저장/조회/삭제 메서드 추가**

```kotlin
fun getRegisteredApps(): Set<String> {
    return prefs.getStringSet(KEY_REGISTERED_APPS, emptySet()) ?: emptySet()
}

fun addRegisteredApp(packageName: String) {
    val apps = getRegisteredApps().toMutableSet()
    apps.add(packageName)
    prefs.edit().putStringSet(KEY_REGISTERED_APPS, apps).apply()
}

fun removeRegisteredApp(packageName: String) {
    val apps = getRegisteredApps().toMutableSet()
    apps.remove(packageName)
    prefs.edit().putStringSet(KEY_REGISTERED_APPS, apps).apply()
}
```

companion에 `private const val KEY_REGISTERED_APPS = "registered_apps"` 추가.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/data/settings/SecurePreferences.kt
git commit -m "feat: SecurePreferences에 등록 앱 관리 추가"
```

---

### Task 6: 설정 화면에 앱 제어 섹션 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt`

- [ ] **Step 1: 앱 제어 섹션 추가**

알림 접근 섹션 뒤에:
- "앱 제어" SettingsSection
- 등록된 앱 목록 (앱 이름 + 패키지명 + X 삭제 버튼)
- "앱 추가" 버튼 → 설치된 앱 리스트 다이얼로그 표시
- 접근성 서비스 권한 상태 + 설정 이동 버튼

앱 리스트 다이얼로그:
- `PackageManager.getInstalledApplications()`로 설치된 앱 조회
- 시스템 앱 제외 (`FLAG_SYSTEM` 필터)
- 앱 이름 + 아이콘 표시
- 클릭 → SecurePreferences에 추가 → 다이얼로그 닫기

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/bara/heybara/ui/SettingsActivity.kt
git commit -m "feat: 설정 화면에 앱 제어 섹션 + 앱 선택 다이얼로그"
```

---

### Task 7: 최종 빌드 + 테스트

- [ ] **Step 1: clean 빌드 확인**

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew clean assembleDebug
```

- [ ] **Step 2: 유닛 테스트 실행**

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :app:testDebugUnitTest
```

- [ ] **Step 3: 실기기 테스트 시나리오**

1. 설정 → 앱 제어 → 앱 추가 → 스포티파이 선택
2. 설정 → 접근성 서비스 → Hey Bara 허용
3. "스포티파이에서 음악 틀어줘"
4. 미등록 앱 → 에러 메시지
5. 접근성 서비스 미활성 → 안내 메시지

- [ ] **Step 4: 최종 커밋**

```bash
git add -A
git commit -m "feat: 범용 앱 제어 에이전트 통합 완료"
```
