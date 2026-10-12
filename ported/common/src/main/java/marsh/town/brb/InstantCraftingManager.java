package marsh.town.brb;

import marsh.town.brb.util.ClientInventoryUtil;
import marsh.town.brb.util.RecipePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Serializes ordinary recipe placement and result-slot clicks, leaving crafting to the server. */
public class InstantCraftingManager {
    private static final long TIMEOUT_NANOS = 5_000_000_000L;

    public Button lastInstantCraftButton;
    private Pending pending;
    private boolean applying;
    private boolean requestingPlacement;
    private int placementClicks;

    public InstantCraftingManager() {
        if (BetterRecipeBook.configHolder == null) return;
        BetterRecipeBook.configHolder.registerLoadListener((holder, config) -> {
            if (!config.instantCraft.enabled) cancelPending();
            return InteractionResult.SUCCESS;
        });
        BetterRecipeBook.configHolder.registerSaveListener((holder, config) -> {
            if (!config.instantCraft.enabled) cancelPending();
            return InteractionResult.SUCCESS;
        });
    }

    public boolean requestCraft(RecipeDisplayId id, boolean maximum) {
        Minecraft client = Minecraft.getInstance();
        if (!canStart(client)) return false;
        AbstractContainerMenu menu = client.player.containerMenu;
        if (!(menu instanceof InventoryMenu || menu instanceof CraftingMenu)) return false;
        RecipeDisplayEntry entry = client.player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .filter(recipe -> recipe.id().equals(id)).findFirst().orElse(null);
        if (entry == null) return false;
        if (id.index() < 0) return requestCraft(entry, () -> RecipePlacement.place(entry, false), maximum);
        ContextMap context = SlotDisplayContext.fromLevel(client.level);
        List<ItemStack> outputs = entry.resultItems(context);
        if (outputs.isEmpty()) return false;
        AbstractCraftingMenu crafting = (AbstractCraftingMenu) menu;
        pending = new Pending(menu, client.gui.screen(), client.getConnection(), crafting.getResultSlot().index, maximum,
                stack -> outputs.stream().anyMatch(output -> sameBatch(stack, output)),
                () -> matchesGrid(crafting, entry, context));
        if (maximum) {
            pending.placement = () -> {
                StackedItemContents available = new StackedItemContents();
                for (Slot slot : menu.slots) {
                    if (isPlayerStorage(client, slot)) available.accountSimpleStack(slot.getItem());
                    else if (crafting.getInputGridSlots().contains(slot)) available.accountStack(slot.getItem());
                }
                if (!entry.canCraft(available)) return false;
                client.gameMode.handlePlaceRecipe(menu.containerId, id, true);
                return true;
            };
        }
        if (isPrepared(entry, crafting, context)) {
            pending.receivedResult = true;
            return true;
        }
        requestingPlacement = true;
        try {
            client.gameMode.handlePlaceRecipe(menu.containerId, id, maximum);
        } finally {
            requestingPlacement = false;
        }
        return true;
    }

    public boolean canCraftPrepared(RecipeDisplayId id) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !(client.player.containerMenu instanceof AbstractCraftingMenu menu)
                || !menu.getCarried().isEmpty()) return false;
        RecipeDisplayEntry entry = client.player.getRecipeBook().getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                .filter(recipe -> recipe.id().equals(id)).findFirst().orElse(null);
        return entry != null && isPrepared(entry, menu, SlotDisplayContext.fromLevel(client.level));
    }

    private static boolean isPrepared(RecipeDisplayEntry entry, AbstractCraftingMenu menu, ContextMap context) {
        ItemStack result = menu.getResultSlot().getItem();
        return entry.resultItems(context).stream().anyMatch(output -> sameBatch(result, output)) && matchesGrid(menu, entry, context);
    }

    /** Locally discovered recipes use ordinary container clicks, never an invalid server recipe ID. */
    public boolean requestCraft(RecipeDisplayEntry entry, BooleanSupplier placement, boolean maximum) {
        Minecraft client = Minecraft.getInstance();
        if (!canStart(client) || !(client.player.containerMenu instanceof InventoryMenu || client.player.containerMenu instanceof CraftingMenu)) return false;
        AbstractCraftingMenu menu = (AbstractCraftingMenu) client.player.containerMenu;
        ContextMap context = SlotDisplayContext.fromLevel(client.level);
        List<ItemStack> outputs = entry.resultItems(context);
        if (outputs.isEmpty()) return false;
        pending = new Pending(menu, client.gui.screen(), client.getConnection(), menu.getResultSlot().index, maximum,
                stack -> outputs.stream().anyMatch(output -> sameBatch(stack, output)), () -> matchesGrid(menu, entry, context));
        pending.placement = placement;
        return placeLocal();
    }

    private boolean placeLocal() {
        Pending request = pending;
        if (request == null || request.placement == null) return false;
        request.receivedResult = false;
        request.awaitingNextBatch = false;
        request.placementStates.clear();
        request.lastPlacementSlot = -1;
        request.trackingPlacement = true;
        request.awaitingPlacement = true;
        request.placementConfirmed = false;
        request.confirmedIntermediate = false;
        request.lastState = request.menu.getStateId();
        request.lastProgress = System.nanoTime();
        ItemStack previousResult = request.menu.getSlot(request.resultSlot).getItem().copy();
        placementClicks = 0;
        applying = true;
        requestingPlacement = true;
        try {
            if (!request.placement.getAsBoolean()) {
                cancelPending();
                return false;
            }
            ItemStack result = request.menu.getSlot(request.resultSlot).getItem();
            request.finalPlacement = request.placementStates.isEmpty() ? null : request.placementStates.getLast();
            request.placementNeedsFullConfirmation = request.menu instanceof AbstractCraftingMenu && request.lastPlacementSlot >= 0
                    && isPlayerStorage(Minecraft.getInstance(), request.menu.getSlot(request.lastPlacementSlot));
            // A prepared local recipe needs no placement clicks and therefore produces no new result packet.
            if (placementClicks == 0 && sameBatch(previousResult, result)
                    && request.matchesOutput.test(result) && request.matchesGrid.getAsBoolean()) {
                request.receivedResult = true;
                request.awaitingPlacement = false;
            }
            // A server recipe-placement packet sends no local container clicks and uses its normal result response.
            if (placementClicks == 0) request.awaitingPlacement = false;
            return true;
        } catch (RuntimeException exception) {
            cancelPending();
            throw exception;
        } finally {
            request.trackingPlacement = false;
            applying = false;
            requestingPlacement = false;
        }
    }

    public boolean requestSmithing(ItemStack expectedResult, BooleanSupplier placement, boolean maximum) {
        ItemStack expected = expectedResult.copy();
        return requestSmithing(() -> expected, placement, maximum);
    }

    public boolean requestSmithing(Supplier<ItemStack> expectedResult, BooleanSupplier placement, boolean maximum) {
        Minecraft client = Minecraft.getInstance();
        if (!canStart(client) || !(client.player.containerMenu instanceof SmithingMenu)) return false;
        Pending request = new Pending(client.player.containerMenu, client.gui.screen(), client.getConnection(), 3, maximum,
                stack -> false, () -> true);
        pending = request;
        request.placement = () -> {
            ItemStack expected = expectedResult.get().copy();
            if (expected.isEmpty()) return false;
            request.matchesOutput = stack -> sameBatch(stack, expected);
            return placement.getAsBoolean();
        };
        return placeLocal();
    }

    private boolean canStart(Minecraft client) {
        return isEnabled() && pending == null && client.player != null && client.level != null && client.gameMode != null
                && client.getConnection() != null && client.gui.screen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() == client.player.containerMenu && client.player.containerMenu.getCarried().isEmpty();
    }

    private static boolean sameBatch(ItemStack actual, ItemStack expected) {
        return !actual.isEmpty() && actual.getCount() == expected.getCount() && ItemStack.isSameItemSameComponents(actual, expected);
    }

    public void onSlotUpdate(int containerId, int stateId, int slot) {
        if (pending != null && containerId == pending.menu.containerId && slot == pending.resultSlot
                && CraftRequestChecks.newerState(stateId, pending.lastState)) {
            pending.lastState = stateId;
            pending.receivedResult = true;
        }
    }

    public void onContentUpdate(int containerId, int stateId) {
        Pending request = pending;
        if (request == null || (containerId != 0 && containerId != request.menu.containerId)) return;
        // Vanilla may resynchronize chained clicks after its result update increments the menu state.
        // Accept only an exact state produced by this batch's own predicted transfers.
        if (containerId == request.menu.containerId && request.awaitingNextBatch) {
            for (TransferState state : request.transferStates) {
                if (!state.matches(request.menu, request.resultSlot)) continue;
                ItemStack result = request.menu.getSlot(request.resultSlot).getItem();
                if (!result.isEmpty() && (!request.matchesOutput.test(result) || !request.matchesGrid.getAsBoolean())) break;
                request.lastState = stateId;
                request.confirmedIntermediate = !state.carried().isEmpty();
                request.receivedResult = !request.confirmedIntermediate;
                request.transferConfirmed = !request.confirmedIntermediate;
                return;
            }
        }
        if (containerId == request.menu.containerId && request.awaitingPlacement) {
            for (TransferState state : request.placementStates) {
                if (!state.matches(request.menu, request.resultSlot)) continue;
                request.lastState = stateId;
                if (request.finalPlacement != null && request.finalPlacement.matches(request.menu, request.resultSlot)) {
                    ItemStack result = request.menu.getSlot(request.resultSlot).getItem();
                    if (!request.matchesOutput.test(result) || !request.matchesGrid.getAsBoolean()) break;
                    request.placementConfirmed = true;
                    request.awaitingPlacement = false;
                    request.confirmedIntermediate = false;
                    request.receivedResult = true;
                } else {
                    request.confirmedIntermediate = true;
                    request.receivedResult = false;
                }
                return;
            }
        }
        cancelPending();
    }

    public void onContainerInputCompleted(int slot) {
        if (pending != null && applying && pending.trackingPlacement) {
            pending.placementStates.add(new TransferState(pending.menu.slots.stream().map(input -> input.getItem().copy()).toList(),
                    pending.menu.getCarried().copy()));
            pending.lastPlacementSlot = slot;
        }
        if (pending != null && applying && pending.trackingBatch) {
            pending.transferStates.add(new TransferState(pending.menu.slots.stream().map(input -> input.getItem().copy()).toList(),
                    pending.menu.getCarried().copy()));
        }
    }

    public void onGhostRecipe(int containerId) {
        if (pending != null && containerId == pending.menu.containerId) cancelPending();
    }

    public void onUserContainerInput() {
        if (!applying) cancelPending();
        else if (requestingPlacement) placementClicks++;
    }

    public boolean isRequestingPlacement() {
        return requestingPlacement;
    }

    public void tick() {
        Pending request = pending;
        if (request == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.gameMode == null || client.level == null
                || client.getConnection() != request.connection || client.player.containerMenu != request.menu
                || client.gui.screen() != request.screen || System.nanoTime() - request.lastProgress > TIMEOUT_NANOS) {
            cancelPending();
            return;
        }
        if (request.awaitingPlacement) {
            if (request.placementNeedsFullConfirmation && !request.placementConfirmed) return;
            if (request.receivedResult && request.finalPlacement != null
                    && request.finalPlacement.matches(request.menu, request.resultSlot)
                    && request.matchesOutput.test(request.menu.getSlot(request.resultSlot).getItem())
                    && request.matchesGrid.getAsBoolean()) {
                request.awaitingPlacement = false;
                request.confirmedIntermediate = false;
            }
        }
        if (request.confirmedIntermediate) return;
        // The forced crafting result packet acknowledges pickup before the queued storage clicks.
        // Its stale-state storage responses must confirm the final predicted transfer before another pickup.
        if (request.awaitingNextBatch && request.menu instanceof AbstractCraftingMenu && !request.transferConfirmed) return;
        if (!request.menu.getCarried().isEmpty()) {
            cancelPending();
            return;
        }
        // Smithing may suppress an empty output acknowledgement when ordinary click prediction matched the server.
        // Refill through ordered vanilla clicks, then require a fresh confirmed output before taking the next batch.
        if (!request.receivedResult && request.awaitingNextBatch && request.menu instanceof SmithingMenu
                && request.maximum && request.menu.getSlot(request.resultSlot).getItem().isEmpty()) {
            placeLocal();
            return;
        }
        // Crafting result packets precede the grid updates. Inspect the settled grid on the next client tick.
        if (!request.receivedResult) return;
        request.receivedResult = false;
        ItemStack result = request.menu.getSlot(request.resultSlot).getItem();
        if (result.isEmpty() && request.maximum && request.placement != null) {
            placeLocal();
            return;
        }
        if (result.isEmpty() || !request.matchesOutput.test(result) || !request.matchesGrid.getAsBoolean()) {
            cancelPending();
            return;
        }
        Predicate<Integer> storage = index -> isPlayerStorage(client, request.menu.getSlot(index));
        List<ItemStack> toStore = new ArrayList<>();
        if (request.menu instanceof AbstractCraftingMenu crafting) {
            for (Slot input : crafting.getInputGridSlots()) {
                ItemStack ingredient = input.getItem();
                var remainder = ingredient.getItem().getCraftingRemainder();
                if (ingredient.getCount() > 1 && remainder != null) {
                    ItemStack remaining = remainder.create();
                    if (!remaining.isEmpty() && !ItemStack.isSameItemSameComponents(ingredient, remaining)) toStore.add(remaining);
                }
            }
        }
        toStore.add(result);
        if (!ClientInventoryUtil.canStoreAll(toStore, request.menu, storage)) {
            cancelPending();
            return;
        }
        request.lastProgress = System.nanoTime();
        // PICKUP crafts exactly one recipe batch. QUICK_MOVE can spill a partial final batch with a nearly full inventory.
        request.transferStates.clear();
        request.transferConfirmed = false;
        request.trackingBatch = request.maximum;
        applying = true;
        try {
            client.gameMode.handleContainerInput(request.menu.containerId, request.resultSlot, 0, ContainerInput.PICKUP, client.player);
            request.remainingBatches--;
            if (!ClientInventoryUtil.storeItem(-1, storage) || !request.maximum || request.remainingBatches <= 0) cancelPending();
            else request.awaitingNextBatch = true;
        } finally {
            request.trackingBatch = false;
            applying = false;
        }
    }

    private static boolean isPlayerStorage(Minecraft client, Slot slot) {
        return slot.container == client.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36;
    }

    private static boolean matchesGrid(AbstractCraftingMenu menu, RecipeDisplayEntry entry, ContextMap context) {
        List<ItemStack> grid = menu.getInputGridSlots().stream().map(Slot::getItem).toList();
        if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
            for (int top = 0; top <= menu.getGridHeight() - shaped.height(); top++) {
                for (int left = 0; left <= menu.getGridWidth() - shaped.width(); left++) {
                    if (matchesShape(grid, menu.getGridWidth(), menu.getGridHeight(), shaped, context, left, top, false)
                            || matchesShape(grid, menu.getGridWidth(), menu.getGridHeight(), shaped, context, left, top, true)) return true;
                }
            }
            return false;
        }
        if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<ItemStack> filled = grid.stream().filter(stack -> !stack.isEmpty()).toList();
            if (filled.size() != shapeless.ingredients().size()) return false;
            boolean[][] choices = new boolean[filled.size()][filled.size()];
            for (int ingredient = 0; ingredient < filled.size(); ingredient++) {
                for (int stack = 0; stack < filled.size(); stack++) {
                    choices[ingredient][stack] = matchesIngredient(filled.get(stack), shapeless.ingredients().get(ingredient), context);
                }
            }
            return CraftRequestChecks.matchesAssignment(choices);
        }
        return false;
    }

    private static boolean matchesShape(List<ItemStack> grid, int width, int height, ShapedCraftingRecipeDisplay shaped,
                                        ContextMap context, int left, int top, boolean mirrored) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                ItemStack stack = grid.get(y * width + x);
                int recipeX = x - left;
                int recipeY = y - top;
                if (recipeX < 0 || recipeY < 0 || recipeX >= shaped.width() || recipeY >= shaped.height()) {
                    if (!stack.isEmpty()) return false;
                } else {
                    if (mirrored) recipeX = shaped.width() - 1 - recipeX;
                    SlotDisplay ingredient = shaped.ingredients().get(recipeY * shaped.width() + recipeX);
                    if (!matchesIngredient(stack, ingredient, context)) return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesIngredient(ItemStack stack, SlotDisplay ingredient, ContextMap context) {
        if (ingredient instanceof SlotDisplay.ItemSlotDisplay item) return !stack.isEmpty() && stack.typeHolder().equals(item.item());
        if (ingredient instanceof SlotDisplay.TagSlotDisplay tag) return !stack.isEmpty() && tag.tag().contains(stack.typeHolder());
        if (ingredient instanceof SlotDisplay.Composite composite) return composite.contents().stream().anyMatch(option -> matchesIngredient(stack, option, context));
        if (ingredient instanceof SlotDisplay.WithRemainder remainder) return matchesIngredient(stack, remainder.input(), context);
        List<ItemStack> options = ingredient.resolveForStacks(context);
        if (options.isEmpty()) return ingredient instanceof SlotDisplay.Empty && stack.isEmpty();
        return options.stream().anyMatch(option -> ItemStack.isSameItemSameComponents(stack, option));
    }

    public void cancelPending() {
        pending = null;
    }

    public boolean toggleEnabled() {
        BetterRecipeBook.config.instantCraft.enabled = !BetterRecipeBook.config.instantCraft.enabled;
        if (!isEnabled()) cancelPending();
        BetterRecipeBook.configHolder.save();
        return isEnabled();
    }

    public boolean isEnabled() {
        return BetterRecipeBook.config.instantCraft.enabled;
    }

    private record TransferState(List<ItemStack> items, ItemStack carried) {
        boolean matches(AbstractContainerMenu menu, int resultSlot) {
            if (items.size() != menu.slots.size() || !sameStack(carried, menu.getCarried())) return false;
            for (int i = 0; i < items.size(); i++) {
                if (i != resultSlot && !sameStack(items.get(i), menu.getSlot(i).getItem())) return false;
            }
            return true;
        }

        private static boolean sameStack(ItemStack expected, ItemStack actual) {
            return expected.getCount() == actual.getCount() && (expected.isEmpty() && actual.isEmpty()
                    || ItemStack.isSameItemSameComponents(expected, actual));
        }
    }

    private static class Pending {
        final AbstractContainerMenu menu;
        final Screen screen;
        final ClientPacketListener connection;
        final int resultSlot;
        final boolean maximum;
        Predicate<ItemStack> matchesOutput;
        final BooleanSupplier matchesGrid;
        BooleanSupplier placement;
        int lastState;
        long remainingBatches;
        long lastProgress = System.nanoTime();
        boolean receivedResult;
        boolean awaitingNextBatch;
        boolean trackingBatch;
        boolean confirmedIntermediate;
        boolean transferConfirmed;
        final List<TransferState> transferStates = new ArrayList<>();
        final List<TransferState> placementStates = new ArrayList<>();
        TransferState finalPlacement;
        int lastPlacementSlot = -1;
        boolean trackingPlacement;
        boolean awaitingPlacement;
        boolean placementNeedsFullConfirmation;
        boolean placementConfirmed;

        Pending(AbstractContainerMenu menu, Screen screen, ClientPacketListener connection, int resultSlot, boolean maximum,
                Predicate<ItemStack> matchesOutput, BooleanSupplier matchesGrid) {
            this.menu = menu;
            this.screen = screen;
            this.connection = connection;
            this.resultSlot = resultSlot;
            this.maximum = maximum;
            this.matchesOutput = matchesOutput;
            this.matchesGrid = matchesGrid;
            lastState = menu.getStateId();
            // A custom recipe whose output can refill its own inputs must not run forever.
            remainingBatches = Math.max(1L, menu.slots.stream().filter(slot -> slot.index != resultSlot)
                    .mapToLong(slot -> Math.max(0, slot.getItem().getCount())).sum());
        }
    }
}
