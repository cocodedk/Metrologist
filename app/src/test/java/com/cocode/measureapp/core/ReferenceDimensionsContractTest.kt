package com.cocode.measureapp.core

import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.core.dimensions.ReferenceField
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.ProfileValidation
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.stick.StickScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Contract N09 (C09 valid settings, C12 metric profile): reference dimensions are finite and
 * strictly positive at the text-input, storage and engine-profile boundaries.
 */
class ReferenceDimensionsContractTest {
    private val invalidTexts = listOf("NaN", "Infinity", "-Infinity", "1e309", "0", "-1", "", "  ", "abc")
    private val invalidMeters = listOf(
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0,
    )

    @Test fun textInputRejectsEveryInvalidValueInEveryUnit() {
        for (unit in LengthUnit.values()) for (text in invalidTexts) {
            assertNull("'$text' in $unit", LengthInput.parseToMeters(text, unit))
        }
    }

    @Test fun textInputRejectsPositiveDecimalThatUnderflowsToZeroMeters() {
        // Parses to a positive subnormal, but 4.9e-324 * 0.01 rounds to 0.0 m.
        assertTrue("1e-323".toDouble() > 0.0)
        assertNull(LengthInput.parseToMeters("1e-323", LengthUnit.CENTIMETERS))
        assertNull(LengthInput.parseToMeters("1e-330", LengthUnit.METERS))
    }

    @Test fun textInputAcceptsEquivalentValuesAcrossUnits() {
        assertEquals(1.0, LengthInput.parseToMeters("1", LengthUnit.METERS)!!, 1e-12)
        assertEquals(1.0, LengthInput.parseToMeters("100", LengthUnit.CENTIMETERS)!!, 1e-12)
        assertEquals(0.3048, LengthInput.parseToMeters("1", LengthUnit.FEET_INCHES)!!, 1e-12)
    }

    @Test fun storageWriteGuardRejectsInvalidAndPassesValidThrough() {
        for (field in ReferenceField.values()) {
            for (v in invalidMeters) {
                try {
                    ReferenceDimensions.requireValid(field, v)
                    fail("$field accepted $v")
                } catch (expected: IllegalArgumentException) {
                    assertTrue(expected.message!!.contains(if (field == ReferenceField.LENGTH) "length" else "width"))
                }
            }
            assertEquals(0.04, ReferenceDimensions.requireValid(field, 0.04), 0.0)
        }
    }

    @Test fun appWidthDoesNotAcceptEngineOnlyZero() {
        val check = ReferenceDimensions.check(1.0, 0.0)
        assertEquals(setOf(ReferenceField.WIDTH), (check as ReferenceCheck.NeedsCorrection).fields)
    }

    @Test fun invalidPersistedLengthIsFlaggedWithoutDefaultSubstitution() {
        for (bad in invalidMeters) {
            val check = ReferenceDimensions.fromStored(bad, 0.05, 1.0, 0.04)
            check as ReferenceCheck.NeedsCorrection
            assertEquals(setOf(ReferenceField.LENGTH), check.fields)
            assertEquals("raw value retained, not defaulted", bad, check.rawLengthMeters, 0.0)
            assertEquals("valid width preserved", 0.05, check.rawWidthMeters, 0.0)
            assertTrue(check.message.contains("length"))
            assertFalse(check.message.contains("width"))
        }
    }

    @Test fun invalidPersistedWidthIsFlaggedWithoutDefaultSubstitution() {
        for (bad in invalidMeters) {
            val check = ReferenceDimensions.fromStored(2.0, bad, 1.0, 0.04)
            check as ReferenceCheck.NeedsCorrection
            assertEquals(setOf(ReferenceField.WIDTH), check.fields)
            assertEquals("valid length preserved", 2.0, check.rawLengthMeters, 0.0)
            assertEquals(bad, check.rawWidthMeters, 0.0)
        }
    }

    @Test fun bothInvalidPersistedValuesAreBothNamed() {
        val check = ReferenceDimensions.fromStored(Double.NaN, -1.0, 1.0, 0.04)
        assertEquals(
            setOf(ReferenceField.LENGTH, ReferenceField.WIDTH),
            (check as ReferenceCheck.NeedsCorrection).fields,
        )
    }

    @Test fun absentPersistedValuesUseDocumentedDefaults() {
        assertEquals(ReferenceCheck.Valid(1.0, 0.04), ReferenceDimensions.fromStored(null, null, 1.0, 0.04))
        assertEquals(ReferenceCheck.Valid(2.0, 0.04), ReferenceDimensions.fromStored(2.0, null, 1.0, 0.04))
    }

    @Test fun correctedValueBecomesValid() {
        assertTrue(ReferenceDimensions.fromStored(Double.NaN, 0.04, 1.0, 0.04) is ReferenceCheck.NeedsCorrection)
        val fixed = LengthInput.parseToMeters("120", LengthUnit.CENTIMETERS)!!
        val valid = ReferenceDimensions.fromStored(fixed, 0.04, 1.0, 0.04) as ReferenceCheck.Valid
        assertEquals(1.2, valid.lengthMeters, 1e-12)
        assertEquals(0.04, valid.widthMeters, 0.0)
    }

    @Test fun engineProfileRejectsInvalidLengthWithExplicitOutcome() {
        for (bad in invalidMeters) {
            val v = StickProfile.validated(totalLength = bad, width = 0.04)
            v as ProfileValidation.Rejected
            assertEquals(MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS, v.failure.reason)
            assertFalse(v.failure.usable)
        }
    }

    @Test fun engineProfileRejectsNegativeNanAndInfiniteWidth() {
        for (bad in listOf(-1.0, -1e-9, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue("width $bad", StickProfile.validated(1.0, width = bad) is ProfileValidation.Rejected)
            try {
                StickProfile(1.0, width = bad)
                fail("constructor accepted width $bad")
            } catch (expected: IllegalArgumentException) {
                // rejected at the profile boundary
            }
        }
    }

    @Test fun engineOnlyZeroWidthLengthOnlyPathStillWorks() {
        val v = StickProfile.validated(totalLength = 1.0, width = 0.0) as ProfileValidation.Valid
        val res = StickScale.solve(box(2.0, 0.5), v.profile)
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test fun equivalentProfilesInDifferentUnitsGiveSameMetricScale() {
        val fromM = profile("1", "0.25", LengthUnit.METERS)
        val fromCm = profile("100", "25", LengthUnit.CENTIMETERS)
        val a = StickScale.solve(box(2.0, 0.5), fromM)
        val b = StickScale.solve(box(2.0, 0.5), fromCm)
        assertEquals(a.scale, b.scale, 1e-12)
        assertEquals(a.agreement, b.agreement, 1e-12)
        val fromFt = profile("1", "0.25", LengthUnit.FEET_INCHES)
        assertEquals(0.3048 * a.scale, StickScale.solve(box(2.0, 0.5), fromFt).scale, 1e-12)
    }

    @Test fun veryLargeFiniteValueIsValidInputNotAnException() {
        // Magnitude is not capped: 1e308 is finite and positive. Overflow in downstream scale /
        // measurement arithmetic is guarded by the C12 consumer (solver/outcome), not here.
        val meters = LengthInput.parseToMeters("1e308", LengthUnit.METERS)!!
        assertTrue(meters.isFinite())
        assertTrue(ReferenceDimensions.check(meters, meters) is ReferenceCheck.Valid)
        assertTrue(StickProfile.validated(meters, width = meters) is ProfileValidation.Valid)
        assertEquals(1e306, LengthInput.parseToMeters("1e308", LengthUnit.CENTIMETERS)!!, 1e294)
    }

    private fun profile(length: String, width: String, unit: LengthUnit): StickProfile {
        val l = LengthInput.parseToMeters(length, unit)!!
        val w = LengthInput.parseToMeters(width, unit)!!
        val check = ReferenceDimensions.check(l, w) as ReferenceCheck.Valid
        return (StickProfile.validated(check.lengthMeters, width = check.widthMeters) as ProfileValidation.Valid).profile
    }

    private fun box(length: Double, width: Double): List<Vec2> = listOf(
        Vec2(0.0, 0.0), Vec2(length, 0.0), Vec2(length, width), Vec2(0.0, width),
    )
}
