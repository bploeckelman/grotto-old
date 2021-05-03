package zendo.games.grotto.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.sprites.Content;
import zendo.games.grotto.sprites.Sprite;
import zendo.games.grotto.utils.Calc;

public class Animator extends Component {

    public enum LoopMode { none, loop }

    public Vector2 scale;
    public LoopMode mode;
    public float rotation;
    public float speed;

    private Color tint;
    private Sprite sprite;
    private int animationIndex;
    private int frameIndex;
    private float frameCounter;

    public Animator() {}

    public Animator(String spriteName) {
        scale = new Vector2(1f, 1f);
        tint = new Color(1f, 1f, 1f, 1f);
        sprite = Content.findSprite(spriteName);
    }

    public Animator(String spriteName, String animationName) {
        this(spriteName);
        play(animationName);
    }

    @Override
    public void reset() {
        super.reset();
        mode = LoopMode.loop;
        rotation = 0;
        speed = 1;
        scale = null;
        tint = null;
        sprite = null;
        animationIndex = 0;
        frameIndex = 0;
        frameCounter = 0;
    }

    public Sprite sprite() {
        return sprite;
    }

    public float duration() {
        return (animation() != null) ? animation().duration() : 0f;
    }

    public Sprite.Anim animation() {
        if (sprite != null && animationIndex >= 0 && animationIndex < sprite.animations.size()) {
            return sprite.animations.get(animationIndex);
        }
        return null;
    }

    public Sprite.Frame frame() {
        Sprite.Anim anim = animation();
        return anim.frames.get(frameIndex);
    }

    public Color tint() {
        return tint;
    }

    public void setAlpha(float a) {
        tint.a = a;
    }

    public void setRGB(float r, float g, float b) {
        tint.set(r, g, b, tint.a);
    }

    public void setColor(float r, float g, float b, float a) {
        tint.set(r, g, b, a);
    }

    public Animator play(String animation) {
        return play(animation, false);
    }

    public Animator play(String animation, boolean restart) {
        if (sprite == null) {
            throw new GdxRuntimeException("No Sprite assigned to Animator");
        }

        for (int i = 0; i < sprite.animations.size(); i++) {
            if (sprite.animations.get(i).name.equals(animation)) {
                if (animationIndex != i || restart) {
                    animationIndex = i;
                    frameIndex = 0;
                    frameCounter = 0;
                }
                break;
            }
        }
        return this;
    }

    @Override
    public void update(float dt) {
        if (!inValidState()) return;

        var anim = sprite.animations.get(animationIndex);
        var frame = anim.frames.get(frameIndex);

        // increment frame counter
        frameCounter += speed * dt;

        // move to next frame after duration
        while (frameCounter >= frame.duration) {
            // reset frame counter
            frameCounter -= frame.duration;

            // increment frame, adjust based on loop mode
            frameIndex++;
            switch (mode) {
                case none -> frameIndex = Calc.clampInt(frameIndex, 0, animation().frames.size() - 1);
                case loop -> {
                    if (frameIndex >= anim.frames.size()) {
                        frameIndex = 0;
                    }
                }
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        if (!inValidState()) return;

        var anim = sprite.animations.get(animationIndex);
        var frame = anim.frames.get(frameIndex);

        batch.setColor(tint);
        batch.draw(frame.image,
                   entity.position.x - sprite.origin.x,
                   entity.position.y - sprite.origin.y,
                   sprite.origin.x,
                   sprite.origin.y,
                   frame.image.getRegionWidth(),
                   frame.image.getRegionHeight(),
                   scale.x, scale.y,
                   rotation
        );
        batch.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void render(ShapeRenderer shapes) {
        var shapeType = shapes.getCurrentType();

        // image bounds
        var x = entity.position.x - sprite().origin.x;
        var y = entity.position.y - sprite().origin.y;
        var w = frame().image.getRegionWidth();
        var h = frame().image.getRegionHeight();
        shapes.set(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 0f, 0.75f);
        shapes.rect(x, y, w, h);
        shapes.setColor(Color.WHITE);

        shapes.set(shapeType);
    }

    private boolean inValidState() {
        return (sprite != null
                && animationIndex >= 0
                && animationIndex < sprite.animations.size()
                && frameIndex >= 0
                && frameIndex < sprite.animations.get(animationIndex).frames.size()
        );
    }

}
