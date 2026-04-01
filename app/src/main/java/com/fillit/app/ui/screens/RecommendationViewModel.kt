package com.fillit.app.ui.screens

import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.fillit.app.data.remote.SearchPlacesApi
import com.fillit.app.data.remote.NearbyRequest
import com.fillit.app.data.remote.LocationRequest
import com.fillit.app.model.UiPlace
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.fillit.app.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import android.util.Log
import com.fillit.app.BuildConfig
import com.fillit.app.ai.GeminiHelper
import com.google.firebase.firestore.SetOptions
import com.fillit.app.data.remote.TextSearchRequest
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query
import com.fillit.app.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import retrofit2.Response
import java.time.Instant
import java.time.ZoneId
import com.fillit.app.data.SearchHistoryRepository
import com.fillit.app.data.remote.DistanceMatrixApi
import com.fillit.app.data.remote.ValueText
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import com.fillit.app.data.remote.RecommendationForSlotRequest
import com.fillit.app.data.remote.RecommendationOrigin
import com.fillit.app.data.remote.RecommendationPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val api: SearchPlacesApi = ServiceLocator.searchPlacesApi
    private val userPrefs = UserPreferencesRepository(getApplication())
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication())
    private val searchHistoryRepository = SearchHistoryRepository()

    private val distanceMatrixApi: DistanceMatrixApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
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

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds = _likedIds.asStateFlow()

    private var slotStartMillis: Long? = null
    private var slotEndMillis: Long? = null

    // Add these two missing properties (Double?) so slot origin is preserved and typed correctly
    private var slotOriginLat: Double? = null
    private var slotOriginLng: Double? = null

    // new: recent search history exposed to UI
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Preserve full clicked UiPlace to avoid partial reconstruction loss through nav args
    private val _selectedPlaceSnapshot = MutableStateFlow<UiPlace?>(null)
    val selectedPlaceSnapshot: StateFlow<UiPlace?> = _selectedPlaceSnapshot.asStateFlow()

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
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("wanted")
            .addSnapshotListener { snap, _ ->
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
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("users").document(uid).collection("wanted")
        val doc = col.document(item.placeId)

        if (_likedIds.value.contains(item.placeId)) {
            doc.delete().addOnFailureListener { e ->
                Log.e("RecommendationVM", "Failed to remove favorite: ${e.message}")
            }
        } else {
            val base = hashMapOf(
                "placeId" to item.placeId,
                "name" to item.name,
                "address" to item.address,
                "rating" to item.rating,
                "photoName" to item.photoName,
                "imageUrl" to item.imageUrl,
                "types" to (item.types ?: emptyList<String>()),
                "primaryType" to (item.primaryType ?: ""),
                // create keywords field immediately as empty list so it's visible in console
                "keywords" to emptyList<String>()
            )
            doc.set(base)
                .addOnSuccessListener {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isBlank()) return@addOnSuccessListener

                    viewModelScope.launch {
                        val rawGemini = try {
                            GeminiHelper.generateKeywords(
                                apiKey,
                                item.name,
                                item.address,
                                item.rating,
                                item.types,
                                item.primaryType
                            )
                        } catch (_: Exception) {
                            emptyList()
                        }

                        val refined = refineKeywords(
                            raw = rawGemini,
                            name = item.name,
                            primaryType = item.primaryType,
                            types = item.types
                        )

                        if (refined.isNotEmpty()) {
                            val data = mapOf(
                                "keywords" to refined,
                                "keywordSignals" to refined.mapIndexed { idx, k ->
                                    mapOf(
                                        "keyword" to k,
                                        "weight" to (refined.size - idx), // 앞쪽 키워드 가중치 높게
                                        "source" to "gemini+normalize"
                                    )
                                }
                            )
                            doc.set(data, SetOptions.merge())
                        }
                    }
                }
        }
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

                    val url = "http://10.0.2.2:3000/api/recommendation/feedback"
                    val client = OkHttpClient()
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
        chip: String? = null
    ) {
        // preserve slot context
        slotStartMillis = startMillis
        slotEndMillis = endMillis
        if (originLat != null && originLng != null) {
            slotOriginLat = originLat
            slotOriginLng = originLng
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                // Log current mode and slot context
                Log.d("RecommendationVM", "mode=slot slotStart=$startMillis slotEnd=$endMillis slotOrigin=(${slotOriginLat},${slotOriginLng})")

                // Determine origin to use: prefer preserved slot origin
                var useLat = slotOriginLat
                var useLng = slotOriginLng

                if (useLat == null || useLng == null) {
                    // If no slot origin provided, try device location as fallback but log warning
                    Log.w("RecommendationVM", "Slot origin not provided; falling back to device/location")
                    val useDeviceLocation = userPrefs.useDeviceLocationFlow.first()
                    if (useDeviceLocation && ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        runCatching { fusedLocationClient.lastLocation.await() }
                            .getOrNull()
                            ?.let { loc ->
                                useLat = loc.latitude
                                useLng = loc.longitude
                                Log.d("RecommendationVM", "Fallback device location used for slot: $useLat, $useLng")
                            }
                    }
                    if (useLat == null || useLng == null) {
                        // final fallback to Seoul center
                        useLat = 37.5665
                        useLng = 126.9780
                        Log.w("RecommendationVM", "No device location available; using default origin Seoul")
                    }
                }

                // categories: default from settings, override with chip if provided (chip is temporary)
                val settingsCategories = userPrefs.selectedCategoriesFlow.first().ifEmpty { setOf("카페") }
                Log.d("RecommendationVM", "settingsCategories=${settingsCategories}")

                val finalCategories = if (!chip.isNullOrBlank()) {
                    listOf(chip)
                } else {
                    settingsCategories.toList()
                }
                Log.d("RecommendationVM", "finalCategories(after chip)=$finalCategories chipParam=${chip ?: "<none>"}")

                val selectedTransports = transport.value.filter { it.value }.keys.toList()
                val transportValue = if (selectedTransports.contains("자차") && !selectedTransports.contains("도보")) "car" else "walk"
                val selectedTransportsForServer = if (transportValue == "car") listOf("자차") else listOf("도보")

                val request = RecommendationForSlotRequest(
                    origin = RecommendationOrigin(lat = useLat!!, lng = useLng!!),
                    slotStart = startMillis,
                    slotEnd = endMillis,
                    categories = finalCategories,
                    transport = transportValue,
                    selectedCategories = finalCategories,
                    selectedTransports = selectedTransportsForServer,
                    language = "ko",
                    region = "KR",
                    maxResults = 20
                )

                // Always use slot recommendation API for free-slot mode (do not call generic nearby flow)
                val response = api.recommendForSlot(request)
                val items = response.data?.places.orEmpty()

                Log.d("API_RESPONSE", response.toString())
                Log.d("API_DATA_NULL", (response.data == null).toString())
                Log.d("API_PLACES_SIZE", response.data?.places?.size?.toString() ?: "null")

                val disliked = userPrefs.negativePlaceIdsFlow.first()

                val mapped = items.map { p ->
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
                        reasonTags = p.reasonTags ?: emptyList()
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

    private fun resolveGoogleMapsApiKey(): String {
        return runCatching {
            val field = BuildConfig::class.java.getDeclaredField("GOOGLE_MAPS_API_KEY")
            field.isAccessible = true
            (field.get(null) as? String).orEmpty()
        }.getOrDefault("")
    }

    companion object {
        private fun resolveBackendImageUrl(rawUrl: String?): String? {
            return when {
                rawUrl.isNullOrBlank() -> null
                rawUrl.startsWith("http", ignoreCase = true) -> rawUrl
                rawUrl.startsWith("/") -> "http://10.0.2.2:3000$rawUrl"
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
}
