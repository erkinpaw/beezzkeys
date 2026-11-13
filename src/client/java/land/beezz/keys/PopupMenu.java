package land.beezz.keys;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/** A small menu drawn over the screen: the right-click menu of a key, the category chooser, the gear menu. */
final class PopupMenu {
    static final int ITEM = 12;

    /**
     * label == null: a separator. action == null: a caption that cannot be clicked. checked != null: a check box, filled
     * with the colour. color != 0 without a check box: a colour square. keepOpen: the menu stays after the click.
     */
    record Item(Component label, int color, BooleanSupplier checked, String right, Runnable action, boolean keepOpen) {
        static Item of(Component label, Runnable action) {
            return new Item(label, 0, null, null, action, false);
        }

        static Item caption(Component label) {
            return new Item(label, 0, null, null, null, true);
        }

        static Item separator() {
            return new Item(null, 0, null, null, null, true);
        }
    }

    final int x, y, w, h;
    private final List<Item> items;
    private final int contentH;
    private double scroll;

    PopupMenu(Font font, int x, int y, List<Item> items, int screenW, int screenH) {
        this.items = items;
        int width = 60;
        for (Item item : items) {
            if (item.label == null) continue;
            int iw = 8 + font.width(item.label) + 8;
            if (item.checked != null || item.color != 0) iw += 12;
            if (item.right != null) iw += font.width(item.right) + 12;
            width = Math.max(width, iw);
        }
        this.w = Math.min(width, Math.min(280, screenW - 8));
        this.contentH = items.size() * ITEM;
        this.h = Math.min(contentH + 4, screenH - 8);
        this.x = Math.max(4, Math.min(x, screenW - 4 - w));
        this.y = Math.max(4, Math.min(y, screenH - 4 - h));
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Item at(double mx, double my) {
        if (!contains(mx, my)) return null;
        int index = (int) Math.floor((my - y - 2 + scroll) / ITEM);
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    /** @return the clicked item, or null when the click hit nothing that can be clicked */
    Item click(double mx, double my) {
        Item item = at(mx, my);
        return item != null && item.action != null ? item : null;
    }

    void scroll(double dy) {
        scroll = Math.max(0, Math.min(scroll - dy * ITEM * 2, Math.max(0, contentH + 4 - h)));
    }

    void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF6A6A74);
        g.fill(x, y, x + w, y + h, 0xFF15151A);
        Item hover = at(mx, my);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int iy = y + 2 + i * ITEM - (int) scroll;
            if (iy + ITEM <= y || iy >= y + h) continue;
            if (item.label == null) {
                g.fill(x + 4, iy + ITEM / 2, x + w - 4, iy + ITEM / 2 + 1, 0xFF44444C);
                continue;
            }
            if (item == hover && item.action != null) g.fill(x + 1, iy, x + w - 1, iy + ITEM, 0x40FFFFFF);
            int tx = x + 6;
            if (item.checked != null) {
                int c = item.color != 0 ? item.color : 0xFFE0E0E8;
                g.fill(tx, iy + 2, tx + 8, iy + 10, c);
                if (!item.checked.getAsBoolean()) g.fill(tx + 1, iy + 3, tx + 7, iy + 9, 0xFF15151A);
                tx += 12;
            } else if (item.color != 0) {
                g.fill(tx, iy + 2, tx + 8, iy + 10, item.color);
                tx += 12;
            }
            boolean dim = item.action == null || (item.checked != null && !item.checked.getAsBoolean());
            int rightW = item.right != null ? font.width(item.right) + 12 : 0;
            String text = item.label.getString();
            int room = x + w - 6 - rightW - tx;
            if (font.width(text) > room) text = font.plainSubstrByWidth(text, room - 6) + "…";
            g.text(font, text, tx, iy + 2, dim ? 0xFF9A9AA4 : 0xFFFFFFFF, false);
            if (item.right != null) g.text(font, item.right, x + w - 6 - font.width(item.right), iy + 2, 0xFF8B8B95, false);
        }
        g.disableScissor();
    }
}
