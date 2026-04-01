// kotlin
package com.fillit.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fillit.app.R
import com.fillit.app.model.SessionManager
import com.fillit.app.model.UiPlace
import com.fillit.app.ui.screens.AddScheduleScreen
import com.fillit.app.ui.screens.OnboardingScreen
import com.fillit.app.ui.screens.PlaceDetailScreen
import com.fillit.app.ui.screens.RecommendationScreen
import com.fillit.app.ui.screens.RecommendationViewModel
import com.fillit.app.ui.screens.ScheduleViewModel
import com.fillit.app.ui.screens.ScheduleViewScreen
import com.fillit.app.ui.screens.SavedPlacesScreen
import com.fillit.app.ui.screens.SettingsScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavHost(
    navController: NavHostController,
    start: Route = Route.Onboarding
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    authResult.user?.uid?.let { userId ->
                        SessionManager.saveUserId(userId)
                    }
                    navController.navigate(Route.Schedule.name) {
                        popUpTo(Route.Onboarding.name) { inclusive = true }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context,
                        e.localizedMessage ?: "로그인에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(
                context,
                "구글 로그인 실패: ${e.statusCode}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "위치 권한이 허용되었습니다." else "위치 권한이 거부되었습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    NavHost(navController = navController, startDestination = start.name) {
        composable(Route.Onboarding.name) {
            OnboardingScreen(
                onGoogleLogin = { signInLauncher.launch(googleSignInClient.signInIntent) },
                onStart = { navController.navigate(Route.Schedule.name) },
                onAllowLocation = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        Toast.makeText(context, "이미 위치 권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            )
        }
        composable(Route.Schedule.name) {
            val scheduleViewModel: ScheduleViewModel = viewModel()
            val savedState = navController.currentBackStackEntry?.savedStateHandle
            val newEventSaved = savedState?.getLiveData<Boolean>("event_saved")?.value
            val updated = savedState?.getLiveData<Boolean>("event_updated")?.value
            val deleted = savedState?.getLiveData<Boolean>("event_deleted")?.value

            LaunchedEffect(newEventSaved, updated, deleted) {
                if (newEventSaved == true || updated == true || deleted == true) {
                    scheduleViewModel.refreshEvents()
                    savedState?.remove<Boolean>("event_saved")
                    savedState?.remove<Boolean>("event_updated")
                    savedState?.remove<Boolean>("event_deleted")
                }
            }

            val selectedDate = scheduleViewModel.uiState.collectAsState().value.selectedDate

            ScheduleViewScreen(
                current = Route.Schedule,
                onNavigate = { route -> navController.navigate(route.name) },
                onAddClick = {
                    val dateArg = selectedDate.format(dateFormatter)
                    navController.navigate("${Route.AddSchedule.name}?selectedDate=$dateArg")
                },
                onOpenRecommendationsForSlot = { startMillis, endMillis, originLat, originLng, originName ->
                    val encodedName = Uri.encode(originName ?: "")
                    navController.navigate(
                        "${Route.Recommendations.name}" +
                            "?startMillis=$startMillis" +
                            "&endMillis=$endMillis" +
                            "&originLat=${originLat ?: ""}" +
                            "&originLng=${originLng ?: ""}" +
                            "&originName=$encodedName"
                    )
                },
                scheduleViewModel = scheduleViewModel
            )
        }
        composable(
            route = "${Route.AddSchedule.name}?selectedDate={selectedDate}",
            arguments = listOf(
                navArgument("selectedDate") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val selectedDateArg = backStackEntry.arguments?.getString("selectedDate").orEmpty()
            val parsedDate = runCatching { LocalDate.parse(selectedDateArg, dateFormatter) }
                .getOrElse { LocalDate.now() }

            AddScheduleScreen(
                selectedDate = parsedDate,
                onBack = { navController.popBackStack() },
                onSave = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("event_saved", true)
                    navController.popBackStack()
                }
            )
        }
        composable(
            "${Route.Recommendations.name}?startMillis={startMillis}&endMillis={endMillis}&originLat={originLat}&originLng={originLng}&originName={originName}",
            arguments = listOf(
                navArgument("startMillis") { type = NavType.StringType; defaultValue = "" },
                navArgument("endMillis") { type = NavType.StringType; defaultValue = "" },
                navArgument("originLat") { type = NavType.StringType; defaultValue = "" },
                navArgument("originLng") { type = NavType.StringType; defaultValue = "" },
                navArgument("originName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val startArg = backStackEntry.arguments?.getString("startMillis").orEmpty()
            val endArg = backStackEntry.arguments?.getString("endMillis").orEmpty()
            val startMillis = startArg.toLongOrNull()
            val endMillis = endArg.toLongOrNull()
            val freeOriginLat = backStackEntry.arguments?.getString("originLat")?.toDoubleOrNull()
            val freeOriginLng = backStackEntry.arguments?.getString("originLng")?.toDoubleOrNull()
            val freeOriginName = backStackEntry.arguments?.getString("originName").orEmpty().ifBlank { null }

            val recommendationViewModel: RecommendationViewModel = viewModel(backStackEntry)
            RecommendationScreen(
                current = Route.Recommendations,
                onBack = { navController.popBackStack() },
                onFilter = { /* TODO */ },
                onNavigate = { r -> navController.navigate(r.name) },
                freeStartMillis = startMillis,
                freeEndMillis = endMillis,
                freeOriginLat = freeOriginLat,
                freeOriginLng = freeOriginLng,
                freeOriginName = freeOriginName,
                viewModel = recommendationViewModel,
                onPlaceClick = { p, s, e ->
                    navController.navigate(
                        PlaceDetailRoute.create(
                            placeId = p.placeId,
                            name = p.name,
                            address = p.address,
                            rating = p.rating,
                            imageUrl = p.imageUrl,
                            startMillis = s,
                            endMillis = e
                        )
                    )
                }
            )
        }

        composable(
            route = PlaceDetailRoute.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(PlaceDetailRoute.ARG_PLACE_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(PlaceDetailRoute.ARG_NAME) { type = NavType.StringType; defaultValue = "" },
                navArgument(PlaceDetailRoute.ARG_ADDRESS) { type = NavType.StringType; defaultValue = "" },
                navArgument(PlaceDetailRoute.ARG_RATING) { type = NavType.StringType; defaultValue = "N/A" },
                navArgument(PlaceDetailRoute.ARG_IMAGE_URL) { type = NavType.StringType; defaultValue = "" },
                navArgument(PlaceDetailRoute.ARG_START_MILLIS) { type = NavType.LongType; defaultValue = -1L },
                navArgument(PlaceDetailRoute.ARG_END_MILLIS) { type = NavType.LongType; defaultValue = -1L },
            )
        ) { entry ->
            val args = PlaceDetailRoute.parse(entry)

            // Reuse Recommendations back stack VM if possible
            // Use the current NavBackStackEntry as the remember key and the base route name
            val recOwner: ViewModelStoreOwner = remember(entry) {
                navController.getBackStackEntry(Route.Recommendations.name)
            }
            val vm: RecommendationViewModel = viewModel(recOwner)

            val place = UiPlace(
                placeId = args.placeId,
                name = args.name,
                address = args.address,
                rating = args.rating,
                photoName = null,
                imageUrl = args.imageUrl, // use parsed imageUrl (or null)
                isFavorite = false,
                types = null,
                primaryType = null
            )

            PlaceDetailScreen(
                place = place,
                onBack = { navController.popBackStack() },
                onInsertToSchedule = if (args.startMillis != null && args.endMillis != null) ({
                    vm.insertSelectedPlaceToSlotAsEvent(place, args.startMillis, args.endMillis)
                    navController.previousBackStackEntry?.savedStateHandle?.set("event_saved", true)
                    navController.popBackStack(Route.Schedule.name, false)
                }) else null
            )
        }

        composable(Route.SavedPlaces.name) {
            SavedPlacesScreen(
                current = Route.SavedPlaces,
                onBack = { navController.popBackStack() },
                onFilter = { /* TODO */ },
                onNavigate = { route -> navController.navigate(route.name) }
            )
        }
        composable(Route.Settings.name) {
            SettingsScreen(
                current = Route.Settings,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route.name) }
            )
        }
    }
}
