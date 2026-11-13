package land.beezz.keys;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Named sets of key binds: save the current one, load or delete a saved one. Each set is a file, see {@link Config}. */
final class PresetsScreen extends Screen {
    private final KeyboardScreen parent;
    private String name = "";
    private Button save;
    private int hiddenRows;
    private Component message;

    PresetsScreen(KeyboardScreen parent) {
        super(Component.translatable("beezzkeys.presets.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        EditBox box = new EditBox(font, cx - 154, 34, 180, 20, Component.translatable("beezzkeys.presets.name"));
        box.setMaxLength(32);
        box.setHint(Component.translatable("beezzkeys.presets.name"));
        box.setValue(name);
        box.setResponder(s -> {
            name = s;
            if (save != null) save.active = !Config.cleanPresetName(s).isEmpty();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        save = addRenderableWidget(Button.builder(Component.translatable("beezzkeys.presets.save"), b -> {
            String clean = Config.cleanPresetName(name);
            if (clean.isEmpty()) return;
            boolean ok = Config.savePreset(clean, Config.get().snapshot());
            message = Component.translatable(ok ? "beezzkeys.presets.saved" : "beezzkeys.presets.failed", clean);
            name = "";
            rebuildWidgets();
        }).bounds(cx + 30, 34, 124, 20).build());
        save.active = !Config.cleanPresetName(name).isEmpty();

        List<String> names = Config.presetNames();
        int rows = Math.max(1, (height - 76 - 44) / 24);
        hiddenRows = Math.max(0, names.size() - rows);
        for (int i = 0; i < Math.min(rows, names.size()); i++) {
            String preset = names.get(i);
            int y = 76 + i * 24;
            addRenderableWidget(Button.builder(Component.literal(preset), b -> {
                Config.Preset p = Config.loadPreset(preset);
                if (p == null) {
                    message = Component.translatable("beezzkeys.presets.failed", preset);
                    return;
                }
                parent.applyPreset(p);
                onClose();
            }).bounds(cx - 154, y, 280, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                Config.deletePreset(preset);
                message = Component.translatable("beezzkeys.presets.deleted", preset);
                rebuildWidgets();
            }).bounds(cx + 130, y, 24, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("beezzkeys.presets.folder"), b -> openFolder())
                .bounds(cx - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(cx + 4, height - 28, 150, 20).build());
    }

    private void openFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String tool = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
        try {
            new ProcessBuilder(tool, Config.presetsDir().toString()).start();
        } catch (IOException e) {
            message = Component.literal(Config.presetsDir().toString());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        Component line = message != null ? message
                : Config.presetNames().isEmpty() ? Component.translatable("beezzkeys.presets.empty")
                : Component.translatable("beezzkeys.presets.pick");
        g.centeredText(font, line, width / 2, 61, message != null ? 0xFFFFD84A : 0xFF9A9AA4);
        if (hiddenRows > 0) {
            g.centeredText(font, Component.translatable("beezzkeys.presets.more", hiddenRows), width / 2, height - 42, 0xFF9A9AA4);
        }
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }
}
