package zendo.games.grotto.factories;

import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

public class EffectFactory {

    public static Entity bullet(World world, Point position, int dir) {
        var entity = world.addEntity();
        {
            entity.position.set(position);

            entity.add(new Item(), Item.class);

            var anim = entity.add(new Animator("shot", "idle"), Animator.class);
            anim.depth = 10;

            var bounds = RectI.at(-3, -3, 6, 6);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            var mover = entity.add(new Mover(), Mover.class);
            mover.speed.x = dir * 100f;
            mover.speed.y = 0;

            // TODO: destroy bullet when it exits room instead of just after n seconds
            entity.add(new Timer(3f, (self) -> entity.destroy()), Timer.class);
        }
        return entity;
    }

    public static Entity spriteAnimOneShot(World world, Point position, String sprite, String animation) {
        var entity = world.addEntity();
        {
            entity.position.set(position);

            var anim = entity.add(new Animator(sprite, animation), Animator.class);
            anim.depth = 100;

            entity.add(new Timer(anim.duration(), (self) -> self.entity().destroy()), Timer.class);
        }
        return entity;
    }

}
