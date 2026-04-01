package com.fillit.app.data.remote.model

data class NearbyResponseDTO(
    val results: List<PlaceItemDTO>,
    val nextPageToken: String? = null,
    val status: String? = null
)
