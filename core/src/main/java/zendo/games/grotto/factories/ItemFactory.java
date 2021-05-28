package zendo.games.grotto.factories;

import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

public class ItemFactory {

    public static Entity coin(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);

            var anim = entity.add(new Animator("coin", "idle"), Animator.class);
            anim.depth = 10;

            var bounds = RectI.at(-4, -4, 8, 8);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.item;

            var mover = entity.add(new Mover(), Mover.class);

            var pickup = entity.add(new Pickupable(), Pickupable.class);
            pickup.collider = collider;
            pickup.pickupBy = Collider.Mask.player;
            pickup.onPickup = (self) -> {
                mover.speed.y = 120f;
                entity.add(new Timer(0.1f, (timer) -> {
                    EffectFactory.spriteAnimOneShot(world, entity.position, "coin", "pickup");
                    entity.destroy();
                }), Timer.class);
            };
        }
        return entity;
    }

    public static Entity bacterium(String name, World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);

            var anim = entity.add(new Animator(name, "idle"), Animator.class);
            anim.depth = 1;

            var bounds = RectI.at(-8, -8, 16, 16);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.item;

            // TODO: add a timer or something that 'bounces' this
            var mover = entity.add(new Mover(), Mover.class);

            var pickup = entity.add(new Pickupable(), Pickupable.class);
            pickup.collider = collider;
            pickup.pickupBy = Collider.Mask.player;
            pickup.onPickup = (self) -> {
                // TODO: add a new pickup animation
                EffectFactory.spriteAnimOneShot(world, entity.position, "coin", "pickup");
                entity.destroy();
            };
        }
        return entity;
    }

}
