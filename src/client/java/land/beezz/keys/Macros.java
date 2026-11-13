package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/** Sends the player's own commands when their keys are pressed in the game (no screen open). */
public final class Macros {
    /** Modifier keys seen going down and not yet up; a second source next to the flags of the key event itself. */
    private static int held;

    private Macros() {}

    /** @return true when the press belongs to a combination and the game must not see it */
    public static boolean onKey(int action, KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = InputConstants.getKey(event);
        int own = Config.modBit(key);
        if (!mc.isWindowActive()) held = 0;
        if (action == InputConstants.RELEASE) held &= ~own;
        else if (action == InputConstants.PRESS) held |= own;
        if (action != InputConstants.PRESS || Compat.screen(mc) != null || mc.player == null) return false;
        Config config = Config.get();
        if (config.macros.isEmpty()) return false;
        int mods = held | (event.hasControlDown() ? Config.CTRL : 0) | (event.hasShiftDown() ? Config.SHIFT : 0)
                | (event.hasAltDown() ? Config.ALT : 0);
        mods &= ~own;
        for (Config.Macro m : config.macros) {
            if (m.mods != mods || !m.key.equals(key.getName())) continue;
            String text = m.command.trim();
            if (text.startsWith("/")) mc.player.connection.sendCommand(text.substring(1));
            else if (!text.isEmpty()) mc.player.connection.sendChat(text);
            return m.mods != 0;
        }
        return false;
    }

    /** Alt+Tab takes the release of Alt away from the game: forget the held keys while the window is not active. */
    public static void tick(Minecraft mc) {
        if (!mc.isWindowActive()) held = 0;
    }
}
