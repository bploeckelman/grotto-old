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
import zendo.games.grotto.sprites.Sprite;
import zendo.games.grotto.utils.Point;
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

    public Assets() {
        tween = new TweenManager();
        {
            Tween.setWaypointsLimit(4);
            Tween.setCombinedAttributesLimit(4);
            Tween.registerAccessor(Point.class, new PointAccessor());
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

        // load aseprite sprites from an atlas and json definitions
        TextureAtlas aseAtlas = new TextureAtlas("atlas/aseprites.atlas");
        FileHandle spritesDir = Gdx.files.internal("sprites");
        for (FileHandle fileHandle : spritesDir.list(".json")) {
            sprites.add(Content.loadSprite(fileHandle.path(), aseAtlas));
        }

        // load sprites from the raw spritesheet
        sprites.addAll(
              loadSpriteManual("clostridium",    atlas.findRegion("clostridium")    .split(24, 24), Point.at(12, 12), Point.at(0, 0))
            , loadSpriteManual("geobacter",      atlas.findRegion("geobacter")      .split(24, 24), Point.at(12, 12), Point.at(0, 0))
            , loadSpriteManual("staphylococcus", atlas.findRegion("staphylococcus") .split(24, 24), Point.at(12, 12), Point.at(0, 0))
            , loadSpriteManual("synechococcus",  atlas.findRegion("synechococcus")  .split(24, 24), Point.at(12, 12), Point.at(0, 0))
        );

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

    private static Sprite loadSpriteManual(String name, TextureRegion[][] sheet, Point origin, Point... sheetIndices) {
        Sprite sprite = new Sprite();
        {
            // set the SpriteInfo
            sprite.name = name;
            sprite.origin.set(origin.x, origin.y);

            // build animation frames
            String anim_name = "idle";
            Sprite.Frame[] anim_frames = new Sprite.Frame[sheetIndices.length];
            for (int i = 0; i < sheetIndices.length; i++) {
                Point index = sheetIndices[i];
                int frame_duration = 100; // millis
                TextureRegion frame_region = sheet[index.y][index.x];
                anim_frames[i] = new Sprite.Frame(frame_region, frame_duration / 1000f);
            }

            // create animation from frames
            Sprite.Anim anim = new Sprite.Anim(anim_name, anim_frames);

            // add animation to sprite
            sprite.animations.add(anim);
        }
        return sprite;
    }

}
