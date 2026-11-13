package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Opens the key binds in a real client and walks through them: binding by click, key press and drag, the key menu, own
 * commands with and without Ctrl, the category chooser, the gear, the sets, undo, other languages, and the commands in
 * a world. Every step saves a screenshot.
 */
public class KeyboardScreenTest implements FabricClientGameTest {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT, RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private ClientGameTestContext ctx;

    private static Object field(Object o, String name) {
        try {
            Field f = KeyboardScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Moves the cursor to a point given in GUI pixels. */
    private void cursor(double x, double y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getWidth() / Compat.screen(mc).width);
        ctx.getInput().setCursorPos(x * scale, y * scale);
        ctx.waitTicks(3);
    }

    private double[] cap(int keyCode) {
        return ctx.computeOnClient(mc -> {
            Object s = Compat.screen(mc);
            float unit = (float) field(s, "unit");
            int kbX = (int) field(s, "kbX"), kbY = (int) field(s, "kbY");
            for (KeyLayout.Cap c : KeyLayout.build(Config.get().numpad)) {
                if (c.key().getType() == Compat.KEYBOARD && c.key().getValue() == keyCode) {
                    return new double[] {kbX + (c.x() + c.w() / 2) * unit - 1, kbY + (c.y() + c.h() / 2) * unit - 1};
                }
            }
            throw new IllegalStateException("no cap " + keyCode);
        });
    }

    private double[] row(int index) {
        return ctx.computeOnClient(mc -> {
            Object s = Compat.screen(mc);
            int cols = (int) field(s, "cols"), colW = (int) field(s, "colW"), listTop = (int) field(s, "listTop");
            return new double[] {10 + (index % cols) * colW + 40, listTop + (index / cols) * 14 + 7};
        });
    }

    private double[] menuItem(int index) {
        return ctx.computeOnClient(mc -> {
            PopupMenu menu = (PopupMenu) field(Compat.screen(mc), "menu");
            return new double[] {menu.x + 24, menu.y + 2 + index * PopupMenu.ITEM + 6};
        });
    }

    private String binding(String name) {
        return ctx.computeOnClient(mc -> {
            KeyMapping m = KeyMapping.get(name);
            return name + " = " + KeyMappingHelper.getBoundKeyOf(m).getName();
        });
    }

    private String macros() {
        return ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (Config.Macro m : Config.get().macros) sb.append('[').append(Config.modsText(m.mods)).append(m.key).append(" -> ").append(m.command).append("] ");
            return sb.length() == 0 ? "none" : sb.toString();
        });
    }

    private void click(double[] p, int button) {
        cursor(p[0], p[1]);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(3);
    }

    private void click(double[] p) {
        click(p, LEFT);
    }

    private void shot(String name) {
        ctx.waitTicks(5);
        ctx.takeScreenshot(name);
    }

    private void language(String code) {
        ctx.runOnClient(mc -> {
            mc.options.languageCode = code;
            Compat.setScreen(mc, new KeyboardScreen(new TitleScreen()));
        });
        ctx.waitTicks(5);
        cursor(5, 200);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        ctx.waitTicks(20);
        ctx.runOnClient(Minecraft::reloadResourcePacks);
        ctx.waitTicks(20);
        ctx.waitFor(mc -> Compat.loaded(mc), 20 * 120);
        ctx.waitTicks(20);

        // the vanilla screen must turn into the keyboard by itself
        ctx.setScreen(() -> new KeyBindsScreen(new TitleScreen(), Minecraft.getInstance().options));
        ctx.waitForScreen(KeyboardScreen.class);
        cursor(5, 200);
        shot("keys_01_default");
        cursor(row(9)[0], row(9)[1]);
        shot("keys_01b_full_name");

        // binding: a click on the drawing, a key press, a drag
        click(row(0));
        cursor(5, 200);
        shot("keys_02_listening");
        click(cap(InputConstants.KEY_E));
        shot("keys_03_conflict");
        click(row(1));
        ctx.getInput().pressKey(InputConstants.KEY_G);
        ctx.waitTicks(3);
        double[] from = row(2), to = cap(InputConstants.KEY_H);
        cursor(from[0], from[1]);
        ctx.getInput().holdMouse(LEFT);
        ctx.waitTicks(3);
        cursor(to[0], to[1]);
        ctx.getInput().releaseMouse(LEFT);
        ctx.waitTicks(3);
        System.out.println("[beezzkeys-test] binds: " + binding("key.sprint") + "; " + binding("key.left") + "; " + binding("key.forward"));

        // undo, three times, by Ctrl+Z
        for (int i = 0; i < 3; i++) {
            ctx.getInput().holdControl();
            ctx.getInput().pressKey(InputConstants.KEY_Z);
            ctx.getInput().releaseControl();
            ctx.waitTicks(3);
        }
        System.out.println("[beezzkeys-test] after undo: " + binding("key.sprint") + "; " + binding("key.left") + "; " + binding("key.forward"));

        // the category chooser
        double[] plus = ctx.computeOnClient(mc -> {
            for (Object chip : (List<?>) field(Compat.screen(mc), "chips")) {
                if (chip.toString().contains("kind=1")) {
                    String s = chip.toString();
                    int x = Integer.parseInt(s.replaceAll(".*, x=(\\d+).*", "$1")), y = Integer.parseInt(s.replaceAll(".*, y=(\\d+).*", "$1"));
                    return new double[] {x + 5, y + 5};
                }
            }
            throw new IllegalStateException("no plus");
        });
        click(plus);
        shot("keys_04_categories");
        click(menuItem(1));
        shot("keys_05_category_off");
        click(menuItem(1));
        click(new double[] {5, 200});

        // the key menu: an action of a category onto the key
        click(cap(InputConstants.KEY_H), RIGHT);
        shot("keys_06_key_menu");
        click(menuItem(5));
        shot("keys_07_actions");
        click(menuItem(2));
        System.out.println("[beezzkeys-test] from the menu: " + binding("key.sprint"));

        // own commands: plain J and Ctrl+K
        click(cap(InputConstants.KEY_J), RIGHT);
        click(menuItem(1));
        ctx.waitForScreen(CommandScreen.class);
        ctx.getInput().typeChars("/time set 6000");
        shot("keys_08_command");
        ctx.clickScreenButton("beezzkeys.command.save");
        ctx.waitForScreen(KeyboardScreen.class);
        click(cap(InputConstants.KEY_K), RIGHT);
        click(menuItem(1));
        ctx.waitForScreen(CommandScreen.class);
        ctx.getInput().typeChars("hello from ctrl+k");
        double[] ctrl = ctx.computeOnClient(mc -> new double[] {Compat.screen(mc).width / 2.0 - 150 + 48, Compat.screen(mc).height / 2.0 - 40 + 38});
        click(ctrl);
        shot("keys_09_command_ctrl");
        ctx.clickScreenButton("beezzkeys.command.save");
        ctx.waitForScreen(KeyboardScreen.class);
        System.out.println("[beezzkeys-test] commands: " + macros());
        cursor(cap(InputConstants.KEY_K)[0], cap(InputConstants.KEY_K)[1]);
        shot("keys_10_with_commands");
        ctx.getInput().holdControl();
        ctx.waitTicks(3);
        cursor(5, 200);
        shot("keys_11_ctrl_held");
        ctx.getInput().releaseControl();
        ctx.waitTicks(3);

        // the gear and the sets
        click(new double[] {16, 15});
        shot("keys_12_gear");
        click(menuItem(0));
        ctx.waitForScreen(PresetsScreen.class);
        ctx.getInput().typeChars("PvP");
        ctx.clickScreenButton("beezzkeys.presets.save");
        shot("keys_13_presets");
        ctx.clickScreenButton("gui.back");
        ctx.waitForScreen(KeyboardScreen.class);
        ctx.clickScreenButton("beezzkeys.button.reset_all");
        ctx.runOnClient(mc -> ((KeyboardScreen) Compat.screen(mc)).saveMacro(Config.get().macros.get(0), null));
        System.out.println("[beezzkeys-test] before the set: " + binding("key.sprint") + "; " + macros());
        ctx.runOnClient(mc -> ((KeyboardScreen) Compat.screen(mc)).applyPreset(Config.loadPreset("PvP")));
        System.out.println("[beezzkeys-test] after the set: " + binding("key.sprint") + "; " + macros());

        // no numpad
        click(new double[] {16, 15});
        click(menuItem(1));
        cursor(5, 200);
        shot("keys_14_no_numpad");
        click(new double[] {16, 15});
        click(menuItem(1));

        // other languages and a small window
        language("de_de");
        shot("keys_15_german");
        language("fr_fr");
        shot("keys_16_french");
        language("en_us");
        shot("keys_17_english");
        language("ru_ru");
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(4);
            mc.resizeGui();
        });
        ctx.waitTicks(10);
        shot("keys_18_small");
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        ctx.setScreen(TitleScreen::new);

        // the commands in a world
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(100);
            ctx.getInput().pressKey(InputConstants.KEY_J);
            ctx.waitTicks(20);
            ctx.getInput().holdControl();
            ctx.getInput().pressKey(InputConstants.KEY_K);
            ctx.getInput().releaseControl();
            ctx.waitTicks(20);
            shot("keys_19_in_world");
        }
        ctx.runOnClient(mc -> {
            for (KeyMapping m : mc.options.keyMappings) m.setKey(m.getDefaultKey());
            KeyMapping.resetMapping();
            mc.options.save();
        });
    }
}
