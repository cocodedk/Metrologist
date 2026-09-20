package com.cocode.measureapp.contracts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravityUnavailableReason
import com.cocode.measureapp.capture.recovery.CalibrationQuality
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.model.CapturedScene
import com.cocode.measureapp.ui.CapturedImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals

/**
 * One rendered calibration-target page from `androidTest/assets/calibration-target`: the PNG and
 * its independent ground truth. Rendered artwork with synthetic pinhole metadata, NOT a
 * physical-camera capture. Values are read as stored and never adjusted to the solver.
 */
data class TargetFixture(
    val name: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val intrinsics: CameraIntrinsics,
    val down: Vec3,
    val orientation: SurfaceOrientation,
    val objectPixels: List<Vec2>,
    val stickPixels: List<Vec2>,
    val lengthMetres: Double,
    val widthMetres: Double,
    val expected: MeasurementResult,
) {
    /** Validated reference settings equal to the page's printed reference. */
    val reference: ReferenceCheck get() = ReferenceCheck.Valid(lengthMetres, widthMetres)

    fun bitmap(): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = assets.open("calibration-target/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("cannot decode $name.png")
        assertEquals("$name.png width", imageWidth, bmp.width)
        assertEquals("$name.png height", imageHeight, bmp.height)
        return bmp
    }

    /** The page as a trusted synthetic scene: fixture intrinsics and its physical-down vector. */
    fun image(
        shotId: Int = 1,
        gravity: AlignedGravity = AlignedGravity.Available(down, 0L),
        calibration: CalibrationProvenance = CalibrationProvenance.CALIBRATED,
    ): CapturedImage = Scenes.image(bitmap(), intrinsics, gravity, calibration, shotId)

    companion object {
        private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets

        val frontal: TargetFixture by lazy { load("frontal") }
        val nonrectangle: TargetFixture by lazy { load("nonrectangle") }

        private fun load(name: String): TargetFixture {
            val j = JSONObject(assets.open("calibration-target/$name.json").bufferedReader().use { it.readText() })
            val k = j.getJSONObject("intrinsics")
            val d = j.getJSONArray("physicalDown")
            val e = j.getJSONObject("expected")
            val angles = e.getJSONArray("anglesDegrees")
            return TargetFixture(
                name = name,
                imageWidth = j.getInt("imageWidth"),
                imageHeight = j.getInt("imageHeight"),
                intrinsics = CameraIntrinsics(k.getDouble("fx"), k.getDouble("fy"), k.getDouble("cx"), k.getDouble("cy")),
                down = Vec3(d.getDouble(0), d.getDouble(1), d.getDouble(2)),
                orientation = SurfaceOrientation.valueOf(j.getString("orientation")),
                objectPixels = points(j.getJSONArray("objectPixels")),
                stickPixels = points(j.getJSONArray("referencePixels")),
                lengthMetres = j.getDouble("referenceLengthMetres"),
                widthMetres = j.getDouble("referenceWidthMetres"),
                expected = MeasurementResult(
                    e.getDouble("widthMetres"), e.getDouble("heightMetres"), e.getDouble("areaSquareMetres"),
                    e.getDouble("diagonalMetres"), (0 until angles.length()).map { angles.getDouble(it) },
                ),
            )
        }

        private fun points(a: JSONArray): List<Vec2> = (0 until a.length()).map {
            val p = a.getJSONArray(it)
            Vec2(p.getDouble(0), p.getDouble(1))
        }
    }
}

/** Builders for captured images whose scene is given directly (already in the marking frame). */
object Scenes {
    fun blank(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** Metadata of a synthetic shot: identity and frame only; timestamp and gravity flagged unavailable. */
    fun metadata(shotId: Int, width: Int, height: Int): CaptureMetadata = CaptureMetadata(
        requestId = shotId,
        frameWidth = width,
        frameHeight = height,
        rotationDegrees = 0,
        crop = CropRect(0, 0, width, height),
        exposure = ExposureTimestamp.Unavailable("synthetic scene"),
        gravity = GravitySample.Unavailable(GravityUnavailableReason.NO_SAMPLE_YET),
        recentGravity = emptyList(),
        calibration = CalibrationQuality.APPROXIMATE,
    )

    /** A scene in the bitmap's own frame (identity alignment) with explicit gravity and provenance. */
    fun image(
        bitmap: Bitmap,
        k: CameraIntrinsics,
        gravity: AlignedGravity,
        calibration: CalibrationProvenance,
        shotId: Int = 1,
    ): CapturedImage {
        val w = bitmap.width
        val h = bitmap.height
        val scene = CapturedScene(
            imageWidth = w,
            imageHeight = h,
            intrinsics = k,
            gravity = (gravity as? AlignedGravity.Available)?.down ?: SceneAligner.LEGACY_PLACEHOLDER,
            shotId = shotId,
            calibration = calibration,
            alignedGravity = gravity,
            alignment = FrameAlignment(w, h, 0, 0, w, h, w, h, QuarterTurn.R0),
        )
        return CapturedImage(bitmap, scene, metadata(shotId, w, h))
    }

    /** A scene built without the capture-frame transform (the legacy 4-argument form). */
    fun unaligned(bitmap: Bitmap, k: CameraIntrinsics, gravity: Vec3, shotId: Int = 1): CapturedImage =
        CapturedImage(bitmap, CapturedScene(bitmap.width, bitmap.height, k, gravity), metadata(shotId, bitmap.width, bitmap.height))
}
