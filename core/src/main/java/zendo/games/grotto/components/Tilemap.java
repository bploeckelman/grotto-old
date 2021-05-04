package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Point;

public class Tilemap extends Component {

    private int tileSize;
    private int rows;
    private int cols;

    protected TextureRegion[] grid;

    public Point offset;

    public Tilemap() {}

    public Tilemap(int tileSize, int cols, int rows) {
        this.tileSize = tileSize;
        this.cols = cols;
        this.rows = rows;
        this.grid = new TextureRegion[rows * cols];
        this.offset = Point.zero();
    }

    @Override
    public void reset() {
        super.reset();
        tileSize = 0;
        rows = 0;
        cols = 0;
        grid = null;
        offset = null;
    }

    public int tileSize() {
        return tileSize;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public void setCell(int x, int y, TextureRegion texture) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) {
            throw new GdxRuntimeException("Tilemap indices out of bounds");
        }
        grid[x + y * cols] = texture;
    }

    public void setCells(int x, int y, int w, int h, TextureRegion texture) {
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) {
            throw new GdxRuntimeException("Tilemap indices out of bounds");
        }
        for (int ix = x; ix < x + w; ix++) {
            for (int iy = y; iy < y + h; iy++) {
                grid[ix + iy * cols] = texture;
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                var texture = grid[x + y * cols];
                if (texture == null) {
                    continue;
                }
                batch.draw(texture,
                        entity.position.x + x * tileSize + offset.x,
                        entity.position.y + y * tileSize + offset.y,
                        tileSize, tileSize);
            }
        }
    }

}
