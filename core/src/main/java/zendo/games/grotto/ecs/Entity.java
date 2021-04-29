package zendo.games.grotto.ecs;

import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.grotto.utils.Point;

import java.util.ArrayList;
import java.util.List;

public class Entity extends ListNode<Entity> {

    public boolean active;
    public boolean visible;
    public World world;
    public final Point position;
    public final List<Component> components;

    public Entity() {
        this.position = Point.zero();
        this.components = new ArrayList<>();
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        this.active = true;
        this.visible = true;
        this.world = null;
        this.position.set(0, 0);
        this.components.clear();
    }

    public void destroy() {
        world.destroyEntity(this);
    }

    public <T extends Component> T add(T component, Class<T> clazz) {
        if (world == null) {
            throw new GdxRuntimeException("Entity must be assigned to a World");
        }
        return world.add(this, component, clazz);
    }

    public <T extends Component> T get(Class<T> clazz) {
        if (world == null) {
            throw new GdxRuntimeException("Entity must be assigned to a World");
        }
        for (Component component : components) {
            if (component.type == Component.Types.id(clazz)) {
                return clazz.cast(component);
            }
        }
        return null;
    }

}
