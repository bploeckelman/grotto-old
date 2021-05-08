package zendo.games.grotto.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisWindow;
import zendo.games.grotto.Assets;
import zendo.games.grotto.Game;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

public class Editor {

    private static final boolean DRAW_EXTRAS = false;

    private final Game game;
    private final Assets assets;
    private final Stage stage;

    private boolean leftMousePressed;
    private boolean rightMousePressed;
    private Point lastPress;
    private Point mouseDelta;
    private Point startPos;

    public float lastZoom;

    public Editor(Game game, Assets assets) {
        this.game = game;
        this.assets = assets;
        this.stage = new Stage(new ScreenViewport(game.getWindowCamera()), assets.batch);

        this.leftMousePressed = false;
        this.rightMousePressed = false;
        this.lastPress = Point.zero();
        this.mouseDelta = Point.zero();
        this.startPos = Point.zero();
        this.lastZoom = 0;

        initializeWidgets();
    }

    private void initializeWidgets() {
        var camera = game.getWindowCamera();

        var window = new VisWindow("Editor");
        window.setSize(200, camera.viewportHeight);

        stage.addActor(window);
    }

    public void update(float dt) {
        var world = game.getWorld();
        var worldCamera = game.getWorldCamera();
        var worldMouse = game.getWorldMouse();
        var level = game.getLevel();

        // process input
        {
            Input.frame();

            if (Input.pressed(Input.Key.tab)) {
                game.toggleMode();
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
                leftMousePressed = true;
                lastPress.set((int) Input.mouse().x, (int) Input.mouse().y);
                startPos.set((int) worldCamera.position.x, (int) worldCamera.position.y);
            }
            if (Input.released(Input.MouseButton.left)) {
                leftMousePressed = false;
                lastPress.set(0, 0);
            }

            if (Input.pressed(Input.MouseButton.right)) {
                rightMousePressed = true;
                lastPress.set((int) worldMouse.x, (int) worldMouse.y);
                startPos.set(level.entity.position);
            }
            if (Input.released(Input.MouseButton.right)) {
                rightMousePressed = false;
                lastPress.set(0, 0);
            }

            if (leftMousePressed) {
                mouseDelta.set((int) Input.mouse().x - lastPress.x, (int) Input.mouse().y - lastPress.y);
                worldCamera.translate((int) (-mouseDelta.x * dt), (int) (mouseDelta.y * dt), 0);
                worldCamera.update();
            }
            else if (rightMousePressed) {
                mouseDelta.set((int) worldMouse.x - lastPress.x, (int) worldMouse.y - lastPress.y);
                level.entity.position.set(startPos.x + mouseDelta.x, startPos.y + mouseDelta.y);
            }
        }

        // update timer
        {
            Time.millis += Time.delta;
            Time.previous_elapsed = Time.elapsed_millis();
        }

        // update systems
        {
            stage.act(Time.delta);
            assets.tween.update(Time.delta);
            world.update(Time.delta);
        }
    }

    public void renderStage() {
        stage.draw();
    }

    public void render(SpriteBatch batch) {
        if (!DRAW_EXTRAS) return;

        var margin = 10;
        var panelWidth = 200;
        var worldMouse = game.getWorldMouse();
        var worldCamera = game.getWorldCamera();
        var windowCamera = game.getWindowCamera();

        // draw control panel
        batch.setColor(0.2f, 0.2f, 0.2f, 0.6f);
        batch.draw(assets.pixel, 0, 0, panelWidth, windowCamera.viewportHeight);
        batch.setColor(Color.WHITE);

        // mouse pos, current zoom
        {
            var color = Color.WHITE;

            assets.font.getData().setScale(0.9f);
            {
                var mouseStr = String.format("SCREEN:\n\n(%4.0f,%3.0f)", Input.mouse().x, Input.mouse().y);
                assets.layout.setText(assets.font, mouseStr, color, panelWidth, Align.left, false);
                assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin);
            }
            var screenMouseHeight = assets.layout.height;
            assets.font.getData().setScale(1f);

            assets.font.getData().setScale(0.9f);
            {
                var worldMouseStr = String.format("WORLD:\n\n(%3.0f,%3.0f)", worldMouse.x, worldMouse.y);
                assets.layout.setText(assets.font, worldMouseStr, color, panelWidth, Align.left, false);
                assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin - screenMouseHeight - margin);
            }
            var worldMouseHeight = assets.layout.height;
            assets.font.getData().setScale(1f);

            var zoomStr = String.format("ZOOM:\n\n(%2.2f)", worldCamera.zoom);
            assets.layout.setText(assets.font, zoomStr, color, panelWidth, Align.left, false);
            assets.font.draw(batch, assets.layout, margin, windowCamera.viewportHeight - margin - screenMouseHeight - margin - worldMouseHeight - margin);
        }
    }

}
