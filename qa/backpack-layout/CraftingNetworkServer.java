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
                        player.getInventory().clearContent();
                        me.pajic.toolpouch.ToolPouch.CONFIG.allowUseFromInventory.accept(true);
                        var hammer = command.get("size").getAsInt() == 2 ? BackpackCrafting.IRON_HAMMER : BackpackCrafting.DIAMOND_HAMMER;
                        var pouch = new ItemStack(me.pajic.toolpouch.item.ModItems.TOOL_POUCH);
                        pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(hammer))));
                        player.getInventory().setItem(12, pouch);
                        var backpack = new ItemStack(command.has("tier") && command.get("tier").getAsString().equals("netherite")
                                ? me.pajic.tiered_backpacks.item.ModItems.NETHERITE_BACKPACK
                                : me.pajic.tiered_backpacks.item.ModItems.LEATHER_BACKPACK);
                        boolean baseline = command.has("baseline") && command.get("baseline").getAsBoolean();
                        boolean fromBackpack = !baseline && command.has("backpack") && command.get("backpack").getAsBoolean();
                        backpack.set(DataComponents.CONTAINER, fromBackpack ? ItemContainerContents.fromItems(List.of(new ItemStack(Items.OAK_PLANKS, 12))) : ItemContainerContents.EMPTY);
                        player.getInventory().setItem(13, backpack);
                        if (!fromBackpack) player.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 12));
                        player.awardRecipes(server.getRecipeManager().getRecipes());
                        if (baseline) {
                            var position = player.blockPosition().below();
                            player.level().setBlock(position, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                            player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, owner) -> new net.minecraft.world.inventory.CraftingMenu(id, inventory, net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), position)), net.minecraft.network.chat.Component.literal("Crafting baseline")));
                        }
                        else me.pajic.tiered_backpacks.platform.MultiLoaderUtil.INSTANCE.openBackpackScreen(player, backpack);
                    }
                    var row = new JsonObject();
                    row.addProperty("menu", player.containerMenu.getClass().getName());
                    row.addProperty("crafted_tables", player.getInventory().countItem(Items.CRAFTING_TABLE));
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
