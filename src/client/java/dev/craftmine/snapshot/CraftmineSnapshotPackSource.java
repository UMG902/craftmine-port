package dev.craftmine.snapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

/**
 * Registers the user's installed 25w14craftmine snapshot as a built-in client
 * resource pack. Only the checked-in, explicit asset allowlist is exposed.
 */
public final class CraftmineSnapshotPackSource implements RepositorySource {
    public static final String PACK_ID = "craftmine/snapshot";
    public static final CraftmineSnapshotPackSource INSTANCE = new CraftmineSnapshotPackSource();

    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/snapshot");
    private static final Set<String> SNAPSHOT_ASSETS = CraftmineSnapshotPackResources.snapshotAssetAllowlist();

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
                        Component.literal("Approved assets served from your installed 25w14craftmine snapshot"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.DEFAULT_FLAGS,
                        List.of()),
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        LOGGER.info("Craftmine: discovered snapshot asset pack '{}' from {} ({} allowlisted paths)",
                PACK_ID, jar, SNAPSHOT_ASSETS.size());
        sink.accept(pack);
    }

    private record SnapshotResourcesSupplier(Path jar) implements Pack.ResourcesSupplier {
        @Override
        public PackMetadataResources openMetadata(PackLocationInfo info) {
            return new CraftmineSnapshotPackResources(info, this.jar, SNAPSHOT_ASSETS);
        }

        @Override
        public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) {
            return Stream.of(new CraftmineSnapshotPackResources(info, this.jar, SNAPSHOT_ASSETS));
        }
    }
}
