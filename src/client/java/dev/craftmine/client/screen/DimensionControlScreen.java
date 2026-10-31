package dev.craftmine.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import dev.craftmine.inventory.DimensionControlMenu;

/**
 * Static-texture container screen for the dimension control block: blits
 * its background and lets the vanilla container plumbing do the rest — the
 * menu handles all sky/dimension state, this screen draws nothing dynamic.
 */
public class DimensionControlScreen extends AbstractContainerScreen<DimensionControlMenu> {

    private static final Identifier BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/dimension_control.png");

    public DimensionControlScreen(DimensionControlMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE,
            left, top, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }
}
