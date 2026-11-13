package land.beezz.keys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * config/beezzkeys.json: the player's own commands, the categories hidden from the list and the numpad switch.
 * Named sets of key binds are separate files in config/beezzkeys/presets, so sharing one is copying a file.
 */
final class Config {
    static final int CTRL = 1, SHIFT = 2, ALT = 4;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** A command or a chat line sent when the key is pressed with exactly these modifiers. */
    static final class Macro {
        String key;
        int mods;
        String command;

        Macro(String key, int mods, String command) {
            this.key = key;
            this.mods = mods;
            this.command = command;
        }

        InputConstants.Key keyObj() {
            return InputConstants.getKey(key);
        }

        Macro copy() {
            return new Macro(key, mods, command);
        }
    }

    static final class Preset {
        Map<String, String> keys = new LinkedHashMap<>();
        List<Macro> macros = new ArrayList<>();
    }

    List<Macro> macros = new ArrayList<>();
    /** Category ids left out of the list; null until the player changes the choice for the first time. */
    Set<String> hidden;
    boolean numpad = true;

    static Config get() {
        if (instance == null) {
            try {
                instance = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Config.class);
            } catch (Exception e) {
                instance = null;
            }
            if (instance == null) instance = new Config();
            if (instance.macros == null) instance.macros = new ArrayList<>();
            instance.macros.removeIf(m -> m == null || m.key == null || m.command == null);
        }
        return instance;
    }

    void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("beezzkeys.json");
    }

    static String modsText(int mods) {
        return ((mods & CTRL) != 0 ? "Ctrl+" : "") + ((mods & SHIFT) != 0 ? "Shift+" : "") + ((mods & ALT) != 0 ? "Alt+" : "");
    }

    /** The modifier a key is by itself, or 0. */
    static int modBit(InputConstants.Key key) {
        if (key.getType() != Compat.KEYBOARD) return 0;
        int v = key.getValue();
        if (v == InputConstants.KEY_LCONTROL || v == InputConstants.KEY_RCONTROL) return CTRL;
        if (v == InputConstants.KEY_LSHIFT || v == InputConstants.KEY_RSHIFT) return SHIFT;
        if (v == InputConstants.KEY_LALT || v == InputConstants.KEY_RALT) return ALT;
        return 0;
    }

    // ---- presets ----

    static Path presetsDir() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("beezzkeys").resolve("presets");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    static List<String> presetNames() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(presetsDir())) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(n -> names.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) {
        }
        return names;
    }

    static String cleanPresetName(String name) {
        String s = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        return s.length() > 32 ? s.substring(0, 32).trim() : s;
    }

    Preset snapshot() {
        Preset p = new Preset();
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) p.keys.put(m.getName(), m.saveString());
        for (Macro m : macros) p.macros.add(m.copy());
        return p;
    }

    static boolean savePreset(String name, Preset preset) {
        try {
            Files.writeString(presetsDir().resolve(name + ".json"), GSON.toJson(preset), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static Preset loadPreset(String name) {
        try {
            Preset p = GSON.fromJson(Files.readString(presetsDir().resolve(name + ".json"), StandardCharsets.UTF_8), Preset.class);
            if (p == null) return null;
            if (p.keys == null) p.keys = new LinkedHashMap<>();
            if (p.macros == null) p.macros = new ArrayList<>();
            p.macros.removeIf(m -> m == null || m.key == null || m.command == null);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    static void deletePreset(String name) {
        try {
            Files.deleteIfExists(presetsDir().resolve(name + ".json"));
        } catch (IOException ignored) {
        }
    }
}
