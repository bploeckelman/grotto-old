package zendo.games.grotto.factories;

import zendo.games.grotto.Assets;
import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;
import zendo.games.grotto.utils.Time;

public class CreatureFactory {

    public static Entity player(Assets assets, World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);
            entity.add(new Player(), Player.class);

            var anim = entity.add(new Animator("hero", "idle"), Animator.class);
            anim.depth = 1;

            var bounds = RectI.at(-2, 0, 6, 12);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.player;

            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;
        }
        return entity;
    }

    public static Entity eye(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);
            entity.add(new Enemy(), Enemy.class);

            var anim = entity.add(new Animator("eye", "idle"), Animator.class);

            var bounds = RectI.at(-6, 2, 20, 20);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            // mostly so gravity gets applied
            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;
            mover.gravity = -300;

            // TODO: add timer that changes collider orientation based on animation and facing

            // add timer to trigger additional behavior
            // TODO: probably need a stateful timer that updates regularly instead of this,
            //       it will just act as an update loop and set animation based on current anim state
            //       and relative player position
            entity.add(new Timer(2f, (self) -> {
                var player = self.world().first(Player.class);
                if (player != null) {
                    // TODO: add a raycast to check for line of sight with the player,
                    //  rather than just relying on distance since player may be on a
                    //  different platform

                    var dist = player.entity().position.x - self.entity().position.x;

                    // set facing
                    var dir  = Calc.sign(dist);
                    if (dir == 0) dir = 1;
                    anim.scale.set(dir, 1);

                    // perform action based on distance to player
                    var abs = Calc.abs(dist);
                    var closest = abs < 64;
                    var close   = abs < 128;
                    if (closest) {
                        anim.play("attack");
                        self.start(anim.duration());
                        // TODO: spawn shot
                    }
                    else if (close) {
                        anim.play("emerge");
                        self.entity().add(new Timer(anim.duration(), (timer) -> {
                            anim.play("warn");
                            self.start(anim.duration());
                            timer.destroy();
                        }), Timer.class);
                    }
                    else {
                        anim.play("idle");
                        self.start(anim.duration());
                    }
                } else {
                    anim.play("idle");
                    self.start(anim.duration());
                }
            }), Timer.class);
        }
        return entity;
    }

    public static Entity slime(Assets assets, World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);
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
                                EffectFactory.spriteAnimOneShot(world, entity.position, "slime", "death")
                                        .get(Animator.class).scale.set(anim.scale);
                                entity.destroy();
                            }), Timer.class);
                        }
                    }
                }
            };
        }
        return entity;
    }

    public static Entity goblin(Assets assets, World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);
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
                            EffectFactory.spriteAnimOneShot(world, entity.position, "goblin", "death")
                                    .get(Animator.class).scale.set(anim.scale);
                            entity.destroy();
                        }), Timer.class);
                    }
                }
            };
        }
        return entity;
    }

    public static Entity shroom(World world, Point position) {
        var entity = world.addEntity();
        {
            entity.position.set(position);
            entity.add(new Enemy(), Enemy.class);

            var anim = entity.add(new Animator("shroom", "idle"), Animator.class);

            var bounds = RectI.at(-6, 0, 12, 12);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            var mover = entity.add(new Mover(), Mover.class);
            mover.gravity = -300;
            mover.collider = collider;

            var hurtable = entity.add(new Hurtable(), Hurtable.class);
            hurtable.hurtBy = Collider.Mask.player_attack;
            hurtable.collider = collider;
            // handle a player stomp
            hurtable.hurtCheck = (self) -> {
                var player = self.world().first(Player.class);
                if (player != null) {
                    var playerMover = player.get(Mover.class);
                    if (playerMover.speed.y < 0) {
                        var offset = Point.pool.obtain().set(0, -1);
                        var stomped = self.collider.check(Collider.Mask.player, offset);
                        if (stomped) {
                            // stop the shroom
                            mover.stopX();
                            mover.active = false;
                            Time.pause_for(0.2f);

                            // play the crush animation
                            anim.mode = Animator.LoopMode.none;
                            anim.play("crush");
                            entity.add(new Timer(anim.duration(), (timer) -> {
                                anim.mode = Animator.LoopMode.loop;
                                mover.active = true;
                            }), Timer.class);

                            // mover player up a bit so they don't get hurt
                            player.entity().position.y += 5;
                            // bounce the player up as if they jumped
                            playerMover.speed.y = 155;
                        }
                        Point.pool.free(offset);
                        return stomped;
                    }
                }
                return self.collider.check(self.hurtBy);
            };
            // handle a hurt
            hurtable.onHurt = new Hurtable.OnHurt() {
                // TODO: pass along the method of hurt (stomp vs attack) and play appropriate response animation here
                int health = 3;
                @Override
                public void hurt(Hurtable self) {
                    var player = self.world().first(Player.class);
                    if (player != null) {
                        health -= 1;

                        if (health > 0) {
//                            anim.mode = Animator.LoopMode.none;
//                            anim.play("hurt");
//                            entity.add(new Timer(anim.duration(), (timer) -> anim.mode = Animator.LoopMode.loop), Timer.class);

                            var sign = Calc.sign(self.entity().position.x - player.entity().position.x);
                            mover.speed.x = sign * 120;
                            mover.speed.y = 20;
                        } else {
//                            anim.mode = Animator.LoopMode.none;
//                            anim.play("hurt");

                            // play death animation and self destruct after last hurt animation finishes
                            entity.add(new Timer(anim.duration(), (timer) -> {
                                EffectFactory.spriteAnimOneShot(world, entity.position, "shroom", "death")
                                        .get(Animator.class).scale.set(anim.scale);
                                entity.destroy();
                            }), Timer.class);
                        }
                    }
                }
            };

            // custom behavior
            entity.add(new Component() {
                int dir = 1;
                @Override
                public void update(float dt) {
                    if (hurtable.stunTimer < 0) {
                        anim.play("walk");
                    }

                    // check whether to turn around
                    var feelerDist = 5;
                    var fallOffset = Point.pool.obtain().set(feelerDist * dir, -1);
                    var hitOffset = Point.pool.obtain().set(dir, 0);
                    {
                        var willFallOff = !collider.check(Collider.Mask.solid, fallOffset);
                        var willHitWall = collider.check(Collider.Mask.solid, hitOffset);
                        if (willFallOff || willHitWall) {
                            // stop moving
                            mover.stopX();
                            // turn around
                            dir *= -1;
                        }
                    }
                    Point.pool.free(fallOffset);
                    Point.pool.free(hitOffset);

                    // set facing direction
                    anim.scale.set(dir, 1);

                    // calculate speed for frame
                    var speed = mover.speed.x + dir * 100 * dt;

                    // limit max horizontal speed
                    mover.speed.x = Calc.clampInt((int) speed, -40, 40);
                }
            }, Component.class);
        }
        return entity;
    }

}
