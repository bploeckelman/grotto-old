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

            // TODO: check against actors to
            //  - trigger OnSquish callbacks

            // move riders if needed
            // TODO: still need to handle non-riders by pushing and potentially squishing
            int moveX = x - bounds.x;
            int moveY = y - bounds.y;
            var riding = getRidingMovers();
            for (var rider : riding) {
                rider.moveX(moveX);
                rider.moveY(moveY);
            }

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

    private List<Mover> ridingMovers = new ArrayList<>();
    private List<Mover> getRidingMovers() {
        ridingMovers.clear();
        var mover = world().first(Mover.class);
        while (mover != null) {
            if (mover.isRiding(this)) {
                ridingMovers.add(mover);
            }
            mover = (Mover) mover.next;
        }
        return ridingMovers;
    }

}
