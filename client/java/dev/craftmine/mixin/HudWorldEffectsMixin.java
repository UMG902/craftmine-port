/*
 * The fork's HUD: active world effects of the current mine, rendered as a
 * grid of 24x24 effect-ingredient icons in the upper left corner (starting
 * at y=1; vanilla potion icons live in the upper RIGHT).
 *
 * Original: Gui.renderActiveWorldEffects, registered as a LayeredDraw layer
 * between renderEffects (vanilla potion icons) and the boss bar.
 *
 * 26.3 port: the LayeredDraw pipeline is gone; the HUD is extracted via
 * Hud.extractRenderState -> extractEffects (potion icons) -> extractBossOverlay.
 * We inject at extractEffects RETURN so the icons land in the same stratum,
 * between vanilla effect icons and the boss overlay - the fork's exact order.
 *
 * Fork-faithful details kept:
 * - 10 icons per row, 25px pitch, 24x24 boxes; rows compress when more than
 *   3 rows are visible (50/rows per-row pitch instead of 25).
 * - Demo mode pushes the grid down 15px (demo banner clearance).
 * - Effects without an itemModel are skipped (no icon to draw).
 * - Items are rendered after all background sprites (fork's deferred
 *   Runnable list) so the ingredient models never interleave with blits.
 * - Hover tooltips only resolve while a ChatScreen is open (the only screen
 *   that leaves the HUD visible underneath); tooltip = effect name +
 *   description in gray, joined. 26.3 note: Screen
 *   .setTooltipForNextRenderPass no longer exists - the extractor's
 *   setTooltipForNextFrame is the direct replacement and renders over chat.
 * - Data source: ClientLevel's synced activeEffects via
 *   CraftmineClientLevelDuck (spawn info + ClientboundUpdateUnlockedEffectsPacket).
 */
package dev.craftmine.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffects;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.duck.CraftmineClientLevelDuck;

@Mixin(Hud.class)
public abstract class HudWorldEffectsMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    private static final Identifier craftmine$EFFECT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/effect_background");

    @Inject(method = "extractEffects", at = @At("RETURN"))
    private void craftmine$extractActiveWorldEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.minecraft.level == null) {
            return;
        }
        List<WorldEffect> list = ((CraftmineClientLevelDuck) this.minecraft.level).getActiveEffects();
        if (list == null || list.isEmpty()) {
            return;
        }
        Screen screen = this.minecraft.gui.screen();
        int mouseX;
        int mouseY;
        if (screen instanceof ChatScreen) {
            mouseX = Mth.floor(this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow()));
            mouseY = Mth.floor(this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow()));
        } else {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        List<Runnable> deferredItems = new ArrayList<>(list.size());
        int index = 0;
        int rowCount = Mth.ceil(list.size() / 10.0f);
        for (WorldEffect worldEffect : list) {
            if (worldEffect.itemModel() == null) {
                continue;
            }
            int x = 1;
            int y = 1;
            if (this.minecraft.isDemo()) {
                y += 15;
            }
            int row = Mth.floor(index / 10.0f);
            y = rowCount > 3 ? y + (int) (50.0f / rowCount * row) : y + 25 * row;
            x += 25 * (index % 10);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, craftmine$EFFECT_BACKGROUND_SPRITE, x, y, 24, 24);
            int itemX = x;
            int itemY = y;
            deferredItems.add(() -> graphics.item(WorldEffects.createEffectItem(worldEffect, false), itemX + 4, itemY + 4));
            if (screen != null && mouseX >= x && mouseY >= y && mouseX < x + 24 && mouseY < y + 24) {
                graphics.setTooltipForNextFrame(this.minecraft.font,
                        List.of(worldEffect.name(), worldEffect.description().copy().withStyle(ChatFormatting.GRAY)),
                        Optional.empty(), mouseX, mouseY);
            }
            index++;
        }
        deferredItems.forEach(Runnable::run);
    }
}
