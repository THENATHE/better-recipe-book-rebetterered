package marsh.town.brb.workstation;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public record WorkstationRecipe(Kind kind, Identifier id, Identifier groupId,
                                ItemStack preview, List<Input> inputs, int category,
                                Function<List<ItemStack>, ItemStack> resultFactory) {
    public enum Kind { BREWING, SMITHING }

    public record Input(int slot, Predicate<ItemStack> accepts, List<ItemStack> examples) {}

    public ItemStack resultFor(List<ItemStack> supplied) {
        return resultFactory.apply(supplied);
    }
}
