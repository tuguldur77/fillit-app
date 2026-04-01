package com.fillit.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fillit.app.navigation.Route

data class BottomItem(val route: Route, val label: String, val icon: ImageVector)

@Composable
fun FillItBottomBar(
    current: Route,
    onNavigate: (Route) -> Unit
) {
    val items = listOf(
        BottomItem(Route.Schedule, "내 일정", Icons.Filled.Home),
        BottomItem(Route.Recommendations, "추천", Icons.Filled.Star),
        BottomItem(Route.SavedPlaces, "찜목록", Icons.Filled.Favorite),
        BottomItem(Route.Settings, "설정", Icons.Filled.Settings),
    )

    NavigationBar(
        containerColor = Color.White, // white background
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            val selected = current == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF5B67EA),   // primary purple
                    selectedTextColor = Color(0xFF5B67EA),
                    unselectedIconColor = Color(0xFF666666), // subtext-light
                    unselectedTextColor = Color(0xFF666666),
                    indicatorColor = Color(0xFFE3E7FF)       // light purple highlight
                )
            )
        }
    }
}
