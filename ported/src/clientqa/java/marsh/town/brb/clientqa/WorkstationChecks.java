package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.workstation.WorkstationBookPanel;
import marsh.town.brb.workstation.WorkstationRecipe;
import marsh.town.brb.workstation.WorkstationRecipes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Real workstation screen input, authoritative smithing, and timed vanilla brewing. */
public final class WorkstationChecks {
    private WorkstationChecks() {}

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<String> evidence) {
        RecipeDisplayMode originalMode = BetterRecipeBook.config.recipeDisplayMode;
        boolean originalInstant = BetterRecipeBook.config.instantCraft.enabled;
        Set<Identifier> originalPins = new HashSet<>(BetterRecipeBook.pinnedRecipeManager.pinned);
        List<ItemStack> originalInventory = world.getServer().computeOnServer(server -> {
            var inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            var stacks = new ArrayList<ItemStack>();
            for (int index = 0; index < inventory.getContainerSize(); index++) stacks.add(inventory.getItem(index).copy());
            return stacks;
        });
        try {
            context.runOnClient(client -> {
                BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
                BetterRecipeBook.config.instantCraft.enabled = true;
            });
            Item template = item("sentry_armor_trim_smithing_template");
            ItemStack base = helmet("QA Exact Trim");
            openSmithing(context, world, List.of(base, new ItemStack(template), new ItemStack(Items.IRON_INGOT)));
            WorkstationRecipe recipe = context.computeOnClient(client -> matchingSmithing(template, base, Items.IRON_INGOT));
            ItemStack expected = recipe.resultFor(List.of(new ItemStack(template), base, new ItemStack(Items.IRON_INGOT)));
            check(!expected.isEmpty(), "native trim recipe computes a concrete output", evidence);
            showRecipe(context, recipe);
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-smithing-fixed-variant"));
            clickRecipe(context, 0, false);
            sync(context, world);
            check(world.getServer().computeOnServer(server -> {
                var menu = server.getPlayerList().getPlayers().getFirst().containerMenu;
                return menu instanceof SmithingMenu && sameAcrossRegistries(menu.getSlot(3).getItem(), expected);
            }), "left-click prepares the exact named/damaged trim variant on the server", evidence);

            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_R, 0, 0)));
            check(BetterRecipeBook.pinnedRecipeManager.has(recipe.id()), "workstation pin records the exact trim variant", evidence);
            context.runOnClient(client -> {
                var panel = panel(client.gui.screen());
                button(panel, "Pins").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(recipeIds(panel).contains(recipe.id()), "Pins tab retains the selected concrete trim variant", evidence);
                button(panel, "All").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            });
            clickRecipe(context, 1, false);
            context.waitFor(client -> count(client.player.getInventory(), expected) == 1, 200);
            sync(context, world);
            check(world.getServer().computeOnServer(server -> countAcrossRegistries(server.getPlayerList().getPlayers().getFirst().getInventory(), expected) == 1),
                    "right-click crafts one prepared trim directly into authoritative inventory", evidence);
            check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    "smithing instant craft leaves no item on the cursor", evidence);

            // Re-trimming an existing component-bearing item must remain a valid vanilla operation.
            openSmithing(context, world, List.of(expected.copy(), new ItemStack(template), new ItemStack(Items.GOLD_INGOT)));
            WorkstationRecipe retrim = context.computeOnClient(client -> matchingSmithing(template, expected, Items.GOLD_INGOT));
            ItemStack retrimmed = retrim.resultFor(List.of(new ItemStack(template), expected, new ItemStack(Items.GOLD_INGOT)));
            showRecipe(context, retrim);
            clickRecipe(context, 1, false);
            context.waitFor(client -> count(client.player.getInventory(), retrimmed) == 1, 200);
            sync(context, world);
            check(world.getServer().computeOnServer(server -> countAcrossRegistries(server.getPlayerList().getPlayers().getFirst().getInventory(), retrimmed) == 1),
                    "already-trimmed named/damaged armor can be re-trimmed without losing unrelated components", evidence);

            ItemStack firstBase = helmet("QA Max Trim A");
            ItemStack secondBase = helmet("QA Max Trim B");
            openSmithing(context, world, List.of(firstBase, secondBase, new ItemStack(template, 2), new ItemStack(Items.IRON_INGOT, 2)));
            WorkstationRecipe maximum = context.computeOnClient(client -> matchingSmithing(template, firstBase, Items.IRON_INGOT));
            ItemStack firstExpected = maximum.resultFor(List.of(new ItemStack(template), firstBase, new ItemStack(Items.IRON_INGOT)));
            ItemStack secondExpected = maximum.resultFor(List.of(new ItemStack(template), secondBase, new ItemStack(Items.IRON_INGOT)));
            showRecipe(context, maximum);
            clickRecipe(context, 0, true);
            sync(context, world);
            check(context.computeOnClient(client -> client.player.containerMenu.getSlot(1).getItem().getCount() == 1),
                    "Shift-left respects the unstackable smithing base slot limit", evidence);
            clickRecipe(context, 1, true);
            context.waitTicks(30);
            context.runOnClient(client -> {
                var menu = client.player.containerMenu;
                Object pending = field(BetterRecipeBook.instantCraftingManager, "pending");
                evidence.add("DEBUG: smithing maximum after 30 ticks; A=" + count(client.player.getInventory(), firstExpected)
                        + "; B=" + count(client.player.getInventory(), secondExpected) + "; cursor=" + menu.getCarried()
                        + "; inputs=" + menu.slots.subList(0, 4).stream().map(slot -> slot.getItem().toString()).toList()
                        + "; pending=" + (pending == null ? "none" : "maximum=" + field(pending, "maximum")
                        + ", received=" + field(pending, "receivedResult") + ", state=" + field(pending, "lastState")));
            });
            context.waitFor(client -> count(client.player.getInventory(), firstExpected) == 1
                    && count(client.player.getInventory(), secondExpected) == 1, 300);
            sync(context, world);
            check(world.getServer().computeOnServer(server -> {
                var inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
                return countAcrossRegistries(inventory, firstExpected) == 1 && countAcrossRegistries(inventory, secondExpected) == 1;
            }), "Shift-right crafts both smithing bases and preserves each distinct custom component set", evidence);
            context.runOnClient(client -> inspectModes(panel(client.gui.screen()), evidence));

            BlockPos brewPos = openBrewing(context, world);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            WorkstationRecipe brew = context.computeOnClient(client -> WorkstationRecipes.forMenu(client.player.containerMenu).stream()
                    .filter(candidate -> candidate.kind() == WorkstationRecipe.Kind.BREWING
                            && candidate.inputs().stream().anyMatch(input -> input.slot() == 0 && input.accepts().test(awkward))
                            && candidate.inputs().stream().anyMatch(input -> input.slot() == 3 && input.accepts().test(new ItemStack(Items.SUGAR))))
                    .findFirst().orElseThrow(() -> new AssertionError("native awkward/sugar brewing variant missing")));
            ItemStack brewed = brew.resultFor(List.of(awkward, new ItemStack(Items.SUGAR)));
            showRecipe(context, brew);
            clickRecipe(context, 1, true);
            sync(context, world);
            context.waitFor(client -> ((BrewingStandMenu) client.player.containerMenu).getBrewingTicks() > 0, 100);
            check(context.computeOnClient(client -> {
                var menu = (BrewingStandMenu) client.player.containerMenu;
                return menu.getBrewingTicks() > 0 && menu.getTotalBrewingTicks() > 0
                        && ItemStack.isSameItemSameComponents(menu.getSlot(0).getItem(), awkward)
                        && ItemStack.isSameItemSameComponents(menu.getSlot(1).getItem(), awkward)
                        && ItemStack.isSameItemSameComponents(menu.getSlot(2).getItem(), awkward);
            }), "Shift-right fills three bottles and starts normal-duration brewing instead of instant output", evidence);
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-brewing-normal-duration"));
            context.waitFor(client -> {
                var menu = (BrewingStandMenu) client.player.containerMenu;
                return menu.getBrewingTicks() == 0 && ItemStack.isSameItemSameComponents(menu.getSlot(0).getItem(), brewed);
            }, 600);
            check(world.getServer().computeOnServer(server -> {
                var entity = (BrewingStandBlockEntity) server.getPlayerList().getPlayers().getFirst().level().getBlockEntity(brewPos);
                return entity != null && ItemStack.isSameItemSameComponents(entity.getItem(0), brewed)
                        && ItemStack.isSameItemSameComponents(entity.getItem(1), brewed)
                        && ItemStack.isSameItemSameComponents(entity.getItem(2), brewed);
            }), "three vanilla brewed outputs finish in the authoritative brewing block entity", evidence);
            context.runOnClient(client -> inspectModes(panel(client.gui.screen()), evidence));
        } finally {
            context.runOnClient(client -> {
                BetterRecipeBook.instantCraftingManager.cancelPending();
                BetterRecipeBook.config.recipeDisplayMode = originalMode;
                BetterRecipeBook.config.instantCraft.enabled = originalInstant;
                var current = new HashSet<>(BetterRecipeBook.pinnedRecipeManager.pinned);
                for (Identifier id : current) if (!originalPins.contains(id)) BetterRecipeBook.pinnedRecipeManager.toggle(id);
                for (Identifier id : originalPins) if (!current.contains(id)) BetterRecipeBook.pinnedRecipeManager.toggle(id);
                client.player.closeContainer();
                client.gui.setScreen(null);
            });
            world.getConnection().waitForServerboundPackets();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.closeContainer();
                for (int index = 0; index < originalInventory.size(); index++) player.getInventory().setItem(index, originalInventory.get(index));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
        }
    }

    private static ItemStack helmet(String name) {
        var stack = new ItemStack(item("diamond_helmet"));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.setDamageValue(17);
        return stack;
    }

    private static Item item(String name) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", name));
        if (item == null || item == Items.AIR) throw new AssertionError("missing native item " + name);
        return item;
    }

    private static void openSmithing(ClientGameTestContext context, TestSingleplayerContext world, List<ItemStack> stock) {
        close(context, world);
        var encodedStock = context.computeOnClient(client -> {
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return stock.stream().map(stack -> ItemStackTemplate.CODEC.encodeStart(ops,
                    ItemStackTemplate.fromNonEmptyStack(stack, stack.getCount())).getOrThrow()).toList();
        });
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int index = 0; index < encodedStock.size(); index++) {
                ItemStack stack = ItemStackTemplate.CODEC.parse(ops, encodedStock.get(index)).getOrThrow().create();
                player.getInventory().setItem(index + 9, stack);
            }
            var pos = player.blockPosition().offset(1, 0, 0);
            player.level().setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
                    new SmithingMenu(id, inventory, ContainerLevelAccess.create(player.level(), pos)), Component.literal("Smithing QA")));
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitForScreen(SmithingScreen.class);
        context.waitTicks(3);
        openBook(context);
    }

    private static BlockPos openBrewing(ClientGameTestContext context, TestSingleplayerContext world) {
        close(context, world);
        BlockPos pos = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            for (int index = 0; index < 3; index++) player.getInventory().setItem(index + 9, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
            player.getInventory().setItem(12, new ItemStack(Items.SUGAR));
            var position = player.blockPosition().offset(2, 0, 0);
            player.level().setBlockAndUpdate(position, Blocks.BREWING_STAND.defaultBlockState());
            var stand = (BrewingStandBlockEntity) player.level().getBlockEntity(position);
            if (stand == null) throw new AssertionError("native brewing entity missing");
            stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            player.openMenu(stand);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            return position;
        });
        world.getConnection().waitForClientboundPackets();
        context.waitForScreen(BrewingStandScreen.class);
        context.waitTicks(3);
        openBook(context);
        return pos;
    }

    private static WorkstationRecipe matchingSmithing(Item template, ItemStack base, Item addition) {
        var client = net.minecraft.client.Minecraft.getInstance();
        return WorkstationRecipes.forMenu(client.player.containerMenu).stream().filter(recipe -> recipe.category() == 1
                && recipe.inputs().stream().anyMatch(input -> input.slot() == 0 && input.accepts().test(new ItemStack(template)))
                && recipe.inputs().stream().anyMatch(input -> input.slot() == 1 && input.accepts().test(base))
                && recipe.inputs().stream().anyMatch(input -> input.slot() == 2 && input.accepts().test(new ItemStack(addition))))
                .findFirst().orElseThrow(() -> new AssertionError("native exact trim variant missing"));
    }

    private static void openBook(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (panel(client.gui.screen()).isVisible()) return;
            var toggle = client.gui.screen().children().stream().filter(ImageButton.class::isInstance)
                    .map(ImageButton.class::cast).findFirst().orElseThrow();
            click(client.gui.screen(), toggle, 0, false);
        });
    }

    private static void showRecipe(ClientGameTestContext context, WorkstationRecipe recipe) {
        context.runOnClient(client -> {
            var panel = panel(client.gui.screen());
            BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
            panel.tick();
            search(panel).setValue(recipe.id().toString());
            if (!recipeIds(panel).equals(List.of(recipe.id()))) throw new AssertionError("exact recipe search did not isolate requested variant");
        });
    }

    private static void clickRecipe(ClientGameTestContext context, int button, boolean shifted) {
        context.runOnClient(client -> {
            var tiles = tiles(panel(client.gui.screen()));
            if (tiles.isEmpty()) throw new AssertionError("workstation recipe tile missing");
            click(client.gui.screen(), tiles.getFirst(), button, shifted);
        });
    }

    private static void inspectModes(WorkstationBookPanel panel, List<String> evidence) {
        search(panel).setValue("");
        BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
        panel.tick();
        int allCount = ((List<?>) field(panel, "rows")).size();
        check(allCount > 0 && ((List<?>) field(panel, "rows")).stream().allMatch(row -> !((boolean) recordField(row, "header"))),
                "workstation All mode exposes fixed individual variants", evidence);
        var pinTiles = tiles(panel);
        if (pinTiles.size() < 2) throw new AssertionError("Pin hover regression needs two workstation variants");
        var focused = pinTiles.getFirst();
        var hovered = pinTiles.get(1);
        var focusedRecipe = (WorkstationRecipe) field(focused, "displayed");
        var hoveredRecipe = (WorkstationRecipe) field(hovered, "displayed");
        var manager = BetterRecipeBook.pinnedRecipeManager;
        boolean focusedPinned = manager.has(focusedRecipe.id());
        boolean hoveredPinned = manager.has(hoveredRecipe.id());
        try {
            panel.setFocused(focused);
            try {
                var hoverField = AbstractWidget.class.getDeclaredField("isHovered");
                hoverField.setAccessible(true);
                for (var tile : pinTiles) hoverField.setBoolean(tile, tile == hovered);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not set workstation hover state", exception);
            }
            panel.keyPressed(new KeyEvent(InputConstants.KEY_R, 0, 0));
            check(manager.has(hoveredRecipe.id()) != hoveredPinned && manager.has(focusedRecipe.id()) == focusedPinned,
                    "workstation pin prefers the hovered variant over the previously focused tile", evidence);
        } finally {
            if (manager.has(focusedRecipe.id()) != focusedPinned) manager.toggle(focusedRecipe.id());
            if (manager.has(hoveredRecipe.id()) != hoveredPinned) manager.toggle(hoveredRecipe.id());
        }
        BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.EXPANDABLE;
        panel.tick();
        check(((List<?>) field(panel, "rows")).size() <= allCount,
                "workstation expandable mode condenses native variant groups", evidence);
        AbstractWidget header = null;
        int pages = Math.max(1, (((List<?>) field(panel, "rows")).size() + 19) / 20);
        for (int page = 0; page < pages && header == null; page++) {
            header = tiles(panel).stream().filter(tile -> (boolean) recordField(field(tile, "row"), "header"))
                    .findFirst().orElse(null);
            if (header == null) panel.mouseScrolled(search(panel).getX(), search(panel).getY(), 0, -1);
        }
        if (header != null) {
            var client = net.minecraft.client.Minecraft.getInstance();
            var screen = (AbstractContainerScreen<?>) client.gui.screen();
            List<ItemStack> before = screen.getMenu().slots.stream().map(slot -> slot.getItem().copy()).toList();
            ItemStack carried = screen.getMenu().getCarried().copy();
            int collapsed = ((List<?>) field(panel, "rows")).size();
            click(screen, header, 0, false);
            int expanded = ((List<?>) field(panel, "rows")).size();
            check(expanded > collapsed, "workstation plus control expands fixed individual children", evidence);
            AbstractWidget minus = tiles(panel).stream().filter(tile -> (boolean) recordField(field(tile, "row"), "header"))
                    .findFirst().orElseThrow();
            click(screen, minus, 1, true);
            check(((List<?>) field(panel, "rows")).size() == collapsed,
                    "workstation minus control collapses without a Shift-right craft", evidence);
            boolean unchanged = ItemStack.matches(carried, screen.getMenu().getCarried());
            for (int index = 0; index < before.size(); index++) unchanged &= ItemStack.matches(before.get(index), screen.getMenu().getSlot(index).getItem());
            check(unchanged, "workstation expansion/collapse preserves every menu slot and the cursor", evidence);
        }
        BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.VANILLA;
        panel.tick();
        check(((List<?>) field(panel, "rows")).stream().noneMatch(row -> (boolean) recordField(row, "header")),
                "workstation vanilla mode contains cycling entries without expansion headers", evidence);
        BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
        panel.tick();
    }

    private static void click(net.minecraft.client.gui.screens.Screen screen, AbstractWidget widget, int button, boolean shifted) {
        var event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0,
                new MouseButtonInfo(button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT,
                        shifted ? InputConstants.MOD_SHIFT : 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static WorkstationBookPanel panel(net.minecraft.client.gui.screens.Screen screen) {
        return screen.children().stream().filter(WorkstationBookPanel.class::isInstance).map(WorkstationBookPanel.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("workstation book was not registered with the real screen"));
    }

    private static EditBox search(WorkstationBookPanel panel) {
        return panel.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static Button button(WorkstationBookPanel panel, String label) {
        return panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<AbstractWidget> tiles(WorkstationBookPanel panel) {
        return (List<AbstractWidget>) field(panel, "tiles");
    }

    private static List<Identifier> recipeIds(WorkstationBookPanel panel) {
        var ids = new ArrayList<Identifier>();
        for (Object row : (List<?>) field(panel, "rows")) {
            for (Object recipe : (List<?>) recordField(row, "recipes")) ids.add(((WorkstationRecipe) recipe).id());
        }
        return ids;
    }

    private static Object recordField(Object value, String name) {
        try {
            var method = value.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("workstation QA record reflection " + name, exception);
        }
    }

    private static Object field(Object value, String name) {
        try {
            Field field = value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("workstation QA reflection " + name, exception);
        }
    }

    private static int count(net.minecraft.world.entity.player.Inventory inventory, ItemStack expected) {
        int count = 0;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (ItemStack.isSameItemSameComponents(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    private static int countAcrossRegistries(net.minecraft.world.entity.player.Inventory inventory, ItemStack expected) {
        int count = 0;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (sameAcrossRegistries(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    private static boolean sameAcrossRegistries(ItemStack actual, ItemStack expected) {
        if (actual.isEmpty() || expected.isEmpty()) return actual.isEmpty() && expected.isEmpty();
        ItemStack actualWithoutTrim = actual.copy();
        ItemStack expectedWithoutTrim = expected.copy();
        var actualTrim = actualWithoutTrim.remove(DataComponents.TRIM);
        var expectedTrim = expectedWithoutTrim.remove(DataComponents.TRIM);
        if (!ItemStack.isSameItemSameComponents(actualWithoutTrim, expectedWithoutTrim)) return false;
        if (actualTrim == null || expectedTrim == null) return actualTrim == null && expectedTrim == null;
        return actualTrim.pattern().unwrapKey().equals(expectedTrim.pattern().unwrapKey())
                && actualTrim.material().unwrapKey().equals(expectedTrim.material().unwrapKey());
    }

    private static void sync(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(4);
    }

    private static void close(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            BetterRecipeBook.instantCraftingManager.cancelPending();
            client.player.closeContainer();
            client.gui.setScreen(null);
        });
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
    }

    private static void check(boolean passes, String message, List<String> evidence) {
        if (!passes) throw new AssertionError(message);
        evidence.add("PASS: " + message);
    }
}
