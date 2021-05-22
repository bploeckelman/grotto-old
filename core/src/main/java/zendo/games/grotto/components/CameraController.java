package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.editor.Level;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;

public class CameraController extends Component {

    public enum TargetMode { point, entity }

    public OrthographicCamera camera;
    public Point point;
    public Entity entity;
    public Level level;

    private TargetMode mode;
    private Vector3 target;
    private Vector2 dist;

    public CameraController() {}

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
        this.point = Point.zero();
        this.target = new Vector3();
        this.dist = new Vector2();
    }

    @Override
    public void reset() {
        super.reset();
        this.camera = null;
        this.entity = null;
        this.point = null;
        this.mode = null;
        this.target = null;
        this.level = null;
    }

    public void setTarget(Entity entity) {
        setTarget(entity, false);
    }

    public void setTarget(Entity entity, boolean immediate) {
        this.entity = entity;
        this.mode = TargetMode.entity;
        if (immediate) {
            target.set(entity.position.x, entity.position.y, 0);
            camera.position.set(target);
            camera.update();
        }
    }

    public void setTarget(Point point) {
        setTarget(point, false);
    }

    public void setTarget(Point point, boolean immediate) {
        this.point.set(point);
        this.mode = TargetMode.point;
        if (immediate) {
            target.set(point.x, point.y, 0);
            camera.position.set(target);
            camera.update();
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

        // update target
        var speed = 50f;
        dist.x = targetPoint.x - target.x;
        dist.y = targetPoint.y - target.y;
        var scaleX = Calc.abs(dist.x) < ((1f / 4f) * camera.viewportWidth)  ? 1 : 1.5f;
        var scaleY = Calc.abs(dist.y) < ((1f / 5f) * camera.viewportHeight) ? 1 : 5f;
        target.x = Calc.approach(target.x, targetPoint.x, scaleX * speed * dt);
        target.y = Calc.approach(target.y, targetPoint.y, scaleY * speed * dt);

        // keep in bounds
        if (level != null) {
            var room = level.room(targetPoint.x, targetPoint.y);
            if (room != null) {
                var bounds = level.getRoomBounds(room);
                if (bounds != null) {
                    // clamp the camera to within the current room's bounds
                    var cameraHorzEdge = (int) (camera.viewportWidth / 2f);
                    var cameraVertEdge = (int) (camera.viewportHeight / 2f);
                    var left   = bounds.x + cameraHorzEdge;
                    var bottom = bounds.y + cameraVertEdge;
                    var right  = bounds.x + bounds.w - cameraHorzEdge;
                    var top    = bounds.y + bounds.h - cameraVertEdge;
                    target.x = MathUtils.clamp(target.x, left, right);
                    target.y = MathUtils.clamp(target.y, bottom, top);
                }
            }
        }

        camera.position.set((int) target.x, (int) target.y, 0);
        camera.update();
    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();
        {
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
