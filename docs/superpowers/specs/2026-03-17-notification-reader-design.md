# Phase 5: 알림 조회 — Design Spec

## 개요

NotificationListenerService를 통해 현재 상태바 알림을 조회하는 기능을 추가한다.

**범위:**
- NotificationListenerService 구현 (현재 알림 목록 읽기)
- list_notifications Tool 1개 추가
- AndroidManifest 서비스 등록

**범위 외:**
- 알림 지우기/삭제
- 실시간 알림 감지
- 특정 앱 필터링 (LLM이 결과에서 자연스럽게 처리)

---

## BaraNotificationListener

`NotificationListenerService`를 상속한 서비스. 싱글톤 companion으로 외부에서 현재 알림에 접근 가능.

```kotlin
class BaraNotificationListener : NotificationListenerService() {
    companion object {
        private var instance: BaraNotificationListener? = null
        fun getActiveNotificationList(): List<NotificationInfo>?
        fun isEnabled(context: Context): Boolean
    }
}

data class NotificationInfo(
    val appName: String,
    val title: String,
    val content: String,
    val time: Long
)
```

- `getActiveNotificationList()`: `activeNotifications`에서 앱 이름, 제목, 내용, 시간 추출
- `isEnabled()`: `NotificationManagerCompat.getEnabledListenerPackages()`로 권한 확인

---

## Tool

### list_notifications
- **description**: "현재 알림 목록을 조회한다"
- **params**: 없음
- **반환**: 알림 목록 (앱 이름, 제목, 내용) 또는 "알림이 없습니다"
- **권한 미허용 시**: "알림 접근 권한이 필요합니다. 설정에서 Hey Bara의 알림 접근을 허용해 주세요"
- **확인 모달**: 없음 (읽기 전용)

---

## AndroidManifest

```xml
<service
    android:name=".data.notification.BaraNotificationListener"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

---

## 시스템 프롬프트

기존 프롬프트에 추가:

```
알림 관련 요청이 오면 list_notifications를 사용해.
```

---

## 에러 처리

| 상황 | Tool 반환값 |
|------|-----------|
| 권한 미허용 | "알림 접근 권한이 필요합니다. 설정에서 Hey Bara의 알림 접근을 허용해 주세요" |
| 알림 없음 | "알림이 없습니다" |
| 서비스 미연결 | "알림 서비스가 연결되지 않았습니다" |

---

## 파일 구조

| 파일 | 역할 |
|------|------|
| `data/notification/BaraNotificationListener.kt` | NotificationListenerService 구현 |
| `data/agent/KoogAgentEngine.kt` | ListNotificationsTool 추가 |
| `AndroidManifest.xml` | 서비스 등록 |

---

## 테스트 시나리오

1. "알림 뭐 왔어?" → list_notifications → 현재 알림 목록
2. "카톡 알림 있어?" → list_notifications → LLM이 카카오톡 필터링
3. 알림 없을 때 → "알림이 없습니다"
4. 권한 미허용 → 안내 메시지
