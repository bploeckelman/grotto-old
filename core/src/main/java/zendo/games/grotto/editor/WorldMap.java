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
import java.util.Optional;
import java.util.stream.Collectors;

public class WorldMap implements Disposable {

    // ------------------------------------------
    // Helper data structures
    // ------------------------------------------

    static class RoomInfo {
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

    static class Tileset {
        public int uid;
        public int gridSize;
        public int rows;
        public int cols;
        public String name;
    }

    static class Spawner {
        public String type;
        public Point pos;
        Spawner(String type, int x, int y) {
            this.type = type;
            this.pos = Point.at(x, y);
        }
    }

    public static class Barrier {
        public RectI bounds;
        public Entity entity;
        Barrier(RectI bounds) {
            this.bounds = bounds;
            this.entity = null;
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

    public static class SolidInfo {
        public final String id;
        public final RectI bounds;
        public final float speed;
        SolidInfo(String id, RectI bounds, float speed) {
            this.id = id;
            this.bounds = bounds;
            this.speed = speed;
        }
    }

    public static class WaypointInfo {
        public final String solidId;
        public final int sequence;
        public final Point point;
        WaypointInfo(String solidId, int sequence, Point point) {
            this.solidId = solidId;
            this.sequence = sequence;
            this.point = point;
        }
    }

    // ------------------------------------------
    // Member data
    // ------------------------------------------

    private final List<Entity> rooms;
    private final List<Entity> solids;
    private final List<Spawner> spawners;
    private final List<Barrier> barriers;
    private final List<Jumpthru> jumpthrus;

    public final List<SolidInfo> solidInfos;
    public final List<WaypointInfo> waypointInfos;

    private final Assets assets;
    private final IntMap<Tileset> tilesets;
    private final List<Texture> backgrounds;

    // ------------------------------------------
    // Constructor and interface implementations
    // ------------------------------------------

    public WorldMap(World world, Assets assets, String filename) {
        rooms = new ArrayList<>();
        solids = new ArrayList<>();
        spawners = new ArrayList<>();
        barriers = new ArrayList<>();
        jumpthrus = new ArrayList<>();
        tilesets = new IntMap<>();
        backgrounds = new ArrayList<>();
        solidInfos = new ArrayList<>();
        waypointInfos = new ArrayList<>();

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
        for (var solid : solids) {
            solid.destroy();
        }
        solids.clear();
        for (var room : rooms) {
            room.destroy();
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

    public List<Barrier> barriers() {
        return barriers;
    }

    private List<WaypointInfo> getWaypointInfosForSolid(String solidId) {
        return waypointInfos.stream()
                .filter(waypoint -> waypoint.solidId.equalsIgnoreCase(solidId))
                .collect(Collectors.toList());
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

    public void spawnBarriers(World world) {
        // TODO: factory?
        for (var barrier : barriers) {
            var entity = world.addEntity();
            var collider = entity.add(Collider.makeRect(barrier.bounds), Collider.class);
            collider.mask = Collider.Mask.solid;
            barrier.entity = entity;
        }
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

    public void spawnSolids(World world) {
        // TODO: factory?
        for (var info : solidInfos) {
            var entity = world.addEntity();
            {
                entity.position.set(info.bounds.x, info.bounds.y);

                entity.add(new Animator("platform", "idle"), Animator.class);

                var collider = entity.add(Collider.makeRect(RectI.zero()), Collider.class);
                collider.mask = Collider.Mask.solid;

                var waypoints = getWaypointInfosForSolid(info.id);
                var solid = entity.add(new Solid(info, waypoints), Solid.class);
                collider.rect().setSize(solid.bounds.w, solid.bounds.h);
                solid.collider = collider;
            }
            solids.add(entity);
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

            // load room from the ldtk file data
            var roomInfos = parseLdtkMap(ldtk);
            for (var roomInfo : roomInfos) {
                rooms.add(createRoomEntity(roomInfo, assets, world));
            }
        } else {
            Gdx.app.error("WorldMap", "Unable to load, unrecognized file type '" + filename + "'");
        }
    }

    // ------------------------------------------
    // Loading implementation details
    // ------------------------------------------

    private Entity createRoomEntity(RoomInfo info, Assets assets, World world) {
        // setup tileset
        var tileset = tilesets.get(info.tilesetUid);
        var tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
        var regions = tilesetTexture.split(tileset.gridSize, tileset.gridSize);

        // initialize a map between texture region array indices and the texture region they point to in the tileset
        var pointRegionMap = new HashMap<Point, TextureRegion>();
        for (int x = 0; x < info.cols; x++) {
            for (int y = 0; y < info.rows; y++) {
                var point = info.tilemapCellTextures[x + y * info.cols];
                if (point != null) {
                    pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                }
            }
        }

        // create the room entity and initialize it
        var entity = world.addEntity();
        {
            // create components
            var tilemap = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
            var collider = entity.add(Collider.makeGrid(info.colliderSize, info.colliderCols, info.colliderRows), Collider.class);
            collider.mask = Collider.Mask.solid;

            // optional background image
            if (info.backgroundImage != null) {
                var image = entity.add(new Image(info.backgroundImage), Image.class);
                image.depth = -1000;
            }

            // initialize tilemap component contents
            for (int x = 0; x < info.cols; x++) {
                for (int y = 0; y < info.rows; y++) {
                    var point = info.tilemapCellTextures[x + y * info.cols];
                    tilemap.setCell(x, y, pointRegionMap.get(point));
                }
            }

            // initialize collider component contents
            for (int x = 0; x < info.colliderCols; x++) {
                for (int y = 0; y < info.colliderRows; y++) {
                    var value = (info.colliderCells[x + y * info.colliderCols] == 1);
                    collider.setCell(x, y, value);
                }
            }

            // add a foreground tile layer if one exists in the info
            if (info.foregroundTilemapCellTextures != null && info.foregroundTilesetUid != -1) {
                // setup tileset
                tileset = tilesets.get(info.foregroundTilesetUid);
                tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
                regions = tilesetTexture.split(tileset.gridSize, tileset.gridSize);

                // initialize a map between texture region array indices and the texture region they point to in the tileset
                pointRegionMap.clear();
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        var point = info.foregroundTilemapCellTextures[x + y * info.cols];
                        if (point != null) {
                            pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                        }
                    }
                }

                // create foreground tilemap component and set regions
                var foreground = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
                foreground.depth = 10;
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        var point = info.foregroundTilemapCellTextures[x + y * info.cols];
                        foreground.setCell(x, y, pointRegionMap.get(point));
                    }
                }
            }

            // setup a collider for quick lookup of the room's bounds
            collider = entity.add(Collider.makeRect(RectI.at(
                    info.position.x, info.position.y,
                    info.tileSize * info.cols,
                    info.tileSize * info.rows
            )), Collider.class);
            collider.mask = Collider.Mask.room_bounds;
            collider.origin.set(-info.position.x, -info.position.y);
            collider.depth = 100;
        }
        entity.position.set(info.position);

        return entity;
    }

    private List<RoomInfo> parseLdtkMap(Ldtk ldtk) {
        var roomInfos = new ArrayList<RoomInfo>();

        // instantiate tilesets
        for (var def : ldtk.defs.tilesets) {
            var nameBeginIndex = 0;
            var nameEndIndex = def.relPath.lastIndexOf(".png");
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
            var info = new RoomInfo();
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

                info.position = Point.at(level.worldX, -(level.worldY + level.pxHei));
                info.tileSize = tileset.gridSize;
                info.cols = tileLayer.__cWid;
                info.rows = tileLayer.__cHei;
                info.tilesetUid = tileset.uid;
                info.foregroundTilesetUid = -1;
                info.entityGridSize = entityLayer.__gridSize;
                info.colliderSize = collisionLayer.__gridSize;
                info.colliderCols = collisionLayer.__cWid;
                info.colliderRows = collisionLayer.__cHei;
                info.backgroundImage = backgroundImage;
                info.colliderCells = new int[info.colliderCols * info.colliderRows];
                info.tilemapCellTextures = new Point[info.cols * info.rows];
                info.foregroundTilemapCellTextures = null;

                // setup entities
                for (var entity : entityLayer.entityInstances) {
                    var x = info.position.x + entity.px[0];
                    var flipY = level.pxHei - entity.px[1];
                    var y = info.position.y + flipY;
                    var w = entity.width;
                    var h = entity.height;

                    // creature spawners
                    if ("Spawner".equals(entity.__identifier)) {
                        for (var field : entity.fieldInstances) {
                            if ("Type".equals(field.__identifier)) {
                                var type = field.__value;
                                spawners.add(new Spawner(type, x, y));
                            }
                        }
                    }
                    // barriers
                    else if ("Barrier".equals(entity.__identifier)) {
                        barriers.add(new Barrier(RectI.at(x, y, w, h)));
                    }
                    // jumpthru platforms
                    else if ("Jumpthru".equals(entity.__identifier)) {
                        jumpthrus.add(new Jumpthru(RectI.at(x, y, w, h)));
                    }
                    // solids
                    else if ("Solid".equals(entity.__identifier)) {
                        var idField = findEntityField(entity, "string", "id");
                        var speedField = findEntityField(entity, "float", "speed");
                        if (idField.isPresent() && speedField.isPresent()) {
                            var id = idField.get().__value;
                            var speed = Float.parseFloat(speedField.get().__value);
                            solidInfos.add(new SolidInfo(id, RectI.at(x, y, w, h), speed));
                        } else {
                            Gdx.app.error("WorldMap", "found solid but unable to read id or speed fields");
                        }
                    }
                    // waypoints
                    else if ("Waypoint".equals(entity.__identifier)) {
                        var idField = findEntityField(entity, "string", "solid_id");
                        var seqField = findEntityField(entity, "int", "sequence");
                        if (idField.isPresent() && seqField.isPresent()) {
                            var id = idField.get().__value;
                            var seq = Integer.parseInt(seqField.get().__value, 10);
                            waypointInfos.add(new WaypointInfo(id, seq, Point.at(x, y)));
                        } else {
                            Gdx.app.error("WorldMap", "found waypoint but unable to read id or sequence fields");
                        }
                    }
                }

                // TODO: validate associations between solids and waypoints

                // setup collision layer
                for (int x = 0; x < info.colliderCols; x++) {
                    for (int y = 0; y < info.colliderRows; y++) {
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        int flipY = (info.colliderRows - 1) - y;
                        var collisionValue = collisionLayer.intGridCsv[x + flipY * info.colliderCols];
                        info.colliderCells[x + y * info.colliderCols] = collisionValue;
                    }
                }

                // setup tilemap layer
                for (var gridTileEntry : tileLayer.gridTiles) {
                    var px = gridTileEntry.px;
                    var tile = Point.at(px[0] / tileset.gridSize, px[1] / tileset.gridSize);
                    // note: ldtk files are stored with origin = top left so we have to flip y
                    tile.y = (info.rows - 1) - tile.y;

                    var src = gridTileEntry.src;
                    var textureIndex = Point.at(src[0] / tileset.gridSize, src[1] / tileset.gridSize);

                    info.tilemapCellTextures[tile.x + tile.y * info.cols] = textureIndex;
                }

                // setup foreground tilemap layer
                if (foregroundLayer != null) {
                    var foregroundTilesetUid = foregroundLayer.__tilesetDefUid;
                    info.foregroundTilesetUid = foregroundTilesetUid;

                    var foregroundTileset = tilesets.get(foregroundTilesetUid, null);
                    if (foregroundTileset == null) {
                        throw new GdxRuntimeException("Failed to load ldtk file, missing tileset with uid " + foregroundTilesetUid + " in foreground layer");
                    }

                    // setup foreground layer
                    info.foregroundTilemapCellTextures = new Point[info.cols * info.rows];
                    for (var gridTileEntry : foregroundLayer.gridTiles) {
                        var px = gridTileEntry.px;
                        var tile = Point.at(px[0] / foregroundTileset.gridSize, px[1] / foregroundTileset.gridSize);
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        tile.y = (info.rows - 1) - tile.y;

                        var src = gridTileEntry.src;
                        var textureIndex = Point.at(src[0] / foregroundTileset.gridSize, src[1] / foregroundTileset.gridSize);

                        info.foregroundTilemapCellTextures[tile.x + tile.y * info.cols] = textureIndex;
                    }
                }
            }
            roomInfos.add(info);
        }

        return roomInfos;
    }

    private static Optional<Ldtk.FieldInstance> findEntityField(Ldtk.EntityInstance entity, String type, String identifier) {
        return entity.fieldInstances.stream()
                .filter(field -> {
                    var typeMatches       = type.equalsIgnoreCase(field.__type);
                    var identifierMatches = identifier.equalsIgnoreCase(field.__identifier);
                    return typeMatches && identifierMatches;
                })
                .findFirst();
    }

}
