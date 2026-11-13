package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A full-size keyboard plus five mouse buttons, measured in key widths. Letters are always the English ones; the letter of
 * the game's language (Й on Q for Russian, Z on Y for German) is a second, small label.
 */
final class KeyLayout {
    static final float HEIGHT = 6.25F;
    /** The printable keys in the order the language tables below follow. */
    static final String BASE = "`1234567890-=" + "QWERTYUIOP[]\\" + "ASDFGHJKL;'" + "ZXCVBNM,./";
    private static final String[][] LANGUAGES = {
            {"ru", "Ё1234567890-=" + "ЙЦУКЕНГШЩЗХЪ\\" + "ФЫВАПРОЛДЖЭ" + "ЯЧСМИТЬБЮ."},
            {"uk", "'1234567890-=" + "ЙЦУКЕНГШЩЗХЇ\\" + "ФІВАПРОЛДЖЄ" + "ЯЧСМИТЬБЮ."},
            {"be", "Ё1234567890-=" + "ЙЦУКЕНГШЎЗХ'\\" + "ФЫВАПРОЛДЖЭ" + "ЯЧСМІТЬБЮ."},
            {"de", "^1234567890ß´" + "QWERTZUIOPÜ+#" + "ASDFGHJKLÖÄ" + "YXCVBNM,.-"},
            {"fr", "²&é\"'(-è_çà)=" + "AZERTYUIOP^$*" + "QSDFGHJKLMù" + "WXCVBN,;:!"},
            {"es", "º1234567890'¡" + "QWERTYUIOP`+Ç" + "ASDFGHJKLÑ´" + "ZXCVBNM,.-"},
            {"pt_br", "'1234567890-=" + "QWERTYUIOP´[]" + "ASDFGHJKLÇ~" + "ZXCVBNM,.;"},
            {"pt", "\\1234567890'«" + "QWERTYUIOP+´~" + "ASDFGHJKLÇº" + "ZXCVBNM,.-"},
            {"tr", "\"1234567890*-" + "QWERTYUIOPĞÜ," + "ASDFGHJKLŞİ" + "ZXCVBNMÖÇ."},
    };

    /** printable: a letter, digit or sign key, the ones that differ between languages. */
    record Cap(InputConstants.Key key, float x, float y, float w, float h, String label, boolean printable) {}

    private KeyLayout() {}

    static float width(boolean numpad) {
        return mouseX(numpad) + 3;
    }

    static float mouseX(boolean numpad) {
        return numpad ? 22.75F : 18.5F;
    }

    /** The scancode of a printable key by its English label. */
    static int code(char c) {
        if (c >= 'A' && c <= 'Z') return InputConstants.KEY_A + (c - 'A');
        if (c >= '1' && c <= '9') return InputConstants.KEY_1 + (c - '1');
        return switch (c) {
            case '0' -> InputConstants.KEY_0;
            case '-' -> InputConstants.KEY_MINUS;
            case '=' -> InputConstants.KEY_EQUALS;
            case '[' -> InputConstants.KEY_LBRACKET;
            case ']' -> InputConstants.KEY_RBRACKET;
            case '\\' -> InputConstants.KEY_BACKSLASH;
            case ';' -> InputConstants.KEY_SEMICOLON;
            case '\'' -> InputConstants.KEY_APOSTROPHE;
            case '`' -> InputConstants.KEY_GRAVE;
            case ',' -> InputConstants.KEY_COMMA;
            case '.' -> InputConstants.KEY_PERIOD;
            case '/' -> InputConstants.KEY_SLASH;
            default -> throw new IllegalArgumentException(String.valueOf(c));
        };
    }

    /**
     * The small second label of a printable key: what the key types in the game's language. Null when the game is in
     * English or the letter is the same. Languages without a table here fall back to the system layout.
     */
    static String secondary(String language, Cap cap) {
        if (!cap.printable() || language == null) return null;
        String lang = language.toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) return null;
        String other = null;
        boolean known = false;
        for (String[] table : LANGUAGES) {
            if (lang.startsWith(table[0]) && table[1].length() == BASE.length()) {
                other = String.valueOf(table[1].charAt(BASE.indexOf(cap.label().charAt(0))));
                known = true;
                break;
            }
        }
        if (!known) {
            String system = cap.key().getDisplayName().getString().toUpperCase(Locale.ROOT);
            if (system.length() == 1) other = system;
        }
        return other == null || other.equalsIgnoreCase(cap.label()) ? null : other;
    }

    static List<Cap> build(boolean numpad) {
        List<Cap> caps = new ArrayList<>();
        // function row
        key(caps, InputConstants.KEY_ESCAPE, 0, 0, 1, "Esc");
        for (int i = 0; i < 12; i++) key(caps, InputConstants.KEY_F1 + i, 2 + i + (i / 4) * 0.5F, 0, 1, "F" + (i + 1));
        key(caps, InputConstants.KEY_PRINTSCREEN, 15.25F, 0, 1, "PrtSc");
        key(caps, InputConstants.KEY_SCROLLLOCK, 16.25F, 0, 1, "ScrLk");
        key(caps, InputConstants.KEY_PAUSE, 17.25F, 0, 1, "Pause");
        // digits and letters
        row(caps, 0, 1.25F, "`1234567890-=");
        key(caps, InputConstants.KEY_BACKSPACE, 13, 1.25F, 2, "Bksp");
        key(caps, InputConstants.KEY_TAB, 0, 2.25F, 1.5F, "Tab");
        row(caps, 1.5F, 2.25F, "QWERTYUIOP[]");
        caps.add(new Cap(Compat.KEYBOARD.getOrCreate(InputConstants.KEY_BACKSLASH), 13.5F, 2.25F, 1.5F, 1, "\\", true));
        key(caps, InputConstants.KEY_CAPSLOCK, 0, 3.25F, 1.75F, "Caps");
        row(caps, 1.75F, 3.25F, "ASDFGHJKL;'");
        key(caps, InputConstants.KEY_RETURN, 12.75F, 3.25F, 2.25F, "Enter");
        key(caps, InputConstants.KEY_LSHIFT, 0, 4.25F, 2.25F, "Shift");
        row(caps, 2.25F, 4.25F, "ZXCVBNM,./");
        key(caps, InputConstants.KEY_RSHIFT, 12.25F, 4.25F, 2.75F, "Shift");
        // bottom row
        key(caps, InputConstants.KEY_LCONTROL, 0, 5.25F, 1.25F, "Ctrl");
        key(caps, Compat.LGUI, 1.25F, 5.25F, 1.25F, "Win");
        key(caps, InputConstants.KEY_LALT, 2.5F, 5.25F, 1.25F, "Alt");
        key(caps, InputConstants.KEY_SPACE, 3.75F, 5.25F, 6.25F, "Space");
        key(caps, InputConstants.KEY_RALT, 10, 5.25F, 1.25F, "Alt");
        key(caps, Compat.RGUI, 11.25F, 5.25F, 1.25F, "Win");
        key(caps, Compat.MENU, 12.5F, 5.25F, 1.25F, "Menu");
        key(caps, InputConstants.KEY_RCONTROL, 13.75F, 5.25F, 1.25F, "Ctrl");
        // navigation
        key(caps, InputConstants.KEY_INSERT, 15.25F, 1.25F, 1, "Ins");
        key(caps, InputConstants.KEY_HOME, 16.25F, 1.25F, 1, "Home");
        key(caps, InputConstants.KEY_PAGEUP, 17.25F, 1.25F, 1, "PgUp");
        key(caps, InputConstants.KEY_DELETE, 15.25F, 2.25F, 1, "Del");
        key(caps, InputConstants.KEY_END, 16.25F, 2.25F, 1, "End");
        key(caps, InputConstants.KEY_PAGEDOWN, 17.25F, 2.25F, 1, "PgDn");
        key(caps, InputConstants.KEY_UP, 16.25F, 4.25F, 1, "↑");
        key(caps, InputConstants.KEY_LEFT, 15.25F, 5.25F, 1, "←");
        key(caps, InputConstants.KEY_DOWN, 16.25F, 5.25F, 1, "↓");
        key(caps, InputConstants.KEY_RIGHT, 17.25F, 5.25F, 1, "→");
        if (numpad) {
            float n = 18.5F;
            key(caps, InputConstants.KEY_NUMLOCK, n, 1.25F, 1, "Num");
            key(caps, Compat.KP_DIVIDE, n + 1, 1.25F, 1, "/");
            key(caps, InputConstants.KEY_MULTIPLY, n + 2, 1.25F, 1, "*");
            key(caps, Compat.KP_SUBTRACT, n + 3, 1.25F, 1, "-");
            for (int i = 0; i < 9; i++) key(caps, InputConstants.KEY_NUMPAD1 + i, n + i % 3, 4.25F - i / 3, 1, String.valueOf(i + 1));
            caps.add(new Cap(Compat.KEYBOARD.getOrCreate(InputConstants.KEY_ADD), n + 3, 2.25F, 1, 2, "+", false));
            caps.add(new Cap(Compat.KEYBOARD.getOrCreate(InputConstants.KEY_NUMPADENTER), n + 3, 4.25F, 1, 2, "Ent", false));
            key(caps, InputConstants.KEY_NUMPAD0, n, 5.25F, 2, "0");
            key(caps, Compat.KP_DECIMAL, n + 2, 5.25F, 1, ".");
        }
        float m = mouseX(numpad);
        mouse(caps, InputConstants.MOUSE_BUTTON_LEFT, m, 1.25F, 1, 1.5F, "beezzkeys.mouse.left");
        mouse(caps, InputConstants.MOUSE_BUTTON_MIDDLE, m + 1, 1.25F, 1, 1.5F, "beezzkeys.mouse.middle");
        mouse(caps, InputConstants.MOUSE_BUTTON_RIGHT, m + 2, 1.25F, 1, 1.5F, "beezzkeys.mouse.right");
        mouse(caps, InputConstants.MOUSE_BUTTON_4, m, 2.75F, 1.5F, 1, "beezzkeys.mouse.4");
        mouse(caps, InputConstants.MOUSE_BUTTON_5, m + 1.5F, 2.75F, 1.5F, 1, "beezzkeys.mouse.5");
        return caps;
    }

    private static void key(List<Cap> caps, int code, float x, float y, float w, String label) {
        caps.add(new Cap(Compat.KEYBOARD.getOrCreate(code), x, y, w, 1, label, false));
    }

    private static void row(List<Cap> caps, float x, float y, String labels) {
        for (int i = 0; i < labels.length(); i++) {
            char c = labels.charAt(i);
            caps.add(new Cap(Compat.KEYBOARD.getOrCreate(code(c)), x + i, y, 1, 1, String.valueOf(c), true));
        }
    }

    private static void mouse(List<Cap> caps, int button, float x, float y, float w, float h, String label) {
        caps.add(new Cap(InputConstants.Type.MOUSE.getOrCreate(button), x, y, w, h, I18n.get(label), false));
    }
}
