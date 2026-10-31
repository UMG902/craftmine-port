package dev.craftmine.mixin;

import com.google.common.collect.ImmutableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import dev.craftmine.entity.AngryGhast;
import dev.craftmine.entity.pet.PetArmadillo;
import dev.craftmine.entity.pet.PetAxolotl;
import dev.craftmine.entity.pet.PetBee;
import dev.craftmine.entity.pet.PetCat;
import dev.craftmine.entity.pet.PetChicken;
import dev.craftmine.entity.pet.PetCow;
import dev.craftmine.entity.pet.PetCreeper;
import dev.craftmine.entity.pet.PetFox;
import dev.craftmine.entity.pet.PetFrog;
import dev.craftmine.entity.pet.PetMushroomCow;
import dev.craftmine.entity.pet.PetPolarBear;
import dev.craftmine.entity.pet.PetSlime;
import dev.craftmine.entity.pet.PetTurtle;
import dev.craftmine.entity.pet.PetWolf;

/**
 * The craftmine fork adds its entities to DefaultAttributes.SUPPLIERS inline.
 * We redirect the single ImmutableMap.Builder#build() call in the class init
 * and append the pet / angry-ghast suppliers, mirroring the original mapping:
 * most pets share AbstractPet.createAttributes(), PetPolarBear mirrors the
 * vanilla polar bear, AngryGhast has its own.
 */
@Mixin(DefaultAttributes.class)
public abstract class DefaultAttributesMixin {

    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"
        )
    )
    private static ImmutableMap craftmine$addPetAttributes(ImmutableMap.Builder builder) {
        builder.put(dev.craftmine.registry.CraftmineContent.PET_ARMADILLO, (AttributeSupplier) PetArmadillo.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_AXOLOTL, (AttributeSupplier) PetAxolotl.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_BEE, (AttributeSupplier) PetBee.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_CAT, (AttributeSupplier) PetCat.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_CHICKEN, (AttributeSupplier) PetChicken.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_COW, (AttributeSupplier) PetCow.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_CREEPER, (AttributeSupplier) PetCreeper.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_FOX, (AttributeSupplier) PetFox.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_FROG, (AttributeSupplier) PetFrog.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_MUSHROOM_COW, (AttributeSupplier) PetMushroomCow.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_POLAR_BEAR, (AttributeSupplier) PetPolarBear.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_SLIME, (AttributeSupplier) PetSlime.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_TURTLE, (AttributeSupplier) PetTurtle.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.PET_WOLF, (AttributeSupplier) PetWolf.createAttributes().build());
        builder.put(dev.craftmine.registry.CraftmineContent.ANGRY_GHAST, (AttributeSupplier) AngryGhast.createAttributes().build());
        return builder.build();
    }
}
