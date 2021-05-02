package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

public class Collider extends Component {

    public static class Mask {
        public static final int solid  = 1 << 0;
        public static final int player = 1 << 1;
        public static final int enemy  = 1 << 2;
    }

    public enum Shape { none, rect }

    public int mask = 0;
    public Point origin;

    private Shape shape;
    private RectI rect;

    public Collider() {
        super();
        origin = Point.zero();
        rect = RectI.zero();
    }

    @Override
    public void reset() {
        super.reset();
        mask = 0;
        shape = Shape.none;
        origin = null;
        rect = null;
    }

    // ------------------------------------------------------------------------

    public static Collider makeRect(RectI rect) {
        var collider = new Collider();
        collider.shape = Shape.rect;
        collider.rect.set(rect);
        return collider;
    }

    // ------------------------------------------------------------------------

    public Shape shape() {
        return shape;
    }

    public RectI rect() {
        if (shape != Shape.rect) {
            throw new GdxRuntimeException("Collider is not a Rectangle");
        }
        return rect;
    }

    public Collider rect(RectI rect) {
        if (shape != Shape.rect) {
            throw new GdxRuntimeException("Collider is not a Rectangle");
        }
        this.rect.set(rect);
        return this;
    }

    public Collider rect(int x, int y, int w, int h) {
        if (shape != Shape.rect) {
            throw new GdxRuntimeException("Collider is not a Rectangle");
        }
        rect.set(x, y, w, h);
        return this;
    }

    // ------------------------------------------------------------------------

    public boolean check(int mask) {
        return check(mask, Point.zero());
    }

    public boolean check(int mask, Point offset) {
        var other = world().first(Collider.class);
        while (other != null) {
            var isDifferent = (other != this);
            var isMasked = ((other.mask & mask) == mask);
            var isOverlap = overlaps(other, offset);
            if (isDifferent && isMasked && isOverlap) {
                return true;
            }
            other = (Collider) other.next;
        }
        return false;
    }

    public boolean overlaps(Collider other, Point offset) {
        if (shape == Shape.rect) {
            if (other.shape == Shape.rect) {
                return rectOverlapsRect(this, other, offset);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------

    private final Color debugColor = new Color(1f, 0f, 0f, 0.75f);

    @Override
    public void render(ShapeRenderer shapes) {
        shapes.setColor(debugColor);
        if (shape == Shape.rect) {
            var x = entity.position.x + origin.x + rect.x;
            var y = entity.position.y + origin.y + rect.y;
            shapes.rect(x, y, rect.w, rect.h);
        }
        shapes.setColor(Color.WHITE);
    }

    // ------------------------------------------------------------------------

    private static boolean rectOverlapsRect(Collider a, Collider b, Point offset) {
        var rectA = RectI.pool.obtain().set(
                a.entity.position.x + a.origin.x + a.rect.x + offset.x,
                a.entity.position.y + a.origin.y + a.rect.y + offset.y,
                a.rect.w, a.rect.h
        );
        var rectB = RectI.pool.obtain().set(
                b.entity.position.x + b.origin.x + b.rect.x,
                b.entity.position.y + b.origin.y + b.rect.y,
                b.rect.w, b.rect.h
        );
        var overlap = rectA.overlaps(rectB);
        RectI.pool.free(rectA);
        RectI.pool.free(rectB);
        return overlap;
    }

}
