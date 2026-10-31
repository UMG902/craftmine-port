package dev.craftmine.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import dev.craftmine.server.TheGame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Runtime materialization of generated mine dimensions.
 *
 * The original 25w14craftmine achieves seamless mine entry by tearing the whole
 * TheGame down (players -> configuration phase) and running a full WorldReloader
 * pass, so the freshly saved level datapack is picked up and createLevels() sees
 * the new level stem. 26.3 has no configuration-phase dimension reload to hook,
 * and the port's server loop is single-shot — so instead this decodes the saved
 * dimension JSON directly (the generator is self-contained: modified biomes were
 * inlined by the encoder) and constructs the ServerLevel in place.
 */
public final class CraftmineLevelCreation {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private CraftmineLevelCreation() {
    }

    @Nullable
    public static net.minecraft.server.level.ServerLevel ensureGeneratedLevel(TheGame theGame, ResourceKey<Level> levelKey) {
        net.minecraft.server.level.ServerLevel existing = theGame.getLevel(levelKey);
        if (existing != null) {
            return existing;
        }
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.identifier());
        try {
            LevelStem stem = loadGeneratedLevelStem(theGame, stemKey);
            if (stem == null) {
                LOGGER.error("Craftmine: generated dimension JSON missing for {}, cannot enter mine", stemKey.identifier());
                return null;
            }
            return theGame.createGeneratedLevel(stemKey, stem);
        }
        catch (Exception exception) {
            LOGGER.error("Craftmine: failed to materialize generated dimension {}", stemKey.identifier(), exception);
            return null;
        }
    }

    @Nullable
    private static LevelStem loadGeneratedLevelStem(TheGame theGame, ResourceKey<LevelStem> stemKey) throws Exception {
        net.minecraft.resources.Identifier id = stemKey.identifier();
        Path jsonPath = theGame.server().getWorldPath(LevelResource.DATAPACK_DIR)
            .resolve(id.getPath())
            .resolve("data")
            .resolve(id.getNamespace())
            .resolve("dimension")
            .resolve(id.getPath() + ".json");
        if (!Files.isRegularFile(jsonPath)) {
            return null;
        }
        JsonElement element = JsonParser.parseString(Files.readString(jsonPath));
        RegistryOps<JsonElement> ops = theGame.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        DataResult<LevelStem> result = LevelStem.CODEC.parse(ops, element);
        return result.getOrThrow(message -> {
            // Task 59: the raw error embeds the whole multi_noise JSON plus dozens of
            // per-entry holder failures; trim it and state the real cause.
            String summary = message.length() > 400 ? message.substring(0, 400) + "...(" + message.length() + " chars)" : message;
            if (message.contains("worldgen/biome")) {
                summary += " [the mine datapack's biomes are NOT registered: the generated datapack did not load - check craftmine-live-reload / craftmine-pack-repair log lines]";
            }
            return new IllegalStateException("Invalid generated dimension JSON for " + id + ": " + summary);
        });
    }
}
