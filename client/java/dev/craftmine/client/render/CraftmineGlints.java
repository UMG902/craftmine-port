package dev.craftmine.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import dev.craftmine.mixin.CraftmineRenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.resources.Identifier;

/**
 * Task 60: the fork shipped two extra glint textures - map_won_glint.png
 * (green) and map_lost_glint.png (red) - and render types
 * gwk.al/am (+ translucent + entity variants) that used them for finished
 * mine items. 26.3 resolves the per-quad glint render type from the baked
 * MaterialInfo (Sheets.*ItemGlintSheet), so the port re-materializes the
 * quads at submit time (CraftmineItemLayerDuck kind set by
 * CuboidItemModelWrapperMixin) with these render types swapped in.
 */
public final class CraftmineGlints {
    public static final Identifier MAP_WON_GLINT = Identifier.withDefaultNamespace("textures/misc/map_won_glint.png");
    public static final Identifier MAP_LOST_GLINT = Identifier.withDefaultNamespace("textures/misc/map_lost_glint.png");

    private static final RenderType WON_ITEM_CUTOUT = cutoutGlint(TextureAtlas.LOCATION_ITEMS, MAP_WON_GLINT, "craftmine_item_cutout_won_glint");
    private static final RenderType LOST_ITEM_CUTOUT = cutoutGlint(TextureAtlas.LOCATION_ITEMS, MAP_LOST_GLINT, "craftmine_item_cutout_lost_glint");
    private static final RenderType WON_ITEM_TRANSLUCENT = translucentGlint(TextureAtlas.LOCATION_ITEMS, MAP_WON_GLINT, "craftmine_item_translucent_won_glint");
    private static final RenderType LOST_ITEM_TRANSLUCENT = translucentGlint(TextureAtlas.LOCATION_ITEMS, MAP_LOST_GLINT, "craftmine_item_translucent_lost_glint");
    private static final RenderType WON_BLOCK_CUTOUT = cutoutGlint(TextureAtlas.LOCATION_BLOCKS, MAP_WON_GLINT, "craftmine_block_cutout_won_glint");
    private static final RenderType LOST_BLOCK_CUTOUT = cutoutGlint(TextureAtlas.LOCATION_BLOCKS, MAP_LOST_GLINT, "craftmine_block_cutout_lost_glint");
    private static final RenderType WON_BLOCK_TRANSLUCENT = translucentGlint(TextureAtlas.LOCATION_BLOCKS, MAP_WON_GLINT, "craftmine_block_translucent_won_glint");
    private static final RenderType LOST_BLOCK_TRANSLUCENT = translucentGlint(TextureAtlas.LOCATION_BLOCKS, MAP_LOST_GLINT, "craftmine_block_translucent_lost_glint");

    private CraftmineGlints() {
    }

    private static RenderType cutoutGlint(Identifier sampler0, Identifier glintTexture, String name) {
        RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.ITEM_CUTOUT_GLINT).withTexture("Sampler0", sampler0).withTexture("GlintSampler", glintTexture).setTextureTransform(TextureTransform.GLINT_TEXTURING).useLightmap().useOverlay().affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup();
        return CraftmineRenderTypeInvoker.craftmine$create(name, renderSetup);
    }

    private static RenderType translucentGlint(Identifier sampler0, Identifier glintTexture, String name) {
        RenderSetup renderSetup = RenderSetup.builder(RenderPipelines.ITEM_TRANSLUCENT_GLINT).setOitPipelines(RenderPipelines.OIT_ITEM_GLINT).withTexture("Sampler0", sampler0).withTexture("GlintSampler", glintTexture).setTextureTransform(TextureTransform.GLINT_TEXTURING).useLightmap().useOverlay().affectsCrumbling().sortOnUpload().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup();
        return CraftmineRenderTypeInvoker.craftmine$create(name, renderSetup);
    }

    private static RenderType mapGlintSheet(RenderType original, boolean won) {
        if (original == Sheets.cutoutItemGlintSheet()) {
            return won ? WON_ITEM_CUTOUT : LOST_ITEM_CUTOUT;
        }
        if (original == Sheets.translucentItemGlintSheet()) {
            return won ? WON_ITEM_TRANSLUCENT : LOST_ITEM_TRANSLUCENT;
        }
        if (original == Sheets.cutoutBlockItemGlintSheet()) {
            return won ? WON_BLOCK_CUTOUT : LOST_BLOCK_CUTOUT;
        }
        if (original == Sheets.translucentBlockItemGlintSheet()) {
            return won ? WON_BLOCK_TRANSLUCENT : LOST_BLOCK_TRANSLUCENT;
        }
        return original;
    }

    public static ItemQuads remapQuads(ItemQuads itemQuads, int kind) {
        if (kind != 1 && kind != 2) {
            return itemQuads;
        }
        boolean won = kind == 1;
        return new ItemQuads(remap(itemQuads.all(), won), remap(itemQuads.solid(), won), remap(itemQuads.translucent(), won));
    }

    private static List<BakedQuad> remap(List<BakedQuad> list, boolean won) {
        if (list.isEmpty()) {
            return list;
        }
        ArrayList<BakedQuad> out = new ArrayList<BakedQuad>(list.size());
        for (BakedQuad bakedQuad : list) {
            BakedQuad.MaterialInfo materialInfo = bakedQuad.materialInfo();
            RenderType renderType = mapGlintSheet(materialInfo.itemGlintRenderType(), won);
            RenderType renderType2 = mapGlintSheet(materialInfo.itemGlintSpecialRenderType(), won);
            if (renderType == materialInfo.itemGlintRenderType() && renderType2 == materialInfo.itemGlintSpecialRenderType()) {
                out.add(bakedQuad);
                continue;
            }
            out.add(new BakedQuad(bakedQuad.position0(), bakedQuad.position1(), bakedQuad.position2(), bakedQuad.position3(), bakedQuad.packedUV0(), bakedQuad.packedUV1(), bakedQuad.packedUV2(), bakedQuad.packedUV3(), bakedQuad.direction(), new BakedQuad.MaterialInfo(materialInfo.sprite(), materialInfo.layer(), materialInfo.itemRenderType(), renderType, renderType2, materialInfo.tintIndex(), materialInfo.shadeDirectionOverride(), materialInfo.lightEmission())));
        }
        return List.copyOf(out);
    }
}
