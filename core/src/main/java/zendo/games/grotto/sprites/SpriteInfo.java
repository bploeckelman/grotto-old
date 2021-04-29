package zendo.games.grotto.sprites;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

/**
 * Data required to create a Sprite from an Aseprite file and packed textures
 */
public class SpriteInfo {
    public String path;
    public String name;
    public Point slice_pivot;
    public ObjectMap<String, Array<AnimFrameInfo>> anim_frame_infos;

    public static class AnimFrameInfo {
        public String region_name;
        public RectI hitbox;
        public int region_index;
        public float duration;

        public AnimFrameInfo() {
            region_name = null;
            hitbox = null;
            region_index = -1;
            duration = 0f;
        }
    }

    public SpriteInfo() {
        path = null;
        name = null;
        slice_pivot = new Point();
        anim_frame_infos = new ObjectMap<>();
    }
}
