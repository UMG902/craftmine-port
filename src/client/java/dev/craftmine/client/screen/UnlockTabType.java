package dev.craftmine.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Where an unlocks-tree tab sits relative to the window, and how to draw
 * it: each orientation owns its three edge sprites, its tab size, its slot
 * offset math, and the icon inset that keeps the item art centered on the
 * tab shape. Sprite IDs are the vanilla advancement tab textures — note
 * the horizontal orientations use "_top/_bottom" caps, vertical ones use
 * "_left/_right".
 */
public enum UnlockTabType {
    ABOVE(unselected("advancements/tab_above_left", "advancements/tab_above_middle", "advancements/tab_above_right"),
        selected("advancements/tab_above_left_selected", "advancements/tab_above_middle_selected", "advancements/tab_above_right_selected"),
        28, 32, 8) {
        @Override
        int slotOffsetX(int index, int span) {
            return (this.width + 4) * index;
        }

        @Override
        int slotOffsetY(int index, int span) {
            return -this.height + 4;
        }

        @Override
        int iconOffsetX() {
            return 6;
        }

        @Override
        int iconOffsetY() {
            return 9;
        }
    },
    BELOW(unselected("advancements/tab_below_left", "advancements/tab_below_middle", "advancements/tab_below_right"),
        selected("advancements/tab_below_left_selected", "advancements/tab_below_middle_selected", "advancements/tab_below_right_selected"),
        28, 32, 8) {
        @Override
        int slotOffsetX(int index, int span) {
            return (this.width + 4) * index;
        }

        @Override
        int slotOffsetY(int index, int span) {
            return span - 4;
        }

        @Override
        int iconOffsetX() {
            return 6;
        }

        @Override
        int iconOffsetY() {
            return 6;
        }
    },
    LEFT(unselected("advancements/tab_left_top", "advancements/tab_left_middle", "advancements/tab_left_bottom"),
        selected("advancements/tab_left_top_selected", "advancements/tab_left_middle_selected", "advancements/tab_left_bottom_selected"),
        32, 28, 8) {
        @Override
        int slotOffsetX(int index, int span) {
            return -this.width + 4;
        }

        @Override
        int slotOffsetY(int index, int span) {
            return this.height * index;
        }

        @Override
        int iconOffsetX() {
            return 10;
        }

        @Override
        int iconOffsetY() {
            return 5;
        }
    },
    RIGHT(unselected("advancements/tab_right_top", "advancements/tab_right_middle", "advancements/tab_right_bottom"),
        selected("advancements/tab_right_top_selected", "advancements/tab_right_middle_selected", "advancements/tab_right_bottom_selected"),
        32, 28, 8) {
        @Override
        int slotOffsetX(int index, int span) {
            return span - 4;
        }

        @Override
        int slotOffsetY(int index, int span) {
            return this.height * index;
        }

        @Override
        int iconOffsetX() {
            return 6;
        }

        @Override
        int iconOffsetY() {
            return 5;
        }
    };

    private final Sprites selectedSprites;
    private final Sprites unselectedSprites;
    protected final int width;
    protected final int height;
    private final int max;

    UnlockTabType(Sprites unselectedSprites, Sprites selectedSprites, int width, int height, int max) {
        this.selectedSprites = selectedSprites;
        this.unselectedSprites = unselectedSprites;
        this.width = width;
        this.height = height;
        this.max = max;
    }

    private static Sprites unselected(String first, String middle, String last) {
        return new Sprites(
            Identifier.withDefaultNamespace(first),
            Identifier.withDefaultNamespace(middle),
            Identifier.withDefaultNamespace(last));
    }

    private static Sprites selected(String first, String middle, String last) {
        return new Sprites(
            Identifier.withDefaultNamespace(first),
            Identifier.withDefaultNamespace(middle),
            Identifier.withDefaultNamespace(last));
    }

    public int getMax() {
        return this.max;
    }

    /** Pixel offset of slot {@code index} from the anchor, per orientation. */
    abstract int slotOffsetX(int index, int span);

    abstract int slotOffsetY(int index, int span);

    /** Icon inset inside the tab sprite, per orientation. */
    abstract int iconOffsetX();

    abstract int iconOffsetY();

    /**
     * Draws tab {@code index} at the anchor; the first and last slots use
     * the capped edge sprites, everything between uses the middle sprite.
     */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, boolean selected, int index, int spanX, int spanY) {
        Sprites sprites = selected ? this.selectedSprites : this.unselectedSprites;
        Identifier sprite = index == 0 ? sprites.first()
            : (index == this.max - 1 ? sprites.last() : sprites.middle());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
            x + this.slotOffsetX(index, spanX), y + this.slotOffsetY(index, spanY), this.width, this.height);
    }

    public void drawIcon(GuiGraphicsExtractor graphics, int x, int y, int index, ItemStack icon, int spanX, int spanY) {
        int iconX = x + this.slotOffsetX(index, spanX) + this.iconOffsetX();
        int iconY = y + this.slotOffsetY(index, spanY) + this.iconOffsetY();
        graphics.fakeItem(icon, iconX, iconY);
    }

    public boolean isMouseOver(int x, int y, int index, double mouseX, double mouseY, int spanX, int spanY) {
        int tabX = x + this.slotOffsetX(index, spanX);
        int tabY = y + this.slotOffsetY(index, spanY);
        return mouseX > tabX && mouseX < tabX + this.width && mouseY > tabY && mouseY < tabY + this.height;
    }

    record Sprites(Identifier first, Identifier middle, Identifier last) {
    }
}
