package com.cocode.measureapp.contracts

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.cocode.measureapp.core.Units
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.surface.surfaceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Presses the same buttons a user presses on [FlowHost] and reads what the screens show. */
class FlowDriver(private val rule: ComposeContentTestRule, val s: FlowState = FlowState()) {
    fun start(): FlowDriver {
        rule.setContent { FlowHost(s) }
        rule.waitForIdle()
        return this
    }

    /** Runs [block] on the UI thread between frames, like a callback from outside Compose. */
    fun ui(block: FlowState.() -> Unit) {
        rule.runOnIdle { s.block() }
        rule.waitForIdle()
    }

    fun load(img: CapturedImage, corners: List<Vec2>, stick: List<Vec2>) = ui { load(img, corners, stick) }

    fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    /** Buttons on the scrolling Results screen. */
    fun clickResults(text: String) {
        rule.onNodeWithText(text).performScrollTo().performClick()
        rule.waitForIdle()
    }

    fun back() {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
    }

    /** Presses Measure and returns the outcome the real engine produced for it. */
    fun measure(): MeasurementOutcome {
        val before = s.outcomes.size
        click("Measure")
        assertEquals("Measure must run the engine exactly once", before + 1, s.outcomes.size)
        return s.outcomes.last()
    }

    fun select(o: SurfaceOrientation) = click(surfaceLabel(o))

    fun assertSelected(o: SurfaceOrientation) {
        rule.onNodeWithText(surfaceLabel(o)).assertIsSelected()
    }

    fun has(text: String, substring: Boolean = true): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    fun assertShown(text: String, substring: Boolean = true) =
        assertTrue("'$text' should be on screen", has(text, substring))

    fun assertAbsent(text: String) = assertFalse("'$text' should not be on screen", has(text))

    fun assertResults() {
        assertEquals(HostStep.Results, s.step)
        assertShown("Measurements", substring = false)
    }

    /** Still marking: no Results, no usable result, no export. */
    fun assertMarking() {
        assertEquals(HostStep.Mark, s.step)
        assertFalse(has("Measurements", substring = false))
        assertTrue(has("Measure", substring = false))
    }

    fun assertNoUsableResult() {
        assertNull(s.flow.usableView)
        assertFalse(s.flow.exportEnabled)
        assertNull(s.flow.session.usableResult)
    }

    fun assertCapture(enabled: Boolean) {
        val node = rule.onNodeWithText(if (enabled) "Capture" else "Capturing…")
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    fun assertExport(enabled: Boolean) {
        val node = rule.onNodeWithText("Export").performScrollTo()
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    /** Results show [m] with the presenter's normal rounding in the current unit. */
    fun assertDisplayed(m: MeasurementResult) {
        val u = s.unit
        assertShown(Units.formatLength(m.width, u), substring = false)
        assertShown(Units.formatLength(m.height, u), substring = false)
        assertShown(Units.formatArea(m.area, u), substring = false)
        assertShown(Units.formatLength(m.diagonal, u), substring = false)
        m.cornerAngles.forEach { assertShown(Expect.angleText(it)) }
    }

    /** Settings: picks [unitIndex] among the unit radio buttons and types both dimensions. */
    fun enterReference(unitIndex: Int, length: String, width: String) {
        rule.onAllNodes(isSelectable())[unitIndex].performClick()
        rule.waitForIdle()
        typeLength(length)
        rule.onAllNodes(hasSetTextAction())[1].performTextReplacement(width)
        rule.waitForIdle()
    }

    fun typeLength(text: String) {
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        rule.waitForIdle()
    }
}
