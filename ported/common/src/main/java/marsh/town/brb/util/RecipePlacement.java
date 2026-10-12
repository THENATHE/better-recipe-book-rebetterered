package marsh.town.brb.util;

import marsh.town.brb.workstation.WorkstationRecipe;
import marsh.town.brb.workstation.WorkstationTransfer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.util.ArrayList;
import java.util.List;

/** Local placement for undiscovered recipes uses ordinary, validated inventory clicks. */
public final class RecipePlacement {
    private RecipePlacement() {}

    public static boolean place(RecipeDisplayEntry entry, boolean maximum) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gameMode == null) return false;
        var menu = client.player.containerMenu;
        List<WorkstationRecipe.Input> inputs = new ArrayList<>();
        if (menu instanceof AbstractCraftingMenu crafting) {
            var slots = crafting.getInputGridSlots();
            int width = crafting.getGridWidth();
            int height = crafting.getGridHeight();
            if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > width || shaped.height() > height) return false;
                int offsetX = (width - shaped.width()) / 2;
                int offsetY = (height - shaped.height()) / 2;
                for (int row = 0; row < shaped.height(); row++) {
                    for (int column = 0; column < shaped.width(); column++) {
                        SlotDisplay display = shaped.ingredients().get(row * shaped.width() + column);
                        if (!add(inputs, slots.get((row + offsetY) * width + column + offsetX).index, display)) return false;
                    }
                }
            } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
                if (shapeless.ingredients().size() > slots.size()) return false;
                for (int index = 0; index < shapeless.ingredients().size(); index++) {
                    if (!add(inputs, slots.get(index).index, shapeless.ingredients().get(index))) return false;
                }
            } else return false;
        } else if (menu instanceof AbstractFurnaceMenu && entry.display() instanceof FurnaceRecipeDisplay furnace) {
            if (!add(inputs, 0, furnace.ingredient())) return false;
        } else return false;
        return WorkstationTransfer.placeInputs(menu, inputs, maximum);
    }

    private static boolean accepts(SlotDisplay display, ItemStack stack, List<ItemStack> choices) {
        if (display instanceof SlotDisplay.ItemSlotDisplay item) return stack.is(item.item());
        if (display instanceof SlotDisplay.TagSlotDisplay tag) return tag.tag().contains(stack.typeHolder());
        if (display instanceof SlotDisplay.WithRemainder remainder) return accepts(remainder.input(), stack, choices);
        if (display instanceof SlotDisplay.Composite composite) {
            return composite.contents().stream().anyMatch(child -> accepts(child, stack,
                    child.resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level))));
        }
        return choices.stream().anyMatch(choice -> ItemStack.isSameItemSameComponents(choice, stack));
    }

    private static boolean add(List<WorkstationRecipe.Input> inputs, int slot, SlotDisplay display) {
        var context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        List<ItemStack> choices = display.resolveForStacks(context);
        if (choices.isEmpty()) return display instanceof SlotDisplay.Empty;
        inputs.add(new WorkstationRecipe.Input(slot,
                stack -> accepts(display, stack, choices), choices));
        return true;
    }
}
