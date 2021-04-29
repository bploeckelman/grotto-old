package zendo.games.grotto.components;

import zendo.games.grotto.ecs.Component;

public class Timer extends Component {

    public interface OnEnd {
        void run(Timer timer);
    }

    private OnEnd onEnd;
    private float duration;

    public Timer() {}

    public Timer(float duration, OnEnd onEnd) {
        this.duration = duration;
        this.onEnd = onEnd;
    }

    @Override
    public void reset() {
        super.reset();
        duration = 0;
        onEnd = null;
    }

    public void start(float duration) {
        this.duration = duration;
    }

    @Override
    public void update(float dt) {
        if (duration > 0) {
            duration -= dt;
            if (duration <= 0 && onEnd != null) {
                onEnd.run(this);
            }
        }
    }

}
