# 카카오톡 메시지 보내기 — Design Spec

## 개요

NotificationListenerService의 RemoteInput을 활용하여 카카오톡 메시지를 보내는 기능을 추가한다.

**범위:**
- BaraNotificationListener에서 카카오톡 알림의 답장 Action 저장
- send_kakao Tool 1개 추가 (확인 모달 포함)

**범위 외:**
- 새 대화 시작 (알림 없는 상대)
- 카카오톡 메시지 읽기 (list_notifications로 충분)
- AccessibilityService UI 자동화

---

## BaraNotificationListener 확장

기존 알림 조회 기능에 카카오톡 답장 Action 저장 로직 추가:

```kotlin
companion object {
    // 채팅방 이름 → 답장 Action
    val replyActions = mutableMapOf<String, Notification.Action>()

    fun getAvailableRooms(): List<String>
}

override fun onNotificationPosted(sbn: StatusBarNotification) {
    // 기존 알림 조회 로직 유지
    if (sbn.packageName == "com.kakao.talk") {
        val roomName = sbn.notification.extras.getString("android.title") ?: return
        val action = sbn.notification.actions?.firstOrNull {
            it.remoteInputs?.isNotEmpty() == true
        }
        // WearableExtender에서도 시도
        if (action == null) {
            val wearableActions = Notification.WearableExtender(sbn.notification).actions
            // ...
        }
        if (action != null) replyActions[roomName] = action
    }
}
```

---

## Tool

### send_kakao
- **description**: "카카오톡으로 메시지를 보낸다. 최근 카톡 알림이 온 상대에게만 보낼 수 있다."
- **params**: `roomName` (String, 채팅방/상대 이름), `message` (String, 보낼 메시지)
- **확인 모달**: ActionConfirmation (ActionType.KAKAO, 10초 카운트다운)
- **반환**: 전송 결과 또는 에러 메시지

### 채팅방 매칭 로직
1. 정확히 일치하는 방 이름 우선
2. 없으면 부분 매칭 (contains)
3. 못 찾으면 에러

---

## ActionType 확장

`ActionType`에 `KAKAO` 추가. 확인 모달에서 카카오톡 아이콘 표시.

---

## 시스템 프롬프트

기존 프롬프트에 추가:

```
카카오톡 메시지를 보내라는 요청이 오면 send_kakao를 사용해.
최근 카톡 알림이 온 상대에게만 보낼 수 있다.
```

---

## 에러 처리

| 상황 | Tool 반환값 |
|------|-----------|
| 알림 권한 미허용 | "알림 접근 권한이 필요합니다. 설정에서 허용해 주세요" |
| 채팅방 못 찾음 | "'{name}' 채팅방을 찾을 수 없습니다. 최근 카톡 알림이 필요합니다" |
| 사용자 취소 | "사용자가 취소했습니다" |
| 전송 실패 | "카톡 전송에 실패했습니다: {에러}" |

---

## 수정 파일

| 파일 | 변경 |
|------|------|
| `data/notification/BaraNotificationListener.kt` | replyActions 저장, getAvailableRooms() |
| `data/agent/KoogAgentEngine.kt` | SendKakaoTool 추가, 시스템 프롬프트 |
| `domain/action/ActionConfirmation.kt` | ActionType.KAKAO 추가 |
| `MainActivity.kt` | 확인 모달에 KAKAO 아이콘 |
