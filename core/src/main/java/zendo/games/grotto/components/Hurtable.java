package zendo.games.grotto.components;

import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Time;

public class Hurtable extends Component {

    public interface OnHurt {
        void hurt(Hurtable hurtable);
    }
    public interface HurtCheck {
        boolean check(Hurtable hurtable);
    }

    public Collider collider;
    public HurtCheck hurtCheck;
    public OnHurt onHurt;
    public int hurtBy;
    public float stunTimer;
    public float flickerTimer;

    public Hurtable() {}

    @Override
    public void reset() {
        super.reset();
        collider = null;
        onHurt = null;
        hurtCheck = null;
        hurtBy = 0;
        stunTimer = 0;
        flickerTimer = 0;
    }

    @Override
    public void update(float dt) {
        if (collider != null && stunTimer <= 0) {
            var wasHurt = false;
            if (hurtCheck != null) {
                wasHurt = hurtCheck.check(this);
            } else {
                wasHurt = collider.check(hurtBy);
            }

            if (wasHurt) {
                Time.pause_for(0.1f);
                stunTimer = 0.5f;
                flickerTimer = 0.5f;
                if (onHurt != null) {
                    onHurt.hurt(this);
                }
            }
        }

        stunTimer -= dt;

        // note - not sure about having a flicker
//        if (flickerTimer > 0) {
//            if (Time.on_interval(0.05f)) {
//                entity.visible = !entity.visible;
//            }
//
//            flickerTimer -= dt;
//            if (flickerTimer <= 0) {
//                entity.visible = true;
//            }
//        }
    }

}
