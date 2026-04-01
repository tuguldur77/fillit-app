package com.fillit.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * 반복 규칙의 종류를 정의하는 타입.
 * Firestore에는 "NONE", "DAILY" 같은 문자열로 저장됩니다.
 */
enum class Recurrence(val displayName: String) {
    NONE("반복 안 함"),
    DAILY("매일"),
    WEEKLY("매주"),
    MONTHLY("매월")
}

/**
 * 장소 선택 결과를 구조화해서 저장하기 위한 모델.
 */
data class EventLocation(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeId: String = ""
)

/**
 * Firestore의 'events' 컬렉션에 저장될 단일 일정 데이터 모델
 */
data class Event(
    @DocumentId val id: String = "",
    val userId: String = "",

    // --- 일정 기본 정보 ---
    val title: String = "",
    val location: EventLocation? = null,
    val memo: String? = null,

    // --- 시간 정보 ---
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,

    // --- 반복 정보 ---
    /**
     * 이 일정이 어떤 규칙으로 반복되는지를 나타냅니다.
     */
    val recurrence: Recurrence = Recurrence.NONE,

    /**
     * 반복으로 생성된 일정들을 하나의 그룹으로 묶는 ID.
     * 예: "매주 월요일 회의" 일정 10개가 모두 동일한 ID를 가집니다.
     * 나중에 이 그룹 전체를 한번에 수정/삭제할 때 사용됩니다.
     */
    val recurringGroupId: String? = null
)
