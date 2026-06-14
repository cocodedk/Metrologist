package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.ScaleResult
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.Vec2
import kotlin.math.abs

/**
 * Recovers the metric-to-real [ScaleResult] from the stick's 4 box corners (already projected
 * onto the metric plane) using BOTH known dimensions:
 *
 * - the LONG edge pair calibrates against the known [StickProfile.totalLength]
 *   (`scaleLen = totalLength / meanLongEdge`);
 * - the SHORT edge pair, when [StickProfile.width] `> 0`, calibrates against the known width
 *   (`scaleWid = width / meanShortEdge`).
 *
 * The two estimates are merged with a **length-weighted average**
 * `scale = (totalLength·scaleLen + width·scaleWid) / (totalLength + width)`, so the longer,
 * better-resolved dimension dominates — the across-stick width (a few cm) never drags a long
 * stick's scale around, but it still contributes and catches gross skew. The reported
 * `agreement` is the relative disagreement `|scaleLen - scaleWid| / scale` between the two
 * perpendicular estimates (0 when width is unknown or the two agree).
 */
object StickScale {
    fun solve(boxMetric: List<Vec2>, profile: StickProfile): ScaleResult {
        require(boxMetric.size == 4) { "stick box needs exactly 4 corners, got ${boxMetric.size}" }
        val (longMean, shortMean) = StickBox.longShortMeanEdges(boxMetric)
        require(longMean > 0.0) { "degenerate stick box: zero-length long edge" }
        val scaleLen = profile.totalLength / longMean

        if (profile.width <= 0.0) return ScaleResult(scaleLen, 0.0)

        require(shortMean > 0.0) { "degenerate stick box: zero-length short edge with known width" }
        val scaleWid = profile.width / shortMean
        val scale = (profile.totalLength * scaleLen + profile.width * scaleWid) /
            (profile.totalLength + profile.width)
        val agreement = abs(scaleLen - scaleWid) / scale
        return ScaleResult(scale, agreement)
    }
}
