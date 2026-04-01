//package com.fillit.app.ui.screens
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.fillit.app.ui.theme.FillItTheme
//
//@Composable
//fun OnboardingScreen(onComplete: () -> Unit) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(MaterialTheme.colorScheme.background),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Spacer(Modifier.height(48.dp))
//        // Logo block
//        Box(
//            modifier = Modifier
//                .size(80.dp)
//                .clip(RoundedCornerShape(24.dp))
//                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
//            contentAlignment = Alignment.Center
//        ) {
//            Text("F", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
//        }
//        Spacer(Modifier.height(12.dp))
//        Text("FillIt", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
//        Spacer(Modifier.height(28.dp))
//        Text(
//            "일정 사이 빈 시간,\n나만의 추천",
//            style = MaterialTheme.typography.titleMedium,
//            textAlign = TextAlign.Center
//        )
//        Spacer(Modifier.height(32.dp))
//
//        // Feature cards (simplified)
//        ElevatedCard(modifier = Modifier
//            .padding(horizontal = 20.dp)
//            .fillMaxWidth()) {
//            Column(Modifier.padding(16.dp)) {
//                Text("스마트 일정 관리", fontWeight = FontWeight.SemiBold)
//                Text("캘린더와 연동해 빈 시간을 찾아줘요", style = MaterialTheme.typography.bodySmall)
//            }
//        }
//        Spacer(Modifier.height(12.dp))
//        ElevatedCard(modifier = Modifier
//            .padding(horizontal = 20.dp)
//            .fillMaxWidth()) {
//            Column(Modifier.padding(16.dp)) {
//                Text("맞춤형 장소 추천", fontWeight = FontWeight.SemiBold)
//                Text("현재 위치와 취향을 반영한 추천", style = MaterialTheme.typography.bodySmall)
//            }
//        }
//        Spacer(Modifier.weight(1f))
//        Button(
//            onClick = onComplete,
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(20.dp),
//            shape = RoundedCornerShape(12.dp)
//        ) {
//            Text("시작하기")
//        }
//        Spacer(Modifier.height(12.dp))
//        ElevatedCard(
//            modifier = Modifier
//                .padding(20.dp)
//                .fillMaxWidth()
//        ) {
//            Column(Modifier.padding(16.dp)) {
//                Text("위치 접근 권한", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
//                Text("근처 장소 추천을 위해 위치 정보가 필요해요", style = MaterialTheme.typography.bodySmall)
//                Spacer(Modifier.height(8.dp))
//                OutlinedButton(onClick = onComplete) { Text("권한 허용") }
//            }
//        }
//    }
//}
package com.fillit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fillit.app.R
import com.fillit.app.ui.theme.FillItTheme


@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun OnboardingScreen(
    onGoogleLogin: () -> Unit,
    onStart: () -> Unit,
    onAllowLocation: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFE3E7FF), Color(0xFFF0F2FE))
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo + Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF5B67EA), shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(24.dp))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clock),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Text(
                text = "FillIt",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            )
            Text(
                text = "일정 사이 빈 시간,\n나만의 추천",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF5B67EA)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Feature cards
        FeatureCard(
            icon = Icons.Default.DateRange,
            title = "스마트 일정 관리",
            subtitle = "빈 시간을 자동으로 찾아드려요"
        )
        Spacer(modifier = Modifier.height(16.dp))
        FeatureCard(
            icon = Icons.Default.LocationOn,
            title = "맞춤 장소 추천",
            subtitle = "시간과 위치에 맞는 곳을 추천해요"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google login button
        Button(
            onClick = onGoogleLogin,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Google로 로그인", color = Color.Gray, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Start button
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B67EA))
        ) {
            Text("시작하기", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Permission card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E2)),
            border = BorderStroke(1.dp, Color(0xFFFFD54F)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text("위치 접근 권한", fontWeight = FontWeight.Bold)
                        Text(
                            "근처 장소 추천을 위해 위치 정보가 필요해요",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onAllowLocation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("권한 허용", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureCard(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE0E7FF), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF5B67EA))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Preview
@Composable
private fun PreviewOnboardingScreen() {
    FillItTheme {
        OnboardingScreen(onGoogleLogin = {}, onStart = {}, onAllowLocation = {})
    }
    
}