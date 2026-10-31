package dev.craftmine.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public class CraftmineClient implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
                // Runs at the head of Minecraft's constructor (Fabric patch):
                // registries are bootstrapped+frozen already (all craftmine content
                // exists) and every consumer of the maps below (renderer creation,
                // ParticleResources, item model baking) comes later.
                CraftmineClientRenderers.registerAll();
                // Task 31: the shared unlock-gating slot classes resolve the
                // unlock state through this client resolver (the client keeps
                // its unlock state on the ClientPacketListener, the fork kept
                // it on the Player).
                dev.craftmine.inventory.CraftmineUnlockGate.setClientResolver((player, holder) -> {
                        var connection = Minecraft.getInstance().getConnection();
                        return connection != null && ((dev.craftmine.duck.CraftmineClientUnlocksDuck) connection)
                                .craftmine$getUnlocks().isActive(holder);
                });
        }
}
