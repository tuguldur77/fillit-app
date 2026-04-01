package com.fillit.app.data

import android.util.Log
import com.fillit.app.model.Event
import com.fillit.app.model.Recurrence
import com.fillit.app.model.SessionManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Firestore의 'events' 컬렉션과 관련된 모든 데이터 작업을 처리하는 클래스(저장소).
 */
object EventRepository {

    private const val COLLECTION_NAME = "events"
    private val db = Firebase.firestore

    suspend fun addEvent(event: Event): Result<String> {
        val currentUserId = SessionManager.userId
            ?: return Result.failure(Exception("로그인한 사용자 정보가 없습니다."))

        return when (event.recurrence) {
            Recurrence.NONE -> addSingleEvent(event, currentUserId)
            Recurrence.WEEKLY -> addWeeklyRecurringEvents(event, currentUserId)
            else -> Result.failure(Exception("현재는 매주 반복만 지원합니다."))
        }
    }

    suspend fun getEventsForDate(date: LocalDate): Result<List<Event>> {
        val currentUserId = SessionManager.userId
            ?: return Result.failure(Exception("로그인한 사용자 정보가 없습니다."))

        return try {
            val startOfDay = Timestamp(date.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond, 0)
            val endOfDay = Timestamp(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond - 1, 999_999_999)

            val querySnapshot = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", currentUserId)
                .whereGreaterThanOrEqualTo("startTime", startOfDay)
                .whereLessThanOrEqualTo("startTime", endOfDay)
                .orderBy("startTime")
                .get()
                .await()

            val events = querySnapshot.toObjects<Event>()
            Log.d("EventRepository", "Fetched ${events.size} events for $date")
            Result.success(events)
        } catch (e: Exception) {
            Log.e("EventRepository", "Error fetching events for $date", e)
            Result.failure(e)
        }
    }

    private suspend fun addSingleEvent(event: Event, userId: String): Result<String> {
        if (event.startTime != null && event.endTime != null) {
            // 시간 순서 검사
            if (event.startTime.seconds >= event.endTime.seconds) {
                return Result.failure(Exception("종료 시간은 시작 시간보다 늦어야 합니다."))
            }
            // 시간 중복 검사
            val date = event.startTime.toDate().toLocalDate()
            val eventsOnSameDay = getEventsForDate(date).getOrNull() ?: emptyList()
            if (isOverlapping(event, eventsOnSameDay)) {
                return Result.failure(Exception("다른 일정과 시간이 겹칩니다."))
            }
        }

        return try {
            val eventToSave = event.copy(userId = userId)
            val documentReference = db.collection(COLLECTION_NAME).add(eventToSave).await()
            Log.d("EventRepository", "Single event added with ID: ${documentReference.id}")
            Result.success(documentReference.id)
        } catch (e: Exception) {
            Log.e("EventRepository", "Error adding single event", e)
            Result.failure(e)
        }
    }

    private suspend fun addWeeklyRecurringEvents(event: Event, userId: String): Result<String> {
        val startTime = event.startTime
            ?: return Result.failure(Exception("반복 일정에는 시작 시간이 반드시 필요합니다."))

        if (event.endTime != null) {
            if (startTime.seconds >= event.endTime.seconds) {
                return Result.failure(Exception("종료 시간은 시작 시간보다 늦어야 합니다."))
            }
            // 첫 번째 일정에 대해 시간 중복 검사
            val date = startTime.toDate().toLocalDate()
            val eventsOnSameDay = getEventsForDate(date).getOrNull() ?: emptyList()
            if (isOverlapping(event, eventsOnSameDay)) {
                return Result.failure(Exception("반복 시작일의 일정이 다른 일정과 시간이 겹칩니다."))
            }
        }

        return try {
            val batch = db.batch()
            val groupId = UUID.randomUUID().toString()
            val baseEvent = event.copy(
                userId = userId,
                recurringGroupId = groupId,
                recurrence = Recurrence.WEEKLY
            )

            val calendar = Calendar.getInstance().apply { time = startTime.toDate() }
            val durationSeconds = event.endTime?.let { it.seconds - startTime.seconds }

            for (i in 0 until 52) { // 1년치
                val newStartTime = Timestamp(calendar.time)
                val newEndTime = durationSeconds?.let { Timestamp(newStartTime.seconds + it, 0) }

                val weeklyEvent = baseEvent.copy(startTime = newStartTime, endTime = newEndTime)
                val docRef = db.collection(COLLECTION_NAME).document()
                batch.set(docRef, weeklyEvent)

                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }

            batch.commit().await()
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 새 이벤트가 기존 이벤트 목록과 시간이 겹치는지 확인합니다.
     */
    private fun isOverlapping(newEvent: Event, existingEvents: List<Event>): Boolean {
        val newStart = newEvent.startTime?.seconds ?: return false
        val newEnd = newEvent.endTime?.seconds ?: return false

        return existingEvents.any { existingEvent ->
            val existingStart = existingEvent.startTime?.seconds ?: return@any false
            val existingEnd = existingEvent.endTime?.seconds ?: return@any false

            // 겹치는 조건: newStart가 existingEnd보다 빠르고, newEnd가 existingStart보다 늦을 때
            newStart < existingEnd && newEnd > existingStart
        }
    }

    // Timestamp를 LocalDate로 변환하는 확장 함수
    private fun Date.toLocalDate(): LocalDate {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
