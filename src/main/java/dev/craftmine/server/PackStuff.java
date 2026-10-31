package dev.craftmine.server;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Boot-time datapack reconciliation, adapted from the vanilla server's
 * pack configuration with one mine-specific twist: generated mine packs
 * that a previous session's live reload stranded on the saved disabled
 * list get re-enabled (Task 59 softlock fix below).
 */
public class PackStuff {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VANILLA_PACK_ID = "vanilla";

    public static WorldDataConfiguration configurePackRepository(PackRepository repository, WorldDataConfiguration config, boolean safeMode, boolean vanillaOnly) {
        DataPackConfig savedPacks = config.dataPacks();
        FeatureFlagSet enabledFeatures = safeMode ? FeatureFlagSet.of() : config.enabledFeatures();
        FeatureFlagSet allowedFeatures = safeMode ? FeatureFlags.REGISTRY.allFlags() : config.enabledFeatures();
        repository.reload();
        if (vanillaOnly) {
            return configureRepositoryWithSelection(repository, List.of(VANILLA_PACK_ID), enabledFeatures, false);
        }
        LinkedHashSet<String> selected = Sets.newLinkedHashSet();
        for (String packId : savedPacks.getEnabled()) {
            if (repository.isAvailable(packId)) {
                selected.add(packId);
            } else {
                LOGGER.warn("Missing data pack {}", packId);
            }
        }
        for (Pack pack : repository.getAvailablePacks()) {
            String packId = pack.getId();
            // Task 59: a previous session's live reload could write generated mine
            // packs onto the saved disabled list (available-but-unselected packs land
            // there). That blocked the boot auto-enable and softlocked rejoined mines
            // (datapack never loaded -> no biome registry entries -> no level stem).
            // The fork always loads its generated dimensions, so re-enable ours.
            if (savedPacks.getDisabled().contains(packId)
                && !CraftminePackRepair.isGeneratedMinePack(pack)) {
                continue;
            }
            if (savedPacks.getDisabled().contains(packId)) {
                LOGGER.info("Found craftmine generated dimension pack {} on the saved disabled list, re-enabling", packId);
            }
            FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
            boolean alreadySelected = selected.contains(packId);
            if (!alreadySelected && pack.getPackSource().shouldAddAutomatically()) {
                if (requestedFeatures.isSubsetOf(allowedFeatures)) {
                    LOGGER.info("Found new data pack {}, loading it automatically", packId);
                    selected.add(packId);
                } else {
                    LOGGER.info("Found new data pack {}, but can't load it due to missing features {}",
                        packId, FeatureFlags.printMissingFlags(allowedFeatures, requestedFeatures));
                }
            }
            if (alreadySelected && !requestedFeatures.isSubsetOf(allowedFeatures)) {
                LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.",
                    packId, FeatureFlags.printMissingFlags(allowedFeatures, requestedFeatures));
                selected.remove(packId);
            }
        }
        if (selected.isEmpty()) {
            LOGGER.info("No datapacks selected, forcing vanilla");
            selected.add(VANILLA_PACK_ID);
        }
        return configureRepositoryWithSelection(repository, selected, enabledFeatures, true);
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(PackRepository repository, Collection<String> selection, FeatureFlagSet enabledFeatures, boolean recordDisabled) {
        repository.setSelected(selection);
        enableForcedFeaturePacks(repository, enabledFeatures);
        DataPackConfig packs = getSelectedPacks(repository, recordDisabled);
        FeatureFlagSet requested = repository.getRequestedFeatureFlags().join(enabledFeatures);
        return new WorldDataConfiguration(packs, requested);
    }

    /** Turns on FEATURE-source packs that satisfy feature flags the world asks for. */
    private static void enableForcedFeaturePacks(PackRepository repository, FeatureFlagSet enabledFeatures) {
        FeatureFlagSet availableFlags = repository.getRequestedFeatureFlags();
        FeatureFlagSet missingFlags = enabledFeatures.subtract(availableFlags);
        if (missingFlags.isEmpty()) {
            return;
        }
        ObjectArraySet<String> selected = new ObjectArraySet<>(repository.getSelectedIds());
        for (Pack pack : repository.getAvailablePacks()) {
            if (missingFlags.isEmpty()) {
                break;
            }
            if (pack.getPackSource() != PackSource.FEATURE) {
                continue;
            }
            String packId = pack.getId();
            FeatureFlagSet packFlags = pack.getRequestedFeatures();
            if (packFlags.isEmpty() || !packFlags.intersects(missingFlags) || !packFlags.isSubsetOf(enabledFeatures)) {
                continue;
            }
            if (!selected.add(packId)) {
                throw new IllegalStateException("Tried to force '" + packId + "', but it was already enabled");
            }
            LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", packId);
            missingFlags = missingFlags.subtract(packFlags);
        }
        repository.setSelected(selected);
    }

    public static DataPackConfig getSelectedPacks(PackRepository repository, boolean recordDisabled) {
        Collection<String> selectedIds = repository.getSelectedIds();
        ImmutableList<String> enabled = ImmutableList.copyOf(selectedIds);
        List<String> disabled = recordDisabled
            ? repository.getAvailableIds().stream().filter(id -> !selectedIds.contains(id)).toList()
            : List.of();
        return new DataPackConfig(enabled, disabled);
    }
}
