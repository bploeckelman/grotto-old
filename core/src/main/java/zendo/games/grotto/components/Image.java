package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import zendo.games.grotto.ecs.Component;

public class Image extends Component {

    public float xOffset;
    public float yOffset;
    public float width;
    public float height;

    private TextureRegion region;

    public Image() {}

    public Image(Texture texture) {
        region = new TextureRegion(texture);
        xOffset = 0;
        yOffset = 0;
        width = region.getRegionWidth();
        height = region.getRegionHeight();
    }

    @Override
    public void reset() {
        super.reset();
        region = null;
        xOffset = 0;
        yOffset = 0;
        width = 0;
        height = 0;
    }

    @Override
    public void render(SpriteBatch batch) {
        batch.draw(region, entity.position.x + xOffset, entity.position.y + yOffset, width, height);
    }

}
