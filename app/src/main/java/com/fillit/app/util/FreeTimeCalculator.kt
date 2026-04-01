package com.fillit.app.util

import com.fillit.app.model.Event
import com.fillit.app.model.FreeTimeSlot
import com.google.firebase.Timestamp
import java.util.*

object FreeTimeCalculator {

    fun computeFreeSlots(
        events: List<Event>,
        dayStart: Timestamp,
        dayEnd: Timestamp,
        minMinutes: Long = 30
    ): List<FreeTimeSlot> {
        val sorted = events
            .filter { it.startTime != null && it.endTime != null }
            .sortedBy { it.startTime!!.seconds }

        val slots = mutableListOf<FreeTimeSlot>()
        var cursor = dayStart.toDate().time

        for (e in sorted) {
            val eStart = e.startTime!!.toDate().time
            if (eStart - cursor >= minMinutes * 60_000) {
                slots += FreeTimeSlot(cursor, eStart)
            }
            val eEnd = e.endTime!!.toDate().time
            cursor = maxOf(cursor, eEnd)
        }

        val endMillis = dayEnd.toDate().time
        if (endMillis - cursor >= minMinutes * 60_000) {
            slots += FreeTimeSlot(cursor, endMillis)
        }
        return slots
    }
}