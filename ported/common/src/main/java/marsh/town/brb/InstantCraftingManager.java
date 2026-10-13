package marsh.town.brb;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import marsh.town.brb.config.CraftAllMethod;
import marsh.town.brb.config.CraftingOverflow;
import marsh.town.brb.interfaces.BulkCraftingMenu;
import marsh.town.brb.util.BulkCraftPlan;
import marsh.town.brb.util.ClientInventoryUtil;
import marsh.town.brb.util.RecipePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
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
        pending.entry = entry;
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
        if (isPrepared(entry, crafting, context) && (!pending.fillGrid || !hasInventoryIngredients(client, menu, entry))) {
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
        pending.entry = entry;
        pending.placement = pending.fillGrid ? () -> RecipePlacement.place(entry, true) : placement;
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
        if (containerId == request.menu.containerId && request.awaitingBulkTransfer) {
            for (TransferState state : request.bulkStates) {
                if (!state.matches(request.menu, request.resultSlot)) continue;
                request.lastState = stateId;
                request.receivedResult = state == request.bulkStates.getLast();
                request.bulkConfirmed = request.receivedResult;
                return;
            }
            cancelPending();
            return;
        }
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
            pending.placementStates.add(captureState(pending));
            pending.lastPlacementSlot = slot;
        }
        if (pending != null && applying && pending.trackingBatch) {
            pending.transferStates.add(captureState(pending));
        }
    }

    private static TransferState captureState(Pending request) {
        List<ItemStack> items = request.menu.slots.stream().map(slot -> slot.getItem().copy())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (request.adaptSnapshots && request.menu instanceof BulkCraftingMenu bulk) bulk.brb$updateBulkSnapshot(items);
        return new TransferState(items, request.menu.getCarried().copy());
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
                || request.maximum && request.method != BetterRecipeBook.config.instantCraft.craftAllMethod
                || request.maximum && request.overflow != BetterRecipeBook.config.instantCraft.overflow
                || client.gui.screen() != request.screen || System.nanoTime() - request.lastProgress > TIMEOUT_NANOS) {
            cancelPending();
            return;
        }
        if (request.awaitingBulkTransfer) {
            finishBulkTransfer(request);
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
        // Result updates can acknowledge pickup before the queued storage clicks.
        // Confirm their final transfer before taking another batch from a still-loaded grid.
        if (request.awaitingNextBatch && (request.menu instanceof AbstractCraftingMenu || hasLoadedSmithingInputs(request))
                && !request.transferConfirmed) return;
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
        if (request.fillGrid) {
            transferLoadedGrid(client, request, result);
            return;
        }
        Predicate<Integer> storage = index -> isPlayerStorage(client, request.menu.getSlot(index));
        List<BulkCraftPlan.Input> inputs = bulkInputs(request, client);
        if (inputs != null) {
            List<Slot> storageSlots = request.menu.slots.stream().filter(slot -> storage.test(slot.index)).toList();
            Boolean pickupDrops = BulkCraftPlan.pickupWouldDrop(request.menu, inputs, storageSlots, result);
            if (pickupDrops == null) {
                cancelPending();
                return;
            }
            // Crafting remainders use the player's inventory, even when outputs can go to a backpack.
            if (Boolean.TRUE.equals(pickupDrops)) {
                if (request.maximum && request.overflow != CraftingOverflow.STOP_BEFORE_DROP) transferOneOverflowBatch(client, request, result);
                else cancelPending();
                return;
            }
        }
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
            if (request.maximum && request.overflow != CraftingOverflow.STOP_BEFORE_DROP) transferOneOverflowBatch(client, request, result);
            else cancelPending();
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
            else {
                request.awaitingNextBatch = true;
                if (hasLoadedSmithingInputs(request)) requestBulkConfirmation(request);
            }
        } finally {
            request.trackingBatch = false;
            applying = false;
        }
    }

    private void finishBulkTransfer(Pending request) {
        ItemStack result = request.menu.getSlot(request.resultSlot).getItem();
        if (!request.bulkConfirmed || !request.receivedResult || !request.bulkStates.getLast().matches(request.menu, request.resultSlot)) return;
        if (!result.isEmpty() && (!request.matchesOutput.test(result) || !request.matchesGrid.getAsBoolean())) {
            cancelPending();
            return;
        }
        request.awaitingBulkTransfer = false;
        request.awaitingNextBatch = false;
        request.receivedResult = false;
        if (request.stopAfterBulk || request.remainingBatches <= 0) cancelPending();
        else if (!result.isEmpty()) request.receivedResult = true;
        else if (request.placement != null) placeLocal();
        else cancelPending();
    }

    private void transferLoadedGrid(Minecraft client, Pending request, ItemStack result) {
        List<List<Slot>> storageGroups = outputStorageGroups(client, request.menu);
        List<BulkCraftPlan.Input> inputs = bulkInputs(request, client);
        if (storageGroups == null || inputs == null) {
            cancelPending();
            return;
        }
        BulkCraftPlan.Result plan = BulkCraftPlan.plan(request.menu, inputs, storageGroups, result, request.remainingBatches, false);
        if (plan == null) {
            cancelPending();
            return;
        }
        if (plan.dropped() && request.overflow == CraftingOverflow.STOP_BEFORE_DROP) {
            cancelPending();
            return;
        }
        if (plan.dropped() && request.overflow == CraftingOverflow.STOP_AFTER_DROP) {
            // Vanilla can keep shifting after a remainder drops. One explicit batch is
            // needed here to stop precisely at the first spill, rather than later in its loop.
            transferOneOverflowBatch(client, request, result);
            return;
        }
        ContainerInput input = ContainerInput.QUICK_MOVE;
        if (plan.batches() == 0) {
            if (request.overflow == CraftingOverflow.STOP_BEFORE_DROP) {
                cancelPending();
                return;
            }
            if (request.overflow == CraftingOverflow.STOP_AFTER_DROP) {
                transferOneOverflowBatch(client, request, result);
                return;
            }
            plan = BulkCraftPlan.plan(request.menu, inputs, storageGroups, result, request.remainingBatches, true);
            if (plan == null || plan.batches() == 0) {
                cancelPending();
                return;
            }
            input = ContainerInput.THROW;
        }
        request.bulkStates = plan.states().stream().map(state -> new TransferState(state.items(), ItemStack.EMPTY)).toList();
        request.awaitingBulkTransfer = true;
        request.bulkConfirmed = false;
        request.receivedResult = false;
        request.stopAfterBulk = request.overflow == CraftingOverflow.STOP_AFTER_DROP && plan.dropped();
        request.lastProgress = System.nanoTime();
        applying = true;
        try {
            client.gameMode.handleContainerInput(request.menu.containerId, request.resultSlot,
                    input == ContainerInput.THROW ? 1 : 0, input, client.player);
            request.remainingBatches -= plan.batches();
            requestBulkConfirmation(request);
        } finally {
            applying = false;
        }
    }

    private static List<List<Slot>> outputStorageGroups(Minecraft client, AbstractContainerMenu menu) {
        if (menu instanceof BulkCraftingMenu bulk) {
            if (!bulk.brb$canPlanBulk()) return null;
            List<List<Slot>> groups = bulk.brb$getOutputStorageGroups();
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (List<Slot> group : groups) {
                for (Slot slot : group) {
                    if (!menu.isValidSlotIndex(slot.index) || menu.getSlot(slot.index) != slot || !seen.add(slot.index)) return null;
                }
            }
            return groups;
        }
        if (menu.getClass() != InventoryMenu.class && menu.getClass() != CraftingMenu.class && menu.getClass() != SmithingMenu.class) return null;
        List<Slot> slots = new ArrayList<>();
        for (int index = menu.slots.size() - 1; index >= 0; index--) {
            Slot slot = menu.getSlot(index);
            if (slot.container == client.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36) slots.add(slot);
        }
        return List.of(slots);
    }

    private static List<BulkCraftPlan.Input> bulkInputs(Pending request, Minecraft client) {
        if (request.menu instanceof SmithingMenu) {
            return List.of(new BulkCraftPlan.Input(request.menu.getSlot(0), ItemStack.EMPTY),
                    new BulkCraftPlan.Input(request.menu.getSlot(1), ItemStack.EMPTY),
                    new BulkCraftPlan.Input(request.menu.getSlot(2), ItemStack.EMPTY));
        }
        if (!(request.menu instanceof AbstractCraftingMenu crafting) || request.entry == null) return null;
        ContextMap context = SlotDisplayContext.fromLevel(client.level);
        List<SlotDisplay> displays;
        if (request.entry.display() instanceof ShapedCraftingRecipeDisplay shaped) displays = shaped.ingredients();
        else if (request.entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) displays = shapeless.ingredients();
        else return null;
        List<BulkCraftPlan.Input> inputs = new ArrayList<>();
        for (Slot slot : crafting.getInputGridSlots()) {
            ItemStack ingredient = slot.getItem();
            if (ingredient.isEmpty()) continue;
            ItemStack remainder = null;
            for (SlotDisplay display : displays) {
                if (!matchesIngredient(ingredient, display, context)) continue;
                ItemStack candidate = ingredientRemainder(ingredient, display, context);
                if (candidate == null || candidate.getCount() > 1 || remainder != null && !sameRemainder(remainder, candidate)) return null;
                // A tool whose returned damage/components change while remaining an accepted
                // ingredient needs recipe-specific simulation. Its original count is no loop bound.
                if (!candidate.isEmpty() && matchesIngredient(candidate, display, context)
                        && !ItemStack.isSameItemSameComponents(ingredient, candidate)) return null;
                remainder = candidate;
            }
            if (remainder == null) return null;
            inputs.add(new BulkCraftPlan.Input(slot, remainder));
        }
        return inputs;
    }

    private static ItemStack ingredientRemainder(ItemStack ingredient, SlotDisplay display, ContextMap context) {
        if (display instanceof SlotDisplay.WithRemainder remainder) {
            List<ItemStack> choices = remainder.remainder().resolveForStacks(context);
            if (choices.isEmpty()) return ItemStack.EMPTY;
            ItemStack selected = choices.getFirst();
            return choices.stream().allMatch(choice -> sameRemainder(selected, choice)) ? selected.copy() : null;
        }
        if (display instanceof SlotDisplay.Composite composite) {
            ItemStack result = null;
            for (SlotDisplay child : composite.contents()) {
                if (!matchesIngredient(ingredient, child, context)) continue;
                ItemStack candidate = ingredientRemainder(ingredient, child, context);
                if (candidate == null || result != null && !sameRemainder(result, candidate)) return null;
                result = candidate;
            }
            return result;
        }
        var remainder = ingredient.getItem().getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }

    private static boolean sameRemainder(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty() || sameBatch(first, second);
    }

    private static boolean hasInventoryIngredients(Minecraft client, AbstractContainerMenu menu, RecipeDisplayEntry entry) {
        StackedItemContents available = new StackedItemContents();
        for (Slot slot : menu.slots) if (isPlayerStorage(client, slot)) available.accountSimpleStack(slot.getItem());
        return entry.canCraft(available);
    }

    private void transferOneOverflowBatch(Minecraft client, Pending request, ItemStack result) {
        List<BulkCraftPlan.Input> inputs = bulkInputs(request, client);
        if (inputs == null) {
            cancelPending();
            return;
        }
        List<Slot> storage = request.menu.slots.stream().filter(slot -> isPlayerStorage(client, slot)).toList();
        Boolean drops = BulkCraftPlan.pickupWouldDrop(request.menu, inputs, storage, result);
        if (drops == null || drops && request.overflow == CraftingOverflow.STOP_BEFORE_DROP) {
            cancelPending();
            return;
        }
        request.transferStates.clear();
        request.transferConfirmed = false;
        request.trackingBatch = true;
        request.lastProgress = System.nanoTime();
        applying = true;
        try {
            client.gameMode.handleContainerInput(request.menu.containerId, request.resultSlot, 0, ContainerInput.PICKUP, client.player);
            request.remainingBatches--;
            storeCarriedPartially(client, request.menu, storage);
            if (!request.menu.getCarried().isEmpty()) {
                client.gameMode.handleContainerInput(request.menu.containerId, AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                        0, ContainerInput.PICKUP, client.player);
                drops = true;
            }
            if (request.remainingBatches <= 0 || drops && request.overflow == CraftingOverflow.STOP_AFTER_DROP) cancelPending();
            else {
                request.awaitingNextBatch = true;
                if (hasLoadedSmithingInputs(request)) requestBulkConfirmation(request);
            }
        } finally {
            request.trackingBatch = false;
            applying = false;
        }
    }

    private static void storeCarriedPartially(Minecraft client, AbstractContainerMenu menu, List<Slot> storage) {
        List<Slot> slots = new ArrayList<>(storage);
        slots.sort((first, second) -> Boolean.compare(first.getItem().isEmpty(), second.getItem().isEmpty()));
        for (Slot slot : slots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) return;
            ItemStack stored = slot.getItem();
            if (!slot.isActive() || !slot.mayPlace(carried) || !stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, carried)
                    || stored.getCount() >= slot.getMaxStackSize(carried)) continue;
            client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, client.player);
        }
    }

    private static void requestBulkConfirmation(Pending request) {
        if (!request.menu.getCarried().isEmpty()) return;
        // An empty outside click cannot modify the menu. Its older state asks vanilla for
        // a full authoritative snapshot even when a one-batch smithing prediction was exact.
        request.connection.send(new ServerboundContainerClickPacket(request.menu.containerId,
                request.menu.getStateId() - 1 & 32767, (short) AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, (byte) 0,
                ContainerInput.PICKUP, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    private static boolean hasLoadedSmithingInputs(Pending request) {
        return request.menu instanceof SmithingMenu
                && request.menu.slots.subList(0, 3).stream().allMatch(slot -> !slot.getItem().isEmpty());
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
        final CraftAllMethod method;
        final CraftingOverflow overflow;
        final boolean fillGrid;
        final boolean adaptSnapshots;
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
        boolean awaitingBulkTransfer;
        boolean bulkConfirmed;
        boolean stopAfterBulk;
        RecipeDisplayEntry entry;
        List<TransferState> bulkStates = List.of();
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
            method = BetterRecipeBook.config.instantCraft.craftAllMethod;
            overflow = maximum ? BetterRecipeBook.config.instantCraft.overflow : CraftingOverflow.STOP_BEFORE_DROP;
            fillGrid = maximum && method == CraftAllMethod.FILL_GRID;
            // Resolve a storage holder while its opening copy still matches the inventory item,
            // before any predicted placement or result transfer changes either representation.
            adaptSnapshots = menu instanceof BulkCraftingMenu bulk && bulk.brb$canPlanBulk();
            this.matchesOutput = matchesOutput;
            this.matchesGrid = matchesGrid;
            lastState = menu.getStateId();
            // A custom recipe whose output can refill its own inputs must not run forever.
            remainingBatches = Math.max(1L, Math.min(10_000L, menu.slots.stream().filter(slot -> slot.index != resultSlot)
                    .mapToLong(slot -> Math.max(0, slot.getItem().getCount())).sum()));
        }
    }
}
