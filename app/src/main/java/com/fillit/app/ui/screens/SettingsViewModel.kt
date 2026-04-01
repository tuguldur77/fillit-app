package com.fillit.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fillit.app.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    // Use one canonical key set across Settings/Recommendation/UserPreferences
    private val categoryKeys = listOf("카페", "전시", "체험", "관광지", "맛집", "쇼핑")
    private val transportKeys = listOf("도보", "자차")
    private val priceKeys = listOf("무료", "₩ 저가", "₩₩ 보통", "₩₩₩ 고가")

    val categories: StateFlow<Map<String, Boolean>> = userPreferences.selectedCategoriesFlow
        .map { selected ->
            categoryKeys.associateWith { key -> selected.contains(key) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), categoryKeys.associateWith { false })

    val transport: StateFlow<Map<String, Boolean>> = userPreferences.selectedTransportsFlow
        .map { selected ->
            transportKeys.associateWith { key -> selected.contains(key) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            mapOf("도보" to true, "자차" to false)
        )

    val price: StateFlow<Map<String, Boolean>> = userPreferences.selectedPricesFlow
        .map { selected ->
            priceKeys.associateWith { key -> selected.contains(key) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            mapOf("무료" to true, "₩ 저가" to true, "₩₩ 보통" to true, "₩₩₩ 고가" to false)
        )

    val notifications: StateFlow<Map<String, Boolean>> = combine(
        userPreferences.notifyRecommendationsFlow,
        userPreferences.notifyScheduleFlow
    ) { rec, sched ->
        mapOf(
            "장소 추천 알림" to rec,
            "일정 알림" to sched
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mapOf("장소 추천 알림" to true, "일정 알림" to true))

    val locationPermissionGranted: StateFlow<Boolean> = userPreferences.useDeviceLocationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun updateCategory(key: String, enabled: Boolean) {
        if (key !in categoryKeys) return
        viewModelScope.launch {
            userPreferences.setCategory(key, enabled)
        }
    }

    fun updateTransport(key: String, enabled: Boolean) {
        if (key !in transportKeys) return
        val current = transport.value
        val trueCount = current.values.count { it }
        if (!enabled && trueCount <= 1) return // keep at least one
        viewModelScope.launch {
            userPreferences.setTransport(key, enabled)
        }
    }

    fun updatePrice(key: String, enabled: Boolean) {
        if (key !in priceKeys) return
        viewModelScope.launch {
            userPreferences.setPrice(key, enabled)
        }
    }

    fun updateNotification(displayLabel: String, enabled: Boolean) {
        viewModelScope.launch {
            when (displayLabel) {
                "장소 추천 알림" -> userPreferences.setNotifyRecommendations(enabled)
                "일정 알림" -> userPreferences.setNotifySchedule(enabled)
            }
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        viewModelScope.launch {
            userPreferences.setUseDeviceLocation(granted)
        }
    }
}
