package zendo.games.grotto.factories;

import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Calc;
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

            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;

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

            var bounds = RectI.at(-6, 0, 13, 12);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;
            mover.gravity = -300;
            mover.friction = 400;
            mover.onHitY = (self) -> {
                anim.play("idle");
                self.stopY();
            };

            Timer moveTimer = entity.add(new Timer(2f, (self) -> {
                if (!mover.onGround()) {
                    self.start(0.05f);
                } else {
                    var player = self.world().first(Player.class);
                    if (player != null) {
                        anim.play("walk");
                        self.start(anim.duration());

                        mover.speed.y = 120;

                        var dir = Calc.sign(player.entity().position.x - self.entity().position.x);
                        if (dir == 0) dir = 1;
                        anim.scale.set(dir, 1);
                        mover.speed.x = dir * 50;
                    }
                }
            }), Timer.class);

            var hurtable = entity.add(new Hurtable(), Hurtable.class);
            hurtable.hurtBy = Collider.Mask.player_attack;
            hurtable.collider = collider;
            hurtable.onHurt = new Hurtable.OnHurt() {
                int health = 3;
                @Override
                public void hurt(Hurtable self) {
                    var player = self.world().first(Player.class);
                    if (player != null) {
                        health -= 1;

                        if (health > 0) {
                            anim.mode = Animator.LoopMode.none;
                            anim.play("hurt");
                            entity.add(new Timer(anim.duration(), (timer) -> anim.mode = Animator.LoopMode.loop), Timer.class);

                            var sign = Calc.sign(self.entity().position.x - player.entity().position.x);
                            mover.speed.x = sign * 120;
                            mover.speed.y = 20;
                        } else {
                            anim.mode = Animator.LoopMode.none;
                            anim.play("hurt");

                            // play death animation and self destruct after last hurt animation finishes
                            entity.add(new Timer(anim.duration(), (timer) -> {
                                EffectFactory.deathAnim(world, entity.position, "slime", "death");
                                entity.destroy();
                            }), Timer.class);
                        }
                    }
                }
            };

            entity.position.set(
                    (int) (position.x - anim.sprite().origin.x),
                    (int) (position.y - anim.sprite().origin.y));
        }
        return entity;
    }

    public static Entity goblin(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.add(new Enemy(), Enemy.class);

            var anim = entity.add(new Animator("goblin", "idle"), Animator.class);

            var bounds = RectI.at(-4, 0, 12, 12);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;
            mover.gravity = -300;
            mover.friction = 300;
            mover.onHitY = (self) -> {
                anim.play("idle");
                self.stopY();
            };

            var moveTimer = entity.add(new Timer(2f, (self) -> {
                if (!mover.onGround()) {
                    self.start(0.05f);
                } else {
                    var player = self.world().first(Player.class);
                    if (player != null) {
                        // get distance to player
                        var dist = player.entity().position.x - self.entity().position.x;

                        // set facing direction
                        var dir = Calc.sign(dist);
                        if (dir == 0) dir = 1;
                        anim.scale.set(dir, 1);

                        // if we're close enough; lunge attack, otherwise just move
                        var close = Calc.abs(dist) < 50;
                        if (close) {
                            anim.play("attack");
                            self.start(anim.duration());
                            mover.speed.x = dir * 140;
                        } else {
                            anim.play("run");
                            self.start(anim.duration());
                            mover.speed.x = dir * 80;
                        }
                    }
                }
            }), Timer.class);

            var hurtable = entity.add(new Hurtable(), Hurtable.class);
            hurtable.hurtBy = Collider.Mask.player_attack;
            hurtable.collider = collider;
            hurtable.onHurt = new Hurtable.OnHurt() {
                int health = 5;
                @Override
                public void hurt(Hurtable self) {
                    health--;

                    if (health > 0) {
                        var player = self.world().first(Player.class);
                        if (player != null) {
                            anim.mode = Animator.LoopMode.none;
                            anim.play("hurt");
                            entity.add(new Timer(anim.duration(), (timer) -> anim.mode = Animator.LoopMode.loop), Timer.class);

                            var sign = Calc.sign(self.entity().position.x - player.entity().position.x);
                            mover.speed.x = sign * 150;
                            mover.speed.y = 80;
                        }
                    } else {
                        anim.mode = Animator.LoopMode.none;
                        anim.play("hurt");

                        // play death animation and self destruct after last hurt animation finishes
                        entity.add(new Timer(anim.duration(), (timer) -> {
                            EffectFactory.deathAnim(world, entity.position, "goblin", "death");
                            entity.destroy();
                        }), Timer.class);
                    }
                }
            };

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
