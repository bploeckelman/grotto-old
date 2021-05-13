package zendo.games.grotto;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import zendo.games.grotto.components.CameraController;
import zendo.games.grotto.components.Enemy;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.editor.Editor;
import zendo.games.grotto.editor.Level;
import zendo.games.grotto.factories.CreatureFactory;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Game extends ApplicationAdapter {

    private Input input;
    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapes;

    private OrthographicCamera worldCamera;
    private OrthographicCamera windowCamera;

    private FrameBuffer frameBuffer;
    private Texture frameBufferTexture;
    private TextureRegion frameBufferRegion;

    private World world;
    private Entity player;
    private Entity slime;
    private Entity goblin;

    private Level level;
    private Mode mode;
    private enum Mode {play, edit}
    private Vector3 worldMouse;
    private Editor editor;
    private InputMultiplexer inputMux;

    @Override
    public void create() {
        Time.init();
        Input.init();

        input = new Input();
        inputMux = new InputMultiplexer();
        inputMux.addProcessor(input);
        Gdx.input.setInputProcessor(inputMux);
        Controllers.addListener(input);

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

//        level = new Level(world, assets, "levels/test.json");
        level = new Level(world, assets, "levels/ldtk-test.ldtk");

        player = CreatureFactory.player(world, Point.at((int) worldCamera.viewportWidth / 2, 100));

        slime = CreatureFactory.slime(world, Point.at((int) worldCamera.viewportWidth / 2 + 32, 100));
        goblin = CreatureFactory.goblin(world, Point.at((int) worldCamera.viewportWidth / 4 + 32, 120));

        var camController = world.addEntity().add(new CameraController(worldCamera), CameraController.class);
        camController.setTarget(player, true);
        camController.level = level;

        mode = Mode.play;
        worldMouse = new Vector3();

        editor = new Editor(this, assets);
    }

    @Override
    public void dispose() {
        world.clear();
        assets.dispose();
    }

    public void update() {
        // update global timer
        Time.delta = Calc.min(1 / 30f, Gdx.graphics.getDeltaTime());

        worldCamera.unproject(worldMouse.set(Input.mouse().x, Input.mouse().y, 0));

        if (Input.pressed(Input.Key.l)) {
            level.clear();
            level.load(world, assets, "levels/ldtk-test.ldtk");
            world.first(CameraController.class).level = level;
        }

        // update based on mode
        switch (mode) {
            case play -> updatePlayMode(Time.delta);
            case edit -> editor.update(Time.delta);
        }
    }

    public void toggleMode() {
        if      (mode == Mode.play) mode = Mode.edit;
        else if (mode == Mode.edit) mode = Mode.play;

        DebugFlags.draw_world_origin = (mode == Mode.edit);

        if (mode == Mode.edit) {
            editor.lastZoom = worldCamera.zoom;

            world.first(CameraController.class).active = false;

            inputMux.addProcessor(0, editor.getStage());
        } else {
            worldCamera.zoom = editor.lastZoom;
            worldCamera.update();

            var camController = world.first(CameraController.class);
            camController.active = true;
            camController.setTarget(player, true);

            inputMux.removeProcessor(editor.getStage());
        }

        if (player != null) {
            player.active = !player.active;
        }
        var enemy = world.first(Enemy.class);
        while (enemy != null) {
            enemy.entity().active = !enemy.entity().active;
            enemy = (Enemy) enemy.next;
        }
    }

    private void updatePlayMode(float dt) {
        // process input
        {
            Input.frame();

            if (Input.pressed(Input.Key.tab)) {
                toggleMode();
            }

            if (Input.pressed(Input.Key.escape)) Gdx.app.exit();
            if (Input.pressed(Input.Key.f1)) DebugFlags.draw_entities = !DebugFlags.draw_entities;
            if (Input.pressed(Input.Key.f2)) DebugFlags.draw_anim_bounds = !DebugFlags.draw_anim_bounds;
            if (Input.pressed(Input.Key.f3)) DebugFlags.draw_world_origin = !DebugFlags.draw_world_origin;

            if (Input.pressed(Input.Key.f6)) DebugFlags.frame_stepping_enabled = !DebugFlags.frame_stepping_enabled;
            if (DebugFlags.frame_stepping_enabled && !Input.pressed(Input.Key.f7)) {
                return;
            }

            // test switching camera controller target entity
            if (Input.pressed(0, Input.Button.y)) {
                var camController = world.first(CameraController.class);
                if (camController.mode() == CameraController.TargetMode.entity) {
                    if (camController.entity == player) {
                        camController.entity = slime;
                    } else if (camController.entity == slime) {
                        camController.entity = goblin;
                    } else if (camController.entity == goblin) {
                        camController.entity = player;
                    }
                }
            }
        }

        // handle a pause
        {
            if (Time.pause_timer > 0) {
                Time.pause_timer -= Time.delta;
                if (Time.pause_timer <= -0.0001f) {
                    Time.delta = -Time.pause_timer;
                } else {
                    // skip updates if we're paused
                    return;
                }
            }
            Time.millis += Time.delta;
            Time.previous_elapsed = Time.elapsed_millis();
        }

        // update systems
        {
            assets.tween.update(Time.delta);
            world.update(Time.delta);

            // respawn if both enemies are dead
            if (slime.world == null && goblin.world == null) {
                slime = CreatureFactory.slime(world, Point.at((int) worldCamera.viewportWidth / 2 + 32, 100));
                goblin = CreatureFactory.goblin(world, Point.at((int) worldCamera.viewportWidth / 4 + 32, 120));
            }
        }
    }

    @Override
    public void render() {
        update();

        renderWorldIntoFramebuffer();
        renderFramebufferIntoWindow();
        renderWindowOverlay();
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
                assets.font.draw(batch, assets.layout, 0, (3f / 4f) * worldCamera.viewportHeight + assets.layout.height);

                if (mode == Mode.edit) {
                    editor.renderWorld(batch);
                }
            }
            batch.end();

            shapes.setProjectionMatrix(worldCamera.combined);
            shapes.begin();
            {
                // world ------------------------
                if (DebugFlags.draw_entities) {
                    world.render(shapes);
                }

                if (mode == Mode.edit) {
                    editor.renderGrid(shapes);
                }

                // coord axis at origin
                if (DebugFlags.draw_world_origin) {
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
        if (mode == Mode.edit) {
            editor.renderStage();
        }

        // render hud
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            if (mode == Mode.edit) {
                editor.render(batch);
            }

            // current mode
            {
                var margin = 10;
                var color = (mode == Mode.play) ? Color.LIME : Color.GOLDENROD;
                var modeStr = mode.toString().toUpperCase();
                assets.layout.setText(assets.font, modeStr, color, windowCamera.viewportWidth, Align.center, false);
                assets.font.draw(batch, assets.layout, 0, windowCamera.viewportHeight - margin);
            }
        }
        batch.end();
    }

    // ------------------------------------------------------------------------

    public OrthographicCamera getWorldCamera() {
        return worldCamera;
    }

    public OrthographicCamera getWindowCamera() {
        return windowCamera;
    }

    public Level getLevel() {
        return level;
    }

    public Vector3 getWorldMouse() {
        return worldMouse;
    }

    public World getWorld() {
        return world;
    }

    // ------------------------------------------------------------------------

    public static class DebugFlags {
        public static boolean draw_world_origin = false;
        public static boolean draw_entities = false;
        public static boolean draw_anim_bounds = false;
        public static boolean frame_stepping_enabled = false;
    }

}