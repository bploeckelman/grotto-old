package zendo.games.grotto.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.MathUtils;
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
import zendo.games.grotto.utils.Calc;
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
        public int backgroundTilesetUid;
        public int entityGridSize;
        public int colliderSize;
        public int colliderRows;
        public int colliderCols;
        public int[] colliderCells;
        public BackgroundInfo backgroundInfo;
        public TextureRegion[] tilemapCellTextureRegions;
        public TextureRegion[] nearTilemapCellTextureRegions;
        public TextureRegion[] nearestTilemapCellTextureRegions;
        public TextureRegion[] farTilemapCellTextureRegions;
        public TextureRegion[] farthestTilemapCellTextureRegions;
        public Point[] tilemapCellTextures;
        public Point[] foregroundTilemapCellTextures;
        public Point[] backgroundTilemapCellTextures;
    }

    static class BackgroundInfo {
        public Texture texture;
        public RectI bounds = RectI.zero();
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

    public static class Ladder {
        public RectI bounds;
        public Entity entity;
        Ladder(RectI bounds) {
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
    private final List<Solid> solids;
    private final List<Spawner> spawners;
    private final List<Barrier> barriers;
    private final List<Jumpthru> jumpthrus;
    private final List<Ladder> ladders;

    private final List<SolidInfo> solidInfos;
    private final List<WaypointInfo> waypointInfos;

    private final Assets assets;
    private final IntMap<Tileset> tilesets;
    private final List<BackgroundInfo> backgrounds;

    // ------------------------------------------
    // Constructor and interface implementations
    // ------------------------------------------

    public WorldMap(World world, Assets assets, String filename) {
        rooms = new ArrayList<>();
        solids = new ArrayList<>();
        spawners = new ArrayList<>();
        barriers = new ArrayList<>();
        jumpthrus = new ArrayList<>();
        ladders = new ArrayList<>();
        tilesets = new IntMap<>();
        backgrounds = new ArrayList<>();
        solidInfos = new ArrayList<>();
        waypointInfos = new ArrayList<>();

        this.assets = assets;
        load(world, filename);
    }

    @Override
    public void dispose() {
        backgrounds.forEach(info -> {
            if (info.texture != null) {
                info.texture.dispose();
            }
        });
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
        spawners.clear();

        solids.forEach(solid -> {
            if (solid.entity() != null) {
                solid.entity().destroy();
            }
        });
        solids.clear();

        jumpthrus.forEach(jumpthru -> {
            if (jumpthru.entity != null) {
                jumpthru.entity.destroy();
            }
        });
        jumpthrus.clear();

        ladders.forEach(ladder -> {
            if (ladder.entity != null) {
                ladder.entity.destroy();
            }
        });
        ladders.clear();

        barriers.forEach(barrier -> {
            if (barrier.entity != null) {
                barrier.entity.destroy();
            }
        });
        barriers.clear();

        rooms.forEach(room -> {
            if (room != null) {
                room.destroy();
            }
        });
        rooms.clear();

        solidInfos.clear();
        waypointInfos.clear();

        backgrounds.forEach(info -> {
            if (info.texture != null) {
                info.texture.dispose();
            }
        });
        backgrounds.clear();
    }

    // ------------------------------------------
    // Getters and setters
    // ------------------------------------------

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

    public void spawnLadders(World world) {
        // TODO: factory?
        for (var ladder : ladders) {
            var entity = world.addEntity();
            var collider = entity.add(Collider.makeRect(ladder.bounds), Collider.class);
            collider.mask = Collider.Mask.climbable;
            ladder.entity = entity;
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

                solids.add(solid);
            }
        }
    }

    // ------------------------------------------
    // Loading API
    // ------------------------------------------

    public void load(World world, String filename) {
        var json = new Json();
        if (filename.endsWith(".ldtk")) {
            Gdx.app.log("WorldMap", "Loading LDTK map: " + filename);

            // load ldtk file
            json.setIgnoreUnknownFields(true);
            var ldtk = json.fromJson(Ldtk.class, Gdx.files.internal(filename));

            // load room from the ldtk file data
            var roomInfos = parseLdtkMap(ldtk);
            for (var roomInfo : roomInfos) {
                rooms.add(createRoomEntity(roomInfo, assets, world));
            }
        } else if (filename.endsWith(".tmx")) {
            Gdx.app.log("WorldMap", "Loading Tiled map: " + filename);

// TODO: add support for world definitions that list many levels, their relative positions, and their corresponding tmx files
//        https://doc.mapeditor.org/en/stable/manual/worlds/

            // load the map
            var params = new TmxMapLoader.Parameters();
            params.textureMinFilter = Texture.TextureFilter.Nearest;
            params.textureMagFilter = Texture.TextureFilter.Nearest;
            var loader = new TmxMapLoader();
            var map = loader.load(filename, params);

            // load a room from the map
            var roomInfos = parseTmxMap(filename, map);
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
        var regions = (tilesetTexture == null) ? null : tilesetTexture.split(tileset.gridSize, tileset.gridSize);

        // initialize a map between texture region array indices and the texture region they point to in the tileset
        var pointRegionMap = new HashMap<Point, TextureRegion>();
        if (regions != null) {
            for (int x = 0; x < info.cols; x++) {
                for (int y = 0; y < info.rows; y++) {
                    var point = info.tilemapCellTextures[x + y * info.cols];
                    if (point != null) {
                        pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                    }
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
            if (info.backgroundInfo.texture != null) {
                var image = entity.add(new Image(info.backgroundInfo.texture), Image.class);
                image.xOffset = info.backgroundInfo.bounds.x;
                image.yOffset = info.backgroundInfo.bounds.y;
                image.width = info.backgroundInfo.bounds.w;
                image.height = info.backgroundInfo.bounds.h;
                image.depth = -1000;
            }

            // initialize tilemap component contents
            for (int x = 0; x < info.cols; x++) {
                for (int y = 0; y < info.rows; y++) {
                    if (info.tilemapCellTextures != null) {
                        var point = info.tilemapCellTextures[x + y * info.cols];
                        tilemap.setCell(x, y, pointRegionMap.get(point));
                    }
                    else if (info.tilemapCellTextureRegions != null) {
                        var region = info.tilemapCellTextureRegions[x + y * info.cols];
                        tilemap.setCell(x, y, region);
                    }
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
            if ((info.foregroundTilemapCellTextures != null && info.foregroundTilesetUid != -1)
             || (info.nearTilemapCellTextureRegions != null)) {
                // setup tileset
                tileset = tilesets.get(info.foregroundTilesetUid);
                tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
                regions = (info.nearTilemapCellTextureRegions != null) ? null
                        : tilesetTexture.split(tileset.gridSize, tileset.gridSize);

                // initialize a map between texture region array indices and the texture region they point to in the tileset
                if (regions != null) {
                    pointRegionMap.clear();
                    for (int x = 0; x < info.cols; x++) {
                        for (int y = 0; y < info.rows; y++) {
                            var point = info.foregroundTilemapCellTextures[x + y * info.cols];
                            if (point != null) {
                                pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                            }
                        }
                    }
                }

                // create foreground tilemap component and set regions
                var foreground = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
                foreground.depth = 10;
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        if (regions != null) {
                            var point = info.foregroundTilemapCellTextures[x + y * info.cols];
                            foreground.setCell(x, y, pointRegionMap.get(point));
                        } else if (info.nearTilemapCellTextureRegions != null) {
                            var region = info.nearTilemapCellTextureRegions[x + y * info.cols];
                            foreground.setCell(x, y, region);
                        } else if (info.nearestTilemapCellTextureRegions != null) {
                            var region = info.nearestTilemapCellTextureRegions[x + y * info.cols];
                            foreground.setCell(x, y, region);
                        }
                    }
                }
            }

            // add nearest tilemap layer if one exists in info
            if (info.nearestTilemapCellTextureRegions != null) {
                var nearest = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
                nearest.depth = 20;
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        var region = info.nearestTilemapCellTextureRegions[x + y * info.cols];
                        nearest.setCell(x, y, region);
                    }
                }
            }

            // add a background tile layer if one exists in the info
            if ((info.backgroundTilemapCellTextures != null && info.backgroundTilesetUid != -1)
             || (info.farTilemapCellTextureRegions != null)) {
                // setup tileset
                tileset = tilesets.get(info.backgroundTilesetUid);
                tilesetTexture = assets.tilesetAtlas.findRegion(tileset.name);
                regions = (info.farTilemapCellTextureRegions != null) ? null
                        : tilesetTexture.split(tileset.gridSize, tileset.gridSize);

                // initialize a map between texture region array indices and the texture region they point to in the tileset
                if (regions != null) {
                                 pointRegionMap.clear();
                                 for (int x = 0; x < info.cols; x++) {
                                 for (int y = 0; y < info.rows; y++) {
                                 var point = info.backgroundTilemapCellTextures[x + y * info.cols];
                                 if (point != null) {
                                 pointRegionMap.putIfAbsent(point, regions[point.y][point.x]);
                                 }
                                 }
                                 }
                                 }

                // TODO: need to make tilemap layer depths uniform
                // create background tilemap component and set regions
                var background = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
                background.depth = -10;
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        if (regions != null) {
                            var point = info.backgroundTilemapCellTextures[x + y * info.cols];
                            background.setCell(x, y, pointRegionMap.get(point));
                        } else if (info.farTilemapCellTextureRegions != null) {
                            var region = info.farTilemapCellTextureRegions[x + y * info.cols];
                            background.setCell(x, y, region);
                        }
                    }
                }
            }

            // add farthest tilemap layer if one exists in info
            if (info.farthestTilemapCellTextureRegions != null) {
                var farthest = entity.add(new Tilemap(info.tileSize, info.cols, info.rows), Tilemap.class);
                farthest.depth = -20;
                for (int x = 0; x < info.cols; x++) {
                    for (int y = 0; y < info.rows; y++) {
                        var region = info.farthestTilemapCellTextureRegions[x + y * info.cols];
                        farthest.setCell(x, y, region);
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
                var backgroundInfo = new BackgroundInfo();
                var backgroundImageRelPath = level.bgRelPath;
                if (backgroundImageRelPath != null) {
                    backgroundInfo.texture = new Texture("levels/" + backgroundImageRelPath);
                    var w = (int) Calc.floor(backgroundInfo.texture.getWidth()  * (float) level.__bgPos.scale[0]);
                    var h = (int) Calc.floor(backgroundInfo.texture.getHeight() * (float) level.__bgPos.scale[1]);
                    // x and y are offsets from entity position, which should be bottom left corner of level, so 0, 0 fits in most cases
                    backgroundInfo.bounds.set(0, 0, w, h);
                    backgrounds.add(backgroundInfo);
                }

                // find required layers
                Ldtk.LayerInstance tileLayer = null;
                Ldtk.LayerInstance collisionLayer = null;
                Ldtk.LayerInstance entityLayer = null;
                Ldtk.LayerInstance foregroundLayer = null;
                Ldtk.LayerInstance backgroundLayer = null;
                for (var layer : level.layerInstances) {
                    if ("Tiles".equals(layer.__type)) {
                        if ("Main".equals(layer.__identifier)) {
                            tileLayer = layer;
                        } else if ("Foreground".equals(layer.__identifier)) {
                            foregroundLayer = layer;
                        } else if ("Background".equals(layer.__identifier)) {
                            backgroundLayer = layer;
                        }
                } else if ("IntGrid".equals(layer.__type) && "Collision".equals(layer.__identifier)) {
                        collisionLayer = layer;
                    } else if ("Entities".equals(layer.__type) && "Entities".equals(layer.__identifier)) {
                        entityLayer = layer;
                    }
                }
                if (tileLayer == null) {
                    throw new GdxRuntimeException("Failed to load ldtk file, no 'Main' tile layer found");
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
                info.backgroundTilesetUid = -1;
                info.entityGridSize = entityLayer.__gridSize;
                info.colliderSize = collisionLayer.__gridSize;
                info.colliderCols = collisionLayer.__cWid;
                info.colliderRows = collisionLayer.__cHei;
                info.backgroundInfo = backgroundInfo;
                info.colliderCells = new int[info.colliderCols * info.colliderRows];
                info.tilemapCellTextures = new Point[info.cols * info.rows];
                info.foregroundTilemapCellTextures = null;
                info.backgroundTilemapCellTextures = null;

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
                    // ladders
                    else if ("Ladder".equals(entity.__identifier)) {
                        ladders.add(new Ladder(RectI.at(x, y, w, h)));
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

                // setup background tilemap layer
                if (backgroundLayer != null) {
                    var backgroundTilesetUid = backgroundLayer.__tilesetDefUid;
                    info.backgroundTilesetUid = backgroundTilesetUid;

                    var backgroundTileset = tilesets.get(backgroundTilesetUid, null);
                    if (backgroundTileset == null) {
                        throw new GdxRuntimeException("Failed to load ldtk file, missing tileset with uid " + backgroundTilesetUid + " in background layer");
                    }

                    // setup background layer
                    info.backgroundTilemapCellTextures = new Point[info.cols * info.rows];
                    for (var gridTileEntry : backgroundLayer.gridTiles) {
                        var px = gridTileEntry.px;
                        var tile = Point.at(px[0] / backgroundTileset.gridSize, px[1] / backgroundTileset.gridSize);
                        // note: ldtk files are stored with origin = top left so we have to flip y
                        tile.y = (info.rows - 1) - tile.y;

                        var src = gridTileEntry.src;
                        var textureIndex = Point.at(src[0] / backgroundTileset.gridSize, src[1] / backgroundTileset.gridSize);

                        info.backgroundTilemapCellTextures[tile.x + tile.y * info.cols] = textureIndex;
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

    private List<RoomInfo> parseTmxMap(String filename, TiledMap map) {
        var roomInfos = new ArrayList<RoomInfo>();

        // instantiate tilesets
        for (var mapTileset : map.getTileSets()) {
            var props = mapTileset.getProperties();
            var tileWidth   = (int) props.get("tilewidth", 0, Integer.class);
            var tileHeight  = (int) props.get("tileheight", 0, Integer.class);
            var imageWidth  = (int) props.get("imagewidth", 0, Integer.class);
            var imageHeight = (int) props.get("imageheight", 0, Integer.class);
            var spacing     = (int) props.get("spacing", 0, Integer.class);
            var margin      = (int) props.get("margin", 0, Integer.class);

            if (tileWidth != tileHeight) {
                throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': tileset width and height must be equal");
            }
            if (spacing != 0) {
                throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': tileset spacing not yet supported");
            }
            if (margin != 0) {
                throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': tileset margin not yet supported");
            }

            var tileset = new Tileset();
            tileset.uid = mapTileset.hashCode();
            tileset.rows = imageHeight / tileHeight;
            tileset.cols = imageWidth / tileWidth;
            tileset.gridSize = tileWidth;
            tileset.name = mapTileset.getName();
            tilesets.put(tileset.uid, tileset);
        }
// TODO: add support for world definitions that list many levels, their relative positions, and their corresponding tmx files
//        https://doc.mapeditor.org/en/stable/manual/worlds/
        var numLevels = 1;
        for (int levelNum = 0; levelNum < numLevels; levelNum++) {
            var info = new RoomInfo();
            {
                // TODO: load background image (optional)

                // find required layers
                TiledMapTileLayer mainLayer = null;
                TiledMapTileLayer collisionLayer = null;
                MapLayer entityLayer = null;

                // optional layers
                TiledMapTileLayer near = null;
                TiledMapTileLayer nearest = null;
                TiledMapTileLayer far = null;
                TiledMapTileLayer farthest = null;

                // find groups for each plane
                MapGroupLayer bgGroup = null;
                MapGroupLayer fgGroup = null;
                MapGroupLayer midGroup = null;
                var groups = map.getLayers().getByType(MapGroupLayer.class);
                for (var group : groups) {
                    switch (group.getName()) {
                        case "background" -> bgGroup = group;
                        case "foreground" -> fgGroup = group;
                        case "middle" -> midGroup = group;
                    }
                }
                // initialize foreground layers
                if (fgGroup != null) {
                    for (var layer : fgGroup.getLayers()) {
                        switch (layer.getName()) {
                            case "near" -> near = (TiledMapTileLayer) layer;
                            case "nearest" -> nearest = (TiledMapTileLayer) layer;
                        }
                    }
                }

                // initialize middle layers
                if (midGroup != null) {
                    for (var layer : midGroup.getLayers()) {
                        switch (layer.getName()) {
                            case "main" -> mainLayer = (TiledMapTileLayer) layer;
                            case "collision" -> collisionLayer = (TiledMapTileLayer) layer;
                            case "entity" -> entityLayer = layer;
                        }
                    }
                }

                // initialize background layers
                if (bgGroup != null) {
                    for (var layer : bgGroup.getLayers()) {
                        switch (layer.getName()) {
                            case "far" -> far = (TiledMapTileLayer) layer;
                            case "farthest" -> farthest = (TiledMapTileLayer) layer;
                        }
                    }
                }

                // validate that the required layers were found
                if (mainLayer == null) {
                    throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': missing layer 'main'");
                }
                if (collisionLayer == null) {
                    throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': missing layer 'collision'");
                }
                if (entityLayer == null) {
                    throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': missing layer 'entity'");
                }

                // lookup tileset
                // TODO: support multiple tilesets per map
                var tileset = tilesets.values().toArray().first();
                if (tileset == null) {
                    throw new GdxRuntimeException("Failed to load Tiled map '" + filename + "': no tileset found for map");
                }

                // populate RoomInfo
                info.position = Point.zero();
                info.tilesetUid = tileset.uid;
                info.tileSize = tileset.gridSize;
                info.cols = mainLayer.getWidth();
                info.rows = mainLayer.getHeight();
                info.foregroundTilesetUid = tileset.uid; // all layers in a map share the same tileset (for now)
                info.backgroundTilesetUid = tileset.uid; // all layers in a map share the same tileset (for now)
                info.entityGridSize = mainLayer.getWidth(); // entity layer doesn't have it's own grid
                info.colliderSize = collisionLayer.getTileWidth();
                info.colliderCols = collisionLayer.getWidth();
                info.colliderRows = collisionLayer.getHeight();
                info.backgroundInfo = new BackgroundInfo();
                info.colliderCells = new int[info.colliderCols * info.colliderRows];
                info.tilemapCellTextureRegions = new TextureRegion[info.cols * info.rows];
                info.nearTilemapCellTextureRegions = null;
                info.nearestTilemapCellTextureRegions = null;
                info.farTilemapCellTextureRegions = null;
                info.farthestTilemapCellTextureRegions = null;
                info.tilemapCellTextures = null;
                info.foregroundTilemapCellTextures = null;
                info.backgroundTilemapCellTextures = null;

                // initialize entities
                for (var object : entityLayer.getObjects()) {
                    var props = object.getProperties();
                    var id = props.get("id", -1, Integer.class);
                    var type = props.get("type", "unknown", String.class);
                    var x = MathUtils.round(props.get("x", 0f, Float.class)) + info.position.x;
                    var y = MathUtils.round(props.get("y", 0f, Float.class)) + info.position.y;
                    var w = MathUtils.round(props.get("width", 0f, Float.class));
                    var h = MathUtils.round(props.get("height", 0f, Float.class));

                    if ("player".equalsIgnoreCase(type)) {
                        spawners.add(new Spawner(type, x, y));
                    }
                }

                // initialize collision layer
                for (int x = 0; x < info.colliderCols; x++) {
                    for (int y = 0; y < info.colliderRows; y++) {
                        var cell = collisionLayer.getCell(x, y);
                        var value = (cell == null || cell.getTile() == null) ? 0 : 1;
                        info.colliderCells[x + y * info.colliderCols] = value;
                    }
                }

                // initialize main tile layer
                for (int x = 0; x < mainLayer.getWidth(); x++) {
                    for (int y = 0; y < mainLayer.getHeight(); y++) {
                        var cell = mainLayer.getCell(x, y);
                        var region = (cell == null || cell.getTile() == null) ? null : cell.getTile().getTextureRegion();
                        info.tilemapCellTextureRegions[x + y * mainLayer.getWidth()] = region;
                    }
                }

                // initialize foreground layer(s)
                if (near != null || nearest != null) {
                    if (near != null) {
                        info.nearTilemapCellTextureRegions = new TextureRegion[near.getWidth() * near.getHeight()];
                        for (int x = 0; x < near.getWidth(); x++) {
                            for (int y = 0; y < near.getHeight(); y++) {
                                var cell = near.getCell(x, y);
                                var region = (cell == null || cell.getTile() == null) ? null : cell.getTile().getTextureRegion();
                                info.nearTilemapCellTextureRegions[x + y * near.getWidth()] = region;
                            }
                        }
                    }
                    if (nearest != null) {
                        info.nearestTilemapCellTextureRegions = new TextureRegion[nearest.getWidth() * nearest.getHeight()];
                        for (int x = 0; x < nearest.getWidth(); x++) {
                            for (int y = 0; y < nearest.getHeight(); y++) {
                                var cell = nearest.getCell(x, y);
                                var region = (cell == null || cell.getTile() == null) ? null : cell.getTile().getTextureRegion();
                                info.nearestTilemapCellTextureRegions[x + y * nearest.getWidth()] = region;
                            }
                        }
                    }
                }

                // initialize background layer(s)
                if (far != null || farthest != null) {
                    if (far != null) {
                        info.farTilemapCellTextureRegions = new TextureRegion[far.getWidth() * far.getHeight()];
                        for (int x = 0; x < far.getWidth(); x++) {
                            for (int y = 0; y < far.getHeight(); y++) {
                                var cell = far.getCell(x, y);
                                var region = (cell == null || cell.getTile() == null) ? null : cell.getTile().getTextureRegion();
                                info.farTilemapCellTextureRegions[x + y * far.getWidth()] = region;
                            }
                        }
                    }
                    if (farthest != null) {
                        info.farthestTilemapCellTextureRegions = new TextureRegion[farthest.getWidth() * farthest.getHeight()];
                        for (int x = 0; x < farthest.getWidth(); x++) {
                            for (int y = 0; y < farthest.getHeight(); y++) {
                                var cell = farthest.getCell(x, y);
                                var region = (cell == null || cell.getTile() == null) ? null : cell.getTile().getTextureRegion();
                                info.farthestTilemapCellTextureRegions[x + y * farthest.getWidth()] = region;
                            }
                        }
                    }
                }

                // TODO: initialize other tile layers
            }
            roomInfos.add(info);
        }

        return roomInfos;
    }

}
