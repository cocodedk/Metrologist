package com.cocode.measureapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.core.dimensions.ReferenceField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists the reference stick length + width (meters) and the preferred display unit.
 * Setters refuse non-finite / non-positive dimensions; stored values are exposed raw (an
 * absent key takes the default, a present invalid value is never replaced) and validated
 * through [referenceDimensions], which flags which dimension needs correction.
 */
class SettingsRepository(private val context: Context) {
    private val lengthKey = doublePreferencesKey("stick_length_m")
    private val widthKey = doublePreferencesKey("stick_width_m")
    private val unitKey = stringPreferencesKey("length_unit")

    val stickLengthMeters: Flow<Double> = context.dataStore.data.map { it[lengthKey] ?: DEFAULT_LENGTH_M }
    val stickWidthMeters: Flow<Double> = context.dataStore.data.map { it[widthKey] ?: DEFAULT_WIDTH_M }

    /** Validated dimensions, or an explicit correction requirement for invalid stored values. */
    val referenceDimensions: Flow<ReferenceCheck> = context.dataStore.data.map {
        ReferenceDimensions.fromStored(it[lengthKey], it[widthKey], DEFAULT_LENGTH_M, DEFAULT_WIDTH_M)
    }

    val unit: Flow<LengthUnit> = context.dataStore.data.map { prefs ->
        runCatching { LengthUnit.valueOf(prefs[unitKey] ?: LengthUnit.METERS.name) }
            .getOrDefault(LengthUnit.METERS)
    }

    /** @throws IllegalArgumentException for a non-finite or non-positive [value]; nothing is written. */
    suspend fun setStickLengthMeters(value: Double) {
        val valid = ReferenceDimensions.requireValid(ReferenceField.LENGTH, value)
        context.dataStore.edit { it[lengthKey] = valid }
    }

    /** @throws IllegalArgumentException for a non-finite or non-positive [value]; nothing is written. */
    suspend fun setStickWidthMeters(value: Double) {
        val valid = ReferenceDimensions.requireValid(ReferenceField.WIDTH, value)
        context.dataStore.edit { it[widthKey] = valid }
    }

    suspend fun setUnit(unit: LengthUnit) {
        context.dataStore.edit { it[unitKey] = unit.name }
    }

    companion object {
        const val DEFAULT_LENGTH_M = 1.0
        const val DEFAULT_WIDTH_M = 0.04
    }
}
