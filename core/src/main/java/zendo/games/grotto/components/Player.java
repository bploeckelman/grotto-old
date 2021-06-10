package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.input.VirtualButton;
import zendo.games.grotto.input.VirtualStick;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;
import zendo.games.grotto.utils.Time;

public class Player extends Component {

    private enum State { normal, attack, hurt }

    private static final float gravity = -400;
    private static final float gravity_peak = gravity * 0.5f;
    private static final float gravity_fastfall = gravity * 2f;
    private static final float gravity_wallsliding = gravity * 0.33f;
    private static final float maxfall = -100;
    private static final float maxfall_fastfall = -150;
    private static final float maxfall_wallsliding = -50;
    private static final float friction = 800;
    private static final float hurt_friction = 200;
    private static final float hurt_duration = 0.5f;
    private static final float invincible_duration = 1.5f;
    private static final float hover_grav = 0.5f;
    private static final float jump_time = 0.15f;
    private static final float jump_impulse = 155;
    private static final float ground_accel = 500;
    private static final float ground_speed_max = 100;
    private static final Vector2 acceleration_air = new Vector2(600, 600);
    private static final Vector2 acceleration_ground = new Vector2(500, 500);
    private static final Vector2 acceleration_turnaround = new Vector2(50, 50);
    private static final Vector2 grounded_normal = new Vector2(500, 500);
    private static final float maxspeed_ground = 100;
    private static final float maxspeed_air = 100;
    private static final float slash_cooldown = 0.2f;

    private int facing = 1;
    private float jumpTimer;
    private float attackTimer;
    private float hurtTimer;
    private float invincibleTimer;
    private float runStartupTimer;
    private float slashCooldownTimer;
    private boolean onGround;
    private boolean canJump;
    private boolean ducking;
    private boolean turning;
    private State state;
    private VirtualStick stick;
    private VirtualButton jumpButton;
    private VirtualButton attackButton;
    private VirtualButton duckButton;
    private Entity attackEntity;
    private Collider attackCollider;
    private Animator attackEffectAnim;

    public Player() {
        super();
        stick = new VirtualStick()
                .addButtons(0, Input.Button.left, Input.Button.right, Input.Button.up, Input.Button.down)
                .addAxes(0, Input.Axis.leftX, Input.Axis.leftY, 0.2f)
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
        duckButton = new VirtualButton()
                .addAxis(0, Input.Axis.leftY, 0.5f, true)
                .addButton(0, Input.Button.down)
                .addKey(Input.Key.z)
                .addKey(Input.Key.down)
                .pressBuffer(0.15f);
        canJump = true;
    }

    @Override
    public void reset() {
        super.reset();
        onGround = false;
        canJump = false;
        ducking = false;
        turning = false;
        jumpTimer = 0;
        attackTimer = 0;
        hurtTimer = 0;
        invincibleTimer = 0;
        runStartupTimer = 0;
        state = State.normal;
        stick = null;
        jumpButton = null;
        attackButton = null;
        duckButton = null;
        attackEntity = null;
        attackCollider = null;
        attackEffectAnim = null;
    }

    @Override
    public void update(float dt) {
//        oldUpdate(dt);
        newUpdate(dt);
    }

    public void oldUpdate(float dt) {
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
            duckButton.update();

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
                    attackEffectAnim.mode = Animator.LoopMode.none;
                    attackEffectAnim.depth = 2;
                }
            }
        }
        // ----------------------------------------------------------
        else if (state == State.attack) {
            attackTimer += dt;

            // play the attack animation if we're not moving
            anim.play((moveDir == 0) ? "attack" : "run");

            // trigger the slash animation
            attackEffectAnim.play("attack-effect");

            // apply friction
            if (moveDir == 0 && onGround) {
                mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
            }

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

            // update animation and collider position/orientation based on facing direction
            var collider = get(Collider.class);
            if (attackEntity != null) {
                if (facing >= 0) {
                    // update attack effect position
                    attackEntity.position.x = entity.position.x + collider.rect().right() + 8;
                    attackEntity.position.y = entity.position.y;
                    // make sure animation points in the right direction
                    if (attackEffectAnim.scale.x != 1) {
                        attackEffectAnim.scale.x = 1;
                    }
                } else if (facing < 0) {
                    // update attack effect position
                    attackEntity.position.x = entity.position.x - collider.rect().left() - 12;
                    attackEntity.position.y = entity.position.y;
                    // make sure animation points in the left direction
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
            if (attackTimer >= attackEffectAnim.duration()) {
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
        else if (state == State.hurt) {
            anim.mode = Animator.LoopMode.none;
            anim.play("hurt");

            hurtTimer -= dt;
            if (hurtTimer <= 0) {
                state = State.normal;
                anim.mode = Animator.LoopMode.loop;
            }

            mover.speed.x = Calc.approach(mover.speed.x, 0, hurt_friction * dt);
        }

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
                grav *= hover_grav;
            }

            // wall sliding, kinda sorta
//            if (mover.speed.y < 0 &&
//                (mover.collider.check(Collider.Mask.solid, Point.at(-1, 0))
//              || mover.collider.check(Collider.Mask.solid, Point.at(1, 0)))) {
//                grav *= 0.2f;
//            }

            mover.speed.y += grav * dt;
        }

        // invincibility timer & flicker
        if (state != State.hurt && invincibleTimer > 0) {
            if (Time.on_interval(0.05f)) {
                anim.visible = !anim.visible;
            }

            invincibleTimer -= dt;
            if (invincibleTimer <= 0) {
                anim.visible = true;
            }
        }

        // hurt check
        if (invincibleTimer <= 0) {
            var collider = get(Collider.class);
            var hitbox = collider.first(Collider.Mask.enemy);
            if (hitbox != null) {
                // kill an attack in progress
                if (attackEntity != null) {
                    attackEntity.destroy();
                    attackEntity = null;
                    attackCollider = null;
                    attackEffectAnim = null;
                }

                // bounce back
                mover.speed.set(-facing * 100, 80);

                // todo - lose health

                // initialize timers
                hurtTimer = hurt_duration;
                invincibleTimer = invincible_duration;

                Time.pause_for(0.1f);
                state = State.hurt;
            }
        }
    }

    public void newUpdate(float dt) {
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

        // TODO TEMP: set animation
        anim.play("idle");

        // ----------------------------------------

        // handle gravity & jumping
        updateVerticalSpeed(dt, moveDir);

        // jumping
        {
            if (tryJump()) {
                // cancel backwards horizontal movement
                if (Calc.sign(mover.speed.x) == -moveDir) {
                    mover.speed.x = 0;
                }

                // push out the way we're inputting
                facing = moveDir;
                mover.speed.x += moveDir * 50;
            }

            tryWallJump();
        }

        // ducking
        {
            boolean wasDucking = ducking;
            ducking = onGround && duckButton.down();
            duckButton.clearPressBuffer();

            // stopped ducking, squash and stretch
            if (wasDucking && !ducking) {
                anim.scale.set(0.7f, 1.2f);
            }
        }

        // acceleration
        turning = false;
        if (moveDir != 0 && !ducking && runStartupTimer <= 0) {
            var prevSpeedX = mover.speed.x;

            if (onGround) {
                facing = moveDir;
            }

            // get accel value
            // TODO: pool me
            var accelerationAmount = new Vector2();
            if (onGround) {
                if (moveDir == -Calc.sign(mover.speed.x)) {
                    turning = true;
                    accelerationAmount.set(
                            -grounded_normal.y * moveDir * acceleration_turnaround.x,
                             grounded_normal.x * moveDir * acceleration_turnaround.y
                    );
                } else {
                    if (mover.speed.len() < maxspeed_ground) {
                        accelerationAmount.set(
                                -grounded_normal.y * moveDir * acceleration_ground.x,
                                 grounded_normal.x * moveDir * acceleration_ground.y
                        );
                    }
                }
            } else if (Calc.abs(mover.speed.x) < maxspeed_air || moveDir == Calc.sign(mover.speed.x)) {
                accelerationAmount.set(
                        1f * moveDir * acceleration_air.x,
                        0f * moveDir * acceleration_air.y
                );
            }

            // apply
            mover.speed.x += accelerationAmount.x * dt;
            mover.speed.y += accelerationAmount.y * dt;

            // freeze-frame on turnaround
            if (turning && Calc.sign(prevSpeedX) != Calc.sign(mover.speed.x)) {
                Time.pause_for(0.05f);
            }
        }

        updateHorizontalSpeed(moveDir == 0 || ducking);

        trySlash();

        // https://github.com/ExOK/Celeste2/blob/main/player.lua
        // https://github.com/NoelFB/Celeste/blob/master/Source/PICO-8/Classic.cs#L203
    }

    private boolean tryJump() {
        /*
        if (jumpButton.pressed() && air_timer < coyote_time) {
            jumpButton.clearPressBuffer();

            var mover = get(Mover.class);
            var anim = get(Animator.class);

            // can we fall through a platform instead?
            var isFallthrough = false; // mover.isFallthrough();
            if (duckButton.down() && state == State.normal && onGround && isFallthrough) {
                mover.moveY(-1);
            } else {
                jumpforceAmount = jumpforce_normaljump;
                jumpforceTimer = jumpforce_max_duration;

                // check ground / platform speed
                if (groundedVelocity.y < 0) {
                    jumpforceAmount -= groundedVelocity.y;
                }
                if (conveyorVelocity.y < 0) {
                    jumpforceAmount -= conveyorVelocity.y * 0.75f;
                }

                mover.speed.y = -jumpforceAmount;
                mover.speed.x += groundedVelocity.x;
                mover.speed.x += conveyorVelocity.x * 1.5f;

                entity.position.y = groundedPosition.y;
                anim.scale.set(0.8f, 1.4f);

                // TODO: trigger jump effect
//                EffectFactory.jump(entity.world, entity.position);

                return true;
            }
        }
        */

        return false;
    }

    private boolean tryWallJump() {
        // TODO
        return false;
    }

    private boolean trySlash() {
        if (attackButton.pressed() && slashCooldownTimer <= 0) {
            attackButton.clearPressBuffer();
            // TODO: if we change to a state machine interface with enter/update/exit methods, set cooldown timer in attack.enter()
            slashCooldownTimer = slash_cooldown;
            state = State.attack;
            return true;
        }
        return false;
    }

    private void updateVerticalSpeed(float dt, int moveDir) {
        var mover = get(Mover.class);
        var fastFalling = false;
        var wallSliding = false;

        // gravity
        if (!onGround) {
            var gravityAmount = gravity;

            // slow gravity at peak of jump
            var peakJumpThreshold = 12;
            if (jumpButton.down() && Calc.abs(mover.speed.y) < peakJumpThreshold) {
                gravityAmount = gravity_peak;
            }

            // fast falling
            if (duckButton.down() && !jumpButton.down() && mover.speed.y <= 0) {
                fastFalling = true;
                gravityAmount = gravity_fastfall;
            }
            // wall behavior
            else if (moveDir != 0 && mover.collider.check(Collider.Mask.solid, Point.at(moveDir, 0))) {
                // wall sliding
                if (mover.speed.y < 0) {
                    wallSliding = true;
                    facing = moveDir;
                    gravityAmount = gravity_wallsliding;
                }
            }

            // apply gravity
            mover.speed.y += gravityAmount * dt;
        }

        // apply the jump
        // either we're holding down & jump timer hasn't run out
        // or jump timer is within the minimum and forced on
        // TODO: (scratch.txt line 38)

        // max falling
        {
            var maxfallAmount = maxfall;

            if (fastFalling) {
                maxfallAmount = maxfall_fastfall;
            } else if (wallSliding) {
                maxfallAmount = maxfall_wallsliding;
            }
            // TODO: are there other conditions here?

            // apply maxfall
            if (mover.speed.y < maxfallAmount) {
                mover.speed.y = maxfallAmount;
            }
        }

        // TODO: probably other stuff? need to review the stream
    }

    private void updateHorizontalSpeed(boolean isStopped) {
        // TODO
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
