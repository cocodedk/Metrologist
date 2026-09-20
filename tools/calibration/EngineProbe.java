import com.cocode.measureapp.geometry.*;
import com.cocode.measureapp.geometry.frames.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Calls the compiled production engine using the page fixture's synthetic camera metadata. */
public final class EngineProbe {
    private static List<Vec2> points(double[] values, int offset) {
        List<Vec2> points = new ArrayList<>();
        for (int i = 0; i < 8; i += 2) points.add(new Vec2(values[offset + i], values[offset + i + 1]));
        return points;
    }

    public static void main(String[] args) {
        if (args.length != 25) throw new IllegalArgumentException("Expected 25 fixture coordinates and dimensions");
        double[] v = java.util.Arrays.stream(args).mapToDouble(Double::parseDouble).toArray();
        MeasurementOutcome outcome = MetrologyEngine.INSTANCE.evaluate(
            points(v, 0), points(v, 8), new CameraIntrinsics(v[16], v[17], v[18], v[19]),
            CalibrationProvenance.Companion.getCALIBRATED(),
            new AlignedGravity.Available(new Vec3(v[20], v[21], v[22]), 0L),
            new StickProfile(v[23], 4, v[24]), SurfaceOrientation.VERTICAL, 1);
        if (!(outcome instanceof MeasurementOutcome.Success result)) {
            throw new AssertionError("Page target rejected: " + outcome);
        }
        MeasurementResult m = result.getMeasurement();
        System.out.printf(Locale.ROOT,
            "{\"widthMetres\":%.17g,\"heightMetres\":%.17g,\"areaSquareMetres\":%.17g," +
            "\"diagonalMetres\":%.17g,\"anglesDegrees\":%s,\"solver\":\"%s\",\"confidence\":%.17g}%n",
            m.getWidth(), m.getHeight(), m.getArea(), m.getDiagonal(), m.getCornerAngles(),
            result.getSolver(), result.getConfidence());
    }
}
