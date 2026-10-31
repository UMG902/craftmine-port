package dev.craftmine.mixin;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import dev.craftmine.client.screen.PlayerUnlocksScreen;
import dev.craftmine.snapshot.CraftmineSnapshotPackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.duck.CraftmineOptionsDuck;

/**
 * Ported from the fork's Minecraft.handleKeybinds addition:
 * while (this.options.keyUnlocks.consumeClick())
 *     this.setScreen(new PlayerUnlocksScreen(this.player.connection.getUnlocks()));
 * Injected at TAIL - ordering versus the vanilla key loops is not observable
 * (each loop consumes its own mapping).
 */
@Mixin(Minecraft.class)
@Environment(EnvType.CLIENT)
public abstract class MinecraftMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("craftmine/snapshot");

    /**
     * Registers the user's installed 25w14craftmine snapshot jar as a
     * built-in, always-on resource pack (the "bring your own files" asset
     * model). PackRepository.sources is an ImmutableSet, so we swap in an
     * extended copy - the same trick fabric-resource-loader uses for its
     * builtin mod packs; the access widener marks the field accessible and
     * mutable. This targets the CLIENT resource-pack repository only (the
     * server's data-pack repositories are built elsewhere and must never see
     * the snapshot's old-format data).
     *
     * <p>Injection point is CRITICAL: the first PackRepository.reload() call
     * inside the constructor (the initial pack discovery, before
     * loadSelectedResourcePacks and before the inline INITIAL reload at
     * openAllSelected()/startReload(INITIAL)/createReload further down).
     * Registering any later (e.g. constructor TAIL, as an earlier build did)
     * is too late: the initial resource reload has already snapshotted
     * openAllSelected(), the pack would only be picked up by the NEXT
     * manual reload (F3+T), and the title screen would keep showing the
     * vanilla MINECRAFT logo instead of the snapshot's CRAFTMINE art.
     * No manual repository.reload() is needed here - vanilla's own reload()
     * immediately follows this injection point and runs the discovery.
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
            )
    )
    private void craftmine$registerSnapshotAssetPack(CallbackInfo ci) {
        PackRepository repository = ((Minecraft) (Object) this).getResourcePackRepository();
        if (repository.sources.contains(CraftmineSnapshotPackSource.INSTANCE)) {
            return;
        }
        Set<RepositorySource> extended = ImmutableSet.<RepositorySource>builder()
                .addAll(repository.sources)
                .add(CraftmineSnapshotPackSource.INSTANCE)
                .build();
        repository.sources = extended;
        LOGGER.info("Craftmine: snapshot asset pack source registered before first pack discovery");
    }

    /**
     * Startup diagnostic: the exact pack list the INITIAL resource reload
     * uses. "craftmine/snapshot" must appear here - if it does not, the
     * snapshot jar was not found (see the locator's warning) or registration
     * failed, and the title screen will show the vanilla MINECRAFT logo.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$logStartupPackList(CallbackInfo ci) {
        LOGGER.info("Craftmine: resource packs active at startup: {}",
                ((Minecraft) (Object) this).getResourcePackRepository().getSelectedIds());
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void craftmine$handleKeyUnlocks(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player == null) {
            return;
        }
        while (((CraftmineOptionsDuck) minecraft.options).craftmine$keyUnlocks().consumeClick()) {
            if (!dev.craftmine.Craftmine.craftmineLoggedKeyOpen) {
                dev.craftmine.Craftmine.craftmineLoggedKeyOpen = true;
                dev.craftmine.Craftmine.LOGGER.info("Craftmine: Player Unlocks opened via keybind");
            }
            // 26.3 moved screen management from Minecraft to Gui (the fork's
            // this.setScreen(...) becomes gui.setScreen - see
            // ClientPacketListenerMixin.craftmine$openMenu)
            minecraft.gui.setScreen(new PlayerUnlocksScreen(
                ((CraftmineClientUnlocksDuck) minecraft.player.connection).craftmine$getUnlocks()));
        }
    }
}
