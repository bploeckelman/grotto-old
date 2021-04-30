package zendo.games.grotto.factories;

import zendo.games.grotto.components.Animator;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Player;
import zendo.games.grotto.components.Timer;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

public class CreatureFactory {

    public static Entity player(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.add(new Player(), Player.class);

            var anim = entity.add(new Animator("hero", "idle"), Animator.class);

            var bounds = RectI.at(-2, 0, 6, 12);
            entity.add(Collider.makeRect(bounds), Collider.class);

            entity.position.set(
                    (int) (position.x - anim.sprite().origin.x),
                    (int) (position.y - anim.sprite().origin.y));
        }
        return entity;
    }

    public static Entity stabby(World world, Point position) {
        var entity = world.addEntity();
        {
            var anim = entity.add(new Animator("player", "idle-down"), Animator.class);

            entity.add(new Timer(anim.animation().duration(), self -> {
                // toggle between animations
                if (anim.animation().name.equals("idle-down")) {
                    anim.play("attack-down");
                } else {
                    anim.play("idle-down");
                }
                // restart timer
                self.start(anim.animation().duration());
            }), Timer.class);

            entity.position.set(
                    (int) (position.x - anim.sprite().origin.x),
                    (int) (position.y - anim.sprite().origin.y));
        }
        return entity;
    }

}
