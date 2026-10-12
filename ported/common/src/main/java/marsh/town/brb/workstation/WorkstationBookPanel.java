package marsh.town.brb.workstation;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.Config;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** A recipe book for the two vanilla workstations without a built-in book. */
public final class WorkstationBookPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    public static final int WIDTH = 147;
    public static final int HEIGHT = 166;
    private static final int PAGE_SIZE = 20;
    private static final Map<WorkstationRecipe.Kind, Boolean> OPEN_BOOKS = new EnumMap<>(WorkstationRecipe.Kind.class);

    private final Minecraft client = Minecraft.getInstance();
    private final AbstractContainerMenu menu;
    private final Consumer<ItemStack> previewConsumer;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private final List<RecipeTile> tiles = new ArrayList<>();
    private final Set<Identifier> expanded = new HashSet<>();
    private final Map<Identifier, Boolean> available = new HashMap<>();
    private final WorkstationRecipe.Kind kind;
    private final int pinnedCategory;
    private final EditBox search;
    private final Button filter;
    private final Button previous;
    private final Button next;
    private final Button instant;
    private final Button settings;
    private final List<Button> tabs = new ArrayList<>();

    private List<WorkstationRecipe> recipes;
    private List<Row> rows = List.of();
    private WorkstationRecipe ghost;
    private boolean visible;
    private boolean craftableOnly;
    private int category = -1;
    private int page;
    private int x;
    private int y;
    private int inventoryFingerprint;
    private int pinsFingerprint;
    private int pressedButton = -1;
    private RecipeDisplayMode displayMode;

    public WorkstationBookPanel(AbstractContainerMenu menu, Consumer<ItemStack> previewConsumer) {
        this.menu = menu;
        this.previewConsumer = previewConsumer;
        kind = menu instanceof SmithingMenu ? WorkstationRecipe.Kind.SMITHING : WorkstationRecipe.Kind.BREWING;
        pinnedCategory = kind == WorkstationRecipe.Kind.BREWING ? 3 : 2;
        visible = OPEN_BOOKS.getOrDefault(kind, false);
        recipes = WorkstationRecipes.forMenu(menu);
        search = new EditBox(client.font, 0, 0, 98, 18, Component.translatable("itemGroup.search"));
        search.setMaxLength(100);
        search.setHint(Component.translatable("itemGroup.search"));
        search.setResponder(value -> { page = 0; rebuild(); });
        controls.add(search);
        filter = button("All", 29, pressed -> {
            craftableOnly = !craftableOnly;
            pressed.setMessage(Component.literal(craftableOnly ? "✓" : "All"));
            page = 0;
            rebuild();
        });
        filter.setTooltip(Tooltip.create(Component.translatable("gui.recipebook.toggleRecipes.all")));
        controls.add(filter);
        int tabCount = pinnedCategory + 2;
        for (int tab = 0; tab < tabCount; tab++) {
            final int chosen = tab - 1;
            String name = tab == 0 ? "All" : tab == tabCount - 1 ? "Pins"
                    : kind == WorkstationRecipe.Kind.BREWING ? (tab == 1 ? "Ptn" : tab == 2 ? "Spl" : "Lng")
                    : (tab == 1 ? "Up" : "Trim");
            var widget = button(name, kind == WorkstationRecipe.Kind.BREWING ? 26 : 32, ignored -> {
                category = chosen;
                page = 0;
                rebuild();
            });
            if (tab == tabCount - 1) widget.setTooltip(Tooltip.create(Component.literal("Pinned recipe variants")));
            else if (tab > 0) widget.setTooltip(Tooltip.create(Component.literal(
                    kind == WorkstationRecipe.Kind.BREWING ? (tab == 1 ? "Potions" : tab == 2 ? "Splash potions" : "Lingering potions")
                            : (tab == 1 ? "Smithing upgrades" : "Armor trims"))));
            tabs.add(widget);
            controls.add(widget);
        }
        previous = button("<", 20, ignored -> changePage(-1));
        next = button(">", 20, ignored -> changePage(1));
        controls.add(previous);
        controls.add(next);
        instant = button("!", 20, ignored -> {
            BetterRecipeBook.instantCraftingManager.toggleEnabled();
            updateControls();
        });
        instant.setTooltip(Tooltip.create(Component.literal("Toggle instant crafting. Brewing keeps its normal duration.")));
        controls.add(instant);
        settings = button("...", 20, ignored -> client.gui.setScreen(
                AutoConfigClient.getConfigScreen(Config.class, client.gui.screen()).get()));
        settings.setTooltip(Tooltip.create(Component.literal("Better Recipe Book settings")));
        controls.add(settings);
        refreshAvailability();
        rebuild();
    }

    private static Button button(String label, int width, Button.OnPress action) {
        return Button.builder(Component.literal(label), action).bounds(0, 0, width, 18).build();
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggleVisibility() {
        visible = !visible;
        OPEN_BOOKS.put(kind, visible);
        if (!visible) setFocused(null);
        updateControls();
    }

    public int findLeftEdge(int screenWidth, int imageWidth) {
        int centered = (screenWidth - imageWidth) / 2;
        return visible && !BetterRecipeBook.config.keepCentered && screenWidth >= 379 ? centered + 77 : centered;
    }

    public void position(int screenWidth, int containerLeft, int containerTop) {
        x = screenWidth < 379 ? (screenWidth - WIDTH) / 2 : containerLeft - WIDTH - 4;
        y = containerTop;
        search.setPosition(x + 7, y + 7);
        filter.setPosition(x + 109, y + 7);
        int tabSpacing = kind == WorkstationRecipe.Kind.BREWING ? 27 : 33;
        for (int index = 0; index < tabs.size(); index++) tabs.get(index).setPosition(x + 7 + index * tabSpacing, y + 29);
        previous.setPosition(x + 7, y + 145);
        next.setPosition(x + 74, y + 145);
        settings.setPosition(x + 98, y + 145);
        instant.setPosition(x + 121, y + 145);
        layoutTiles();
    }

    public void tick() {
        if (!visible) return;
        List<WorkstationRecipe> current = WorkstationRecipes.forMenu(menu);
        if (current != recipes) {
            recipes = current;
            refreshAvailability();
            rebuild();
        }
        int fingerprint = 1;
        for (var slot : menu.slots) fingerprint = 31 * fingerprint + ItemStack.hashItemAndComponents(slot.getItem())
                + slot.getItem().getCount();
        int pins = BetterRecipeBook.pinnedRecipeManager.pinned.hashCode();
        RecipeDisplayMode mode = BetterRecipeBook.config.recipeDisplayMode;
        if (fingerprint != inventoryFingerprint || pins != pinsFingerprint || mode != displayMode) {
            inventoryFingerprint = fingerprint;
            pinsFingerprint = pins;
            displayMode = mode;
            refreshAvailability();
            rebuild();
        }
    }

    private void refreshAvailability() {
        available.clear();
        for (WorkstationRecipe recipe : recipes) available.put(recipe.id(), WorkstationTransfer.canPlace(menu, recipe));
    }

    private void rebuild() {
        if (recipes == null) return;
        RecipeDisplayMode mode = BetterRecipeBook.config.recipeDisplayMode;
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        var filtered = recipes.stream().filter(recipe -> {
            if (category == pinnedCategory && !BetterRecipeBook.pinnedRecipeManager.has(recipe.id())) return false;
            if (category >= 0 && category < pinnedCategory && recipe.category() != category) return false;
            if (craftableOnly && !available.getOrDefault(recipe.id(), false)) return false;
            if (query.isEmpty()) return true;
            return recipe.preview().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    || recipe.id().toString().contains(query)
                    || recipe.inputs().stream().flatMap(input -> input.examples().stream())
                        .anyMatch(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query));
        }).toList();

        var result = new ArrayList<Row>();
        if (mode == RecipeDisplayMode.ALL || category == pinnedCategory || !query.isEmpty()) {
            for (var recipe : filtered) result.add(new Row(List.of(recipe), false, false));
        } else {
            Map<Identifier, List<WorkstationRecipe>> groups = new LinkedHashMap<>();
            for (var recipe : filtered) groups.computeIfAbsent(recipe.groupId(), ignored -> new ArrayList<>()).add(recipe);
            for (var group : groups.values()) {
                boolean header = mode == RecipeDisplayMode.EXPANDABLE && group.size() > 1;
                boolean open = header && expanded.contains(group.getFirst().groupId());
                result.add(new Row(List.copyOf(group), header, open));
                if (open) for (var recipe : group) result.add(new Row(List.of(recipe), false, false));
            }
        }
        rows = List.copyOf(result);
        page = Math.min(page, Math.max(0, (rows.size() - 1) / PAGE_SIZE));
        buildTiles();
        updateControls();
    }

    private void buildTiles() {
        Identifier focusedId = getFocused() instanceof RecipeTile tile ? tile.selected().id() : null;
        controls.removeAll(tiles);
        tiles.clear();
        int end = Math.min(rows.size(), (page + 1) * PAGE_SIZE);
        for (int index = page * PAGE_SIZE; index < end; index++) {
            var tile = new RecipeTile(rows.get(index));
            tiles.add(tile);
            controls.add(tile);
            if (focusedId != null && tile.selected().id().equals(focusedId)) setFocused(tile);
        }
        if (getFocused() instanceof RecipeTile tile && !tiles.contains(tile)) setFocused(null);
        layoutTiles();
    }

    private void layoutTiles() {
        for (int index = 0; index < tiles.size(); index++) {
            tiles.get(index).setPosition(x + 7 + (index % 5) * 27, y + 50 + (index / 5) * 23);
        }
    }

    private void updateControls() {
        for (var widget : controls) widget.visible = visible;
        tabs.getLast().visible = visible && BetterRecipeBook.config.enablePinning;
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < rows.size();
        settings.visible = visible && BetterRecipeBook.config.settingsButton;
        instant.visible = visible && kind == WorkstationRecipe.Kind.SMITHING;
        instant.setMessage(Component.literal(BetterRecipeBook.config.instantCraft.enabled ? "!" : "×"));
    }

    private void changePage(int direction) {
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int target = page + direction;
        if (BetterRecipeBook.config.scrolling.scrollAround) target = Math.floorMod(target, pages);
        page = Math.clamp(target, 0, pages - 1);
        buildTiles();
        updateControls();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return controls;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        pressedButton = event.button();
        super.mouseClicked(event, doubleClick);
        if (getFocused() instanceof RecipeTile tile && !tiles.contains(tile)) {
            Identifier group = tile.row.recipes().getFirst().groupId();
            setFocused(tiles.stream().filter(replacement -> replacement.row.recipes().getFirst().groupId().equals(group))
                    .findFirst().orElse(null));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != pressedButton) return false;
        pressedButton = -1;
        super.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() != pressedButton) return false;
        super.mouseDragged(event, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY) || !BetterRecipeBook.config.scrolling.enableScrolling) return false;
        if (vertical != 0) changePage(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!visible) return false;
        if (search.isFocused()) return search.keyPressed(event);
        if (BetterRecipeBook.config.enablePinning && BetterRecipeBook.PIN_MAPPING.matches(event)) {
            RecipeTile target = tiles.stream().filter(AbstractWidget::isHovered).findFirst()
                    .orElseGet(() -> getFocused() instanceof RecipeTile focused ? focused : null);
            if (target != null && !target.row.header()) {
                BetterRecipeBook.pinnedRecipeManager.toggle(target.selected().id());
                rebuild();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            changePage(event.key() == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF313731);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, 0xFFC6C6C6);
        for (var widget : controls) widget.extractRenderState(graphics, mouseX, mouseY, delta);
        var selectedTab = tabs.get(category + 1);
        if (selectedTab.visible) graphics.fill(selectedTab.getX(), selectedTab.getBottom() - 2,
                selectedTab.getRight(), selectedTab.getBottom(), 0xFF376449);
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(client.font, (page + 1) + "/" + pages, x + 33, y + 150, 0xFF404040, false);
        if (rows.isEmpty()) graphics.text(client.font, Component.literal("No recipes"), x + 38, y + 90, 0xFF505050, false);
        for (var tile : tiles) {
            if (tile.isMouseOver(mouseX, mouseY)) {
                var tooltip = new ArrayList<>(Screen.getTooltipFromItem(client, tile.selected().preview()));
                if (tile.row.header()) {
                    tooltip.add(Component.literal((tile.row.expanded() ? "Collapse" : "Expand") + " "
                            + tile.row.recipes().size() + " variants"));
                } else {
                    tooltip.add(Component.literal("Left-click: place. Shift: place maximum."));
                    tooltip.add(Component.literal(kind == WorkstationRecipe.Kind.SMITHING
                            ? "Right-click: craft. Shift: craft maximum." : "Right-click: place. Brewing takes its normal time."));
                    if (BetterRecipeBook.config.enablePinning) tooltip.add(Component.literal("Pin: ")
                            .append(BetterRecipeBook.PIN_MAPPING.getTranslatedKeyMessage()));
                }
                graphics.setTooltipForNextFrame(client.font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    public void renderGhost(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        if (!visible || ghost == null) return;
        for (var input : ghost.inputs()) {
            if (input.slot() < 0 || input.slot() >= menu.slots.size() || input.examples().isEmpty()) continue;
            var slot = menu.slots.get(input.slot());
            if (!slot.getItem().isEmpty()) continue;
            ItemStack example = input.examples().getFirst();
            renderGhostItem(graphics, example, left + slot.x, top + slot.y, mouseX, mouseY);
        }
        if (kind == WorkstationRecipe.Kind.SMITHING && menu.getSlot(3).getItem().isEmpty()) {
            var slot = menu.getSlot(3);
            renderGhostItem(graphics, ghost.preview(), left + slot.x, top + slot.y, mouseX, mouseY);
        }
    }

    private void renderGhostItem(GuiGraphicsExtractor graphics, ItemStack stack, int slotX, int slotY,
                                 int mouseX, int mouseY) {
        graphics.fakeItem(stack, slotX, slotY);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x66707070);
        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
            graphics.setTooltipForNextFrame(client.font, Screen.getTooltipFromItem(client, stack),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    public void clearGhost() {
        ghost = null;
    }

    public boolean hasGhost() {
        return ghost != null;
    }

    private void selectRecipe(WorkstationRecipe recipe, boolean rightClick, boolean maximum) {
        if (!WorkstationTransfer.canPlace(menu, recipe)) {
            ghost = recipe;
            previewConsumer.accept(recipe.preview());
            return;
        }
        ghost = null;
        if (rightClick && recipe.kind() == WorkstationRecipe.Kind.SMITHING
                && BetterRecipeBook.config.instantCraft.enabled) {
            BetterRecipeBook.instantCraftingManager.requestSmithing(
                    () -> WorkstationTransfer.expectedResult(menu, recipe),
                    () -> WorkstationTransfer.place(menu, recipe, false), maximum);
        } else {
            WorkstationTransfer.place(menu, recipe, maximum);
        }
    }

    @Override
    public NarrationPriority narrationPriority() {
        if (!visible) return NarrationPriority.NONE;
        return controls.stream().filter(widget -> widget.visible).map(AbstractWidget::narrationPriority)
                .max(java.util.Comparator.naturalOrder()).orElse(NarrationPriority.NONE);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        for (var widget : controls) {
            if (widget.visible && widget.isHoveredOrFocused()) widget.updateNarration(output.nest());
        }
    }

    @Override
    public java.util.Collection<? extends NarratableEntry> getNarratables() {
        return visible ? controls.stream().filter(widget -> widget.visible).toList() : List.of();
    }

    private record Row(List<WorkstationRecipe> recipes, boolean header, boolean expanded) {}

    private final class RecipeTile extends AbstractWidget {
        private final Row row;
        private WorkstationRecipe displayed;

        RecipeTile(Row row) {
            super(0, 0, 24, 22, row.recipes().getFirst().preview().getHoverName());
            this.row = row;
            displayed = row.recipes().getFirst();
        }

        WorkstationRecipe selected() {
            return displayed;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if (!row.header() && row.recipes().size() > 1 && !isHoveredOrFocused()) {
                displayed = row.recipes().get((int) ((Util.getMillis() / 1500) % row.recipes().size()));
            }
            boolean canMake = available.getOrDefault(displayed.id(), false);
            graphics.fill(getX(), getY(), getRight(), getBottom(), isHoveredOrFocused() ? 0xFFF5F5F5 : 0xFF707070);
            graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1,
                    row.header() || canMake ? 0xFFC8D0C8 : 0xFFC9B7B7);
            graphics.fakeItem(displayed.preview(), getX() + 4, getY() + 3);
            if (row.header()) {
                graphics.fill(getX() + 1, getY() + 13, getX() + 10, getY() + 21, 0xFF254734);
                graphics.text(client.font, row.expanded() ? "−" : "+", getX() + 2, getY() + 13, 0xFFFFFFFF, false);
            } else if (BetterRecipeBook.config.enablePinning && BetterRecipeBook.pinnedRecipeManager.has(displayed.id())) {
                graphics.text(client.font, "*", getRight() - 6, getY(), 0xFFFFFF00, false);
            }
            setMessage(row.header() ? Component.literal((row.expanded() ? "Collapse " : "Expand ")
                    + row.recipes().size() + " " + displayed.preview().getHoverName().getString() + " variants")
                    : displayed.preview().getHoverName());
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo info) {
            return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            activate(event.button() == InputConstants.MOUSE_BUTTON_RIGHT, event.hasShiftDown());
        }

        private void activate(boolean rightClick, boolean maximum) {
            if (row.header()) {
                Identifier group = row.recipes().getFirst().groupId();
                if (!expanded.add(group)) expanded.remove(group);
                rebuild();
            } else {
                selectRecipe(displayed, rightClick, maximum);
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE)) {
                activate(event.hasControlDown(), event.hasShiftDown());
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
