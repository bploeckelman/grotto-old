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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import zendo.games.grotto.components.*;
import zendo.games.grotto.curves.CubicBezier;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.factories.CreatureFactory;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.map.WorldMap;
import zendo.games.grotto.sprites.Sprite;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

import java.util.List;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Game extends ApplicationAdapter {

    private static final String level_path = "levels/world-0.ldtk";

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

        worldMap = new WorldMap(world, assets, level_path);
        world.addEntity().add(new WorldMapContainer(worldMap), WorldMapContainer.class);

        player = worldMap.spawnPlayer(world);
        enemies = worldMap.spawnEnemies(world);
        worldMap.spawnBarriers(world);
        worldMap.spawnJumpthrus(world);
        worldMap.spawnSolids(world);

        // TEMP
        CreatureFactory.slider(world, bezier);

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
        if (Input.pressed(Input.Key.r) || world.first(Player.class).entity() == null) {
            reload();
        }

        updatePlayMode(Time.delta);
    }

    public void reload() {
        // clear and reload level
        worldMap.clear();
        worldMap.load(world, level_path);

        // respawn player
        player = worldMap.spawnPlayer(world);

        // wire up camera controller
        var camera = world.first(CameraController.class);
        camera.worldMap = worldMap;
        camera.follow(player, Point.zero(), true);
        camera.resetRoom();

        // destroy items (will be respawned by level.spawnEnemies())
        var item = world.first(Item.class);
        while (item != null) {
            var next = (Item) item.next;
            if (item.entity() != null) {
                item.entity().destroy();
            }
            item = next;
        }

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

            if (Input.pressed(Input.Key.escape)) Gdx.app.exit();
            if (Input.pressed(Input.Key.f1)) DebugFlags.draw_entities     = !DebugFlags.draw_entities;
            if (Input.pressed(Input.Key.f2)) DebugFlags.draw_anim_bounds  = !DebugFlags.draw_anim_bounds;
            if (Input.pressed(Input.Key.f3)) DebugFlags.draw_world_origin = !DebugFlags.draw_world_origin;
            if (Input.pressed(Input.Key.f4)) DebugFlags.draw_temp_debug   = !DebugFlags.draw_temp_debug;

            if (Input.pressed(Input.Key.f6)) DebugFlags.frame_stepping_enabled = !DebugFlags.frame_stepping_enabled;
            if (DebugFlags.frame_stepping_enabled && !Input.pressed(Input.Key.f7)) {
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
    /* NOTE: TEMP */
//    CubicBezier bezier = new CubicBezier(30, 30, 100, 150, 200, 150, 290, 30);
    CubicBezier bezier = new CubicBezier(30, 30, 30, 150, 70, -20, 270, 30);
//    CubicBezier bezier = new CubicBezier(30, 30, 160, 180, 70, -20, 270, 30);
    /* NOTE: TEMP */


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

                if (DebugFlags.draw_temp_debug) {
                    bezier.draw(shapes);
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
            // TODO: add overlay components
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