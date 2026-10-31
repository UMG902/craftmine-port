/*
 * Exposes ServerCommonPacketListenerImpl's protected `connection` field
 * (declared on the common superclass of both play and configuration
 * listeners) so ServerGamePacketListenerMixin can read it. The project's
 * MixinAudit forbids @Shadow on inherited fields, so this accessor follows
 * the established ServerPlayerAccessor pattern instead.
 */
package dev.craftmine.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor {

    @Accessor("connection")
    Connection craftmine$getConnection();
}
