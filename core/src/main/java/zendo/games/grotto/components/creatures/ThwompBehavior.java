package zendo.games.grotto.components.creatures;

import com.badlogic.gdx.Gdx;
import zendo.games.grotto.Config;
import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.factories.EffectFactory;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;

import static zendo.games.grotto.components.creatures.ThwompBehavior.State.*;

public class ThwompBehavior extends Component {

    private final float rest_time = 1f;
    private final float attack_velocity = -150f;
    private final float retreat_velocity = 50f;

    enum State { idle, warn, attack, retreat }

    private State state;
    private float stateTime;
    private Point startingPoint;

    public ThwompBehavior() {}

    public ThwompBehavior(Point startingPoint) {
        this.state = idle;
        this.stateTime = 0;
        this.startingPoint = startingPoint;
    }

    @Override
    public void reset() {
        super.reset();
        this.state = null;
        this.stateTime = 0;
        this.startingPoint = null;
    }

    @Override
    public void update(float dt) {
        var player = world().first(Player.class);
        if (player == null) return;

        var anim = get(Animator.class);
        var collider = get(Collider.class);
        var mover = get(Mover.class);

        var horizDist = player.entity().position.x - entity.position.x;
        var dist = Calc.abs(horizDist);
        var playerIsClose = (dist < collider.rect().w / 2);

        stateTime += dt;
        switch (state) {
            case idle -> {
                anim.play("idle");
                if (playerIsClose && stateTime >= rest_time) {
                    changeState(warn);
                }
            }
            case warn -> {
                anim.play("warn");
                if (stateTime >= anim.duration()) {
                    changeState(attack);
                }
            }
            case attack -> {
                anim.play("attack");
                mover.speed.y = attack_velocity;
                if (mover.onGround()) {
                    // poof
                    EffectFactory.spriteAnimOneShot(world(), entity.position.x - collider.rect().w / 2 - 2, entity.position.y, "hero", "land");
                    EffectFactory.spriteAnimOneShot(world(), entity.position.x + collider.rect().w / 2 - 3, entity.position.y, "hero", "land");
                    changeState(retreat);
                }
            }
            case retreat -> {
                anim.play("warn");
                mover.speed.y = retreat_velocity;
                if (entity.position.y >= startingPoint.y) {
                    entity.position.y = startingPoint.y;
                    mover.speed.y = 0;
                    changeState(idle);
                }
            }
        }
    }

    private void changeState(State state) {
        if (Config.debug_states) {
            Gdx.app.log("state", "thwomp changed state from: " + this.state.name() + " to: " + state.name());
        }
        this.state = state;
        this.stateTime = 0;
    }

}
