package dev.craftmine.client.screen;

import java.util.Map;
import com.google.common.collect.Maps;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import dev.craftmine.unlock.PlayerUnlock;
import org.jetbrains.annotations.Nullable;

/**
 * One unlocks tab (a root unlock and its whole subtree): owns the widgets,
 * the scroll offset, the world-bounds of the laid-out tree, and the tab
 * chrome drawing. Tab slots are allocated across the four
 * {@link UnlockTabType} orientations in declaration order.
 */
public class PlayerUnlocksTab {

    /** Widget footprint used for the scroll-bounds bookkeeping. */
    private static final int WIDGET_WIDTH = 28;
    private static final int WIDGET_HEIGHT = 27;
    /** Hover dimming fades in while pointing at a widget, out otherwise. */
    private static final float FADE_IN_PER_FRAME = 0.02F;
    private static final float FADE_OUT_PER_FRAME = 0.04F;
    private static final float FADE_MAX = 0.3F;
    private static final float FADE_MIN = 0.0F;

    private final Minecraft minecraft;
    private final PlayerUnlocksScreen screen;
    private final UnlockTabType type;
    private final int index;
    private final Holder<PlayerUnlock> rootNode;
    private final DisplayInfo display;
    private final ItemStack icon;
    private final Component title;
    private final PlayerUnlockWidget root;
    private final Map<Holder<PlayerUnlock>, PlayerUnlockWidget> widgets = Maps.newLinkedHashMap();

    private double scrollX;
    private double scrollY;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private float fade;
    private boolean centered;

    public PlayerUnlocksTab(Minecraft minecraft, PlayerUnlocksScreen screen, UnlockTabType type,
                            int index, Holder<PlayerUnlock> rootNode, DisplayInfo display) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.type = type;
        this.index = index;
        this.rootNode = rootNode;
        this.display = display;
        this.icon = display.icon().create();
        this.title = display.title();
        this.root = new PlayerUnlockWidget(this, minecraft, rootNode, display,
            screen.getVisibility(rootNode), screen.isUnlocked(rootNode), rootNode.value().unlockPrice(),
            !rootNode.value().exclusiveKey().isEmpty(), screen.isActiveExclusive(rootNode));
        this.addWidget(this.root, rootNode);
    }

    public UnlockTabType getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public Holder<PlayerUnlock> getRoot() {
        return this.rootNode;
    }

    public Component getTitle() {
        return this.title;
    }

    public DisplayInfo getDisplay() {
        return this.display;
    }

    public void drawTab(GuiGraphicsExtractor graphics, int x, int y, boolean selected) {
        this.type.draw(graphics, x, y, selected, this.index, this.screen.getWindowWidth(), this.screen.getWindowHeight());
    }

    public void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        this.type.drawIcon(graphics, x, y, this.index, this.icon, this.screen.getWindowWidth(), this.screen.getWindowHeight());
    }

    /** First draw centers the scroll on the laid-out tree's bounding box. */
    public void drawContents(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!this.centered) {
            this.scrollX = (double) width / 2.0 - (double) (this.maxX + this.minX) / 2.0;
            this.scrollY = (double) height / 2.0 - (double) (this.maxY + this.minY) / 2.0;
            this.centered = true;
        }
        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        Identifier background = this.display.background().map(ClientAsset::id).orElse(MissingTextureAtlasSprite.getLocation());
        int scrollLeft = Mth.floor(this.scrollX);
        int scrollTop = Mth.floor(this.scrollY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, 0, 0, width, height);
        this.root.drawConnectivity(graphics, scrollLeft, scrollTop, true);
        this.root.drawConnectivity(graphics, scrollLeft, scrollTop, false);
        this.root.draw(graphics, scrollLeft, scrollTop);
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    /** Dimming overlay + hover card for whatever widget the mouse points at. */
    public void drawTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int scrollDeltaX, int scrollDeltaY,
                             int width, int height) {
        graphics.pose().pushMatrix();
        graphics.fill(0, 0, width, height, Mth.floor(this.fade * 255.0F) << 24);
        boolean hoveringWidget = false;
        int scrollLeft = Mth.floor(this.scrollX);
        int scrollTop = Mth.floor(this.scrollY);
        if (mouseX > 0 && mouseX < width && mouseY > 0 && mouseY < height) {
            for (PlayerUnlockWidget widget : this.widgets.values()) {
                if (!widget.isMouseOver(scrollLeft, scrollTop, mouseX, mouseY)) {
                    continue;
                }
                hoveringWidget = true;
                widget.drawHover(graphics, scrollLeft, scrollTop, this.fade, scrollDeltaX, scrollDeltaY, width, height);
                break;
            }
        }
        graphics.pose().popMatrix();
        this.fade = hoveringWidget
            ? Mth.clamp(this.fade + FADE_IN_PER_FRAME, FADE_MIN, FADE_MAX)
            : Mth.clamp(this.fade - FADE_OUT_PER_FRAME, FADE_MIN, 1.0F);
    }

    public boolean isMouseOver(int x, int y, double mouseX, double mouseY) {
        return this.type.isMouseOver(x, y, this.index, mouseX, mouseY, this.screen.getWindowWidth(), this.screen.getWindowHeight());
    }

    /**
     * Builds a tab for the Nth slot: slots are consumed ABOVE first, then
     * BELOW, LEFT, RIGHT. Returns null when the index exceeds all tabs'
     * capacity.
     */
    @Nullable
    public static PlayerUnlocksTab create(Minecraft minecraft, PlayerUnlocksScreen screen,
                                          ClientPlayerUnlocks unlocks, int slot, Holder<PlayerUnlock> rootNode) {
        PlayerUnlockTreeNodePosition.run(rootNode, unlocks);
        DisplayInfo display = rootNode.value().display();
        int remaining = slot;
        for (UnlockTabType type : UnlockTabType.values()) {
            if (remaining >= type.getMax()) {
                remaining -= type.getMax();
                continue;
            }
            return new PlayerUnlocksTab(minecraft, screen, type, remaining, rootNode, display);
        }
        return null;
    }

    /**
     * Re-syncs widget placement after tree changes. Kept 1:1: when a child
     * of the root has no widget yet, the fork re-adds the ROOT widget
     * instead (the child is created by the add-listener path); this quirk
     * is preserved rather than "fixed".
     */
    public void updateLayout() {
        ClientPlayerUnlocks unlocks = ((CraftmineClientUnlocksDuck) this.minecraft.getConnection()).craftmine$getUnlocks();
        PlayerUnlocksTree tree = unlocks.getTree();
        for (Holder<PlayerUnlock> child : tree.getChildren(this.rootNode)) {
            PlayerUnlockWidget existing = this.getWidget(child);
            if (existing != null) {
                continue;
            }
            this.addWidget(this.root, this.rootNode);
        }
        PlayerUnlockTreeNodePosition.run(this.rootNode, unlocks);
        this.screen.repositionElements();
    }

    public void scroll(double deltaX, double deltaY, int viewWidth, int viewHeight) {
        if (this.maxX - this.minX > viewWidth) {
            this.scrollX = Mth.clamp(this.scrollX + deltaX, (double) -(this.maxX - viewWidth), 0.0);
        }
        if (this.maxY - this.minY > viewHeight) {
            this.scrollY = Mth.clamp(this.scrollY + deltaY, (double) -(this.maxY - viewHeight), 0.0);
        }
    }

    public void addUnlock(Holder<PlayerUnlock> unlock) {
        DisplayInfo display = unlock.value().display();
        PlayerUnlockWidget widget = new PlayerUnlockWidget(this, this.minecraft, unlock, display,
            this.screen.getVisibility(unlock), this.screen.isUnlocked(unlock), unlock.value().unlockPrice(),
            !unlock.value().exclusiveKey().isEmpty(), this.screen.isActiveExclusive(unlock));
        this.addWidget(widget, unlock);
    }

    private void addWidget(PlayerUnlockWidget widget, Holder<PlayerUnlock> unlock) {
        this.widgets.put(unlock, widget);
        int widgetX = widget.getX();
        int widgetY = widget.getY();
        this.minX = Math.min(this.minX, widgetX);
        this.maxX = Math.max(this.maxX, widgetX + WIDGET_WIDTH);
        this.minY = Math.min(this.minY, widgetY);
        this.maxY = Math.max(this.maxY, widgetY + WIDGET_HEIGHT);
        for (PlayerUnlockWidget existing : this.widgets.values()) {
            existing.attachToParent();
        }
        widget.attachToParent();
    }

    @Nullable
    public PlayerUnlockWidget getWidget(Holder<PlayerUnlock> unlock) {
        return this.widgets.get(unlock);
    }

    public PlayerUnlocksScreen getScreen() {
        return this.screen;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        this.mouseClicked(mouseX, mouseY, button, false);
    }

    /**
     * @param relock ctrl held: unoriginal relock click (see PlayerUnlockWidget.onClicked(boolean)).
     */
    public void mouseClicked(double mouseX, double mouseY, int button, boolean relock) {
        // 26.3 mouse buttons are 1-BASED (1=left) - see PlayerUnlocksScreen.mouseClicked
        if (button != 1) {
            return;
        }
        int scrollLeft = Mth.floor(this.scrollX);
        int scrollTop = Mth.floor(this.scrollY);
        for (PlayerUnlockWidget widget : this.widgets.values()) {
            if (!widget.isMouseOver(scrollLeft, scrollTop, (int) mouseX, (int) mouseY)) {
                continue;
            }
            widget.onClicked(relock);
            break;
        }
    }
}
