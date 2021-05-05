package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;

public class CameraController extends Component {

    public enum TargetMode { point, entity }

    public OrthographicCamera camera;
    public Point point;
    public Entity entity;

    private TargetMode mode;
    private Vector3 target;

    public CameraController() {}

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
        this.point = Point.zero();
        this.target = new Vector3();
    }

    @Override
    public void reset() {
        super.reset();
        this.camera = null;
        this.entity = null;
        this.point = null;
        this.mode = null;
        this.target = null;
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

        var speed = 50f;
        target.x = Calc.approach(target.x, targetPoint.x, speed * dt);
        target.y = Calc.approach(target.y, targetPoint.y, speed * dt);
        camera.position.set((int) target.x, (int) target.y, 0);
        camera.update();
    }

}
