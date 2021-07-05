package zendo.games.grotto.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Json;
import zendo.games.grotto.Assets;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Enemy;
import zendo.games.grotto.components.Player;
import zendo.games.grotto.components.Tilemap;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.factories.CreatureFactory;
import zendo.games.grotto.factories.ItemFactory;
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
        public int tilesetUid;
        public int foregroundTilesetUid;
        public int colliderSize;
        public int colliderRows;
        public int colliderCols;
        public int[] colliderCells;
        public Point[] tilemapCellTextures;
        public Point[] foregroundTilemapCellTextures;
    }

    static class Spawner {
        public String type;
        public Point pos;
        Spawner(String type, int x, int y) {
            this.type = type;
            this.pos = Point.at(x, y);
        }
    }

    static class Tileset {
        public int uid;
        public int gridSize;
        public int rows;
        public int cols;
        public String name;
    }

    private final List<Entity> rooms;
    private final List<Spawner> spawners;
    private final IntMap<Tileset> tilesets;

    private final Assets assets;

    public Level(World world, Assets assets, String filename) {
        rooms = new ArrayList<>();
        spawners = new ArrayList<>();
        tilesets = new IntMap<>();

        this.assets = assets;
        load(world, filename);
    }

    // TODO - removeme, only used in editor
    public Entity entity() {
        return rooms.get(0);
    }

    public Entity room(Point position) {
        return room(position.x, position.y);
    }

    public Entity room(int x, int y) {
        for (var room : rooms) {
            var bounds = getRoomBounds(room);
            if (bounds != null && bounds.contains(x, y)) {
                return room;
            }
        }
        return null;
    }

    public RectI getRoomBounds(Entity room) {
        if (room == null) {
            return null;
        }

        // find the room's bounds collider
        var collider = room.get(Collider.class);
        while (collider != null) {
            if (collider.mask == Collider.Mask.room_bounds) {
                break;
            }
            collider = (Collider) collider.next;
        }

        if (collider == null) {
            return null;
        }
        return collider.rect();
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
            if (spawner.type.equals("player")) {
                player = CreatureFactory.player(assets, world, spawner.pos);
                break;
            }
        }
        return player;
    }

    public void update(float dt, World world) {
        var player = world.first(Player.class);
        var playerRoom = room(player.entity().position);
        var enemy = world.first(Enemy.class);
        while (enemy != null) {
            if (enemy.entity() == null) continue;
            var enemyRoom = room(enemy.entity().position);
            enemy.entity().active = (enemyRoom == playerRoom);
            enemy = (Enemy) enemy.next;
        }
    }

    public List<Enemy> spawnEnemies(World world) {
        var player = world.first(Player.class);
        var playerRoom = room(player.entity().position);

        var enemies = new ArrayList<Enemy>();
        for (var spawner : spawners) {
            Enemy enemy = null;
            switch (spawner.type) {
                case "slime"  -> enemy = CreatureFactory.slime(assets, world, spawner.pos).get(Enemy.class);
                case "goblin" -> enemy = CreatureFactory.goblin(assets, world, spawner.pos).get(Enemy.class);
                case "shroom" -> enemy = CreatureFactory.shroom(world, spawner.pos).get(Enemy.class);
                // TODO - spawn enemies / items separately?
                case "coin" -> ItemFactory.coin(world, spawner.pos);
                case "vase" -> ItemFactory.vase(world, spawner.pos);
                case "clostridium", "geobacter", "staphylococcus", "synechococcus"
                        -> ItemFactory.bacterium(spawner.type, assets, world, spawner.pos);
            }
            if (enemy != null) {
                var enemyRoom = room(enemy.entity().position);
                if (enemyRoom != playerRoom) {
                    enemy.entity().active = false;
                }
                enemies.add(enemy);
            }
        }
        return enemies;
    }

    public void clear() {
        for (var entity : rooms) {
            entity.destroy();
        }
        rooms.clear();
        spawners.clear();
    }

    public void load(World world, String filename) {
        var json = new Json();
        if (filename.endsWith(".json")) {
            // load the map descriptor from the specified json file
            var desc = json.fromJson(Level.Desc.class, Gdx.files.local(filename));
            if (desc == null) {
                throw new GdxRuntimeException("Unable to load level file: '" + filename + "'");
            }

            // load the room from the map descriptor
            var room = createRoomEntityFromDesc(desc, assets, world);
            rooms.add(room);
        } else if (filename.endsWith(".ldtk")) {
            // load ldtk file
            json.setIgnoreUnknownFields(true);
            var ldtk = json.fromJson(Ldtk.class, Gdx.files.internal(filename));

            // load the level descriptors from the ldtk file data
            var descs = loadDescsFromLdtk(ldtk);
            for (var desc : descs) {
                // load a room entity based on the level descriptor
                var room = createRoomEntityFromDesc(desc, assets, world);
                rooms.add(room);
            }
        }
    }

    public Entity createRoomEntityFromDesc(Desc desc, Assets assets, World world) {
        // setup tileset
        var tileset = tilesets.get(desc.tilesetUid);
        var tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
        var regions = tilesetTexture.split(tileset.gridSize, tileset.gridSize);

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
            var collider = entity.add(Collider.makeGrid(desc.colliderSize, desc.colliderCols, desc.colliderRows), Collider.class);
            collider.mask = Collider.Mask.solid;

            // initialize tilemap component contents
            for (int x = 0; x < desc.cols; x++) {
                for (int y = 0; y < desc.rows; y++) {
                    var point = desc.tilemapCellTextures[x + y * desc.cols];
                    tilemap.setCell(x, y, pointRegionMap.get(point));
                }
            }

            // initialize collider component contents
            for (int x = 0; x < desc.colliderCols; x++) {
                for (int y = 0; y < desc.colliderRows; y++) {
                    var value = (desc.colliderCells[x + y * desc.colliderCols] == 1);
                    collider.setCell(x, y, value);
                }
            }

            // add a foreground tile layer if one exists in the desc
            if (desc.foregroundTilemapCellTextures != null && desc.foregroundTilesetUid != -1) {
                // setup tileset
                tileset = tilesets.get(desc.foregroundTilesetUid);
                tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
                regions = tilesetTexture.split(tileset.gridSize, tileset.gridSize);

                // initialize a map between texture region array indices and the texture region they point to in the tileset
                pointRegionMap.clear();
                for (int x = 0; x < desc.cols; x++) {
                    for (int y = 0; y < desc.rows; y++) {
                        var point = desc.foregroundTilemapCellTextures[x + y * desc.cols];
                        if (point != null) {
                            pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                        }
                    }
                }

                // create foreground tilemap component and set regions
                var foreground = entity.add(new Tilemap(desc.tileSize, desc.cols, desc.rows), Tilemap.class);
                foreground.depth = 10;
                for (int x = 0; x < desc.cols; x++) {
                    for (int y = 0; y < desc.rows; y++) {
                        var point = desc.foregroundTilemapCellTextures[x + y * desc.cols];
                        foreground.setCell(x, y, pointRegionMap.get(point));
                    }
                }
            }

            // setup a collider for quick lookup of the room's bounds
            collider = entity.add(Collider.makeRect(RectI.at(
                    desc.position.x, desc.position.y,
                    desc.tileSize * desc.cols,
                    desc.tileSize * desc.rows
            )), Collider.class);
            collider.mask = Collider.Mask.room_bounds;
            collider.origin.set(-desc.position.x, -desc.position.y);
            collider.depth = 100;
        }
        entity.position.set(desc.position);

        return entity;
    }

    public void createAndSaveTestFile(World world, Assets assets, String filename) {
        var entity = world.addEntity();
        {
            /*
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
            desc.tilesetUid = 5;
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
            */
        }
        rooms.add(entity);
    }

    /** @deprecated */
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
            desc.tilesetUid = 5;
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
        /*
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
        */
        return index;
    }

    private List<Level.Desc> loadDescsFromLdtk(Ldtk ldtk) {
        var descs = new ArrayList<Level.Desc>();

        // instantiate tilesets
        for (var def : ldtk.defs.tilesets) {
            var nameEndIndex = def.relPath.lastIndexOf(".png");
            var nameBeginIndex = def.relPath.lastIndexOf("/");
            if (nameBeginIndex == -1) {
                nameBeginIndex = 0;
            }
            var tileset = new Tileset();
            tileset.uid = def.uid;
            tileset.rows = def.__cHei;
            tileset.cols = def.__cWid;
            tileset.gridSize = def.tileGridSize;
            tileset.name = def.relPath.substring(nameBeginIndex, nameEndIndex);
            tilesets.put(tileset.uid, tileset);
        }

        var numLevels = ldtk.levels.size();
        for (int levelNum = 0; levelNum < numLevels; levelNum++) {
            var desc = new Level.Desc();
            {
                var level = ldtk.levels.get(levelNum);

                // find required layers
                Ldtk.LayerInstance tileLayer = null;
                Ldtk.LayerInstance collisionLayer = null;
                Ldtk.LayerInstance entityLayer = null;
                Ldtk.LayerInstance foregroundLayer = null;
                for (var layer : level.layerInstances) {
                    if ("Tiles".equals(layer.__type)) {
                        if ("Tiles".equals(layer.__identifier)) {
                            tileLayer = layer;
                        } else if ("Foreground".equals(layer.__identifier)) {
                            foregroundLayer = layer;
                        }
                    } else if ("IntGrid".equals(layer.__type) && "Collision".equals(layer.__identifier)) {
                        collisionLayer = layer;
                    } else if ("Entities".equals(layer.__type) && "Entities".equals(layer.__identifier)) {
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

                // lookup tileset
                var tilesetUid = tileLayer.__tilesetDefUid;
                var tileset = tilesets.get(tilesetUid, null);
                if (tileset == null) {
                    throw new GdxRuntimeException("Failed to load ldtk file, missing tileset with uid " + tilesetUid + " in tile layer");
                }

                desc.position = Point.at(level.worldX, -(level.worldY + level.pxHei));
                desc.tileSize = tileset.gridSize;
                desc.cols = tileLayer.__cWid;
                desc.rows = tileLayer.__cHei;
                desc.tilesetUid = tileset.uid;
                desc.foregroundTilesetUid = -1;
                desc.colliderSize = collisionLayer.__gridSize;
                desc.colliderCols = collisionLayer.__cWid;
                desc.colliderRows = collisionLayer.__cHei;
                desc.colliderCells = new int[desc.colliderCols * desc.colliderRows];
                desc.tilemapCellTextures = new Point[desc.cols * desc.rows];
                desc.foregroundTilemapCellTextures = null;

                // setup spawners
                for (var entity : entityLayer.entityInstances) {
                    if ("Spawner".equals(entity.__identifier)) {
                        for (var field : entity.fieldInstances) {
                            if ("Type".equals(field.__identifier)) {
                                var type = field.__value;
                                var x = desc.position.x + entity.px[0];
                                var flipY = level.pxHei - entity.px[1];
                                var y = desc.position.y + flipY;
                                spawners.add(new Spawner(type, x, y));
                            }
                        }
                    }
                }

                // setup collision layer
                for (int x = 0; x < desc.colliderCols; x++) {
                    for (int y = 0; y < desc.colliderRows; y++) {
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        int flipY = (desc.colliderRows - 1) - y;
                        var collisionValue = collisionLayer.intGridCsv[x + flipY * desc.colliderCols];
                        desc.colliderCells[x + y * desc.colliderCols] = collisionValue;
                    }
                }

                // setup tilemap layer
                for (var gridTileEntry : tileLayer.gridTiles) {
                    var px = gridTileEntry.px;
                    var tile = Point.at(px[0] / tileset.gridSize, px[1] / tileset.gridSize);
                    // note: ldtk files are stored with origin = top left so we have to flip y
                    tile.y = (desc.rows - 1) - tile.y;

                    var src = gridTileEntry.src;
                    var textureIndex = Point.at(src[0] / tileset.gridSize, src[1] / tileset.gridSize);

                    desc.tilemapCellTextures[tile.x + tile.y * desc.cols] = textureIndex;
                }

                // setup foreground tilemap layer
                if (foregroundLayer != null) {
                    var foregroundTilesetUid = foregroundLayer.__tilesetDefUid;
                    desc.foregroundTilesetUid = foregroundTilesetUid;

                    var foregroundTileset = tilesets.get(foregroundTilesetUid, null);
                    if (foregroundTileset == null) {
                        throw new GdxRuntimeException("Failed to load ldtk file, missing tileset with uid " + foregroundTilesetUid + " in foreground layer");
                    }

                    // setup foreground layer
                    desc.foregroundTilemapCellTextures = new Point[desc.cols * desc.rows];
                    for (var gridTileEntry : foregroundLayer.gridTiles) {
                        var px = gridTileEntry.px;
                        var tile = Point.at(px[0] / foregroundTileset.gridSize, px[1] / foregroundTileset.gridSize);
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        tile.y = (desc.rows - 1) - tile.y;

                        var src = gridTileEntry.src;
                        var textureIndex = Point.at(src[0] / foregroundTileset.gridSize, src[1] / foregroundTileset.gridSize);

                        desc.foregroundTilemapCellTextures[tile.x + tile.y * desc.cols] = textureIndex;
                    }
                }
            }
            descs.add(desc);
        }

        return descs;
    }

}
