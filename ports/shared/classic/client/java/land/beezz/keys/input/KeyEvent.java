package land.beezz.keys.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Before 1.21.9 a key press reached a screen as three ints. This record gives the ports the same methods the 26.x code
 * calls on the game's own KeyEvent, so that code needs no changes.
 */
public record KeyEvent(int key, int scancode, int modifiers) {
    private static final int SHIFT = 1, CTRL = 2, ALT = 4;

    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }

    public boolean isConfirmation() {
        return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
    }

    public boolean hasShiftDown() {
        return (modifiers & SHIFT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & CTRL) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & ALT) != 0;
    }
}
