package zendo.games.grotto.components;

import aurelienribon.tweenengine.Tween;
import zendo.games.grotto.ecs.Component;

/**
 * NOTE:
 * Can't use a raw tween on an entity or component field because the entities and components are pooled
 * so the tween wouldn't get killed when the entity or component gets destroyed and whatever new entity
 * reuses the old entity's object would continue tweening. So we wrap a Tween in a component that
 * ensures the tween gets killed when the component gets destroyed.
 */
public class TweenComponent extends Component {

    private Tween tween;

    public TweenComponent() {}

    public TweenComponent(Tween tween) {
        this.tween = tween;
    }

    @Override
    public void destroyed() {
        if (this.tween != null) {
            this.tween.kill();
            this.tween = null;
        }
    }

    @Override
    public void reset() {
        super.reset();
        if (this.tween != null) {
            this.tween.kill();
            this.tween = null;
        }
    }

}
