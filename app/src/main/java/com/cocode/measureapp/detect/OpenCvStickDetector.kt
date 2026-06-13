package com.cocode.measureapp.detect

import android.graphics.Bitmap
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.stick.StickAssembler
import com.cocode.measureapp.stick.StickPoints
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Detects the red-white-red-white stick via HSV segmentation, then delegates ALL geometry
 * (axis fit, ordering, band-pattern validation, confidence) to the pure [StickAssembler].
 *
 * Requires the OpenCV native libraries to be loaded (OpenCVLoader.initLocal()). Any failure —
 * including OpenCV not being loaded — is caught and yields null, so the UI falls back to manual
 * marking. Runtime behavior needs verification on a real device.
 */
class OpenCvStickDetector(private val targetSamples: Int = 220) : StickDetector {

    override fun detect(image: Bitmap): StickPoints? {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val red1 = Mat()
        val red2 = Mat()
        val redMask = Mat()
        val whiteMask = Mat()
        val stickMask = Mat()
        val hierarchy = Mat()
        val contourMask = Mat()
        val redInStick = Mat()
        val contours = ArrayList<MatOfPoint>()
        return try {
            Utils.bitmapToMat(image, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            Core.inRange(hsv, Scalar(0.0, 120.0, 70.0), Scalar(10.0, 255.0, 255.0), red1)
            Core.inRange(hsv, Scalar(160.0, 120.0, 70.0), Scalar(179.0, 255.0, 255.0), red2)
            Core.bitwise_or(red1, red2, redMask)
            Core.inRange(hsv, Scalar(0.0, 0.0, 180.0), Scalar(179.0, 40.0, 255.0), whiteMask)
            Core.bitwise_or(redMask, whiteMask, stickMask)

            Imgproc.findContours(stickMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null

            Mat.zeros(stickMask.size(), CvType.CV_8UC1).copyTo(contourMask)
            Imgproc.drawContours(contourMask, listOf(largest), -1, Scalar(255.0), -1)

            val stride = maxOf(1, minOf(contourMask.rows(), contourMask.cols()) / targetSamples)
            val stickPoints = sampleMask(contourMask, stride)
            Core.bitwise_and(redMask, contourMask, redInStick)
            val redPoints = sampleMask(redInStick, stride)
            StickAssembler.assemble(stickPoints, redPoints)
        } catch (t: Throwable) {
            null
        } finally {
            listOf(rgba, rgb, hsv, red1, red2, redMask, whiteMask, stickMask, hierarchy, contourMask, redInStick)
                .forEach { it.release() }
            contours.forEach { it.release() }
        }
    }

    /** Samples foreground (non-zero) pixels of an 8-bit single-channel mask on a stride grid. */
    private fun sampleMask(mask: Mat, stride: Int): List<Vec2> {
        val pts = ArrayList<Vec2>()
        val buf = ByteArray(1)
        var y = 0
        while (y < mask.rows()) {
            var x = 0
            while (x < mask.cols()) {
                mask.get(y, x, buf)
                if (buf[0].toInt() != 0) pts.add(Vec2(x.toDouble(), y.toDouble()))
                x += stride
            }
            y += stride
        }
        return pts
    }
}
