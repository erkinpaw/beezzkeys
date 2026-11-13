package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * What differs between game versions. One copy of this class per family, picked in build.gradle:
 * sdl (26.3 and later), glfw-gui (26.2), glfw (26.1).
 */
final class Compat {
    static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;
    static final int LGUI = 343, RGUI = 347, MENU = 348, KP_DIVIDE = 331, KP_SUBTRACT = 333, KP_DECIMAL = 330;

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
