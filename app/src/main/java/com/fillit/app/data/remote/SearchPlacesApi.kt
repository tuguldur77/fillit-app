package com.fillit.app.data.remote

import retrofit2.http.*

data class LocationRequest(val lat: Double, val lng: Double)

data class NearbyRequest(
    val location: LocationRequest?,
    val radius: Int?,
    val keyword: String? = null,
    val chip: String? = null,
    val language: String? = "ko",
    val region: String? = "KR",
    val includedTypes: List<String>? = null,
    val rankPreference: String? = null,
    val selectedPrices: List<String>? = null,
    val selectedTransports: List<String>? = null
)

// New: request body for server-side text search
// Mirrors Node controller's searchByKeyword contract
// Either keyword or pageToken must be present

data class TextSearchRequest(
    val keyword: String? = null,
    val location: LocationRequest? = null,
    val radius: Int? = null,
    val language: String? = "ko",
    val maxResults: Int? = null,
    val pageToken: String? = null,
    val region: String? = "KR",
    val selectedPrices: List<String>? = null,
    val selectedTransports: List<String>? = null
)

data class PhotoDTO(
    val name: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)

data class PlaceItemDTO(
    val placeId: String,
    val name: String? = null,
    val formattedAddress: String? = null,
    val rating: Double? = null,
    val userRatingsTotal: Int? = null,
    val priceLevel: String? = null,
    val photos: List<PhotoDTO>? = null,
    val types: List<String>? = null,
    val primaryType: String? = null
)

data class NearbyResponseDTO(
    val results: List<PlaceItemDTO>,
    val nextPageToken: String? = null,
    val status: String? = null
)

data class PhotoUrlResponse(val url: String)

interface SearchPlacesApi {
    @POST("api/searchPlace/nearby")
    suspend fun searchNearby(@Body body: NearbyRequest): NearbyResponseDTO

    @POST
    suspend fun searchText(@Url url: String, @Body body: TextSearchRequest): NearbyResponseDTO

    // Minimal map-based POST (only keyword field) for servers that reject extra nulls
    @POST
    suspend fun searchTextMap(@Url url: String, @Body body: Map<String, @JvmSuppressWildcards Any>): NearbyResponseDTO

    @GET
    suspend fun searchTextGet(
        @Url url: String,
        @Query("keyword") keyword: String?,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("language") language: String? = "ko",
        @Query("region") region: String? = "KR"
    ): NearbyResponseDTO

    // Alternate GET using 'query' param name some backends choose
    @GET
    suspend fun searchTextGetQuery(
        @Url url: String,
        @Query("query") query: String?,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("language") language: String? = "ko",
        @Query("region") region: String? = "KR"
    ): NearbyResponseDTO

    @GET("api/searchPlace/photo-url")
    suspend fun photoUrl(
        @Query("name") name: String,
        @Query("maxWidthPx") maxWidthPx: Int = 800
    ): PhotoUrlResponse

    @POST("api/recommendation/for-slot")
    suspend fun recommendForSlot(
        @Body request: RecommendationForSlotRequest
    ): RecommendationForSlotResponse
}
