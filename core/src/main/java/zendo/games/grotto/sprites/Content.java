package zendo.games.grotto.sprites;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

public abstract class Content {

    protected static Array<Sprite> sprites = new Array<>();
    protected static Json json = new Json();

    public static void unload() {
        sprites.clear();
    }

    /**
     * Find the Sprite with the specified name, if one has been loaded
     *
     * @param name the name of the Sprite to find
     *
     * @return the Sprite, if loaded, otherwise null
     */
    public static Sprite findSprite(String name) {
        for (Sprite sprite : sprites) {
            if (sprite.name.equals(name)) {
                return sprite;
            }
        }
        return null;
    }

    /**
     * Create a Sprite object based on the specified SpriteInfo found in the json file
     * specified by 'path', with TextureRegions found in the specified TextureAtlas
     *
     * @param path the path to a json file contianing SpriteInfo data required to create a Sprite
     * @param atlas the TextureAtlas that holds animation frame TextureRegions referred
     *              to by the specified SpriteInfo
     *
     * @return a Sprite object populated based on data specified in SpriteInfo
     */
    public static Sprite loadSprite(String path, TextureAtlas atlas) {
        Sprite sprite = new Sprite();
        {
            // load the json SpriteInfo
            SpriteInfo info = json.fromJson(SpriteInfo.class, Gdx.files.internal(path));

            // extract properties from aseprite info
            sprite.name = info.name;
            sprite.origin.set(info.slice_pivot.x, info.slice_pivot.y);

            // build sprite animations
            for (String anim_name : info.anim_frame_infos.keys()) {
                Array<SpriteInfo.AnimFrameInfo> anim_frame_info = info.anim_frame_infos.get(anim_name);

                // build frames for animation
                Sprite.Frame[] anim_frames = new Sprite.Frame[anim_frame_info.size];
                for (int i = 0; i < anim_frame_info.size; i++) {
                    SpriteInfo.AnimFrameInfo frame_info = anim_frame_info.get(i);
                    TextureRegion frame_region = atlas.findRegion(frame_info.region_name, frame_info.region_index);
                    float frame_duration = frame_info.duration;
                    anim_frames[i] = new Sprite.Frame(frame_region, frame_duration / 1000f);

                    if (frame_info.hitbox != null) {
                        anim_frames[i].hitbox = frame_info.hitbox;
                    }
                }

                // build animation from frames
                Sprite.Anim anim = new Sprite.Anim(anim_name, anim_frames);

                // add to sprite
                sprite.animations.add(anim);
            }
        }
        return sprite;
    }

}
