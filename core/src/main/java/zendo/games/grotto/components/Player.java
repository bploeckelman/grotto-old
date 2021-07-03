package zendo.games.grotto.components;

import com.badlogic.gdx.Gdx;
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

    private static final float coyote_time = 0.1f;

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

    private static final float invincible_duration = 0.5f;

    private static final float run_startup_time = 0.30f;

    // timers
    private float airTimer;
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

        changeState(new NormalState(this));

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
        jumpforceAmount = 0;
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
        numCoins = 0;
    }

    public void addCoin() {
        numCoins++;
    }

    public int numCoins() {
        return numCoins;
    }

    @Override
    public void update(float dt) {
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
                    EffectFactory.spriteAnimOneShot(world(), entity.position, "hero", "land");
                }

                groundedPosition.set(entity.position.x, entity.position.y);
            }
            // not touching ground, increase air timer
            else {
                airTimer += dt;
            }
        }

        // hurt check
        {
            if (!(state instanceof HurtState)) {
                var collider = get(Collider.class);
                var hitbox = collider.first(Collider.Mask.enemy);
                if (hitbox != null) {
                    changeState(new HurtState(this));
                }
            }
        }

        // update state machine
        {
            if (state != null) {
                state.update(dt, moveDir);
            }
        }

        // lerp scale back to normal
        {
            var sx = Calc.approach(Calc.abs(anim.scale.x), 1f, 4 * dt);
            var sy = Calc.approach(Calc.abs(anim.scale.y), 1f, 4 * dt);
            anim.scale.set(facing * sx, sy);

            anim.setAlpha(Calc.approach(anim.getAlpha(), 1f, dt));
        }

        // set animation based on state and other stuff
        {
            if (state == null) {
                Gdx.app.log("player", "invalid state");
            }
            else if (state instanceof HurtState) {
                anim.play("hurt");
            }
            else if (state instanceof AttackState) {
                anim.play("attack");
            }
            else if (state instanceof NormalState) {
                if (grounded) {
                    if (Calc.abs(mover.speed.x) > 4) {
                        anim.play("run");
                    } else {
                        anim.play("idle");
                    }
                } else if (wallsliding) {
                    anim.play("slide");
                } else {
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
    }

    private boolean tryJump() {
        if (jumpButton.pressed() && airTimer < coyote_time) {
            jumpButton.clearPressBuffer();

            jumpforceAmount = jumpforce_normaljump;

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
            anim.scale.set(0.8f, 1.6f);

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

    // ------------------------------------------------------------------------
    // State Machine
    // ------------------------------------------------------------------------

    private void changeState(State newState) {
        if (state != null) {
            state.exit();
        }
        state = newState;
        state.enter();
    }

    static abstract class State {
        final Player player;
        State(Player player) {
            this.player = player;
        }
        void enter() {}
        void update(float dt, int input) {}
        void exit() {}
    }

    // ------------------------------------------------------------------------

    static class NormalState extends State {
        NormalState(Player player) {
            super(player);
        }

        @Override
        public void update(float dt, int input) {
            var mover = player.get(Mover.class);
            var anim = player.get(Animator.class);

            player.wallsliding = false;
            player.fastfalling = false;
            player.running = false;

            // vertical speed
            {
                // gravity
                if (!player.grounded) {
                    var gravityAmount = gravity;

                    // slow gravity at peak of jump
                    var peakJumpThreshold = 12;
                    if (player.jumpButton.down() && Calc.abs(mover.speed.y) < peakJumpThreshold) {
                        gravityAmount = gravity_peak;
                    }

                    // fast falling
                    if (player.duckButton.down() && !player.jumpButton.down() && mover.speed.y < 0) {
                        player.fastfalling = true;
                        gravityAmount = gravity_fastfall;
                    }
                    // wall behavior
                    else if (input != 0 && mover.collider.check(Collider.Mask.solid, Point.at(input, 0))) {
                        // wall sliding
                        if (mover.speed.y < 0) {
                            player.wallsliding = true;
                            if (input != 0) {
                                player.facing = -input;
                            }
                            gravityAmount = gravity_wallsliding;
                        }
                    }

                    // apply gravity
                    mover.speed.y += gravityAmount * dt;
                }

                // max falling
                {
                    var maxfallAmount = maxfall;

                    if (player.fastfalling) {
                        maxfallAmount = maxfall_fastfall;
                    } else if (player.wallsliding) {
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
                if (player.tryJump()) {
                    // cancel backwards horizontal movement
                    if (Calc.sign(mover.speed.x) == -input) {
                        mover.speed.x = 0;
                    }

                    // push out the way we're inputting for extra oomph in a turn/jump
                    if (input != 0) {
                        player.facing = input;
                        mover.speed.x += input * 50;
                    }
                }

                // do a wall jump!
                {
                    player.walljumpFacingChangeTimer -= dt;
                    if (player.walljumpFacingChangeTimer < 0) {
                        player.walljumpFacingChangeTimer = 0;
                    }

                    if (player.tryWallJump()) {
                        // set a timer and ignore input for a brief period so facing stays in jump direction
                        player.walljumpFacingChangeTimer = walljump_facing_change_duration;
                    }
                }

                // if we didn't jump this frame, clear the state anyways so we don't jump automatically when we land next
                player.jumpButton.clearPressBuffer();
            }

            // ducking
            {
                boolean wasDucking = player.ducking;
                player.ducking = player.grounded && player.duckButton.down();

                // stopped ducking, squash and stretch
                if (wasDucking && !player.ducking) {
                    anim.scale.set(0.7f, 1.2f);
                }
            }

            // running
            {
                boolean wasRunning = player.running;
                player.running = player.runButton.down();

                // stopped running, trigger a skid effect
                if (wasRunning && !player.running) {
                    anim.scale.set(1.3f, 1f);
                }
            }

            // horizontal speed
            {
                // acceleration
                var accel = (player.grounded) ? acceleration_ground : acceleration_air;
                mover.speed.x += input * accel * dt;

                // max speed
                var max = (player.grounded) ? maxspeed_ground : maxspeed_air;
                if (player.running) {
                    max = maxspeed_running;
                }
                if (Calc.abs(mover.speed.x) > max) {
                    mover.speed.x = Calc.approach(mover.speed.x, Calc.sign(mover.speed.x) * max, 2000 * dt);
                }

                // friction
                var friction = (player.grounded) ? friction_ground : friction_air;
                if (input == 0) {
                    mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
                }

                // facing
                if (input != 0 && !player.wallsliding && player.walljumpFacingChangeTimer <= 0) {
                    player.facing = input;
                }
            }

            if (trySlash()) {
                player.changeState(new AttackState(player));
                return;
            }
        }

        private boolean trySlash() {
            if (player.attackButton.pressed()) {
                player.attackButton.clearPressBuffer();
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------------

    static class AttackState extends State {

        private float attackTimer;
        private float cooldownTimer;

        private Entity attackEntity;
        private Collider attackCollider;
        private Animator attackEffectAnim;

        public AttackState(Player player) {
            super(player);
        }

        @Override
        public void enter() {
            attackTimer = 0;
            cooldownTimer = slash_duration;

            if (attackEntity != null) {
                attackEntity.destroy();
            }

            // entity position gets updated during attack state based of facing
            attackEntity = player.world().addEntity();
            attackEntity.position.set(player.entity.position);

            // the rect for this collider is updated during attack state based on what frame is active
            attackCollider = attackEntity.add(Collider.makeRect(new RectI()), Collider.class);
            attackCollider.mask = Collider.Mask.player_attack;

            // this is the actual weapon slashing animation, different from the player's 'attacking' animation
            attackEffectAnim = attackEntity.add(new Animator("hero", "attack-effect"), Animator.class);
            attackEffectAnim.mode = Animator.LoopMode.none;
            attackEffectAnim.depth = 2;
        }

        @Override
        public void exit() {
            if (attackEntity != null) {
                attackEntity.destroy();
                attackEntity = null;
                attackCollider = null;
                attackEffectAnim = null;
            }
        }

        @Override
        public void update(float dt, int input) {
            if (cooldownTimer < 0) {
                cooldownTimer = 0;
                player.changeState(new NormalState(player));
                return;
            }
            cooldownTimer -= dt;

            // apply friction to movement
            if (input == 0) {
                var mover = player.get(Mover.class);
                var friction = (player.grounded) ? friction_ground : friction_air;
                mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
            }

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
            attackTimer += dt;

            // update animation and collider position/orientation based on facing direction
            var collider = player.get(Collider.class);
            if (attackEntity != null) {
                if (player.facing >= 0) {
                    // update attack effect position
                    attackEntity.position.x = player.entity.position.x + collider.rect().right() + 8;
                    attackEntity.position.y = player.entity.position.y;
                    // make sure animation points in the right direction
                    if (attackEffectAnim.scale.x != 1) {
                        attackEffectAnim.scale.x = 1;
                    }
                } else if (player.facing < 0) {
                    // update attack effect position
                    attackEntity.position.x = player.entity.position.x - collider.rect().left() - 12;
                    attackEntity.position.y = player.entity.position.y;
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
    }

    // ------------------------------------------------------------------------

    static class HurtState extends State {

        private float invincibleTimer;

        HurtState(Player player) {
            super(player);
            invincibleTimer = 0;
        }

        @Override
        public void enter() {
            Time.pause_for(0.1f);
            invincibleTimer = invincible_duration;

            // todo - lose health

            // bounce back
            player.get(Mover.class).speed.set(-player.facing * 100, 80);

        }

        @Override
        public void update(float dt, int input) {
            invincibleTimer -= dt;
            if (invincibleTimer <= 0) {
                player.changeState(new NormalState(player));
            }
        }

    }

}
