package com.fillit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fillit.app.navigation.*
import com.fillit.app.ui.components.FillItBottomBar
import com.fillit.app.ui.theme.FillItTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FillItApp()
        }
    }
}

@Composable
fun FillItApp() {
    FillItTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRouteName = backStackEntry?.destination?.route
        val current = Route.values().find { it.name == currentRouteName?.substringBefore("?") } ?: Route.Onboarding
        Scaffold(
            bottomBar = {
                if (current != Route.Onboarding) {
                    FillItBottomBar(current = current) { route ->
                        navController.navigate(route.name) {
                            popUpTo(Route.Schedule.name) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }
        ) { inner ->
            AppNavHost(navController = navController, start = Route.Onboarding)
        }
    }
}
