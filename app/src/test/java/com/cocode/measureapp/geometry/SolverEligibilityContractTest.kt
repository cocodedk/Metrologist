package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.eligibility.OutcomeCases
import com.cocode.measureapp.geometry.eligibility.ShapeCases
import com.cocode.measureapp.geometry.eligibility.ToleranceCases
import org.junit.Test

/**
 * Node 03 acceptance checks and contract C10 (measurement outcome). Each test runs one
 * acceptance case end to end through [MetrologyEngine.evaluate] and the eligibility assessors;
 * the case bodies and independent fixtures live in `geometry/eligibility` (test sources).
 *
 * These are synthetic pinhole fixtures: they do not prove physical-camera behaviour.
 */
class SolverEligibilityContractTest {
    // --- Nonrectangle fixture (world corners (-1,-0.5) (1,-0.5) (1.4,0.5) (-1,0.6), 2.32 m²) ---

    @Test fun nonRectangleWallRejectsRectangleAndGravityMatchesIndependentTruth() =
        ShapeCases.nonRectangleWallWithLevelGravity()

    @Test fun nonRectangleWithoutGravityReportsTheMissingAssumption() =
        ShapeCases.nonRectangleWithoutGravity()

    @Test fun nonRectangleOnHorizontalPlaneKeepsItsGenuineShape() =
        ShapeCases.nonRectangleOnHorizontalPlane()

    @Test fun wallWithUnknownAzimuthNeverClaimsHighConfidence() =
        ShapeCases.wallWithUnknownAzimuth()

    // --- Valid rectangles and documented thresholds ---

    @Test fun validRectanglesIncludingInfinityCasesRemainEligible() =
        ToleranceCases.validRectanglesStayEligible()

    @Test fun orthogonalityAndStickThresholdsHoldOnBothSides() =
        ToleranceCases.thresholdsHoldOnBothSides()

    @Test fun surfaceNormalsAt4point9And5point1DegreesAcceptThenReject() =
        ToleranceCases.surfaceToleranceBothSides()

    @Test fun rectangleFarFromBothConstraintsRejectedWithGravityUnverifiedWithout() =
        ToleranceCases.planeFarFromBothConstraints()

    @Test fun halfPixelMarkingNoiseStaysRectangleEligibleWithinTwoPercent() =
        ToleranceCases.halfPixelNoiseStaysEligible()

    @Test fun focalErrorIsQualifiedApproximateOrRejectedNeverExact() =
        ToleranceCases.focalErrorIsQualified()

    // --- C10 outcome edge ---

    @Test fun C10_engineToSessionOnlySuccessIsUsableAndFailuresKeepReasons() =
        OutcomeCases.engineToSessionEdge()

    @Test fun C10_bothCandidatesIneligibleExplainsEachMethod() =
        OutcomeCases.bothIneligibleExplainsEachMethod()

    @Test fun C10_invalidInputsAreStructuredFailuresNotExceptions() =
        OutcomeCases.invalidInputsAreFailures()

    @Test fun C10_calibrationProvenanceAndConfidenceBoundsStayVisible() =
        OutcomeCases.provenanceStaysVisible()
}
