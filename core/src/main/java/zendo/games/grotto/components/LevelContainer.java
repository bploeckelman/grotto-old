package zendo.games.grotto.components;

import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.editor.Level;

/**
 * Provide a convenient way to get at the Level from the ECS
 */
public class LevelContainer extends Component {

    private Level level;

    public LevelContainer() {}

    public LevelContainer(Level level) {
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    @Override
    public void reset() {
        super.reset();
        this.level = null;
    }

}
