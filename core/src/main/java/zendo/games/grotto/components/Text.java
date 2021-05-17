package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import zendo.games.grotto.Assets;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Point;

public class Text extends Component {

    public String text;
    public Point offset;

    // TODO: add other params: width, align, color, etc...
    public float scale = 1;

    private BitmapFont font;
    private GlyphLayout layout;

    public Text() {}

    public Text(Assets assets, String text) {
        this(assets, text, Point.zero());
    }

    public Text(Assets assets, String text, Point offset) {
        // TODO - current font is missing some lowercase glyphs apparently?
        this.text = text.toUpperCase();
        this.offset = offset;
        this.font = assets.worldFont;
        this.layout = assets.layout;
    }

    @Override
    public void reset() {
        super.reset();
        text = null;
        offset = null;
        font = null;
        layout = null;
    }

    @Override
    public void render(SpriteBatch batch) {
        var scaleX = font.getScaleX();
        var scaleY = font.getScaleY();
        font.getData().setScale(scale);
        {
            layout.setText(font, text);
            font.draw(batch, layout, entity.position.x + offset.x, entity.position.y + offset.y + layout.height);
        }
        font.getData().setScale(scaleX, scaleY);
    }

}
