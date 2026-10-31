package dev.craftmine.client.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import dev.craftmine.inventory.DoorMenu;

/**
 * Renders the shimmering door's room-layout previews (from
 * {@link dev.craftmine.inventory.DoorMenu.Layout}) as a color grid, with
 * next/prev/open buttons that route through the menu's
 * clickMenuButton protocol.
 */
public class DoorScreen extends AbstractContainerScreen<DoorMenu> {

    private static final Component TITLE = Component.translatable("gui.door");
    private static final Component LABEL_PREVIOUS = Component.translatable("gui.door.previous");
    private static final Component LABEL_NEXT = Component.translatable("gui.door.next");
    private static final Component LABEL_OPEN = Component.translatable("gui.door.open");

    public static final Identifier BOOK_LOCATION = Identifier.withDefaultNamespace("textures/gui/door.png");
    protected static final int BG_WIDTH = 176;
    protected static final int BG_HEIGHT = 247;

    /** Button protocol ids sent via handleInventoryButtonClick. */
    private static final int BUTTON_NEXT = 0;
    private static final int BUTTON_PREVIOUS = 1;
    private static final int BUTTON_OPEN = 2;

    private final Layout buttonLayout;

    public DoorScreen(int containerId, Inventory playerInventory, List<DoorMenu.Layout> layouts) {
        super(new DoorMenu(containerId, playerInventory, layouts), playerInventory, TITLE, BG_WIDTH, BG_HEIGHT);
        EqualSpacingLayout buttons = new EqualSpacingLayout(162, 16, EqualSpacingLayout.Orientation.HORIZONTAL);
        if (layouts.size() > 1) {
            buttons.addChild(Button.builder(LABEL_PREVIOUS, button -> this.clickButton(containerId, BUTTON_PREVIOUS)).width(48).build());
        }
        if (!layouts.isEmpty()) {
            buttons.addChild(Button.builder(LABEL_OPEN, button -> this.clickButton(containerId, BUTTON_OPEN)).width(48).build());
        }
        if (layouts.size() > 1) {
            buttons.addChild(Button.builder(LABEL_NEXT, button -> this.clickButton(containerId, BUTTON_NEXT)).width(48).build());
        }
        LinearLayout column = LinearLayout.vertical();
        column.addChild(SpacerElement.height(30));
        column.addChild(new LayoutPreviewWidget());
        column.addChild(SpacerElement.height(4));
        column.addChild(buttons);
        column.visitWidgets(widget -> this.addRenderableWidget(widget));
        this.buttonLayout = column;
    }

    private void clickButton(int containerId, int buttonId) {
        this.minecraft.gameMode.handleInventoryButtonClick(containerId, buttonId);
    }

    @Override
    protected void init() {
        super.init();
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.buttonLayout.arrangeElements();
        this.buttonLayout.setX(this.leftPos + 7);
        this.buttonLayout.setY(this.topPos);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.menu.getKey().getHoverName(), 30, 12, -1);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK_LOCATION, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The room preview: the chosen layout's fields painted as a centered
     * grid of colored cells; hovering a cell shows its field tooltip.
     */
    class LayoutPreviewWidget extends AbstractWidget {

        private static final int GRID_MARGIN = 8;
        private static final int MIN_CELL_SIZE = 4;

        LayoutPreviewWidget() {
            super(0, 0, 162, 108, Component.literal("rather big box with a lot of smaller boxes inside"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ScreenRectangle bounds = this.getRectangle();
            DoorMenu.Layout layout = Objects.requireNonNullElse(DoorScreen.this.menu.getOption(), DoorMenu.ERROR);
            List<DoorMenu.FieldType> fields = layout.fields();
            int fieldCount = fields.size();
            int columns = layout.width();
            int rows = Mth.positiveCeilDiv(fieldCount, columns);
            int availableWidth = bounds.width() - GRID_MARGIN * 2;
            int availableHeight = bounds.height() - GRID_MARGIN * 2;
            int cellByWidth = availableWidth / columns;
            int cellByHeight = availableHeight / rows;
            int cell = Math.max(Math.min(cellByHeight, cellByWidth), MIN_CELL_SIZE);
            int cellInner = cell - 1; // 1px gap between cells
            int gridLeft = bounds.left() + (availableWidth - cell * columns) / 2 + GRID_MARGIN;
            int gridTop = bounds.top() + (availableHeight - cell * rows) / 2 + GRID_MARGIN;

            Component hoverTooltip = null;
            int index = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    DoorMenu.FieldType field = fields.get(index++);
                    if (field.color == null) {
                        continue;
                    }
                    int cellX = gridLeft + col * cell;
                    int cellY = gridTop + row * cell;
                    int cellEndX = cellX + cellInner;
                    int cellEndY = cellY + cellInner;
                    graphics.fill(cellX, cellY, cellEndX, cellEndY, 0xFF000000 | field.color.getTextureDiffuseColor());
                    if (mouseX >= cellX && mouseX < cellEndX && mouseY >= cellY && mouseY < cellEndY) {
                        hoverTooltip = field.tooltip;
                    }
                }
            }
            if (hoverTooltip != null) {
                graphics.setTooltipForNextFrame(DoorScreen.this.font, hoverTooltip, mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            // decorative grid — nothing to narrate
        }
    }
}
