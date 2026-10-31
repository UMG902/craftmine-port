/*
 * Task 85 (EULA de-shadow): replaces the shipped DimensionType overlay copy
 * (task 28) with a mixin on the vanilla 26.3 class. The fork delta is ONLY
 * the network codec pair: vanilla 26.3 regressed to the id-only
 * holderRegistry codec, so every login/respawn packet for a player in a
 * level carrying a DIRECT dimension-type holder died with
 * "IllegalArgumentException: Can't find id for 'Direct{...}'" (the
 * Dimension Control's sky-gated type and any inline level_stem type).
 * The snapshot (25w14craftmine) codec travels registered types by registry
 * id and DIRECT holders by value; everything else (record shape, codecs,
 * nested Skybox/MonsterSettings) is byte-identical between the overlay and
 * vanilla 26.3.
 */
package dev.craftmine.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionType.class)
public abstract class DimensionTypeMixin {

    @Shadow
    @Final
    @Mutable
    private static Codec<DimensionType> NETWORK_CODEC;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, Holder<DimensionType>> STREAM_CODEC;

    @Inject(method = "<clinit>()V", at = @At("TAIL"))
    private static void craftmine$snapshotStreamCodec(CallbackInfo ci) {
        // 25w14craftmine: registered dimension types travel by registry id,
        // DIRECT holders (the Dimension Control sky-gate type, inline
        // level_stem types) travel by value. Network packet codecs compose
        // DimensionType.STREAM_CODEC by reference AFTER this class
        // initializes, so reassigning here is picked up everywhere.
        STREAM_CODEC = ByteBufCodecs.holder(
            Registries.DIMENSION_TYPE,
            ByteBufCodecs.fromCodecWithRegistries(NETWORK_CODEC));
    }
}
