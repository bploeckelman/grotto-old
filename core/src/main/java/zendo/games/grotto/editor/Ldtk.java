package zendo.games.grotto.editor;

import java.util.List;

// https://ldtk.io/docs/game-dev/json-overview/
public class Ldtk {

    public String name;
    public String jsonVersion;
    public double defaultPivotX;
    public double defaultPivotY;
    public String bgColor;
    public int nextUid;

    public Defs defs;
    public List<Level> levels;

    // --------------------------------------------------------------
    // NOTE: don't need defs section since components are included elsewhere for convenience as double underscore prefixed fields

    public static class Defs {
        public List<Layer> layers;
        public List<Entity> entities;
        public List<Tileset> tilesets;
        public List<LdtkEnum> enums;
        public List<ExternalEnum> externalEnums;
    }

    public static class Layer {}

    public static class Entity {}

    public static class Tileset {}

    public static class LdtkEnum {}

    public static class ExternalEnum {}

    // --------------------------------------------------------------

    public static class Level {
        public String identifier;
        public int uid;
        public int pxWid;
        public int pxHei;
        public List<LayerInstance> layerInstances;
    }

    public static class LayerInstance {
        public String __identifier;
        public String __type;
        public int __cWid; // layer width (grid based)
        public int __cHei; // layer height (grid based)
        public int __gridSize;
        public int levelId;
        public int layerDefUid;
        public int pxOffsetX; // optional offset that could happen when resizing levels
        public int pxOffsetY; // optional offset that could happen when resizing levels
        public long seed;
        public List<IntGridEntry> intGrid; // only populated if layer is an IntGrid
        public List<AutoTileEntry> autoTiles; // only populated if layer is an Auto-layer
        public List<GridTileEntry> gridTiles; // only populated if layer is a TileLayer
        public List<EntityInstance> entityInstances; // only populated if layer is an EntityLayer
    }

    public static class IntGridEntry {
        public int coordId;
        public int v;
    }

    public static class AutoTileEntry {
        public int[] px;     // pixel coords of tile in the layer: [x,y] (apply optional layer offsets if appropriate)
        public int[] src;    // pixel coordinates of the tile in the tileset: [x,y]
        public int f;        // 'flip bits'; 0=none, 1=horiz, 2=vert, 3=both
        public int t;        // ??? tile index maybe? undocumented?
        public int[] d;      // ??? no idea... undocumented?
    }

    public static class GridTileEntry {
        public int[] px;     // pixel coords of tile in the layer: [x,y] (apply optional layer offsets if appropriate)
        public int[] src;    // pixel coordinates of the tile in the tileset: [x,y]
        public int f;        // 'flip bits'; 0=none, 1=horiz, 2=vert, 3=both
        public int t;        // ??? tile index maybe? undocumented?
        public int[] d;      // ??? no idea... undocumented?
    }

    public static class EntityInstance {
        public String __identifier;
        public int __cx;
        public int __cy;
        public int defUid;
        public int x;
        public int y;
        public List<FieldInstance> fieldInstances;
    }

    public static class FieldInstance {
        public String __identifier;
        // NOTE: this could be a plain value (int, float bool, string) or an array of plain values, not sure how to handle that
        public List<String> __value;
        public String __type;
        public int defUid;
    }

}
