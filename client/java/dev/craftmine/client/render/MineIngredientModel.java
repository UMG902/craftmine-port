package dev.craftmine.client.render;

import java.util.Optional;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.item.component.WorldModifiers;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model for mine ingredients: renders as the item model of the FIRST
 * world effect attached to the stack (via {@link WorldModifiers}), so a
 * "rain mine" ingredient looks like rain and a "cave mine" ingredient looks
 * like stone. Stacks without any modeled effect render as nothing — the
 * ingredient is a pure concept and the crafter UI shows it as text.
 */
public class MineIngredientModel implements ItemModel {

    static final ItemModel INSTANCE = new MineIngredientModel();

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        renderState.appendModelIdentityElement(this);
        WorldModifiers modifiers = stack.getOrDefault(CraftmineDataComponents.WORLD_MODIFIERS, WorldModifiers.EMPTY);
        Optional<Identifier> modelId = modifiers.effects().stream()
            .flatMap(effect -> Optional.ofNullable(effect.itemModel()).stream())
            .findFirst();
        modelId.ifPresent(id -> {
            ItemModel model = Minecraft.getInstance().getModelManager().getItemModel(id);
            model.update(renderState, stack, resolver, displayContext, level, owner, seed);
        });
    }

    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc matrix) {
            return INSTANCE;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            // no nested model resources to resolve — the effect models resolve themselves
        }
    }
}
