package com.fillit.app.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATA_STORE_NAME = "user_prefs"

val Context.userPreferencesDataStore by preferencesDataStore(name = DATA_STORE_NAME)

object UserPreferenceKeys {
    val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
    val SELECTED_CATEGORIES = stringSetPreferencesKey("selected_categories")
    val SELECTED_TRANSPORTS = stringSetPreferencesKey("selected_transports")
    val NOTIFY_RECOMMENDATIONS = booleanPreferencesKey("notify_recommendations")
    val NOTIFY_SCHEDULE = booleanPreferencesKey("notify_schedule")
    val SELECTED_PRICES = stringSetPreferencesKey("selected_prices")
    val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
    val NEGATIVE_PLACE_IDS = stringSetPreferencesKey("negative_place_ids")
}

class UserPreferencesRepository(private val context: Context) {
    val useDeviceLocationFlow: Flow<Boolean> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.USE_DEVICE_LOCATION] ?: true }

    val selectedCategoriesFlow: Flow<Set<String>> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.SELECTED_CATEGORIES] ?: emptySet() }

    val selectedTransportsFlow: Flow<Set<String>> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.SELECTED_TRANSPORTS] ?: emptySet() }

    val notifyRecommendationsFlow: Flow<Boolean> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.NOTIFY_RECOMMENDATIONS] ?: true }

    val notifyScheduleFlow: Flow<Boolean> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.NOTIFY_SCHEDULE] ?: true }

    val selectedPricesFlow: Flow<Set<String>> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.SELECTED_PRICES] ?: emptySet() }

    val searchHistoryFlow: Flow<Set<String>> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.SEARCH_HISTORY] ?: emptySet() }

    val searchHistoryOrderedFlow: Flow<List<String>> = searchHistoryFlow
        .map { set ->
            // StringSet is unordered. deterministic fallback for UI.
            set.toList().sortedByDescending { it.lowercase() }
        }

    val negativePlaceIdsFlow: Flow<Set<String>> = context.userPreferencesDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
        .map { prefs -> prefs[UserPreferenceKeys.NEGATIVE_PLACE_IDS] ?: emptySet() }

    suspend fun setUseDeviceLocation(value: Boolean) {
        context.userPreferencesDataStore.edit { it[UserPreferenceKeys.USE_DEVICE_LOCATION] = value }
    }

    suspend fun toggleCategory(category: String) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[UserPreferenceKeys.SELECTED_CATEGORIES] ?: emptySet()
            prefs[UserPreferenceKeys.SELECTED_CATEGORIES] = if (current.contains(category)) {
                current - category
            } else {
                current + category
            }
        }
    }

    suspend fun setCategory(category: String, enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[UserPreferenceKeys.SELECTED_CATEGORIES] ?: emptySet()
            prefs[UserPreferenceKeys.SELECTED_CATEGORIES] = if (enabled) current + category else current - category
        }
    }

    suspend fun toggleTransport(mode: String) {
        context.userPreferencesDataStore.edit { prefs ->
            val cur = prefs[UserPreferenceKeys.SELECTED_TRANSPORTS] ?: emptySet()
            prefs[UserPreferenceKeys.SELECTED_TRANSPORTS] = if (cur.contains(mode)) cur - mode else cur + mode
        }
    }

    suspend fun setTransport(mode: String, enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            val cur = prefs[UserPreferenceKeys.SELECTED_TRANSPORTS] ?: emptySet()
            prefs[UserPreferenceKeys.SELECTED_TRANSPORTS] = if (enabled) cur + mode else cur - mode
        }
    }

    suspend fun setNotifyRecommendations(value: Boolean) {
        context.userPreferencesDataStore.edit { it[UserPreferenceKeys.NOTIFY_RECOMMENDATIONS] = value }
    }

    suspend fun setNotifySchedule(value: Boolean) {
        context.userPreferencesDataStore.edit { it[UserPreferenceKeys.NOTIFY_SCHEDULE] = value }
    }

    suspend fun setPrice(level: String, enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            val cur = prefs[UserPreferenceKeys.SELECTED_PRICES] ?: emptySet()
            prefs[UserPreferenceKeys.SELECTED_PRICES] = if (enabled) cur + level else cur - level
        }
    }

    suspend fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        context.userPreferencesDataStore.edit { prefs ->
            val cur = prefs[UserPreferenceKeys.SEARCH_HISTORY]?.toList().orEmpty()
            val next = (cur.filterNot { it.equals(q, ignoreCase = true) } + q).takeLast(30)
            prefs[UserPreferenceKeys.SEARCH_HISTORY] = next.toSet()
        }
    }

    suspend fun clearSearchHistory() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[UserPreferenceKeys.SEARCH_HISTORY] = emptySet()
        }
    }

    suspend fun addNegativePlaceId(placeId: String) {
        if (placeId.isBlank()) return
        context.userPreferencesDataStore.edit { prefs ->
            val cur = prefs[UserPreferenceKeys.NEGATIVE_PLACE_IDS] ?: emptySet()
            prefs[UserPreferenceKeys.NEGATIVE_PLACE_IDS] = cur + placeId
        }
    }
}
