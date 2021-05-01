package zendo.games.grotto.factories;

import com.badlogic.gdx.Gdx;
import zendo.games.grotto.components.*;
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
            anim.depth = 1;

            var bounds = RectI.at(-2, 0, 6, 12);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.player;

            entity.position.set(
                    (int) (position.x - anim.sprite().origin.x),
                    (int) (position.y - anim.sprite().origin.y));
        }
        return entity;
    }

    public static Entity slime(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.add(new Enemy(), Enemy.class);

            var anim = entity.add(new Animator("slime", "idle"), Animator.class);

            var bounds = RectI.at(-5, 0, 10, 10);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            entity.add(new Timer(2f, (self) -> {
                var name = anim.animation().name;
                switch (name) {
                    case "idle" -> {
                        anim.play("walk");
                        self.start(anim.duration());
                    }
                    case "walk" -> {
                        anim.play("hit");
                        self.start(anim.duration());
                    }
                    case "hit" -> {
                        anim.play("death");
                        self.start(anim.duration());
                    }
                    case "death" -> {
                        anim.play("idle");
                        self.start(2f);
                    }
                }
                Gdx.app.log("slime", name + " -> " + anim.animation().name);
            }), Timer.class);

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
