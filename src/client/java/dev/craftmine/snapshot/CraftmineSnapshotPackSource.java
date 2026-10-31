package dev.craftmine.snapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Registers the user's installed 25w14craftmine snapshot jar as a built-in
 * resource pack on the client ("bring your own files" asset model).
 *
 * <p>The pack is required + fixed + TOP: it cannot be toggled off (the mod's
 * content needs its assets), it sits above every user-selected pack, and it
 * only serves paths this mod does not ship itself (see
 * {@link CraftmineSnapshotPackResources}), so it can never shadow our
 * 26.3-format overrides. Metadata is supplied directly instead of being read
 * from the snapshot's pack.mcmeta - that file still advertises the snapshot's
 * own pack format, which must not mark the pack incompatible.
 */
public final class CraftmineSnapshotPackSource implements RepositorySource {
    public static final String PACK_ID = "craftmine/snapshot";
    public static final CraftmineSnapshotPackSource INSTANCE = new CraftmineSnapshotPackSource();

    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/snapshot");

    private CraftmineSnapshotPackSource() {
    }

    @Override
    public void loadPacks(Consumer<Pack> sink) {
        Path jar = CraftmineSnapshotLocator.locate();
        if (jar == null) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(
                PACK_ID,
                Component.literal("Craftmine snapshot assets"),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack pack = new Pack(
                info,
                new SnapshotResourcesSupplier(jar),
                new Pack.Metadata(
                        Component.literal("Assets served from your installed 25w14craftmine snapshot"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.DEFAULT_FLAGS,
                        List.of()),
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        // Diagnostic: proves discovery ran (this only prints when the pack
        // actually enters the repository - i.e. BEFORE the initial reload).
        LOGGER.info("Craftmine: discovered snapshot asset pack '{}' from {}", PACK_ID, jar);
        sink.accept(pack);
    }

    private record SnapshotResourcesSupplier(Path jar) implements Pack.ResourcesSupplier {
        @Override
        public PackMetadataResources openMetadata(PackLocationInfo info) {
            return new CraftmineSnapshotPackResources(info, this.jar, modBlocklist());
        }

        @Override
        public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) {
            return Stream.of(new CraftmineSnapshotPackResources(info, this.jar, modBlocklist()));
        }

        private static java.util.function.Predicate<String> modBlocklist() {
            return CraftmineSnapshotPackResources.modShippedAssetPaths();
        }
    }
}
