package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.editor.WorldMap;
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

    private int id;
    private float t;
    private boolean forward;
    private RectI bounds;
    private Vector2 remainder;
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
    }

    @Override
    public void reset() {
        super.reset();
        id = 0;
        t = 0;
        forward = false;
        bounds = null;
        if (remainder != null) {
            VectorPool.dim2.free(remainder);
        }
        remainder = null;
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

        var interp = VectorPool.dim2.obtain();
        {
            // TODO: figure out which waypoints we're between based on t and the sequence numbers
            var start = waypoints.get(0);
            var end   = waypoints.get(waypoints.size() - 1);
            interp.x = (1f - t) * start.point.x + t * end.point.x;
            interp.y = (1f - t) * start.point.y + t * end.point.y;

            var x = (int) interp.x;
            var y = (int) interp.y;

            bounds.setPosition(x, y);
            entity.position.set(x, y);
        }
        VectorPool.dim2.free(interp);

        var dir = forward ? 1 : -1;
        var speed = 0.4f;
        t += dir * speed * dt;

        // TODO: check against actors to
        //  - check for Actor isRiding status (maybe do this in the actor/player class?)
        //  - trigger OnSquish callbacks
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
            shapes.setColor(1f, 1f, 0f, 0.5f);
            shapes.rect(bounds.x, bounds.y, bounds.w, bounds.h);
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);
    }

}
