package com.fillit.app.data.remote.model

data class PlaceItemDTO(
    val placeId: String,
    val name: String? = null,
    val formattedAddress: String? = null,
    val rating: Double? = null,
    val userRatingsTotal: Int? = null,
    val priceLevel: String? = null,
    val photos: List<PhotoDTO>? = null
)
