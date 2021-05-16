package zendo.games.grotto.factories;

import zendo.games.grotto.components.Animator;
import zendo.games.grotto.components.Timer;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Point;

public class EffectFactory {

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
