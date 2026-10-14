import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.backpackcrafting.BackpackCrafting;
import com.thenathe.backpackcrafting.menu.BackpackCraftingMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CraftingNetworkServer implements ModInitializer {
    private final java.util.Map<String, List<ItemStack>> bucketInventories = new java.util.HashMap<>();
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                Path control = Path.of(System.getProperty("crafting.qa.control"));
                for (var player : server.getPlayerList().getPlayers()) {
                    String name = player.getGameProfile().name();
                    Path request = control.resolve(name + "-open.json");
                    if (Files.exists(request)) {
                        var command = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                        Files.delete(request);
                        player.closeContainer();
                        if (bucketInventories.containsKey(name)) {
                            // Closing the preceding test can return its final bucket remainders by dropping them.
                            player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                    player.getBoundingBox().inflate(32)).stream()
                                    .filter(entity -> entity.getItem().is(Items.BUCKET) || entity.getItem().is(Items.CAKE))
                                    .forEach(entity -> entity.addTag("brb_bucket_qa"));
                        }
                        player.getInventory().clearContent();
                        me.pajic.toolpouch.ToolPouch.CONFIG.allowUseFromInventory.accept(true);
                        var toolbox = command.get("size").getAsInt() == 2 ? BackpackCrafting.IRON_TOOLBOX : BackpackCrafting.NETHERITE_TOOLBOX;
                        var pouch = new ItemStack(me.pajic.toolpouch.item.ModItems.TOOL_POUCH);
                        pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(toolbox))));
                        player.getInventory().setItem(12, pouch);
                        var backpack = new ItemStack(command.has("tier") && command.get("tier").getAsString().equals("netherite")
                                ? me.pajic.tiered_backpacks.item.ModItems.NETHERITE_BACKPACK
                                : me.pajic.tiered_backpacks.item.ModItems.LEATHER_BACKPACK);
                        boolean baseline = command.has("baseline") && command.get("baseline").getAsBoolean();
                        boolean bucketOverflow = command.has("bucketOverflow") && command.get("bucketOverflow").getAsBoolean();
                        int ingredientCount = bucketOverflow ? 0 : command.has("ingredients") ? command.get("ingredients").getAsInt() : 12;
                        boolean fromBackpack = !baseline && command.has("backpack") && command.get("backpack").getAsBoolean();
                        var ingredientStacks = new java.util.ArrayList<ItemStack>();
                        for (int remaining = ingredientCount; remaining > 0; remaining -= 64) ingredientStacks.add(new ItemStack(Items.OAK_PLANKS, Math.min(64, remaining)));
                        backpack.set(DataComponents.CONTAINER, fromBackpack ? ItemContainerContents.fromItems(ingredientStacks) : ItemContainerContents.EMPTY);
                        player.getInventory().setItem(13, backpack);
                        if (!fromBackpack) {
                            for (int index = 0; index < ingredientStacks.size(); index++) player.getInventory().setItem(index, ingredientStacks.get(index));
                        }
                        if (command.has("outputToBackpack") && command.get("outputToBackpack").getAsBoolean()) {
                            for (int index = 0; index < 36; index++) {
                                if (index != 12 && index != 13) player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
                            }
                        }
                        player.awardRecipes(server.getRecipeManager().getRecipes());
                        if (baseline) {
                            var position = player.blockPosition().below();
                            player.level().setBlock(position, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                            player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, owner) -> new net.minecraft.world.inventory.CraftingMenu(id, inventory, net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), position)), net.minecraft.network.chat.Component.literal("Crafting baseline")));
                        }
                        else me.pajic.tiered_backpacks.platform.MultiLoaderUtil.INSTANCE.openBackpackScreen(player, backpack);
                        if (bucketOverflow) {
                            player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                    player.getBoundingBox().inflate(32)).stream()
                                    .filter(entity -> entity.entityTags().contains("brb_bucket_qa")).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                            if (!(player.containerMenu instanceof BackpackCraftingMenu menu) || menu.getGridWidth() != 3) {
                                throw new AssertionError("Bucket overflow fixture requires a native 3x3 backpack menu");
                            }
                            var inputs = menu.getInputGridSlots();
                            for (int index = 0; index < 3; index++) {
                                ItemStack milk = new ItemStack(Items.MILK_BUCKET, 3);
                                milk.set(DataComponents.MAX_STACK_SIZE, 16);
                                inputs.get(index).set(milk);
                            }
                            inputs.get(3).set(new ItemStack(Items.SUGAR, 3));
                            inputs.get(4).set(new ItemStack(Items.EGG, 3));
                            inputs.get(5).set(new ItemStack(Items.SUGAR, 3));
                            for (int index = 6; index < 9; index++) inputs.get(index).set(new ItemStack(Items.WHEAT, 3));
                            bucketInventories.put(name, player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList());
                            menu.broadcastFullState();
                        } else bucketInventories.remove(name);
                    }
                    var row = new JsonObject();
                    row.addProperty("menu", player.containerMenu.getClass().getName());
                    int storedTables = player.containerMenu instanceof BackpackCraftingMenu backpackMenu
                            ? backpackMenu.getBackpack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().filter(stack -> stack.is(Items.CRAFTING_TABLE)).mapToInt(ItemStack::getCount).sum() : 0;
                    row.addProperty("stored_tables", storedTables);
                    row.addProperty("crafted_tables", player.getInventory().countItem(Items.CRAFTING_TABLE) + storedTables);
                    row.addProperty("planks", player.getInventory().countItem(Items.OAK_PLANKS));
                    if (player.containerMenu instanceof net.minecraft.world.inventory.AbstractCraftingMenu menu) {
                        row.addProperty("size", menu.getGridWidth());
                        row.addProperty("menu_id", menu.containerId);
                        row.addProperty("valid", menu.stillValid(player));
                        row.addProperty("knows_recipe", player.getRecipeBook().contains(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace("crafting_table"))));
                        row.addProperty("capacity", menu instanceof BackpackCraftingMenu backpack ? backpack.getStorage().getContainerSize() : 0);
                        row.addProperty("result_count", menu.getSlot(0).getItem().getCount());
                        row.addProperty("grid_planks", menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(Items.OAK_PLANKS)).mapToInt(slot -> slot.getItem().getCount()).sum());
                        row.addProperty("stored_planks", menu instanceof BackpackCraftingMenu backpack ? backpack.getBackpack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().filter(stack -> stack.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum() : 0);
                    }
                    if (bucketInventories.containsKey(name) && player.containerMenu instanceof BackpackCraftingMenu menu) {
                        var drops = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                player.getBoundingBox().inflate(32));
                        for (var entity : drops) {
                            if (entity.getItem().is(Items.BUCKET) || entity.getItem().is(Items.CAKE)) entity.addTag("brb_bucket_qa");
                        }
                        row.addProperty("dropped_buckets", drops.stream().filter(entity -> entity.entityTags().contains("brb_bucket_qa")
                                && entity.getItem().is(Items.BUCKET)).mapToInt(entity -> entity.getItem().getCount()).sum());
                        row.addProperty("dropped_cakes", drops.stream().filter(entity -> entity.entityTags().contains("brb_bucket_qa")
                                && entity.getItem().is(Items.CAKE)).mapToInt(entity -> entity.getItem().getCount()).sum());
                        row.addProperty("stored_cakes", menu.getStorage().countItem(Items.CAKE));
                        row.addProperty("stored_buckets", menu.getStorage().countItem(Items.BUCKET));
                        row.addProperty("inventory_cakes", player.getInventory().countItem(Items.CAKE));
                        row.addProperty("inventory_buckets", player.getInventory().countItem(Items.BUCKET));
                        for (var entry : java.util.Map.of("grid_milk", Items.MILK_BUCKET, "grid_buckets", Items.BUCKET,
                                "grid_sugar", Items.SUGAR, "grid_wheat", Items.WHEAT, "grid_eggs", Items.EGG).entrySet()) {
                            row.addProperty(entry.getKey(), menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(entry.getValue()))
                                    .mapToInt(slot -> slot.getItem().getCount()).sum());
                        }
                        row.addProperty("cursor_empty", menu.getCarried().isEmpty());
                        boolean preserved = true;
                        var expected = bucketInventories.get(name);
                        for (int index = 0; index < 36; index++) {
                            if (index != 13 && !ItemStack.matches(expected.get(index), player.getInventory().getItem(index))) preserved = false;
                        }
                        row.addProperty("unrelated_inventory_preserved", preserved);
                    }
                    Path observation = control.resolve(name + "-server.json");
                    Path temporary = control.resolve(name + "-server.json.tmp");
                    Files.writeString(temporary, new GsonBuilder().create().toJson(row));
                    Files.move(temporary, observation, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of(System.getProperty("crafting.qa.control"), "failure"), failure.toString()); }
                catch (Exception ignored) {}
            }
        });
    }
}
