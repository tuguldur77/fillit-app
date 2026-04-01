package com.fillit.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class DistanceMatrixResponse(
    val destination_addresses: List<String>? = null,
    val origin_addresses: List<String>? = null,
    val rows: List<Row>? = null,
    val status: String? = null
)

data class Row(
    val elements: List<Element>? = null
)

data class Element(
    val status: String? = null,
    val distance: ValueText? = null,
    val duration: ValueText? = null,
    val duration_in_traffic: ValueText? = null
)

data class ValueText(
    val text: String? = null,
    val value: Int? = null
)

interface DistanceMatrixApi {
    @GET("maps/api/distancematrix/json")
    suspend fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("mode") mode: String,
        @Query("language") language: String = "ko",
        @Query("key") apiKey: String
    ): DistanceMatrixResponse
}
