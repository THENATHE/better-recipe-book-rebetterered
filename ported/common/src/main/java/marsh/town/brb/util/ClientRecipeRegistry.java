package marsh.town.brb.util;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import marsh.town.brb.BetterRecipeBook;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/** Full server recipes are not part of the vanilla client protocol. */
public final class ClientRecipeRegistry {
    private static volatile MinecraftServer integratedServer;
    private static volatile HolderLookup.Provider integratedRecipes;
    private static ClientLevel loadedLevel;
    private static RegistryAccess localRecipes;
    private static HolderLookup.Provider sourceSnapshot;

    private ClientRecipeRegistry() {}

    // Called by Fabric lifecycle events on the server thread after registries are frozen.
    public static void snapshot(MinecraftServer server) {
        integratedRecipes = server.reloadableRegistries().lookup();
        integratedServer = server;
        Minecraft.getInstance().execute(() -> {
            clear();
            RecipeUnlockUtil.invalidate();
            marsh.town.brb.workstation.WorkstationRecipes.clear();
        });
    }

    public static void serverStopped(MinecraftServer server) {
        if (integratedServer == server) { integratedServer = null; integratedRecipes = null; }
    }

    public static void clear() { loadedLevel = null; localRecipes = null; sourceSnapshot = null; }

    public static RegistryAccess provider() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return RegistryAccess.EMPTY;
        HolderLookup.Provider snapshot = integratedRecipes;
        boolean integrated = client.getSingleplayerServer() == integratedServer && snapshot != null;
        HolderLookup.Provider source = integrated ? snapshot : null;
        if (loadedLevel != client.level || localRecipes == null || sourceSnapshot != source) {
            localRecipes = integrated ? rebaseRecipes(snapshot, client.level.registryAccess())
                    : loadInstalledRecipes(client.level.registryAccess());
            loadedLevel = client.level;
            sourceSnapshot = source;
        }
        return localRecipes;
    }

    private static RegistryAccess loadInstalledRecipes(RegistryAccess clientRegistries) {
        var recipes = new MappedRegistry<Recipe<?>>(Registries.RECIPE, Lifecycle.stable());
        var ops = RegistryOps.create(JsonOps.INSTANCE, clientRegistries);
        ClassLoader loader = ClientRecipeRegistry.class.getClassLoader();
        try (var index = loader.getResourceAsStream("brb-vanilla-recipes.txt")) {
            if (index != null) {
                for (String path : new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8)).lines().toList()) {
                    try (var stream = loader.getResourceAsStream(path)) {
                        if (stream == null) continue;
                        var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                        var recipe = Recipe.DIRECT_CODEC.parse(ops, json).result();
                        if (recipe.isPresent()) {
                            String name = path.substring("data/minecraft/recipe/".length(), path.length() - 5);
                            recipes.register(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(name)),
                                    recipe.get(), RegistrationInfo.BUILT_IN);
                        }
                    } catch (Exception failure) { BetterRecipeBook.LOGGER.debug("Cannot decode local recipe {}", path, failure); }
                }
            }
        } catch (Exception failure) { BetterRecipeBook.LOGGER.warn("Cannot read vanilla recipe index", failure); }
        // Installed mod data can supplement vanilla previews. Remote server datapacks remain authoritative.
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getId().equals("minecraft")) continue;
            for (Path root : mod.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) continue;
                try (var files = Files.walk(data)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).toList()) {
                        Path relative = data.relativize(file);
                        if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals("recipe")) continue;
                        String recipePath = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                        Identifier id = Identifier.fromNamespaceAndPath(relative.getName(0).toString(), recipePath.substring(0, recipePath.length() - 5));
                        if (recipes.containsKey(id)) continue;
                        try (var reader = Files.newBufferedReader(file)) {
                            var decoded = Recipe.DIRECT_CODEC.parse(ops, JsonParser.parseReader(reader)).result();
                            if (decoded.isPresent()) recipes.register(ResourceKey.create(Registries.RECIPE, id), decoded.get(), RegistrationInfo.BUILT_IN);
                        } catch (Exception failure) { BetterRecipeBook.LOGGER.debug("Cannot decode installed recipe {}", id, failure); }
                    }
                } catch (Exception failure) { BetterRecipeBook.LOGGER.debug("Cannot read installed recipe data for {}", mod.getMetadata().getId(), failure); }
            }
        }
        return combine(clientRegistries, recipes.freeze());
    }

    private static RegistryAccess rebaseRecipes(HolderLookup.Provider serverRegistries, RegistryAccess clientRegistries) {
        var recipes = new MappedRegistry<Recipe<?>>(Registries.RECIPE, Lifecycle.stable());
        var serverOps = RegistryOps.create(JsonOps.INSTANCE, serverRegistries);
        var clientOps = RegistryOps.create(JsonOps.INSTANCE, clientRegistries);
        serverRegistries.lookupOrThrow(Registries.RECIPE).listElements().forEach(holder -> {
            var encoded = Recipe.DIRECT_CODEC.encodeStart(serverOps, holder.value());
            var decoded = encoded.flatMap(json -> Recipe.DIRECT_CODEC.parse(clientOps, json));
            decoded.result().ifPresent(recipe -> recipes.register(holder.key(), recipe, RegistrationInfo.BUILT_IN));
            decoded.error().ifPresent(error -> BetterRecipeBook.LOGGER.debug("Cannot rebase integrated recipe {}: {}", holder.key(), error.message()));
        });
        return combine(clientRegistries, recipes.freeze());
    }

    private static RegistryAccess combine(RegistryAccess clientRegistries, Registry<Recipe<?>> frozen) {
        return new RegistryAccess() {
            @Override @SuppressWarnings("unchecked")
            public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
                return key.equals(Registries.RECIPE) ? Optional.of((Registry<E>) frozen) : clientRegistries.lookup(key);
            }
            @Override public Stream<RegistryEntry<?>> registries() {
                return Stream.concat(clientRegistries.registries().filter(entry -> !entry.key().equals(Registries.RECIPE)),
                        Stream.of(new RegistryEntry<>(Registries.RECIPE, frozen)));
            }
        };
    }
}
