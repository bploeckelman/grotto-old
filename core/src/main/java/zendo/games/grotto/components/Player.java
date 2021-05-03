package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.input.VirtualButton;
import zendo.games.grotto.input.VirtualStick;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.RectI;
import zendo.games.grotto.utils.Time;

public class Player extends Component {

    private enum State { normal, attack }

    private static final float gravity = -400;
    private static final float friction = 800;
    private static final float jump_time = 0.18f;
    private static final float jump_impulse = 130;
    private static final float ground_accel = 500;
    private static final float ground_speed_max = 100;

    private int facing = 1;
    private float jumpTimer;
    private float attackTimer;
    private boolean onGround;
    private boolean canJump;
    private State state;
    private VirtualStick stick;
    private VirtualButton jumpButton;
    private VirtualButton attackButton;
    private Entity attackEntity;
    private Collider attackCollider;
    private Animator attackEffectAnim;

    public Player() {
        super();
        stick = new VirtualStick()
                .addButtons(0, Input.Button.left, Input.Button.right, Input.Button.up, Input.Button.down)
                .addKeys(Input.Key.left, Input.Key.right, Input.Key.up, Input.Key.down)
                .addKeys(Input.Key.a, Input.Key.d, Input.Key.w, Input.Key.s)
                .pressBuffer(0.15f);
        jumpButton = new VirtualButton()
                .addButton(0, Input.Button.a)
                .addKey(Input.Key.space)
                .pressBuffer(0.15f);
        attackButton = new VirtualButton()
                .addButton(0, Input.Button.x)
                .addKey(Input.Key.control_left)
                .addKey(Input.Key.f)
                .pressBuffer(0.15f);
    }

    @Override
    public void reset() {
        super.reset();
        onGround = false;
        canJump = false;
        jumpTimer = 0;
        state = State.normal;
        stick = null;
        jumpButton = null;
        attackButton = null;
        attackEntity = null;
        attackCollider = null;
        attackEffectAnim = null;
    }

    @Override
    public void update(float dt) {
        // get components
        var anim = get(Animator.class);
        var mover = get(Mover.class);

        // store previous state
        var wasOnGround = onGround;
        onGround = mover.onGround();

        // handle input
        var moveDir = 0;
        {
            jumpButton.update();
            attackButton.update();

            var sign = 0;
            stick.update();
            if (stick.pressed()) {
                stick.clearPressBuffer();
                var move = stick.value();
                if      (move.x < 0) sign = -1;
                else if (move.x > 0) sign = 1;
            }
            moveDir = sign;
        }

        // update sprite
        {
            // landing squish
            if (!wasOnGround && onGround) {
                anim.scale.set(facing * 1.5f, 0.7f);
            }

            // lerp scale back to one
            anim.scale.x = Calc.approach(anim.scale.x, facing, Time.delta * 4);
            anim.scale.y = Calc.approach(anim.scale.y, 1, Time.delta * 4);

            // set facing
            anim.scale.x = Calc.abs(anim.scale.x) * facing;
        }

        // state handling
        // ----------------------------------------------------------
        if (state == State.normal) {
            // stopped
            if (onGround) {
                if (moveDir != 0) {
                    anim.play("run");
                } else {
                    if (wasOnGround) {
                        anim.play("idle");
                    }
                }
            } else {
                if (mover.speed.y > 0) {
                    anim.play("jump");
                } else {
                    anim.play("fall");
                }
            }

            // horizontal movement
            {
                // acceleration
                var accel = ground_accel;
                mover.speed.x += moveDir * accel * dt;

                // max speed
                var max = ground_speed_max;
                if (Calc.abs(mover.speed.x) > max) {
                    mover.speed.x = Calc.approach(mover.speed.x, Calc.sign(mover.speed.x) * max, 2000 * dt);
                }

                // friction
                if (moveDir == 0 && onGround) {
                    mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
                }

                // facing
                if (moveDir != 0) {
                    facing = moveDir;
                }
            }

            // vertical movement (jump trigger)
            {
                if (onGround && !canJump) {
                    canJump = jumpButton.released();
                }

                if (canJump && jumpButton.pressed()) {
                    jumpButton.clearPressBuffer();

                    // squoosh on jump
                    anim.scale.set(facing * 0.65f, 1.4f);

                    jumpTimer = jump_time;
                    canJump = false;
                }
            }

            // attack trigger
            if (attackButton.pressed()) {
                attackButton.clearPressBuffer();
                state = State.attack;
                attackTimer = 0;

                if (attackEntity == null) {
                    attackEntity = world().addEntity();
                    attackEntity.position.set(entity.position);
                    // entity position gets updated during attack state based of facing

                    attackCollider = attackEntity.add(Collider.makeRect(new RectI()), Collider.class);
                    attackCollider.mask = Collider.Mask.player_attack;
                    // the rect for this collider is updated during attack state based on what frame is active

                    attackEffectAnim = attackEntity.add(new Animator("hero", "attack-effect"), Animator.class);
                    attackEffectAnim.depth = 2;
                }

                if (onGround) {
                    mover.stopX();
                }
            }
        }
        // ----------------------------------------------------------
        else if (state == State.attack) {
            anim.play("attack");
            attackEffectAnim.play("attack-effect");
            attackTimer += dt;

            // setup collider based on what frame is currently activated in the attack animation
            // assumes right facing, if left facing it gets flips after

            if (attackTimer < 0.1f) {
                attackCollider.rect(-6, 1, 12, 9);
            } else if (attackTimer < 0.2f) {
                attackCollider.rect(-8, 3, 15, 7);
            } else if (attackTimer < 0.3f) {
                attackCollider.rect(-8, 8, 5, 4);
            } else if (attackTimer < 0.4f) {
                attackCollider.rect(-8, 8, 3, 4);
            }

            // flip collider if facing left
            var collider = get(Collider.class);
            if (attackEntity != null) {
                var animW = attackEffectAnim.frame().image.getRegionWidth();
                if (facing >= 0) {
                    attackEntity.position.x = entity.position.x + collider.rect().right() + 8;
                    if (attackEffectAnim.scale.x != 1) {
                        attackEffectAnim.scale.x = 1;
                    }
                } else if (facing < 0) {
                    attackEntity.position.x = entity.position.x - collider.rect().left() - 12;
                    if (attackEffectAnim.scale.x != -1) {
                        attackEffectAnim.scale.x = -1;
                    }
                    // mirror the collider horizontally
                    var rect = attackCollider.rect();
                    rect.x = -(rect.x + rect.w);
                    attackCollider.rect(rect);
                }
            }

            // end the attack
            var duration = 0.38f;
            if (attackTimer >= duration) {
                if (attackEntity != null) {
                    attackEntity.destroy();
                    attackEntity = null;
                    attackCollider = null;
                    attackEffectAnim = null;
                }

                anim.play("idle");
                state = State.normal;
            }
        }
        // ----------------------------------------------------------

        // variable duration jumping
        if (jumpTimer > 0) {
            jumpTimer -= dt;

            mover.speed.y = jump_impulse;

            if (!jumpButton.down()) {
                jumpTimer = 0f;
            }
        }

        // gravity
        if (!onGround) {
            // make gravity more 'hovery' when in the air
            var grav = gravity;
            if (Calc.abs(mover.speed.y) < 20 && jumpButton.down()) {
                grav *= 0.4f;
            }

            mover.speed.y += grav * dt;
        }
    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();

        // entity position
        {
            var x = entity.position.x;
            var y = entity.position.y;
            var radius = 1;
            shapes.set(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(1f, 0f, 1f, 0.75f);
            shapes.circle(x, y, radius);
            shapes.setColor(Color.WHITE);
        }

        shapes.set(shapeType);
    }

}
