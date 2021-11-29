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
import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.map.WorldMap;
import zendo.games.grotto.sprites.Sprite;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

import java.util.List;

import static zendo.games.grotto.input.Input.Key.*;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Game extends ApplicationAdapter {

    private static final String world_path = "levels/world-0.world";

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
    private List<Enemy> enemies;

    private WorldMap worldMap;
    private Vector3 worldMouse;
    private InputMultiplexer inputMux;

    private boolean showingRestartPrompt;

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
        world.addEntity().add(new GameContainer(this), GameContainer.class);

        worldMap = new WorldMap(world, assets, world_path);
        world.addEntity().add(new WorldMapContainer(worldMap), WorldMapContainer.class);

        player = worldMap.spawnPlayer(world);
        enemies = worldMap.spawnEnemies(world);
        worldMap.spawnItems(world);
        worldMap.spawnBarriers(world);
        worldMap.spawnJumpthrus(world);
        worldMap.spawnSolids(world);

        var camera = world.addEntity().add(new CameraController(worldCamera, assets.tween), CameraController.class);
        camera.worldMap = worldMap;
        camera.follow(player, Point.zero(), true);

        worldMouse = new Vector3();
    }

    @Override
    public void dispose() {
        world.clear();
        worldMap.dispose();
        assets.dispose();
    }

    public void update() {
        Time.update();

        worldCamera.unproject(worldMouse.set(Input.mouse().x, Input.mouse().y, 0));

        // trigger a reload in current room
        if (Input.pressed(r) || world.first(Player.class).entity() == null) {
            reload();
        }

        updatePlayMode(Time.delta);
    }

    public void showRestartPrompt() {
        if (showingRestartPrompt) return;
        showingRestartPrompt = true;
    }

    public void reload() {
        showingRestartPrompt = false;

        // clear and reload level
        worldMap.clear();
        worldMap.load(world, world_path);

        // respawn player
        player = worldMap.spawnPlayer(world);

        // wire up camera controller
        var camera = world.first(CameraController.class);
        camera.worldMap = worldMap;
        camera.follow(player, Point.zero(), true);
        camera.resetRoom();

        // destroy and respawn items
        worldMap.destroyItems(world);
        worldMap.spawnItems(world);

        // destroy and respawn enemies
        enemies.forEach(enemy -> {
            if (enemy.entity() != null) {
                enemy.entity().destroy();
            }
        });
        enemies.clear();
        enemies = worldMap.spawnEnemies(world);

        // respawn other map entities
        worldMap.spawnBarriers(world);
        worldMap.spawnJumpthrus(world);
        worldMap.spawnSolids(world);

        Gdx.app.log("reload", "World map reloaded");
    }

    private void updatePlayMode(float dt) {
        // process input
        {
            Input.frame();

            var speed = 100f;
            if (Gdx.input.isKeyPressed(a.index) || Gdx.input.isKeyPressed(left.index))  worldCamera.translate(-speed * Time.delta, 0);
            if (Gdx.input.isKeyPressed(d.index) || Gdx.input.isKeyPressed(right.index)) worldCamera.translate( speed * Time.delta, 0);
            if (Gdx.input.isKeyPressed(w.index) || Gdx.input.isKeyPressed(up.index))    worldCamera.translate(0, -speed * Time.delta);
            if (Gdx.input.isKeyPressed(s.index) || Gdx.input.isKeyPressed(down.index))  worldCamera.translate(0,  speed * Time.delta);

            if (Input.pressed(escape)) Gdx.app.exit();
            if (showingRestartPrompt && (Gdx.input.justTouched()
             || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY))) {
                reload();
            }

            if (Input.pressed(f1)) DebugFlags.draw_entities     = !DebugFlags.draw_entities;
            if (Input.pressed(f2)) DebugFlags.draw_anim_bounds  = !DebugFlags.draw_anim_bounds;
            if (Input.pressed(f3)) DebugFlags.draw_world_origin = !DebugFlags.draw_world_origin;
            if (Input.pressed(f4)) DebugFlags.draw_temp_debug   = !DebugFlags.draw_temp_debug;

            if (Input.pressed(f6)) DebugFlags.frame_stepping_enabled = !DebugFlags.frame_stepping_enabled;
            if (DebugFlags.frame_stepping_enabled && !Input.pressed(f7)) {
                return;
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
            worldMap.update(Time.delta, world);
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
//                assets.layout.setText(assets.font, "Grotto", Color.WHITE, worldCamera.viewportWidth, Align.center, false);
//                assets.font.draw(batch, assets.layout, 0, (3f / 4f) * worldCamera.viewportHeight + assets.layout.height);
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
            Sprite coin = Assets.findSprite("coin");
            if (coin != null) {
                Sprite.Anim anim = coin.getAnimation("idle");
                TextureRegion texture = anim.frames.get(4).image;
                batch.draw(texture, 10, 10, 20, 20);
                int numCoins = player.get(Player.class).numCoins();
                assets.font.draw(batch, "" + numCoins, 35, 30);
            }
        }
        batch.end();
    }

    private void renderWindowOverlay() {
        // render hud
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            if (showingRestartPrompt) {
                batch.setColor(0.1f, 0.1f, 0.1f, 0.5f);
                batch.draw(assets.pixel, 0, 0, windowCamera.viewportWidth, windowCamera.viewportHeight);
                batch.setColor(Color.WHITE);

                var w = (1 / 2f) * windowCamera.viewportWidth;
                var h = (1 / 4f) * windowCamera.viewportHeight;
                var centerX = windowCamera.viewportWidth / 2f;
                var centerY = windowCamera.viewportHeight / 2f;
                var c = Color.NAVY;
                batch.setColor(c.r, c.g, c.b, 0.5f);
                batch.draw(assets.pixel, centerX - w / 2f, centerY - h / 2f, w, h);
                batch.setColor(Color.WHITE);

                var font = assets.font;
                var layout = assets.layout;
                layout.setText(font, "FUCK... YOU DIED\n\nCLICK OR PRESS ANY KEY\n\nTO RESTART", Color.WHITE, windowCamera.viewportWidth, Align.center, false);
                font.draw(batch, layout, 0, windowCamera.viewportHeight / 2f + assets.layout.height / 2f);
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

    public WorldMap getLevel() {
        return worldMap;
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
        public static boolean draw_temp_debug = false;
    }

}