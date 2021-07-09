package zendo.games.grotto.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisWindow;
import zendo.games.grotto.Assets;
import zendo.games.grotto.Game;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Tilemap;
import zendo.games.grotto.input.Input;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

public class Editor {

    private static final boolean DRAW_EXTRAS = false;

    private final Game game;
    private final Assets assets;
    private final Stage stage;

    private boolean leftMousePressed;
    private boolean rightMousePressed;
    private boolean middleMousePressed;
    private Point lastPress;
    private Point mouseDelta;
    private Point startPos;

    private Point selectedTileCoord;

    public float lastZoom;

    public Editor(Game game, Assets assets) {
        this.game = game;
        this.assets = assets;
        this.stage = new Stage(new ScreenViewport(game.getWindowCamera()), assets.batch);

        this.leftMousePressed = false;
        this.rightMousePressed = false;
        this.middleMousePressed = false;
        this.lastPress = Point.zero();
        this.mouseDelta = Point.zero();
        this.startPos = Point.zero();
        this.lastZoom = 0;

        this.selectedTileCoord = null;

        initializeWidgets();
    }

    public Stage getStage() {
        return stage;
    }

    private void initializeWidgets() {
        var level = game.getLevel();
        var camera = game.getWindowCamera();

        var width = 50;
        var window = new VisWindow("Editor");
        window.setSize(width, camera.viewportHeight);
        window.defaults().pad(1f);

        // scroll pane
        // TODO - scroll pane steals mouse wheel once it gets focus once, so wheel no longer works for zoom after that
        var scrollTable = new VisTable();
        var scrollPane = new VisScrollPane(scrollTable);
        scrollPane.setFlickScroll(false);
        scrollPane.setFadeScrollBars(false);
        window.row();
        window.add(scrollPane).growX();

        // tileset buttons in scroll pane
        /*
        var tiles = assets.tilesetRegions;
        var cols = 12;
        var sections = 8;
        var sectionsPerRow = 4;
        var rowsPerSection = 3;
        var section = 0;
        var done = false;
        do {
            // test tileset consists of a bunch of 3x3 sections
            // so layout the buttons in the tool panel that way
            for (int y = 0; y < 3; y++) {
                scrollTable.row();

                for (int x = 0; x < 3; x++) {
                    var sectionRow = (section / sectionsPerRow);
                    var ix = x + (section * rowsPerSection) % cols;
                    var iy = y + (section * rowsPerSection) % rowsPerSection + (sectionRow * rowsPerSection);

                    var drawable = new TextureRegionDrawable(tiles[iy][ix]);
                    drawable.setMinSize(64, 64);

                    var button = new VisImageButton(drawable);
                    button.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            selectedTileCoord = Point.at(ix, iy);
                        }
                    });
                    scrollTable.add(button).size(64, 64);
                }
            }

            section++;
            if (section >= sections) {
                done = true;
            }
        } while (!done);
        */

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

            // left mouse --------------------------------
            if (Input.pressed(Input.MouseButton.left)) {
                leftMousePressed = true;
                lastPress.set((int) Input.mouse().x, (int) Input.mouse().y);
                startPos.set((int) worldCamera.position.x, (int) worldCamera.position.y);
            }
            if (Input.released(Input.MouseButton.left)) {
                leftMousePressed = false;
                lastPress.set(0, 0);
            }

            // right mouse -------------------------------
            if (Input.pressed(Input.MouseButton.right)) {
                rightMousePressed = true;
                lastPress.set((int) worldMouse.x, (int) worldMouse.y);
                startPos.set(level.entity().position);

                // deselect currently selected tile
                if (selectedTileCoord != null) {
                    selectedTileCoord = null;
                }
            }
            if (Input.released(Input.MouseButton.right)) {
                rightMousePressed = false;
                lastPress.set(0, 0);
            }

            // middle mouse ------------------------------
            if (Input.pressed(Input.MouseButton.middle)) {
                middleMousePressed = true;
                lastPress.set((int) Input.mouse().x, (int) Input.mouse().y);
                startPos.set((int) worldCamera.position.x, (int) worldCamera.position.y);
            }
            if (Input.released(Input.MouseButton.middle)) {
                middleMousePressed = false;
                lastPress.set(0, 0);
            }

            if (leftMousePressed) {
                mouseDelta.set((int) Input.mouse().x - lastPress.x, (int) Input.mouse().y - lastPress.y);
                // paint tile onto map
                /*
                if (selectedTileCoord != null) {
                    var tilemap = level.entity().get(Tilemap.class);
                    var collider = level.entity().get(Collider.class);

                    var tiles = assets.tilesetRegions;
                    var tileSize = tilemap.tileSize();
                    var tileX = (int) Calc.floor((worldMouse.x - level.entity().position.x)  / tileSize);
                    var tileY = (int) Calc.floor((worldMouse.y - level.entity().position.y)  / tileSize);

                    if (tileX >= 0 && tileY >= 0 && tileX < tilemap.cols() && tileY < tilemap.rows()) {
                        tilemap.setCell(tileX, tileY, tiles[selectedTileCoord.y][selectedTileCoord.x]);
                        // TODO - set collision layer separate from tilemap
                        collider.setCell(tileX, tileY, true);
                    }
                }
                */
            }
            else if (rightMousePressed) {
                mouseDelta.set((int) Input.mouse().x - lastPress.x, (int) Input.mouse().y - lastPress.y);
                // erase tiles
                if (selectedTileCoord == null) {
                    var tilemap = level.entity().get(Tilemap.class);
                    var collider = level.entity().get(Collider.class);

                    var tileSize = tilemap.tileSize();
                    var tileX = (int) Calc.floor((worldMouse.x - level.entity().position.x)  / tileSize);
                    var tileY = (int) Calc.floor((worldMouse.y - level.entity().position.y)  / tileSize);

                    if (tileX >= 0 && tileY >= 0 && tileX < tilemap.cols() && tileY < tilemap.rows()) {
                        tilemap.setCell(tileX, tileY, null);
                        // TODO - set collision layer separate from tilemap
                        collider.setCell(tileX, tileY, false);
                    }
                }
            }
            else if (middleMousePressed) {
                if (Input.down(Input.Key.shift_left)) {
                    // move level
                    // TODO - should probably move this a tile at a time by default, allowing pixel movement with a modifier key
                    mouseDelta.set((int) worldMouse.x - lastPress.x, (int) worldMouse.y - lastPress.y);
                    level.entity().position.set(startPos.x + mouseDelta.x, startPos.y + mouseDelta.y);
                } else {
                    // move camera
                    mouseDelta.set((int) Input.mouse().x - lastPress.x, (int) Input.mouse().y - lastPress.y);
                    worldCamera.translate((int) (-mouseDelta.x * dt), (int) (mouseDelta.y * dt), 0);
                    worldCamera.update();
                }
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

    public void renderWorld(SpriteBatch batch) {
        /*
        if (selectedTileCoord != null) {
            var level = game.getLevel();
            var worldMouse = game.getWorldMouse();
            var tileSize = level.entity().get(Tilemap.class).tileSize();
            var tileX = tileSize * Calc.floor(worldMouse.x / tileSize);
            var tileY = tileSize * Calc.floor(worldMouse.y / tileSize);
            batch.draw(assets.tilesetRegions[selectedTileCoord.y][selectedTileCoord.x],
                    tileX, tileY, tileSize, tileSize);
        }
        */
    }

    public void render(SpriteBatch batch) {
        if (!DRAW_EXTRAS) return;

        var margin = 10;
        var panelWidth = 220;
        var worldMouse = game.getWorldMouse();
        var worldCamera = game.getWorldCamera();
        var windowCamera = game.getWindowCamera();

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

    public void renderGrid(ShapeRenderer shapes) {
        // skip this since we're not actually using this mode as an editor anymore
        if (true) return;

        var level = game.getLevel();
        var tilemap = level.entity().get(Tilemap.class);
        var tileSize = tilemap.tileSize();
        var rows = tilemap.rows();
        var cols = tilemap.cols();
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.2f);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                shapes.rect(level.entity().position.x + x * tileSize, level.entity().position.y + y * tileSize, tileSize, tileSize);
            }
        }
        shapes.setColor(Color.WHITE);
    }

}
