package com.cocode.measureapp.core

import com.cocode.measureapp.core.recovery.RecoveryCases
import org.junit.Test

/**
 * Node 05 measurement failure handling, driven through the `MarkingFlow` callbacks that
 * `MeasureApp` wires to Measure, mark edits, surface selection, Settings and Retake, with node
 * 03's real producer. Not covered here: Compose taps and physical-camera capture, which the
 * Android integration task verifies on a device.
 */
class MeasurementRecoveryContractTest {
    @Test fun collapsedStickIsCaughtAndExplained() {
        RecoveryCases.collapsedStickIsExplainedInTheMarkingFlow()
    }

    @Test fun rayParallelAndUnusableSolverAreFailuresNotExceptions() {
        RecoveryCases.geometryFailuresReachThePresenter()
    }

    @Test fun correctionAfterSuccessHasNoStaleResultThenSucceeds() {
        RecoveryCases.correctionAfterSuccessNeverShowsTheOldResult()
    }

    @Test fun rejectedSettingsKeepPhotoMarksAndSurface() {
        RecoveryCases.invalidSettingsKeepPhotoMarksAndSurface()
    }

    @Test fun C11_retakeAfterFailedMeasurementAcceptsNewCapture() {
        RecoveryCases.retakeAfterFailureAcceptsNewCapture()
    }

    @Test fun C14_realSuccessAndFailureVariantsThroughPresenter() {
        RecoveryCases.sharedSchemaThroughPresenter()
    }

    @Test fun C15_surfaceChangeRejectsLateCompletionUntilNewSuccess() {
        RecoveryCases.surfaceChangeRejectsLateCompletion()
    }
}
