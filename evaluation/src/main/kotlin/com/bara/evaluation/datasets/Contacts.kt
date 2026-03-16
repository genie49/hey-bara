package com.bara.evaluation.datasets

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

/** 데이터셋 이름 → 연락처 목록 매핑 */
val DATASETS: Map<String, List<Contact>> = mapOf(
    "default-contacts" to DEFAULT_CONTACTS,
)
