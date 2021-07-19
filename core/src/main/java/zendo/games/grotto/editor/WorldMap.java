package zendo.games.grotto.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Json;
import zendo.games.grotto.Assets;
import zendo.games.grotto.components.*;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.factories.CreatureFactory;
import zendo.games.grotto.factories.ItemFactory;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WorldMap implements Disposable {

    // ------------------------------------------
    // Helper data structures
    // ------------------------------------------

    static class RoomDesc {
        public Point position;
        public int tileSize;
        public int cols;
        public int rows;
        public int tilesetUid;
        public int foregroundTilesetUid;
        public int entityGridSize;
        public int colliderSize;
        public int colliderRows;
        public int colliderCols;
        public int[] colliderCells;
        public Texture backgroundImage;
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

    public static class Jumpthru {
        public RectI bounds;
        public Entity entity;
        Jumpthru(RectI bounds) {
            this.bounds = bounds;
            this.entity = null;
        }
    }

    static class Tileset {
        public int uid;
        public int gridSize;
        public int rows;
        public int cols;
        public String name;
    }

    // ------------------------------------------
    // Member data
    // ------------------------------------------

    private final List<Entity> rooms;
    private final List<Spawner> spawners;
    private final List<Jumpthru> jumpthrus;
    private final IntMap<Tileset> tilesets;
    private final List<Texture> backgrounds;

    private final Assets assets;

    // ------------------------------------------
    // Constructor and interface implementations
    // ------------------------------------------

    public WorldMap(World world, Assets assets, String filename) {
        rooms = new ArrayList<>();
        spawners = new ArrayList<>();
        jumpthrus = new ArrayList<>();
        tilesets = new IntMap<>();
        backgrounds = new ArrayList<>();

        this.assets = assets;
        load(world, filename);
    }

    @Override
    public void dispose() {
        backgrounds.forEach(Texture::dispose);
    }

    public void update(float dt, World world) {
        // only enemies in the same room as the player are active
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

    public void clear() {
        for (var entity : rooms) {
            entity.destroy();
        }
        rooms.clear();
        spawners.clear();
        jumpthrus.clear();
    }

    // ------------------------------------------
    // Getters and setters
    // ------------------------------------------

    public List<Jumpthru> jumpthrus() {
        return jumpthrus;
    }

    // ------------------------------------------
    // Room lookup API
    // ------------------------------------------

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

    // ------------------------------------------
    // Spawning API
    // ------------------------------------------

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
                case "eye"    -> enemy = CreatureFactory.eye(world, spawner.pos).get(Enemy.class);
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

    public void spawnJumpthrus(World world) {
        // TODO: factory?
        for (var jumpthru : jumpthrus) {
            var entity = world.addEntity();
            var collider = entity.add(Collider.makeRect(jumpthru.bounds), Collider.class);
            collider.mask = Collider.Mask.jumpthru;
            jumpthru.entity = entity;
        }
    }

    // ------------------------------------------
    // Loading API
    // ------------------------------------------

    public void load(World world, String filename) {
        var json = new Json();
        if (filename.endsWith(".ldtk")) {
            // load ldtk file
            json.setIgnoreUnknownFields(true);
            var ldtk = json.fromJson(Ldtk.class, Gdx.files.internal(filename));

            // load the room descriptors from the ldtk file data
            var descs = parseLdtkMap(ldtk);
            for (var desc : descs) {
                // load a room entity based on the level descriptor
                var room = createRoomEntity(desc, assets, world);
                rooms.add(room);
            }
        } else {
            Gdx.app.error("WorldMap", "Unable to load, unrecognized file type '" + filename + "'");
        }
    }

    // ------------------------------------------
    // Loading implementation details
    // ------------------------------------------

    private Entity createRoomEntity(RoomDesc desc, Assets assets, World world) {
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

            // optional background image
            if (desc.backgroundImage != null) {
                var image = entity.add(new Image(desc.backgroundImage), Image.class);
                image.depth = -1000;
            }

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

    private List<RoomDesc> parseLdtkMap(Ldtk ldtk) {
        var descs = new ArrayList<RoomDesc>();

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
            var desc = new RoomDesc();
            {
                var level = ldtk.levels.get(levelNum);

                // load background image (optionally)
                Texture backgroundImage = null;
                var backgroundImageRelPath = level.bgRelPath;
                if (backgroundImageRelPath != null) {
                    backgroundImage = new Texture("levels/" + backgroundImageRelPath);
                    backgrounds.add(backgroundImage);
                }

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
                desc.entityGridSize = entityLayer.__gridSize;
                desc.colliderSize = collisionLayer.__gridSize;
                desc.colliderCols = collisionLayer.__cWid;
                desc.colliderRows = collisionLayer.__cHei;
                desc.backgroundImage = backgroundImage;
                desc.colliderCells = new int[desc.colliderCols * desc.colliderRows];
                desc.tilemapCellTextures = new Point[desc.cols * desc.rows];
                desc.foregroundTilemapCellTextures = null;

                // setup entities
                for (var entity : entityLayer.entityInstances) {
                    // creature spawners
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
                    // jumpthru platforms
                    else if ("Jumpthru".equals(entity.__identifier)) {
                        var x = desc.position.x + entity.px[0];
                        var flipY = level.pxHei - entity.px[1];
                        var y = desc.position.y + flipY;
                        var w = entity.width;
                        var h = entity.height;
                        jumpthrus.add(new Jumpthru(RectI.at(x, y, w, h)));
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
