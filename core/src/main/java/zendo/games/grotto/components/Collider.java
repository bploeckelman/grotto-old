package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

import java.util.Arrays;

public class Collider extends Component {

    public static class Mask {
        public static final int solid         = 1 << 0;
        public static final int jumpthru      = 1 << 1;
        public static final int player        = 1 << 2;
        public static final int enemy         = 1 << 3;
        public static final int player_attack = 1 << 4;
        public static final int item          = 1 << 5;
        public static final int room_bounds   = 1 << 6;
        public static final int climbable     = 1 << 7;
    }

    public enum Shape { none, rect, grid }

    public static class Grid {
        public int tileSize;
        public int cols;
        public int rows;
        public boolean[] cells;
    }

    public int mask = 0;
    public Point origin;

    private Shape shape;
    private RectI rect;
    private Grid grid;

    private RectI worldRect;

    public Collider() {
        super();
        origin = Point.zero();
        worldRect = RectI.zero();
    }

    @Override
    public void reset() {
        super.reset();
        mask = 0;
        shape = Shape.none;
        origin = null;
        rect = null;
        grid = null;
        worldRect = null;
    }

    // ------------------------------------------------------------------------

    public static Collider makeRect(RectI rect) {
        var collider = new Collider();
        collider.shape = Shape.rect;
        collider.rect = RectI.at(rect);
        return collider;
    }

    public static Collider makeGrid(int tileSize, int cols, int rows) {
        var collider = new Collider();
        collider.shape = Shape.grid;
        collider.grid = new Grid();
        collider.grid.tileSize = tileSize;
        collider.grid.cols = cols;
        collider.grid.rows = rows;
        collider.grid.cells = new boolean[cols * rows];
        Arrays.fill(collider.grid.cells, false);
        return collider;
    }

    // ------------------------------------------------------------------------

    public Shape shape() {
        return shape;
    }

    // ------------------------------------------------------------------------

    public RectI rect() {
        if (shape != Shape.rect) {
            throw new GdxRuntimeException("Collider is not a Rectangle");
        }
        return rect;
    }

    public RectI worldRect() {
        return worldRect.set(
                entity.position.x + origin.x + rect.x,
                entity.position.y + origin.y + rect.y,
                rect.w, rect.h);
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

    public Grid grid() {
        if (shape != Shape.grid) {
            throw new GdxRuntimeException("Collider is not a Grid");
        }
        return grid;
    }

    public boolean getCell(int x, int y) {
        if (shape != Shape.grid) {
            throw new GdxRuntimeException("Collider is not a Grid");
        }
        if (x < 0 || y < 0 || x >= grid.cols || y >= grid.rows) {
            throw new GdxRuntimeException("Cell is out of bounds");
        }
        return grid.cells[x + y * grid.cols];
    }

    public void setCell(int x, int y, boolean value) {
        if (shape != Shape.grid) {
            throw new GdxRuntimeException("Collider is not a Grid");
        }
        if (x < 0 || y < 0 || x >= grid.cols || y >= grid.rows) {
            throw new GdxRuntimeException("Cell is out of bounds");
        }
        grid.cells[x + y * grid.cols] = value;
    }

    public void setCells(int x, int y, int w, int h, boolean value) {
        if (shape != Shape.grid) {
            throw new GdxRuntimeException("Collider is not a Grid");
        }
        if (x < 0 || y < 0 || x + w > grid.cols || y + h > grid.rows) {
            throw new GdxRuntimeException("Cell is out of bounds");
        }
        for (int ix = x; ix < x + w; ix++) {
            for (int iy = y; iy < y + h; iy++) {
                grid.cells[ix + iy * grid.cols] = value;
            }
        }
    }

    // ------------------------------------------------------------------------

    public Collider first(int mask) {
        return first(mask, Point.zero());
    }

    public Collider first(int mask, Point offset) {
        if (world() != null) {
            var other = world().first(Collider.class);
            while (other != null) {
                var isDifferent = (other != this);
                var isMasked = ((other.mask & mask) == mask);
                var isOverlap = overlaps(other, offset);
                if (isDifferent && isMasked && isOverlap) {
                    return other;
                }
                other = (Collider) other.next;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------

    public boolean check(int mask) {
        return check(mask, Point.zero());
    }

    public boolean check(int mask, Point offset) {
        if (world() != null) {
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
        }
        return false;
    }

    public boolean overlaps(Collider other, Point offset) {
        if (shape == Shape.rect) {
            if (other.shape == Shape.rect) {
                return rectOverlapsRect(this, other, offset);
            } else if (other.shape == Shape.grid) {
                return rectOverlapsGrid(this, other, offset);
            }
        } else if (shape == Shape.grid) {
            if (other.shape == Shape.rect) {
                return rectOverlapsGrid(other, this, offset);
            } else if (other.shape == Shape.grid) {
                throw new GdxRuntimeException("Grid->Grid overlap checks not supported");
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------

    private final Color debugColor = new Color(1f, 0f, 0f, 0.75f);
    private final Color debugJumpthruColor = new Color(0, 0.5f, 0.5f, 0.75f);

    @Override
    public void render(ShapeRenderer shapes) {
        if (mask == Mask.jumpthru) {
            shapes.setColor(debugJumpthruColor);
        } else {
            shapes.setColor(debugColor);
        }
        if (shape == Shape.rect) {
            var x = entity.position.x + origin.x + rect.x;
            var y = entity.position.y + origin.y + rect.y;
            shapes.rect(x, y, rect.w, rect.h);
        } else if (shape == Shape.grid) {
            var rect = RectI.pool.obtain();
            for (int x = 0; x < grid.cols; x++) {
                for (int y = 0; y < grid.rows; y++) {
                    if (!grid.cells[x + y * grid.cols]) continue;
                    rect.set(
                            entity.position.x + origin.x + x * grid.tileSize,
                            entity.position.y + origin.y + y * grid.tileSize,
                            grid.tileSize, grid.tileSize
                    );
                    shapes.rect(rect.x, rect.y, rect.w, rect.h);
                }
            }
            RectI.pool.free(rect);
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

    private static boolean rectOverlapsGrid(Collider a, Collider b, Point offset) {
        // get a relative rectangle to the grid
        RectI rect = RectI.pool.obtain().set(
                a.origin.x + a.rect.x + a.entity().position.x + offset.x - b.entity().position.x,
                a.origin.y + a.rect.y + a.entity().position.y + offset.y - b.entity().position.y,
                a.rect.w,
                a.rect.h
        );

        // first do a sanity check that the Rect is within the bounds of the Grid
        RectI gridBounds = RectI.pool.obtain().set(
                b.origin.x,
                b.origin.y,
                b.grid.cols * b.grid.tileSize,
                b.grid.rows * b.grid.tileSize
        );

        if (!rect.overlaps(gridBounds)) {
            RectI.pool.free(rect);
            RectI.pool.free(gridBounds);
            return false;
        }

        // get the cells the rectangle overlaps
        // subtract out the rect collider's origin to put it back in the same space as the grid (0..col*tileSz,0..row*tileSz)
        int left   = Calc.clampInt((int) Calc.floor  (rect.x        / (float) b.grid.tileSize), 0, b.grid.cols);
        int right  = Calc.clampInt((int) Calc.ceiling(rect.right()  / (float) b.grid.tileSize), 0, b.grid.cols);
        int top    = Calc.clampInt((int) Calc.floor  (rect.y        / (float) b.grid.tileSize), 0, b.grid.rows);
        int bottom = Calc.clampInt((int) Calc.ceiling(rect.bottom() / (float) b.grid.tileSize), 0, b.grid.rows);

        // check each cell
        for (int x = left; x < right; x++) {
            for (int y = top; y < bottom; y++) {
                if (b.grid.cells[x + y * b.grid.cols]) {
                    RectI.pool.free(rect);
                    RectI.pool.free(gridBounds);
                    return true;
                }
            }
        }

        // all cells were empty
        RectI.pool.free(rect);
        RectI.pool.free(gridBounds);
        return false;
    }

}
