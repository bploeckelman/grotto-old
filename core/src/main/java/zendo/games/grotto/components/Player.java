package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import zendo.games.grotto.Config;
import zendo.games.grotto.Input;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Time;

public class Player extends Component {

    private float speed = 80;
    private int facing = 1;

    public Player() {}

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public void update(float dt) {
        // handle input
        var input = 0;
        {
            var sign = 0;
            if      (Input.down(Input.Key.a) || Input.down(Input.Key.left))  sign = -1;
            else if (Input.down(Input.Key.d) || Input.down(Input.Key.right)) sign = 1;
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
            shapes.setColor(Color.YELLOW);
            shapes.rect(x, y, w, h);
            shapes.setColor(Color.WHITE);
        }

        // entity position
        {
            var x = entity.position.x;
            var y = entity.position.y;
            var radius = 1;
            shapes.set(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(Color.MAGENTA);
            shapes.circle(x, y, radius);
            shapes.setColor(Color.WHITE);
        }

        shapes.set(shapeType);
    }

}
