package zendo.games.grotto.curves;

import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.utils.Calc;

public class CubicBezier {

    public Vector2 p0, p1, p2, p3;

    // internal for calculations and return values
    private Vector2 p;

    public CubicBezier(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        this(
                new Vector2(x0, y0),
                new Vector2(x1, y1),
                new Vector2(x2, y2),
                new Vector2(x3, y3)
        );
    }

    public CubicBezier(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.p = new Vector2(p0);
    }

    public Vector2 evaluate(float t) {
        /*
        Bernstein polynomial form:
        --------------------------
        P(t) = P0(  -t^3 +  3t^2 + -3t + 1 )
             + P1(  3t^3 + -6t^2 +  3t     )
             + P2( -3t^3 +  3t^2           )
             + P3(   t^3                   )
         */
        float k0 = Calc.pow(           -t, 3) + Calc.pow(  3f * t, 2) + -3f * t + 1;
        float k1 = Calc.pow(  3f * t, 3) + Calc.pow( -6f * t, 2) +  3f * t;
        float k2 = Calc.pow( -3f * t, 3) + Calc.pow(  3f * t, 2);
        float k3 = Calc.pow(            t, 3);

        return applyCoefficients(k0, k1, k2, k3);
    }

    public Vector2 firstDerivative(float t) {
        /*
        Bernstein polynomial form:
        --------------------------
        P(t) = P0( -3t^2 +   6t + -3 )
             + P1(  9t^2 + -12t +  3 )
             + P2( -9t^2 +   6t      )
             + P3(  3t^2             )
         */
        float k0 = Calc.pow( -3f * t, 2) +   6f * t + -3f;
        float k1 = Calc.pow(  9f * t, 2) + -12f * t +  3f;
        float k2 = Calc.pow( -9f * t, 2) +   6f * t;
        float k3 = Calc.pow(  3f * t, 2);

        return applyCoefficients(k0, k1, k2, k3);
    }

    public Vector2 secondDerivative(float t) {
        /*
        Bernstein polynomial form:
        --------------------------
        P(t) = P0(  -6t +   6 )
             + P1(  18t + -12 )
             + P2( -18t +   6 )
             + P3(   6t       )
         */
        float k0 =  -6f * t +   6f;
        float k1 =  18f * t + -12f;
        float k2 = -18f * t +   6f;
        float k3 =   6f * t;

        return applyCoefficients(k0, k1, k2, k3);
    }

    private Vector2 applyCoefficients(float k0, float k1, float k2, float k3) {
        float px = (k0 * p0.x) + (k1 * p1.x) + (k2 * p2.x) + (k3 * p3.x);
        float py = (k0 * p0.y) + (k1 * p1.y) + (k2 * p2.y) + (k3 * p3.y);
        p.set(px, py);
        return p;
    }

    /*
    curvature = det(firstDeriv, secondDeriv) / norm(firstDeriv)^3
    (in units of: radians per meter, or reciprocal radius (ie radius of oscillating circle at that spot))
    */

    /**
     * Arc length parameterization
     * Thanks to Freya Holmér, you the best!
     * @param LUT
     * @param distance
     * @return
     */
    private float distToT(float[] LUT, float distance) {
        float arcLength = LUT[LUT.length - 1]; // total arc length
        int n = LUT.length;                    // n = sample count

        if (0 <= distance && distance <= arcLength) {               // check if the value is within the length of the curve
            for (int i = 0; i < n - 1; i++) {                       // iterate through the list to find which segment our distance lies within
                if (LUT[i] <= distance && distance <= LUT[i + 1]) { // check if our input distance lies between the two distances
                    // remap the distance range to the t-value range
                    return remapDistance(distance, LUT[i], LUT[i + 1], i / (n - 1f), (i + 1) / (n - 1f));
                }
            }
        }

        return distance / arcLength;  // distance is outside the length of the curve - extrapolate values outside
    }

    private float remapDistance(float distance, float prevDist, float nextDist, float prevT, float nextT) {
        // TODO ...
        return 1;
    }

}
