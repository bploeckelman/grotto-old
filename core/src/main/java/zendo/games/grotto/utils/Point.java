package zendo.games.grotto.utils;

import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public class Point implements Pool.Poolable {

    public static Pool<Point> pool = Pools.get(Point.class);

    public static Point zero() {
        return new Point();
    }

    public static Point at(int x, int y) {
        return new Point(x, y);
    }

    public int x;
    public int y;

    // must be public for Json deserialization
    public Point() {
        this(0, 0);
    }

    public Point(Point other) {
        this(other.x, other.y);
    }

    private Point(int x, int y) {
        set(x, y);
    }

    public Point copy() {
        return new Point(x, y);
    }

    public Point set(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Point set(Point other) {
        return set(other.x, other.y);
    }

    public boolean is(int x, int y) {
        return (this.x == x && this.y == y);
    }

    public boolean is(Point point) {
        return is(point.x, point.y);
    }

    public Point add(int x, int y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Point add(Point point) {
        this.x += point.x;
        this.y += point.y;
        return this;
    }

    public Point sub(int x, int y) {
        this.x -= x;
        this.y -= y;
        return this;
    }

    public Point sub(Point point) {
        this.x -= point.x;
        this.y -= point.y;
        return this;
    }

    public Point mul(int s) {
        this.x *= s;
        this.y *= s;
        return this;
    }

    public Point div(int s) {
        this.x /= s;
        this.y /= s;
        return this;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d)", x, y);
    }

    @Override
    public void reset() {
        this.x = 0;
        this.y = 0;
    }

}
