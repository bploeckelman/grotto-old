package zendo.games.grotto.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

/**
 * Based on https://github.com/noelfb/blah/include/input/VirtualStick.h
 */
public class VirtualStick {

    private static final String tag = VirtualStick.class.getSimpleName();

    // ----------------------------------------------------

    private static class KeysNode {
        Input.Key left  = Input.Key.unknown;
        Input.Key right = Input.Key.unknown;
        Input.Key up    = Input.Key.unknown;
        Input.Key down  = Input.Key.unknown;

        Point value;

        void init(Input.Key left, Input.Key right, Input.Key up, Input.Key down) {
            this.left  = left;
            this.right = right;
            this.up    = up;
            this.down  = down;
            this.value = Point.zero();
        }

        void update() {
            value.x = Input.axisCheck(value.x, left, right);
            value.y = Input.axisCheck(value.y, down, up);
        }
    }

    // ----------------------------------------------------

    private static class ButtonsNode {
        int gamepadId = 0;

        Input.Button left  = Input.Button.none;
        Input.Button right = Input.Button.none;
        Input.Button up    = Input.Button.none;
        Input.Button down  = Input.Button.none;

        Point value;

        void init(int gamepadId, Input.Button left, Input.Button right, Input.Button up, Input.Button down) {
            this.gamepadId = gamepadId;
            this.left  = left;
            this.right = right;
            this.up    = up;
            this.down  = down;
            this.value = Point.zero();
        }

        void update() {
            value.x = Input.axisCheck(value.x, gamepadId, left, right);
            value.y = Input.axisCheck(value.x, gamepadId, down, up);
        }
    }

    // ----------------------------------------------------

    private static class AxisNode {
        int gamepadId = 0;

        Input.Axis horizontal = Input.Axis.none;
        Input.Axis vertical   = Input.Axis.none;

        float deadzone = 0;

        Vector2 value;

        void init(int gamepadId, Input.Axis horizontal, Input.Axis vertical, float deadzone) {
            this.gamepadId = gamepadId;
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.deadzone = deadzone;
            this.value = Vector2.Zero.cpy();
        }

        void update() {
            value.x = Input.axisCheck(gamepadId, horizontal);
            value.y = Input.axisCheck(gamepadId, vertical);
            if (value.len() < deadzone) {
                value.set(0f, 0f);
            }
        }
    }

    // ------------------------------------------------------------------------

    private static final int max_virtual_nodes = 32;

    KeysNode[] keys = new KeysNode[max_virtual_nodes];
    ButtonsNode[] buttons = new ButtonsNode[max_virtual_nodes];
    AxisNode[] axes = new AxisNode[max_virtual_nodes];

    int keysLen = 0;
    int buttonsLen = 0;
    int axesLen = 0;

    float pressBuffer = 0;
    float releaseBuffer = 0;
    float repeatDelay = 0;
    float repeatInterval = 0;

    Vector2 value = new Vector2();
    Vector2 lastValue = new Vector2();
    Point valueInt = Point.zero();
    Point lastValueInt = Point.zero();

    boolean pressed = false;
    boolean released = false;

    double lastPressTime = -1;
    double lastReleaseTime = -1;
    double repeatPressTime = -1;

    float deadzone;

    // ------------------------------------------------------------------------

    private static final float default_deadzone = 0;

    public VirtualStick() {
        this(default_deadzone);
    }

    public VirtualStick(float deadzone) {
        this.deadzone = deadzone;
        for (int i = 0; i < max_virtual_nodes; i++) {
            keys[i]    = new KeysNode();
            buttons[i] = new ButtonsNode();
            axes[i]    = new AxisNode();
        }
    }

    // ------------------------------------------------------------------------

    public VirtualStick addKeys(Input.Key left, Input.Key right, Input.Key up, Input.Key down) {
        if (keysLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualStick no more keys available");
        } else {
            keys[keysLen].init(left, right, up, down);
            keysLen++;
        }
        return this;
    }

    public VirtualStick addButtons(int gamepadId, Input.Button left, Input.Button right, Input.Button up, Input.Button down) {
        if (buttonsLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualStick no more buttons available");
        } else {
            buttons[buttonsLen].init(gamepadId, left, right, up, down);
            buttonsLen++;
        }
        return this;
    }

    public VirtualStick addAxes(int gamepadId, Input.Axis horizontal, Input.Axis vertical, float deadzone) {
        if (axesLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualStick no more axes available");
        } else {
            axes[axesLen].init(gamepadId, horizontal, vertical, deadzone);
            axesLen++;
        }
        return this;
    }

    public VirtualStick repeat(float repeatDelay, float repeatInterval) {
        this.repeatDelay = repeatDelay;
        this.repeatInterval = repeatInterval;
        return this;
    }

    public VirtualStick pressBuffer(float duration) {
        this.pressBuffer = duration;
        return this;
    }

    public VirtualStick releaseBuffer(float duration) {
        this.releaseBuffer = duration;
        return this;
    }

    // ------------------------------------------------------------------------

    public void update() {
        lastValue = value;
        value = Vector2.Zero.cpy();

        // keys
        for (int i = 0; i < keysLen; i++) {
            keys[i].update();
            if (value.isZero()) {
                value.x = keys[i].value.x;
                value.y = keys[i].value.y;
            }
        }

        // buttons
        for (int i = 0; i < buttonsLen; i++) {
            buttons[i].update();
            if (value.isZero()) {
                value.x = buttons[i].value.x;
                value.y = buttons[i].value.y;
            }
        }

        // axes
        for (int i = 0; i < axesLen; i++) {
            axes[i].update();
            if (value.isZero()) {
                value.x = axes[i].value.x;
                value.y = axes[i].value.y;
            }
        }

        // integer bounded values
        lastValueInt.set(valueInt.x, valueInt.y);
        if      (value.x >  deadzone) valueInt.x = 1;
        else if (value.x < -deadzone) valueInt.x = -1;
        else                          valueInt.x = 0;
        if      (value.y >  deadzone) valueInt.y = 1;
        else if (value.y < -deadzone) valueInt.y = -1;
        else                          valueInt.y = 0;

        // pressed?
        pressed = false;
        if (!valueInt.is(0, 0) && !lastValueInt.is(valueInt)) {
            pressed = true;
            lastPressTime = repeatPressTime = Time.millis;
        } else if (!valueInt.is(0, 0) && valueInt.is(lastValueInt)) {
            if (Time.millis - lastPressTime <= pressBuffer) {
                pressed = true;
            } else if (repeatInterval > 0 && Time.millis >= repeatPressTime + repeatDelay) {
                int prev = (int) ((Time.previous_elapsed - repeatPressTime - repeatDelay) / repeatInterval);
                int curr = (int) ((Time.millis - repeatPressTime - repeatDelay) / repeatInterval);
                pressed = prev < curr;
            }
        }

        // released?
        if (!lastValueInt.is(0, 0) && !valueInt.is(lastValueInt)) {
            released = true;
            lastReleaseTime = Time.millis;
        } else {
            released = (Time.millis - lastReleaseTime <= releaseBuffer);
        }
    }

    // ------------------------------------------------------------------------

    public Vector2 value() { return value; }
    public Vector2 lastValue() { return lastValue; }

    public Point valueInt() { return valueInt; }
    public Point lastValueInt() { return lastValueInt; }

    public boolean pressed() { return pressed; }
    public boolean released() { return released; }

    public void clearPressBuffer() {
        lastPressTime = 0;
    }

    public void clearReleaseBuffer() {
        lastReleaseTime = 0;
    }

}
