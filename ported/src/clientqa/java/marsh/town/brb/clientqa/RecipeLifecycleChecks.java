package marsh.town.brb.clientqa;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.util.RecipeUnlockUtil;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Uses real respawn packets to exercise a retained recipe book across dimensions. */
public final class RecipeLifecycleChecks {
    private RecipeLifecycleChecks() {}

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<String> evidence) {
        boolean originalUnlock = context.computeOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll);
        var originalDimension = context.computeOnClient(client -> client.level.dimension());
        var originalPosition = context.computeOnClient(client -> client.player.position());
        var originalMode = world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer());
        try {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                BetterRecipeBook.config.newRecipes.unlockAll = false;
                RecipeUnlockUtil.unlockRecipesIfRequired();
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SPECTATOR);
                player.resetRecipes(server.getRecipeManager().getRecipes());
                player.awardRecipes(server.getRecipeManager().getRecipes().stream()
                        .filter(recipe -> recipe.id().identifier().equals(Identifier.withDefaultNamespace("oak_planks")))
                        .toList());
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            ClientRecipeBook originalBook = context.computeOnClient(client -> client.player.getRecipeBook());
            Set<Integer> learned = context.computeOnClient(client -> ids(client.player.getRecipeBook(), false));
            check(!learned.isEmpty(), "dimension fixture retains a server-learned recipe", evidence);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), true).isEmpty()),
                    "unlock disabled starts without local recipe entries", evidence);

            context.runOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll = true);
            context.waitFor(client -> !ids(client.player.getRecipeBook(), true).isEmpty(), 100);
            int localCount = context.computeOnClient(client -> ids(client.player.getRecipeBook(), true).size());
            var destination = originalDimension.equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER;
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.getLevel(destination), 0.5, 128.0, 0.5, Set.of(), 0, 0, true);
            });
            context.waitFor(client -> client.level != null && client.level.dimension().equals(destination), 400);
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            check(context.computeOnClient(client -> client.player.getRecipeBook() == originalBook),
                    "actual dimension change retains vanilla recipe book identity", evidence);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), true).size() == localCount),
                    "dimension change preserves the complete local recipe catalogue without duplicates", evidence);

            context.runOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll = false);
            context.waitTicks(5);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), true).isEmpty()),
                    "disabling unlock after dimension change removes every injected entry", evidence);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), false).equals(learned)),
                    "dimension cleanup preserves server-learned entries", evidence);

            context.runOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll = true);
            context.waitFor(client -> ids(client.player.getRecipeBook(), true).size() == localCount, 100);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), false).equals(learned)),
                    "re-enabling unlock after dimension cleanup preserves learned entries", evidence);
            context.runOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll = false);
            context.waitTicks(5);
            check(context.computeOnClient(client -> ids(client.player.getRecipeBook(), true).isEmpty()),
                    "repeated unlock toggle removes re-created local entries", evidence);
        } finally {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.getLevel(originalDimension), originalPosition.x, originalPosition.y,
                        originalPosition.z, Set.of(), 0, 0, true);
                player.setGameMode(originalMode);
                player.awardRecipes(server.getRecipeManager().getRecipes());
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> client.level != null && client.level.dimension().equals(originalDimension), 400);
            context.runOnClient(client -> BetterRecipeBook.config.newRecipes.unlockAll = originalUnlock);
            context.waitTicks(5);
        }
    }

    private static Set<Integer> ids(ClientRecipeBook book, boolean local) {
        return book.getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                .map(RecipeDisplayEntry::id).map(id -> id.index()).filter(id -> (id < 0) == local)
                .collect(Collectors.toSet());
    }

    private static void check(boolean condition, String description, List<String> evidence) {
        if (!condition) throw new AssertionError(description);
        evidence.add("PASS: " + description);
    }
}
