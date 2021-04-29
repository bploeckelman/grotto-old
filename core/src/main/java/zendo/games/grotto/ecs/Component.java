package zendo.games.grotto.ecs;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.HashMap;
import java.util.Map;

public class Component extends ListNode<Component> {

    protected Entity entity;

    public boolean active;
    public boolean visible;

    public int type;
    public int depth;

    public String tag;

    public Component() {
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        this.entity = null;
        this.type = 0;
        this.depth = 0;
        this.active = true;
        this.visible = true;
        this.tag = "";
    }

    public void update(float dt) {}
    public void render(SpriteBatch batch) {}
    public void render(ShapeRenderer shapes) {}
    public void destroyed() {}

    public Entity entity() {
        return entity;
    }

    public World world() {
        return (entity != null) ? entity.world : null;
    }

    public int type() {
        return type;
    }

    public int depth() {
        return depth;
    }

    public void destroy() {
        if (entity != null && entity.world != null) {
            entity.world.destroy(this);
        }
    }

    public <T extends Component> T get(Class<T> clazz) {
        if (entity == null) {
            throw new GdxRuntimeException("Component must be assigned to an Entity");
        }
        return entity.get(clazz);
    }

    static class Types {
        private static int counter = 0;

        private static final Map<Class<? extends Component>, Integer> componentTypeMap= new HashMap<>();
        private static final Map<Integer, Class<? extends Component>> typeComponentMap = new HashMap<>();

        public static int count() { return counter; }

        public static int id(Class<? extends Component> clazz) {
            if (!componentTypeMap.containsKey(clazz)) {
                final int type = Types.counter++;
                componentTypeMap.put(clazz, type);
                typeComponentMap.put(type, clazz);
            }
            return componentTypeMap.get(clazz);
        }
    }

}
