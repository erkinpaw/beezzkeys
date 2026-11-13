package land.beezz.keys;

import com.mojang.blaze3d.platform.InputConstants;
import land.beezz.keys.KeyLayout.Cap;
import land.beezz.keys.PopupMenu.Item;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Key binds as a drawn keyboard. A key takes the colour of the category of its action, a free key stays grey, a key with
 * a conflict is red. Click an action, then press a key or click it on the keyboard (or drag the action onto the key).
 * A right click on a key opens its menu: the player's own command, or an action of any category to put on this key.
 * Holding Ctrl, Shift or Alt shows the commands bound to combinations with them. The "+" under the keyboard chooses the
 * categories the list shows; the gear holds the named sets, the numpad switch, undo and the vanilla list.
 */
public final class KeyboardScreen extends Screen {
    private static final int[] PALETTE = {0xFF8E6CFF, 0xFF3D9BFF, 0xFFFF9A3D, 0xFF2EC4B6, 0xFFFF6FB5, 0xFFE6C84A, 0xFFB08968, 0xFF9AA5CE};
    private static final int FREE_FILL = 0xFF26262B, FREE_EDGE = 0xFF45454D, FREE_TEXT = 0xFF8B8B95;
    private static final int CONFLICT = 0xFFE5484D, GREEN = 0xFF4ADE80, YELLOW = 0xFFFFD84A, WHITE = 0xFFFFFFFF;
    private static final int COMMAND_COLOR = 0xFFDADAE4;
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT, RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int ROW = 14, KEY_BOX = 62, ICON = 13;
    private static final String COMMANDS = "beezzkeys:commands";
    private static final InputConstants.Key ESCAPE = Compat.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);

    private record Cat(String id, Component label, int color) {}

    /** A line of the list and an occupant of a key: a game action or the player's own command. */
    private record Row(KeyMapping mapping, Config.Macro macro) {}

    /** kind 0: a category, 1: the "+" button, 2: a counter. */
    private record Chip(int kind, Cat cat, Component text, int color, int x, int y, int w) {}

    private record Snapshot(Map<KeyMapping, InputConstants.Key> keys, List<Config.Macro> macros) {}

    private final Screen parent;
    private final Config config = Config.get();
    private List<Cap> caps = List.of();
    private final Map<InputConstants.Key, Cap> capByKey = new HashMap<>();
    private final Map<InputConstants.Key, String> secondary = new HashMap<>();
    private final KeyMapping[] mappings;
    private final Map<KeyMapping, String> names = new HashMap<>();
    private final List<Cat> cats = new ArrayList<>();
    private final Map<String, Cat> catById = new HashMap<>();
    private final Cat commandsCat;
    private final Map<InputConstants.Key, List<Row>> byKey = new HashMap<>();
    private final Set<InputConstants.Key> conflicts = new HashSet<>();
    private final Set<InputConstants.Key> comboKeys = new HashSet<>();
    private final List<Row> visible = new ArrayList<>();
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private List<Chip> chips = List.of();

    private EditBox searchBox;
    private String search = "";
    private KeyMapping listening;
    private InputConstants.Key pinned;
    private PopupMenu menu;
    private int heldMods;
    private double scroll;
    private boolean dragging;
    private double dragX, dragY;

    private float unit, layoutW, mouseX;
    private int kbX, kbY, gap, legendY, hintY, hintLines, listTop, listBottom, cols, colW;

    public KeyboardScreen(Screen parent) {
        super(Component.translatable("beezzkeys.title"));
        this.parent = parent;
        mappings = minecraft.options.keyMappings.clone();
        Arrays.sort(mappings);
        for (KeyMapping m : mappings) {
            names.put(m, Component.translatable(m.getName()).getString());
            String id = m.getCategory().id().toString();
            if (!catById.containsKey(id)) {
                Cat cat = new Cat(id, m.getCategory().label(), PALETTE[cats.size() % PALETTE.length]);
                cats.add(cat);
                catById.put(id, cat);
            }
        }
        commandsCat = new Cat(COMMANDS, Component.translatable("beezzkeys.category.commands"), COMMAND_COLOR);
        cats.add(commandsCat);
        catById.put(COMMANDS, commandsCat);
    }

    @Override
    protected void init() {
        heldMods = 0;
        menu = null;
        caps = KeyLayout.build(config.numpad);
        layoutW = KeyLayout.width(config.numpad);
        mouseX = KeyLayout.mouseX(config.numpad);
        capByKey.clear();
        secondary.clear();
        for (Cap c : caps) {
            capByKey.put(c.key(), c);
            String second = KeyLayout.secondary(minecraft.options.languageCode, c);
            if (second != null) secondary.put(c.key(), second);
        }

        addRenderableWidget(Button.builder(Component.literal("⚙"), b -> menu = gearMenu()).bounds(6, 5, 20, 20).build());
        int searchW = Math.min(150, width / 3 - 20);
        searchBox = new EditBox(font, width - 6 - searchW, 6, searchW, 18, Component.translatable("beezzkeys.search"));
        searchBox.setHint(Component.translatable("beezzkeys.search"));
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s;
            scroll = 0;
            refilter();
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.translatable("beezzkeys.button.reset_all"), b -> {
            pushUndo();
            for (KeyMapping m : mappings) m.setKey(m.getDefaultKey());
            listening = null;
            changed();
        }).bounds(width / 2 - 154, height - 26, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 4, height - 26, 150, 20).build());

        rebuild();
        layout();
    }

    private void layout() {
        kbY = 32;
        hintLines = 1;
        int legendLines = layoutLegend(0);
        int below = 6 + legendLines * 11 + 2 + hintLines * 10 + 4;
        unit = Math.min((width - 12) / layoutW, (height - kbY - below - 4 * ROW - 32) / KeyLayout.HEIGHT);
        unit = Math.max(9, Math.min(26, unit));
        gap = unit >= 16 ? 2 : 1;
        kbX = Math.round((width - layoutW * unit) / 2) + gap / 2;
        legendY = kbY + Math.round(KeyLayout.HEIGHT * unit) + 6;
        hintY = legendY + legendLines * 11 + 2;
        listTop = hintY + hintLines * 10 + 4;
        listBottom = height - 32;
        cols = Math.max(1, (width - 20) / 200);
        colW = (width - 20) / cols;
        clampScroll();
    }

    // ---- state ----

    private static InputConstants.Key keyOf(KeyMapping m) {
        return KeyMappingHelper.getBoundKeyOf(m);
    }

    private Cat catOf(Row row) {
        return row.macro != null ? commandsCat : catById.get(row.mapping.getCategory().id().toString());
    }

    private Set<String> hidden() {
        if (config.hidden == null) {
            config.hidden = new HashSet<>(List.of(KeyMapping.Category.CREATIVE.id().toString(),
                    KeyMapping.Category.SPECTATOR.id().toString(), KeyMapping.Category.DEBUG.id().toString()));
        }
        return config.hidden;
    }

    private boolean shown(Cat cat) {
        return !hidden().contains(cat.id);
    }

    private void rebuild() {
        byKey.clear();
        conflicts.clear();
        comboKeys.clear();
        if (heldMods == 0) {
            for (KeyMapping m : mappings) {
                if (!m.isUnbound()) byKey.computeIfAbsent(keyOf(m), k -> new ArrayList<>()).add(new Row(m, null));
            }
        }
        for (Config.Macro macro : config.macros) {
            if (macro.mods == heldMods) byKey.computeIfAbsent(macro.keyObj(), k -> new ArrayList<>()).add(new Row(null, macro));
            else if (heldMods == 0) comboKeys.add(macro.keyObj());
        }
        // the game's own rule: two actions on one key clash unless both sit on their default keys
        byKey.forEach((key, list) -> {
            if (list.size() > 1 && list.stream().anyMatch(r -> r.macro != null || !r.mapping.isDefault())) conflicts.add(key);
        });
        refilter();
    }

    private void refilter() {
        visible.clear();
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Row> onPinned = pinned != null ? byKey.get(pinned) : null;
        List<Row> all = new ArrayList<>();
        for (KeyMapping m : mappings) all.add(new Row(m, null));
        for (Config.Macro macro : config.macros) all.add(new Row(null, macro));
        for (Row row : all) {
            if (onPinned != null) {
                if (!onPinned.contains(row)) continue;
            } else if (!q.isEmpty()) {
                if (!rowName(row).toLowerCase(Locale.ROOT).contains(q) && !rowKey(row).toLowerCase(Locale.ROOT).contains(q)
                        && !catOf(row).label.getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            } else if (!shown(catOf(row))) {
                continue;
            }
            visible.add(row);
        }
        clampScroll();
    }

    private String rowName(Row row) {
        return row.macro != null ? row.macro.command : names.get(row.mapping);
    }

    private String rowKey(Row row) {
        if (row.macro != null) return Config.modsText(row.macro.mods) + keyLabel(row.macro.keyObj());
        return row.mapping.isUnbound() ? Component.translatable("beezzkeys.none").getString() : keyLabel(keyOf(row.mapping));
    }

    /** The English letter for letter keys, the game's own name for the rest. */
    private String keyLabel(InputConstants.Key key) {
        Cap cap = capByKey.get(key);
        return cap != null && cap.printable() ? cap.label() : key.getDisplayName().getString();
    }

    private void changed() {
        KeyMapping.resetMapping();
        rebuild();
    }

    private void pushUndo() {
        Map<KeyMapping, InputConstants.Key> keys = new HashMap<>();
        for (KeyMapping m : mappings) keys.put(m, m.isUnbound() ? InputConstants.UNKNOWN : keyOf(m));
        List<Config.Macro> macros = new ArrayList<>();
        for (Config.Macro m : config.macros) macros.add(m.copy());
        undo.push(new Snapshot(keys, macros));
        if (undo.size() > 50) undo.removeLast();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        Snapshot s = undo.pop();
        s.keys.forEach(KeyMapping::setKey);
        config.macros = new ArrayList<>(s.macros);
        config.save();
        listening = null;
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        changed();
    }

    private void bind(KeyMapping m, InputConstants.Key key) {
        pushUndo();
        m.setKey(key);
        listening = null;
        dragging = false;
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        changed();
    }

    /** Replaces a command (old may be null) with a new one (null deletes). One key with one set of modifiers holds one command. */
    void saveMacro(Config.Macro old, Config.Macro replacement) {
        pushUndo();
        config.macros.remove(old);
        if (replacement != null) {
            config.macros.removeIf(m -> m.mods == replacement.mods && m.key.equals(replacement.key));
            config.macros.add(replacement);
        }
        config.save();
        rebuild();
    }

    void applyPreset(Config.Preset preset) {
        pushUndo();
        for (KeyMapping m : mappings) {
            String saved = preset.keys.get(m.getName());
            if (saved == null) continue;
            try {
                m.setKey(InputConstants.getKey(saved));
            } catch (RuntimeException ignored) {
            }
        }
        config.macros = new ArrayList<>();
        for (Config.Macro m : preset.macros) config.macros.add(m.copy());
        config.save();
        changed();
    }

    private void clampScroll() {
        int rows = cols == 0 ? 0 : (visible.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows * ROW - (listBottom - listTop))));
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // ---- menus ----

    private PopupMenu popup(int x, int y, List<Item> items) {
        return new PopupMenu(font, x, y, items, width, height);
    }

    private PopupMenu gearMenu() {
        List<Item> items = new ArrayList<>();
        items.add(Item.of(Component.translatable("beezzkeys.menu.presets"), () -> Compat.setScreen(minecraft, new PresetsScreen(this))));
        items.add(new Item(Component.translatable("beezzkeys.menu.numpad"), 0, () -> config.numpad, null, () -> {
            config.numpad = !config.numpad;
            config.save();
            rebuildWidgets();
        }, false));
        items.add(new Item(Component.translatable("beezzkeys.menu.undo"), 0, null, "Ctrl+Z", undo.isEmpty() ? null : this::undo, false));
        items.add(Item.separator());
        items.add(Item.of(Component.translatable("beezzkeys.menu.vanilla"), () -> BeezzKeys.openVanilla(parent)));
        return popup(6, 27, items);
    }

    private PopupMenu categoryMenu(int x, int y) {
        List<Item> items = new ArrayList<>();
        items.add(Item.caption(Component.translatable("beezzkeys.menu.categories")));
        for (Cat cat : cats) {
            items.add(new Item(cat.label, cat.color, () -> shown(cat), null, () -> {
                if (!hidden().remove(cat.id)) hidden().add(cat.id);
                config.save();
                scroll = 0;
                refilter();
                layout();
            }, true));
        }
        return popup(x, y, items);
    }

    private PopupMenu capMenu(Cap cap, int x, int y) {
        InputConstants.Key key = cap.key();
        List<Row> on = byKey.get(key);
        List<Item> items = new ArrayList<>();
        items.add(Item.caption(Component.literal(Config.modsText(heldMods) + keyLabel(key))));
        if (key.getType() == Compat.KEYBOARD) {
            Config.Macro existing = null;
            if (on != null) for (Row row : on) if (row.macro != null) existing = row.macro;
            Config.Macro macro = existing;
            int mods = heldMods;
            items.add(Item.of(Component.translatable(macro == null ? "beezzkeys.menu.command" : "beezzkeys.menu.command_edit"),
                    () -> Compat.setScreen(minecraft, new CommandScreen(this, key, keyLabel(key), mods, macro))));
        }
        if (on != null) {
            items.add(Item.of(Component.translatable("beezzkeys.menu.clear"), () -> {
                pushUndo();
                for (Row row : on) {
                    if (row.mapping != null) row.mapping.setKey(InputConstants.UNKNOWN);
                    else config.macros.remove(row.macro);
                }
                config.save();
                changed();
            }));
        }
        if (heldMods == 0) {
            items.add(Item.separator());
            items.add(Item.caption(Component.translatable("beezzkeys.menu.assign")));
            for (Cat cat : cats) {
                if (cat == commandsCat) continue;
                items.add(new Item(cat.label, cat.color, null, "›", () -> menu = actionsMenu(cap, cat, x, y), true));
            }
        }
        return popup(x, y, items);
    }

    private PopupMenu actionsMenu(Cap cap, Cat cat, int x, int y) {
        List<Item> items = new ArrayList<>();
        items.add(new Item(Component.literal("‹ ").append(cat.label), 0, null, null, () -> menu = capMenu(cap, x, y), true));
        items.add(Item.separator());
        for (KeyMapping m : mappings) {
            if (!m.getCategory().id().toString().equals(cat.id)) continue;
            String now = m.isUnbound() ? "—" : keyLabel(keyOf(m));
            items.add(new Item(Component.literal(names.get(m)), 0, null, now, () -> bind(m, cap.key()), false));
        }
        return popup(x, y, items);
    }

    // ---- geometry ----

    private int capX0(Cap c) { return kbX + Math.round(c.x() * unit); }
    private int capY0(Cap c) { return kbY + Math.round(c.y() * unit); }
    private int capX1(Cap c) { return kbX + Math.round((c.x() + c.w()) * unit) - gap; }
    private int capY1(Cap c) { return kbY + Math.round((c.y() + c.h()) * unit) - gap; }

    private Cap capAt(double mx, double my) {
        for (Cap c : caps) {
            if (mx >= capX0(c) && mx < capX1(c) && my >= capY0(c) && my < capY1(c)) return c;
        }
        return null;
    }

    private boolean isOverWidget(double mx, double my) {
        return my < kbY - 5 || my >= height - 30;
    }

    private int rowAt(double mx, double my) {
        if (my < listTop || my >= listBottom || mx < 10 || mx >= 10 + cols * colW) return -1;
        int col = (int) ((mx - 10) / colW);
        if (mx - 10 - col * colW >= colW - 4) return -1;
        int index = (int) ((my - listTop + scroll) / ROW) * cols + col;
        return index >= 0 && index < visible.size() ? index : -1;
    }

    /** 0: the row itself, 1: reset, 2: unbind. */
    private int zoneAt(double mx) {
        int col = (int) ((mx - 10) / colW);
        int right = 10 + col * colW + colW - 4;
        return mx >= right - ICON ? 2 : mx >= right - 2 * ICON ? 1 : 0;
    }

    /** Lays out the row under the keyboard: the chosen categories, the "+" and the counters. Returns the number of lines. */
    private int layoutLegend(int y) {
        int free = 0;
        for (Cap c : caps) if (!byKey.containsKey(c.key()) && !c.key().equals(ESCAPE)) free++;
        List<Chip> list = new ArrayList<>();
        int x = 10, lines = 1;
        List<Cat> shownCats = new ArrayList<>();
        for (Cat cat : cats) if (shown(cat)) shownCats.add(cat);
        int total = shownCats.size() + 3;
        for (int i = 0; i < total; i++) {
            int kind = i < shownCats.size() ? 0 : i == shownCats.size() ? 1 : 2;
            Cat cat = kind == 0 ? shownCats.get(i) : null;
            Component text = kind == 0 ? cat.label : kind == 1 ? Component.literal("+")
                    : i == total - 2 ? Component.translatable("beezzkeys.legend.free", free)
                    : Component.translatable("beezzkeys.legend.conflict", conflicts.size());
            int color = kind == 0 ? cat.color : kind == 1 ? WHITE : i == total - 2 ? FREE_EDGE : CONFLICT;
            int w = kind == 1 ? 22 : 11 + font.width(text) + 10;
            if (x + w > width - 10 && x > 10) {
                x = 10;
                y += 11;
                lines++;
            }
            list.add(new Chip(kind, cat, text, color, x, y, w));
            x += w;
        }
        chips = list;
        return lines;
    }

    private Chip chipAt(double mx, double my) {
        for (Chip chip : chips) {
            if (chip.kind != 2 && mx >= chip.x - 2 && mx < chip.x + chip.w - 6 && my >= chip.y - 1 && my < chip.y + 10) return chip;
        }
        return null;
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        g.centeredText(font, title, width / 2, 11, WHITE);
        boolean free = menu == null && !dragging;

        int right = kbX + Math.round(layoutW * unit);
        g.fill(kbX - 5, kbY - 5, right + 3, kbY + Math.round(KeyLayout.HEIGHT * unit) + 3, 0x70000000);
        fit(g, Component.translatable("beezzkeys.mouse").getString(), kbX + Math.round((mouseX + 1.5F) * unit),
                kbY + Math.round(unit / 2), Math.round(3 * unit), FREE_TEXT);
        Cap hovered = menu == null && (dragging || !isOverWidget(mx, my)) ? capAt(mx, my) : null;
        InputConstants.Key current = listening != null && !listening.isUnbound() ? keyOf(listening) : null;
        for (Cap c : caps) cap(g, c, c == hovered, current);

        layoutLegend(legendY);
        Chip hoverChip = free ? chipAt(mx, my) : null;
        for (Chip chip : chips) {
            if (chip == hoverChip) g.fill(chip.x - 2, chip.y - 1, chip.x + chip.w - 6, chip.y + 10, 0x40FFFFFF);
            if (chip.kind == 1) {
                g.fill(chip.x, chip.y, chip.x + 14, chip.y + 10, 0xFF55555C);
                g.fill(chip.x + 1, chip.y + 1, chip.x + 13, chip.y + 9, FREE_FILL);
                g.fill(chip.x + 4, chip.y + 4, chip.x + 10, chip.y + 6, WHITE);
                g.fill(chip.x + 6, chip.y + 2, chip.x + 8, chip.y + 8, WHITE);
            } else {
                g.fill(chip.x, chip.y + 1, chip.x + 8, chip.y + 9, chip.color);
                g.text(font, chip.text, chip.x + 11, chip.y + 1, chip.kind == 2 ? 0xFFB8B8C0 : 0xFFDDDDDD, false);
            }
        }
        if (hoverChip != null) g.setTooltipForNextFrame(font, Component.translatable("beezzkeys.tip.categories"), mx, my);

        List<FormattedCharSequence> hint = font.split(hint(), width - 20);
        for (int i = 0; i < Math.min(hint.size(), hintLines); i++) {
            g.centeredText(font, hint.get(i), width / 2, hintY + i * 10, listening != null || heldMods != 0 ? YELLOW : 0xFFB8B8C0);
        }

        list(g, mx, my, free);

        if (hovered != null && free) g.setComponentTooltipForNextFrame(font, capTooltip(hovered), mx, my);
        if (dragging && listening != null && Math.abs(mx - dragX) + Math.abs(my - dragY) > 4) {
            String name = names.get(listening);
            int w = font.width(name);
            g.fill(mx + 6, my - 6, mx + 14 + w, my + 7, 0xF0101014);
            g.fill(mx + 6, my - 6, mx + 9, my + 7, catOf(new Row(listening, null)).color);
            g.text(font, name, mx + 11, my - 3, WHITE, false);
        }
        if (menu != null) {
            g.nextStratum();
            menu.render(g, font, mx, my);
        }
    }

    private void cap(GuiGraphicsExtractor g, Cap c, boolean hover, InputConstants.Key current) {
        InputConstants.Key key = c.key();
        List<Row> on = byKey.get(key);
        boolean reserved = key.equals(ESCAPE);
        int fill = FREE_FILL, edge = FREE_EDGE, text = FREE_TEXT;
        if (reserved) {
            fill = 0xFF19191C;
            edge = 0xFF303036;
            text = 0xFF5C5C66;
        } else if (on != null) {
            int base = conflicts.contains(key) ? CONFLICT : catOf(on.get(0)).color;
            fill = mix(base, 0xFF000000, 0.45F);
            edge = base;
            text = WHITE;
        } else if (listening != null) {
            fill = 0xFF1D3326;
            edge = GREEN;
            text = 0xFFC4F7D4;
        }
        if (hover && !reserved) fill = mix(fill, WHITE, 0.18F);
        int x0 = capX0(c), y0 = capY0(c), x1 = capX1(c), y1 = capY1(c);
        if (key.equals(pinned)) g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, YELLOW);
        else if (key.equals(current)) g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, WHITE);
        g.fill(x0, y0, x1, y1, edge);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        if (comboKeys.contains(key)) g.fill(x0 + 2, y0 + 2, x0 + 5, y0 + 5, COMMAND_COLOR);
        String second = unit >= 15 ? secondary.get(key) : null;
        if (second == null) {
            fit(g, c.label(), (x0 + x1) / 2, (y0 + y1) / 2, x1 - x0 - 4, text);
        } else {
            // the English letter low on the left, the letter of the game's language small in the upper right corner
            g.text(font, c.label(), x0 + 4, y1 - 10, text, false);
            float s = 0.7F;
            g.pose().pushMatrix();
            g.pose().translate(x1 - 3 - font.width(second) * s, y0 + 2);
            g.pose().scale(s, s);
            g.text(font, second, 0, 0, mix(text, fill, 0.25F), false);
            g.pose().popMatrix();
        }
    }

    /** Text centred on a point and shrunk when it is wider than the room it has. */
    private void fit(GuiGraphicsExtractor g, String text, int cx, int cy, int room, int color) {
        int w = font.width(text);
        if (w <= room) {
            g.text(font, text, cx - w / 2, cy - 4, color, false);
            return;
        }
        float s = Math.max(0.3F, (float) room / w);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        g.text(font, text, -w / 2, -4, color, false);
        g.pose().popMatrix();
    }

    private static final String[] RESET_ICON = {"..#....", ".####..", "######.", ".##..##", "..#..##", ".....##", "..####."};

    /** A hooked arrow drawn by pixels: the font's own glyph is too small to read. */
    private static void resetIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int row = 0; row < RESET_ICON.length; row++) {
            String line = RESET_ICON[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }

    private Component hint() {
        if (listening != null) return Component.translatable("beezzkeys.hint.listen", names.get(listening));
        if (heldMods != 0) return Component.translatable("beezzkeys.hint.mods", Config.modsText(heldMods).replaceAll("[+]$", ""));
        if (pinned != null) {
            List<Row> on = byKey.get(pinned);
            if (on == null) return Component.translatable("beezzkeys.hint.pinned_free", keyLabel(pinned));
            List<String> list = new ArrayList<>();
            for (Row row : on) list.add(rowName(row));
            return Component.translatable("beezzkeys.hint.pinned", keyLabel(pinned), String.join(", ", list));
        }
        return Component.empty();
    }

    private List<Component> capTooltip(Cap c) {
        List<Component> lines = new ArrayList<>();
        String second = secondary.get(c.key());
        String title = Config.modsText(heldMods) + (c.printable() ? c.label() + (second != null ? " (" + second + ")" : "")
                : c.key().getDisplayName().getString());
        lines.add(Component.literal(title).withStyle(ChatFormatting.BOLD));
        List<Row> on = byKey.get(c.key());
        if (c.key().equals(ESCAPE)) {
            lines.add(Component.translatable("beezzkeys.tip.reserved").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (on == null) {
            lines.add(Component.translatable("beezzkeys.tip.free").withStyle(ChatFormatting.GREEN));
        } else {
            for (Row row : on) {
                Cat cat = catOf(row);
                lines.add(Component.literal("■ ").withColor(cat.color)
                        .append(Component.literal(rowName(row)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + cat.label.getString()).withStyle(ChatFormatting.DARK_GRAY)));
            }
            if (conflicts.contains(c.key())) lines.add(Component.translatable("beezzkeys.tip.conflict").withStyle(ChatFormatting.RED));
        }
        if (comboKeys.contains(c.key())) {
            for (Config.Macro m : config.macros) {
                if (m.mods != 0 && m.keyObj().equals(c.key())) {
                    lines.add(Component.literal(Config.modsText(m.mods) + keyLabel(c.key()) + ": " + m.command).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        lines.add(Component.translatable("beezzkeys.tip.menu").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void list(GuiGraphicsExtractor g, int mx, int my, boolean free) {
        g.fill(8, listTop - 2, width - 8, listBottom + 2, 0x50000000);
        if (visible.isEmpty()) {
            boolean none = search.isBlank() && pinned == null;
            g.centeredText(font, Component.translatable(none ? "beezzkeys.empty_categories" : "beezzkeys.empty"), width / 2,
                    (listTop + listBottom) / 2 - 4, FREE_TEXT);
            return;
        }
        int hoverRow = free ? rowAt(mx, my) : -1;
        g.enableScissor(8, listTop, width - 8, listBottom);
        for (int i = 0; i < visible.size(); i++) {
            int y = listTop + (i / cols) * ROW - (int) scroll;
            if (y + ROW <= listTop || y >= listBottom) continue;
            Row row = visible.get(i);
            KeyMapping m = row.mapping;
            boolean macro = row.macro != null;
            int color = catOf(row).color;
            int x = 10 + (i % cols) * colW, w = colW - 4, right = x + w;
            boolean active = !macro && m == listening, hover = i == hoverRow;
            boolean unbound = !macro && m.isUnbound();
            boolean clash = macro ? row.macro.mods == 0 && isClash(row.macro.keyObj()) : !unbound && isClash(keyOf(m));
            g.fill(x, y, right, y + ROW - 1, active ? 0x66FFD84A : hover ? 0x38FFFFFF : 0x24000000);
            g.fill(x, y, x + 3, y + ROW - 1, color);
            int boxX = right - 2 * ICON - KEY_BOX;
            String name = rowName(row);
            int room = boxX - x - 10;
            boolean cut = font.width(name) > room;
            if (cut) name = font.plainSubstrByWidth(name, room - 6) + "…";
            g.text(font, name, x + 6, y + 3, WHITE, false);
            // a name that does not fit is shown in full under the cursor
            if (cut && hover && zoneAt(mx) == 0 && mx < boxX) g.setTooltipForNextFrame(font, Component.literal(rowName(row)), mx, my);

            int edge = active ? YELLOW : clash ? CONFLICT : unbound ? FREE_EDGE : color;
            g.fill(boxX, y + 1, boxX + KEY_BOX, y + ROW - 2, edge);
            g.fill(boxX + 1, y + 2, boxX + KEY_BOX - 1, y + ROW - 3, clash ? mix(CONFLICT, 0xFF000000, 0.6F) : FREE_FILL);
            fit(g, active ? "> ? <" : rowKey(row), boxX + KEY_BOX / 2, y + ROW / 2, KEY_BOX - 6, active ? YELLOW : unbound ? FREE_TEXT : WHITE);

            int zone = hover ? zoneAt(mx) : 0;
            if (!macro) {
                resetIcon(g, right - ICON - ICON / 2 - 3, y + ROW / 2 - 4, m.isDefault() ? 0xFF55555C : zone == 1 ? YELLOW : 0xFFD0D0D8);
            }
            fit(g, "✕", right - ICON / 2, y + ROW / 2, ICON, unbound ? 0xFF55555C : zone == 2 ? CONFLICT : 0xFFD0D0D8);
            if (zone == 1 && !macro && !m.isDefault()) {
                g.setTooltipForNextFrame(font, Component.translatable("beezzkeys.tip.reset", keyLabel(m.getDefaultKey())), mx, my);
            } else if (zone == 2 && !unbound) {
                g.setTooltipForNextFrame(font, Component.translatable(macro ? "beezzkeys.tip.delete" : "beezzkeys.tip.unbind"), mx, my);
            }
        }
        g.disableScissor();
        int rows = (visible.size() + cols - 1) / cols, view = listBottom - listTop, max = rows * ROW - view;
        if (max > 0) {
            int bar = Math.max(12, view * view / (rows * ROW));
            int top = listTop + (int) ((view - bar) * scroll / max);
            g.fill(width - 7, listTop, width - 4, listBottom, 0x40000000);
            g.fill(width - 7, top, width - 4, top + bar, 0xFFA0A0A8);
        }
    }

    /** Whether the key holds a conflict in the plain view (no modifiers held). */
    private boolean isClash(InputConstants.Key key) {
        return heldMods == 0 && conflicts.contains(key);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (menu != null) {
            PopupMenu open = menu;
            if (!open.contains(mx, my)) {
                menu = null;
            } else if (button == LEFT) {
                Item item = open.click(mx, my);
                if (item != null) {
                    AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
                    item.action().run();
                    if (!item.keepOpen() && menu == open) menu = null;
                }
            }
            return true;
        }
        Cap cap = isOverWidget(mx, my) ? null : capAt(mx, my);
        if (listening != null && button != LEFT) {
            bind(listening, InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        if (cap != null && button == RIGHT) {
            if (!cap.key().equals(ESCAPE)) menu = capMenu(cap, (int) mx, (int) my);
            return true;
        }
        if (button == LEFT && cap != null) {
            if (listening != null) {
                if (!cap.key().equals(ESCAPE)) bind(listening, cap.key());
            } else {
                pinned = cap.key().equals(pinned) ? null : cap.key();
                scroll = 0;
                refilter();
            }
            return true;
        }
        if (button == LEFT) {
            Chip chip = chipAt(mx, my);
            if (chip != null) {
                menu = categoryMenu(chip.x, chip.y + 12);
                return true;
            }
            int index = rowAt(mx, my);
            if (index >= 0) {
                Row row = visible.get(index);
                int zone = zoneAt(mx);
                if (row.macro != null) {
                    if (zone == 2) saveMacro(row.macro, null);
                    else if (zone == 0) {
                        InputConstants.Key key = row.macro.keyObj();
                        Compat.setScreen(minecraft, new CommandScreen(this, key, keyLabel(key), row.macro.mods, row.macro));
                    }
                    return true;
                }
                KeyMapping m = row.mapping;
                if (zone == 1) {
                    if (!m.isDefault()) bind(m, m.getDefaultKey());
                } else if (zone == 2) {
                    if (!m.isUnbound()) bind(m, InputConstants.UNKNOWN);
                } else if (m == listening) {
                    listening = null;
                } else {
                    listening = m;
                    heldMods = 0;
                    rebuild();
                    dragging = true;
                    dragX = mx;
                    dragY = my;
                    setFocused(null);
                }
                return true;
            }
        }
        listening = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == LEFT) {
            dragging = false;
            Cap cap = capAt(event.x(), event.y());
            if (listening != null && cap != null && !cap.key().equals(ESCAPE)) {
                bind(listening, cap.key());
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (menu != null) {
            menu.scroll(dy);
            return true;
        }
        if (my >= listTop - 2 && my < listBottom + 2) {
            scroll -= dy * ROW * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menu != null) {
            if (event.isEscape()) menu = null;
            return true;
        }
        if (listening != null) {
            bind(listening, event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(event);
        boolean typing = searchBox != null && searchBox.isFocused();
        int bit = Config.modBit(key);
        if (bit != 0 && !typing && (heldMods & bit) == 0) {
            heldMods |= bit;
            pinned = null;
            rebuild();
        }
        if (!typing && (event.hasControlDown() || (heldMods & Config.CTRL) != 0) && key.getValue() == InputConstants.KEY_Z) {
            undo();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int bit = Config.modBit(InputConstants.getKey(event));
        if ((heldMods & bit) != 0) {
            heldMods &= ~bit;
            rebuild();
        }
        return super.keyReleased(event);
    }

    @Override
    public void onClose() {
        Compat.setScreen(minecraft, parent);
    }

    @Override
    public void removed() {
        minecraft.options.save();
    }
}
