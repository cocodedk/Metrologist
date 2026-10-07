package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextTest {
    // Caveat messages, one value per rule, so a rename of a message fails a test.
    private val lowConfidence = UiText(TextKey.CAVEAT_LOW_CONFIDENCE)
    private val scaleDisagree = UiText(TextKey.CAVEAT_STICK_SPACING)
    private val gravityFallback = UiText(TextKey.CAVEAT_TILT_SENSOR_METHOD)
    private val steepTilt = UiText(TextKey.CAVEAT_STEEP_CAMERA)

    /** A diagnostics object that triggers no caveats; override fields per test. */
    private fun clean(
        solver: SolverKind = SolverKind.RECTANGLE,
        confidence: Double = 0.9,
        cameraTiltDeg: Double = 0.0,
        scale: Double = 1.0,
        scaleAgreement: Double = 0.0,
    ) = MeasurementDiagnostics(solver, confidence, cameraTiltDeg, scale, scaleAgreement)

    // --- confidenceLabel boundaries ---
    @Test fun confidenceLabelHighAtBoundary() {
        assertEquals(UiText(TextKey.CONFIDENCE_HIGH), DiagnosticsText.confidenceLabel(0.7))
    }

    @Test fun confidenceLabelMediumAtBoundary() {
        assertEquals(UiText(TextKey.CONFIDENCE_MEDIUM), DiagnosticsText.confidenceLabel(0.4))
    }

    @Test fun confidenceLabelLowJustBelowMedium() {
        assertEquals(UiText(TextKey.CONFIDENCE_LOW), DiagnosticsText.confidenceLabel(0.39))
    }

    @Test fun confidenceLabelMediumJustBelowHigh() {
        assertEquals(UiText(TextKey.CONFIDENCE_MEDIUM), DiagnosticsText.confidenceLabel(0.69))
    }

    @Test fun confidenceLabelHighWellAbove() {
        assertEquals(UiText(TextKey.CONFIDENCE_HIGH), DiagnosticsText.confidenceLabel(1.0))
    }

    // --- caveats: low-confidence toggled ON/OFF independently ---
    @Test fun caveatLowConfidenceOn() {
        assertTrue(DiagnosticsText.caveats(clean(confidence = 0.39)).contains(lowConfidence))
    }

    @Test fun caveatLowConfidenceOffAtBoundary() {
        // 0.4 is NOT < 0.4, so the caveat must not fire.
        assertTrue(!DiagnosticsText.caveats(clean(confidence = 0.4)).contains(lowConfidence))
    }

    // --- caveats: scaleAgreement toggled ON/OFF independently ---
    @Test fun caveatScaleAgreementOn() {
        assertTrue(DiagnosticsText.caveats(clean(scaleAgreement = 0.11)).contains(scaleDisagree))
    }

    @Test fun caveatScaleAgreementOffAtBoundary() {
        // 0.1 is NOT > 0.1, so the caveat must not fire.
        assertTrue(!DiagnosticsText.caveats(clean(scaleAgreement = 0.1)).contains(scaleDisagree))
    }

    // --- caveats: gravity solver toggled ON/OFF independently ---
    @Test fun caveatGravityOn() {
        assertTrue(
            DiagnosticsText.caveats(clean(solver = SolverKind.GRAVITY)).contains(gravityFallback),
        )
    }

    @Test fun caveatGravityOffForRectangle() {
        assertTrue(
            !DiagnosticsText.caveats(clean(solver = SolverKind.RECTANGLE)).contains(gravityFallback),
        )
    }

    // --- caveats: camera tilt toggled ON/OFF independently (abs check, both signs) ---
    @Test fun caveatTiltOnPositive() {
        assertTrue(DiagnosticsText.caveats(clean(cameraTiltDeg = 60.1)).contains(steepTilt))
    }

    @Test fun caveatTiltOnNegative() {
        assertTrue(DiagnosticsText.caveats(clean(cameraTiltDeg = -60.1)).contains(steepTilt))
    }

    @Test fun caveatTiltOffAtBoundary() {
        // abs(60.0) is NOT > 60.0, so the caveat must not fire.
        assertTrue(!DiagnosticsText.caveats(clean(cameraTiltDeg = 60.0)).contains(steepTilt))
        assertTrue(!DiagnosticsText.caveats(clean(cameraTiltDeg = -60.0)).contains(steepTilt))
    }

    // --- clean object -> empty list ---
    @Test fun cleanDiagnosticsYieldsEmptyList() {
        assertEquals(emptyList<UiText>(), DiagnosticsText.caveats(clean()))
    }

    // --- worst case -> all four caveats ---
    @Test fun worstCaseYieldsAllFourCaveats() {
        val d = clean(
            solver = SolverKind.GRAVITY,
            confidence = 0.1,
            cameraTiltDeg = 89.0,
            scaleAgreement = 0.5,
        )
        assertEquals(
            listOf(lowConfidence, scaleDisagree, gravityFallback, steepTilt),
            DiagnosticsText.caveats(d),
        )
    }
}
