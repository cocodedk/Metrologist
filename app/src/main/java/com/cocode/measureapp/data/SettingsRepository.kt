package com.cocode.measureapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cocode.measureapp.core.LengthUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists the reference stick length + width (meters) and the preferred display unit. */
class SettingsRepository(private val context: Context) {
    private val lengthKey = doublePreferencesKey("stick_length_m")
    private val widthKey = doublePreferencesKey("stick_width_m")
    private val unitKey = stringPreferencesKey("length_unit")

    val stickLengthMeters: Flow<Double> = context.dataStore.data.map { it[lengthKey] ?: DEFAULT_LENGTH_M }
    val stickWidthMeters: Flow<Double> = context.dataStore.data.map { it[widthKey] ?: DEFAULT_WIDTH_M }
    val unit: Flow<LengthUnit> = context.dataStore.data.map { prefs ->
        runCatching { LengthUnit.valueOf(prefs[unitKey] ?: LengthUnit.METERS.name) }
            .getOrDefault(LengthUnit.METERS)
    }

    suspend fun setStickLengthMeters(value: Double) {
        context.dataStore.edit { it[lengthKey] = value }
    }

    suspend fun setStickWidthMeters(value: Double) {
        context.dataStore.edit { it[widthKey] = value }
    }

    suspend fun setUnit(unit: LengthUnit) {
        context.dataStore.edit { it[unitKey] = unit.name }
    }

    companion object {
        const val DEFAULT_LENGTH_M = 1.0
        const val DEFAULT_WIDTH_M = 0.04
    }
}
