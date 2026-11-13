package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The player's own command for a key: the text, and whether Ctrl, Shift or Alt must be held. */
final class CommandScreen extends Screen {
    private final KeyboardScreen parent;
    private final InputConstants.Key key;
    private final Config.Macro existing;
    private final String keyLabel;
    private int mods;
    private String text;
    private EditBox box;
    private Button save;

    CommandScreen(KeyboardScreen parent, InputConstants.Key key, String keyLabel, int mods, Config.Macro existing) {
        super(Component.translatable("beezzkeys.command.title"));
        this.parent = parent;
        this.key = key;
        this.keyLabel = keyLabel;
        this.existing = existing;
        this.mods = existing != null ? existing.mods : mods & ~Config.modBit(key);
        this.text = existing != null ? existing.command : "";
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        box = new EditBox(font, cx - 150, y, 300, 20, Component.translatable("beezzkeys.command.hint"));
        box.setMaxLength(256);
        box.setHint(Component.translatable("beezzkeys.command.hint"));
        box.setValue(text);
        box.setResponder(s -> {
            text = s;
            if (save != null) save.active = !s.isBlank();
        });
        addRenderableWidget(box);
        setInitialFocus(box);

        String[] names = {"Ctrl", "Shift", "Alt"};
        int own = Config.modBit(key);
        for (int i = 0; i < 3; i++) {
            int bit = 1 << i;
            Button b = Button.builder(CommonComponents.optionStatus(Component.literal(names[i]), (mods & bit) != 0), btn -> {
                mods ^= bit;
                rebuildWidgets();
            }).bounds(cx - 150 + i * 102, y + 28, 96, 20).build();
            b.active = bit != own;
            addRenderableWidget(b);
        }

        boolean edit = existing != null;
        int bw = edit ? 96 : 147;
        save = addRenderableWidget(Button.builder(Component.translatable("beezzkeys.command.save"), b -> save())
                .bounds(cx - 150, y + 60, bw, 20).build());
        save.active = !text.isBlank();
        if (edit) {
            addRenderableWidget(Button.builder(Component.translatable("beezzkeys.command.delete"), b -> {
                parent.saveMacro(existing, null);
                onClose();
            }).bounds(cx - 48, y + 60, 96, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(cx + 150 - bw, y + 60, bw, 20).build());
    }

    private void save() {
        if (text.isBlank()) return;
        parent.saveMacro(existing, new Config.Macro(key.getName(), mods, text.trim()));
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        int cx = width / 2, y = height / 2 - 40;
        g.centeredText(font, title, cx, y - 34, 0xFFFFFFFF);
        g.centeredText(font, Component.literal(Config.modsText(mods) + keyLabel), cx, y - 18, 0xFFFFD84A);
        g.centeredText(font, Component.translatable("beezzkeys.command.note"), cx, y + 90, 0xFF9A9AA4);
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }
}
