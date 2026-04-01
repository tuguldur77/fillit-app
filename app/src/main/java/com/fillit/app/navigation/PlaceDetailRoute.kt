package com.fillit.app.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

object PlaceDetailRoute {
    const val BASE = "placeDetail"

    const val ARG_PLACE_ID = "placeId"
    const val ARG_NAME = "name"
    const val ARG_ADDRESS = "address"
    const val ARG_RATING = "rating"
    const val ARG_START_MILLIS = "startMillis"
    const val ARG_END_MILLIS = "endMillis"
    const val ARG_IMAGE_URL = "imageUrl"
    const val ARG_OPEN_NOW = "openNow"
    const val ARG_WEEKDAY_DESCRIPTIONS = "weekdayDescriptions"

    // include imageUrl and both start/end millis in the pattern (all optional via query params)
    const val ROUTE_PATTERN =
        "$BASE?" +
            "$ARG_PLACE_ID={$ARG_PLACE_ID}&" +
            "$ARG_NAME={$ARG_NAME}&" +
            "$ARG_ADDRESS={$ARG_ADDRESS}&" +
            "$ARG_RATING={$ARG_RATING}&" +
            "$ARG_IMAGE_URL={$ARG_IMAGE_URL}&" +
            "$ARG_OPEN_NOW={$ARG_OPEN_NOW}&" +
            "$ARG_WEEKDAY_DESCRIPTIONS={$ARG_WEEKDAY_DESCRIPTIONS}&" +
            "$ARG_START_MILLIS={$ARG_START_MILLIS}&" +
            "$ARG_END_MILLIS={$ARG_END_MILLIS}"

    fun create(
        placeId: String,
        name: String,
        address: String,
        rating: String,
        imageUrl: String? = null,
        openNow: Boolean? = null,
        weekdayDescriptions: List<String>? = null,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): String {
        val weekdayJoined = weekdayDescriptions?.joinToString("||").orEmpty()
        return "$BASE" +
            "?$ARG_PLACE_ID=${Uri.encode(placeId)}" +
            "&$ARG_NAME=${Uri.encode(name)}" +
            "&$ARG_ADDRESS=${Uri.encode(address)}" +
            "&$ARG_RATING=${Uri.encode(rating)}" +
            "&$ARG_IMAGE_URL=${Uri.encode(imageUrl ?: "")}" +
            "&$ARG_OPEN_NOW=${Uri.encode(openNow?.toString() ?: "")}" +
            "&$ARG_WEEKDAY_DESCRIPTIONS=${Uri.encode(weekdayJoined)}" +
            "&$ARG_START_MILLIS=${startMillis ?: -1L}" +
            "&$ARG_END_MILLIS=${endMillis ?: -1L}"
    }

    data class Args(
        val placeId: String,
        val name: String,
        val address: String,
        val rating: String,
        val imageUrl: String?,
        val openNow: Boolean?,
        val weekdayDescriptions: List<String>?,
        val startMillis: Long?,
        val endMillis: Long?
    )

    fun parse(entry: NavBackStackEntry): Args {
        val a = entry.arguments
        val start = a?.getLong(ARG_START_MILLIS) ?: -1L
        val end = a?.getLong(ARG_END_MILLIS) ?: -1L
        val img = a?.getString(ARG_IMAGE_URL).orEmpty().ifBlank { null }
        val openNowStr = a?.getString(ARG_OPEN_NOW).orEmpty().ifBlank { null }
        val openNow = when (openNowStr?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        val weekdayRaw = a?.getString(ARG_WEEKDAY_DESCRIPTIONS).orEmpty().ifBlank { null }
        val weekdays = weekdayRaw?.split("||")?.map { it.trim() }?.filter { it.isNotBlank() }

        return Args(
            placeId = a?.getString(ARG_PLACE_ID).orEmpty(),
            name = a?.getString(ARG_NAME).orEmpty(),
            address = a?.getString(ARG_ADDRESS).orEmpty(),
            rating = a?.getString(ARG_RATING).orEmpty(),
            imageUrl = img,
            openNow = openNow,
            weekdayDescriptions = weekdays,
            startMillis = if (start > 0) start else null,
            endMillis = if (end > 0) end else null
        )
    }
}
