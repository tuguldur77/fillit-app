package com.fillit.app.data.remote.model

data class NearbyRequest(
    val location: LocationRequest,
    val radius: Int,
    val keyword: String? = null,
    val language: String? = "ko",
    val region: String? = "KR"
)
