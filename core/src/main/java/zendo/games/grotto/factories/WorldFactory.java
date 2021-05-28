package zendo.games.grotto.factories;

import zendo.games.grotto.components.Collider;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.RectI;

public class WorldFactory {

    public static Entity boundary(World world, RectI rect) {
        var entity = world.addEntity();
        {
            var collider = entity.add(Collider.makeRect(rect), Collider.class);
            collider.mask = Collider.Mask.solid;

            entity.position.set(rect.x, rect.y);
        }
        return entity;
    }

}
