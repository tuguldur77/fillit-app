package com.fillit.app.model

import com.google.gson.annotations.SerializedName

data class CalendarContext(
    @SerializedName("mood")
    val mood: String,
    @SerializedName("energy")
    val energy: String,
    @SerializedName("social")
    val social: String,
    @SerializedName("suggestedKeywords")
    val suggestedKeywords: List<String>,
    @SerializedName("avoidKeywords")
    val avoidKeywords: List<String>
)

data class LatLngBody(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double
)

data class RecommendationForSlotRequest(
    @SerializedName("requestOrigin")
    val requestOrigin: LatLngBody,
    @SerializedName("slotStart")
    val slotStart: Long,
    @SerializedName("slotEnd")
    val slotEnd: Long,
    @SerializedName("categories")
    val categories: List<String>,
    @SerializedName("transport")
    val transport: String,
    @SerializedName("calendarContext")
    val calendarContext: CalendarContextBody? = null
)

data class RecommendationRequestBody(
    @SerializedName("origin")
    val origin: Map<String, Double>,
    @SerializedName("slotStart")
    val slotStart: Long,
    @SerializedName("slotEnd")
    val slotEnd: Long,
    @SerializedName("categories")
    val categories: List<String>,
    @SerializedName("transport")
    val transport: String,
    @SerializedName("calendarContext")
    val calendarContext: CalendarContextBody? = null
)

data class CalendarContextBody(
    @SerializedName("mood")
    val mood: String,
    @SerializedName("energy")
    val energy: String,
    @SerializedName("social")
    val social: String,
    @SerializedName("suggestedKeywords")
    val suggestedKeywords: List<String>,
    @SerializedName("avoidKeywords")
    val avoidKeywords: List<String>
)

