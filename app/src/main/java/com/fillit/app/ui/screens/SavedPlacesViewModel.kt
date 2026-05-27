package com.fillit.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fillit.app.ServiceLocator
import com.fillit.app.data.remote.LocationRequest
import com.fillit.app.data.remote.SearchPlacesApi
import com.fillit.app.model.UiPlace
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SavedPlacesViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val api: SearchPlacesApi = ServiceLocator.searchPlacesApi

    private val _favorites = MutableStateFlow<List<UiPlace>>(emptyList())
    val favorites: StateFlow<List<UiPlace>> = _favorites.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _favoriteMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val favoriteMessages: SharedFlow<String> = _favoriteMessages.asSharedFlow()

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        val uid = auth.currentUser?.uid ?: return
        _loading.value = true
        db.collection("users").document(uid).collection("wanted")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _error.value = err.message
                    _loading.value = false
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { d ->
                    // Resolve imageUrl consistently: prefer stored imageUrl, otherwise proxy by photoName
                    val rawImage = d.getString("imageUrl")
                    val photoName = d.getString("photoName")
                    val fallbackProxy = photoName?.let { "/api/searchPlace/photoProxy?name=$it" }
                    val resolved = resolveBackendImageUrl(rawImage ?: fallbackProxy)
                    Log.d("IMAGE_URL_RESOLVE", "savedFavorite raw=$rawImage photoName=$photoName fallbackProxy=$fallbackProxy resolved=$resolved")

                    UiPlace(
                        placeId = d.getString("placeId") ?: d.id,
                        name = d.getString("name") ?: "이름 없음",
                        address = d.getString("address") ?: "",
                        rating = d.getString("rating") ?: "N/A",
                        photoName = photoName,
                        imageUrl = resolved,
                        isFavorite = true,
                        types = (d.get("types") as? List<*>)?.mapNotNull { it as? String },
                        primaryType = d.getString("primaryType")
                    )
                } ?: emptyList()
                _favorites.value = list
                _loading.value = false
            }
    }

    fun fetchPhotoUrlIfNeeded(index: Int) {
        val current = _favorites.value.getOrNull(index) ?: return
        val photoName = current.photoName
        if (photoName.isNullOrBlank() || current.imageUrl != null) return
        viewModelScope.launch {
            try {
                val url = api.photoUrl(photoName).url
                val resolved = resolveBackendImageUrl(url)
                Log.d("IMAGE_URL_RESOLVE", "fetchPhoto raw=$url resolved=$resolved path=savedFavorites")
                val copy = _favorites.value.toMutableList()
                copy[index] = copy[index].copy(imageUrl = resolved)
                _favorites.value = copy
            } catch (e: Exception) {
                Log.w("SavedPlacesVM", "photo-url failed: ${e.message}")
            }
        }
    }

    // Helper: resolve backend relative photo paths to absolute URL used by Coil
    private fun resolveBackendImageUrl(rawUrl: String?): String? {
        val baseUrl = ServiceLocator.BASE_URL
        return when {
            rawUrl.isNullOrBlank() -> null
            rawUrl.startsWith("http://10.0.2.2:3000", ignoreCase = true) -> {
                val suffix = rawUrl.removePrefix("http://10.0.2.2:3000")
                baseUrl.trimEnd('/') + suffix
            }
            rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
            rawUrl.startsWith("/") -> baseUrl.trimEnd('/') + rawUrl
            else -> null
        }
    }

    fun removeFavorite(placeId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("wanted").document(favoriteDocId(placeId))
            .delete()
            .addOnFailureListener { e -> Log.e("SavedPlacesVM", "removeFavorite failed: ${e.message}") }
    }

    private fun favoriteDocId(placeId: String): String = placeId.replace("/", "_")

    fun toggleFavorite(place: UiPlace) {
        val uid = auth.currentUser?.uid ?: return
        val docId = favoriteDocId(place.placeId)
        val ref = db.collection("users").document(uid).collection("wanted").document(docId)
        val previousFavorites = _favorites.value

        viewModelScope.launch {
            if (place.isFavorite) {
                try {
                    ref.delete().await()
                    _favorites.value = _favorites.value.filterNot { it.placeId == place.placeId }
                } catch (e: Exception) {
                    _favorites.value = previousFavorites
                    _favoriteMessages.tryEmit("찜 해제를 실패했어요. 잠시 후 다시 시도해 주세요.")
                    Log.e("SavedPlacesVM", "toggleFavorite remove failed: ${e.message}")
                }
            } else {
                val data = hashMapOf(
                    "placeId" to place.placeId,
                    "name" to place.name,
                    "address" to place.address,
                    "rating" to place.rating,
                    "photoName" to place.photoName,
                    "imageUrl" to place.imageUrl,
                    "primaryType" to place.primaryType,
                    "types" to (place.types ?: emptyList<String>()),
                    "createdAt" to Timestamp.now()
                )
                try {
                    ref.set(data).await()
                    _favorites.value = _favorites.value + place.copy(isFavorite = true)
                } catch (e: Exception) {
                    _favorites.value = previousFavorites
                    _favoriteMessages.tryEmit("찜 저장에 실패했어요. 잠시 후 다시 시도해 주세요.")
                    Log.e("SavedPlacesVM", "toggleFavorite add failed: ${e.message}")
                }
            }
        }
    }
}
