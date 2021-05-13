package zendo.games.grotto.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import org.w3c.dom.css.Rect;
import zendo.games.grotto.Assets;
import zendo.games.grotto.Config;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Enemy;
import zendo.games.grotto.components.Player;
import zendo.games.grotto.components.Tilemap;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.factories.CreatureFactory;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Level {

    static class Desc {
        public Point position;
        public int tileSize;
        public int cols;
        public int rows;
        public String tileset;
        public int[] colliderCells;
        public Point[] tilemapCellTextures;
    }

    static class Spawner {
        public String type;
        public Point pos;
    }

    public List<Entity> entities; // these are rooms
    public List<Spawner> spawners;

    public int pixelWidth;
    public int pixelHeight;

    public Entity entity() {
        return entities.get(0);
    }

    public Entity room(int x, int y) {
        Entity room = null;
        var bounds = RectI.pool.obtain();
        for (var entity : entities) {
            var tilemap = entity.get(Tilemap.class);
            bounds.set(entity.position.x, entity.position.y,
                    tilemap.cols() * tilemap.tileSize(),
                    tilemap.rows() * tilemap.tileSize());
            if (bounds.contains(x, y)) {
                room = entity;
                break;
            }
        }
        RectI.pool.free(bounds);
        return room;
    }

    public Entity spawnPlayer(World world) {
        // clear existing player
        var p = world.first(Player.class);
        if (p != null) {
            p.entity().destroy();
        }

        // spawn new player
        Entity player = null;
        for (var spawner : spawners) {
            // todo - only spawn from current room's player spawner
            if (spawner.type.equals("player")) {
                player = CreatureFactory.player(world, spawner.pos);
                break;
            }
        }
        return player;
    }

    public List<Enemy> spawnEnemies(World world) {
        var enemies = new ArrayList<Enemy>();
        for (var spawner : spawners) {
            switch (spawner.type) {
                case "slime"  -> enemies.add(CreatureFactory.slime(world, spawner.pos).get(Enemy.class));
                case "goblin" -> enemies.add(CreatureFactory.goblin(world, spawner.pos).get(Enemy.class));
            }
        }
        return enemies;
    }

    public Level(World world, Assets assets, String filename) {
        entities = new ArrayList<>();
        spawners = new ArrayList<>();

        load(world, assets, filename);
    }

    public void clear() {
        for (var entity : entities) {
            entity.destroy();
        }
        entities.clear();
        spawners.clear();
    }

    public void load(World world, Assets assets, String filename) {
        var json = new Json();
        if (filename.endsWith(".json")) {
            // load the map descriptor directly from the specified json file
            var desc = json.fromJson(Level.Desc.class, Gdx.files.local(filename));
            if (desc == null) {
                throw new GdxRuntimeException("Unable to load level file: '" + filename + "'");
            }
            var entity = createFromDesc(desc, assets, world);
            entities.add(entity);

            pixelWidth  = desc.tileSize * desc.cols;
            pixelHeight = desc.tileSize * desc.rows;
        } else if (filename.endsWith(".ldtk")) {
            // load ldtk file
            json.setIgnoreUnknownFields(true);
            var ldtk = json.fromJson(Ldtk.class, Gdx.files.internal(filename));

            // load the level descriptors from the ldtk file data
            var descs = loadDescsFromLdtk(ldtk);
            for (var desc : descs) {
                // load a room entity based on the level descriptor
                var entity = createFromDesc(desc, assets, world);
                entities.add(entity);
            }

            // TODO - set pix w/h from 'current' level, not just level 0, maybe even do this somewhere else
            var tilemap = entity().get(Tilemap.class);
            pixelWidth  = tilemap.tileSize() * tilemap.cols();
            pixelHeight = tilemap.tileSize() * tilemap.rows();
        }
    }

    public Entity createFromDesc(Desc desc, Assets assets, World world) {
        // setup tileset
        var tileset = assets.atlas.findRegion(desc.tileset);
        var regions = assets.tilesetRegions;

        // initialize a map between texture region array indices and the texture region they point to in the tileset
        var pointRegionMap = new HashMap<Point, TextureRegion>();
        for (int x = 0; x < desc.cols; x++) {
            for (int y = 0; y < desc.rows; y++) {
                var point = desc.tilemapCellTextures[x + y * desc.cols];
                if (point != null) {
                    pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                }
            }
        }

        // create the room entity and initialize it
        var entity = world.addEntity();
        {
            // create components
            var tilemap = entity.add(new Tilemap(desc.tileSize, desc.cols, desc.rows), Tilemap.class);
            var collider = entity.add(Collider.makeGrid(desc.tileSize, desc.cols, desc.rows), Collider.class);
            collider.mask = Collider.Mask.solid;

            // initialize component contents
            for (int x = 0; x < desc.cols; x++) {
                for (int y = 0; y < desc.rows; y++) {
                    var point = desc.tilemapCellTextures[x + y * desc.cols];
                    tilemap.setCell(x, y, pointRegionMap.get(point));

                    var value = (desc.colliderCells[x + y * desc.cols] == 1);
                    collider.setCell(x, y, value);
                }
            }
        }
        entity.position.set(desc.position);

        return entity;
    }

    public void createAndSaveTestFile(World world, Assets assets, String filename) {
        var entity = world.addEntity();
        {
            var tileSize = 16;
            var cols = (Config.framebuffer_width + (Config.framebuffer_width / 2)) / tileSize;
            var rows = (Config.framebuffer_height + (Config.framebuffer_height / 3)) / tileSize + 1;

            var ul = assets.tilesetRegions[0][3];
            var u = assets.tilesetRegions[0][4];
            var ur = assets.tilesetRegions[0][5];

            var uu = assets.tilesetRegions[4][10];

            var l = assets.tilesetRegions[1][3];
            var r = assets.tilesetRegions[1][5];

            var dl = assets.tilesetRegions[2][3];
            var d = assets.tilesetRegions[2][4];
            var dr = assets.tilesetRegions[2][5];

            var tilemap = entity.add(new Tilemap(tileSize, cols, rows), Tilemap.class);
            // bottom row
            tilemap.setCells(1, 0, cols - 2, 1, d);
            // top rows
            tilemap.setCells(0, rows - 1, cols, 1, uu);
            tilemap.setCells(1, rows - 2, cols - 2, 1, u);
            // left side
            tilemap.setCells(0, 1, 1, rows - 2, l);
            // right side
            tilemap.setCells(cols - 1, 1, 1, rows - 2, r);
            // corners
            tilemap.setCell(0, 0, dl);
            tilemap.setCell(0, rows - 2, ul);
            tilemap.setCell(cols - 1, rows - 2, ur);
            tilemap.setCell(cols - 1, 0, dr);

            // platforms
            tilemap.setCells(10, 2, 5, 1, d);
            tilemap.setCells(15, 4, 5, 1, d);
            tilemap.setCells(20, 6, 5, 1, d);
            tilemap.setCells(15, 8, 5, 1, d);
            tilemap.setCells(10, 10, 5, 1, d);

            var collider = entity.add(Collider.makeGrid(tileSize, cols, rows), Collider.class);
            collider.mask = Collider.Mask.solid;
            collider.setCells(0, 0, cols, 1, true);
            collider.setCells(0, rows - 1, cols, 1, true);
            collider.setCells(0, rows - 2, cols, 1, true);
            collider.setCells(0, 0, 1, rows - 1, true);
            collider.setCells(cols - 1, 0, 1, rows - 1, true);

            collider.setCells(10, 2, 5, 1, true);
            collider.setCells(15, 4, 5, 1, true);
            collider.setCells(20, 6, 5, 1, true);
            collider.setCells(15, 8, 5, 1, true);
            collider.setCells(10, 10, 5, 1, true);

            // --------------------------------------------

            // build the descriptor for json output
            var desc = new Desc();
            desc.position = Point.zero();
            desc.tileSize = tileSize;
            desc.cols = cols;
            desc.rows = rows;
            desc.tileset = "tileset";
            desc.colliderCells = new int[cols * rows];
            desc.tilemapCellTextures = new Point[cols * rows];

            // initialize tilemap cell textures and collider cells
            for (int x = 0; x < cols; x++) {
                for (int y = 0; y < rows; y++) {
                    var collision = collider.getCell(x, y);
                    desc.colliderCells[x + y * cols] = collision ? 1 : 0;

                    var texture = tilemap.getCell(x, y);
                    if      (texture == ul) desc.tilemapCellTextures[x + y * cols] = Point.at(3, 0);
                    else if (texture == u)  desc.tilemapCellTextures[x + y * cols] = Point.at(4, 0);
                    else if (texture == ur) desc.tilemapCellTextures[x + y * cols] = Point.at(5, 0);
                    else if (texture == uu) desc.tilemapCellTextures[x + y * cols] = Point.at(10, 4);
                    else if (texture == l)  desc.tilemapCellTextures[x + y * cols] = Point.at(3, 1);
                    else if (texture == r)  desc.tilemapCellTextures[x + y * cols] = Point.at(5, 1);
                    else if (texture == dl) desc.tilemapCellTextures[x + y * cols] = Point.at(3, 2);
                    else if (texture == d)  desc.tilemapCellTextures[x + y * cols] = Point.at(4, 2);
                    else if (texture == dr) desc.tilemapCellTextures[x + y * cols] = Point.at(5, 2);
                    else                    desc.tilemapCellTextures[x + y * cols] = null;
                }
            }

            var json = new Json();
            var descJson = json.toJson(desc, Level.Desc.class);
            var outFile = Gdx.files.local(filename);
            outFile.writeString(descJson, false);

            pixelWidth  = desc.tileSize * desc.cols;
            pixelHeight = desc.tileSize * desc.rows;
        }
        entities.add(entity);
    }

    public void save(String filename, Assets assets) {
        if (entity() == null) {
            throw new GdxRuntimeException("Can't save level, no level entity");
        }
        var tilemap = entity().get(Tilemap.class);
        if (tilemap == null) {
            throw new GdxRuntimeException("Can't save level, entity missing tilemap component");
        }
        var collider = entity().get(Collider.class);
        if (collider == null) {
            throw new GdxRuntimeException("Can't save level, entity missing collider component");
        }
        if (collider.grid() == null) {
            throw new GdxRuntimeException("Can't save level, entity collider component is not a grid");
        }

        {
            // build the descriptor for json output from the current entity state
            var desc = new Desc();
            desc.position = entity().position;
            desc.tileSize = tilemap.tileSize();
            desc.cols = tilemap.cols();
            desc.rows = tilemap.rows();
            desc.tileset = "tileset";                                                  // TODO
            desc.colliderCells = new int[desc.cols * desc.rows];
            desc.tilemapCellTextures = new Point[desc.cols * desc.rows];

            // populate desc arrays with data from entity components
            for (int x = 0; x < desc.cols; x++) {
                for (int y = 0; y < desc.rows; y++) {
                    var collision = collider.getCell(x, y);
                    desc.colliderCells[x + y * desc.cols] = collision ? 1 : 0;

                    var tile = tilemap.getCell(x, y);
                    if (tile != null) {
                        desc.tilemapCellTextures[x + y * desc.cols] = findTilesetIndex(tile, assets);
                    }
                }
            }

            // write the output file
            var json = new Json();
            var descJson = json.toJson(desc, Level.Desc.class);
            var outFile = Gdx.files.local(filename);
            outFile.writeString(descJson, false);

            Gdx.app.log("level", "saved level: " + filename);
        }
    }

    private Point findTilesetIndex(TextureRegion tile, Assets assets) {
        var index = Point.zero();
        var tiles = assets.tilesetRegions;
        var rows = tiles.length;
        var cols = tiles[0].length;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (tile == tiles[y][x]) {
                    index.set(x, y);
                    return index;
                }
            }
        }
        Gdx.app.log("findTilesetIndex", "failed to find tile");
        return index;
    }

    private List<Level.Desc> loadDescsFromLdtk(Ldtk ldtk) {
        var descs = new ArrayList<Level.Desc>();

        var numLevels = ldtk.levels.size();
        for (int levelNum = 0; levelNum < numLevels; levelNum++) {
            var desc = new Level.Desc();
            {
                var level = ldtk.levels.get(levelNum);
                var tileset = ldtk.defs.tilesets.get(0);
                var tilesetName = tileset.relPath.substring(tileset.relPath.lastIndexOf("/") + 1, tileset.relPath.lastIndexOf(".png"));

                // find required layers
                Ldtk.LayerInstance tileLayer = null;
                Ldtk.LayerInstance collisionLayer = null;
                Ldtk.LayerInstance entityLayer = null;
                for (var layer : level.layerInstances) {
                    if (layer.__type.equals("Tiles") && layer.__identifier.equals("Tiles")) {
                        tileLayer = layer;
                    } else if (layer.__type.equals("IntGrid") && layer.__identifier.equals("Collision")) {
                        collisionLayer = layer;
                    } else if (layer.__type.equals("Entities") && layer.__identifier.equals("Entities")) {
                        entityLayer = layer;
                    }
                }
                if (tileLayer == null) {
                    throw new GdxRuntimeException("Failed to load ldtk file, no 'Tiles' layer found");
                }
                if (collisionLayer == null) {
                    throw new GdxRuntimeException("Failed to load ldtk file, no IntGrid 'Collision' layer found");
                }
                if (entityLayer == null) {
                    throw new GdxRuntimeException("Failed to load ldtk file, no 'Entities' layer found");
                }

                desc.position = Point.at(level.worldX, -(level.worldY + level.pxHei));
                desc.tileSize = tileset.tileGridSize;
                desc.cols = tileLayer.__cWid;
                desc.rows = tileLayer.__cHei;
                desc.tileset = tilesetName;
                desc.colliderCells = new int[desc.cols * desc.rows];
                desc.tilemapCellTextures = new Point[desc.cols * desc.rows];

                // setup spawners
                for (var entity : entityLayer.entityInstances) {
                    if ("Spawner".equals(entity.__identifier)) {
                        // extract 'Type' field to figure out what to spawn
                        for (var field : entity.fieldInstances) {
                            if ("Type".equals(field.__identifier)) {
                                var type = field.__value;
                                var x = desc.position.x + entity.px[0] + desc.tileSize / 2;
                                var y = desc.position.y + entity.px[1];
                                var flipY = ((desc.rows - 1) * desc.tileSize) - y + desc.tileSize / 2;
                                var spawner = new Spawner();
                                spawner.type = type;
                                spawner.pos = Point.at(x, flipY);
                                spawners.add(spawner);
                            }
                        }
                    }
                }

                // setup collision layer
                for (int x = 0; x < desc.cols; x++) {
                    for (int y = 0; y < desc.rows; y++) {
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        int flipY = (desc.rows - 1) - y;
                        var collisionValue = collisionLayer.intGridCsv[x + flipY * desc.cols];

                        var value = collisionValue;
                        desc.colliderCells[x + y * desc.cols] = value;
                    }
                }

                // setup tilemap layer
                for (var gridTileEntry : tileLayer.gridTiles) {
                    var px = gridTileEntry.px;
                    var tile = Point.at(px[0] / tileset.tileGridSize, px[1] / tileset.tileGridSize);
                    // note: ldtk files are stored with origin = top left so we have to flip y
                    tile.y = (desc.rows - 1) - tile.y;

                    var src = gridTileEntry.src;
                    var textureIndex = Point.at(src[0] / tileset.tileGridSize, src[1] / tileset.tileGridSize);

                    desc.tilemapCellTextures[tile.x + tile.y * desc.cols] = textureIndex;
                }
            }
            descs.add(desc);
        }

        return descs;
    }

}
