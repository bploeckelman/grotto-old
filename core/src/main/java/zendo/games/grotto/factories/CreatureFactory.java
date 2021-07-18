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
            anim.mode = Animator.LoopMode.none;

            var bounds = RectI.at(-6, 2, 20, 20);
            var collider = entity.add(Collider.makeRect(bounds), Collider.class);
            collider.mask = Collider.Mask.enemy;

            // mostly so gravity gets applied
            var mover = entity.add(new Mover(), Mover.class);
            mover.collider = collider;
            mover.gravity = -300;

            // add custom behavior
            entity.add(new Component() {

                enum State { idle, emerge, attack, retreat }

                private State state = State.idle;
                private float stateTime = 0;
                private boolean didShoot = false;

                private final float threat_range = 100;
                private final float secs_between_attacks = 1f;
                private final float secs_after_retreat = 1f;

                private void changeState(State state) {
//                    Gdx.app.log("state", "from: " + this.state.name() + " to: " + state.name());
                    this.state = state;
                    this.stateTime = 0;
                }

                @Override
                public void update(float dt) {
                    var player = world.first(Player.class);
                    if (player == null) {
                        return;
                    }

                    // get some info about relative player position
                    var dist = player.entity().position.x - entity().position.x;
                    var sign = Calc.sign(dist);
                    var absDist = Calc.abs(dist);

                    // set facing
                    var dir  = Calc.sign(dist);
                    if (dir == 0) dir = 1;
                    anim.scale.set(dir, 1);

                    // is the player within range to trigger an attack
                    boolean inRange = (absDist <= threat_range);

                    // state specific updates
                    stateTime += dt;
                    switch (state) {
                        case idle -> {
                            anim.play("idle");
                            anim.mode = Animator.LoopMode.loop;

                            // update collider position (orientation based on facing happens after state updates)
                            if (stateTime < 0.1f) {
                                collider.rect(0, 0, 1, 1);
                            } else if (stateTime < 0.3f) {
                                collider.rect(-5, 2, 10, 4);
                            } else if (stateTime < 0.6f) {
                                collider.rect(-7, 1, 14, 10);
                            } else if (stateTime < 0.8f) {
                                collider.rect(-5, 2, 10, 4);
                            } else if (stateTime < 0.9f) {
                                collider.rect(0, 0, 1, 1);
                            }

                            if (stateTime >= anim.duration()) {
                                var nextState = (inRange) ? State.emerge : State.idle;
                                changeState(nextState);
                            }
                        }
                        case emerge -> {
                            anim.play("emerge");
                            anim.mode = Animator.LoopMode.none;

                            // update collider position (orientation based on facing happens after state updates)
                            if (stateTime < 0.1f) {
                                collider.rect(0, 0, 1, 1);
                            } else if (stateTime < 0.2f) {
                                collider.rect(-5, 2, 10, 4);
                            } else if (stateTime < 0.3f) {
                                collider.rect(-7, 1, 14, 10);
                            } else if (stateTime < 0.6f) {
                                collider.rect(-4, 1, 15, 17);
                            } else if (stateTime < 0.8f) {
                                collider.rect(-4, 1, 15, 18);
                            } else if (stateTime < 0.9f) {
                                collider.rect(-5, 1, 15, 21);
                            }

                            if (stateTime >= anim.duration()) {
                                var nextState = (inRange) ? State.attack : State.retreat;
                                changeState(nextState);
                            }
                        }
                        case attack -> {
                            anim.play("attack");
                            anim.mode = Animator.LoopMode.none;

                            // update collider position (orientation based on facing happens after state updates)
                            if (stateTime < 0.2f) {
                                collider.rect(-6, 1, 18, 21);
                            } else if (stateTime < 0.3f) {
                                collider.rect(-5, 1, 15, 21);
                            } else if (stateTime < 0.4f) {
                                collider.rect(-4, 1, 15, 18);
                            }

                            // shoot our shot
                            if (!didShoot) {
                                didShoot = true;
                                var shotPosition = Point.at(position.x + dir * 7, position.y);
                                EffectFactory.bullet(world, shotPosition, dir);
                            }

                            if (stateTime >= anim.duration()) {
                                //
                                collider.rect(-4, 1, 15, 18);

                                // either start a new attack or retreat
                                if (stateTime >= anim.duration() + secs_between_attacks) {
                                    didShoot = false;

                                    var nextState = (inRange) ? State.attack : State.retreat;
                                    if (nextState == State.attack) {
                                        boolean restart = true;
                                        anim.play("attack", restart);
                                    }
                                    changeState(nextState);
                                }
                            }
                        }
                        case retreat -> {
                            anim.play("retreat");
                            anim.mode = Animator.LoopMode.none;

                            // update collider position (orientation based on facing happens after state updates)
                            if (stateTime < 0.1f) {
                                collider.rect(-4, 1, 15, 18);
                            } else if (stateTime < 0.3f) {
                                collider.rect(-4, 1, 14, 17);
                            } else if (stateTime < 0.4f) {
                                collider.rect(-7, 1, 14, 10);
                            } else if (stateTime < 0.5f) {
                                collider.rect(-5, 2, 10, 4);
                            } else if (stateTime < 0.6f) {
                                collider.rect(0, 0, 1, 1);
                            }

                            float endTime = anim.duration() + secs_after_retreat;
                            if (stateTime > anim.duration()) {
                                if (stateTime >= endTime) {
                                    changeState(State.idle);
                                }
                            }
                        }
                    }

                    // update collider orientation based on facing direction
                    if (dir < 0) {
                        var rect = collider.rect();
                        rect.x = -(rect.x + rect.w);
                        collider.rect(rect);
                    }
                }
            }, Component.class);
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
