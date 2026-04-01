@file:OptIn(ExperimentalMaterial3Api::class)

package com.fillit.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fillit.app.model.UiPlace
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun PlaceDetailScreen(
    place: UiPlace,
    onBack: () -> Unit,
    onInsertToSchedule: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // TEMP DEBUG: verify place payload received by detail screen
    android.util.Log.d(
        "PLACE_FLOW_DETAIL_RECEIVE",
        "name=${place.name}, primaryType=${place.primaryType}, types=${place.types}"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AsyncImage(
                        model = place.imageUrl ?: "https://via.placeholder.com/300",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        place.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        place.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "평점: ${place.rating}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // NEW: 영업 상태
                    val openStatus = when (place.openNow) {
                        true -> "영업 중"
                        false -> "영업 종료"
                        null -> null
                    }
                    if (openStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = openStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (place.openNow == true) Color(0xFF059669) else Color(0xFF9CA3AF)
                        )
                    }

                    // 운영 시간(요일별) 블록은 이미 구현되어 있으므로 그대로 사용
                    val hours = place.weekdayDescriptions.orEmpty().filter { it.isNotBlank() }
                    if (hours.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "운영 시간",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        hours.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 버튼들: 네이버 지도 열기, 주소 복사, (선택) 일정 추가
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openInNaverMap(context, place.address) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A0FF))
                ) {
                    Text("네이버 지도에서 열기", color = Color.White)
                }

                Button(
                    onClick = { copyAddress(context, place.address) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("주소 복사")
                }
            }

            if (onInsertToSchedule != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onInsertToSchedule()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("이 빈 시간에 일정 추가")
                }
            }
        }
    }
}

private fun openInNaverMap(context: Context, address: String) {
    val encoded = Uri.encode(address)
    // Prefer Naver Map native scheme, fallback to web search
    val naverUri = Uri.parse("nmap://search?query=$encoded")
    val webUri = Uri.parse("https://map.naver.com/v5/search/$encoded")
    val intent = Intent(Intent.ACTION_VIEW, naverUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // fallback to web
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("address", address)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "주소가 복사되었습니다.", Toast.LENGTH_SHORT).show()
}

@Preview
@Composable
private fun PreviewPlaceDetailScreen() {
    val samplePlace = UiPlace(
        placeId = "sample_place_id",
        name = "성수동 카페",
        address = "서울특별시 성동구 성수이로 113",
        rating = "4.5",
        photoName = null,
        imageUrl = null,
        isFavorite = false,
        types = listOf("cafe"),
        primaryType = "cafe"
    )
    PlaceDetailScreen(place = samplePlace, onBack = {})
}