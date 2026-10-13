package marsh.town.brb.workstation;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.Config;
import marsh.town.brb.config.CraftAllMethod;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.util.BRBTextures;
import marsh.town.brb.util.GroupControlRenderer;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
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
        visible = kind == WorkstationRecipe.Kind.BREWING
                ? BetterRecipeBook.config.brewingBookOpen : BetterRecipeBook.config.smithingBookOpen;
        craftableOnly = kind == WorkstationRecipe.Kind.BREWING
                ? BetterRecipeBook.config.brewingBookCraftableOnly : BetterRecipeBook.config.smithingBookCraftableOnly;
        recipes = WorkstationRecipes.forMenu(menu);
        search = new EditBox(client.font, 0, 0, 81, 14, Component.translatable("itemGroup.search"));
        search.setMaxLength(100);
        search.setHint(Component.translatable("gui.recipebook.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE));
        search.setTextColor(0xFFFFFFFF);
        search.setResponder(value -> { page = 0; rebuild(); });
        controls.add(search);
        filter = new ImageButton(0, 0, 26, 16, BRBTextures.RECIPE_BOOK_FILTER_BUTTON_SPRITES, pressed -> {
            craftableOnly = !craftableOnly;
            if (kind == WorkstationRecipe.Kind.BREWING) BetterRecipeBook.config.brewingBookCraftableOnly = craftableOnly;
            else BetterRecipeBook.config.smithingBookCraftableOnly = craftableOnly;
            if (BetterRecipeBook.configHolder != null) BetterRecipeBook.configHolder.save();
            page = 0;
            rebuild();
        }, Component.translatable("gui.recipebook.toggleRecipes.all")) {
            @Override
            public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        sprites.get(craftableOnly, isHoveredOrFocused()), getX(), getY(), width, height);
            }
        };
        controls.add(filter);
        for (int tab = 0; tab < pinnedCategory + 2; tab++) {
            var widget = new CategoryTab(tab - 1);
            tabs.add(widget);
            controls.add(widget);
        }
        previous = new ImageButton(0, 0, 12, 17, BRBTextures.RECIPE_BOOK_PAGE_BACKWARD_SPRITES,
                ignored -> changePage(-1), Component.translatable("gui.recipebook.previous_page"));
        next = new ImageButton(0, 0, 12, 17, BRBTextures.RECIPE_BOOK_PAGE_FORWARD_SPRITES,
                ignored -> changePage(1), Component.translatable("gui.recipebook.next_page"));
        previous.setTooltip(Tooltip.create(previous.getMessage()));
        next.setTooltip(Tooltip.create(next.getMessage()));
        controls.add(previous);
        controls.add(next);
        instant = new ImageButton(0, 0, 18, 18, BRBTextures.RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES, ignored -> {
            BetterRecipeBook.instantCraftingManager.toggleEnabled();
            updateControls();
        }, Component.empty()) {
            @Override
            public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        sprites.get(BetterRecipeBook.config.instantCraft.enabled, isHoveredOrFocused()),
                        getX(), getY(), width, height);
            }
        };
        controls.add(instant);
        settings = new ImageButton(0, 0, 18, 18, BRBTextures.SETTINGS_BUTTON_SPRITES,
                ignored -> client.gui.setScreen(AutoConfigClient.getConfigScreen(Config.class, client.gui.screen()).get()),
                Component.translatable("brb.gui.settings.open"));
        settings.setTooltip(Tooltip.create(settings.getMessage()));
        controls.add(settings);
        refreshAvailability();
        rebuild();
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggleVisibility() {
        visible = !visible;
        if (kind == WorkstationRecipe.Kind.BREWING) BetterRecipeBook.config.brewingBookOpen = visible;
        else BetterRecipeBook.config.smithingBookOpen = visible;
        if (BetterRecipeBook.configHolder != null) BetterRecipeBook.configHolder.save();
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
        search.setPosition(x + 25, y + 13);
        filter.setPosition(x + 110, y + 12);
        for (int index = 0; index < tabs.size(); index++) tabs.get(index).setPosition(x - 30, y + 3 + index * 27);
        previous.setPosition(x + 38, y + 137);
        next.setPosition(x + 93, y + 137);
        settings.setPosition(x + 11, y + 137);
        instant.setPosition(x + 119, y + 137);
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
            List<List<WorkstationRecipe>> families = List.copyOf(groups.values());
            if (mode == RecipeDisplayMode.EXPANDABLE) {
                families = marsh.town.brb.recipegroups.RecipeOutputGroups.merge(families,
                        recipe -> List.of(recipe.preview()));
            }
            for (var group : families) {
                boolean header = mode == RecipeDisplayMode.EXPANDABLE && group.size() > 1;
                boolean open = header && expanded.contains(group.getFirst().groupId());
                result.add(new Row(List.copyOf(group), header, open));
                if (open) for (var recipe : group) result.add(new Row(List.of(recipe), false, true));
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
            tiles.get(index).setPosition(x + 11 + (index % 5) * 25, y + 31 + (index / 5) * 25);
        }
    }

    private void updateControls() {
        for (var widget : controls) widget.visible = visible;
        tabs.getLast().visible = visible && BetterRecipeBook.config.enablePinning;
        previous.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < rows.size();
        previous.visible = visible && previous.active;
        next.visible = visible && next.active;
        Component filterName = Component.translatable(craftableOnly
                ? "gui.recipebook.toggleRecipes.craftable" : "gui.recipebook.toggleRecipes.all");
        filter.setMessage(filterName);
        filter.setTooltip(Tooltip.create(filterName));
        settings.visible = visible && BetterRecipeBook.config.settingsButton;
        instant.visible = visible && kind == WorkstationRecipe.Kind.SMITHING && BetterRecipeBook.config.instantCraft.showButton;
        Component instantName = Component.translatable(BetterRecipeBook.config.instantCraft.enabled
                ? "brb.gui.instantCraft.on" : "brb.gui.instantCraft.off");
        instant.setMessage(instantName);
        instant.setTooltip(Tooltip.create(instantName));
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
        return visible && ((mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT)
                || tabs.stream().anyMatch(tab -> tab.visible && tab.isMouseOver(mouseX, mouseY)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean magnifier = event.x() >= x + 8 && event.x() < search.getX()
                && event.y() >= search.getY() && event.y() < search.getBottom();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !search.isMouseOver(event.x(), event.y()) && !magnifier) {
            search.setFocused(false);
            if (getFocused() == search) setFocused(null);
        }
        if (!isMouseOver(event.x(), event.y())) return false;
        pressedButton = event.button();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && magnifier) {
            setFocused(search);
            return true;
        }
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

    public boolean capturesInput() {
        return visible && search.capturesInput();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!visible) return false;
        if (search.isFocused()) return search.keyPressed(event) || (!event.isEscape() && search.capturesInput());
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                x, y, 1.0F, 1.0F, WIDTH, HEIGHT, 256, 256);
        for (var widget : controls) widget.extractRenderState(graphics, mouseX, mouseY, delta);
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 1) {
            Component pageNumbers = Component.translatable("gui.recipebook.page", page + 1, pages);
            graphics.text(client.font, pageNumbers, x + 73 - client.font.width(pageNumbers) / 2, y + 141, 0xFFFFFFFF);
        }
        if (rows.isEmpty()) {
            Component empty = Component.translatable("brb.gui.workstation.no_recipes");
            graphics.text(client.font, empty, x + (WIDTH - client.font.width(empty)) / 2, y + 78, 0xFF404040, false);
        }
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
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x30FF0000);
        graphics.fakeItem(stack, slotX, slotY);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x30FFFFFF);
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
            boolean fillGrid = maximum && BetterRecipeBook.config.instantCraft.craftAllMethod == CraftAllMethod.FILL_GRID;
            BetterRecipeBook.instantCraftingManager.requestSmithing(
                    () -> WorkstationTransfer.expectedResult(menu, recipe),
                    () -> WorkstationTransfer.place(menu, recipe, fillGrid), maximum);
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

    private Component categoryName(int category) {
        if (category == -1) return Component.translatable("gui.recipebook.toggleRecipes.all");
        if (category == pinnedCategory) return Component.translatable("brb.gui.workstation.pins");
        if (kind == WorkstationRecipe.Kind.BREWING) {
            return Component.translatable(switch (category) {
                case 0 -> "brb.gui.workstation.potions";
                case 1 -> "brb.gui.workstation.splash_potions";
                default -> "brb.gui.workstation.lingering_potions";
            });
        }
        return Component.translatable(category == 0 ? "brb.gui.workstation.upgrades" : "brb.gui.workstation.trims");
    }

    private ItemStack categoryIcon(int category) {
        if (category == -1) return new ItemStack(Items.COMPASS);
        if (category == pinnedCategory) return new ItemStack(Items.KNOWLEDGE_BOOK);
        if (kind == WorkstationRecipe.Kind.BREWING) {
            return new ItemStack(switch (category) {
                case 0 -> Items.POTION;
                case 1 -> Items.SPLASH_POTION;
                default -> Items.LINGERING_POTION;
            });
        }
        return new ItemStack(category == 0 ? Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE : Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
    }

    private final class CategoryTab extends ImageButton {
        private final int chosen;
        private final ItemStack icon;

        CategoryTab(int chosen) {
            super(0, 0, 35, 27, BRBTextures.RECIPE_BOOK_TAB_SPRITES, ignored -> {
                category = chosen;
                page = 0;
                rebuild();
            }, WorkstationBookPanel.this.categoryName(chosen));
            this.chosen = chosen;
            icon = categoryIcon(chosen);
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean selected = category == chosen;
            int left = getX() - (selected ? 2 : 0);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites.get(true, selected), left, getY(), width, height);
            graphics.fakeItem(icon, left + 9, getY() + 5);
            if (chosen == pinnedCategory) graphics.text(client.font, "★", left + 6, getY() + 3, 0xFFFFD66A, false);
            if (isFocused()) graphics.outline(left + 2, getY() + 2, width - 4, height - 4, 0xFFFFFFFF);
        }
    }

    private final class RecipeTile extends AbstractWidget {
        private final Row row;
        private WorkstationRecipe displayed;

        RecipeTile(Row row) {
            super(0, 0, 25, 25, row.recipes().getFirst().preview().getHoverName());
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
            boolean multiple = row.recipes().size() > 1;
            if (row.header()) canMake = row.recipes().stream().anyMatch(recipe -> available.getOrDefault(recipe.id(), false));
            String sprite = "recipe_book/slot_" + (multiple ? "many_" : "") + (canMake ? "craftable" : "uncraftable");
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(sprite),
                    getX(), getY(), width, height);
            graphics.fakeItem(displayed.preview(), getX() + 4, getY() + 4);
            if (!row.header() && row.expanded()) {
                GroupControlRenderer.renderChild(graphics, getX(), getY(), width, height);
            }
            if (isFocused()) graphics.outline(getX(), getY(), width, height, 0xFFFFFFFF);
            if (row.header()) {
                GroupControlRenderer.renderHeader(graphics, getX(), getY(), width, height, 3, row.expanded());
            } else if (BetterRecipeBook.config.enablePinning && BetterRecipeBook.pinnedRecipeManager.has(displayed.id())) {
                graphics.text(client.font, "★", getX() - 2, getY() - 2, 0xFFFFD66A, false);
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
