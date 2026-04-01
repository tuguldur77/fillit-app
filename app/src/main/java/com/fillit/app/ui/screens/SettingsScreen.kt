@file:OptIn(ExperimentalMaterial3Api::class)

package com.fillit.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fillit.app.ServiceLocator
import com.fillit.app.navigation.Route
import com.fillit.app.ui.components.FillItBottomBar
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat

@Composable
fun SettingsScreen(
    current: Route,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(ServiceLocator.getUserPreferencesRepository(context)) }
    val categories by viewModel.categories.collectAsState()
    val transport by viewModel.transport.collectAsState()
    val price by viewModel.price.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val locationGranted by viewModel.locationPermissionGranted.collectAsState()

    val categoryUi = listOf(
        "카페" to "☕️ 카페",
        "전시" to "🎨 전시",
        "체험" to "🛠️ 체험",
        "관광지" to "🏞️ 관광지",
        "맛집" to "🍽️ 맛집",
        "쇼핑" to "🛍️ 쇼핑"
    )
    val transportUi = listOf(
        "도보" to "🚶 도보",
        "자차" to "🚗 자차"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val grantedNow = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setLocationPermissionGranted(grantedNow)
    }

    val user = remember { FirebaseAuth.getInstance().currentUser }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE3E7FF), Color(0xFFF0F2FE))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "설정",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                FillItBottomBar(current = current, onNavigate = onNavigate)
            }
        ) { inner ->
            Column(
                modifier = Modifier
                    .padding(inner)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==== Profile Card ====
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user?.photoUrl != null) {
                            AsyncImage(
                                model = user.photoUrl.toString(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(24.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFF5B67EA), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user?.displayName ?: "이름 없음", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF333333))
                            Text(user?.email ?: "이메일 없음", fontSize = 14.sp, color = Color(0xFF666666))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Google 계정",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD97706),
                                modifier = Modifier
                                    .background(Color(0xFFFFF3E2), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // ==== Category Section ====
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF5B67EA))
                        Spacer(Modifier.width(8.dp))
                        Text("관심 카테고리", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF333333))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "선호하는 장소 유형을 선택하세요",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    categoryUi.forEach { (key, label) ->
                        CategoryRow(
                            label = label,
                            checked = categories[key] ?: false,
                            onCheckedChange = { viewModel.updateCategory(key, it) }
                        )
                    }
                }

                // ==== Transport Section ====
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF5B67EA))
                        Spacer(Modifier.width(8.dp))
                        Text("이동 수단", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF333333))
                    }
                    Spacer(Modifier.height(12.dp))
                    transportUi.forEach { (key, label) ->
                        CategoryRow(
                            label = label,
                            checked = transport[key] ?: false,
                            onCheckedChange = { viewModel.updateTransport(key, it) }
                        )
                    }
                }

                // ==== Price Section ====
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = Color(0xFF5B67EA))
                        Spacer(Modifier.width(8.dp))
                        Text("선호 가격대", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF333333))
                    }
                    Spacer(Modifier.height(12.dp))
                    CategoryRow("무료", checked = price["무료"] ?: true, onCheckedChange = { viewModel.updatePrice("무료", it) })
                    CategoryRow("₩ 저가", checked = price["₩ 저가"] ?: true, onCheckedChange = { viewModel.updatePrice("₩ 저가", it) })
                    CategoryRow("₩₩ 보통", checked = price["₩₩ 보통"] ?: true, onCheckedChange = { viewModel.updatePrice("₩₩ 보통", it) })
                    CategoryRow("₩₩₩ 고가", checked = price["₩₩₩ 고가"] ?: false, onCheckedChange = { viewModel.updatePrice("₩₩₩ 고가", it) })
                }

                // ==== Notifications Section ====
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF5B67EA))
                        Spacer(Modifier.width(8.dp))
                        Text("알림 설정", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF333333))
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingRowDesc(
                        title = "장소 추천 알림",
                        subtitle = "빈 시간에 맞는 추천을 받으세요",
                        checked = notifications["장소 추천 알림"] ?: true,
                        onCheckedChange = { viewModel.updateNotification("장소 추천 알림", it) }
                    )
                    SettingRowDesc(
                        title = "일정 알림",
                        subtitle = "다가오는 일정을 미리 알려드려요",
                        checked = notifications["일정 알림"] ?: true,
                        onCheckedChange = { viewModel.updateNotification("일정 알림", it) }
                    )
                }
                // ==== Accent Permission Section (extra example) ====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E2))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFD97706))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("위치 접근 권한", fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                                Text(
                                    if (locationGranted) "권한 허용됨: 위치 기반 추천 사용 가능" else "근처 장소 추천을 위해 위치 정보가 필요해요",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text(
                                    if (locationGranted) "권한 다시 확인" else "권한 허용",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun CategoryRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF333333))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF5B67EA)
            )
        )
    }
}

@Composable
fun SettingRowDesc(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF666666))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF5B67EA)
            )
        )
    }
}

@Preview
@Composable
private fun PreviewSettingsScreen() {
    SettingsScreen(
        current = Route.Settings,
        onBack = {},
        onNavigate = {}
    )
}