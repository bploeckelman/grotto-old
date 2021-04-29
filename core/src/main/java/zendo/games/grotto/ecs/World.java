package zendo.games.grotto.ecs;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

import java.util.Comparator;
import java.util.List;

public class World {

    static final int max_component_types = 256;

    private final Pool<Entity> entityPool;
    private final Array<Entity> entitiesAlive;

    private final Pool<Component>[] componentPools;
    private final Array<Component>[] componentsAlive;
    private final Array<Component> componentsVisible;

    public World() {
        entityPool = Pools.get(Entity.class);
        entitiesAlive = new Array<>();

        componentPools = new Pool[max_component_types];
        componentsAlive = new Array[max_component_types];
        componentsVisible = new Array<>();
    }

    public Entity firstEntity() {
        if (entitiesAlive.isEmpty()) {
            return null;
        }
        return entitiesAlive.first();
    }

    public Entity lastEntity() {
        if (entitiesAlive.isEmpty()) {
            return null;
        }
        return entitiesAlive.get(entitiesAlive.size - 1);
    }

    public <T extends Component> T first(Class<T> clazz) {
        int type = Component.Types.id(clazz);
        if (componentsAlive[type] == null || componentsAlive[type].isEmpty()) {
            return null;
        }
        var component = componentsAlive[type].get(0);
        return clazz.cast(component);
    }

    public <T extends Component> T last(Class<T> clazz) {
        int type = Component.Types.id(clazz);
        if (componentsAlive[type] == null || componentsAlive[type].isEmpty()) {
            return null;
        }
        var components = componentsAlive[type];
        var component = components.get(components.size - 1);
        return clazz.cast(component);
    }

    public <T extends Component> T add(Entity entity, T component, Class<T> clazz) {
        if (entity == null) {
            throw new GdxRuntimeException("Entity cannot be null");
        }
        if (entity.world != this) {
            throw new GdxRuntimeException("Entity must be part of this World");
        }

        // get the component type
        int type = Component.Types.id(clazz);
        if (componentPools[type] == null) {
            componentPools[type] = (Pool<Component>) Pools.get(clazz);
        }
        if (componentsAlive[type] == null) {
            componentsAlive[type] = new Array<>();
        }
        Pool<Component> pool = componentPools[type];
        Array<Component> alive = componentsAlive[type];

        // instantiate a new instance
        T instance = (T) pool.obtain();
        if (instance == null) {
            throw new GdxRuntimeException("Component instance could not be instantiated");
        }

        // initialize the new instance
        instance = component;
        instance.type = type;
        instance.entity = entity;

        // add it to the live components;
        var last = alive.isEmpty() ? null : alive.get(alive.size - 1);
        if (last != null) {
            instance.next = last.next;
            instance.prev = last;
            last.next = instance;
        }
        alive.add(instance);

        // add it to the entity
        entity.components.add(instance);

        return instance;
    }

    public Entity addEntity() {
        // create entity instance
        Entity instance = entityPool.obtain();

        // add to list
        var last = entitiesAlive.isEmpty() ? null : entitiesAlive.get(entitiesAlive.size - 1);
        if (last != null) {
            instance.next = last.next;
            instance.prev = last;
            last.next = instance;
        }
        entitiesAlive.add(instance);

        // assign
        instance.world = this;

        return instance;
    }

    public void destroyEntity(Entity entity) {
        if (entity != null && entity.world == this) {
            // destroy components
            for (int i = entity.components.size() - 1; i >= 0; i--) {
                destroy(entity.components.get(i));
            }

            // remove ourselves from the list
            var next = entity.next;
            var prev = entity.prev;
            if (prev != null) prev.next = next;
            if (next != null) next.prev = prev;
            entitiesAlive.removeValue(entity, true);

            // release the instance back to the pool
            entityPool.free(entity);

            entity.world = null;
        }
    }

    public void destroy(Component component) {
        if (component != null && component.entity != null && component.entity.world == this) {
            int type = component.type;

            // mark destroyed
            component.destroyed();

            // remove from entity
            List<Component> list = component.entity.components;
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i) == component) {
                    list.remove(i);
                    break;
                }
            }

            // remove from list
            var next = component.next;
            var prev = component.prev;
            if (prev != null) prev.next = next;
            if (next != null) next.prev = prev;
            componentsAlive[type].removeValue(component, true);

            // release the instance back to the pool
            componentPools[type].free(component);
        }
    }

    public void clear() {
        Entity entity = firstEntity();
        while (entity != null) {
            Entity next = entity.next();
            destroyEntity(entity);
            entity = next;
        }
    }

    public void update(float dt) {
        for (int i = 0; i < Component.Types.count(); i++) {
            if (componentsAlive[i] == null || componentsAlive[i].isEmpty()) {
                continue;
            }

            Component component = componentsAlive[i].first();
            while (component != null) {
                Component next = component.next();
                if (component.active && component.entity.active) {
                    component.update(dt);
                }
                component = next;
            }
        }
    }

    public void render(SpriteBatch batch) {
        // Notes:
        // In general this isn't a great way to render objects
        // Every frame it has to rebuild the list and sort it
        // A more ideal way would be to cache the visible list
        // and insert / remove objects as they update or change
        // their depth

        // assemble list
        for (int i = 0; i < Component.Types.count(); i++) {
            if (componentsAlive[i] == null || componentsAlive[i].isEmpty()) {
                continue;
            }

            Component component = componentsAlive[i].first();
            while (component != null) {
                if (component.visible && component.entity.visible) {
                    componentsVisible.add(component);
                }
                component = component.next();
            }
        }

        // sort by depth
        componentsVisible.sort(Comparator.comparingInt(Component::depth));

        // render them
        for (Component component : componentsVisible) {
            component.render(batch);
        }

        // clear the list for next time around
        componentsVisible.clear();
    }

    public void render(ShapeRenderer shapes) {
        // assemble list
        for (int i = 0; i < Component.Types.count(); i++) {
            if (componentsAlive[i] == null || componentsAlive[i].isEmpty()) {
                continue;
            }

            Component component = componentsAlive[i].first();
            while (component != null) {
                if (component.visible && component.entity.visible) {
                    componentsVisible.add(component);
                }
                component = component.next();
            }
        }

        // sort by depth
        componentsVisible.sort(Comparator.comparingInt(Component::depth));

        // render them
        for (Component component : componentsVisible) {
            component.render(shapes);
        }

        // clear the list for next time around
        componentsVisible.clear();
    }

}
