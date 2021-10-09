package zendo.games.grotto.map;

import java.util.List;

// https://doc.mapeditor.org/en/stable/manual/worlds/
public class TiledWorldDef {

    public List<MapDef> maps;
    public boolean onlyShowAdjacentMaps;
    public String type;

    public static class MapDef {
        public String fileName;
        public int width;
        public int height;
        public int x;
        public int y;
    }

}
