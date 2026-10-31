package dev.craftmine.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import dev.craftmine.server.TheGame;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Task 58 (first-entry mine fixes, live side): the 25w14craftmine fork picked
 * up a freshly written mine datapack through its WorldReloader pass (full
 * server rebuild with players parked in the configuration phase), so the mine's
 * biomes were registered BEFORE anything consumed them. The port materializes
 * the mine's entries into the registry layer in place instead
 * (CraftmineRegistryMaterialization), which covers chunk generation and the
 * level itself but leaves TWO stale consumers:
 *
 *  1. The command dispatcher's CommandBuildContext. 26.3 builds it once per
 *     resource load inside the ReloadableServerResources constructor
 *     (CommandBuildContext.simple(loadingContext.lookupWithUpdatedTags(), ...)),
 *     so "/locate biome minecraft:levelN/<biome>" failed to PARSE against the
 *     old registry on first entry (reported: "Can't find element
 *     'minecraft:level1/meadow' of type 'minecraft:worldgen/biome'") while
 *     working after close-and-rejoin, when boot loads the pack from disk.
 *  2. Anything reading the pack-derived RELOADABLE registry layer (26.3's
 *     RegistryLayer.RELOADABLE is rebuilt from packs on every resource load).
 *
 * A full resource reload fixes both, exactly like vanilla /reload:
 * PackRepository.reload() rescans the world's datapack folder (the just-written
 * levelN pack is discovered and default-enabled), then
 * TheGame.reloadResources(selectedIds) rebuilds ReloadableServerResources from
 * disk - which rebuilds the Commands against the freshly loaded registries -
 * and resyncs connected clients. Chunk generation is unaffected either way
 * (the generator already holds direct materialized holders).
 */
public final class CraftmineLiveReload {
    private CraftmineLiveReload() {
    }

    /**
     * Call AFTER the mine datapack JSONs are fully written (synchronize joined)
     * and BEFORE the level is materialized/entered. dimensionId is the level
     * stem id (minecraft:levelN); the datapack folder name equals its path.
     */
    public static void afterDimensionWritten(TheGame theGame, Identifier dimensionId) {
        PackRepository packRepository = theGame.getPackRepository();
        try {
            // Task 59: repair stale generated pack.mcmeta formats BEFORE the rescan,
            // otherwise 26.3 reads them through the fallback-metadata path and marks
            // them TOO_OLD.
            dev.craftmine.server.CraftminePackRepair.repairWorldDatapackFormats(
                theGame.server().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR));
            packRepository.reload();
            // Task 59: FolderRepositorySource ids world-folder packs "file/<name>"
            // (PackLocationInfo id), NOT the bare folder name. With the bare name
            // getPack() returned null, addPack never ran, the reload EXCLUDED the
            // pack, and getSelectedPacks(repo, true) then wrote "file/<name>" into
            // the world's saved DISABLED list - which blocked the boot auto-enable
            // and softlocked every rejoin (no biomes -> no levelN stem).
            String packId = "file/" + dimensionId.getPath();
            if (packRepository.getPack(packId) != null) {
                if (packRepository.addPack(packId)) {
                    CraftmineLiveReload.LOGGER.info("craftmine-live-reload: enabled datapack {} for the reload", packId);
                }
            } else {
                CraftmineLiveReload.LOGGER.warn("craftmine-live-reload: datapack {} not discovered after repository reload (folder missing?)", packId);
            }
            List<String> selected = new ArrayList<String>(packRepository.getSelectedIds());
            if (!selected.contains(packId)) {
                // last resort: still reload with the pack injected so the registry
                // layer and the command dispatcher see the mine's biomes
                selected.add(packId);
            }
            theGame.reloadResources(selected).whenComplete((unused, throwable) -> {
                // Task 60: a reloadResources future that completes exceptionally is
                // swallowed silently by callers (managedBlock only waits for DONE),
                // which strands the BOOT-TIME command dispatcher - that dispatcher
                // cannot parse /locate biome minecraft:levelN/<biome> and locate
                // looks "still dead" even though everything else works. Log the
                // completion state and probe which registry views now contain the
                // mine's biomes (dispatcher context vs live composite).
                if (throwable != null) {
                    CraftmineLiveReload.LOGGER.error("craftmine-live-reload: reload future completed exceptionally - live dispatcher NOT rebuilt (locate will keep failing for this session)", throwable);
                    return;
                }
                try {
                    long composite = theGame.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElementIds().map(key -> key.identifier()).filter(id -> id.getNamespace().equals(dimensionId.getNamespace()) && id.getPath().startsWith(dimensionId.getPath() + "/")).count();
                    long dispatcher = theGame.reloadableRegistries().lookup().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElementIds().map(key -> key.identifier()).filter(id -> id.getNamespace().equals(dimensionId.getNamespace()) && id.getPath().startsWith(dimensionId.getPath() + "/")).count();
                    CraftmineLiveReload.LOGGER.info("craftmine-live-reload: reload completed; mine biome(s) visible composite={} dispatcher={} (dispatcher = the registry view the reloaded Commands were built against)", composite, dispatcher);
                } catch (Exception exception) {
                    CraftmineLiveReload.LOGGER.warn("craftmine-live-reload: post-reload registry probe failed", exception);
                }
            });
        }
        catch (Exception exception) {
            dev.craftmine.server.CraftmineLiveReload.LOGGER.error(
                "craftmine-live-reload: resource reload after writing {} failed (mine entry continues with in-place materialization)",
                dimensionId, exception);
        }
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
}
