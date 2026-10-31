/*
 * Exposes the `minecraft` field of ClientCommonPacketListenerImpl (the parent
 * of ClientPacketListener). @Shadow cannot resolve inherited fields, so the
 * packet-handler mixin reads it through this accessor instead.
 */
package dev.craftmine.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {
    @Accessor("minecraft")
    Minecraft craftmine$minecraft();
}
