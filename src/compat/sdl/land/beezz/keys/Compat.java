package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * What differs between game versions. One copy of this class per family, picked in build.gradle:
 * sdl (26.3 and later), glfw-gui (26.2), glfw (26.1).
 */
final class Compat {
    static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
    static final int LGUI = InputConstants.KEY_LGUI, RGUI = InputConstants.KEY_RGUI, MENU = 101, KP_DIVIDE = 84, KP_SUBTRACT = 86, KP_DECIMAL = 99;

    private Compat() {}

    static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    /** No loading overlay on top. */
    static boolean loaded(Minecraft mc) {
        return mc.gui.overlay() == null;
    }
}
