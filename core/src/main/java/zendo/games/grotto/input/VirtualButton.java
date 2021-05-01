package zendo.games.grotto.input;

import com.badlogic.gdx.Gdx;
import zendo.games.grotto.utils.Time;

/**
 * Based on https://github.com/noelfb/blah/include/input/VirtualButton.h
 */
public class VirtualButton {

    private static final String tag = VirtualButton.class.getSimpleName();

    // ----------------------------------------------------

    private static class KeyNode  {
        Input.Key key = Input.Key.unknown;

        boolean pressed = false;
        boolean down = false;
        boolean released = false;

        void init(Input.Key key) {
            this.key = key;
        }

        void update() {
            pressed  = Input.pressed(key);
            down     = Input.down(key);
            released = Input.released(key);
        }
    }

    // ----------------------------------------------------

    private static class ButtonNode {
        int gamepadId = 0;
        Input.Button button = Input.Button.none;

        boolean pressed = false;
        boolean down = false;
        boolean released = false;

        void init(int gamepadId, Input.Button button) {
            this.gamepadId = gamepadId;
            this.button = button;
        }

        void update() {
            pressed  = Input.pressed(gamepadId, button);
            down     = Input.down(gamepadId, button);
            released = Input.released(gamepadId, button);
        }
    }

    // ----------------------------------------------------

    private static class MouseButtonNode {
        Input.MouseButton button = Input.MouseButton.none;

        boolean pressed = false;
        boolean down = false;
        boolean released = false;

        void init(Input.MouseButton button) {
            this.button = button;
        }

        void update() {
            pressed  = Input.pressed(button);
            down     = Input.down(button);
            released = Input.released(button);
        }
    }

    // ----------------------------------------------------

    private static class AxisNode {
        int gamepadId = 0;

        Input.Axis axis = Input.Axis.none;
        float threshold = 0;
        boolean greaterThan = false;

        boolean down = false;
        boolean pressed = false;
        boolean released = false;

        void init(int gamepadId, Input.Axis axis, float threshold, boolean greaterThan) {
            this.gamepadId = gamepadId;
            this.axis = axis;
            this.threshold = threshold;
            this.greaterThan = greaterThan;
        }

        void update() {
            float curr = Input.state().controllers[gamepadId].axis[axis.index];
            float prev = Input.lastState().controllers[gamepadId].axis[axis.index];

            if (greaterThan) {
                down = curr >= threshold;
                pressed = down && prev < threshold;
                released = !down && prev >= threshold;
            } else {
                down = curr <= threshold;
                pressed = down && prev > threshold;
                released = !down && prev <= threshold;
            }
        }
    }

    // ------------------------------------------------------------------------

    private static final int max_virtual_nodes = 32;

    KeyNode[] keys = new KeyNode[max_virtual_nodes];
    ButtonNode[] buttons = new ButtonNode[max_virtual_nodes];
    MouseButtonNode[] mouseButtons = new MouseButtonNode[max_virtual_nodes];
    AxisNode[] axes = new AxisNode[max_virtual_nodes];

    int keysLen = 0;
    int buttonsLen = 0;
    int mouseButtonsLen = 0;
    int axesLen = 0;

    float pressBuffer = 0;
    float releaseBuffer = 0;
    float repeatDelay = 0;
    float repeatInterval = 0;

    boolean down = false;
    boolean pressed = false;
    boolean released = false;

    double lastPressTime = -1;
    double lastReleaseTime = -1;
    double repeatPressTime = -1;

    // ------------------------------------------------------------------------

    public VirtualButton() {
        for (int i = 0; i < max_virtual_nodes; i++) {
            keys[i] = new KeyNode();
            buttons[i] = new ButtonNode();
            mouseButtons[i] = new MouseButtonNode();
            axes[i] = new AxisNode();
        }
    }

    public VirtualButton addKey(Input.Key key) {
        if (keysLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualButton no more keys available");
        } else {
            keys[keysLen].init(key);
            keysLen++;
        }
        return this;
    }

    public VirtualButton addButton(int gamepadId, Input.Button button) {
        if (buttonsLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualButton no more buttons available");
        } else {
            buttons[buttonsLen].init(gamepadId, button);
            buttonsLen++;
        }
        return this;
    }

    public VirtualButton addMouseButton(Input.MouseButton button) {
        if (mouseButtonsLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualButton no more mouse buttons available");
        } else {
            mouseButtons[mouseButtonsLen].init(button);
            mouseButtonsLen++;
        }
        return this;
    }

    public VirtualButton addAxis(int gamepadId, Input.Axis axis, float threshold, boolean greaterThan) {
        if (axesLen >= max_virtual_nodes) {
            Gdx.app.log(tag, "VirtualButton no more axis available");
        } else {
            axes[axesLen].init(gamepadId, axis, threshold, greaterThan);
            axesLen++;
        }
        return this;
    }

    public VirtualButton repeat(float repeatDelay, float repeatInterval) {
        this.repeatDelay = repeatDelay;
        this.repeatInterval = repeatInterval;
        return this;
    }

    public VirtualButton pressBuffer(float duration) {
        this.pressBuffer = duration;
        return this;
    }

    public VirtualButton releaseBuffer(float duration) {
        this.releaseBuffer = duration;
        return this;
    }

    // ------------------------------------------------------------------------

    public void update() {
        pressed = false;
        down = false;
        released = false;

        // keys
        for (int i = 0; i < keysLen; i++) {
            keys[i].update();

            pressed  = pressed  || keys[i].pressed;
            down     = down     || keys[i].down;
            released = released || keys[i].released;
        }

        // buttons
        for (int i = 0; i < buttonsLen; i++) {
            buttons[i].update();

            pressed  = pressed  || buttons[i].pressed;
            down     = down     || buttons[i].down;
            released = released || buttons[i].released;
        }

        // mouse buttons
        for (int i = 0; i < mouseButtonsLen; i++) {
            mouseButtons[i].update();

            pressed  = pressed  || mouseButtons[i].pressed;
            down     = down     || mouseButtons[i].down;
            released = released || mouseButtons[i].released;
        }

        // axes
        for (int i = 0; i < axesLen; i++) {
            axes[i].update();

            pressed  = pressed  || axes[i].pressed;
            down     = down     || axes[i].down;
            released = released || axes[i].released;
        }

        // pressed?
        if (pressed) {
            repeatPressTime = lastPressTime = Time.millis;
        } else if (Time.millis - lastPressTime <= pressBuffer) {
            pressed = true;
        } else if (down && repeatInterval > 0 && Time.millis >= repeatPressTime + repeatDelay) {
            int prev = (int) ((Time.previous_elapsed - repeatPressTime - repeatDelay) / repeatInterval);
            int curr = (int) ((Time.millis - repeatPressTime - repeatDelay) / repeatInterval);
            pressed = prev < curr;
        }

        // released?
        if (released) {
            lastReleaseTime = Time.millis;
        } else {
            released = Time.millis - lastReleaseTime <= releaseBuffer;
        }
    }

    // ------------------------------------------------------------------------

    public boolean down() {
        return down;
    }

    public boolean pressed() {
        return pressed;
    }

    public boolean released() {
        return released;
    }

    public void clearPressBuffer() {
        lastPressTime = -1;
        pressed = false;
    }

    public void clearReleaseBuffer() {
        lastReleaseTime = -1;
        released = false;
    }

}
