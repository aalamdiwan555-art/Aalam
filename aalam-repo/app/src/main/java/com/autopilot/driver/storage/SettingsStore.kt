package com.autopilot.driver.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.autopilotDataStore by preferencesDataStore("autopilot_settings")

data class StoredSettings(
    val minimumPrice: String = "100",
    val maximumPrice: String = "150",
    val showOverlay: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val minimumPrice = doublePreferencesKey("minimum_price")
        val maximumPrice = doublePreferencesKey("maximum_price")
        val showOverlay = booleanPreferencesKey("show_overlay")
    }

    val settings: Flow<StoredSettings> = context.autopilotDataStore.data.map { preferences ->
        StoredSettings(
            minimumPrice = preferences[Keys.minimumPrice]?.toString() ?: "100",
            maximumPrice = preferences[Keys.maximumPrice]?.toString() ?: "150",
            showOverlay = preferences[Keys.showOverlay] ?: false,
        )
    }

    suspend fun savePriceRange(minimum: Double, maximum: Double) {
        context.autopilotDataStore.edit {
            it[Keys.minimumPrice] = minimum
            it[Keys.maximumPrice] = maximum
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.autopilotDataStore.edit { it[Keys.showOverlay] = enabled }
    }
}