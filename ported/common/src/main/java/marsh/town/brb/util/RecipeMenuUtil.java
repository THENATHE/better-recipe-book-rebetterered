package marsh.town.brb.util;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;

public class RecipeMenuUtil {
    public static boolean isRecipeSlot(RecipeBookMenu menu, int slot) {
        return menu instanceof AbstractFurnaceMenu ? slot == AbstractFurnaceMenu.INGREDIENT_SLOT : isCraftingGridSlot(menu, slot);
    }

    public static boolean isCraftingGridSlot(RecipeBookMenu menu, int slot) {
        return menu instanceof AbstractCraftingMenu crafting && crafting.getInputGridSlots().stream().anyMatch(input -> input.index == slot);
    }

    public static boolean isResultSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractCraftingMenu crafting) return crafting.getResultSlot().index == slot;
        return menu instanceof AbstractFurnaceMenu && slot == AbstractFurnaceMenu.RESULT_SLOT;
    }

    public static boolean isCraftingMenuSlot(RecipeBookMenu menu, int slot) {
        return isCraftingGridSlot(menu, slot) || isResultSlot(menu, slot);
    }
}
