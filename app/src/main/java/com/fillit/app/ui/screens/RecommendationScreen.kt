@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fillit.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import com.fillit.app.R
import com.fillit.app.navigation.Route
import com.fillit.app.ui.components.FillItBottomBar
import com.fillit.app.model.UiPlace
import com.fillit.app.BuildConfig
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.BrokenImage
import coil.compose.AsyncImage
import com.fillit.app.preferences.UserPreferencesRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun RecommendationScreen(
    current: Route,
    onBack: () -> Unit,
    onFilter: () -> Unit,
    onNavigate: (Route) -> Unit,
    freeStartMillis: Long? = null,
    freeEndMillis: Long? = null,
    freeOriginLat: Double? = null,    // NEW optional origin params
    freeOriginLng: Double? = null,    // NEW optional origin params
    freeOriginName: String? = null,   // NEW
    viewModel: RecommendationViewModel,
    onPlaceClick: (UiPlace, Long?, Long?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current

    LaunchedEffect(freeStartMillis to freeEndMillis to freeOriginLat to freeOriginLng to freeOriginName) {
        if (freeStartMillis != null && freeEndMillis != null) {
            Log.d(
                "SLOT_ORIGIN_TRACE",
                "enter free-slot mode freeOrigin=($freeOriginLat,$freeOriginLng) name=$freeOriginName slot=($freeStartMillis,$freeEndMillis)"
            )
            if (freeOriginLat == null || freeOriginLng == null) {
                Log.w("SLOT_ORIGIN_TRACE", "free-slot origin missing from calendar flow; geocode/fallback path will be used")
            }

            viewModel.enterSlotMode(
                startMillis = freeStartMillis,
                endMillis = freeEndMillis,
                originLat = freeOriginLat,
                originLng = freeOriginLng
            )
            viewModel.loadForFreeSlot(
                startMillis = freeStartMillis,
                endMillis = freeEndMillis,
                originLat = freeOriginLat,
                originLng = freeOriginLng,
                originAddressText = freeOriginName // NEW: geocode fallback source
            )
        } else {
            viewModel.exitSlotMode()
            viewModel.loadNearbyPlaces(radius = 800)
        }
        // refresh recent searches when screen shows
        viewModel.refreshSearchHistory()
    }

    val places by viewModel.places.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    val recentSearches by viewModel.searchHistory.collectAsState()

    // Search UI state (local). We now call server-side search to use chip/type mapping.
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) } // NEW: show recents only while typing / focused
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "빈 시간 추천",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF111827)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = onFilter) {
                        Icon(Icons.Default.Edit, contentDescription = "필터")
                    }
                    if (BuildConfig.DEBUG) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("키워드 다시 생성") },
                                onClick = {
                                    showMenu = false
                                    viewModel.regenerateAllFavoriteKeywords()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = { FillItBottomBar(current = current, onNavigate = onNavigate) }
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF3F4F6), Color.White)
                    )
                )
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF6D28D9)
                    )
                }

                error != null -> {
                    // Show search even when error so user can retry
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        SearchBarRow(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {
                                val kw = searchQuery.trim().takeIf { it.isNotBlank() }
                                val slotActive = viewModel.isInSlotMode() &&
                                    viewModel.currentSlotStart() != null &&
                                    viewModel.currentSlotEnd() != null

                                if (slotActive) {
                                    viewModel.loadForFreeSlot(
                                        startMillis = viewModel.currentSlotStart()!!,
                                        endMillis = viewModel.currentSlotEnd()!!,
                                        originLat = viewModel.currentSlotOriginLat(),
                                        originLng = viewModel.currentSlotOriginLng(),
                                        searchKeyword = kw
                                    )
                                } else {
                                    if (kw == null) viewModel.loadNearbyPlaces() else viewModel.onSearch(kw)
                                }
                                focusManager.clearFocus()
                            },
                            onClear = { searchQuery = "" }
                        )
                        Spacer(Modifier.height(12.dp))
                        SuggestionChips(onPick = { picked ->
                            searchQuery = picked
                            val slotActive = viewModel.isInSlotMode() &&
                                viewModel.currentSlotStart() != null &&
                                viewModel.currentSlotEnd() != null

                            if (slotActive) {
                                viewModel.loadForFreeSlot(
                                    startMillis = viewModel.currentSlotStart()!!,
                                    endMillis = viewModel.currentSlotEnd()!!,
                                    originLat = viewModel.currentSlotOriginLat(),
                                    originLng = viewModel.currentSlotOriginLng(),
                                    chip = picked
                                )
                            } else {
                                viewModel.loadNearbyPlaces(keyword = picked)
                            }
                            focusManager.clearFocus()
                        })
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "오류 발생: $error",
                            color = Color.Red,
                        )
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        // Search field + actions
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            SearchBarRow(
                                query = searchQuery,
                                onQueryChange = {
                                    searchQuery = it
                                    // keep active while user types
                                    isSearchActive = true
                                },
                                onSearch = {
                                    val kw = searchQuery.trim().takeIf { it.isNotBlank() }
                                    val slotActive = viewModel.isInSlotMode() &&
                                        viewModel.currentSlotStart() != null &&
                                        viewModel.currentSlotEnd() != null

                                    if (slotActive) {
                                        viewModel.loadForFreeSlot(
                                            startMillis = viewModel.currentSlotStart()!!,
                                            endMillis = viewModel.currentSlotEnd()!!,
                                            originLat = viewModel.currentSlotOriginLat(),
                                            originLng = viewModel.currentSlotOriginLng(),
                                            searchKeyword = kw
                                        )
                                    } else {
                                        if (kw == null) viewModel.loadNearbyPlaces() else viewModel.onSearch(kw)
                                    }
                                    focusManager.clearFocus()
                                    isSearchActive = false // hide recents after submit
                                },
                                onClear = {
                                    searchQuery = ""
                                    isSearchActive = false // hide recents on clear
                                },
                                onFocusChange = { focused ->
                                    isSearchActive = focused
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // NEW: Recent searches list — only visible while user focused/typing
                        if (recentSearches.isNotEmpty() && isSearchActive) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Text("최근 검색어", fontWeight = FontWeight.SemiBold, color = Color(0xFF333333))
                                Spacer(Modifier.height(6.dp))
                                // show items line-by-line with delete button
                                recentSearches.forEach { q ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = q,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    searchQuery = q
                                                    viewModel.onSearch(q)
                                                    focusManager.clearFocus()
                                                    isSearchActive = false
                                                },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color(0xFF333333)
                                        )
                                        IconButton(onClick = { viewModel.deleteSearchHistory(q) }) {
                                            Icon(Icons.Default.Close, contentDescription = "삭제", tint = Color.Gray)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        // existing suggestion chips
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            SuggestionChips(onPick = { picked ->
                                searchQuery = picked
                                val slotActive = viewModel.isInSlotMode() &&
                                    viewModel.currentSlotStart() != null &&
                                    viewModel.currentSlotEnd() != null

                                if (slotActive) {
                                    viewModel.loadForFreeSlot(
                                        startMillis = viewModel.currentSlotStart()!!,
                                        endMillis = viewModel.currentSlotEnd()!!,
                                        originLat = viewModel.currentSlotOriginLat(),
                                        originLng = viewModel.currentSlotOriginLng(),
                                        chip = picked
                                    )
                                } else {
                                    viewModel.loadNearbyPlaces(keyword = null, chip = picked)
                                }
                                focusManager.clearFocus()
                            })
                        }
                        Spacer(Modifier.height(8.dp))

                        if (places.isEmpty()) {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    if (searchQuery.isBlank()) "추천할 장소가 없습니다." else "검색 결과가 없습니다.",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(places) { index, p ->
                                    LaunchedEffect(p.placeId) {
                                        viewModel.fetchPhotoUrlIfNeeded(index)
                                    }

                                    RecommendationCard(
                                        place = p,
                                        isFavorite = likedIds.contains(p.placeId),
                                        onToggleFavorite = { viewModel.toggleFavorite(p) },
                                        onClick = {
                                            viewModel.setSelectedPlaceForFlow(p) // preserve full UiPlace
                                            Log.d(
                                                "PLACE_FLOW_CLICK",
                                                "name=${p.name}, primaryType=${p.primaryType}, types=${p.types}"
                                            )
                                            onPlaceClick(p, freeStartMillis, freeEndMillis)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {}
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) },
        singleLine = true,
        placeholder = { Text("이름 또는 유형으로 검색 (예: 카페, 박물관)") },
        trailingIcon = {
            Row {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "지우기") }
                }
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "검색") }
            }
        },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun SuggestionChips(onPick: (String) -> Unit) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val selectedCategories by userPrefs.selectedCategoriesFlow.collectAsState(initial = emptySet())
    val suggestions = selectedCategories.toList()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { label ->
            AssistChip(
                onClick = { onPick(label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun RecommendationCard(
    place: UiPlace,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Log.d("CARD_IMAGE_DEBUG", "name=${place.name}, imageUrl=${place.imageUrl}")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color(0xFFF3F4F6), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                ) {
                    if (!place.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = place.imageUrl,
                            contentDescription = place.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Default.Search),
                            error = rememberVectorPainter(Icons.Default.BrokenImage),
                            onSuccess = {
                                Log.d("COIL_IMAGE", "success url=${place.imageUrl}")
                            },
                            onError = { state ->
                                Log.e(
                                    "COIL_IMAGE",
                                    "error url=${place.imageUrl}, throwable=${state.result.throwable}"
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                        )
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "찜",
                        tint = if (isFavorite) Color.Red else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(place.address, fontSize = 13.sp, color = Color.Gray)
            Text("⭐ ${place.rating}", fontSize = 13.sp, color = Color(0xFF6B7280))

            // NEW: Distance and travel time
            val distanceKm = (place.distanceM ?: 0.0) / 1000.0
            val travelMin = (place.travelTimeSec ?: 0) / 60
            if (place.distanceM != null || place.travelTimeSec != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "거리: ${"%.1f".format(distanceKm)} km · 이동시간: ${travelMin}분",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }

            // NEW: opening status + one short opening-hours line
            val openStatus = when (place.openNow) {
                true -> "영업 중"
                false -> "영업 종료"
                null -> null
            }
            val openingLine = place.weekdayDescriptions
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }

            if (openStatus != null || openingLine != null) {
                Spacer(Modifier.height(4.dp))
                openStatus?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = if (place.openNow == true) Color(0xFF059669) else Color(0xFF9CA3AF)
                    )
                }
                openingLine?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // NEW: Reason tags
            val reasonTags = place.reasonTags.orEmpty().filter { it.isNotBlank() }.take(2)
            if (reasonTags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reasonTags.forEach { tag ->
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                            color = Color(0xFFF3F4F6)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }
        }
    }
}

// No change needed for calendar date navigation.
