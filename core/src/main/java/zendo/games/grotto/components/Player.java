package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import zendo.games.grotto.Config;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.input.VirtualStick;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Time;

public class Player extends Component {

    private float speed = 80;
    private int facing = 1;

    private VirtualStick stick;

    public Player() {
        super();
        stick = new VirtualStick()
                .addButtons(0, Input.Button.left, Input.Button.right, Input.Button.up, Input.Button.down)
                .addKeys(Input.Key.left, Input.Key.right, Input.Key.up, Input.Key.down)
                .addKeys(Input.Key.a, Input.Key.d, Input.Key.w, Input.Key.s)
                .pressBuffer(0.15f);
    }

    @Override
    public void reset() {
        super.reset();
        stick = null;
    }

    @Override
    public void update(float dt) {
        // handle input
        var input = 0;
        {
            var sign = 0;

            stick.update();
            if (stick.pressed()) {
                stick.clearPressBuffer();
                var move = stick.value();
                if      (move.x < 0) sign = -1;
                else if (move.x > 0) sign = 1;
            }

            var velocity = (int) (sign * speed * dt);
            entity.position.x += velocity;
            entity.position.x = Calc.clampInt(entity.position.x, 0, Config.framebuffer_width);

            input = sign;
        }

        // update sprite
        var anim = get(Animator.class);
        {
            // lerp scale back to one
            anim.scale.x = Calc.approach(anim.scale.x, facing, Time.delta * 4);
            anim.scale.y = Calc.approach(anim.scale.y, 1, Time.delta * 4);

            // set facing
            anim.scale.x = Calc.abs(anim.scale.x) * facing;
        }

        // state machine
        {
            if (input == 0) {
                anim.play("idle");
            } else {
                anim.play("run");
            }

            if (input != 0) {
                facing = input;
            }
        }

    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();

        // animator image bounds
        var animator = get(Animator.class);
        if (animator != null) {
            var x = entity.position.x - animator.sprite().origin.x;
            var y = entity.position.y - animator.sprite().origin.y;
            var w = animator.frame().image.getRegionWidth();
            var h = animator.frame().image.getRegionHeight();
            shapes.set(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 1f, 0f, 0.75f);
            shapes.rect(x, y, w, h);
            shapes.setColor(Color.WHITE);
        }

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
