package com.fillit.app.model

data class UiPlace(
    val placeId: String,
    val name: String,
    val address: String,
    val rating: String,
    val photoName: String? = null,
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val types: List<String>? = null,
    val primaryType: String? = null,
    val distanceM: Double? = null,
    val travelTimeSec: Int? = null,
    val score: Double? = null,
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null,
    val reasonTags: List<String>? = null
)
