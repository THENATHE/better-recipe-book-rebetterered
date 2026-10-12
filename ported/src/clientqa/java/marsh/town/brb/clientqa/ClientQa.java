package marsh.town.brb.clientqa;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.GameType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ClientQa implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> evidence = new ArrayList<>();
        TestSingleplayerContext world = null;
        try {
            context.runOnClient(client -> client.options.pauseOnLostFocus = false);
            world = context.worldBuilder().create();
            world.getConnection().waitForChunksDownload();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.awardRecipes(server.getRecipeManager().getRecipes());
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            String suite = System.getProperty("brb.qaSuite", "all");
            if (suite.equals("all") || suite.equals("recipes")) {
                RecipeModeChecks.run(context, world);
                evidence.add("PASS: real recipe grouping, pagination, scrolling and exact pins");
            }
            if (suite.equals("all") || suite.equals("creative")) CreativeChecks.run(context, world, evidence);
            if (suite.equals("all") || suite.equals("crafting")) CraftingChecks.run(context, world, evidence);
            if (suite.equals("all") || suite.equals("workstation")) WorkstationChecks.run(context, world, evidence);
            if (suite.equals("all") || suite.equals("lifecycle")) RecipeLifecycleChecks.run(context, world, evidence);
            evidence.add("RESULT: PASS; " + evidence.stream().filter(line -> line.startsWith("PASS:")).count() + " recorded checks");
            Files.write(Path.of("clientqa-result.txt"), evidence);
        } catch (Throwable failure) {
            evidence.add("RESULT: FAIL; " + failure);
            try { Files.write(Path.of("clientqa-result.txt"), evidence); } catch (Exception ignored) { }
            throw new AssertionError("Better Recipe Book client QA failed", failure);
        } finally {
            context.runOnClient(client -> client.options.keyShift.setDown(false));
            if (world != null) world.close();
        }
    }
}
