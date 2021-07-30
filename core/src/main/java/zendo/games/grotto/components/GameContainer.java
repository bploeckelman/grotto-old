package zendo.games.grotto.components;

import zendo.games.grotto.Game;
import zendo.games.grotto.ecs.Component;

public class GameContainer extends Component {

    public Game game;

    public GameContainer() {}

    public GameContainer(Game game) {
        this.game = game;
    }

    @Override
    public void reset() {
        super.reset();
        game = null;
    }

}
