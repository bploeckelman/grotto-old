package zendo.games.grotto.components;

import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.editor.WorldMap;

/**
 * Provide a convenient way to get at the WorldMap from the ECS
 */
public class WorldMapContainer extends Component {

    private WorldMap worldMap;

    public WorldMapContainer() {}

    public WorldMapContainer(WorldMap worldMap) {
        this.worldMap = worldMap;
    }

    public WorldMap get() {
        return worldMap;
    }

    @Override
    public void reset() {
        super.reset();
        this.worldMap = null;
    }

}
