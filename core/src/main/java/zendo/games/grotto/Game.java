package zendo.games.grotto;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import zendo.games.grotto.components.CameraController;
import zendo.games.grotto.components.Enemy;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
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
    private Point lastMousePressed;

    @Override
    public void create() {
        Time.init();
        Input.init();

        input = new Input();
        Gdx.input.setInputProcessor(input);
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

        level = new Level(world, assets, "levels/test.json");

        player = CreatureFactory.player(world, Point.at((int) worldCamera.viewportWidth / 2, 100));

        slime = CreatureFactory.slime(world, Point.at((int) worldCamera.viewportWidth / 2 + 32, 100));
        goblin = CreatureFactory.goblin(world, Point.at((int) worldCamera.viewportWidth / 4 + 32, 120));

        var camController = world.addEntity().add(new CameraController(worldCamera), CameraController.class);
        // todo - lock to level bounds, disable for now
        camController.active = false;
//        camController.setTarget(player, true);

        mode = Mode.play;
        worldMouse = new Vector3();
        lastMousePressed = Point.zero();
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

        // update based on mode
        switch (mode) {
            case play -> updatePlayMode(Time.delta);
            case edit -> updateEditMode(Time.delta);
        }
    }

    private void toggleMode() {
        if      (mode == Mode.play) mode = Mode.edit;
        else if (mode == Mode.edit) mode = Mode.play;

        DebugFlags.draw_world_origin = (mode == Mode.edit);

        if (mode == Mode.edit) {
            edit.lastZoom = worldCamera.zoom;
        } else {
            worldCamera.zoom = edit.lastZoom;
            worldCamera.update();
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

    static class LevelEdit {
        boolean leftMousePressed = false;
        boolean rightMousePressed = false;
        Point lastPress = Point.zero();
        Point mouseDelta = Point.zero();
        Point startPos = Point.zero();
        float lastZoom = 0;
    }
    private LevelEdit edit = new LevelEdit();

    private void updateEditMode(float dt) {
        // process input
        {
            Input.frame();

            if (Input.pressed(Input.Key.tab)) {
                toggleMode();
            }

            if (Input.pressed(Input.Key.escape)) {
                Gdx.app.exit();
            }

            if (Input.mouseWheel().y != 0) {
                var speed = 2f;
                worldCamera.zoom += Input.mouseWheel().y * speed * dt;
                worldCamera.update();
            }

            if (Input.pressed(Input.MouseButton.left)) {
                edit.leftMousePressed = true;
                edit.lastPress.set((int) Input.mouse().x, (int) Input.mouse().y);
                edit.startPos.set((int) worldCamera.position.x, (int) worldCamera.position.y);
            }
            if (Input.released(Input.MouseButton.left)) {
                edit.leftMousePressed = false;
                edit.lastPress.set(0, 0);
            }

            if (Input.pressed(Input.MouseButton.right)) {
                edit.rightMousePressed = true;
                edit.lastPress.set((int) worldMouse.x, (int) worldMouse.y);
                edit.startPos.set(level.entity.position);
            }
            if (Input.released(Input.MouseButton.right)) {
                edit.rightMousePressed = false;
                edit.lastPress.set(0, 0);
            }

            if (edit.leftMousePressed) {
                edit.mouseDelta.set((int) Input.mouse().x - edit.lastPress.x, (int) Input.mouse().y - edit.lastPress.y);
                worldCamera.translate(-edit.mouseDelta.x * dt, edit.mouseDelta.y * dt, 0);
                worldCamera.update();
            }
            else if (edit.rightMousePressed) {
                edit.mouseDelta.set((int) worldMouse.x - edit.lastPress.x, (int) worldMouse.y - edit.lastPress.y);
                level.entity.position.set(edit.startPos.x + edit.mouseDelta.x, edit.startPos.y + edit.mouseDelta.y);
            }
        }

        // update timer
        {
            Time.millis += Time.delta;
            Time.previous_elapsed = Time.elapsed_millis();
        }

        // update systems
        {
            assets.tween.update(Time.delta);
            world.update(Time.delta);
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
            }
            batch.end();

            shapes.setProjectionMatrix(worldCamera.combined);
            shapes.begin();
            {
                // world ------------------------
                if (DebugFlags.draw_entities) {
                    world.render(shapes);
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
        // render hud
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            var panelWidth = 200;

            // draw control panel
            if (mode == Mode.edit) {
                batch.setColor(0.2f, 0.2f, 0.2f, 0.6f);
                batch.draw(assets.pixel, 0, 0, panelWidth, windowCamera.viewportHeight);
                batch.setColor(Color.WHITE);
            }

            var margin = 10;

            // mouse pos
            if (mode == Mode.edit) {
                var color = Color.WHITE;

                var mouseStr = String.format("SCREEN:\n\n(%4.0f,%3.0f)", Input.mouse().x, Input.mouse().y);
                assets.font.getData().setScale(0.9f);
                {
                    assets.layout.setText(assets.font, mouseStr, color, panelWidth, Align.left, false);
                    assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin);
                }
                var screenMouseHeight = assets.layout.height;
                assets.font.getData().setScale(1f);

                var worldMouseStr = String.format("WORLD:\n\n(%3.0f,%3.0f)", worldMouse.x, worldMouse.y);
                assets.layout.setText(assets.font, worldMouseStr, color, panelWidth, Align.left, false);
                assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin - screenMouseHeight - margin);
            }

            // current mode
            {
                var color = (mode == Mode.play) ? Color.LIME : Color.GOLDENROD;
                var modeStr = mode.toString().toUpperCase();
                assets.layout.setText(assets.font, modeStr, color, windowCamera.viewportWidth, Align.center, false);
                assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin);
            }
        }
        batch.end();
    }

    // ------------------------------------------------------------------------

    public static class DebugFlags {
        public static boolean draw_world_origin = false;
        public static boolean draw_entities = false;
        public static boolean draw_anim_bounds = false;
        public static boolean frame_stepping_enabled = false;
    }

}