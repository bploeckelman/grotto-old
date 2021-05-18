package zendo.games.grotto;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;
import zendo.games.grotto.sprites.Content;
import zendo.games.grotto.utils.accessors.*;

public class Assets extends Content implements Disposable {

    public TweenManager tween;
    public BitmapFont font;
    public BitmapFont worldFont;
    public GlyphLayout layout;
    public SpriteBatch batch;
    public ShapeRenderer shapes;

    public Texture pixel;
    public TextureAtlas atlas;
    public TextureAtlas tilesetAtlas;
    public TextureRegion[][] tilesetRegions;

    public Assets() {
        tween = new TweenManager();
        {
            Tween.setWaypointsLimit(4);
            Tween.setCombinedAttributesLimit(4);
            Tween.registerAccessor(Color.class, new ColorAccessor());
            Tween.registerAccessor(Vector2.class, new Vector2Accessor());
            Tween.registerAccessor(Vector3.class, new Vector3Accessor());
            Tween.registerAccessor(Rectangle.class, new RectangleAccessor());
            Tween.registerAccessor(OrthographicCamera.class, new CameraAccessor());
        }

        {
            var fontFile = Gdx.files.internal("fonts/dogicapixel.ttf");
            var generator = new FreeTypeFontGenerator(fontFile);
            var parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 20;
            parameter.color = Color.WHITE;
            parameter.borderColor = Color.DARK_GRAY;
            parameter.shadowColor = Color.BLACK;
            parameter.borderWidth = 2;
            parameter.shadowOffsetX = 1;
            parameter.shadowOffsetY = 2;
            font = generator.generateFont(parameter);
            generator.dispose();

            fontFile = Gdx.files.internal("fonts/smallest_pixel-7.ttf");
            generator = new FreeTypeFontGenerator(fontFile);
            parameter.size = 10;
            parameter.color = Color.WHITE;
            parameter.borderColor = Color.DARK_GRAY;
            parameter.shadowColor = Color.BLACK;
            parameter.borderWidth = 1;
            parameter.shadowOffsetX = 0;
            parameter.shadowOffsetY = 0;
            worldFont = generator.generateFont(parameter);
            generator.dispose();
        }

        layout = new GlyphLayout();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        shapes.setAutoShapeType(true);

        pixel = new Texture("images/pixel.png");

        // load raw sprites from an atlas (see gradle lwjgl3:pack_rawsprites)
        atlas = new TextureAtlas("atlas/sprites.atlas");

        // load tileset sprites from an atlas (see gradle lwjgl3:pack_tilesets)
        tilesetAtlas = new TextureAtlas("atlas/tilesets.atlas");
        // temp
        tilesetRegions = atlas.findRegion("tileset").split(16, 16);

        // load aseprite sprites from an atlas and json definitions
        TextureAtlas aseAtlas = new TextureAtlas("atlas/aseprites.atlas");
        FileHandle spritesDir = Gdx.files.internal("sprites");
        for (FileHandle fileHandle : spritesDir.list(".json")) {
            sprites.add(Content.loadSprite(fileHandle.path(), aseAtlas));
        }

        VisUI.load();
    }

    @Override
    public void dispose() {
        VisUI.dispose();
        Content.unload();
        pixel.dispose();
        font.dispose();
        batch.dispose();
        shapes.dispose();
        tilesetAtlas.dispose();
        atlas.dispose();
    }

}
