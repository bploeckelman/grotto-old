package zendo.games.grotto.components;

import zendo.games.grotto.ecs.Component;

public class Pickupable extends Component {

    public interface OnPickup {
        void pickup(Pickupable pickupable);
    }

    public Collider collider;
    public OnPickup onPickup;
    public int pickupBy;

    public Pickupable() {}

    @Override
    public void reset() {
        super.reset();
        collider = null;
        onPickup = null;
        pickupBy = 0;
    }

    @Override
    public void update(float dt) {
        if (collider != null) {
            if (collider.check(pickupBy)) {
                if (onPickup != null) {
                    onPickup.pickup(this);
                }
                destroy();
            }
        }
    }

}
