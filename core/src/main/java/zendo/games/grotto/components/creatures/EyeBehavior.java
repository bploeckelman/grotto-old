package zendo.games.grotto.components.creatures;

import zendo.games.grotto.components.Animator;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Player;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.factories.EffectFactory;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;

public class EyeBehavior extends Component {

    private final float threat_range = 100f;
    private final float secs_between_attacks = 1f;
    private final float secs_after_retreat = 1f;

    enum State { idle, emerge, attack, retreat }

    private State state;
    private float stateTime;
    private boolean didShoot;

    public EyeBehavior() {
        state = State.idle;
    }

    @Override
    public void reset() {
        super.reset();
        this.state = null;
        this.stateTime = 0;
        this.didShoot = false;
    }

    @Override
    public void update(float dt) {
        // nothing to target without a player
        var player = world().first(Player.class);
        if (player == null) {
            return;
        }

        // get components
        var anim = get(Animator.class);
        var collider = get(Collider.class);

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
                    var shotPosition = Point.at(entity.position.x + dir * 7, entity.position.y + 11);
                    EffectFactory.bullet(world(), shotPosition, dir);
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

    private void changeState(State state) {
//        Gdx.app.log("state", "eye changed state from: " + this.state.name() + " to: " + state.name());
        this.state = state;
        this.stateTime = 0;
    }

}
