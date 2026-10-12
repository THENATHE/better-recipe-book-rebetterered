package marsh.town.brb.mixins.pins;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @ModifyArg(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"), index = 0)
    private List<RecipeCollection> brb$sortPins(List<RecipeCollection> collections) {
        List<RecipeCollection> sorted = new ArrayList<>(collections);
        if (BetterRecipeBook.config.enablePinning)
            sorted.sort(Comparator.comparing(collection -> !BetterRecipeBook.pinnedRecipeManager.has(collection)));
        return sorted;
    }
}
