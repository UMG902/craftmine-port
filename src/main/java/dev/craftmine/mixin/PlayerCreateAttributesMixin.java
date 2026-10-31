package dev.craftmine.mixin;

import dev.craftmine.registry.CraftmineAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 45 (mine-exit crash): the craftmine attributes (experience_gain_modifier,
 * max_jumps, player.pickup_area_size) were registered in the attribute REGISTRY
 * but never added to the PLAYER's default AttributeSupplier - the fork added
 * them to its Player.createAttributes() (vanilla DefaultAttributes builds the
 * player supplier straight from Player.createAttributes().build(), verified in
 * the 26.3 decompile). Any getAttributeValue / unlock AttributeModifier on them
 * therefore threw IllegalArgumentException "Can't find attribute minecraft:..."
 * - the user hit it as a hard crash at mine win: ServerLevel
 * craftmine$cleanInventoryAndReward reads experience_gain_modifier while paying
 * out XP. The one-arg add() uses each attribute's own default (all 1.0,
 * matching the fork's RangedAttribute defaults). Registration timing is safe:
 * CraftmineAttributes.bootstrap() runs pre-freeze from CraftmineBootstrap
 * .bootstrapAll(), and DefaultAttributes.<clinit> only runs on first entity
 * attribute lookup (long after mod bootstrap - the same proven window the pet
 * DefaultAttributesMixin relies on).
 */
@Mixin(Player.class)
public abstract class PlayerCreateAttributesMixin {

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void craftmine$addCraftmineAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue()
            .add(CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER)
            .add(CraftmineAttributes.MAX_JUMPS)
            .add(CraftmineAttributes.PICKUP_AREA_SIZE);
    }
}
