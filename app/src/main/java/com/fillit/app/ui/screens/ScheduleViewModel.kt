package com.fillit.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fillit.app.model.Event
import com.fillit.app.model.EventLocation
import com.fillit.app.model.Recurrence
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// 화면에 표시될 타임라인의 각 항목을 나타내는 데이터 클래스
sealed class TimelineItem {
    // 실제 일정을 나타내는 항목
    data class EventItem(val event: Event) : TimelineItem()
    // 일정 사이의 빈 시간을 나타내는 항목
    data class EmptySlot(val startTime: Timestamp, val endTime: Timestamp) : TimelineItem()
}

/**
 * ScheduleViewScreen에 표시될 데이터를 관리하고, 비즈니스 로직을 처리하는 ViewModel.
 */
data class ScheduleUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val timeline: List<TimelineItem> = emptyList(), // 'events'에서 'timeline'으로 변경
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ScheduleViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var dateListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val koreaZoneId = ZoneId.of("Asia/Seoul")

    init {
        // ViewModel이 생성되면, 오늘 날짜의 일정을 즉시 가져옵니다.
        subscribeEventsForDate(LocalDate.now())
    }

    /**
     * 사용자가 화면에서 날짜를 선택했을 때 호출되는 함수.
     */
    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        subscribeEventsForDate(date)
    }

    /**
     * 현재 선택된 날짜의 데이터를 다시 불러옵니다.
     * 화면이 다시 활성화될 때 호출됩니다.
     */
    fun refreshEvents() {
        subscribeEventsForDate(_uiState.value.selectedDate)
    }

    /**
     * 새로운 일정을 추가합니다.
     */
    fun addEvent(
        title: String,
        startTime: Timestamp,
        endTime: Timestamp,
        location: EventLocation?,
        memo: String,
        recurrence: Recurrence = Recurrence.NONE
    ) {
        val uid = auth.currentUser?.uid ?: return
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title,
            "startTime" to startTime,
            "endTime" to endTime,
            "memo" to memo,
            "recurrence" to recurrence.name,
            "createdAt" to Timestamp.now()
        )
        if (location != null) {
            payload["location"] = mapOf(
                "name" to location.name,
                "lat" to location.lat,
                "lng" to location.lng,
                "placeId" to location.placeId
            )
        }
        db.collection("events").add(payload)
    }

    /**
     * 기존 일정을 업데이트합니다.
     */
    fun updateEvent(eventId: String, updatedFields: Map<String, Any>) {
        db.collection("events").document(eventId).update(updatedFields)
    }

    /**
     * 일정을 삭제합니다.
     */
    fun deleteEvent(eventId: String) {
        db.collection("events").document(eventId).delete()
    }

    /**
     * 특정 날짜의 일정을 Firestore에서 실시간으로 구독합니다.
     */
    private fun subscribeEventsForDate(date: LocalDate) {
        dateListener?.remove()
        val uid = auth.currentUser?.uid ?: return

        val startOfDay = date.atStartOfDay(koreaZoneId).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(koreaZoneId).toInstant()

        _uiState.update { it.copy(isLoading = true, errorMessage = null, timeline = emptyList()) }

        dateListener = db.collection("events")
            .whereEqualTo("userId", uid)
            .whereGreaterThanOrEqualTo("startTime", Timestamp(startOfDay.epochSecond, 0))
            .whereLessThan("startTime", Timestamp(endOfDay.epochSecond, 0))
            .orderBy("startTime")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                    return@addSnapshotListener
                }

                val events = snap?.documents.orEmpty().mapNotNull { d ->
                    runCatching {
                        val locationMap = d.get("location") as? Map<*, *>
                        val eventLocation = locationMap?.let {
                            EventLocation(
                                name = it["name"] as? String ?: "",
                                lat = (it["lat"] as? Number)?.toDouble() ?: 0.0,
                                lng = (it["lng"] as? Number)?.toDouble() ?: 0.0,
                                placeId = it["placeId"] as? String ?: ""
                            )
                        }

                        Event(
                            id = d.id,
                            title = d.getString("title").orEmpty(),
                            startTime = d.getTimestamp("startTime"),
                            endTime = d.getTimestamp("endTime"),
                            location = eventLocation,
                            memo = d.getString("memo"),
                            recurrence = d.getString("recurrence")
                                ?.let { runCatching { Recurrence.valueOf(it) }.getOrNull() }
                                ?: Recurrence.NONE
                        )
                    }.getOrNull()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        timeline = calculateTimeline(events),
                        errorMessage = null
                    )
                }
            }
    }

    /**
     * 정렬된 일정 목록을 기반으로 빈 시간을 계산하여 전체 타임라인을 생성합니다.
     */
    private fun calculateTimeline(events: List<Event>): List<TimelineItem> {
        val timeline = mutableListOf<TimelineItem>()
        var lastEndTime: Timestamp? = null

        // Firestore에서 이미 시작 시간으로 정렬해서 가져옴
        for (event in events) {
            val eventStartTime = event.startTime ?: continue // 시작 시간 없는 이벤트는 타임라인에 표시 안함

            // 이전 이벤트의 종료 시간과 현재 이벤트의 시작 시간 사이에 빈틈이 있는지 확인
            if (lastEndTime != null) {
                // 1분 이상 차이가 나면 '빈 시간'으로 간주
                if (eventStartTime.seconds > lastEndTime.seconds + 60) {
                    timeline.add(TimelineItem.EmptySlot(lastEndTime, eventStartTime))
                }
            }

            timeline.add(TimelineItem.EventItem(event))
            lastEndTime = event.endTime
        }
        return timeline
    }

    /**
     * 특정 날짜의 이벤트를 가져오는 헬퍼 함수.
     * History 탭 로직에서 재사용할 수 있도록 공개됨.
     */
    fun getEventsByDate(date: LocalDate) {
        onDateSelected(date)
    }

    override fun onCleared() {
        dateListener?.remove()
        super.onCleared()
    }
}
