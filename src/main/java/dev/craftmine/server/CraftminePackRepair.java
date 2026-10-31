package dev.craftmine.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
// note: Pack.open() returns Stream<PackResources> (overlays) - handled below
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import dev.craftmine.server.GeneratedMarkerMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.Logger;

/**
 * Task 59 (mine pipeline recovery): helpers around generated mine datapacks.
 *
 * 26.3 rejects a bare "pack_format" in [15..81] (JsonParseException, then the
 * fallback metadata path marks the pack PackCompatibility.TOO_OLD). The port
 * wrote fork-era format 71, so every generated pack was TOO_OLD. A TOO_OLD
 * pack still auto-enables at boot UNLESS the world's saved pack config lists
 * it as disabled - and task 58's live reload did exactly that (its reload
 * collection missed the pack because FolderRepositorySource ids are
 * "file/<name>"). This class:
 *
 * 1. repairWorldDatapackFormats: rewrites <world>/datapacks/<name>/pack.mcmeta
 *    of OUR packs (the "generated" marker section) to the current data pack
 *    format, so discovery parses cleanly from then on. Safe + idempotent.
 * 2. isGeneratedMinePack: marker check used by PackStuff.configurePackRepository
 *    to re-enable generated packs that a previous session's reload put on the
 *    saved disabled list (the fork always loads its generated dimensions).
 */
public final class CraftminePackRepair {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private CraftminePackRepair() {
    }

    /**
     * Current data pack format major (26.3: 121). The port declares
     * [current, current], which parses standalone (>= 82) with no
     * supported_formats field.
     */
    public static int currentDataPackFormatMajor() {
        return SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
    }

    /**
     * Rewrites the "pack" section of every generated pack's pack.mcmeta whose
     * declared version is not the current data pack major. Only touches packs
     * carrying the "generated" marker section (written by DimensionGenerator).
     */
    public static void repairWorldDatapackFormats(Path datapacksDir) {
        if (datapacksDir == null || !Files.isDirectory(datapacksDir)) {
            return;
        }
        int expected = currentDataPackFormatMajor();
        int repaired = 0;
        try (Stream<Path> packs = Files.list(datapacksDir)) {
            for (Path packDir : (Iterable<Path>) packs::iterator) {
                if (!Files.isDirectory(packDir)) {
                    continue;
                }
                Path mcmeta = packDir.resolve("pack.mcmeta");
                if (!Files.isRegularFile(mcmeta)) {
                    continue;
                }
                try {
                    JsonElement rootElement = JsonParser.parseString(Files.readString(mcmeta));
                    if (!rootElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject root = rootElement.getAsJsonObject();
                    if (!root.has("generated")) {
                        // only OUR generated packs (marker section from DimensionGenerator)
                        continue;
                    }
                    if (declaresFormat(root, expected)) {
                        continue;
                    }
                    JsonObject packSection = root.has("pack") && root.get("pack").isJsonObject()
                        ? root.getAsJsonObject("pack")
                        : new JsonObject();
                    JsonElement description = packSection.get("description");
                    JsonObject fresh = new JsonObject();
                    if (description != null) {
                        fresh.add("description", description);
                    }
                    fresh.addProperty("pack_format", expected);
                    root.add("pack", fresh);
                    Files.writeString(mcmeta, root.toString());
                    LOGGER.info("craftmine-pack-repair: rewrote {} to data pack format {} (was stale)", mcmeta, expected);
                    repaired++;
                }
                catch (Exception exception) {
                    LOGGER.warn("craftmine-pack-repair: failed to inspect {}", mcmeta, exception);
                }
            }
        }
        catch (Exception exception) {
            LOGGER.warn("craftmine-pack-repair: failed to scan {}", datapacksDir, exception);
        }
        if (repaired > 0) {
            LOGGER.info("craftmine-pack-repair: repaired {} generated pack.mcmeta file(s)", repaired);
        }
    }

    /** True when the "pack" section declares exactly the expected major version. */
    private static boolean declaresFormat(JsonObject root, int expected) {
        if (!root.has("pack") || !root.getAsJsonObject("pack").isJsonObject()) {
            return false;
        }
        JsonObject pack = root.getAsJsonObject("pack");
        for (String key : new String[] {"pack_format", "min_format", "max_format"}) {
            if (!pack.has(key)) {
                continue;
            }
            JsonElement element = pack.get(key);
            int major = -1;
            if (element.isJsonPrimitive()) {
                major = element.getAsInt();
            } else if (element.isJsonArray() && element.getAsJsonArray().size() > 0
                && element.getAsJsonArray().get(0).isJsonPrimitive()) {
                major = element.getAsJsonArray().get(0).getAsInt();
            } else if (element.isJsonObject() && element.getAsJsonObject().has("major")) {
                major = element.getAsJsonObject().get("major").getAsInt();
            }
            if (major != expected) {
                return false;
            }
        }
        return pack.has("pack_format") || pack.has("min_format") || pack.has("max_format");
    }

    /**
     * True when the pack's pack.mcmeta carries the "generated" marker section.
     * Prefiltered on the "file/" id prefix so only folder-discovered packs are
     * opened; any read failure returns false (treated as not ours).
     */
    public static boolean isGeneratedMinePack(Pack pack) {
        if (pack == null || !pack.getId().startsWith("file/")) {
            return false;
        }
        try (Stream<PackResources> resources = pack.open()) {
            for (PackResources packResources : (Iterable<PackResources>) resources::iterator) {
                if (packResources.getMetadataSection(GeneratedMarkerMetadataSection.TYPE) != null) {
                    return true;
                }
            }
        }
        catch (Exception exception) {
            return false;
        }
        return false;
    }
}
