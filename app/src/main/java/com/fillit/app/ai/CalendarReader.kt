package com.fillit.app.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.fillit.app.BuildConfig
import com.fillit.app.model.CalendarEvent
import com.fillit.app.model.CalendarGap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

@Suppress("unused")
class CalendarReader(
    private val context: Context
) {
    companion object {
        private const val TAG = "CalendarReader"
        private const val WINDOW_MS = 3 * 60 * 60 * 1000L
    }

    suspend fun getGapEvents(slotStart: Long, slotEnd: Long, context: Context = this.context): CalendarGap = withContext(Dispatchers.IO) {
        val durationMinutes = ((slotEnd - slotStart) / 60000L).toInt().coerceAtLeast(0)
        val searchFrom = slotStart - WINDOW_MS
        val searchTo = slotEnd + WINDOW_MS

        Log.d(TAG, "=== CalendarReader START ===")
        Log.d(TAG, "slotStart: $slotStart (${Date(slotStart)})")
        Log.d(TAG, "slotEnd: $slotEnd (${Date(slotEnd)})")
        Log.d(TAG, "searchFrom: ${Date(searchFrom)}")
        Log.d(TAG, "searchTo: ${Date(searchTo)}")

        val hasPermission = hasReadCalendarPermission(context)
        Log.d(TAG, "READ_CALENDAR permission: $hasPermission")

        if (!hasPermission) {
            Log.w(TAG, "NO PERMISSION -> returning empty gap")
            Log.d(TAG, "=== CalendarReader END ===")
            return@withContext CalendarGap(null, null, slotStart, slotEnd, durationMinutes)
        }

        val events = mutableListOf<CalendarEvent>()
        val resolver = context.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
        val selectionWindow = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selection = "$selectionWindow AND ${CalendarContract.Events.VISIBLE} = 1"
        val selectionArgs = arrayOf(
            searchFrom.toString(),
            searchTo.toString()
        )
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        try {
            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            Log.d(TAG, "Raw events found in window: ${cursor?.count ?: 0}")
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)

                while (c.moveToNext()) {
                    val allDay = c.getInt(allDayIdx) == 1
                    val id = c.getLong(idIdx).toString()
                    val title = c.getString(titleIdx)?.trim().orEmpty()
                    val start = c.getLong(startIdx)
                    val end = c.getLong(endIdx)

                    Log.d(TAG, "Event: title=$title start=${Date(start)} end=${Date(end)} allDay=$allDay")

                    if (allDay) continue
                    if (start == end) continue
                    if (title.isBlank()) continue

                    events += CalendarEvent(id = id, title = title, startTime = start, endTime = end)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Calendar query failed: ${e.message}", e)
        }

        val sorted = events.sortedBy { it.startTime }
        Log.d(TAG, "After filtering allDay/blank: ${sorted.size} events")
        val previousEvent = sorted.filter { it.endTime <= slotStart }.maxByOrNull { it.endTime }
        val nextEvent = sorted.filter { it.startTime >= slotEnd }.minByOrNull { it.startTime }

        Log.d(TAG, "previousEvent: ${previousEvent?.title} ends ${previousEvent?.endTime?.let { Date(it) }}")
        Log.d(TAG, "nextEvent: ${nextEvent?.title} starts ${nextEvent?.startTime?.let { Date(it) }}")
        Log.d(TAG, "=== CalendarReader END ===")

        CalendarGap(
            previousEvent = previousEvent,
            nextEvent = nextEvent,
            gapStartTime = slotStart,
            gapEndTime = slotEnd,
            gapDurationMinutes = durationMinutes
        )
    }

    fun hasReadCalendarPermission(context: Context = this.context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    // Temporary debug helper to inspect emulator/device calendar data.
    fun debugPrintAllCalendarEvents(context: Context = this.context) {
        if (!BuildConfig.DEBUG) return
        try {
            val hasPermission = hasReadCalendarPermission(context)
            Log.d("CalendarDebug", "READ_CALENDAR permission: $hasPermission")
            if (!hasPermission) {
                Log.w("CalendarDebug", "Permission denied; skip full event dump")
                return
            }

            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_ID
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            Log.d("CalendarDebug", "=== ALL CALENDAR EVENTS ===")
            Log.d("CalendarDebug", "Total events: ${cursor?.count ?: 0}")

            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(1) ?: "null"
                    val start = it.getLong(2)
                    val end = it.getLong(3)
                    val allDay = it.getInt(4)
                    val calId = it.getLong(5)
                    Log.d(
                        "CalendarDebug",
                        "[$calId] $title | ${Date(start)} -> ${Date(end)} | allDay=$allDay"
                    )
                }
            }
            Log.d("CalendarDebug", "=== END ===")
        } catch (e: Exception) {
            Log.e("CalendarDebug", "debugPrintAllCalendarEvents failed: ${e.message}", e)
        }
    }
}

