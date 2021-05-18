package zendo.games.grotto.factories;

import zendo.games.grotto.components.Animator;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Pickupable;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

public class ItemFactory {

    public static Entity coin(World world, Point position) {
        var entity = world.addEntity();
        {
            var anim = entity.add(new Animator("coin", "idle"), Animator.class);

            entity.position.set(
                    (int) (position.x - anim.sprite().origin.x),
                    (int) (position.y - anim.sprite().origin.y));

            var bounds = RectI.at(-4, 0, 8, 8);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.item;

            var pickup = entity.add(new Pickupable(), Pickupable.class);
            pickup.collider = collider;
            pickup.pickupBy = Collider.Mask.player;
            pickup.onPickup = (self) -> {
                var pos = Point.at(entity.position.x, entity.position.y + 4);
                EffectFactory.spriteAnimOneShot(world, pos, "coin", "pickup");
                self.entity().destroy();
            };
        }
        return entity;
    }

}
