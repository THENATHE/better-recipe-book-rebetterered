package marsh.town.brb.interfaces;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.generic.pins.Pinnable;
import java.util.List;

public interface IPinningComponent<T extends Pinnable> {
    default void betterRecipeBook$sortByPinsInPlace(List<T> results) {
        if (BetterRecipeBook.config.enablePinning) {
            // TimSort is stable, so pinning does not reverse entries or change their relative order.
            results.sort(java.util.Comparator.comparing(result -> !BetterRecipeBook.pinnedRecipeManager.has(result)));
        }
    }
}
