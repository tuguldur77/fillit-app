package com.fillit.app.model

data class CalendarGap(
    val previousEvent: CalendarEvent?,
    val nextEvent: CalendarEvent?,
    val gapStartTime: Long,
    val gapEndTime: Long,
    val gapDurationMinutes: Int
)

