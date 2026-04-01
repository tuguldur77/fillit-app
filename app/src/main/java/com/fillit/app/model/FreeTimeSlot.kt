package com.fillit.app.model

data class FreeTimeSlot(
    val startMillis: Long,
    val endMillis: Long
) {
    val durationMinutes: Long get() = (endMillis - startMillis) / 60000
}
