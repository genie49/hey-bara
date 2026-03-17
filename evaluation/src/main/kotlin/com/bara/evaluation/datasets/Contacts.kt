package com.bara.evaluation.datasets

import com.bara.evaluation.mocks.EventRecord
import com.bara.evaluation.mocks.TaskRecord

/** 연락처 데이터 */
data class Contact(
    val name: String,
    val phoneNumber: String,
)

/** 기본 테스트 연락처 목록 (중복 이름 포함) */
val DEFAULT_CONTACTS = listOf(
    Contact("엄마", "010-1234-5678"),
    Contact("아빠", "010-2345-6789"),
    Contact("김철수", "010-3456-7890"),
    Contact("김영희", "010-4567-8901"),
    Contact("이민수", "010-5678-9012"),
    Contact("박지영", "010-6789-0123"),
    Contact("김철수", "010-7890-1234"),
)

/** 기본 테스트 캘린더 일정 (고정 시각 2026-03-17 14:00 기준) */
val DEFAULT_EVENTS = listOf(
    EventRecord("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00"),
    EventRecord("evt-002", "점심 약속", "2026-03-17T12:30:00+09:00", "2026-03-17T13:30:00+09:00"),
    EventRecord("evt-003", "치과", "2026-03-18T15:00:00+09:00", "2026-03-18T16:00:00+09:00"),
    EventRecord("evt-004", "프로젝트 마감", "2026-03-20T09:00:00+09:00", "2026-03-20T18:00:00+09:00"),
    EventRecord("evt-005", "주간 회의", "2026-03-24T14:00:00+09:00", "2026-03-24T15:00:00+09:00"),
)

/** 기본 테스트 할일 (고정 시각 2026-03-17 14:00 기준) */
val DEFAULT_TASKS = listOf(
    TaskRecord("task-001", "장보기", "needsAction", "2026-03-18"),
    TaskRecord("task-002", "세탁소 옷 찾기", "needsAction", "2026-03-17"),
    TaskRecord("task-003", "보고서 작성", "needsAction"),
    TaskRecord("task-004", "운동", "completed", "2026-03-16"),
)

/** 데이터셋 이름 → 연락처 목록 매핑 */
val DATASETS: Map<String, List<Contact>> = mapOf(
    "default-contacts" to DEFAULT_CONTACTS,
)
