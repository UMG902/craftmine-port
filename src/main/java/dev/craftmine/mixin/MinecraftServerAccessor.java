/*
 * Exposes MinecraftServer.storageSource for unlock data paths and the
 * registries field for CraftmineRegistryMaterialization (the field is made
 * accessible + mutable via the access widener; the configuration-phase
 * registry sync reads exactly this field).
 */
package dev.craftmine.mixin;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess craftmine$storageSource();

    @Accessor("registries")
    LayeredRegistryAccess<RegistryLayer> craftmine$registries();

    /**
     * Task 60: vanilla's MinecraftServer.reloadResources swaps THIS field
     * (this.resources = new ReloadableResources(...)); every player and console
     * command dispatches through server.getCommands() -> resources.managers.
     * The port's TheGame.reloadResources used to swap only TheGame.resources,
     * so the live dispatcher never picked up reloaded registries and
     * /locate biome minecraft:levelN/<biome> stayed dead for in-session mines.
     */
    @Accessor("resources")
    void craftmine$setResources(MinecraftServer.ReloadableResources resources);

    @Accessor("registries")
    void craftmine$setRegistries(LayeredRegistryAccess<RegistryLayer> registries);

    /**
     * The vanilla server's authoritative level map: tickChildren's level loop,
     * save-on-stop and every other vanilla path iterate THIS map, not
     * TheGame.levels. Live-created mine levels must be registered here or they
     * never tick (dead chunk system: tickets never promote, chunks never become
     * sendable, clients hang on "Loading terrain..." forever).
     */
    @Accessor("levels")
    java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.server.level.ServerLevel> craftmine$levels();
}
