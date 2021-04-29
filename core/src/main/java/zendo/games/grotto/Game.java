package zendo.games.grotto;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import zendo.games.grotto.sprites.Content;
import zendo.games.grotto.sprites.Sprite;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Game extends ApplicationAdapter {

    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapes;

    private Sprite sprite;
    private Animation<TextureRegion> anim;
    private float stateTime;

    private OrthographicCamera worldCamera;
    private OrthographicCamera windowCamera;

    private FrameBuffer frameBuffer;
    private Texture frameBufferTexture;
    private TextureRegion frameBufferRegion;

    @Override
    public void create() {
        assets = new Assets();
        batch = assets.batch;
        shapes = assets.shapes;

        sprite = Content.findSprite("blob");
        stateTime = 0f;
        var keyframes = new Array<TextureRegion>();
        for (var keyframe : sprite.getAnimation("idle").frames) {
            keyframes.add(keyframe.image);
        }
        anim = new Animation<>(0.2f, keyframes, Animation.PlayMode.LOOP);

        worldCamera = new OrthographicCamera();
        worldCamera.setToOrtho(false, Config.framebuffer_width, Config.framebuffer_height);
        worldCamera.update();

        windowCamera = new OrthographicCamera();
        windowCamera.setToOrtho(false, Config.window_width, Config.window_height);
        windowCamera.update();

        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Config.framebuffer_width, Config.framebuffer_height, false);
        frameBufferTexture = frameBuffer.getColorBufferTexture();
        frameBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        frameBufferRegion = new TextureRegion(frameBufferTexture);
        frameBufferRegion.flip(false, true);
    }

    public void update(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        stateTime += dt;
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        update(dt);

        Gdx.gl.glClearColor(0.0f, 0.5f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderWorldIntoFramebuffer();
        renderFramebufferIntoWindow();

        // render hud
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            assets.layout.setText(assets.font, "Grotto", Color.WHITE, windowCamera.viewportWidth, Align.center, false);
            assets.font.draw(batch, assets.layout, 0, (1f / 3f) * windowCamera.viewportHeight + assets.layout.height);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        assets.dispose();
    }

    // ------------------------------------------------------------------------

    private void renderWorldIntoFramebuffer() {
        frameBuffer.begin();
        {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 0f);
            Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                // world ------------------------
//                var keyframe = sprite.getAnimation("idle").frames.get(0);
                var keyframe = anim.getKeyFrame(stateTime);
                batch.draw(keyframe,
                           worldCamera.viewportWidth  / 2f - keyframe.getRegionWidth()  / 2f,
                           worldCamera.viewportHeight / 2f - keyframe.getRegionHeight() / 2f);

                // in-world ui ------------------
                // ...
            }
            batch.end();

            shapes.setProjectionMatrix(worldCamera.combined);
            shapes.setAutoShapeType(true);
            shapes.begin();
            {
                // coord axis at origin
                if (DebugFlags.draw_origin) {
                    shapes.setColor(Color.BLUE);
                    shapes.rectLine(0, 0, 10, 0, 1);
                    shapes.setColor(Color.GREEN);
                    shapes.rectLine(0, 0, 0, 10, 1);
                    shapes.setColor(Color.RED);
                    shapes.circle(0, 0, 1);
                    shapes.setColor(Color.WHITE);
                }
            }
            shapes.end();
        }
        frameBuffer.end();
    }

    private void renderFramebufferIntoWindow() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            // background ---------------------------------
            batch.setColor(Color.ROYAL);
            batch.draw(assets.pixel, 0, 0, Config.window_width, Config.window_height);
            batch.setColor(Color.WHITE);

            // composite scene ----------------------------
            batch.draw(frameBufferRegion, 0, 0, Config.window_width, Config.window_height);

            // hud overlay items --------------------------
            // ...
        }
        batch.end();
    }

    // ------------------------------------------------------------------------

    public static class DebugFlags {
        public static boolean draw_origin = true;
    }

}