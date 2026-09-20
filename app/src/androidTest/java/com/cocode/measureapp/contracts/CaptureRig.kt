package com.cocode.measureapp.contracts

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.SensorEvent
import com.cocode.measureapp.capture.frames.ActiveArray
import com.cocode.measureapp.capture.frames.LensCharacteristics
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravityUnavailableReason
import com.cocode.measureapp.capture.recovery.CalibrationQuality
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.TIMESTAMP_SOURCE_REALTIME
import com.cocode.measureapp.capture.recovery.consumeFrame
import com.cocode.measureapp.capture.recovery.exposureTimestampOf
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.frames.CameraMounting
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.alignCapturedShot

/** A camera frame as CameraX hands it over; every accessor fails once it is closed. */
class FakeProxy(
    private val raw: Bitmap,
    private val cropRect: CropRect,
    private val rotation: Int,
    private val timestampNanos: Long,
) : AutoCloseable {
    var closed = false
        private set

    private fun <T> open(read: () -> T): T {
        check(!closed) { "frame already closed" }
        return read()
    }

    val width: Int get() = open { raw.width }
    val height: Int get() = open { raw.height }
    val crop: CropRect get() = open { cropRect }
    val rotationDegrees: Int get() = open { rotation }
    val timestamp: Long get() = open { timestampNanos }

    /** A fresh copy, like `ImageProxy.toBitmap()`. */
    fun toBitmap(): Bitmap = open { raw.copy(Bitmap.Config.ARGB_8888, false) }

    override fun close() {
        closed = true
    }
}

/**
 * One simulated shot: [proxy] plus the bound camera's [lens] and the gravity history at capture.
 * [outcome] mirrors the app's `buildCapturedImage`: every piece of metadata is copied from the
 * proxy before it is closed, then the real [alignCapturedShot] produces the marking bitmap.
 */
class Shot(
    val proxy: FakeProxy,
    val lens: LensCharacteristics,
    val kBuffer: CameraIntrinsics,
    val targetRotation: Int?,
    val latest: GravitySample,
    val recent: List<GravitySample.Available>,
    val bufferObject: List<Vec2>,
    val bufferStick: List<Vec2>,
) {
    fun outcome(requestId: Int): CaptureOutcome<CapturedImage> = consumeFrame(proxy) { convert(requestId) }

    private fun convert(requestId: Int): CapturedImage {
        val metadata = CaptureMetadata(
            requestId = requestId,
            frameWidth = proxy.width,
            frameHeight = proxy.height,
            rotationDegrees = proxy.rotationDegrees,
            crop = proxy.crop,
            exposure = exposureTimestampOf(proxy.timestamp, lens.timestampSource),
            gravity = latest,
            recentGravity = recent,
            calibration = CalibrationQuality.APPROXIMATE,
        )
        val raw = proxy.toBitmap()
        metadata.requireComplete(raw.width, raw.height)
        val (upright, scene) = alignCapturedShot(raw, metadata, lens, targetRotation)
        val quality = if (scene.calibration.status == CalibrationStatus.CALIBRATED) {
            CalibrationQuality.DEVICE_CALIBRATED
        } else {
            CalibrationQuality.APPROXIMATE
        }
        return CapturedImage(upright, scene, metadata.copy(calibration = quality))
    }

    /** Object and stick marks in the delivered image, through that image's own alignment. */
    fun marks(img: CapturedImage): Pair<List<Vec2>, List<Vec2>> {
        val a = img.scene.alignment ?: error("scene has no alignment")
        return bufferObject.map(a::toMarking) to bufferStick.map(a::toMarking)
    }
}

/** Builds simulated shots from a back camera mounted at [SENSOR] (the common phone layout). */
object ShotRig {
    const val EXPOSURE = 5_000_000_000L
    const val SAMPLE_AGE = 10_000_000L
    val SENSOR = QuarterTurn.R90

    /** Physical down in device axes that this camera sees as [bufferDown] in buffer axes. */
    fun deviceDown(bufferDown: Vec3): Vec3 {
        val u = SENSOR.vector(bufferDown)
        return Vec3(u.x, -u.y, -u.z)
    }

    /** One fresh device-axis sample for [bufferDown], taken [age] ns before exposure. */
    fun samples(bufferDown: Vec3, age: Long = SAMPLE_AGE): Pair<GravitySample, List<GravitySample.Available>> {
        val s = GravitySample.Available(deviceDown(bufferDown), EXPOSURE - age)
        return s to listOf(s)
    }

    val NO_SAMPLES: Pair<GravitySample, List<GravitySample.Available>> =
        GravitySample.Unavailable(GravityUnavailableReason.NO_SAMPLE_YET) to emptyList()

    /**
     * A frame whose full buffer is [raw] with [crop] still pending, reported with [rotation]
     * (the target rotation is the one that explains it for [SENSOR]). [calibratedLens] reports
     * [k] as device calibration; otherwise only the field-of-view fallback is available.
     */
    fun frame(
        raw: Bitmap,
        crop: CropRect,
        k: CameraIntrinsics,
        rotation: QuarterTurn,
        gravity: Pair<GravitySample, List<GravitySample.Available>>,
        obj: List<Vec2>,
        stick: List<Vec2>,
        source: Int? = TIMESTAMP_SOURCE_REALTIME,
        calibratedLens: Boolean = true,
    ): Shot {
        val lens = LensCharacteristics(
            intrinsicCalibration = if (calibratedLens) listOf(k.fx, k.fy, k.cx, k.cy, 0.0) else null,
            activeArray = ActiveArray(raw.width, raw.height),
            facing = CameraMounting.CAMERA2_FACING_BACK,
            sensorOrientation = SENSOR.degrees,
            timestampSource = source,
        )
        val target = (SENSOR.degrees - rotation.degrees + 360) % 360
        val proxy = FakeProxy(raw, crop, rotation.degrees, EXPOSURE)
        return Shot(proxy, lens, k, target, gravity.first, gravity.second, obj, stick)
    }

    /**
     * The [upright] picture turned back into buffer orientation for a capture reported with
     * [rotation], inside a [margin]-pixel border that the pending crop removes again.
     * [markingDown] is physical down as seen in the upright picture.
     */
    fun fromUpright(
        upright: Bitmap,
        k: CameraIntrinsics,
        rotation: QuarterTurn,
        markingDown: Vec3?,
        obj: List<Vec2>,
        stick: List<Vec2>,
        source: Int? = TIMESTAMP_SOURCE_REALTIME,
        gravity: Pair<GravitySample, List<GravitySample.Available>>? = null,
        margin: Int = 6,
    ): Shot {
        val inv = rotation.inverse()
        val w = upright.width.toDouble()
        val h = upright.height.toDouble()
        val turned = Bitmap.createBitmap(
            upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(inv.degrees.toFloat()) }, false,
        )
        val raw = Bitmap.createBitmap(turned.width + 2 * margin, turned.height + 2 * margin, Bitmap.Config.ARGB_8888)
        val px = IntArray(turned.width * turned.height)
        turned.getPixels(px, 0, turned.width, 0, 0, turned.width, turned.height)
        raw.setPixels(px, 0, turned.width, margin, margin, turned.width, turned.height)
        val kc = inv.intrinsics(k, w, h)
        val kb = kc.copy(cx = kc.cx + margin, cy = kc.cy + margin)
        fun buffer(p: Vec2) = inv.pixel(p, w, h).let { Vec2(it.x + margin, it.y + margin) }
        val crop = CropRect(margin, margin, margin + turned.width, margin + turned.height)
        val g = gravity ?: markingDown?.let { samples(inv.vector(it)) } ?: NO_SAMPLES
        return frame(raw, crop, kb, rotation, g, obj.map(::buffer), stick.map(::buffer), source)
    }

    /** A framework `TYPE_GRAVITY` event; its constructor is not public API, so reflection is used. */
    @SuppressLint("DiscouragedPrivateApi", "SoonBlockedPrivateApi", "BlockedPrivateApi")
    fun gravityEvent(raw: FloatArray, timestampNanos: Long): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        ctor.isAccessible = true
        return ctor.newInstance(raw.size).apply {
            raw.copyInto(this.values)
            timestamp = timestampNanos
        }
    }
}
