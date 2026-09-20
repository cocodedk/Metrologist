package com.cocode.measureapp.contracts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual repository and DataStore boundary, beyond the flow host's stand-in. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryContractTest {
    @Test fun C09_repositoryRejectsInvalidWritesWithoutLosingOtherStoredSettings() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repo = SettingsRepository(context)
            val length = repo.stickLengthMeters.first()
            val width = repo.stickWidthMeters.first()
            val unit = repo.unit.first()
            try {
                repo.setStickLengthMeters(0.123456789)
                repo.setStickWidthMeters(0.023456789)
                repo.setUnit(LengthUnit.FEET_INCHES)
                val expected = ReferenceCheck.Valid(0.123456789, 0.023456789)
                assertEquals(expected, repo.referenceDimensions.first())
                for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
                    for (setter in listOf(repo::setStickLengthMeters, repo::setStickWidthMeters)) {
                        try {
                            setter(bad)
                            fail("Repository accepted $bad")
                        } catch (_: IllegalArgumentException) {
                            // The real setter must reject before touching persisted preferences.
                        }
                        assertEquals(expected, repo.referenceDimensions.first())
                        assertEquals(LengthUnit.FEET_INCHES, repo.unit.first())
                    }
                }
                // A second repository observes the same unrounded, persisted values.
                assertEquals(expected, SettingsRepository(context).referenceDimensions.first())
            } finally {
                repo.setStickLengthMeters(length)
                repo.setStickWidthMeters(width)
                repo.setUnit(unit)
            }
        }
    }
}
