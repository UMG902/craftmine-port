/*
 * Client-side registrations for all craftmine renderers/screens/models.
 *
 * The 25w14craftmine snapshot registers this content inside vanilla's own
 * static initializers (EntityRenderers, BlockEntityRenderers, ItemModels,
 * SpecialModelRenderers, ItemTintSources). The port cannot edit those classes'
 * static blocks, so the same registrations happen here, at Fabric client
 * entrypoint time. That point is safe: it runs at the head of Minecraft's
 * constructor (after full bootstrap/registry freeze - the entity types are
 * registered - and before createEntityRenderers/ParticleResources
 * construction/first resource reload, which are the consumers of these maps).
 *
 * Access is by REFLECTION (dev.craftmine.util.VanillaReflection), not accessor
 * mixins: sponge-mixin rejects classloading a mixin interface before its target
 * class has been transformed, and at entrypoint time none of the target classes
 * have been touched yet. Reading the private static field triggers the owner's
 * clinit (registering vanilla content first), then our puts land on top. All
 * the target maps are mutable (Object2ObjectOpenHashMap / LateBoundIdMapper).
 *
 * NOT registered here: the craftmine menus (opened directly in
 * ClientPacketListenerMixin.handleWindowScreen - MenuScreens' ScreenConstructor
 * is a private inner interface) and the mine_travel particle provider
 * (per-instance via ParticleResourcesMixin).
 */
package dev.craftmine.client;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.util.VanillaReflection;
import dev.craftmine.client.render.GradeColor;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import dev.craftmine.client.render.MineTravellingBlockEntityRenderer;
import dev.craftmine.client.render.MobTrophyRenderer;
import dev.craftmine.client.render.AngryGhastRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import dev.craftmine.client.render.PetArmadilloRenderer;
import dev.craftmine.client.render.PetAxolotlRenderer;
import dev.craftmine.client.render.PetBeeRenderer;
import dev.craftmine.client.render.PetCatRenderer;
import dev.craftmine.client.render.PetChickenRenderer;
import dev.craftmine.client.render.PetCowRenderer;
import dev.craftmine.client.render.PetCreeperRenderer;
import dev.craftmine.client.render.PetFoxRenderer;
import dev.craftmine.client.render.PetFrogRenderer;
import dev.craftmine.client.render.PetMushroomCowRenderer;
import dev.craftmine.client.render.PetPolarBearRenderer;
import dev.craftmine.client.render.PetSlimeRenderer;
import dev.craftmine.client.render.PetTurtleRenderer;
import dev.craftmine.client.render.PetWolfRenderer;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import dev.craftmine.client.render.MineIngredientModel;
import dev.craftmine.client.render.MobTrophySpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class CraftmineClientRenderers {

    private CraftmineClientRenderers() {
    }

    public static void registerAll() {
        registerEntityRenderers();
        registerBlockEntityRenderers();
        registerItemModels();
    }

    /**
     * Ported from 25w14craftmine EntityRenderers.java static block additions.
     */
    private static void registerEntityRenderers() {
        Map<EntityType<?>, EntityRendererProvider<?>> providers =
            VanillaReflection.readStaticMap(EntityRenderers.class, "PROVIDERS");
        putEntityRenderer(providers, CraftmineContent.ANGRY_GHAST, AngryGhastRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_ARMADILLO, PetArmadilloRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_AXOLOTL, PetAxolotlRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_BEE, PetBeeRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_CAT, PetCatRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_CHICKEN, PetChickenRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_COW, PetCowRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_CREEPER, PetCreeperRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_FOX, PetFoxRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_FROG, PetFrogRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_MUSHROOM_COW, PetMushroomCowRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_POLAR_BEAR, PetPolarBearRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_SLIME, PetSlimeRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_TURTLE, PetTurtleRenderer::new);
        putEntityRenderer(providers, CraftmineContent.PET_WOLF, PetWolfRenderer::new);
    }

    private static <T extends Entity> void putEntityRenderer(Map<EntityType<?>, EntityRendererProvider<?>> map,
                                                             EntityType<T> entityType,
                                                             EntityRendererProvider<T> entityRendererProvider) {
        map.put(entityType, entityRendererProvider);
    }

    /**
     * Ported from 25w14craftmine BlockEntityRenderers.java static block additions.
     * mine_crafter intentionally has no renderer (vanilla-shaped plain block model).
     */
    private static void registerBlockEntityRenderers() {
        Map<BlockEntityType<?>, BlockEntityRendererProvider<?, ?>> providers =
            VanillaReflection.readStaticMap(BlockEntityRenderers.class, "PROVIDERS");
        putBlockEntityRenderer(providers, CraftmineContent.MINE_TRAVELLING_BLOCK_ENTITY, MineTravellingBlockEntityRenderer::new);
        putBlockEntityRenderer(providers, CraftmineContent.MOB_TROPHY_BLOCK_ENTITY, MobTrophyRenderer::new);
    }

    private static <T extends BlockEntity, S extends BlockEntityRenderState> void putBlockEntityRenderer(
            Map<BlockEntityType<?>, BlockEntityRendererProvider<?, ?>> map,
            BlockEntityType<T> blockEntityType,
            BlockEntityRendererProvider<T, S> blockEntityRendererProvider) {
        map.put(blockEntityType, blockEntityRendererProvider);
    }

    /**
     * Registers the custom codec ids referenced by the shipped item model JSONs:
     * items/mine_ingredient.json -> "minecraft:level_ingredient" (ItemModels),
     * items/mob_trophy.json -> "minecraft:mob_trophy" (SpecialModelRenderers)
     * and "minecraft:grade" tint (ItemTintSources). The LateBoundIdMapper codecs
     * capture the live BiMap, so entries added before item model baking resolve.
     */
    @SuppressWarnings("unchecked")
    private static void registerItemModels() {
        ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> itemModels =
            (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>>)
                VanillaReflection.readStaticField(ItemModels.class, "ID_MAPPER");
        itemModels.put(Identifier.withDefaultNamespace("level_ingredient"), MineIngredientModel.Unbaked.MAP_CODEC);
        ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> specialModels =
            (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>>)
                VanillaReflection.readStaticField(SpecialModelRenderers.class, "ID_MAPPER");
        specialModels.put(Identifier.withDefaultNamespace("mob_trophy"), MobTrophySpecialRenderer.Unbaked.MAP_CODEC);
        ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemTintSource>> tintSources =
            (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemTintSource>>)
                VanillaReflection.readStaticField(ItemTintSources.class, "ID_MAPPER");
        tintSources.put(Identifier.withDefaultNamespace("grade"), GradeColor.MAP_CODEC);
    }
}
