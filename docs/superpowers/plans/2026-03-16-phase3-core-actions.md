# Phase 3: Core Actions — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 연락처 검색 + 전화 걸기 + SMS 보내기를 실제 연결하고, 대화 히스토리를 Room DB에 저장하여 HistoryActivity에서 조회한다.

**Architecture:** ActionExecutor 인터페이스를 domain에, CallExecutor/SmsExecutor/DeviceContactResolver 구현체를 data에 배치. KoogAgentEngine에 SearchContactsTool, SendSmsTool 추가. Room DB로 대화 히스토리 저장, LLM이 대화 주제/카테고리 자동 요약.

**Tech Stack:** Room (SQLite), ContentResolver (연락처), android.telephony.SmsManager, Intent.ACTION_CALL, Koog Tools, Jetpack Compose.

---

## File Structure

```
app/
├── build.gradle.kts                                           (수정: Room 의존성 추가)
├── src/main/
│   ├── AndroidManifest.xml                                    (수정: 권한 + HistoryActivity 등록)
│   └── java/com/bara/heybara/
│       ├── domain/
│       │   ├── action/
│       │   │   ├── ActionExecutor.kt                          (생성: 인터페이스)
│       │   │   └── ContactResolver.kt                         (생성: 인터페이스 + Contact 모델)
│       │   ├── agent/
│       │   │   └── AgentResponse.kt                           (수정: AgentAction 확장)
│       │   └── history/
│       │       ├── Conversation.kt                            (생성: 대화 모델)
│       │       └── ConversationRepository.kt                  (생성: 인터페이스)
│       ├── data/
│       │   ├── action/
│       │   │   ├── ActionExecutorImpl.kt                      (생성: Call/Sms 분기)
│       │   │   ├── CallExecutor.kt                            (생성: Intent.ACTION_CALL)
│       │   │   ├── SmsExecutor.kt                             (생성: SmsManager)
│       │   │   └── DeviceContactResolver.kt                   (생성: ContentResolver 검색)
│       │   ├── agent/
│       │   │   └── KoogAgentEngine.kt                         (수정: Tool 추가 + ActionExecutor 연결)
│       │   └── history/
│       │       ├── ConversationEntity.kt                      (생성: Room @Entity)
│       │       ├── ConversationDao.kt                         (생성: Room @Dao)
│       │       ├── AppDatabase.kt                             (생성: Room Database)
│       │       └── RoomConversationRepository.kt              (생성: Repository 구현)
│       ├── service/
│       │   └── VoiceAssistantService.kt                       (수정: ActionExecutor 연결)
│       ├── ui/
│       │   ├── HistoryActivity.kt                             (생성: 대화 히스토리 화면)
│       │   └── MainViewModel.kt                               (수정: 히스토리 저장 연결)
│       └── MainActivity.kt                                    (수정: 권한 추가 + 히스토리 연결)
```

---

## Chunk 1: Domain 레이어 + 의존성

### Task 1: 의존성 추가 (Room)

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: libs.versions.toml에 Room 버전 추가**

```toml
# [versions] 섹션에 추가
room = "2.6.1"
ksp = "2.2.10-1.0.31"

# [libraries] 섹션에 추가
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# [plugins] 섹션에 추가
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: app/build.gradle.kts에 Room + KSP 추가**

```kotlin
// plugins 블록에 추가
alias(libs.plugins.ksp)

// dependencies 블록에 추가
// Room
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

- [ ] **Step 3: Gradle Sync 확인**

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: Room + KSP 의존성 추가"
```

---

### Task 2: Domain 인터페이스 생성

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/domain/agent/AgentResponse.kt`
- Create: `app/src/main/java/com/bara/heybara/domain/action/ActionExecutor.kt`
- Create: `app/src/main/java/com/bara/heybara/domain/action/ContactResolver.kt`
- Create: `app/src/main/java/com/bara/heybara/domain/history/Conversation.kt`
- Create: `app/src/main/java/com/bara/heybara/domain/history/ConversationRepository.kt`

- [ ] **Step 1: AgentAction 확장 (Call에 phoneNumber 추가 + SendSms)**

```kotlin
// domain/agent/AgentResponse.kt
package com.bara.heybara.domain.agent

data class AgentResponse(
    val text: String,
    val action: AgentAction?,
    val requiresConfirmation: Boolean
)

sealed class AgentAction {
    data class Call(val contact: String, val phoneNumber: String?) : AgentAction()
    data class SendSms(val contact: String, val phoneNumber: String?, val message: String) : AgentAction()
}
```

- [ ] **Step 2: ActionExecutor 인터페이스 생성**

```kotlin
// domain/action/ActionExecutor.kt
package com.bara.heybara.domain.action

import com.bara.heybara.domain.agent.AgentAction

interface ActionExecutor {
    suspend fun execute(action: AgentAction): Boolean
}
```

- [ ] **Step 3: ContactResolver 인터페이스 + Contact 모델 생성**

```kotlin
// domain/action/ContactResolver.kt
package com.bara.heybara.domain.action

data class Contact(
    val name: String,
    val phoneNumber: String
)

interface ContactResolver {
    suspend fun searchContacts(query: String): List<Contact>
}
```

- [ ] **Step 4: Conversation 모델 생성**

```kotlin
// domain/history/Conversation.kt
package com.bara.heybara.domain.history

data class Conversation(
    val id: Long = 0,
    val topic: String,
    val category: String,       // "call" | "sms" | "chat"
    val inputMode: String,      // "voice" | "text"
    val timestamp: Long,
    val transcript: String      // 대화 전문 JSON
)
```

- [ ] **Step 5: ConversationRepository 인터페이스 생성**

```kotlin
// domain/history/ConversationRepository.kt
package com.bara.heybara.domain.history

interface ConversationRepository {
    suspend fun save(conversation: Conversation)
    suspend fun getAll(): List<Conversation>
}
```

- [ ] **Step 6: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/bara/heybara/domain/
git commit -m "feat: Phase 3 domain 인터페이스 (ActionExecutor, ContactResolver, ConversationRepository)"
```

---

## Chunk 2: Data 레이어 (Action + History)

### Task 3: ContactResolver + CallExecutor + SmsExecutor 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/action/DeviceContactResolver.kt`
- Create: `app/src/main/java/com/bara/heybara/data/action/CallExecutor.kt`
- Create: `app/src/main/java/com/bara/heybara/data/action/SmsExecutor.kt`
- Create: `app/src/main/java/com/bara/heybara/data/action/ActionExecutorImpl.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AndroidManifest에 권한 추가**

```xml
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
```

- [ ] **Step 2: DeviceContactResolver 구현**

```kotlin
// data/action/DeviceContactResolver.kt
package com.bara.heybara.data.action

import android.content.Context
import android.provider.ContactsContract
import com.bara.heybara.domain.action.Contact
import com.bara.heybara.domain.action.ContactResolver

class DeviceContactResolver(private val context: Context) : ContactResolver {

    override suspend fun searchContacts(query: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val number = cursor.getString(numberIdx)
                if (name != null && number != null) {
                    contacts.add(Contact(name, number))
                }
            }
        }
        return contacts.distinctBy { it.phoneNumber }
    }
}
```

- [ ] **Step 3: CallExecutor 구현**

```kotlin
// data/action/CallExecutor.kt
package com.bara.heybara.data.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class CallExecutor(private val context: Context) {

    fun call(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("CallExecutor", "전화 걸기: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("CallExecutor", "전화 걸기 실패", e)
            false
        }
    }
}
```

- [ ] **Step 4: SmsExecutor 구현**

```kotlin
// data/action/SmsExecutor.kt
package com.bara.heybara.data.action

import android.telephony.SmsManager
import android.util.Log

class SmsExecutor {

    fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("SmsExecutor", "SMS 전송: $phoneNumber, $message")
            true
        } catch (e: Exception) {
            Log.e("SmsExecutor", "SMS 전송 실패", e)
            false
        }
    }
}
```

- [ ] **Step 5: ActionExecutorImpl 구현**

```kotlin
// data/action/ActionExecutorImpl.kt
package com.bara.heybara.data.action

import com.bara.heybara.domain.action.ActionExecutor
import com.bara.heybara.domain.agent.AgentAction

class ActionExecutorImpl(
    private val callExecutor: CallExecutor,
    private val smsExecutor: SmsExecutor
) : ActionExecutor {

    override suspend fun execute(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.Call -> {
                val number = action.phoneNumber ?: return false
                callExecutor.call(number)
            }
            is AgentAction.SendSms -> {
                val number = action.phoneNumber ?: return false
                smsExecutor.sendSms(number, action.message)
            }
        }
    }
}
```

- [ ] **Step 6: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/bara/heybara/data/action/
git commit -m "feat: ActionExecutor + ContactResolver + CallExecutor + SmsExecutor 구현"
```

---

### Task 4: Room DB (대화 히스토리)

**Files:**
- Create: `app/src/main/java/com/bara/heybara/data/history/ConversationEntity.kt`
- Create: `app/src/main/java/com/bara/heybara/data/history/ConversationDao.kt`
- Create: `app/src/main/java/com/bara/heybara/data/history/AppDatabase.kt`
- Create: `app/src/main/java/com/bara/heybara/data/history/RoomConversationRepository.kt`

- [ ] **Step 1: ConversationEntity**

```kotlin
// data/history/ConversationEntity.kt
package com.bara.heybara.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val category: String,
    val inputMode: String,
    val timestamp: Long,
    val transcript: String
)
```

- [ ] **Step 2: ConversationDao**

```kotlin
// data/history/ConversationDao.kt
package com.bara.heybara.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    suspend fun getAll(): List<ConversationEntity>
}
```

- [ ] **Step 3: AppDatabase**

```kotlin
// data/history/AppDatabase.kt
package com.bara.heybara.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hey_bara_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 4: RoomConversationRepository**

```kotlin
// data/history/RoomConversationRepository.kt
package com.bara.heybara.data.history

import com.bara.heybara.domain.history.Conversation
import com.bara.heybara.domain.history.ConversationRepository

class RoomConversationRepository(
    private val dao: ConversationDao
) : ConversationRepository {

    override suspend fun save(conversation: Conversation) {
        dao.insert(
            ConversationEntity(
                topic = conversation.topic,
                category = conversation.category,
                inputMode = conversation.inputMode,
                timestamp = conversation.timestamp,
                transcript = conversation.transcript
            )
        )
    }

    override suspend fun getAll(): List<Conversation> {
        return dao.getAll().map {
            Conversation(
                id = it.id,
                topic = it.topic,
                category = it.category,
                inputMode = it.inputMode,
                timestamp = it.timestamp,
                transcript = it.transcript
            )
        }
    }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `⌘F9`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/bara/heybara/data/history/
git commit -m "feat: Room DB (ConversationEntity, Dao, AppDatabase, Repository)"
```

---

## Chunk 3: KoogAgentEngine Tool 확장 + Service 연결

### Task 5: KoogAgentEngine에 SearchContactsTool + SendSmsTool 추가

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt`

- [ ] **Step 1: KoogAgentEngine 수정**

KoogAgentEngine 생성자에 ContactResolver 추가. SearchContactsTool, MakeCallTool(수정), SendSmsTool 정의. Tool 결과에서 AgentAction을 올바르게 파싱.

```kotlin
// KoogAgentEngine 생성자 변경
class KoogAgentEngine(
    private val apiKey: String,
    private val contactResolver: ContactResolver? = null
) : AgentEngine {
```

Tool 추가:
```kotlin
// SearchContactsTool: contactResolver.searchContacts(query) 호출
// 결과를 JSON 문자열로 LLM에 반환

// MakeCallTool: param을 phoneNumber(String)로 변경
// SendSmsTool: params는 phoneNumber(String), message(String)
```

AgentResponse 파싱 개선: 결과 텍스트에서 Tool 호출 여부와 파라미터를 추출하여 적절한 AgentAction 생성.

- [ ] **Step 2: 빌드 확인**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/bara/heybara/data/agent/KoogAgentEngine.kt
git commit -m "feat: KoogAgentEngine에 SearchContacts + SendSms Tool 추가"
```

---

### Task 6: VoiceAssistantService + MainViewModel에 ActionExecutor 연결

**Files:**
- Modify: `app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt`
- Modify: `app/src/main/java/com/bara/heybara/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/bara/heybara/MainActivity.kt`

- [ ] **Step 1: VoiceAssistantService에 ActionExecutor 연결**

```kotlin
// VoiceAssistantService에서:
// - ActionExecutorImpl 초기화 (CallExecutor + SmsExecutor)
// - DeviceContactResolver 초기화
// - KoogAgentEngine에 contactResolver 전달
// - onActionExecute 콜백에서 ActionExecutor.execute() 호출
```

- [ ] **Step 2: MainViewModel에 ActionExecutor + 히스토리 저장 연결**

```kotlin
// MainViewModel에서:
// - ActionExecutorImpl 초기화
// - sendMessage 결과에서 액션 확인 시 execute 호출
// - 대화 종료 시 LLM 요약 → Room DB 저장
```

- [ ] **Step 3: MainActivity 권한 추가**

```kotlin
// requestPermissionsAndStart()에 CALL_PHONE, SEND_SMS, READ_CONTACTS 추가
```

- [ ] **Step 4: 빌드 확인**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bara/heybara/service/VoiceAssistantService.kt
git add app/src/main/java/com/bara/heybara/ui/MainViewModel.kt
git add app/src/main/java/com/bara/heybara/MainActivity.kt
git commit -m "feat: ActionExecutor + 히스토리 저장 Service/ViewModel 연결"
```

---

## Chunk 4: HistoryActivity + E2E 테스트

### Task 7: HistoryActivity 구현

**Files:**
- Create: `app/src/main/java/com/bara/heybara/ui/HistoryActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/bara/heybara/MainActivity.kt`

- [ ] **Step 1: HistoryActivity 구현 (Compose, Pencil 디자인 기반)**

```kotlin
// ui/HistoryActivity.kt
// - 날짜별 그룹핑 (오늘, 어제, 이전)
// - 카테고리 아이콘 + 색상 (call=coral, sms=teal, chat=gray)
// - 시간 + 입력 모드 (음성/텍스트) 표시
// - Room DB에서 ConversationRepository.getAll() 조회
```

- [ ] **Step 2: AndroidManifest에 HistoryActivity 등록**

```xml
<activity
    android:name=".ui.HistoryActivity"
    android:exported="false" />
```

- [ ] **Step 3: MainActivity 히스토리 아이콘에 HistoryActivity 연결**

```kotlin
// 헤더의 History 아이콘 onClick에서:
context.startActivity(Intent(context, HistoryActivity::class.java))
```

- [ ] **Step 4: 빌드 확인**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/bara/heybara/ui/HistoryActivity.kt
git add app/src/main/AndroidManifest.xml
git add app/src/main/java/com/bara/heybara/MainActivity.kt
git commit -m "feat: HistoryActivity (날짜별 그룹핑, 카테고리 아이콘)"
```

---

### Task 8: 에뮬레이터 통합 테스트

- [ ] **Step 1: 에뮬레이터에서 앱 실행**

확인 사항:
1. 텍스트 채팅: "엄마한테 전화해" → 연락처 검색 → 확인 응답
2. 텍스트 채팅: "철수한테 밥 먹자고 문자 보내줘" → SMS 확인
3. 대화 종료 후 히스토리 아이콘 탭 → HistoryActivity에 기록 표시

- [ ] **Step 2: 수정사항 있으면 커밋**

```bash
git add -A
git commit -m "fix: Phase 3 에뮬레이터 통합 테스트 수정사항"
```

---

### Task 9: 최종 정리

- [ ] **Step 1: 모든 유닛 테스트 통과 확인**

- [ ] **Step 2: 최종 커밋**

```bash
git add -A
git commit -m "milestone: Phase 3 Core Actions 완료"
```

Phase 3 결과물:
- 연락처 검색 (ContentResolver + LLM 매칭)
- 전화 걸기 (Intent.ACTION_CALL)
- SMS 보내기 (SmsManager)
- KoogAgentEngine: SearchContactsTool + SendSmsTool
- Room DB 대화 히스토리 + LLM 요약
- HistoryActivity (Pencil 디자인 기반)
