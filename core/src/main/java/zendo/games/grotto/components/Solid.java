package zendo.games.grotto.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.map.WorldMap;
import zendo.games.grotto.factories.EffectFactory;
import zendo.games.grotto.utils.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Solid extends Component {

    private static Color[] debug_colors = new Color[] {
            Color.BLUE,
            Color.GREEN,
            Color.YELLOW,
            Color.RED,
            Color.VIOLET,
            Color.NAVY,
            Color.CHARTREUSE,
            Color.GOLD,
            Color.SCARLET,
            Color.MAROON,
            Color.ROYAL,
            Color.LIME,
            Color.GOLDENROD,
            Color.CORAL,
            Color.SLATE,
            Color.FOREST,
            Color.ORANGE,
            Color.SALMON,
            Color.SKY,
            Color.OLIVE,
            Color.BROWN,
            Color.PINK,
            Color.CYAN,
            Color.TAN,
            Color.MAGENTA,
            Color.TEAL,
            Color.FIREBRICK,
            Color.PURPLE
    };
    private static int next_debug_color = 0;

    // TODO: additional info like interp method, speed, maybe other things
    static class Waypoint {
        Point point;
    }

    public float speed;
    public RectI bounds;
    public Collider collider;

    private String id;
    private float t;
    private Vector2 remainder;
    private Color debugColor;

    private boolean forward;
    private List<Waypoint> waypoints;

    public Solid() {}

    public Solid(WorldMap.SolidInfo info, List<WorldMap.WaypointInfo> waypointInfos) {
        // make sure the waypoints are in order
        waypointInfos.sort(Comparator.comparing(waypointInfo -> waypointInfo.sequence));

        this.id = info.id;
        this.debugColor = debug_colors[next_debug_color++];
        this.speed = info.speed;
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
        id = null;
        speed = 0;
        t = 0;
        forward = false;
        bounds = null;
        collider = null;
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

        // interpolate between waypoints
        var interp = VectorPool.dim2.obtain();
        {
            int lastWaypoint = (waypoints.size() - 1);
            // figure out which waypoints to transition between (assumes waypoints are in sequence order)
            int segment = (int) Calc.floor(t * lastWaypoint);
            segment = Calc.max(segment, 0);
            segment = Calc.min(segment, lastWaypoint - 1);

            // adjust main timer to move across current segment
            float tSegment = t * lastWaypoint - segment;

            // interpolate across current segment
            var start = waypoints.get(segment);
            var end   = waypoints.get(segment + 1);
            interp.x = (1f - tSegment) * start.point.x + (tSegment) * end.point.x;
            interp.y = (1f - tSegment) * start.point.y + (tSegment) * end.point.y;

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
        t += dir * speed * dt;
    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();
        {
            shapes.set(ShapeRenderer.ShapeType.Line);
            shapes.setColor(debugColor.r, debugColor.g, debugColor.b, 1);
            shapes.rect(bounds.x, bounds.y, bounds.w, bounds.h);
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);

        shapeType = shapes.getCurrentType();
        {
            shapes.set(ShapeRenderer.ShapeType.Line);
            shapes.setColor(debugColor.r, debugColor.g, debugColor.b, 0.75f);
            var radius = 2;
            for (var waypoint : waypoints) {
                var x = waypoint.point.x;
                var y = waypoint.point.y;
                shapes.circle(x, y, radius);
            }
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);
    }

    // ------------------------------------------------------------------------

    private final List<Mover> allMovers = new ArrayList<>();
    private final List<Mover> ridingMovers = new ArrayList<>();
    private final Mover.OnSquish onSquish = new Mover.OnSquish() {
        boolean triggered = false;
        @Override
        public void squish(Mover mover) {
            if (!triggered) {
                triggered = true;
                Time.pause_for(0.2f);
                EffectFactory.squish(world(), mover.entity().position);
                if (mover.get(Player.class) != null) {
                    // TODO: execution of updateOtherMovers continues after triggering this
                    //       need a better way to trigger a global state reload
//                    world().first(GameContainer.class).game.reload();
                    Gdx.app.log("player squished", "");
                } else {
                    mover.entity().destroy();
                }
            }
        }
    };
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
        // TODO: when pushing off a ledge, the push completes before gravity takes effect

        // check horizontal movement
        if (moveX != 0) {
            if (moveX > 0) {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push right
                        var thisRect = this.collider.worldRect();
                        var thatRect = mover.collider.worldRect();
                        var amount = thisRect.right() - thatRect.left();
                        mover.moveX(amount, onSquish);
                    } else if (ridingMovers.contains(mover)) {
                        // carry right
                        mover.moveX(moveX, null);
                    }
                }
            } else {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push left
                        var thisRect = this.collider.worldRect();
                        var thatRect = mover.collider.worldRect();
                        var amount = thisRect.left() - thatRect.right();
                        mover.moveX(amount, onSquish);
                    } else if (ridingMovers.contains(mover)) {
                        // carry left
                        mover.moveX(moveX, null);
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
                        var thisRect = this.collider.worldRect();
                        var thatRect = mover.collider.worldRect();
                        var amount = thisRect.top() - thatRect.bottom();
                        mover.moveY(amount, onSquish);
                    } else if (ridingMovers.contains(mover)) {
                        // carry up
                        mover.moveY(moveY, null);
                    }
                }
            } else {
                for (var mover : allMovers) {
                    if (mover.collider == null) continue;
                    if (mover.collider.overlaps(this.collider, Point.zero())) {
                        // push down
                        var thisRect = this.collider.worldRect();
                        var thatRect = mover.collider.worldRect();
                        var amount = thisRect.bottom() - thatRect.top();
                        mover.moveY(amount, onSquish);
                    } else if (ridingMovers.contains(mover)) {
                        // carry down
                        mover.moveY(moveY, null);
                    }
                }
            }
        }
    }

}
