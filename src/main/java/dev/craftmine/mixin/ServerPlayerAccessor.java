package dev.craftmine.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes ServerPlayer's private container-id machinery so the craftmine menu
 * opener (CraftmineServerHooks.openCraftmineMenu) can replicate the vanilla
 * openMenu body while sending the fork's ClientboundOpenWindowPacket (which
 * carries the menu additional data the client needs) instead of the vanilla
 * ClientboundOpenScreenPacket.
 */
@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {

    @Accessor("containerCounter")
    int craftmine$getContainerCounter();

    @Invoker("nextContainerCounter")
    void craftmine$nextContainerCounter();

    @Invoker("initMenu")
    void craftmine$initMenu(AbstractContainerMenu menu);
}
