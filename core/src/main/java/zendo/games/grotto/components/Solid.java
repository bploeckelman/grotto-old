package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.editor.WorldMap;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;
import zendo.games.grotto.utils.VectorPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Solid extends Component {

    // TODO: additional info like interp method, speed, maybe other things
    static class Waypoint {
        Point point;
    }

    public RectI bounds;
    public Collider collider;

    private int id;
    private float t;
    private Vector2 remainder;

    private boolean forward;
    private int currentWaypoint;
    private List<Waypoint> waypoints;

    public Solid() {}

    public Solid(WorldMap.SolidInfo info, List<WorldMap.WaypointInfo> waypointInfos) {
        // make sure the waypoints are in order
        waypointInfos.sort(Comparator.comparing(waypointInfo -> waypointInfo.sequence));

        this.id = info.id;
        this.t = 0;
        this.forward = true;
        this.bounds = info.bounds;
        this.remainder = VectorPool.dim2.obtain();
        this.waypoints = new ArrayList<>();
        for (var waypointInfo : waypointInfos) {
            var waypoint = new Waypoint();
            waypoint.point = waypointInfo.point;
            this.waypoints.add(waypoint);
        }
        this.currentWaypoint = 0;
    }

    @Override
    public void reset() {
        super.reset();
        id = 0;
        t = 0;
        forward = false;
        bounds = null;
        collider = null;
        if (remainder != null) {
            VectorPool.dim2.free(remainder);
        }
        remainder = null;
        currentWaypoint = -1;
        waypoints = null;
    }

    @Override
    public void update(float dt) {
        // move the solid
        if (t > 1f || t < 0) {
            forward = !forward;
            if (t > 1) t = 1;
            if (t < 0) t = 0;
        }

        // interpolate between waypoints
        var interp = VectorPool.dim2.obtain();
        {
            // figure out which waypoints to transition between (assumes waypoints are in sequence order)
            int segment = (int) Calc.floor(t * (waypoints.size() - 1));
            segment = Calc.max(segment, 0);
            segment = Calc.min(segment, waypoints.size() - 2);

            // adjust main timer to move across current segment
            float tt = t * (waypoints.size() - 1) - segment;

            // interpolate across current segment
            var start = waypoints.get(segment);
            var end   = waypoints.get(segment + 1);
            interp.x = (1f - tt) * start.point.x + (tt) * end.point.x;
            interp.y = (1f - tt) * start.point.y + (tt) * end.point.y;

            // limit movement to integer intervals
            var x = (int) interp.x;
            var y = (int) interp.y;

            // how much will we move in this step
            // TODO: since these are used to carry/push other entities,
            //  need to track interp remainders so entities don't fall behind
            //  (like currently with player being carried downwards)
            int moveX = x - bounds.x;
            int moveY = y - bounds.y;

            // push or carry other movers
            updateOtherMovers(moveX, moveY);

            // move to new position
            bounds.setPosition(x, y);
            entity.position.set(x, y);
        }
        VectorPool.dim2.free(interp);

        // increment the overall movement timer
        var dir = forward ? 1 : -1;
        var speed = 0.4f;
        t += dir * speed * dt;
    }

    @Override
    public void render(ShapeRenderer shapes) {
        // TODO: pick debug color based on id, color waypoints the same as solid

        var shapeType = shapes.getCurrentType();
        {
            shapes.set(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 1f, 0.5f, 0.75f);
            var radius = 2;
            for (var waypoint : waypoints) {
                var x = waypoint.point.x;
                var y = waypoint.point.y;
                shapes.circle(x, y, radius);
            }
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);

        shapeType = shapes.getCurrentType();
        {
            shapes.set(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(1f, 1f, 0f, 0.2f);
            shapes.rect(bounds.x, bounds.y, bounds.w, bounds.h);
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);
    }

    // ------------------------------------------------------------------------

    private final List<Mover> allMovers = new ArrayList<>();
    private final List<Mover> ridingMovers = new ArrayList<>();
    private void updateOtherMovers(int moveX, int moveY) {
        // update lists of other movers
        {
            allMovers.clear();
            ridingMovers.clear();
            var mover = world().first(Mover.class);
            while (mover != null) {
                allMovers.add(mover);
                if (mover.isRiding(this)) {
                    ridingMovers.add(mover);
                }
                mover = (Mover) mover.next;
            }
        }

        // apply movement to other movers, either pushing or carrying them
        // TODO: pass OnSquish callbacks to moveX|Y when pushing

        // check horizontal movement
        if (moveX != 0) {
            if (moveX > 0) {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push right
                        var amount = this.collider.rect().right() - mover.collider.rect().left();
                        mover.moveX(amount);
                    } else if (ridingMovers.contains(mover)) {
                        // carry right
                        mover.moveX(moveX);
                    }
                }
            } else {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push left
                        var amount = this.collider.rect().left() - mover.collider.rect().right();
                        mover.moveX(amount);
                    } else if (ridingMovers.contains(mover)) {
                        // carry left
                        mover.moveX(moveX);
                    }
                }
            }
        }

        // check vertical movement
        if (moveY != 0) {
            if (moveY > 0) {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push up
                        var amount = this.collider.rect().top() - mover.collider.rect().bottom();
                        mover.moveY(amount);
                    } else if (ridingMovers.contains(mover)) {
                        // carry up
                        mover.moveY(moveY);
                    }
                }
            } else {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push down
                        var amount = this.collider.rect().bottom() - mover.collider.rect().top();
                        mover.moveY(amount);
                    } else if (ridingMovers.contains(mover)) {
                        // carry down
                        mover.moveY(moveY);
                    }
                }
            }
        }
    }

}
