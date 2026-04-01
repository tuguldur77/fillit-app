@file:OptIn(ExperimentalMaterial3Api::class)
package com.fillit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fillit.app.navigation.Route
import com.fillit.app.ui.components.FillItBottomBar

@Composable
fun SavedPlacesScreen(
    current: Route,
    onBack: () -> Unit,
    onFilter: () -> Unit,
    onNavigate: (Route) -> Unit,
    viewModel: SavedPlacesViewModel = viewModel()
) {
    val favorites by viewModel.favorites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "찜목록",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF111827)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Reserved: filters for sorting later
                    IconButton(onClick = onFilter) {
                        Icon(Icons.Default.Delete, contentDescription = "Filter")
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
                    Text(
                        text = error ?: "오류",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                favorites.isEmpty() -> {
                    Text(
                        text = "저장된 찜이 없습니다.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(favorites) { index, place ->
                            LaunchedEffect(place.placeId) {
                                viewModel.fetchPhotoUrlIfNeeded(index)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = place.imageUrl ?: "https://via.placeholder.com/90",
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(Color(0xFFF3F4F6), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (place.address.isNotBlank()) {
                                            Text(place.address, color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Text("⭐ ${place.rating}", color = Color(0xFF6B7280), fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { viewModel.removeFavorite(place.placeId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
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
private fun PreviewSavedPlacesScreen() { /* removed preview to avoid preview-only imports */ }
