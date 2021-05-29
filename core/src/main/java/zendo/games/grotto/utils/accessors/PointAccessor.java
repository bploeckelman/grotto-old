package zendo.games.grotto.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import zendo.games.grotto.utils.Point;

public class PointAccessor implements TweenAccessor<Point> {

    public static final int X = 1;
    public static final int Y = 2;
    public static final int XY = 3;

    @Override
    public int getValues(Point target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case X:  returnValues[0] = target.x; return 1;
            case Y:  returnValues[0] = target.y; return 1;
            case XY:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                return 2;
            default: assert false; return -1;
        }
    }

    @Override
    public void setValues(Point target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case X:  target.x = (int) newValues[0]; break;
            case Y:  target.y = (int) newValues[0]; break;
            case XY:
                target.x = (int) newValues[0];
                target.y = (int) newValues[1];
                break;
            default: assert false;
        }
    }

}
