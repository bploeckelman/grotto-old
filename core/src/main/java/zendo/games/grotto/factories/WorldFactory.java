package zendo.games.grotto.factories;

import zendo.games.grotto.Assets;
import zendo.games.grotto.Config;
import zendo.games.grotto.components.Collider;
import zendo.games.grotto.components.Tilemap;
import zendo.games.grotto.ecs.Entity;
import zendo.games.grotto.ecs.World;
import zendo.games.grotto.utils.RectI;

public class WorldFactory {

    public static Entity boundary(World world, RectI rect) {
        var entity = world.addEntity();
        {
            var collider = entity.add(Collider.makeRect(rect), Collider.class);
            collider.mask = Collider.Mask.solid;

            entity.position.set(rect.x, rect.y);
        }
        return entity;
    }

    public static Entity tilemap(Assets assets, World world) {
        var entity = world.addEntity();
        {
            var tileSize = 16;
            var cols = Config.framebuffer_width / tileSize;
            var rows = Config.framebuffer_height / tileSize + 1;

            var ul = assets.tilesetRegions[0][3];
            var u  = assets.tilesetRegions[0][4];
            var ur = assets.tilesetRegions[0][5];

            var l  = assets.tilesetRegions[1][3];
            var r  = assets.tilesetRegions[1][5];

            var dl = assets.tilesetRegions[2][3];
            var d  = assets.tilesetRegions[2][4];
            var dr = assets.tilesetRegions[2][5];

            var tilemap = entity.add(new Tilemap(tileSize, cols, rows), Tilemap.class);
            // corners
            tilemap.setCell(0,        0,        dl);
            tilemap.setCell(0,        rows - 1, ul);
            tilemap.setCell(cols - 1, rows - 1, ur);
            tilemap.setCell(cols - 1, 0,        dr);
            // bottom row
            tilemap.setCells(1, 0, cols - 2, 1, d);
            // top row
            tilemap.setCells(1, rows - 1, cols - 2, 1, u);
            // left side
            tilemap.setCells(0, 1, 1, rows - 2, l);
            // right side
            tilemap.setCells(cols - 1, 1, 1, rows - 2, r);

            var collider = entity.add(Collider.makeGrid(tileSize, cols, rows), Collider.class);
            collider.mask = Collider.Mask.solid;
            collider.setCells(0,        0,        cols, 1,        true);
            collider.setCells(0,        rows - 1, cols, 1,        true);
            collider.setCells(0,        0,     1,    rows - 1, true);
            collider.setCells(cols - 1, 0,     1,    rows - 1, true);
        }
        return entity;
    }

}
