package zendo.games.grotto;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import zendo.games.grotto.components.Animator;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.Calc;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Game extends ApplicationAdapter {

    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapes;

    private OrthographicCamera worldCamera;
    private OrthographicCamera windowCamera;

    private FrameBuffer frameBuffer;
    private Texture frameBufferTexture;
    private TextureRegion frameBufferRegion;

    private World world;

    @Override
    public void create() {
        assets = new Assets();
        batch = assets.batch;
        shapes = assets.shapes;

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

        world = new World();

        spawnTestEntity();
    }

    private void spawnTestEntity() {
        var entity = world.addEntity();

        var anim = entity.add(new Animator("blob"), Animator.class);
        anim.play("idle");

        entity.position.set(
                (int) (anim.sprite().origin.x + MathUtils.random((1f / 4f) * worldCamera.viewportWidth,  (3f / 4f) * worldCamera.viewportWidth)),
                (int) (anim.sprite().origin.y + MathUtils.random((1f / 4f) * worldCamera.viewportHeight, (3f / 4f) * worldCamera.viewportHeight))
        );
    }

    public void update(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            var entity = world.firstEntity();
            if (entity != null) {
                entity.destroy();
            } else {
                spawnTestEntity();
            }
        }

        world.update(dt);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        update(dt);

        Gdx.gl.glClearColor(0.0f, 0.5f, 0.8f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderWorldIntoFramebuffer();
        renderFramebufferIntoWindow();
        renderWindowOverlay();
    }

    @Override
    public void dispose() {
        world.clear();
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
                world.render(batch);

                // in-world ui ------------------
                assets.layout.setText(assets.font, "Grotto", Color.WHITE, worldCamera.viewportWidth, Align.center, false);
                assets.font.draw(batch, assets.layout, 0, (1f / 3f) * worldCamera.viewportHeight + assets.layout.height);
            }
            batch.end();

            shapes.setProjectionMatrix(worldCamera.combined);
            shapes.setAutoShapeType(true);
            shapes.begin();
            {
                // world ------------------------
                world.render(shapes);

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

    private void renderWindowOverlay() {
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            // render hud
            assets.layout.setText(assets.font, "Grotto", Color.WHITE, windowCamera.viewportWidth, Align.center, false);
            assets.font.draw(batch, assets.layout, 0, (1f / 4f) * windowCamera.viewportHeight + assets.layout.height);
        }
        batch.end();
    }

    // ------------------------------------------------------------------------

    public static class DebugFlags {
        public static boolean draw_origin = true;
    }

}