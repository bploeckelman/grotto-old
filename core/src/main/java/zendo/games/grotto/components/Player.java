package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.factories.EffectFactory;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.input.VirtualButton;
import zendo.games.grotto.input.VirtualStick;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;
import zendo.games.grotto.utils.Time;

public class Player extends Component {

    private enum State { normal, slash, hurt }

    private static final float gravity = -500;
    private static final float gravity_peak = -100;
    private static final float gravity_fastfall = -900;
    private static final float gravity_wallsliding = -200;

    private static final float maxfall = -140;
    private static final float maxfall_fastfall = -200;
    private static final float maxfall_wallsliding = -15;

    private static final float friction_ground = 450;
    private static final float friction_air = 200;

    private static final float acceleration_ground = 700;
    private static final float acceleration_turnaround = 500;
    private static final float acceleration_air = 250;

    private static final float maxspeed_ground = 80;
    private static final float maxspeed_air = 90;
    private static final float maxspeed_running = 180;
    private static final float maxspeed_approach = 300;

    private static final float coyote_time = 1.1f;

    private static final float jumpforce_min_duration = 0.10f;
    private static final float jumpforce_max_duration = 0.25f;
    private static final float jumpforce_normaljump = 185;
    private static final float jumpforce_walljump = 200;
    private static final float jumpforce_walljump_horizontal = 100;
    private static final float walljump_facing_change_duration = 0.25f;

    private static final float slash_cooldown = 0.25f;
    private static final float slash_velocity = 187;
    private static final float slash_jumpcancel_speed = 260;
    private static final float slash_anticipation = 0.05f;
    private static final float slash_duration = 0.22f;
    private static final float slash_damage_duration = 0.12f;
    private static final float slash_friction = 2000;
    private static final float slash_running_friction = 1000;
    private static final float slash_min_speed = 40;
    private static final float slash_running_min_speed = 80;

    private static final float run_startup_time = 0.30f;

    // OLD CONSTANTS ---------------------------------
    private static final float friction = 800;
    private static final float hurt_friction = 200;
    private static final float hurt_duration = 0.5f;
    private static final float invincible_duration = 1.5f;
    private static final float hover_grav = 0.5f;
    private static final float ground_accel = 500;
    private static final float ground_speed_max = 100;
    private static final Vector2 grounded_normal = new Vector2(100, 0);
    private static final float jump_time = 0.15f;
    private static final float jump_impulse = 155;
    // OLD CONSTANTS ---------------------------------

    // OLD TIMERS ------------------------------------
    private float jumpTimer;
    private float hurtTimer;
    private float attackTimer;
    // OLD TIMERS ------------------------------------

    // timers
    private float airTimer;
    private float jumpforceTimer;
    private float invincibleTimer;
    private float runStartupTimer;
    private float slashCooldownTimer;
    private float walljumpFacingChangeTimer;

    // properties
    int facing = 1; // TODO: make enum?
    int runningDir = 1; // TODO: make enum?
    Vector2 groundedNormal;
    Vector2 groundedPosition;
    Vector2 groundedVelocity;
    Vector2 conveyorVelocity;
    Vector2 safePosition;
    float jumpforceAmount;

    // flags
    private boolean dead;
    private boolean ducking;
    private boolean grounded;
    private boolean wallsliding;
    private boolean fastfalling;
    private boolean turning;
    private boolean running;

    private boolean canJump;

    private State state;

    private VirtualStick stick;
    private VirtualButton jumpButton;
    private VirtualButton attackButton;
    private VirtualButton duckButton;
    private VirtualButton runButton;

    private Entity attackEntity;
    private Collider attackCollider;
    private Animator attackEffectAnim;

    private int numCoins;

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
        runButton = new VirtualButton()
                .addButton(0, Input.Button.rightShoulder)
                .addKey(Input.Key.shift_left)
                .pressBuffer(0.15f);
        groundedNormal = new Vector2();
        groundedPosition = new Vector2();
        groundedVelocity = new Vector2();
        conveyorVelocity = new Vector2();
        safePosition = new Vector2();
        canJump = true;
        state = State.normal;

//        var camera = world().first(CameraComponent.class);
//        if (camera != null) {
//            camera.follow(entity, Point.zero());
//        }
    }

    @Override
    public void reset() {
        super.reset();
        facing = 1;
        runningDir = 1;
        groundedNormal = null;
        groundedPosition = null;
        groundedVelocity = null;
        conveyorVelocity = null;
        airTimer = 0;
        jumpTimer = 0;
        jumpforceTimer = 0;
        jumpforceAmount = 0;
        hurtTimer = 0;
        attackTimer = 0;
        invincibleTimer = 0;
        runStartupTimer = 0;
        slashCooldownTimer = 0;
        walljumpFacingChangeTimer = 0;
        canJump = false;
        grounded = false;
        ducking = false;
        turning = false;
        state = null;
        stick = null;
        jumpButton = null;
        attackButton = null;
        duckButton = null;
        runButton = null;
        attackEntity = null;
        attackCollider = null;
        attackEffectAnim = null;
        numCoins = 0;
    }

    @Override
    public void update(float dt) {
//        oldUpdate(dt);
//        newUpdate(dt);
        newerUpdate(dt);
    }

    public void addCoin() {
        numCoins++;
    }

    public int numCoins() {
        return numCoins;
    }

    public void oldUpdate(float dt) {
        // get components
        var anim = get(Animator.class);
        var mover = get(Mover.class);

        // store previous state
        var wasOnGround = grounded;
        grounded = mover.onGround();

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
            if (!wasOnGround && grounded) {
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
            if (grounded) {
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
                if (moveDir == 0 && grounded) {
                    mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
                }

                // facing
                if (moveDir != 0) {
                    facing = moveDir;
                }
            }

            // vertical movement (jump trigger)
            {
                if (grounded && !canJump) {
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
                state = State.slash;
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
        else if (state == State.slash) {
            attackTimer += dt;

            // play the attack animation if we're not moving
            anim.play((moveDir == 0) ? "attack" : "run");

            // trigger the slash animation
            attackEffectAnim.play("attack-effect");

            // apply friction
            if (moveDir == 0 && grounded) {
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
        if (!grounded) {
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

        // update virtual input
        jumpButton.update();
        attackButton.update();
        duckButton.update();
        runButton.update();
        stick.update();

        // determine movement direction based on stick input
        var moveDir = 0;
        {
            var sign = 0;
            if (stick.pressed()) {
                stick.clearPressBuffer();
                var move = stick.value();
                if      (move.x < 0) sign = -1;
                else if (move.x > 0) sign = 1;
            }
            moveDir = sign;
        }

        // ----------------------------------------
        // from noels game
//        var inCutscene = world.first(Cutscene.class).isValid();

        // cancel speed if we're in a cutscene
//        if (inCutscene) {
//            mover.stop();
//        }

        // update light
        /*
        {
            var inDarkRoom = world.game.room().dark;
            var light = get(Light.class);
            var lantern = SaveData.instance().hasLantern;
            var targetRadius = lantern ? 100 : 32;
            var targetStrength = lantern ? 1 : 0.75f;

            light.radius = Calc.approach(light.radius, targetRadius, 100 * dt);
            light.strength = Calc.aproach(light.strength, targetStrength, dt);
            light.sourceAlpha = Calc.approach(light.sourceAlpha, (inDarkRoom && lantern) ? 1f : 0f, dt);

            // set light position
            if (lantern) {
                // float around in dark rooms
                if (inDarkRoom) {
                    lightTarget.set(
                            Calc.randFloat(6, 16) * (Calc.rand_int(0, 2) == 0 ? 1 : -1),
                            Calc.randFloat(-32, -10)
                    );

                    // try to move in front when the player is moving
                    lightOffset = Calc.approach(lightOffset, mover.speed.normal() * 32, 80 * dt);

                    // floaty offset
                    var offset = Point.at(
                            entity.position.x + 6 * Calc.sin(4 * Time.elapsed_millis()),
                            entity.position.y + 4 * Calc.sin(0.25f + 8 * Time.elapsed_millis())
                    );

                    // update position
                    lightPosition.add(
                            entity.position.x + lightTarget.x + offset.x - lightPosition.x * 1f - Calc.pow(0.00001f, dt),
                            entity.position.y + lightTarget.y + offset.y - lightPosition.y * 1f - Calc.pow(0.00001f, dt)
                    );
                }
                // return to player in light rooms
                else {
                    lightPosition.add(
                            (entity.position.x + 0 - lightPosition.x) * (1f * Calc.pow(0.00001f, dt)),
                            (entity.position.y + 0 - lightPosition.y) * (1f * Calc.pow(0.00001f, dt))
                    );
                }

                light.offset = light.position - entity.position;
            } else {
                light.offset.set(0, -6);
                lightPosition.set(entity.position.x + light.offset.x, entity.position.y + light.offset.y);
            }
        }
        */

        // toggle hurtbox
        /*
        if (!ducking) {
            hurtbox.makeBox(RectI.at(-4, -12, 8, 12));
        } else {
            hurtbox.makeBox(RectI.at(-5, -6, 10, 6));
        }
        */

        // update ground state
        {
            var wasGrounded = grounded;
            groundedVelocity.set(0, 0);
            // TODO: pass in groundedNormal and groundedVelocity, they should get set on mover.onGround
//            grounded = mover.onGround(-1, max_slope, groundedNormal, groundedVelocity);
            grounded = mover.onGround(-1);
            // TODO: ??????
            groundedVelocity.set(mover.speed.x, mover.speed.y);

            // snap to slopes
            /*
            if (!grounded && !running && wasGrounded && jumpforceTimer <= 0 && mover.collider.overlaps(Collider.Mask.solid | Collider.Mask.jumpthru, Point.at(0, 4))) {
                int steps = 0;
                while (!mover.collider.overlaps(Masks.solid | Masks.jumpthru, Point.at(0, -1)) && steps++ < 16) {
                    // TODO: entity.move(Vec2(0, 1))
                    mover.moveY(-1);
                }

                grounded = mover.onGround(-1, max_slope, groundedNormal);

                if (mover.speed.y > 0) {
                    mover.speed.y = 0;
                }
            }
            */

            // reset air timer
            if (grounded) {
                // just landed, squash and stretch
                if (!wasGrounded) {
                    anim.scale.set(1.4f, 0.6f);
                    // TODO: spawn 'landed' effect
                }

                groundedPosition.set(entity.position.x, entity.position.y);
                airTimer = 0;
            }
            // not touching ground, increase air timer
            else {
                airTimer += dt;
            }

            // normal is always unit y since we don't have slopes
            groundedNormal.set(0, 1);
            // TODO: this was used to normalize the groundedNormal that was set by the enhanced mover.onGround() call above
//            groundedNormal.set(groundedNormal.nor());
        }

        // store last ground position
        if (grounded) {
            // TODO: check around the collider to see if its safe (for respawn purposes)
//         && mover.collider.overlaps(Masks.safe_ground, Vec2(0, 4))
//         && mover.collider.overlaps(Masks.safe_ground, Vec2(-16, 4))
//         && mover.collider.overlaps(Masks.safe_ground, Vec2(+16, 4))) {
            safePosition.set(entity.position.x, entity.position.y);
        }

        var inCutscene = false;
        if (!inCutscene) {
            // slash cooldown
            if (slashCooldownTimer > 0) {
                slashCooldownTimer -= dt;
            }

            // flash while invincible & countdown timer
            if (invincibleTimer > 0) {
//                hittable.active = false;

                if (Time.on_interval(0.05f)) {
                    anim.visible = !anim.visible;
                }

                invincibleTimer -= dt;
                if (invincibleTimer <= 0) {
//                    hittable.active = true;
                    anim.visible = true;
                }
            }

            // manually update the state machine
            // TODO add state machine
            updateNormalState(dt, moveDir);
        }

        // lerp scale back to normal
        {
            var sx = Calc.approach(Calc.abs(anim.scale.x), 1f, 4 * dt);
            var sy = Calc.approach(anim.scale.y, Calc.sign(anim.scale.y), 4 * dt);
            anim.scale.set(facing * sx, sy);
            anim.setAlpha(Calc.approach(anim.getAlpha(), 1f, dt));
        }

        // sprite animation speed
        /*
        {
            anim.speed = 1f;
            if (stateMachine.is(State.normal) && running) {
                anim.speed = 1.5f;
            }
        }
        */

        // sprite animation
        {
            // TODO: [~:310] switch (state) case: anim.play("whatever")
//            if (!stateMachine.is(State.dead) && !stateMachine.is(State.slash) && !stateMachine.is(State.out_of_bounds)) {
//                if (stateMachine.is(State.hurt) || stateMachine.is(State.knockback)) {
            if (state == State.normal) {// state != State.dead && state != State.slash && state != State.out_of_bounds) {
                if (state == State.hurt) {// || state == State.knockback) {
                    anim.play("hurt");
                }
                else if (grounded || inCutscene) {
                    if (ducking) {
                        anim.play("duck");
                    }
                    else if (Calc.abs(mover.speed.x) > 4) {
                        anim.play("walk");
                    }
                    else {
                        anim.play("idle");
                    }
                }
                else if (wallsliding) {
                    anim.play("slide");
                }
                else {
                    if (mover.speed.y > 10) {
                        anim.play("jump");
                    }
                    else if (airTimer > 0.1f) {
                        anim.play("air");
                    }
                    else if (mover.speed.y > maxfall_fastfall - 5) {
                        anim.play("fall");
                    }
                    else {
                        anim.play("fastfall");
                    }
                }
            }
        }

        // running trail
        /*
        if (running && (state == State.normal || state == State.attack)) { // && Time.on_interval(0.1f)) {
            // TODO: manually create an entity that is a single animation frame from the player's current animation
            //       and that has an attached timer that fades it a bit on update and then destroys the entity after X time
        }
        */

        // reset conveyor speed
        /*
        conveyorVelocity.set(0, 0);
        */
    }

    private void newerUpdate(float dt) {
        // get components
        var anim = get(Animator.class);
        var mover = get(Mover.class);

        // update virtual input
        {
            jumpButton.update();
            attackButton.update();
            duckButton.update();
            runButton.update();
            stick.update();
        }

        // determine movement direction based on stick input
        var moveDir = 0;
        {
            var sign = 0;
            if (stick.pressed()) {
                stick.clearPressBuffer();
                var move = stick.value();
                if      (move.x < 0) sign = -1;
                else if (move.x > 0) sign = 1;
            }
            moveDir = sign;
        }

        // update grounded state
        {
            var wasGrounded = grounded;
            grounded = mover.onGround();

            // reset air timer
            if (grounded) {
                airTimer = 0;

                // just landed, squash and stretch
                if (!wasGrounded) {
                    anim.scale.set(1.4f, 0.6f);
                    // TODO: spawn 'landed' effect
                    EffectFactory.spriteAnimOneShot(world(), entity.position, "vase", "break");
                }

                groundedPosition.set(entity.position.x, entity.position.y);
            }
            // not touching ground, increase air timer
            else {
                airTimer += dt;
            }
        }

        // TODO: update state machine
        {
            if (slashCooldownTimer < 0) {
                slashCooldownTimer = 0;

                // end the attack
                if (attackEntity != null) {
                    attackEntity.destroy();
                    attackEntity = null;
                    attackCollider = null;
                    attackEffectAnim = null;
                }

                state = State.normal;
            }
            slashCooldownTimer -= dt;

            updateNormalStateNew(dt, moveDir);
        }

        // lerp scale back to normal
        {
            var sx = Calc.approach(Calc.abs(anim.scale.x), 1f, 4 * dt);
            var sy = Calc.approach(anim.scale.y, Calc.sign(anim.scale.y), 4 * dt);
            anim.scale.set(facing * sx, sy);

            anim.setAlpha(Calc.approach(anim.getAlpha(), 1f, dt));
        }

        // set animation based on state and other stuff
        {
            if (state == State.hurt) {
                anim.play("hurt");
            }
            else if (state == State.slash) {
                anim.play("attack");

                // trigger the slash animation
                attackEffectAnim.play("attack-effect");

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
            }
            else if (grounded) {
                if (Calc.abs(mover.speed.x) > 4) {
                    anim.play("run");
                }
                else {
                    anim.play("idle");
                }
            }
            else if (wallsliding) {
                anim.play("slide");
            }
            else {
                if (mover.speed.y > 10) {
                    anim.play("jump");
                }
//                    else if (airTimer > 0.1f) {
//                        anim.play("air");
//                    }
                else {// if (mover.speed.y > maxfall_fastfall - 5) {
                    anim.play("fall");
                }
//                    else {
//                        anim.play("fastfall");
//                    }
            }
        }
    }

    private void updateNormalStateNew(float dt, int moveDir) {
        var input = moveDir;
        var mover = get(Mover.class);
        var anim = get(Animator.class);

        wallsliding = false;
        fastfalling = false;
        running = false;

        // vertical speed
        {
            // gravity
            if (!grounded) {
                var gravityAmount = gravity;

                // slow gravity at peak of jump
                var peakJumpThreshold = 12;
                if (jumpButton.down() && Calc.abs(mover.speed.y) < peakJumpThreshold) {
                    gravityAmount = gravity_peak;
                }

                // fast falling
                if (duckButton.down() && !jumpButton.down() && mover.speed.y < 0) {
                    fastfalling = true;
                    gravityAmount = gravity_fastfall;
                }
                // wall behavior
                else if (input != 0 && mover.collider.check(Collider.Mask.solid, Point.at(input, 0))) {
                    // wall sliding
                    if (mover.speed.y < 0) {
                        wallsliding = true;
                        facing = -input;
                        gravityAmount = gravity_wallsliding;
                    }
                }

                // apply gravity
                mover.speed.y += gravityAmount * dt;
            }

            // max falling
            {
                var maxfallAmount = maxfall;

                if (fastfalling) {
                    maxfallAmount = maxfall_fastfall;
                } else if (wallsliding) {
                    maxfallAmount = maxfall_wallsliding;
                }

                // apply maxfall
                if (mover.speed.y < maxfallAmount) {
                    mover.speed.y = maxfallAmount;
                }
            }
        }


        // jumping
        {
            // invoke a ground jump
            if (tryJump()) {
                // cancel backwards horizontal movement
//                if (Calc.sign(mover.speed.x) == -input) {
//                    mover.speed.x = 0;
//                }

                // push out the way we're inputting
//                facing = input;
//                mover.speed.x += input * 50;
            }

            // do a wall jump!
            {
                walljumpFacingChangeTimer -= dt;
                if (walljumpFacingChangeTimer < 0) {
                    walljumpFacingChangeTimer = 0;
                }

                if (tryWallJump()) {
                    // set a timer and ignore input for a brief period so facing stays in jump direction
                    walljumpFacingChangeTimer = walljump_facing_change_duration;
                }
            }

            // if we didn't jump this frame, clear the state anyways so we don't jump automatically when we land next
            jumpButton.clearPressBuffer();
        }

        // ducking
        {
            boolean wasDucking = ducking;
            ducking = grounded && duckButton.down();

            // stopped ducking, squash and stretch
            if (wasDucking && !ducking) {
                anim.scale.set(0.7f, 1.2f);
            }
        }

        // running
        {
            boolean wasRunning = running;
            running = runButton.down();

            // stopped running, trigger a skid effect
            if (wasRunning && !running) {
                anim.scale.set(1.3f, 1f);
            }
        }

        // horizontal speed
        {
            // acceleration
            var accel = (grounded) ? acceleration_ground : acceleration_air;
            mover.speed.x += input * accel * dt;

            // max speed
            var max = (grounded) ? maxspeed_ground : maxspeed_air;
            if (running) {
                max = maxspeed_running;
            }
            if (Calc.abs(mover.speed.x) > max) {
                mover.speed.x = Calc.approach(mover.speed.x, Calc.sign(mover.speed.x) * max, 2000 * dt);
            }

            // friction
            if (input == 0 && grounded) {
                mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
            }

            // facing
            if (input != 0 && !wallsliding && walljumpFacingChangeTimer <= 0) {
                facing = input;
            }
        }

        if (trySlash()) {
            state = State.slash;
            slashCooldownTimer = slash_duration;

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

    private void updateNormalState(float dt, int moveDir) {
        var input = moveDir;
        var mover = get(Mover.class);
        var anim = get(Animator.class);

        // start running
        if (runButton.down() && grounded && !running) {
            if (runStartupTimer <= 0) {
                // TODO: spawn dash effect
//                EffectFactory.dash(world(), entity.position, facing);
            }

            runStartupTimer += dt;
            if (runStartupTimer >= run_startup_time) {
                Time.pause_for(0.1f);

                running = true;
                runningDir = facing;
                runStartupTimer = 0;

                // TODO: camera shake
            }
        } else {
            runStartupTimer = 0;
        }

        // keep running
        if (running && input == 0) {
            input = runningDir;
        }

        // dash effects
        if (running && grounded && Time.on_interval(0.1f)) {
            // TODO: spawn dash effect
//            EffectFactory.dash(world(), entity.position, facing);
        }

        // stop running
        if (!runButton.down() && facing != runningDir) {
            running = false;
        }

        // handles gravity & jumping
        updateVerticalSpeed(dt, moveDir);

        // jumping
        {
            // invoke a ground jump
            if (tryJump()) {
                // cancel backwards horizontal movement
                if (Calc.sign(mover.speed.x) == -input) {
                    mover.speed.x = 0;
                }

                // push out the way we're inputting
                facing = input;
                mover.speed.x += input * 50;
            }

            // do a wall jump!
            tryWallJump();
        }

        // ducking
        {
            boolean wasDucking = ducking;
            ducking = grounded && duckButton.down();

            // stopped ducking, squash and stretch
            if (wasDucking && !ducking) {
                anim.scale.set(0.7f, 1.2f);
            }
        }

        // firgure out maxspeed
        var maxspeed = (grounded ? maxspeed_ground : maxspeed_air);
        if (running) {
            maxspeed = maxspeed_running;
        }

        // acceleration
        turning = false;
        if (input != 0 && !ducking && runStartupTimer <= 0) {
            var was = mover.speed.x;

            if (grounded) {
                facing = input;
            }

            // get accel value
            // TODO: pool me
            var accelerationAmount = new Vector2();
            if (grounded) {
                if (input == -Calc.sign(mover.speed.x)) {
                    turning = true;
                    accelerationAmount.set(input * acceleration_turnaround,  input * acceleration_turnaround);
//                    accelerationAmount.set(
//                            -grounded_normal.y * input * acceleration_turnaround,
//                             grounded_normal.x * input * acceleration_turnaround
//                    );
                } else {
                    if (mover.speed.len() < maxspeed) {
                        accelerationAmount.set(input * acceleration_ground,  input * acceleration_ground);
//                        accelerationAmount.set(
//                                -grounded_normal.y * input * acceleration_ground,
//                                 grounded_normal.x * input * acceleration_ground
//                        );
                    }
                }
            } else if (Calc.abs(mover.speed.x) < maxspeed || input != Calc.sign(mover.speed.x)) {
                accelerationAmount.set(
                        1f * input * acceleration_air,
                        0f * input * acceleration_air
                );
            }

            // apply
            mover.speed.x += accelerationAmount.x * dt;
            mover.speed.y += accelerationAmount.y * dt;

            // freeze-frame on turnaround
            if (turning && Calc.sign(was) != Calc.sign(mover.speed.x)) {
                Time.pause_for(0.05f);
            }
        }

        var isStopped = (input == 0 || ducking || runStartupTimer > 0);
        updateHorizontalSpeed(dt, isStopped, maxspeed);

        if (trySlash()) {
            // TODO
//            keepRunning = true;
        }

        // https://github.com/ExOK/Celeste2/blob/main/player.lua
        // https://github.com/NoelFB/Celeste/blob/master/Source/PICO-8/Classic.cs#L203
    }

    private boolean tryJump() {
        if (jumpButton.pressed() && airTimer < coyote_time) {
            jumpButton.clearPressBuffer();

            jumpforceAmount = jumpforce_normaljump;
            jumpforceTimer = jumpforce_max_duration;

            // check ground / platform speed
//                if (groundedVelocity.y < 0) {
//                    jumpforceAmount -= groundedVelocity.y;
//                }
//                if (conveyorVelocity.y < 0) {
//                    jumpforceAmount -= conveyorVelocity.y * 0.75f;
//                }

            var mover = get(Mover.class);
            mover.speed.y = jumpforceAmount;
//            mover.speed.x += groundedVelocity.x;
//            mover.speed.x += conveyorVelocity.x * 1.5f;

//            entity.position.y = groundedPosition.y;

            // squash and stretch
            var anim = get(Animator.class);
            anim.scale.set(0.8f, 1.4f);

            // TODO: trigger jump effect
            EffectFactory.spriteAnimOneShot(entity.world, entity.position, "coin", "pickup");

            return true;
        }

        return false;
    }

    private boolean tryWallJump() {
        if (jumpButton.pressed() && wallsliding) {
            jumpButton.clearPressBuffer();

            jumpforceAmount = jumpforce_walljump;
            jumpforceTimer = jumpforce_max_duration;

            var mover = get(Mover.class);
            mover.speed.y = jumpforceAmount;
            mover.speed.x = facing * jumpforce_walljump_horizontal;

            // squash and stretch
            var anim = get(Animator.class);
            anim.scale.set(1.4f, 0.8f);

            // TODO: trigger jump effect
            EffectFactory.spriteAnimOneShot(entity.world, entity.position, "coin", "pickup");

            return true;
        }

        return false;
    }

    private boolean trySlash() {
        if (attackButton.pressed() && slashCooldownTimer <= 0) {
            attackButton.clearPressBuffer();
            // TODO: if we change to a state machine interface with enter/update/exit methods, set cooldown timer in attack.enter()
//            slashCooldownTimer = slash_cooldown;
//            state = State.slash;
            return true;
        }
        return false;
    }

    private void updateVerticalSpeed(float dt, int moveDir) {
        var mover = get(Mover.class);
        fastfalling = false;
        wallsliding = false;

        // gravity
        if (!grounded) {
            var gravityAmount = gravity;

            // slow gravity at peak of jump
            var peakJumpThreshold = 12;
            if (jumpButton.down() && Calc.abs(mover.speed.y) < peakJumpThreshold) {
                gravityAmount = gravity_peak;
            }

            // fast falling
            if (duckButton.down() && !jumpButton.down() && mover.speed.y <= 0) {
                fastfalling = true;
                gravityAmount = gravity_fastfall;
            }
            // wall behavior
            else if (moveDir != 0 && mover.collider.check(Collider.Mask.solid, Point.at(moveDir, 0))) {
                // wall sliding
                if (mover.speed.y < 0) {
                    wallsliding = true;
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
        if ((jumpButton.down() && jumpforceTimer > 0)
         || (jumpforceTimer > jumpforce_max_duration - jumpforce_min_duration)) {
            jumpforceTimer -= dt;
            if (mover.speed.y < -jumpforceAmount) {
                mover.speed.y = -jumpforceAmount;
            }
        } else {
            jumpforceTimer = 0;
        }

        // max falling
        {
            var maxfallAmount = maxfall;

            if (fastfalling) {
                maxfallAmount = maxfall_fastfall;
            } else if (wallsliding) {
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

    private void updateHorizontalSpeed(float dt, boolean applyFriction, float altMaxspeed) {
        var mover = get(Mover.class);

        // friction
        if (applyFriction) {
            if (grounded) {// && Vector2.dot(0, -1, groundedNormal.x, groundedNormal.y) >= max_non_sliding_slope) {
                var normal = mover.speed.cpy().nor();
                var magnitude = mover.speed.len();
                mover.speed.set(
                        normal.x * Calc.approach(magnitude, 0, friction_ground * dt),
                        normal.y * Calc.approach(magnitude, 0, friction_ground * dt)
                );
            } else {
                mover.speed.x = Calc.approach(mover.speed.x, 0, friction_air * dt);
            }
        }

        // max horizontal speed
        {
            var maxspeed = (grounded ? maxspeed_ground : maxspeed_air);
            if (altMaxspeed >= 0) {
                maxspeed = altMaxspeed;
            }

            if (grounded) {
                // turn 90 degrees clockwise
                var perp = groundedNormal.cpy().rotate90(-1);
                if (Calc.sign(mover.speed.x) != Calc.sign(perp.x)) {
                    perp.scl(-1);
                }

                if (Calc.abs(mover.speed.x) > Calc.abs(perp.x * maxspeed)
                 || Calc.abs(mover.speed.y) > Calc.abs(perp.y * maxspeed)) {
                    mover.speed.set(
                            Calc.approach(mover.speed.x, perp.x * maxspeed, maxspeed_approach * dt),
                            Calc.approach(mover.speed.y, perp.y * maxspeed, maxspeed_approach * dt)
                    );
                }
            }
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
