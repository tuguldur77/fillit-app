package com.fillit.app.util

object CategoryTypeMapping {
    // Shared mapping between client & backend (keep backend in sync)
    val map: Map<String, Set<String>> = mapOf(
        "카페" to setOf("cafe"),
        "전시" to setOf("museum", "art_gallery"),
        "체험" to setOf("tourist_attraction", "amusement_park", "theme_park"),
        "관광지" to setOf("tourist_attraction", "park", "museum", "landmark"),
        "맛집" to setOf("restaurant", "bakery", "bar", "cafe"),
        "쇼핑" to setOf("shopping_mall", "department_store", "clothing_store", "book_store")
    )
    fun typesForCategory(category: String): Set<String> = map[category] ?: emptySet()
    val allCategories: Set<String> get() = map.keys
}
