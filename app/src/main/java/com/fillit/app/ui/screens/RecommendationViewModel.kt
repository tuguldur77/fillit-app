package com.fillit.app.ui.screens

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillit.app.BuildConfig
import com.fillit.app.ServiceLocator
import com.fillit.app.ai.CalendarContextAnalyzer
import com.fillit.app.ai.GeminiHelper
import com.fillit.app.data.SearchHistoryRepository
import com.fillit.app.data.remote.DistanceMatrixApi
import com.fillit.app.data.remote.LocationRequest
import com.fillit.app.data.remote.NearbyRequest
import com.fillit.app.data.remote.SearchPlacesApi
import com.fillit.app.data.remote.TextSearchRequest
import com.fillit.app.data.remote.ValueText
import com.fillit.app.model.SessionManager
import com.fillit.app.model.CalendarContextBody
import com.fillit.app.model.CalendarContext
import com.fillit.app.model.CalendarEvent
import com.fillit.app.model.CalendarGap
import com.fillit.app.model.LatLngBody
import com.fillit.app.model.RecommendationForSlotRequest
import com.fillit.app.model.RecommendationRequestBody
import com.fillit.app.model.UiPlace
import com.fillit.app.preferences.UserPreferencesRepository
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private fun CalendarContext.toRequestBody() = CalendarContextBody(
    mood = this.mood,
    energy = this.energy,
    social = this.social,
    suggestedKeywords = this.suggestedKeywords,
    avoidKeywords = this.avoidKeywords
)

class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val api: SearchPlacesApi = ServiceLocator.searchPlacesApi
    private val userPrefs = UserPreferencesRepository(getApplication())
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication())
    private val searchHistoryRepository = SearchHistoryRepository()
    private val calendarContextAnalyzer = CalendarContextAnalyzer(
        apiKey = BuildConfig.GEMINI_API_KEY,
        db = db
    )

    private val distanceMatrixApi: DistanceMatrixApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(ServiceLocator.httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DistanceMatrixApi::class.java)
    }

    private val price: StateFlow<Map<String, Boolean>> = userPrefs.selectedPricesFlow
        .map { selected ->
            mapOf(
                "무료" to selected.contains("무료"),
                "₩ 저가" to selected.contains("₩ 저가"),
                "₩₩ 보통" to selected.contains("₩₩ 보통"),
                "₩₩₩ 고가" to selected.contains("₩₩₩ 고가")
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mapOf(
            "무료" to true,
            "₩ 저가" to true,
            "₩₩ 보통" to true,
            "₩₩₩ 고가" to false
        ))

    private val transport: StateFlow<Map<String, Boolean>> = userPrefs.selectedTransportsFlow
        .map { selected ->
            mapOf(
                "도보" to selected.contains("도보"),
                "자차" to selected.contains("자차")
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mapOf(
            "도보" to true,
            "자차" to false
        ))

    private val _places = MutableStateFlow<List<UiPlace>>(emptyList())
    val places: StateFlow<List<UiPlace>> = _places.asStateFlow()

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _favoriteMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val favoriteMessages: SharedFlow<String> = _favoriteMessages.asSharedFlow()

    // persistent slot-mode state
    private var isSlotMode: Boolean = false
    private var slotStartMillis: Long? = null
    private var slotEndMillis: Long? = null
    private var slotOriginLat: Double? = null
    private var slotOriginLng: Double? = null
    private var slotPreviousEvent: CalendarEvent? = null
    private var slotNextEvent: CalendarEvent? = null

    // new: recent search history exposed to UI
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Preserve full clicked UiPlace to avoid partial reconstruction loss through nav args
    private val _selectedPlaceSnapshot = MutableStateFlow<UiPlace?>(null)
    val selectedPlaceSnapshot: StateFlow<UiPlace?> = _selectedPlaceSnapshot.asStateFlow()

    private var favoritesListener: ListenerRegistration? = null

    // ✅ add/keep this public method
    fun setSelectedPlaceForFlow(place: UiPlace) {
        _selectedPlaceSnapshot.value = place
        Log.d(
            "PLACE_FLOW_CLICK",
            "name=${place.name}, primaryType=${place.primaryType}, types=${place.types}"
        )
    }

    init {
        observeFavorites()
        // load recent searches for current user if available
        viewModelScope.launch { loadSearchHistory() }
    }

    /** 🔄 Firestore 실시간 찜 반영 */
    private fun observeFavorites() {
        val uid = resolveCurrentUserId()
        if (uid.isNullOrBlank()) {
            Log.w("RecommendationVM", "observeFavorites skipped: no signed-in user")
            return
        }

        favoritesListener?.remove()
        favoritesListener = db.collection("users").document(uid).collection("wanted")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("RecommendationVM", "favorites listener failed: ${err.message}")
                    return@addSnapshotListener
                }
                val ids = snap?.documents?.mapNotNull { it.getString("placeId") }?.toSet() ?: emptySet()
                _likedIds.value = ids
                _places.value = _places.value.map { it.copy(isFavorite = ids.contains(it.placeId)) }
            }
    }

    /** 📍 Google Places API 호출 (Node.js 서버 경유) */
    fun loadNearbyPlaces(
        lat: Double = 37.5665,
        lng: Double = 126.9780,
        keyword: String? = null,
        chip: String? = null,
        radius: Int = 1500
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                var currentLat = lat
                var currentLng = lng
                var locationFetched = false

                val useDeviceLocation = userPrefs.useDeviceLocationFlow.first()
                if (useDeviceLocation && ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        if (location != null) {
                            currentLat = location.latitude
                            currentLng = location.longitude
                            locationFetched = true
                            Log.d("RecommendationVM", "Using user location: $currentLat, $currentLng")
                        } else {
                            Log.w("RecommendationVM", "Location is null, using default")
                        }
                    } catch (e: Exception) {
                        Log.e("RecommendationVM", "Failed to get location: ${e.message}, using default")
                    }
                } else {
                    Log.d("RecommendationVM", "Location permission not granted or disabled, using default Seoul")
                }

                val selectedPrices = price.value.filter { it.value }.keys.toList()
                val selectedTransports = transport.value.filter { it.value }.keys.toList()

                val adjustedRadius = when {
                    selectedTransports.contains("도보") && !selectedTransports.contains("자차") -> 500
                    selectedTransports.contains("자차") && !selectedTransports.contains("도보") -> 10000
                    else -> radius
                }

                val cappedRadius = minOf(if (locationFetched) adjustedRadius else 200000, 50000)

                Log.d("RecommendationVM", "selectedPrices = $selectedPrices, selectedTransports = $selectedTransports, adjustedRadius = $adjustedRadius")

                Log.d("RecommendationVM", "loadNearbyPlaces lat=$currentLat lng=$currentLng radius=$cappedRadius keyword=${keyword ?: "<null>"} chip=${chip ?: "<null>"}")

                val res = if (!chip.isNullOrBlank()) {
                    // Type search via Nearby with chip resolved on server
                    api.searchNearby(
                        NearbyRequest(
                            location = LocationRequest(currentLat, currentLng),
                            radius = cappedRadius,
                            chip = chip,
                            keyword = null,
                            selectedPrices = selectedPrices,
                            selectedTransports = selectedTransports
                        )
                    )
                } else if (!keyword.isNullOrBlank()) {
                    // Name/text search via server Text Search endpoint(s)
                    val body = TextSearchRequest(
                        keyword = keyword,
                        location = LocationRequest(currentLat, currentLng),
                        radius = cappedRadius,
                        language = "ko",
                        region = "KR",
                        maxResults = null,
                        pageToken = null,
                        selectedPrices = selectedPrices,
                        selectedTransports = selectedTransports
                    )
                    // Try a few common route names to be resilient to server routing
                    val routes = listOf(
                        "api/searchPlace/keyword",
                        "api/searchPlace/searchByKeyword",
                        "api/searchPlace/search-by-keyword"
                    )
                    var lastError: Exception? = null
                    var resp: com.fillit.app.data.remote.NearbyResponseDTO? = null
                    for (route in routes) {
                        try {
                            resp = api.searchText(route, body)
                            break
                        } catch (e: Exception) {
                            lastError = e
                            Log.w("RecommendationVM", "TextSearch route '$route' failed: ${e.message}")
                        }
                    }
                    resp ?: throw lastError ?: RuntimeException("TextSearch failed")
                } else {
                    // Default: Nearby without keyword or chip
                    api.searchNearby(
                        NearbyRequest(
                            location = LocationRequest(currentLat, currentLng),
                            radius = minOf(adjustedRadius, 50000),
                            keyword = null,
                            chip = null,
                            selectedPrices = selectedPrices,
                            selectedTransports = selectedTransports
                        )
                    )
                }

                val disliked = userPrefs.negativePlaceIdsFlow.first()
                val uid = auth.currentUser?.uid
                val historyFreq = if (!uid.isNullOrBlank()) {
                    searchHistoryRepository.getFrequency(uid)
                } else {
                    emptyMap()
                }

                _places.value = res.results.map {
                    val rawPhotoUrl = it.photos?.firstOrNull()?.name?.let { photoName ->
                        "/api/searchPlace/photoProxy?name=$photoName"
                    }
                    val resolvedImageUrl = resolveBackendImageUrl(rawPhotoUrl)
                    Log.d("IMAGE_URL_RESOLVE", "raw=$rawPhotoUrl resolved=$resolvedImageUrl path=nearby")

                    UiPlace(
                        placeId = it.placeId,
                        name = it.name ?: "이름 없음",
                        address = it.formattedAddress.orEmpty(),
                        rating = it.rating?.toString() ?: "N/A",
                        photoName = it.photos?.firstOrNull()?.name,
                        imageUrl = resolvedImageUrl,
                        isFavorite = _likedIds.value.contains(it.placeId),
                        types = it.types,
                        primaryType = it.primaryType
                    )
                }
                    .filterNot { disliked.contains(it.placeId) }
                    .sortedWith(
                        compareByDescending<UiPlace> {
                            val base = it.rating.toDoubleOrNull() ?: 0.0
                            val slot = timeSlotBias(it).toDouble()
                            val hist = historyBoost(it, historyFreq).toDouble()
                            base + slot + hist
                        }.thenBy { it.name }
                    )
            } catch (e: Exception) {
                val message = when (e) {
                    is HttpException -> {
                        val code = e.code()
                        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                        Log.e("RecommendationVM", "HTTP error $code body=$body")
                        when (code) {
                            400 -> "요청이 올바르지 않습니다. (400) 타입/키워드 파라미터를 확인하세요."
                            403 -> "403 (권한 거부): Places API 키 제한 또는 사용 권한 문제\n원인: ${body ?: e.message()}"
                            500 -> if (body?.contains("GOOGLE_PLACES_API_KEY") == true) {
                                "서버 설정 오류: GOOGLE_PLACES_API_KEY 환경변수가 설정되지 않았습니다."
                            } else {
                                "요청 실패 (HTTP 500): ${body ?: e.message()}"
                            }
                            else -> "요청 실패 (HTTP $code): ${body ?: e.message()}"
                        }
                    }
                    else -> e.message ?: "알 수 없는 오류"
                }
                _error.value = message
            } finally {
                _loading.value = false
            }
        }
    }

    /** 🖼️ 개별 사진 URL 로드 */
    fun fetchPhotoUrlIfNeeded(index: Int) {
        val current = _places.value.getOrNull(index) ?: return
        val photoName = current.photoName
        if (photoName.isNullOrBlank() || current.imageUrl != null) return
        viewModelScope.launch {
            try {
                val url = api.photoUrl(photoName).url
                val resolved = resolveBackendImageUrl(url)
                Log.d("IMAGE_URL_RESOLVE", "raw=$url resolved=${resolveBackendImageUrl(url)} path=photoFetch")
                val copy = _places.value.toMutableList()
                copy[index] = copy[index].copy(imageUrl = resolved)
                _places.value = copy
            } catch (_: Exception) {}
        }
    }

    /** 💜 찜 추가/삭제 */
    fun toggleFavorite(item: UiPlace) {
        val uid = resolveCurrentUserId()
        if (uid.isNullOrBlank()) {
            _error.value = "로그인 후 찜 기능을 사용할 수 있어요."
            Log.w("RecommendationVM", "toggleFavorite blocked: no signed-in user")
            return
        }

        val col = db.collection("users").document(uid).collection("wanted")
        val doc = col.document(favoriteDocId(item.placeId))
        val willFavorite = !_likedIds.value.contains(item.placeId)
        val previousLiked = _likedIds.value
        val previousPlaces = _places.value

        // Optimistic UI update
        _likedIds.value = if (willFavorite) _likedIds.value + item.placeId else _likedIds.value - item.placeId
        _places.value = _places.value.map {
            if (it.placeId == item.placeId) it.copy(isFavorite = willFavorite) else it
        }

        viewModelScope.launch {
            if (!willFavorite) {
                try {
                    doc.delete().await()
                } catch (e: Exception) {
                    _likedIds.value = previousLiked
                    _places.value = previousPlaces
                    _favoriteMessages.tryEmit("찜 해제를 실패했어요. 잠시 후 다시 시도해 주세요.")
                    Log.e("RecommendationVM", "Failed to remove favorite: ${e.message}")
                }
                return@launch
            }

            val validatedKeywords = generateValidatedKeywords(item)
            val wantedData = hashMapOf(
                "placeId" to item.placeId,
                "name" to item.name,
                "address" to item.address,
                "rating" to item.rating,
                "photoName" to item.photoName,
                "imageUrl" to item.imageUrl,
                "types" to (item.types ?: emptyList<String>()),
                "primaryType" to (item.primaryType ?: ""),
                "keywords" to validatedKeywords,
                "keywordSignals" to validatedKeywords.mapIndexed { idx, k ->
                    mapOf(
                        "keyword" to k,
                        "weight" to (validatedKeywords.size - idx),
                        "source" to "gemini+validated"
                    )
                },
                "score" to item.score,
                "createdAt" to FieldValue.serverTimestamp()
            )

            try {
                doc.set(wantedData).await()
                Log.d("WantedSave", "saved to wanted/: ${item.placeId}")
            } catch (e: Exception) {
                _likedIds.value = previousLiked
                _places.value = previousPlaces
                _favoriteMessages.tryEmit("찜 저장에 실패했어요. 잠시 후 다시 시도해 주세요.")
                Log.e("WantedSave", "wanted save failed (critical): placeId=${item.placeId}, msg=${e.message}", e)
                return@launch
            }

            val placesData = hashMapOf(
                "name" to item.name,
                "keywords" to validatedKeywords,
                "primaryType" to item.primaryType,
                "types" to (item.types ?: emptyList<String>()),
                "rating" to item.rating,
                "updatedAt" to System.currentTimeMillis()
            )

            try {
                db.collection("places")
                    .document(item.placeId)
                    .set(placesData, SetOptions.merge())
                    .await()
                Log.d("WantedSave", "synced to places/: ${item.placeId} keywords: $validatedKeywords")
            } catch (e: Exception) {
                Log.e("WantedSave", "places/ sync failed (non-critical): ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        favoritesListener?.remove()
        favoritesListener = null
        super.onCleared()
    }

    /** ♻️ 기존 찜의 키워드 일괄 재생성 (개발/관리 도구) */
    fun regenerateAllFavoriteKeywords(limit: Int = 50) {
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("users").document(uid).collection("wanted")
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w("RecommendationVM", "GEMINI_API_KEY blank -> skip regenerateAllFavoriteKeywords")
            return
        }
        viewModelScope.launch {
            try {
                val snap = col.get().await()
                val docs = snap.documents.take(limit)
                Log.d("RecommendationVM", "Regenerating keywords for ${docs.size} favorites…")
                for (d in docs) {
                    val name = d.getString("name").orEmpty()
                    if (name.isBlank()) continue
                    val address = d.getString("address")
                    val rating = d.getString("rating")
                    val types = (d.get("types") as? List<*>)?.mapNotNull { it as? String }
                    val primaryType = d.getString("primaryType")
                    val kws = try {
                        GeminiHelper.generateKeywords(apiKey, name, address, rating, types, primaryType)
                    } catch (e: Exception) {
                        Log.e("RecommendationVM", "Regenerate keywords failed for ${d.id}: ${e.message}")
                        emptyList()
                    }
                    if (kws.isNotEmpty()) {
                        d.reference.set(mapOf("keywords" to kws), SetOptions.merge()).await()
                        Log.d("RecommendationVM", "Updated ${d.id} keywords=${kws}")
                    }
                }
                Log.d("RecommendationVM", "Regeneration complete")
            } catch (e: Exception) {
                Log.e("RecommendationVM", "Regenerate batch failed: ${e.message}")
            }
        }
    }

    fun loadDefault() { loadNearbyPlaces(radius = 800) }

    /** 🔍 검색 수행 및 이력 저장 */
    fun onSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            loadNearbyPlaces()
            return
        }

        viewModelScope.launch {
            userPrefs.addSearchHistory(q)
            auth.currentUser?.uid?.let { uid ->
                // save to Firestore collection "search_history"
                try {
                    val payload = mapOf(
                        "userId" to uid,
                        "query" to q,
                        "timestamp" to Timestamp.now()
                    )
                    db.collection("search_history").add(payload).await()
                } catch (e: Exception) {
                    Log.w("RecommendationVM", "Failed to save search history: ${e.message}")
                }
                // reload recent list after adding
                loadSearchHistory(uid)
            }
        }
        loadNearbyPlaces(keyword = q)
    }

    // Load recent searches for the current user; optional uid param for faster follow-up
    private suspend fun loadSearchHistory(uidParam: String? = null) {
        val uid = uidParam ?: auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _searchHistory.value = emptyList()
            return
        }
        try {
            // Prefer server ordering by timestamp desc
            val snap = db.collection("search_history")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            val items = snap.documents.mapNotNull { it.getString("query") }.distinct()
            _searchHistory.value = items
        } catch (e: Exception) {
            Log.w("RecommendationVM", "Primary query failed, falling back: ${e.message}")
            // Fallback: fetch without order and sort client-side by timestamp field if present
            try {
                val snap = db.collection("search_history")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                val items = snap.documents
                    .mapNotNull { d ->
                        val q = d.getString("query") ?: return@mapNotNull null
                        val t = d.getTimestamp("timestamp")?.seconds ?: 0L
                        Pair(q, t)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .distinct()
                _searchHistory.value = items
            } catch (e2: Exception) {
                Log.w("RecommendationVM", "Fallback also failed: ${e2.message}")
                _searchHistory.value = emptyList()
            }
        }
    }

    // Public helper so UI can request a refresh without dealing with suspend functions
    fun refreshSearchHistory() {
        viewModelScope.launch { loadSearchHistory() }
    }

    // Delete a specific recent search entry for the current user (all matching docs)
    fun deleteSearchHistory(query: String) {
        val qTrim = query.trim()
        if (qTrim.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snap = db.collection("search_history")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("query", qTrim)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    try { doc.reference.delete().await() } catch (_: Exception) { /* best-effort */ }
                }
            } catch (e: Exception) {
                Log.w("RecommendationVM", "Failed deleting history '$qTrim': ${e.message}")
            } finally {
                loadSearchHistory(uid)
            }
        }
    }

    /** ❌ 특정 장소 숨기기 (싫어요) */
    fun markDislike(placeId: String) {
        viewModelScope.launch { userPrefs.addNegativePlaceId(placeId) }
        _places.value = _places.value.filterNot { it.placeId == placeId }
    }

    /** ⏰ 선택한 장소를 일정 빈 슬롯에 이벤트로 추가 */
    fun insertSelectedPlaceToSlotAsEvent(
        place: UiPlace,
        startMillis: Long,
        endMillis: Long,
        title: String = place.name,
        memo: String = "추천에서 선택한 장소"
    ) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Prefer preserved full object from click-time if same placeId
                val snap = _selectedPlaceSnapshot.value
                val selected = if (snap != null && snap.placeId == place.placeId) {
                    // merge to keep latest non-null fields
                    place.copy(
                        primaryType = place.primaryType ?: snap.primaryType,
                        types = place.types ?: snap.types,
                        photoName = place.photoName ?: snap.photoName,
                        imageUrl = place.imageUrl ?: snap.imageUrl
                    )
                } else {
                    place
                }

                Log.d(
                    "PLACE_FLOW_BEFORE_INSERT",
                    "name=${selected.name}, primaryType=${selected.primaryType}, types=${selected.types}"
                )

                val preservedTypes = selected.types ?: emptyList()
                val preservedPrimaryType = selected.primaryType

                val selectedPlace = mapOf(
                    "name" to selected.name,
                    "placeId" to selected.placeId,
                    "primaryType" to preservedPrimaryType,
                    "types" to preservedTypes,
                    "address" to selected.address,
                    "photoName" to selected.photoName,
                    "photoUrl" to selected.imageUrl
                )

                Log.d("SELECTED_PLACE_SAVE", "name=${selected.name}, primaryType=$preservedPrimaryType, types=$preservedTypes")
                Log.d("SCHEDULE_API", "before Firestore save selectedPlace=$selectedPlace")

                val event = hashMapOf(
                    "userId" to uid,
                    "title" to title,
                    "startTime" to Timestamp(startMillis / 1000, ((startMillis % 1000) * 1_000_000).toInt()),
                    "endTime" to Timestamp(endMillis / 1000, ((endMillis % 1000) * 1_000_000).toInt()),
                    "location" to selected.address, // 기존 이벤트 호환 형식 유지
                    "memo" to memo,
                    "createdAt" to Timestamp.now(),
                    "selectedPlace" to selectedPlace
                )

                db.collection("events").add(event).await()
                Log.d("SCHEDULE_API", "Firestore save success (events) placeId=${selected.placeId}")

                Log.d("SCHEDULE_API", "before backend learning call placeId=${selected.placeId}")
                sendTimePreferenceFeedback(
                    place = selected,
                    slotStart = startMillis,
                    slotEnd = endMillis
                )

                loadNearbyPlaces()
            } catch (e: Exception) {
                Log.e("SCHEDULE_API", "insertSelectedPlaceToSlotAsEvent failed: ${e.message}")
            }
        }
    }

    private fun sendTimePreferenceFeedback(
        place: UiPlace,
        slotStart: Long,
        slotEnd: Long
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val idToken = user.getIdToken(false).await().token
                    if (idToken.isNullOrBlank()) {
                        Log.w("LEARNING_API", "skip: empty Firebase ID token")
                        return@withContext
                    }

                    val bodyJson = JSONObject().apply {
                        put("placeId", place.placeId)
                        put("action", "like")
                        put(
                            "context", JSONObject().apply {
                                put("slotStart", slotStart)
                                put("slotEnd", slotEnd)
                                put("primaryType", place.primaryType ?: JSONObject.NULL)
                                put("types", JSONArray(place.types ?: emptyList<String>()))
                            }
                        )
                        put("scheduleId", JSONObject.NULL)
                    }.toString()

                    Log.d(
                        "LEARNING_API",
                        "POST /api/recommendation/feedback placeId=${place.placeId} primaryType=${place.primaryType} types=${place.types}"
                    )

                    val url = ServiceLocator.BASE_URL.trimEnd('/') + "/api/recommendation/feedback"
                    val client = ServiceLocator.httpClient
                    val mediaType = "application/json; charset=utf-8".toMediaType()

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $idToken")
                        .addHeader("Content-Type", "application/json")
                        .post(bodyJson.toRequestBody(mediaType))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val respBody = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            Log.d("LEARNING_API", "success placeId=${place.placeId}")
                        } else {
                            Log.e(
                                "LEARNING_API",
                                "failure code=${resp.code} body=${respBody.take(1200)} placeId=${place.placeId}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "LEARNING_API",
                    "failure type=${e::class.java.simpleName} message=${e.message}",
                    e
                )
            }
        }
    }

    /** 📅 일정 빈 슬롯에 맞춰 장소 재검색 */
    fun loadForFreeSlot(
        startMillis: Long,
        endMillis: Long,
        originLat: Double? = null,
        originLng: Double? = null,
        chip: String? = null,
        searchKeyword: String? = null,
        originAddressText: String? = null, // optional legacy string location for one-time geocode
        prevEventId: String? = null,
        prevEventTitle: String? = null,
        prevEventStartMillis: Long? = null,
        prevEventEndMillis: Long? = null,
        nextEventId: String? = null,
        nextEventTitle: String? = null,
        nextEventStartMillis: Long? = null,
        nextEventEndMillis: Long? = null
    ) {
        isSlotMode = true
        slotStartMillis = startMillis
        slotEndMillis = endMillis
        if (originLat != null && originLng != null) {
            slotOriginLat = originLat
            slotOriginLng = originLng
        }

        val incomingPrevEvent = prevEventTitle?.takeIf { it.isNotBlank() }?.let {
            CalendarEvent(
                id = prevEventId?.takeIf { id -> id.isNotBlank() } ?: "prev",
                title = it,
                startTime = prevEventStartMillis ?: startMillis,
                endTime = prevEventEndMillis ?: startMillis
            )
        }
        val incomingNextEvent = nextEventTitle?.takeIf { it.isNotBlank() }?.let {
            CalendarEvent(
                id = nextEventId?.takeIf { id -> id.isNotBlank() } ?: "next",
                title = it,
                startTime = nextEventStartMillis ?: endMillis,
                endTime = nextEventEndMillis ?: endMillis
            )
        }

        if (incomingPrevEvent != null || incomingNextEvent != null) {
            slotPreviousEvent = incomingPrevEvent
            slotNextEvent = incomingNextEvent
        }

        Log.d(
            "CAL_SLOT_CONTEXT",
            "stored prev=${slotPreviousEvent?.title} next=${slotNextEvent?.title} incomingPrev=${incomingPrevEvent?.title} incomingNext=${incomingNextEvent?.title}"
        )

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                Log.d("ViewModel", "=== loadForFreeSlot START ===")
                Log.d("ViewModel", "slotStart: $startMillis (${Date(startMillis)})")
                Log.d("ViewModel", "slotEnd: $endMillis (${Date(endMillis)})")
                Log.d("ViewModel", "Starting calendar context analysis...")

                val calendarContextDeferred = async(Dispatchers.IO) {
                    try {
                        withTimeout(15000L) {
                            val uid = resolveCurrentUserId().orEmpty()
                            if (uid.isBlank()) {
                                Log.w("ViewModel", "calendar context skipped: uid is blank")
                                return@withTimeout null
                            }

                            Log.d("CalendarContext", "using app slot context (Firestore events), CalendarReader bypassed for this flow")
                            val gap = buildSlotCalendarGap(startMillis, endMillis)
                            Log.d("ViewModel", "gap: prev=${gap.previousEvent?.title} next=${gap.nextEvent?.title}")
                            calendarContextAnalyzer.analyze(gap, uid)
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w("ViewModel", "calendar context timed out, proceeding without")
                        null
                    } catch (e: Exception) {
                        Log.e("ViewModel", "calendar context failed: ${e.message}")
                        null
                    }
                }

                Log.d(
                    "SLOT_DEBUG",
                    "isSlotMode=$isSlotMode slot=($slotStartMillis,$slotEndMillis) storedOrigin=($slotOriginLat,$slotOriginLng) incomingOrigin=($originLat,$originLng)"
                )

                var useLat = slotOriginLat
                var useLng = slotOriginLng

                // 1) legacy string-origin geocode path (explicit)
                if ((useLat == null || useLng == null) && !originAddressText.isNullOrBlank()) {
                    val geo = geocodeAddressOnce(originAddressText)
                    if (geo != null) {
                        useLat = geo.first
                        useLng = geo.second
                        slotOriginLat = useLat
                        slotOriginLng = useLng
                        Log.d("SLOT_ORIGIN_TRACE", "geocoded originAddressText='$originAddressText' -> ($useLat,$useLng)")
                    } else {
                        Log.w("SLOT_ORIGIN_TRACE", "failed geocode originAddressText='$originAddressText'")
                    }
                }

                // 2) optional keyword geocode only when origin still missing
                if ((useLat == null || useLng == null) && !searchKeyword.isNullOrBlank()) {
                    val geo = geocodeAddressOnce(searchKeyword)
                    if (geo != null) {
                        useLat = geo.first
                        useLng = geo.second
                        slotOriginLat = useLat
                        slotOriginLng = useLng
                        Log.d("SLOT_ORIGIN_TRACE", "geocoded searchKeyword='$searchKeyword' -> ($useLat,$useLng)")
                    }
                }

                // 3) explicit fallback only if truly unavailable
                if (useLat == null || useLng == null) {
                    Log.w("SLOT_ORIGIN_TRACE", "slot origin unavailable -> fallback Seoul (37.5665,126.9780)")
                    Toast.makeText(
                        getApplication(),
                        "슬롯 출발 위치를 확인하지 못해 기본 위치를 사용합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.d("SLOT_MODE", "Using slot origin, not device location")
                }

                val finalLat = useLat ?: 37.5665
                val finalLng = useLng ?: 126.9780

                val settingsCategories = userPrefs.selectedCategoriesFlow.first().ifEmpty { setOf("카페") }
                val timeOfDay = getSlotTimeOfDay(startMillis)
                val smartSettingsCategories = if (
                    timeOfDay == "NIGHT" && !settingsCategories.contains("맛집")
                ) {
                    settingsCategories + "맛집"
                } else {
                    settingsCategories
                }
                val finalCategories = when {
                    !chip.isNullOrBlank() -> listOf(chip)
                    !searchKeyword.isNullOrBlank() -> listOf(searchKeyword)
                    else -> smartSettingsCategories.toList()
                }

                Log.d(
                    "SLOT_MODE",
                    "timeOfDay=$timeOfDay settingsCategories=$settingsCategories chip=$chip finalCategories=$finalCategories"
                )
                Log.d("SLOT_ORIGIN_FINAL", "origin=($finalLat,$finalLng) slot=($startMillis,$endMillis)")

                val selectedTransports = transport.value.filter { it.value }.keys.toList()
                val transportValue = if (selectedTransports.contains("자차") && !selectedTransports.contains("도보")) "car" else "walk"
                val calendarContext = calendarContextDeferred.await().also {
                    Log.d("CalendarContext", "context attached=${it != null}")
                }

                val request = RecommendationForSlotRequest(
                    requestOrigin = LatLngBody(finalLat, finalLng),
                    slotStart = startMillis,
                    slotEnd = endMillis,
                    categories = finalCategories,
                    transport = transportValue,
                    calendarContext = calendarContext?.toRequestBody()
                )

                val body = RecommendationRequestBody(
                    origin = mapOf("lat" to request.requestOrigin.lat, "lng" to request.requestOrigin.lng),
                    slotStart = request.slotStart,
                    slotEnd = request.slotEnd,
                    categories = request.categories,
                    transport = request.transport,
                    calendarContext = request.calendarContext
                )

                Log.d("ViewModel", "calendarContext ready: ${request.calendarContext != null}")
                if (request.calendarContext != null) {
                    Log.d("ViewModel", "mood: ${request.calendarContext.mood}")
                    Log.d("ViewModel", "suggestedKeywords: ${request.calendarContext.suggestedKeywords}")
                    Log.d("ViewModel", "avoidKeywords: ${request.calendarContext.avoidKeywords}")
                } else {
                    Log.w("ViewModel", "calendarContext is NULL -> sending without context")
                }
                Log.d(
                    "ViewModel",
                    "sending to server: ${
                        if (request.calendarContext != null)
                            "mood=${request.calendarContext.mood} keywords=${request.calendarContext.suggestedKeywords}"
                        else "null"
                    }"
                )
                val requestJson = Gson().toJson(body)
                Log.d("API_REQUEST", "Full request JSON: $requestJson")

                val response = api.recommendForSlot(body)
                val items = response.data?.places.orEmpty()

                Log.d("API_RESPONSE", response.toString())
                Log.d("API_DATA_NULL", (response.data == null).toString())
                Log.d("API_PLACES_SIZE", response.data?.places?.size?.toString() ?: "null")

                val disliked = userPrefs.negativePlaceIdsFlow.first()

                val mapped = items.map { p ->
                    Log.d("ReasonSentence", "place=${p.name} sentence=${p.reasonSentence}")
                    val resolvedImageUrl = resolveBackendImageUrl(p.photoUrl)
                    Log.d("IMAGE_URL_RESOLVE", "raw=${p.photoUrl} resolved=$resolvedImageUrl path=recommendation")

                    UiPlace(
                        placeId = p.id ?: "${p.name}_${p.address ?: p.formattedAddress.orEmpty()}",
                        name = p.name,
                        address = p.address ?: p.formattedAddress.orEmpty(),
                        rating = p.rating?.toString() ?: "N/A",
                        photoName = p.photoName,
                        imageUrl = resolvedImageUrl,
                        isFavorite = false,
                        types = p.types ?: emptyList(),
                        primaryType = p.primaryType,
                        distanceM = p.distanceM,
                        travelTimeSec = p.travelTimeSec,
                        score = p.score,
                        openNow = p.openNow ?: p.openingHours?.openNow,
                        weekdayDescriptions = p.weekdayDescriptions ?: p.openingHours?.weekdayDescriptions,
                        reasonTags = p.reasonTags ?: emptyList(),
                        reasonSentence = p.reasonSentence?.takeIf { it.isNotBlank() }
                    )
                }.filterNot { disliked.contains(it.placeId) }

                // debug mapped
                mapped.take(3).forEachIndexed { idx, p ->
                    Log.d("RECO_TYPE_DEBUG_UI", "[$idx] name=${p.name}, primaryType=${p.primaryType}, types=${p.types}")
                }

                _places.value = mapped

                // trigger photo fetch for those with photoName but missing imageUrl
                _places.value.forEachIndexed { index, place ->
                    if (place.imageUrl == null && !place.photoName.isNullOrBlank()) {
                        fetchPhotoUrlIfNeeded(index)
                    }
                }
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.e("RecommendationVM", "for-slot HTTP ${e.code()} body=$body")
                _error.value = "추천 실패 (${e.code()}): ${body ?: e.message()}"
            } catch (e: Exception) {
                Log.e("RecommendationVM", "for-slot error", e)
                _error.value = e.message ?: "슬롯 추천 조회 실패"
            } finally {
                Log.d("ViewModel", "=== loadForFreeSlot END ===")
                _loading.value = false
            }
        }
    }

    private fun timeSlotBias(place: UiPlace): Int {
        val start = slotStartMillis ?: return 0
        val hour = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).hour
        val types = (place.types ?: emptyList()) + listOfNotNull(place.primaryType)
        return when {
            hour in 14..16 && types.any { it.contains("cafe", true) } -> 3
            hour >= 18 && types.any { it.contains("museum", true) || it.contains("shopping", true) } -> 3
            else -> 0
        }
    }

    private fun getSlotTimeOfDay(slotStartMillis: Long): String {
        val hour = Instant.ofEpochMilli(slotStartMillis).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 6..11 -> "MORNING"
            in 12..17 -> "AFTERNOON"
            in 18..22 -> "NIGHT"
            else -> "NIGHT"
        }
    }

    private fun resolveGoogleMapsApiKey(): String {
        return BuildConfig.MAPS_API_KEY
    }

    companion object {
        private val MASTER_KEYWORDS = setOf(
            "조용", "감성", "편안", "작업하기좋은", "대화하기좋은",
            "좌석많은", "채광좋은", "인테리어좋은", "데이트", "모임적합",
            "시끄러운", "혼잡한", "주차편리", "반려동물가능", "루프탑"
        )

        private fun resolveBackendImageUrl(rawUrl: String?): String? {
            val baseUrl = ServiceLocator.BASE_URL
            return when {
                rawUrl.isNullOrBlank() -> null
                rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                rawUrl.startsWith("/") -> baseUrl.trimEnd('/') + rawUrl
                else -> null
            }
        }
    }

    private fun historyBoost(place: UiPlace, historyFreq: Map<String, Int>): Int {
        val name = place.name.lowercase(Locale.getDefault())
        val types = ((place.types ?: emptyList()) + listOfNotNull(place.primaryType))
            .joinToString(" ")
            .lowercase(Locale.getDefault())
        var score = 0

        historyFreq.forEach { q: String, count: Int ->
            if (q.isBlank()) return@forEach
            if (name.contains(q) || types.contains(q)) {
                score += (1 + count)
            }
        }
        return score
    }

    data class TravelEstimateUi(
        val distanceText: String,
        val durationText: String,
        val durationSeconds: Int,
        val feasible: Boolean
    )

    suspend fun estimateTravel(
        originAddress: String,
        destinationAddress: String,
        availableGapSeconds: Long,
        byCar: Boolean
    ): TravelEstimateUi? {
        val origin = originAddress.trim()
        val dest = destinationAddress.trim()
        if (origin.isBlank() || dest.isBlank()) return null

        val mode = if (byCar) "driving" else "walking"
        val apiKey = resolveGoogleMapsApiKey()
        if (apiKey.isBlank()) return null

        return runCatching {
            val res = distanceMatrixApi.getDistanceMatrix(
                origins = origin,
                destinations = dest,
                mode = mode,
                language = "ko",
                apiKey = apiKey
            )
            val element = res.rows?.firstOrNull()?.elements?.firstOrNull()
            if (element?.status != "OK") return null

            val duration = (if (byCar) element.duration_in_traffic else null) ?: element.duration
            val distance = element.distance
            val durationSec = duration?.value ?: 0

            TravelEstimateUi(
                distanceText = distance.safeText(),
                durationText = duration.safeText(),
                durationSeconds = durationSec,
                feasible = durationSec.toLong() in 1L..availableGapSeconds.coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    private fun ValueText?.safeText(): String = this?.text?.takeIf { it.isNotBlank() } ?: "-"

    private fun buildSlotCalendarGap(startMillis: Long, endMillis: Long): CalendarGap {
        val gap = CalendarGap(
            previousEvent = slotPreviousEvent,
            nextEvent = slotNextEvent,
            gapStartTime = startMillis,
            gapEndTime = endMillis,
            gapDurationMinutes = ((endMillis - startMillis) / 60000L).toInt().coerceAtLeast(0)
        )

        Log.d("CalendarContext", "gap.previousEvent: ${gap.previousEvent?.title ?: "<null>"}")
        Log.d("CalendarContext", "gap.nextEvent: ${gap.nextEvent?.title ?: "<null>"}")
        Log.d("CalendarContext", "gap.durationMinutes: ${gap.gapDurationMinutes}")
        return gap
    }

    private suspend fun generateValidatedKeywords(place: UiPlace): List<String> {
        val geminiKeywords = try {
            GeminiHelper.generateKeywords(
                apiKey = BuildConfig.GEMINI_API_KEY,
                placeName = place.name,
                address = place.address,
                rating = place.rating,
                types = place.types,
                primaryType = place.primaryType
            )
        } catch (e: Exception) {
            Log.d("GeminiKeywords", "Gemini failed: ${e.message}")
            emptyList()
        }

        val validatedKeywords = geminiKeywords.filter { it in MASTER_KEYWORDS }.distinct()
        Log.d("GeminiKeywords", "raw: $geminiKeywords")
        Log.d("GeminiKeywords", "validated: $validatedKeywords")

        if (validatedKeywords.isNotEmpty()) return validatedKeywords

        val fallback = mapTypesToKeywords(place.types ?: emptyList())
        Log.d("GeminiKeywords", "using type fallback: $fallback")
        return fallback.filter { it in MASTER_KEYWORDS }.distinct()
    }

    private fun mapTypesToKeywords(types: List<String>): List<String> {
        val typeMap = mapOf(
            "cafe" to listOf("감성", "편안"),
            "coffee_shop" to listOf("조용", "작업하기좋은"),
            "bakery" to listOf("감성", "편안"),
            "restaurant" to listOf("대화하기좋은", "모임적합"),
            "bar" to listOf("모임적합", "대화하기좋은"),
            "park" to listOf("채광좋은", "편안")
        )

        return types
            .map { it.lowercase(Locale.getDefault()) }
            .flatMap { typeMap[it] ?: emptyList() }
            .distinct()
    }

    private fun refineKeywords(
        raw: List<String>,
        name: String,
        primaryType: String?,
        types: List<String>?,
        maxCount: Int = 12
    ): List<String> {
        val stopwords = setOf(
            "추천", "장소", "분위기", "좋은", "근처", "서울", "한국", "대한민국",
            "place", "best", "good", "near", "area", "spot"
        )

        fun normalizeToken(s: String): String {
            val n = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .lowercase(Locale.getDefault())
                .trim()
                .replace(Regex("[^\\p{L}\\p{N}\\s#]"), " ")
                .replace(Regex("\\s+"), " ")
            return n
        }

        val nameTokens = normalizeToken(name).split(" ").filter { it.length >= 2 }
        val typeTokens = ((types ?: emptyList()) + listOfNotNull(primaryType))
            .map { normalizeToken(it).replace("_", " ") }
            .flatMap { it.split(" ") }
            .filter { it.length >= 2 }

        val merged = (raw + nameTokens + typeTokens)
            .map(::normalizeToken)
            .flatMap { it.split(" ") }
            .map { it.removePrefix("#") }
            .filter { it.length >= 2 && it !in stopwords && !it.all(Char::isDigit) }

        // 단순 stem-like dedupe: 끝 조사/복수형 유사 정규화
        fun canonical(x: String): String =
            x.replace(Regex("(에서|으로|이고|한|들|들의|적인|스럽다|스럽)\\b"), "")

        val seen = LinkedHashSet<String>()
        val ordered = mutableListOf<String>()
        for (t in merged) {
            val c = canonical(t)
            if (c.isBlank()) continue
            if (seen.add(c)) ordered.add(c)
            if (ordered.size >= maxCount) break
        }
        return ordered
    }

    /** slot-mode control APIs */
    fun enterSlotMode(
        startMillis: Long,
        endMillis: Long,
        originLat: Double?,
        originLng: Double?
    ) {
        isSlotMode = true
        slotStartMillis = startMillis
        slotEndMillis = endMillis
        slotOriginLat = originLat
        slotOriginLng = originLng
        Log.d(
            "SLOT_MODE",
            "isSlotMode=$isSlotMode origin=($slotOriginLat,$slotOriginLng) slot=($slotStartMillis,$slotEndMillis) prev=${slotPreviousEvent?.title} next=${slotNextEvent?.title}"
        )
    }

    fun exitSlotMode() {
        isSlotMode = false
        slotStartMillis = null
        slotEndMillis = null
        slotOriginLat = null
        slotOriginLng = null
        slotPreviousEvent = null
        slotNextEvent = null
        Log.d(
            "SLOT_MODE",
            "isSlotMode=$isSlotMode origin=($slotOriginLat,$slotOriginLng) slot=($slotStartMillis,$slotEndMillis) prev=${slotPreviousEvent?.title} next=${slotNextEvent?.title}"
        )
    }

    fun isInSlotMode(): Boolean = isSlotMode
    fun currentSlotStart(): Long? = slotStartMillis
    fun currentSlotEnd(): Long? = slotEndMillis
    fun currentSlotOriginLat(): Double? = slotOriginLat
    fun currentSlotOriginLng(): Double? = slotOriginLng

    private suspend fun geocodeAddressOnce(raw: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(getApplication(), Locale.KOREA)
            val list = geocoder.getFromLocationName(raw, 1)
            val first = list?.firstOrNull() ?: return@runCatching null
            Pair(first.latitude, first.longitude)
        }.getOrNull()
    }


    private fun resolveCurrentUserId(): String? {
        return auth.currentUser?.uid ?: SessionManager.userId
    }

    private fun favoriteDocId(placeId: String): String {
        // Firestore doc id cannot contain '/'. Keep original placeId in field.
        return placeId.replace("/", "_")
    }
}
