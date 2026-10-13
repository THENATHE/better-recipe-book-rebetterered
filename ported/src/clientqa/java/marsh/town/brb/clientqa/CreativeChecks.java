package marsh.town.brb.clientqa;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.CreativeDisplayMode;
import marsh.town.brb.creative.CreativeGroupModel;
import marsh.town.brb.creative.CreativeGroupRules;
import marsh.town.brb.creative.CreativeGroupScreen;
import marsh.town.brb.mixins.creative.ItemPickerMenuAccess;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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
import java.util.function.Predicate;
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
                select(screen, "building_blocks");
                verifyBuildingFamilies(screen, evidence);
                scrollToIndex(screen, 0);
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-building-collapsed"));
            context.runOnClient(client -> toggleFamily((CreativeModeInventoryScreen) client.gui.screen(), "wood:poplar"));
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-poplar-expanded"));
            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                toggleFamily(screen, "wood:poplar");
                toggleFamily(screen, "building:cobblestone");
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-cobblestone-expanded"));
            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                toggleFamily(screen, "building:cobblestone");
                select(screen, "colored_blocks");
                // The earlier generic test left its first color family expanded.
                for (var entry : List.copyOf(model(screen).entries())) {
                    if (entry.header() && entry.expanded()) toggleFamily(screen, entry.groupKey());
                }
                for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
                        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
                    verifyFamily(screen, "wool:" + color, id -> id.equals(color + "_carpet")
                            || id.equals(color + "_wool") || id.startsWith(color + "_wool_"), evidence);
                    verifyFamily(screen, "concrete:" + color, id -> id.equals(color + "_concrete")
                            || id.startsWith(color + "_concrete_"), evidence);
                }
                verifyFamily(screen, "color:terracotta", id -> id.equals("terracotta")
                        || id.endsWith("_terracotta") && !id.endsWith("_glazed_terracotta"), evidence);
                verifyFamily(screen, "color:glazed_terracotta", id -> id.endsWith("_glazed_terracotta"), evidence);
                scrollToIndex(screen, 0);
            });
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-material-colors-collapsed"));
            double[] headerPosition = context.computeOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                var slot = screen.getMenu().slots.get(firstHeader(screen));
                var window = client.getWindow();
                double guiX = (int) field(AbstractContainerScreen.class, screen, "leftPos") + slot.x + 8;
                double guiY = (int) field(AbstractContainerScreen.class, screen, "topPos") + slot.y + 8;
                // TestInput routes window coordinates through MouseHandler's normal GUI scaling.
                return new double[] {
                        guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
                        guiY * window.getScreenHeight() / window.getGuiScaledHeight()
                };
            });
            context.getInput().setCursorPos(headerPosition[0], headerPosition[1]);
            context.waitTicks(3);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-creative-group-tooltip"));
            context.getInput().setCursorPos(4, 4);
            context.runOnClient(client -> {
                var screen = (CreativeModeInventoryScreen) client.gui.screen();
                verifyFamilyInTabs(screen, "color:stained_glass", id -> id.equals("glass") || id.equals("tinted_glass")
                        || id.endsWith("_stained_glass"), evidence);
                verifyFamilyInTabs(screen, "color:stained_glass_pane", id -> id.equals("glass_pane")
                        || id.endsWith("_stained_glass_pane"), evidence);
                verifyFamilyInTabs(screen, "color:shulker_box", id -> id.equals("shulker_box")
                        || id.endsWith("_shulker_box"), evidence);
                verifyFamilyInTabs(screen, "color:cushion", id -> id.endsWith("_cushion"), evidence);
                verifyFamilyInTabs(screen, "color:candle", id -> id.equals("candle")
                        || id.endsWith("_candle"), evidence);
                verifyFamilyInTabs(screen, "color:bundle", id -> id.equals("bundle")
                        || id.endsWith("_bundle"), evidence);
                verifyFamilyInTabs(screen, "color:harness", id -> id.endsWith("_harness"), evidence);
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
        ItemStack carpet = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_carpet")));
        ItemStack duplicate = red.copy();
        ItemStack ungrouped = new ItemStack(Items.STICK);
        var originals = List.of(red, ungrouped, carpet, duplicate);
        model.setSource(originals, true);
        check(model.entries().size() == 2 && model.entry(0).members() == 3,
                "creative model groups matching variants without discarding duplicates", evidence);
        check(model.toggle(0), "creative model expands a real header", evidence);
        check(model.entry(1).stack() == red && model.entry(2).stack() == carpet
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

    private static void verifyBuildingFamilies(CreativeModeInventoryScreen screen, List<String> evidence) {
        verifyFamily(screen, "wood:poplar", id -> id.startsWith("poplar_")
                || id.startsWith("stripped_poplar_"), evidence);
        verifyFamily(screen, "building:cobblestone", id -> id.contains("cobblestone"), evidence);
        verifyFamily(screen, "building:stone", id -> id.equals("stone") || id.startsWith("stone_")
                || id.startsWith("smooth_stone") || id.contains("stone_brick")
                        && !id.contains("blackstone") && !id.contains("end_stone"), evidence);
        for (String family : List.of("deepslate", "blackstone", "tuff", "quartz", "prismarine", "basalt",
                "purpur", "end_stone", "nether_brick", "mud", "granite", "diorite", "andesite", "cinnabar", "sulfur")) {
            String key = family.equals("nether_brick") ? "nether_bricks" : family;
            verifyFamily(screen, "building:" + key, id -> id.contains(family), evidence);
        }
        verifyFamily(screen, "building:sandstone", id -> id.contains("sandstone")
                && !id.contains("red_sandstone"), evidence);
        verifyFamily(screen, "building:red_sandstone", id -> id.contains("red_sandstone"), evidence);
        verifyFamily(screen, "building:bricks", id -> id.equals("bricks") || id.startsWith("brick_"), evidence);
        verifyFamily(screen, "building:resin", id -> id.contains("resin_brick") || id.equals("resin_block"), evidence);
    }

    private static void verifyFamilyInTabs(CreativeModeInventoryScreen screen, String key,
                                           Predicate<String> belongs, List<String> evidence) {
        int tabs = 0;
        for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            long count = tab.getDisplayItems().stream().filter(stack -> belongs.test(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())).count();
            if (count < 2) continue;
            invoke(screen, "selectTab", new Class<?>[]{CreativeModeTab.class}, tab);
            verifyFamily(screen, key, belongs, evidence);
            tabs++;
        }
        check(tabs > 0, key + " is covered in its real creative category", evidence);
    }

    private static void verifyFamily(CreativeModeInventoryScreen screen, String key,
                                     Predicate<String> belongs, List<String> evidence) {
        var originals = new ArrayList<>(selectedTab().getDisplayItems());
        var expected = originals.stream().filter(stack -> belongs.test(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())).toList();
        check(expected.size() > 1, key + " has multiple real vanilla tab members", evidence);
        var entries = model(screen).entries();
        int index = familyIndex(screen, key);
        check(index >= 0 && entries.get(index).header() && !entries.get(index).expanded()
                        && entries.get(index).members() == expected.size(),
                key + " collapses all expected tab members under one header", evidence);
        check(entries.stream().filter(entry -> key.equals(entry.groupKey())).count() == 1,
                key + " has no visible children while collapsed", evidence);
        verifyHeaderTooltip(screen, key, false, evidence);
        var collapsed = new ArrayList<>(screen.getMenu().items);
        screen.getMenu().setCarried(new ItemStack(Items.DIRT, 7));
        toggleFamily(screen, key);
        entries = model(screen).entries();
        index = familyIndex(screen, key);
        var children = entries.subList(index + 1, index + 1 + expected.size());
        check(children.stream().allMatch(entry -> !entry.header() && key.equals(entry.groupKey()))
                        && sameStacks(expected, children.stream().map(CreativeGroupModel.Entry::stack).toList())
                        && screen.getMenu().items.size() == collapsed.size() + expected.size(),
                key + " expands exact vanilla children in order without omissions or duplicates", evidence);
        verifyVisibleMapping(screen, evidence, key + " expanded");
        verifyHeaderTooltip(screen, key, true, evidence);
        toggleFamily(screen, key);
        check(sameStacks(collapsed, screen.getMenu().items)
                        && screen.getMenu().getCarried().is(Items.DIRT)
                        && screen.getMenu().getCarried().getCount() == 7,
                key + " mouse toggle restores collapsed contents without changing the carried stack", evidence);
        verifyVisibleMapping(screen, evidence, key + " collapsed");
        screen.getMenu().setCarried(ItemStack.EMPTY);
    }

    @SuppressWarnings("unchecked")
    private static void verifyHeaderTooltip(CreativeModeInventoryScreen screen, String key,
                                            boolean expanded, List<String> evidence) {
        int index = scrollToIndex(screen, familyIndex(screen, key));
        var slot = screen.getMenu().slots.get(index);
        setField(AbstractContainerScreen.class, screen, "hoveredSlot", slot);
        try {
            Method method = CreativeModeInventoryScreen.class.getDeclaredMethod(
                    "getTooltipFromContainerItem", ItemStack.class);
            method.setAccessible(true);
            var tooltip = (List<Component>) method.invoke(screen, slot.getItem());
            String action = expanded ? "Click to collapse" : "Click to expand";
            check(tooltip.size() == 2 && tooltip.get(1).getString().equals(action)
                            && TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(tooltip.get(1).getStyle().getColor())
                            && tooltip.stream().noneMatch(line -> line.getString().contains("brb.creative.")),
                    key + " " + (expanded ? "expanded" : "collapsed")
                            + " tooltip shows the translated gray action hint", evidence);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("QA creative header tooltip", exception);
        }
    }

    private static void verifyVisibleMapping(CreativeModeInventoryScreen screen, List<String> evidence, String label) {
        float scroll = (float) field(CreativeModeInventoryScreen.class, screen, "scrollOffs");
        int row = ((ItemPickerMenuAccess) screen.getMenu()).brb$rowForScroll(scroll);
        boolean matches = Float.isFinite(scroll);
        for (int slot = 0; slot < 45; slot++) {
            var entry = ((CreativeGroupScreen) screen).brb$creativeEntry(screen.getMenu().slots.get(slot));
            int index = row * 9 + slot;
            matches &= entry == null ? index >= screen.getMenu().items.size()
                    : ItemStack.matches(entry.stack(), screen.getMenu().slots.get(slot).getItem());
        }
        check(matches, label + " keeps every scrolled slot aligned with its group entry", evidence);
    }

    private static CreativeGroupModel model(CreativeModeInventoryScreen screen) {
        return (CreativeGroupModel) field(CreativeModeInventoryScreen.class, screen, "brb$groups");
    }

    private static int familyIndex(CreativeModeInventoryScreen screen, String key) {
        var entries = model(screen).entries();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).header() && key.equals(entries.get(index).groupKey())) return index;
        }
        return -1;
    }

    private static void toggleFamily(CreativeModeInventoryScreen screen, String key) {
        int index = familyIndex(screen, key);
        if (index < 0) throw new AssertionError("Missing creative family " + key);
        int visibleIndex = scrollToIndex(screen, index);
        click(screen, screen.getMenu().slots.get(visibleIndex), 0, 0);
    }

    private static int scrollToIndex(CreativeModeInventoryScreen screen, int index) {
        var menu = screen.getMenu();
        int lastRow = Math.max(0, (menu.items.size() + 8) / 9 - 5);
        int row = Math.min(index / 9, lastRow);
        float scroll = lastRow == 0 ? 0.0F : ((ItemPickerMenuAccess) menu).brb$scrollForRow(row);
        setField(CreativeModeInventoryScreen.class, screen, "scrollOffs", scroll);
        menu.scrollTo(scroll);
        return index - row * 9;
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
