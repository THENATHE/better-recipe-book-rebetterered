package marsh.town.brb.clientqa;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.CreativeDisplayMode;
import marsh.town.brb.creative.CreativeGroupModel;
import marsh.town.brb.creative.CreativeGroupRules;
import marsh.town.brb.creative.CreativeGroupScreen;
import marsh.town.brb.mixins.creative.ItemPickerMenuAccess;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Tests the real creative screen and vanilla input methods in the running client. */
public final class CreativeChecks {
    private CreativeChecks() {}

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<String> evidence) {
        CreativeDisplayMode originalMode = BetterRecipeBook.config.creativeDisplayMode;
        GameType originalGameType = world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer());
        try {
            world.getServer().runOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE));
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(3);
            context.runOnClient(client -> {
                testModel(evidence);
                BetterRecipeBook.config.creativeDisplayMode = CreativeDisplayMode.EXPANDABLE;
                client.gui.setScreen(new CreativeModeInventoryScreen(client.player,
                        client.player.connection.enabledFeatures(), false));
                select((CreativeModeInventoryScreen) client.gui.screen(), "colored_blocks");
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-collapsed"));

            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                var menu = screen.getMenu();
                int header = firstHeader(screen);
                check(header >= 0, "colored creative tab has stable expandable headers", evidence);
                int collapsedSize = menu.items.size();
                var representative = menu.slots.get(header).getItem().copy();
                var entry = ((CreativeGroupScreen) screen).brb$creativeEntry(menu.slots.get(header));
                int memberCount = entry.members();
                menu.setCarried(ItemStack.EMPTY);
                click(screen, menu.slots.get(header), 0, 0);
                check(menu.items.size() == collapsedSize + memberCount,
                        "creative expansion inserts every child, including the representative", evidence);
                check(menu.getCarried().isEmpty(), "creative header click never takes an item", evidence);
                check(ItemStack.isSameItemSameComponents(menu.items.get(header), representative),
                        "creative group representative does not cycle or change on expansion", evidence);

                var child = menu.slots.get(header + 1);
                ItemStack childItem = child.getItem().copy();
                click(screen, child, 0, 0);
                check(ItemStack.isSameItemSameComponents(menu.getCarried(), childItem),
                        "expanded child uses the vanilla creative pickup path", evidence);
                menu.setCarried(new ItemStack(Items.DIRT, 16));
                var event = event(screen, menu.slots.get(header), 0, InputConstants.MOD_SHIFT);
                screen.mouseClicked(event, false);
                screen.mouseDragged(new MouseButtonEvent(0, 0, event.buttonInfo()), -100, -100);
                screen.mouseReleased(new MouseButtonEvent(0, 0, event.buttonInfo()));
                check(menu.items.size() == collapsedSize,
                        "Shift-click collapses creative header without a vanilla shift action", evidence);
                check(menu.getCarried().is(Items.DIRT) && menu.getCarried().getCount() == 16,
                        "header drag/release outside preserves an already-carried stack", evidence);
                menu.setCarried(ItemStack.EMPTY);

                setField(AbstractContainerScreen.class, screen, "hoveredSlot", menu.slots.get(header));
                screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                check(menu.items.size() == collapsedSize + memberCount,
                        "Enter expands a hovered creative header", evidence);
                screen.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
                check(menu.items.size() == collapsedSize, "Space collapses a hovered creative header", evidence);

                // Number/drop shortcuts reach slotClicked, which must reject header item actions.
                var beforeHotbar = client.player.getInventory().getItem(0).copy();
                screen.keyPressed(new KeyEvent(InputConstants.KEY_1, 0, 0));
                check(ItemStack.matches(beforeHotbar, client.player.getInventory().getItem(0)),
                        "number-key shortcut cannot put a group header into the hotbar", evidence);
                select(screen, "building_blocks");
                screen.mouseScrolled(0, 0, 0, -4);
                float scroll = (float) field(CreativeModeInventoryScreen.class, screen, "scrollOffs");
                check(Float.isFinite(scroll), "creative grouped scrolling stays finite", evidence);
                int row = ((ItemPickerMenuAccess) menu).brb$rowForScroll(scroll);
                for (int slot = 0; slot < 45; slot++) {
                    var visible = ((CreativeGroupScreen) screen).brb$creativeEntry(menu.slots.get(slot));
                    int index = row * 9 + slot;
                    check(visible == null ? index >= menu.items.size()
                                    : ItemStack.matches(visible.stack(), menu.items.get(index)),
                            "scrolled creative entry maps to visible slot " + slot, evidence);
                }

                select(screen, "search");
                setSearch(screen, "oak");
                var expandedSearch = new ArrayList<>(menu.items);
                check(firstHeader(screen) == -1, "typed creative search reveals fixed individual matches", evidence);
                BetterRecipeBook.config.creativeDisplayMode = CreativeDisplayMode.VANILLA;
                setSearch(screen, "oak");
                check(sameStacks(expandedSearch, menu.items),
                        "grouped mode preserves vanilla search results and order exactly", evidence);
                select(screen, "colored_blocks");
                var vanillaContents = new ArrayList<>(selectedTab().getDisplayItems());
                check(sameStacks(vanillaContents, menu.items) && firstHeader(screen) == -1,
                        "vanilla creative mode preserves every original tab entry and order", evidence);
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-vanilla"));
            context.runOnClient(client -> {
                BetterRecipeBook.config.creativeDisplayMode = CreativeDisplayMode.EXPANDABLE;
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                select(screen, "colored_blocks");
                int header = firstHeader(screen);
                click(screen, screen.getMenu().slots.get(header), 0, 0);
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-expanded"));
            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                select(screen, "hotbar");
                check(firstHeader(screen) == -1, "saved-hotbar tab is unaffected by grouping", evidence);
                select(screen, "inventory");
                check(firstHeader(screen) == -1, "survival inventory tab is unaffected by grouping", evidence);
            });
        } finally {
            context.runOnClient(client -> {
                BetterRecipeBook.config.creativeDisplayMode = originalMode;
                if (client.gui.screen() instanceof CreativeModeInventoryScreen creative) {
                    creative.getMenu().setCarried(ItemStack.EMPTY);
                }
                client.gui.setScreen(null);
            });
            world.getServer().runOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().setGameMode(originalGameType));
            world.getConnection().waitForClientboundPackets();
        }
    }

    private static void testModel(List<String> evidence) {
        var model = new CreativeGroupModel();
        ItemStack red = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_wool")));
        ItemStack blue = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:blue_wool")));
        ItemStack duplicate = red.copy();
        ItemStack ungrouped = new ItemStack(Items.STICK);
        var originals = List.of(red, ungrouped, blue, duplicate);
        model.setSource(originals, true);
        check(model.entries().size() == 2 && model.entry(0).members() == 3,
                "creative model groups matching variants without discarding duplicates", evidence);
        check(model.toggle(0), "creative model expands a real header", evidence);
        check(model.entry(1).stack() == red && model.entry(2).stack() == blue
                        && model.entry(3).stack() == duplicate && model.entry(4).stack() == ungrouped,
                "expanded creative children retain exact stack objects and component identity", evidence);
        check(!model.toggle(1) && !model.toggle(-1) && !model.toggle(500),
                "creative model refuses child and out-of-range toggles", evidence);
        model.setSource(originals, false);
        check(model.entries().stream().map(CreativeGroupModel.Entry::stack).toList().equals(originals),
                "creative vanilla mode preserves duplicates and original interleaving", evidence);
        model.setSource(originals, true);
        check(model.entry(0).expanded(), "creative expansion choice survives a source refresh", evidence);
        model.setSource(List.of(), true);
        check(model.entries().isEmpty(), "empty creative lists remain empty", evidence);
        try {
            new CreativeGroupRules.Rule("invalid", Pattern.compile("(.*)"), "$9", "Invalid");
            throw new AssertionError("invalid capture was accepted");
        } catch (IllegalArgumentException expected) {
            evidence.add("PASS: invalid resource-pack group capture is rejected safely");
        }
    }

    private static int firstHeader(CreativeModeInventoryScreen screen) {
        for (int index = 0; index < Math.min(45, screen.getMenu().slots.size()); index++) {
            var entry = ((CreativeGroupScreen) screen).brb$creativeEntry(screen.getMenu().slots.get(index));
            if (entry != null && entry.header()) {
                return index;
            }
        }
        return -1;
    }

    private static void click(CreativeModeInventoryScreen screen, Slot slot, int button, int modifiers) {
        var event = event(screen, slot, button, modifiers);
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static MouseButtonEvent event(CreativeModeInventoryScreen screen, Slot slot, int button, int modifiers) {
        int left = (int) field(AbstractContainerScreen.class, screen, "leftPos");
        int top = (int) field(AbstractContainerScreen.class, screen, "topPos");
        return new MouseButtonEvent(left + slot.x + 8, top + slot.y + 8,
                new MouseButtonInfo(button == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT, modifiers));
    }

    private static void select(CreativeModeInventoryScreen screen, String id) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
        if (tab == null) throw new AssertionError("Missing tab " + id);
        invoke(screen, "selectTab", new Class<?>[]{CreativeModeTab.class}, tab);
    }

    private static CreativeModeTab selectedTab() {
        return (CreativeModeTab) field(CreativeModeInventoryScreen.class, null, "selectedTab");
    }

    private static void setSearch(CreativeModeInventoryScreen screen, String value) {
        ((EditBox) field(CreativeModeInventoryScreen.class, screen, "searchBox")).setValue(value);
        invoke(screen, "refreshSearchResults", new Class<?>[0]);
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!ItemStack.matches(left.get(index), right.get(index))) return false;
        }
        return true;
    }

    private static Object field(Class<?> owner, Object target, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("QA reflection " + name, exception);
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("QA reflection " + name, exception);
        }
    }

    private static void invoke(CreativeModeInventoryScreen screen, String name, Class<?>[] parameters, Object... args) {
        try {
            Method method = CreativeModeInventoryScreen.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            method.invoke(screen, args);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("QA invocation " + name, exception);
        }
    }

    private static void check(boolean passed, String message, List<String> evidence) {
        if (!passed) throw new AssertionError(message);
        evidence.add("PASS: " + message);
    }
}
