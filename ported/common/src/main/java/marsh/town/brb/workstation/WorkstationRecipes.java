package marsh.town.brb.workstation;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.recipegroups.CanonicalJson;
import marsh.town.brb.util.ClientRecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Native recipe adapters retain recipe identities and component-sensitive inputs. */
public final class WorkstationRecipes {
    private static ClientLevel loadedLevel;
    private static List<WorkstationRecipe> brewing = List.of();
    private static List<WorkstationRecipe> smithing = List.of();

    private WorkstationRecipes() {}

    public static void clear() {
        loadedLevel = null;
        brewing = List.of();
        smithing = List.of();
    }

    public static List<WorkstationRecipe> forMenu(AbstractContainerMenu menu) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != loadedLevel) load(client.level);
        return menu instanceof BrewingStandMenu ? brewing : menu instanceof SmithingMenu ? smithing : List.of();
    }

    private static void load(ClientLevel level) {
        clear();
        if (level == null) return;
        var registry = ClientRecipeRegistry.provider().lookup(Registries.RECIPE);
        if (registry.isEmpty()) return;
        List<WorkstationRecipe> brews = new ArrayList<>();
        List<WorkstationRecipe> smiths = new ArrayList<>();
        registry.get().listElements().forEach(holder -> {
            Identifier recipeId = holder.key().identifier();
            if (holder.value() instanceof BrewingRecipe recipe) {
                addBrewing(level, recipeId, recipe, brews);
            } else if (holder.value() instanceof SmithingRecipe recipe) {
                addSmithing(level, recipeId, recipe, smiths);
            }
        });
        brewing = List.copyOf(brews);
        smithing = List.copyOf(smiths);
        loadedLevel = level;
    }

    private static void addBrewing(ClientLevel level, Identifier recipeId, BrewingRecipe recipe, List<WorkstationRecipe> out) {
        for (ItemStack bottle : examples(recipe.getInput())) {
            for (ItemStack reagent : examples(recipe.getReagent())) {
                BrewingInput input = new BrewingInput(bottle, reagent);
                if (!recipe.matches(input)) continue;
                ItemStack result = recipe.assemble(input);
                if (result.isEmpty()) continue;
                int category = result.is(Items.SPLASH_POTION) ? 1 : result.is(Items.LINGERING_POTION) ? 2 : 0;
                List<WorkstationRecipe.Input> inputs = List.of(
                        new WorkstationRecipe.Input(0, stack -> recipe.getInput().test(stack)
                                && ItemStack.isSameItemSameComponents(bottle, stack), List.of(bottle)),
                        new WorkstationRecipe.Input(3, stack -> recipe.getReagent().test(stack)
                                && ItemStack.isSameItemSameComponents(reagent, stack), List.of(reagent)));
                out.add(new WorkstationRecipe(WorkstationRecipe.Kind.BREWING,
                        variantId(level, recipeId, List.of(bottle, reagent)), recipeId, result, inputs, category,
                        actual -> recipe.assemble(new BrewingInput(actual.get(0), actual.get(1)))));
            }
        }
    }

    private static void addSmithing(ClientLevel level, Identifier recipeId, SmithingRecipe recipe, List<WorkstationRecipe> out) {
        List<ItemStack> templates = examples(recipe.templateIngredient());
        List<ItemStack> bases = examples(Optional.of(recipe.baseIngredient()));
        List<ItemStack> additions = examples(recipe.additionIngredient());
        for (ItemStack template : templates) {
            for (ItemStack base : bases) {
                for (ItemStack addition : additions) {
                    SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
                    if (!recipe.matches(input, level)) continue;
                    ItemStack result = recipe.assemble(input);
                    if (result.isEmpty()) continue;
                    List<WorkstationRecipe.Input> inputs = new ArrayList<>();
                    if (!template.isEmpty()) inputs.add(new WorkstationRecipe.Input(0,
                            stack -> Ingredient.testOptionalIngredient(recipe.templateIngredient(), stack)
                                    && stack.is(template.getItem()), List.of(template)));
                    inputs.add(new WorkstationRecipe.Input(1,
                            stack -> recipe.baseIngredient().test(stack) && stack.is(base.getItem())
                                    && !recipe.assemble(new SmithingRecipeInput(template, stack, addition)).isEmpty(), List.of(base)));
                    if (!addition.isEmpty()) inputs.add(new WorkstationRecipe.Input(2,
                            stack -> Ingredient.testOptionalIngredient(recipe.additionIngredient(), stack)
                                    && stack.is(addition.getItem()), List.of(addition)));
                    out.add(new WorkstationRecipe(WorkstationRecipe.Kind.SMITHING,
                            variantId(level, recipeId, List.of(template, base, addition)), recipeId, result,
                            List.copyOf(inputs), recipe instanceof SmithingTrimRecipe ? 1 : 0,
                            actual -> {
                                int cursor = 0;
                                ItemStack t = template.isEmpty() ? ItemStack.EMPTY : actual.get(cursor++);
                                ItemStack b = actual.get(cursor++);
                                ItemStack a = addition.isEmpty() ? ItemStack.EMPTY : actual.get(cursor);
                                return recipe.assemble(new SmithingRecipeInput(t, b, a));
                            }));
                }
            }
        }
    }

    private static List<ItemStack> examples(Optional<Ingredient> ingredient) {
        return ingredient.map(value -> value.items().map(holder -> new ItemStack(holder)).toList())
                .orElse(List.of(ItemStack.EMPTY));
    }

    private static List<ItemStack> examples(PotionIngredient ingredient) {
        List<ItemStack> items = ingredient.ingredient().items().map(holder -> new ItemStack(holder)).toList();
        var potions = ingredient.potions().flatMap(value -> value.potions());
        if (potions.isEmpty()) return items;
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) {
            potions.get().stream().forEach(potion -> result.add(PotionContents.createItemStack(item.getItem(), potion)));
        }
        return result;
    }

    private static Identifier variantId(ClientLevel level, Identifier recipeId, List<ItemStack> inputs) {
        JsonArray values = new JsonArray();
        values.add(recipeId.toString());
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) values.add("empty");
            else values.add(ItemStackTemplate.CODEC.encodeStart(ops, ItemStackTemplate.fromNonEmptyStack(stack, 1)).getOrThrow());
        }
        return Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID, "variant/" + CanonicalJson.fingerprint(values));
    }
}
