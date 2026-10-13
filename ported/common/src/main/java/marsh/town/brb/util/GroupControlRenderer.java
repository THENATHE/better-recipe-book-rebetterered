package marsh.town.brb.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared presentation for group headers, which expand and collapse rather than select an item. */
public final class GroupControlRenderer {
    private static final int BADGE_SIZE = 9;

    private GroupControlRenderer() {}

    public static void renderHeader(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    int inset, boolean expanded) {
        if (expanded) {
            graphics.fill(x, y, x + width, y + height, 0x99000000);
        }

        var font = Minecraft.getInstance().font;
        String label = expanded ? "−" : "+";
        int left = x + width - BADGE_SIZE - inset;
        int top = y + height - BADGE_SIZE - inset;
        graphics.fill(left, top, left + BADGE_SIZE, top + BADGE_SIZE, 0xEE172331);
        graphics.text(font, label, left + (BADGE_SIZE - font.width(label) + 1) / 2,
                top + (BADGE_SIZE - font.lineHeight) / 2, 0xFFFFFFFF, false);
    }
}
