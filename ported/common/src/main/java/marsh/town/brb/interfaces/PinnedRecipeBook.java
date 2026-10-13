package marsh.town.brb.interfaces;

import net.minecraft.client.gui.components.ImageButton;

/** A dedicated exact-variant view without changing vanilla's recipe categories. */
public interface PinnedRecipeBook {
    boolean brb$isPinnedTabSelected();
    ImageButton brb$getPinnedTab();
}
