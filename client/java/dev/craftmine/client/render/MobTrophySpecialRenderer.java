package dev.craftmine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.craftmine.registry.CraftmineDataComponents;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import dev.craftmine.client.render.MobTrophyRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.item.MobTrophyInfo;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;

public class MobTrophySpecialRenderer
implements SpecialModelRenderer<MobTrophyInfo> {
    @Override
    public void submit(@Nullable MobTrophyInfo mobTrophyInfo, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
        if (mobTrophyInfo == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher entityRenderDispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderState entityRenderState = MobTrophyRenderer.extractEntityRenderState(entityRenderDispatcher, mobTrophyInfo, minecraft.level, 1.0f);
        if (entityRenderState == null) {
            return;
        }
        entityRenderState.lightCoords = i;
        entityRenderState.shadowPieces.clear();
        entityRenderState.outlineColor = 0;
        MobTrophyRenderer.render(poseStack, submitNodeCollector, mobTrophyInfo, entityRenderState, entityRenderDispatcher, Direction.NORTH, new CameraRenderState());
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        for (int i = 0; i < 8; ++i) {
            consumer.accept(new Vector3f((i & 1) == 0 ? 0.0f : MobTrophyRenderer.BOX_WIDTH, (i & 2) == 0 ? 0.0f : MobTrophyRenderer.BOX_HEIGHT, (i & 4) == 0 ? 0.0f : MobTrophyRenderer.BOX_WIDTH));
        }
    }

    @Override
    @Nullable
    public MobTrophyInfo extractArgument(ItemStack itemStack) {
        return itemStack.get(CraftmineDataComponents.MOB_TROPHY_TYPE);
    }

    public record Unbaked()
    implements SpecialModelRenderer.Unbaked<MobTrophyInfo>
    {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        public MobTrophySpecialRenderer bake(SpecialModelRenderer.BakingContext bakingContext) {
            return new MobTrophySpecialRenderer();
        }
    }
}

