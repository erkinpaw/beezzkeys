package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Field;

/**
 * A check for the game versions without Fabric's client game tests (before 1.21.4) and for the other loaders: started
 * with -Dbeezzkeys.selftest=true, it opens the keyboard from the title screen, clicks through it by calling the screen's
 * own input methods, saves screenshots (screenshots/beezzkeys_selftest_*.png) and closes the game. The log says
 * "BeezzKeys selftest: OK" or names the step that failed. Does nothing in a normal game.
 */
public final class SelfTest {
    private static final boolean ON = Boolean.getBoolean("beezzkeys.selftest");
    private static int tick, step;
    private static Screen parent;
    private static String lastClick = "none";
    private static int lastSize;

    private SelfTest() {}

    static void tick(Minecraft mc) {
        if (!ON || step < 0) return;
        if (step == 0) {
            if (!(Compat.screen(mc) instanceof TitleScreen) || !Compat.loaded(mc)) return;
            // the window may still be growing to its size (a resize builds the screen again and closes its menus):
            // start once its size has not changed for three seconds
            int size = mc.getWindow().getWidth() * 100000 + mc.getWindow().getHeight();
            if (size != lastSize) {
                lastSize = size;
                tick = 0;
                return;
            }
            if (++tick < 60) return;
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            parent = Compat.screen(mc);
            tick = 0;
            step = 1;
            return;
        }
        if (++tick % 15 != 0) return;
        try {
            Screen s = Compat.screen(mc);
            switch (step) {
                case 1 -> Compat.setScreen(mc, new KeyboardScreen(parent));
                case 2 -> shot(mc, "01_keyboard", s);
                case 3 -> click(s, InputConstants.KEY_H, InputConstants.MOUSE_BUTTON_RIGHT);
                case 4 -> {
                    if (field(s, "menu") == null) throw new IllegalStateException("right click opened no menu; screen " + s.width + "x" + s.height
                            + ", unit " + field(s, "unit") + ", keyboard at " + field(s, "kbX") + "," + field(s, "kbY") + ", last click " + lastClick);
                    shot(mc, "02_key_menu", s);
                }
                case 5 -> s.keyPressed(InputConstants.KEY_ESCAPE, 0, 0);
                case 6 -> click(s, InputConstants.KEY_J, InputConstants.MOUSE_BUTTON_LEFT);
                case 7 -> shot(mc, "03_key_pinned", s);
                case 8 -> s.keyPressed(InputConstants.KEY_LCONTROL, 0, 2);
                case 9 -> shot(mc, "04_ctrl_held", s);
                case 10 -> s.keyReleased(InputConstants.KEY_LCONTROL, 0, 0);
                case 11 -> s.mouseClicked(12, 12, InputConstants.MOUSE_BUTTON_LEFT);
                case 12 -> shot(mc, "05_gear", s);
                case 13 -> s.keyPressed(InputConstants.KEY_ESCAPE, 0, 0);
                case 14 -> s.mouseScrolled(s.width / 2.0, s.height - 60, 0, -3);
                case 15 -> shot(mc, "06_scrolled", s);
                case 16 -> BeezzKeys.openVanilla(parent);
                case 17 -> shot(mc, "07_vanilla_list", s);
                case 18 -> {
                    if (!s.getClass().getSimpleName().equals("KeyBindsScreen")) throw new IllegalStateException("vanilla list did not open: " + s);
                    Compat.setScreen(mc, new KeyboardScreen(parent));
                }
                case 19 -> {
                    if (!(s instanceof KeyboardScreen)) throw new IllegalStateException("keyboard did not open again: " + s);
                    System.out.println("BeezzKeys selftest: OK");
                    step = -1;
                    mc.stop();
                    return;
                }
                default -> { }
            }
            step++;
        } catch (Throwable e) {
            System.out.println("BeezzKeys selftest: FAILED at step " + step + ": " + e);
            e.printStackTrace(System.out);
            step = -1;
            mc.stop();
        }
    }

    private static void shot(Minecraft mc, String name, Screen s) {
        if (!(s instanceof KeyboardScreen) && !name.contains("vanilla")) throw new IllegalStateException("not on the keyboard: " + s);
        Screenshot.grab(mc.gameDirectory, "beezzkeys_selftest_" + name + ".png", mc.getMainRenderTarget(), msg -> { });
    }

    /** A click on the middle of a key cap of the drawn keyboard. */
    private static void click(Screen s, int keyCode, int button) throws ReflectiveOperationException {
        // the screen's own caps and its own geometry, so the click lands where the screen draws the key
        @SuppressWarnings("unchecked")
        java.util.List<KeyLayout.Cap> caps = (java.util.List<KeyLayout.Cap>) field(s, "caps");
        for (KeyLayout.Cap c : caps) {
            if (c.key().getType() == Compat.KEYBOARD && c.key().getValue() == keyCode) {
                double x = (call(s, "capX0", c) + call(s, "capX1", c)) / 2.0, y = (call(s, "capY0", c) + call(s, "capY1", c)) / 2.0;
                lastClick = x + "," + y + " on " + c + " x0..x1 " + call(s, "capX0", c) + ".." + call(s, "capX1", c) + " of " + caps.size() + " caps";
                s.mouseClicked(x, y, button);
                s.mouseReleased(x, y, button);
                return;
            }
        }
        throw new IllegalStateException("no key cap " + keyCode);
    }

    private static int call(Object o, String name, KeyLayout.Cap c) throws ReflectiveOperationException {
        java.lang.reflect.Method m = KeyboardScreen.class.getDeclaredMethod(name, KeyLayout.Cap.class);
        m.setAccessible(true);
        return (int) m.invoke(o, c);
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = KeyboardScreen.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
}
