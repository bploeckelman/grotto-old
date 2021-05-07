package zendo.games.grotto.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.Time;

/**
 * Based on https://github.com/noelfb/blah/include/input/Input.h
 */
public class Input extends InputAdapter implements ControllerListener {

    public static final String tag = Input.class.getSimpleName();

    // ----------------------------------------------------

    public static final int max_keyboard_keys = 255;
    public static final int max_mouse_buttons = 16;
    public static final int max_controller_buttons = 64;
    public static final int max_controller_axis = 16;
    public static final int max_controllers = 8;

    // ----------------------------------------------------

    public static class KeyboardState {
        boolean[] pressed  = new boolean[max_keyboard_keys];
        boolean[] down     = new boolean[max_keyboard_keys];
        boolean[] released = new boolean[max_keyboard_keys];

        long[] timestamp = new long[max_keyboard_keys];

        public void set(KeyboardState other) {
            for (int i = 0; i < max_keyboard_keys; i++) {
                this.pressed[i]   = other.pressed[i];
                this.down[i]      = other.down[i];
                this.released[i]  = other.released[i];
                this.timestamp[i] = other.timestamp[i];
            }
        }
    }

    public static class MouseState {
        boolean[] pressed  = new boolean[max_mouse_buttons];
        boolean[] down     = new boolean[max_mouse_buttons];
        boolean[] released = new boolean[max_mouse_buttons];

        long[] timestamp = new long[max_mouse_buttons];

        Point wheel = Point.zero();

        Vector2 position = new Vector2();

        public void set(MouseState other) {
            for (int i = 0; i < max_mouse_buttons; i++) {
                this.pressed[i]   = other.pressed[i];
                this.down[i]      = other.down[i];
                this.released[i]  = other.released[i];
                this.timestamp[i] = other.timestamp[i];
            }
            this.wheel.set(other.wheel.x, other.wheel.y);
            this.position.set(other.position);
        }
    }

    public static class ControllerState {
        public String uuid;
        public String name;
        public boolean isConnected;

        boolean[] pressed;
        boolean[] down;
        boolean[] released;

        float[] axis;

        long[] buttonTimestamp;
        long[] axisTimestamp;

        ControllerState() {
            clear();
        }

        void clear() {
            name = "disconnected";
            uuid = "";
            isConnected = false;

            pressed  = new boolean[max_controller_buttons];
            down     = new boolean[max_controller_buttons];
            released = new boolean[max_controller_buttons];

            axis = new float[max_controller_axis];

            buttonTimestamp = new long[max_controller_buttons];
            axisTimestamp = new long[max_controller_axis];
        }

        public void set(ControllerState other) {
            this.name = other.name;
            this.uuid = other.uuid;
            this.isConnected = other.isConnected;
            for (int i = 0; i < max_controller_buttons; i++) {
                this.pressed[i] = other.pressed[i];
                this.down[i] = other.down[i];
                this.released[i] = other.released[i];
            }
            for (int i = 0; i < max_controller_axis; i++) {
                this.axis[i] = other.axis[i];
            }
            for (int i = 0; i < max_controller_buttons; i++) {
                this.buttonTimestamp[i] = other.buttonTimestamp[i];
            }
            for (int i = 0; i < max_controller_axis; i++) {
                this.axisTimestamp[i] = other.axisTimestamp[i];
            }
        }
    }

    // ----------------------------------------------------

    public enum Axis {
        none(-1)
        , leftX(0)
        , leftY(1)
        , rightX(2)
        , rightY(3)
        , leftTrigger(4)
        , rightTrigger(5)
        ;
        public int index;
        Axis(int index) { this.index = index; }
    }

    public enum Button {
        none(-1)
        , a(0)
        , b(1)
        , x(2)
        , y(3)
        , back(4)
        , select(5)
        , start(6)
        , leftStick(7)
        , rightStick(8)
        , leftShoulder(9)
        , rightShoulder(10)
        , up(11)
        , down(12)
        , left(13)
        , right(14)
        ;
        public int index;
        Button(int index) { this.index = index; }
    }

    // note - values copied from com.badlogic.gdx.Input.Buttons
    //  so they should be interchangeable with those values
    public enum MouseButton {
        none(-1)
        , left(0)
        , right(1)
        , middle(2)
        , back(3)
        , forward(4)
        ;
        public int index;
        MouseButton(int index) { this.index = index; }
    }

    // note - values copied from com.badlogic.gdx.Input.Keys
    //  so they should be interchangeable with those values
    public enum Key {
        any_key(-1)
        , num_0(7)
        , num_1(8)
        , num_2(9)
        , num_3(10)
        , num_4(11)
        , num_5(12)
        , num_6(13)
        , num_7(14)
        , num_8(15)
        , num_9(16)
        , a(29)
        , alt_left(57)
        , alt_right(58)
        , apostrophe(75)
        , at(77)
        , b(30)
        , back(4)
        , backslash(73)
        , c(31)
        , call(5)
        , camera(27)
        , caps_lock(115)
        , clear(28)
        , comma(55)
        , d(32)
        , del(67)
        , backspace(67)
        , forward_del(112)
        , dpad_center(23)
        , dpad_down(20)
        , dpad_left(21)
        , dpad_right(22)
        , dpad_up(19)
        , center(23)
        , down(20)
        , left(21)
        , right(22)
        , up(19)
        , e(33)
        , endcall(6)
        , enter(66)
        , envelope(65)
        , equals(70)
        , explorer(64)
        , f(34)
        , focus(80)
        , g(35)
        , grave(68)
        , h(36)
        , headsethook(79)
        , home(3)
        , i(37)
        , j(38)
        , k(39)
        , l(40)
        , left_bracket(71)
        , m(41)
        , media_fast_forward(90)
        , media_next(87)
        , media_play_pause(85)
        , media_previous(88)
        , media_rewind(89)
        , media_stop(86)
        , menu(82)
        , minus(69)
        , mute(91)
        , n(42)
        , notification(83)
        , num(78)
        , o(43)
        , p(44)
        , pause(121) // AKA BREAK
        , period(56)
        , plus(81)
        , pound(18)
        , power(26)
        , print_screen(120) // AKA sysrq
        , q(45)
        , r(46)
        , right_bracket(72)
        , s(47)
        , scroll_lock(116)
        , search(84)
        , semicolon(74)
        , shift_left(59)
        , shift_right(60)
        , slash(76)
        , soft_left(1)
        , soft_right(2)
        , space(62)
        , star(17)
        , sym(63)
        , t(48)
        , tab(61)
        , u(49)
        , unknown(0)
        , v(50)
        , volume_down(25)
        , volume_up(24)
        , w(51)
        , x(52)
        , y(53)
        , z(54)
        , meta_alt_left_on(16)
        , meta_alt_on(2)
        , meta_alt_right_on(32)
        , meta_shift_left_on(64)
        , meta_shift_on(1)
        , meta_shift_right_on(128)
        , meta_sym_on(4)
        , control_left(129)
        , control_right(130)
        , escape(111)
        , end(123)
        , insert(124)
        , page_up(92)
        , page_down(93)
        , pictsymbols(94)
        , switch_charset(95)
        , button_circle(255)
        , button_a(96)
        , button_b(97)
        , button_c(98)
        , button_x(99)
        , button_y(100)
        , button_z(101)
        , button_l1(102)
        , button_r1(103)
        , button_l2(104)
        , button_r2(105)
        , button_thumbl(106)
        , button_thumbr(107)
        , button_start(108)
        , button_select(109)
        , button_mode(110)

        , numpad_0(144)
        , numpad_1(145)
        , numpad_2(146)
        , numpad_3(147)
        , numpad_4(148)
        , numpad_5(149)
        , numpad_6(150)
        , numpad_7(151)
        , numpad_8(152)
        , numpad_9(153)

        , numpad_divide(154)
        , numpad_multiply(155)
        , numpad_subtract(156)
        , numpad_add(157)
        , numpad_dot(158)
        , numpad_comma(159)
        , numpad_enter(160)
        , numpad_equals(161)
        , numpad_left_paren(162)
        , numpad_right_paren(163)
        , num_lock(143)
// , backtick(0)
// , tilde(0)
// , underscore(0)
// , dot(0)
// , break(0)
// , pipe(0)
// , exclamation(0)
// , questionmark(0)
        , colon(243)
        , f1(131)
        , f2(132)
        , f3(133)
        , f4(134)
        , f5(135)
        , f6(136)
        , f7(137)
        , f8(138)
        , f9(139)
        , f10(140)
        , f11(141)
        , f12(142)
        , f13(183)
        , f14(184)
        , f15(185)
        , f16(186)
        , f17(187)
        , f18(188)
        , f19(189)
        , f20(190)
        , f21(191)
        , f22(192)
        , f23(193)
        , f24(194)
        ;
        public int index;
        Key(int index) { this.index = index; }
        public static Key fromCode(int code) {
            for (int i = 0; i < Key.values().length; i++) {
                if (Key.values()[i].index == code) {
                    return Key.values()[i];
                }
            }
            return Key.unknown;
        }
    }

    // ----------------------------------------------------

    public static class State {
        ControllerState[] controllers = new ControllerState[max_controllers];
        KeyboardState keyboard = new KeyboardState();
        MouseState mouse = new MouseState();

        State() {
            for (int i = 0; i < controllers.length; i++) {
                controllers[i] = new ControllerState();
            }
        }

        public void set(State other) {
            for (int i = 0; i < controllers.length; i++) {
                controllers[i].set(other.controllers[i]);
            }
            keyboard.set(other.keyboard);
            mouse.set(other.mouse);
        }
    }

    private static ControllerState emptyController = new ControllerState();
    private static State lastState = new State();
    private static State currState = new State();
    private static State nextState = new State();

    public static State state() {
        return currState;
    }

    public static State lastState() {
        return lastState;
    }

    // ----------------------------------------------------

    public static void init() {
        Array<Controller> controllers = Controllers.getControllers();
        for (Controller controller : controllers) {
            ControllerState state = null;
            int index = findControllerIndex(controller);
            if (index >= 0 && index < max_controllers) {
                state = nextState.controllers[index];
            } else {
                index = findFreeControllerIndex();
                state = nextState.controllers[index];
            }
            if (state != null) {
                state.isConnected = true;
                state.uuid = controller.getUniqueId();
                state.name = controller.getName();
            }
        }
    }

    public static void frame() {
        // cycle states
        lastState.set(currState);
        currState.set(nextState);

        // copy state, clear pressed / released values
        {
            for (int i = 0; i < max_keyboard_keys; i++) {
                nextState.keyboard.pressed[i] = false;
                nextState.keyboard.released[i] = false;
            }

            for (int i = 0; i < max_mouse_buttons; i++) {
                nextState.mouse.pressed[i] = false;
                nextState.mouse.released[i] = false;
            }
            nextState.mouse.wheel.set(0, 0);
            nextState.mouse.position.set(Gdx.input.getX(), Gdx.input.getY());

            for (int i = 0; i < max_controllers; i++) {
                ControllerState controller = nextState.controllers[i];
                if (controller == null) {
                    controller = new ControllerState();
                } else {
                    if (!controller.isConnected) {
                        controller.name = "disconnected";
                    }
                }
                for (int j = 0; j < max_controller_buttons; j++) {
                    controller.pressed[j] = false;
                    controller.released[j] = false;
                }
            }
        }
    }

    // ----------------------------------------------------
    // InputAdapter implementation

    @Override
    public boolean keyDown(int keycode) {
        if (keycode >= 0 && keycode < max_keyboard_keys) {
            nextState.keyboard.down[keycode] = true;
            nextState.keyboard.pressed[keycode] = true;
            nextState.keyboard.timestamp[keycode] = Time.millis;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode >= 0 && keycode < max_keyboard_keys) {
            nextState.keyboard.down[keycode] = false;
            nextState.keyboard.released[keycode] = true;
            return true;
        }
        return false;
    }

//    @Override
//    public boolean keyTyped(char character) {
//        return super.keyTyped(character);
//    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button >= 0 && button < max_mouse_buttons) {
            nextState.mouse.down[button] = true;
            nextState.mouse.pressed[button] = true;
            nextState.mouse.timestamp[button] = Time.millis;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button >= 0 && button < max_mouse_buttons) {
            nextState.mouse.down[button] = false;
            nextState.mouse.released[button] = true;
            return true;
        }
        return false;
    }

//    @Override
//    public boolean touchDragged(int screenX, int screenY, int pointer) {
//        return super.touchDragged(screenX, screenY, pointer);
//    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        nextState.mouse.position.set(screenX, screenY);
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        nextState.mouse.wheel.set((int) amountX, (int) amountY);
        return true;
    }

    // ----------------------------------------------------
    // ControllerListener implementation

    @Override
    public void connected(Controller controller) {
        ControllerState state = null;
        int index = findControllerIndex(controller);
        if (index >= 0 && index < max_controllers) {
            state = nextState.controllers[index];
        } else {
            index = findFreeControllerIndex();
            state = nextState.controllers[index];
        }
        if (state != null) {
            state.isConnected = true;
            state.uuid = controller.getUniqueId();
            state.name = controller.getName();
            Gdx.app.log(tag, "Controller connected: " + state.name + "(" + state.uuid + ")");
        }
    }

    @Override
    public void disconnected(Controller controller) {
        int index = findControllerIndex(controller);
        if (index >= 0 && index < max_controllers) {
            var state = nextState.controllers[index];
            Gdx.app.log(tag, "Controller disconnected: " + state.name + "(" + state.uuid + ")");
            state.clear();
        }
    }

    @Override
    public boolean buttonDown(Controller controller, int button) {
        int index = findControllerIndex(controller);
        if (index  >= 0 && index  < max_controllers
                && button >= 0 && button < max_controller_buttons
                && nextState.controllers[index].isConnected) {
            nextState.controllers[index].down[button] = true;
            nextState.controllers[index].pressed[button] = true;
            nextState.controllers[index].buttonTimestamp[button] = Time.millis;
            return true;
        }
        return false;
    }

    @Override
    public boolean buttonUp(Controller controller, int button) {
        int index = findControllerIndex(controller);
        if (index  >= 0 && index  < max_controllers
                && button >= 0 && button < max_controller_buttons
                && nextState.controllers[index].isConnected) {
            nextState.controllers[index].down[button] = false;
            nextState.controllers[index].released[button] = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean axisMoved(Controller controller, int axisCode, float value) {
        int index = findControllerIndex(controller);
        if (index >= 0 && index < max_controllers
                && axisCode >= 0 && axisCode < max_controller_axis
                && nextState.controllers[index].isConnected) {
            nextState.controllers[index].axis[axisCode] = value;
            nextState.controllers[index].axisTimestamp[axisCode] = Time.millis;
            return true;
        }
        return false;
    }

    private static int findControllerIndex(Controller controller) {
        for (int i = 0; i < currState.controllers.length; i++) {
            if (controller.getUniqueId().equals(currState.controllers[i].uuid)) {
                return i;
            }
        }
        return -1;
    }

    private static int findFreeControllerIndex() {
        for (int i = 0; i < currState.controllers.length; i++) {
            if (!currState.controllers[i].isConnected) {
                return i;
            }
        }
        return -1;
    }

    // ----------------------------------------------------

    public static boolean pressed(MouseButton button) {
        return (button.index >= 0 && button.index < max_mouse_buttons && currState.mouse.pressed[button.index]);
    }

    public static boolean down(MouseButton button) {
        return (button.index >= 0 && button.index < max_mouse_buttons && currState.mouse.down[button.index]);
    }

    public static boolean released(MouseButton button) {
        return (button.index >= 0 && button.index < max_mouse_buttons && currState.mouse.released[button.index]);
    }

    public static Point mouseWheel() {
        return currState.mouse.wheel;
    }

    public static Vector2 mouse() {
        return currState.mouse.position;
    }

    // ----------------------------------------------------

    public static boolean pressed(Key key) {
        return (key.index >= 0 && key.index < max_keyboard_keys && currState.keyboard.pressed[key.index]);
    }

    public static boolean down(Key key) {
        return (key.index >= 0 && key.index < max_keyboard_keys && currState.keyboard.down[key.index]);
    }

    public static boolean released(Key key) {
        return (key.index >= 0 && key.index < max_keyboard_keys && currState.keyboard.released[key.index]);
    }

    // ----------------------------------------------------

    public static ControllerState controller(int controllerIndex) {
        if (controllerIndex >= max_controllers) {
            Gdx.app.log(tag, "Trying to access an out-of-range controller at " + controllerIndex);
            return emptyController;
        } else if (!currState.controllers[controllerIndex].isConnected) {
            return emptyController;
        } else {
            return currState.controllers[controllerIndex];
        }
    }

    public static boolean pressed(int controllerIndex, Button button) {
        if (controllerIndex < max_controllers && button.index >= 0 && button.index < max_controller_buttons) {
            return currState.controllers[controllerIndex].pressed[button.index];
        }
        return false;
    }

    public static boolean down(int controllerIndex, Button button) {
        if (controllerIndex < max_controllers && button.index >= 0 && button.index < max_controller_buttons) {
            return currState.controllers[controllerIndex].down[button.index];
        }
        return false;
    }

    public static boolean released(int controllerIndex, Button button) {
        if (controllerIndex < max_controllers && button.index >= 0 && button.index < max_controller_buttons) {
            return currState.controllers[controllerIndex].released[button.index];
        }
        return false;
    }

    public static float axisCheck(int controllerIndex, Axis axis) {
        if (controllerIndex < max_controllers && axis.index >= 0 && axis.index < max_controller_axis) {
            return currState.controllers[controllerIndex].axis[axis.index];
        }
        return 0;
    }

    // check a virtual axis described by 2 keys, 'fallback' is returned if both keys are held
    public static int axisCheck(int fallback, Key negative, Key positive) {
        if      (Input.pressed(positive)) return 1;
        else if (Input.pressed(negative)) return -1;
        else {
            boolean pos = Input.down(positive);
            boolean neg = Input.down(negative);

            if      (pos && neg) return fallback;
            else if (pos)        return 1;
            else if (neg)        return -1;
            else                 return 0;
        }
    }

    // check a virtual axis described by 2 buttons, 'fallback' is returned if both keys are held
    public static int axisCheck(int fallback, int controllerIndex, Button negative, Button positive) {
        if      (Input.pressed(controllerIndex, positive)) return 1;
        else if (Input.pressed(controllerIndex, negative)) return -1;
        else {
            boolean pos = Input.down(controllerIndex, positive);
            boolean neg = Input.down(controllerIndex, negative);

            if      (pos && neg) return fallback;
            else if (pos)        return 1;
            else if (neg)        return -1;
            else                 return 0;
        }
    }

}
