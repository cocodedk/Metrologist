package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** C14: shared success/failure schema — failures carry no measurement, successes are finite. */
class MeasurementOutcomeContractTest {
    private val measurement = MeasurementResult(1.2, 0.8, 0.96, 1.442, listOf(90.0, 90.0, 90.0, 90.0))
    private val scale = ScaleResult(2.5, 0.01)

    private fun success(
        m: MeasurementResult = measurement,
        s: ScaleResult = scale,
        confidence: Double = 0.9,
    ) = MeasurementOutcome.Success(m, SolverKind.RECTANGLE, SurfaceOrientation.VERTICAL, s, confidence)

    @Test
    fun validSuccessIsUsableAndRetainsEvidence() {
        val diagnostics = MeasurementDiagnostics(SolverKind.GRAVITY, 0.7, -12.0, 2.5, 0.01)
        val outcome: MeasurementOutcome = MeasurementOutcome.Success(
            measurement, SolverKind.GRAVITY, SurfaceOrientation.HORIZONTAL, scale, 0.7, diagnostics,
        )
        assertTrue(outcome.usable)
        val s = outcome.successOrNull()!!
        assertSame(outcome, s)
        assertEquals(measurement, s.measurement)
        assertEquals(SolverKind.GRAVITY, s.solver)
        assertEquals(SurfaceOrientation.HORIZONTAL, s.orientation)
        assertEquals(scale, s.scale)
        assertEquals(0.7, s.confidence, 0.0)
        assertEquals(diagnostics, s.diagnostics)
    }

    @Test
    fun everyFailureReasonIsUnusableAndKeepsItsReason() {
        for (reason in MeasurementFailureReason.values()) {
            val outcome: MeasurementOutcome = MeasurementOutcome.Failure(reason, "detail for $reason")
            assertFalse(outcome.usable)
            assertNull(outcome.successOrNull())
            assertEquals(reason, (outcome as MeasurementOutcome.Failure).reason)
            assertEquals("detail for $reason", outcome.detail)
        }
    }

    @Test
    fun failureVocabularyCoversEveryCorrectionCategory() {
        assertEquals(
            setOf(
                "INVALID_OBJECT_CORNERS", "INVALID_STICK_CORNERS", "INVALID_REFERENCE_DIMENSIONS",
                "METADATA_UNAVAILABLE", "UNSUPPORTED_GEOMETRY", "NUMERICAL_FAILURE",
            ),
            MeasurementFailureReason.values().map { it.name }.toSet(),
        )
    }

    @Test
    fun zeroValuedMeasurementCannotBecomeSuccess() {
        // The legacy zeroed engine result must not be expressible as a success.
        val zeroed = MeasurementResult(0.0, 0.0, 0.0, 0.0, emptyList())
        assertThrows(IllegalArgumentException::class.java) { success(m = zeroed) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(width = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(height = -1.0)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(area = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(diagonal = 0.0)) }
    }

    @Test
    fun nonFiniteMeasurementCannotBecomeSuccess() {
        val nan = Double.NaN
        val inf = Double.POSITIVE_INFINITY
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(width = nan)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(height = inf)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(area = nan)) }
        assertThrows(IllegalArgumentException::class.java) { success(m = measurement.copy(diagonal = inf)) }
        assertThrows(IllegalArgumentException::class.java) {
            success(m = measurement.copy(cornerAngles = listOf(90.0, nan, 90.0, 90.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            success(m = measurement.copy(cornerAngles = listOf(90.0, 90.0, 90.0)))
        }
    }

    @Test
    fun invalidScaleCannotBecomeSuccess() {
        assertThrows(IllegalArgumentException::class.java) { success(s = ScaleResult(0.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { success(s = ScaleResult(Double.NaN, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { success(s = ScaleResult(1.0, Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { success(s = ScaleResult(1.0, -0.1)) }
    }

    @Test
    fun successConfidenceMustBeInUnitIntervalExcludingZero() {
        assertThrows(IllegalArgumentException::class.java) { success(confidence = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { success(confidence = -0.2) }
        assertThrows(IllegalArgumentException::class.java) { success(confidence = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { success(confidence = Double.NaN) }
        assertEquals(1.0, success(confidence = 1.0).confidence, 0.0)
    }
}
