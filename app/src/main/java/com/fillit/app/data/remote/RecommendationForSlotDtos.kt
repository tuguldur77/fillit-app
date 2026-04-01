package com.fillit.app.data.remote

data class RecommendationForSlotRequest(
    val origin: RecommendationOrigin,
    val slotStart: Long,
    val slotEnd: Long,
    val categories: List<String>? = null,
    val transport: String? = null,
    val selectedCategories: List<String>? = null,
    val selectedTransports: List<String>? = null,
    val language: String? = "ko",
    val region: String? = "KR",
    val maxResults: Int? = 20
)

data class RecommendationOrigin(
    val lat: Double,
    val lng: Double
)

data class RecommendationForSlotResponse(
    val message: String? = null,
    val data: RecommendationForSlotData? = null
)

data class RecommendationForSlotData(
    val places: List<RecommendationPlace> = emptyList()
)

data class RecommendationPlacePhotoDto(
    val name: String? = null,
    val photoName: String? = null,
    val url: String? = null,
    val photoUrl: String? = null
)

data class RecommendationOpeningHours(
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null
)

data class RecommendationPlace(
    val id: String? = null,
    val name: String = "",
    val address: String? = null,
    val formattedAddress: String? = null,
    val rating: Double? = null,
    val distanceM: Double? = null,
    val travelTimeSec: Int? = null,
    val score: Double? = null,
    val photoName: String? = null,
    val photoUrl: String? = null,
    val photos: List<RecommendationPlacePhotoDto>? = null,
    val openingHours: RecommendationOpeningHours? = null,
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null,
    val primaryType: String? = null,
    val types: List<String>? = null,
    val reasonTags: List<String>? = null
)
