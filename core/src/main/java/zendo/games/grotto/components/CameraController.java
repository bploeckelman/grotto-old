package zendo.games.grotto.components;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.map.WorldMap;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.accessors.Vector3Accessor;

public class CameraController extends Component {

    public enum TargetMode { point, entity }

    public OrthographicCamera camera;
    public Point point;
    public Entity entity;
    public WorldMap worldMap;

    private TweenManager tween;
    private TargetMode mode;
    private Vector3 target;
    private Vector2 dist;

    private Entity lastRoom;
    private TweenComponent transition;

    public CameraController() {}

    public CameraController(OrthographicCamera camera, TweenManager tween) {
        this.camera = camera;
        this.point = Point.zero();
        this.target = new Vector3();
        this.dist = new Vector2();
        this.tween = tween;
    }

    @Override
    public void reset() {
        super.reset();
        this.camera = null;
        this.entity = null;
        this.point = null;
        this.tween = null;
        this.mode = null;
        this.target = null;
        this.worldMap = null;
        this.lastRoom = null;
        this.transition = null;
    }

    public void follow(Entity entity, Point offset) {
        follow(entity, offset, false);
    }

    public void follow(Entity entity, Point offset, boolean immediate) {
        // TODO: wire up offset
        this.entity = entity;
        this.mode = TargetMode.entity;
        if (immediate) {
            target.set(entity.position.x, entity.position.y, 0);
            camera.position.set(target);
            camera.update();
            resetRoom();
        }
    }

    public void target(Point point) {
        target(point, false);
    }

    public void target(Point point, boolean immediate) {
        this.point.set(point);
        this.mode = TargetMode.point;
        if (immediate) {
            target.set(point.x, point.y, 0);
            camera.position.set(target);
            camera.update();
            resetRoom();
        }
    }

    public void resetRoom() {
        if (worldMap != null) {
            lastRoom = worldMap.room((int) target.x, (int) target.y);
        }
    }

    public TargetMode mode() {
        return mode;
    }

    @Override
    public void update(float dt) {
        Point targetPoint;
        switch (mode) {
            case point  -> targetPoint = point;
            case entity -> targetPoint = entity.position;
            default     -> targetPoint = Point.zero();
        }

        // update and constrain camera bounds if not currently transitioning,
        // otherwise transition tween will automatically update target until complete
        if (transition == null) {
            // get camera edges
            var cameraHorzEdge = (int) (camera.viewportWidth / 2f);
            var cameraVertEdge = (int) (camera.viewportHeight / 2f);

            // approach target with a speed based on distance
            var speed = 1f;
            dist.x = targetPoint.x - target.x;
            dist.y = targetPoint.y - target.y;
            var absDx = Calc.abs(dist.x);
            var absDy = Calc.abs(dist.y);
            var scaleX = (absDx > 80) ? 160 : 80f;
            var scaleY = (absDy > 20) ? 200 : 100f;
            target.x = Calc.approach(target.x, targetPoint.x, scaleX * speed * dt);
            target.y = Calc.approach(target.y, targetPoint.y, scaleY * speed * dt);

            // lookup the room that the target is currently in (if any)
            var room = worldMap.room(targetPoint);
            if (room != null) {
                // start a transition between rooms if we need to
                // TODO: pause enemies in source and dest rooms during transition
                if (lastRoom != null && lastRoom != room) {
                    // find the bounds for both room and lastRoom;
                    //  clamp two sets of coords so they are in bounds for both rooms
                    //  then create a transition tween to move from lastRoom target to room target
                    //  once complete, set lastRoom = room and destroy the transition component
                    float lastTargetX, lastTargetY;
                    float nextTargetX, nextTargetY;

                    var lastBounds = worldMap.getRoomBounds(lastRoom);
                    var nextBounds = worldMap.getRoomBounds(room);
                    if (lastBounds != null && nextBounds != null) {
                        // clamp the camera to within the last room's bounds
                        var lastLeft   = lastBounds.x + cameraHorzEdge;
                        var lastBottom = lastBounds.y + cameraVertEdge;
                        var lastRight  = lastBounds.x + lastBounds.w - cameraHorzEdge;
                        var lastTop    = lastBounds.y + lastBounds.h - cameraVertEdge;
                        lastTargetX = MathUtils.clamp(camera.position.x, lastLeft, lastRight);
                        lastTargetY = MathUtils.clamp(camera.position.y, lastBottom, lastTop);

                        // clamp the camera to within the next room's bounds
                        var nextLeft   = nextBounds.x + cameraHorzEdge;
                        var nextBottom = nextBounds.y + cameraVertEdge;
                        var nextRight  = nextBounds.x + nextBounds.w - cameraHorzEdge;
                        var nextTop    = nextBounds.y + nextBounds.h - cameraVertEdge;
                        nextTargetX = MathUtils.clamp(target.x, nextLeft, nextRight);
                        nextTargetY = MathUtils.clamp(target.y, nextBottom, nextTop);

                        // pause the player for the duration of the transition
                        var player = world().first(Player.class);
                        player.entity().active = false;

                        // set the transition's starting point
                        target.set(lastTargetX, lastTargetY, 0);

                        // create a transition tween to move from last to next target
                        // TODO: could probably add a self-destruct into the TweenComponent, or a generic onComplete callback where we can self.destroy()
                        float duration = 1.66f;
                        transition = entity.add(new TweenComponent(
                                Tween.to(target, Vector3Accessor.XY, duration)
                                        .target(nextTargetX, nextTargetY)
                                        .setCallback((type, source) -> {
                                            // restart the player
                                            player.entity().active = true;
                                            // update room reference
                                            lastRoom = room;
                                            // kill the transition component
                                            transition.destroy();
                                            transition = null;
                                        })
                                        .start(tween)
                        ), TweenComponent.class);
                    }
                } else {
                    // keep the camera inside the room's bounds
                    var bounds = worldMap.getRoomBounds(room);
                    if (bounds != null) {
                        // clamp the camera to within the current room's bounds
                        var left = bounds.x + cameraHorzEdge;
                        var bottom = bounds.y + cameraVertEdge;
                        var right = bounds.x + bounds.w - cameraHorzEdge;
                        var top = bounds.y + bounds.h - cameraVertEdge;
                        target.x = MathUtils.clamp(target.x, left, right);
                        target.y = MathUtils.clamp(target.y, bottom, top);
                    }
                }
            }
        }

        // move the camera to the currently calculated target position
        camera.position.set((int) target.x, (int) target.y, 0);
        camera.update();
    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();
        {
//            Gdx.app.log("dist", "(" + (int) Calc.abs(dist.x) + ", " + (int) Calc.abs(dist.y) + ")");

            // entity position
            var x = entity.position.x;
            var y = entity.position.y;
            var scale = 0.25f;
            shapes.set(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 0f, 0f, 1f);
            shapes.rect(x, y, dist.x * scale, 1);
            shapes.setColor(0f, 1f, 0f, 1f);
            shapes.rect(x, y, 1, dist.y * scale);
            shapes.setColor(Color.WHITE);
        }
        shapes.set(shapeType);
    }

}
