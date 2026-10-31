package dev.craftmine.client.screen;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.common.collect.Maps;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import dev.craftmine.unlock.PlayerUnlock;
import org.jetbrains.annotations.Nullable;

/**
 * The unlocks screen ("U"): a resizable window over the unlocks tree with
 * tabs (one per root unlock), pan/drag scrolling, hover cards, and the
 * level counter in the corner. Listens to {@link ClientPlayerUnlocks}
 * changes so tabs/widgets stay in sync with server syncs mid-session.
 */
public class PlayerUnlocksScreen extends Screen implements ClientPlayerUnlocks.Listener {

    private static final KeyMapping UNLOCKS_KEY = new KeyMapping("key.unlocks", 85, KeyMapping.Category.MISC);
    private static final Identifier WINDOW_LOCATION = Identifier.withDefaultNamespace("widget/window");
    private static final int WINDOW_TITLE_X = 8;
    private static final int WINDOW_TITLE_Y = 6;
    /** Window padding: title row on top, content insets on the sides. */
    private static final int TOP_PADDING = 18;
    private static final int BOTTOM_PADDING = 9;
    private static final int LEFT_PADDING = 9;
    private static final int RIGHT_PADDING = 9;
    private static final double SCROLL_SPEED = 16.0;
    /** Title/label colors with restored 0xFF alpha bytes (26.3 drops alpha-0 text). */
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int LEVEL_COLOR = 0xFF00BB00;
    private static final int LEVEL_LABEL_RESERVE = 82;
    private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
    public static final Component TITLE = Component.translatable("gui.unlocks");

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private final Screen lastScreen;
    private final ClientPlayerUnlocks playerUnlocks;
    private final Map<Holder<PlayerUnlock>, PlayerUnlocksTab> tabs = Maps.newLinkedHashMap();
    @Nullable
    private PlayerUnlocksTab selectedTab;
    private boolean isScrolling;
    private int windowWidth;
    private int windowHeight;

    public PlayerUnlocksScreen(ClientPlayerUnlocks playerUnlocks) {
        this(playerUnlocks, null);
    }

    public PlayerUnlocksScreen(ClientPlayerUnlocks playerUnlocks, @Nullable Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.playerUnlocks = playerUnlocks;
    }

    @Override
    protected void init() {
        // Window takes 3/4 of the screen, minimum 80px of breathing room.
        int marginX = Math.max(this.width / 4, 80);
        int marginY = Math.max(this.height / 4, 80);
        this.windowWidth = this.width - marginX;
        this.windowHeight = this.height - marginY;
        this.layout.addTitleHeader(TITLE, this.font);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    public boolean isUnlocked(Holder<PlayerUnlock> unlock) {
        // FIX: was a bogus (CraftminePlayerDuck) cast of this.playerUnlocks -
        // CraftminePlayerDuck is the ServerPlayer duck; a ClientPlayerUnlocks
        // can never implement it (crashed every screen open with CCE).
        // ClientPlayerUnlocks carries the authoritative client-side state.
        return this.playerUnlocks.isUnlocked(unlock);
    }

    public PlayerUnlock.UnlockVisibility getVisibility(Holder<PlayerUnlock> unlock) {
        return this.playerUnlocks.getVisibility(unlock);
    }

    public boolean isActiveExclusive(Holder<PlayerUnlock> unlock) {
        return this.playerUnlocks.isActiveExclusive(unlock);
    }

    @Override
    protected void repositionElements() {
        this.resetTabs();
        this.layout.arrangeElements();
    }

    /**
     * Rebuilds the tab widgets after a resize. (The empty-selectedTab branch
     * is kept 1:1 from the fork — on a fresh open it always re-selects
     * through the unlocks state.)
     */
    private void resetTabs() {
        this.tabs.clear();
        this.selectedTab = null;
        this.playerUnlocks.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            PlayerUnlocksTab firstTab = this.tabs.values().iterator().next();
            this.playerUnlocks.setSelectedTab(firstTab.getRoot());
        } else {
            this.playerUnlocks.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getRoot());
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.lastScreen);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, -1072689136, -805306368);
    }

    @Override
    public void removed() {
        this.playerUnlocks.setListener(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // 26.3 mouse buttons are 1-BASED (1=left, 2=right - verified against
        // AbstractWidget.isValidClickButton which compares button()==1). The fork's
        // decompile used the old 0-based GLFW constants, which silently disabled tab
        // switching and unlock-node clicks.
        int windowLeft = (this.width - this.getWindowWidth()) / 2;
        int windowTop = (this.height - this.getWindowHeight()) / 2;
        if (button == 1) {
            for (PlayerUnlocksTab tab : this.tabs.values()) {
                if (!tab.isMouseOver(windowLeft, windowTop, mouseX, mouseY)) {
                    continue;
                }
                this.playerUnlocks.setSelectedTab(tab.getRoot());
                break;
            }
        }
        if (this.selectedTab != null) {
            double insideX = mouseX - windowLeft - LEFT_PADDING;
            double insideY = mouseY - windowTop - TOP_PADDING;
            if (insideX > 0.0 && insideX < this.getInsideWidth() && insideY > 0.0 && insideY < this.getInsideHeight()) {
                this.selectedTab.mouseClicked(insideX, insideY, button, event.hasControlDown());
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UNLOCKS_KEY.matches(event)) {
            this.minecraft.setScreenAndShow(null);
            this.minecraft.mouseHandler.grabMouse();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int windowWidth = this.getWindowWidth();
        int windowHeight = this.getWindowHeight();
        int windowLeft = (this.width - windowWidth) / 2;
        int windowTop = (this.height - windowHeight) / 2;
        this.renderInside(graphics, mouseX, mouseY, windowLeft, windowTop, windowWidth, windowHeight);
        this.renderWindow(graphics, windowLeft, windowTop, windowWidth, windowHeight);
        this.renderTooltips(graphics, mouseX, mouseY, windowLeft, windowTop, windowWidth, windowHeight);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        if (button != 1) {
            this.isScrolling = false;
            return false;
        }
        if (!this.isScrolling) {
            this.isScrolling = true;
        } else if (this.selectedTab != null) {
            this.selectedTab.scroll(dragX, dragY, this.getInsideWidth(), this.getInsideHeight());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.selectedTab != null) {
            this.selectedTab.scroll(scrollX * SCROLL_SPEED, scrollY * SCROLL_SPEED, this.getInsideWidth(), this.getInsideHeight());
            return true;
        }
        return false;
    }

    private void renderInside(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int windowLeft, int windowTop, int windowWidth, int windowHeight) {
        PlayerUnlocksTab tab = this.selectedTab;
        int insideLeft = windowLeft + LEFT_PADDING;
        int insideTop = windowTop + TOP_PADDING;
        int insideRight = windowLeft + windowWidth - RIGHT_PADDING;
        int insideBottom = windowTop + windowHeight - BOTTOM_PADDING;
        if (tab == null) {
            graphics.fill(insideLeft, insideTop, insideRight, insideBottom, -16777216);
            int centerX = (insideLeft + insideRight) / 2;
            int centerY = (insideTop + insideBottom) / 2;
            graphics.centeredText(this.font, NO_ADVANCEMENTS_LABEL, centerX, centerY - this.font.lineHeight / 2, -1);
            graphics.centeredText(this.font, VERY_SAD_LABEL, centerX, centerY + this.font.lineHeight, -1);
            return;
        }
        tab.drawContents(graphics, insideLeft, insideTop, insideRight - insideLeft, insideBottom - insideTop);
    }

    public void renderWindow(GuiGraphicsExtractor graphics, int windowLeft, int windowTop, int windowWidth, int windowHeight) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, windowLeft, windowTop, windowWidth, windowHeight);
        if (this.tabs.size() > 1) {
            for (PlayerUnlocksTab tab : this.tabs.values()) {
                tab.drawTab(graphics, windowLeft, windowTop, tab == this.selectedTab);
            }
            for (PlayerUnlocksTab tab : this.tabs.values()) {
                tab.drawIcon(graphics, windowLeft, windowTop);
            }
        }
        // 26.3's GuiGraphicsExtractor.text() drops any color whose alpha byte is 0
        // (ARGB.alpha(color)==0 early-return). The fork's RGB-only constants lost their
        // opacity in the decompile transcription - restore the 0xFF alpha bytes.
        Component heading = this.selectedTab != null ? this.selectedTab.getTitle() : TITLE;
        graphics.text(this.font, heading, windowLeft + WINDOW_TITLE_X, windowTop + WINDOW_TITLE_Y, TITLE_COLOR, false);
        int level = this.minecraft.player.experienceLevel;
        int levelLabelX = windowLeft + WINDOW_TITLE_X + this.getWindowWidth() - (LEVEL_LABEL_RESERVE + this.font.width("" + level));
        graphics.text(this.font, Component.translatable("unlocks.screen.points", level), levelLabelX, windowTop + WINDOW_TITLE_Y, LEVEL_COLOR, false);
    }

    private void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int windowLeft, int windowTop, int windowWidth, int windowHeight) {
        if (this.selectedTab != null) {
            int insideLeft = windowLeft + LEFT_PADDING;
            int insideTop = windowTop + TOP_PADDING;
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) insideLeft, (float) insideTop);
            this.selectedTab.drawTooltips(graphics, mouseX - insideLeft, mouseY - insideTop,
                windowWidth, windowHeight, windowWidth - LEFT_PADDING - RIGHT_PADDING, windowHeight - TOP_PADDING - BOTTOM_PADDING);
            graphics.pose().popMatrix();
        }
        if (this.tabs.size() > 1) {
            for (PlayerUnlocksTab tab : this.tabs.values()) {
                if (!tab.isMouseOver(windowLeft, windowTop, mouseX, mouseY)) {
                    continue;
                }
                graphics.setTooltipForNextFrame(this.font, List.of(tab.getTitle()), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    public int getWindowWidth() {
        return this.windowWidth;
    }

    public int getWindowHeight() {
        return this.windowHeight;
    }

    private int getInsideWidth() {
        return this.getWindowWidth() - LEFT_PADDING - RIGHT_PADDING;
    }

    private int getInsideHeight() {
        return this.getWindowHeight() - TOP_PADDING - BOTTOM_PADDING;
    }

    public PlayerUnlocksTree getTree() {
        return this.playerUnlocks.getTree();
    }

    @Override
    public void onAddUnlocksRoot(Holder<PlayerUnlock> root) {
        PlayerUnlocksTab tab = PlayerUnlocksTab.create(this.minecraft, this, this.playerUnlocks, this.tabs.size(), root);
        if (tab == null) {
            return; // ran out of tab slots
        }
        this.tabs.put(root, tab);
    }

    @Override
    public void onRemoveUnlocksRoot(Holder<PlayerUnlock> root) {
        // tabs are rebuilt wholesale on reposition; nothing to do here
    }

    @Override
    public void onAddUnlock(Holder<PlayerUnlock> unlock) {
        PlayerUnlocksTab tab = this.getTab(unlock);
        if (tab != null) {
            tab.addUnlock(unlock);
        }
    }

    @Override
    public void onRemoveUnlock(Holder<PlayerUnlock> unlock) {
        // as above: wholesale rebuild covers removals
    }

    @Override
    public void onUnlockStatusChange(Holder<PlayerUnlock> unlock, boolean unlocked) {
        PlayerUnlockWidget widget = this.getWidget(unlock);
        if (widget != null) {
            widget.unlock(unlocked);
        }
    }

    @Override
    public void onVisibilityStatusChange(Holder<PlayerUnlock> unlock, PlayerUnlock.UnlockVisibility visibility) {
        PlayerUnlockWidget widget = this.getWidget(unlock);
        if (widget != null) {
            widget.visibility(visibility);
        }
        if (this.selectedTab != null) {
            this.selectedTab.updateLayout();
        }
    }

    @Override
    public void onActiveExclusiveStatusChange(Holder<PlayerUnlock> unlock, boolean active) {
        PlayerUnlockWidget widget = this.getWidget(unlock);
        if (widget != null) {
            widget.isActiveExclusive(active);
        }
    }

    @Override
    public void onSelectedTabChanged(@Nullable Holder<PlayerUnlock> root) {
        this.selectedTab = this.tabs.get(root);
    }

    @Override
    public void onUnlocksCleared() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public PlayerUnlockWidget getWidget(Holder<PlayerUnlock> unlock) {
        PlayerUnlocksTab tab = this.getTab(unlock);
        return tab == null ? null : tab.getWidget(unlock);
    }

    @Nullable
    private PlayerUnlocksTab getTab(Holder<PlayerUnlock> unlock) {
        return this.tabs.get(PlayerUnlock.getRoot(unlock));
    }
}
