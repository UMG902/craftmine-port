package dev.craftmine.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import dev.craftmine.CraftmineConfig;
import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.duck.CraftmineDisplayInfoDuck;
import dev.craftmine.network.packet.ServerboundPlayerBuyUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerRelockUnlockPacket;
import dev.craftmine.network.packet.ServerboundPlayerReactivateUnlockPacket;
import dev.craftmine.registry.CraftmineSoundEvents;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import dev.craftmine.unlock.PlayerUnlock;
import dev.craftmine.unlock.PlayerUnlocks;
import org.jetbrains.annotations.Nullable;

/**
 * One node in the unlocks tree: the framed icon on the board plus the hover
 * card (title, price/status line, description or mystery hint). Sprite
 * state is four-way (obtained/active/locked variants the vanilla
 * advancement enum lacks); click routing goes through real serverbound
 * packets because the port is client/server-split.
 */
public class PlayerUnlockWidget {

    private static final Identifier TITLE_BOX_SPRITE = Identifier.withDefaultNamespace("advancements/title_box");
    private static final int HEIGHT = 26;
    private static final int BOX_X = 0;
    private static final int BOX_WIDTH = 200;
    private static final int FRAME_WIDTH = 26;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 5;
    private static final int ICON_WIDTH = 26;
    private static final int TITLE_PADDING_LEFT = 3;
    private static final int TITLE_PADDING_RIGHT = 5;
    private static final int TITLE_X = 32;
    private static final int TITLE_PADDING_TOP = 9;
    private static final int TITLE_PADDING_BOTTOM = 8;
    private static final int TITLE_MAX_WIDTH = 163;
    private static final int TITLE_MIN_WIDTH = 80;
    private static final int[] TEST_SPLIT_OFFSETS = {0, 10, -10, 25, -25};
    /** Accepted drift when a split lands close enough to the target width. */
    private static final float SPLIT_TOLERANCE = 10.0F;
    private static final List<FormattedCharSequence> MYSTERY_TITLE =
        List.of(FormattedCharSequence.forward("much mystery", Style.EMPTY.withObfuscated(true)));

    private final PlayerUnlocksTab tab;
    private final Holder<PlayerUnlock> node;
    final DisplayInfo display;
    final Minecraft minecraft;
    private final int cost;
    @Nullable
    private PlayerUnlockWidget parent;
    private final List<PlayerUnlockWidget> children = Lists.newArrayList();
    private final boolean hasExclusiveKey;
    private boolean activeExclusive;
    private PlayerUnlock.UnlockVisibility visibility;
    private boolean unlocked;
    @Nullable
    private TooltipInfo tooltipInfo;

    public PlayerUnlockWidget(PlayerUnlocksTab tab, Minecraft minecraft, Holder<PlayerUnlock> node, DisplayInfo display,
                              PlayerUnlock.UnlockVisibility visibility, boolean unlocked, int cost,
                              boolean hasExclusiveKey, boolean activeExclusive) {
        this.tab = tab;
        this.node = node;
        this.display = display;
        this.minecraft = minecraft;
        this.hasExclusiveKey = hasExclusiveKey;
        this.activeExclusive = activeExclusive;
        this.visibility = visibility;
        this.unlocked = unlocked;
        this.cost = cost;
    }

    /*
     * Node positions: the tree/radial layouts write x/y into the DisplayInfo via the
     * CraftmineDisplayInfoDuck (DisplayInfoMixin carries the state; the fork had mutable
     * fields directly on DisplayInfo). The widget MUST read the same store - an earlier
     * port revision kept a separate static map here that nothing ever wrote, so every
     * node rendered at (0,0) (all unlocks stacked in one spot).
     */
    protected int getX() {
        return Mth.floor(((CraftmineDisplayInfoDuck) (Object) this.display).craftmine$getX() * 28.0F);
    }

    protected int getY() {
        return Mth.floor(((CraftmineDisplayInfoDuck) (Object) this.display).craftmine$getY() * 27.0F);
    }

    /** Width of the widest status label ("unlocked/locked" or "active/inactive") + padding. */
    int getMaxStatusWidth() {
        MutableComponent unlockedLabel = Component.translatable("unlocks.screen.unlocked");
        MutableComponent lockedLabel = Component.translatable("unlocks.screen.locked");
        if (this.hasExclusiveKey) {
            MutableComponent activeLabel = Component.translatable("unlocks.screen.active");
            MutableComponent inactiveLabel = Component.translatable("unlocks.screen.inactive");
            return Math.max(
                Math.max(this.minecraft.font.width(activeLabel), this.minecraft.font.width(inactiveLabel)),
                Math.max(this.minecraft.font.width(unlockedLabel), this.minecraft.font.width(lockedLabel))) + 8;
        }
        return Math.max(this.minecraft.font.width(unlockedLabel), this.minecraft.font.width(lockedLabel)) + 8;
    }

    private static float maxLineWidth(StringSplitter splitter, List<FormattedText> lines) {
        return (float) lines.stream().mapToDouble(splitter::stringWidth).max().orElse(0.0);
    }

    /**
     * Splits text to the target width, trying a few offsets around it and
     * keeping the split whose longest line hugs the target closest.
     */
    List<FormattedText> findOptimalLines(Component text, int targetWidth) {
        StringSplitter splitter = this.minecraft.font.getSplitter();
        List<FormattedText> best = null;
        float bestDrift = Float.MAX_VALUE;
        for (int offset : TEST_SPLIT_OFFSETS) {
            List<FormattedText> lines = splitter.splitLines(text, targetWidth - offset, Style.EMPTY);
            float drift = Math.abs(maxLineWidth(splitter, lines) - (float) targetWidth);
            if (drift <= SPLIT_TOLERANCE) {
                return lines;
            }
            if (drift < bestDrift) {
                bestDrift = drift;
                best = lines;
            }
        }
        return best;
    }

    /** Walks up until the first ancestor that is placed AND visible (hidden nodes collapse). */
    private PlayerUnlockWidget getFirstVisibleParent(Holder<PlayerUnlock> start) {
        Optional<Holder<PlayerUnlock>> parent = start.value().parent();
        while (parent.isPresent()) {
            PlayerUnlockWidget widget = this.tab.getWidget(parent.get());
            if (widget != null && widget.isVisibleAtAll()) {
                break;
            }
            parent = parent.get().value().parent();
        }
        return this.tab.getWidget(parent.orElse(start));
    }

    /**
     * Draws the parent->this connector lines (drawn twice per tree: shadow
     * pass and highlight pass, so the elbow always reads on any background).
     */
    public void drawConnectivity(GuiGraphicsExtractor graphics, int scrollLeft, int scrollTop, boolean shadow) {
        if (this.parent != null) {
            int color = shadow ? -16777216 : -1;
            int parentCenterX = scrollLeft + this.parent.getX() + 13;
            int parentEdgeX = scrollLeft + this.parent.getX() + 26 + 4;
            int parentCenterY = scrollTop + this.parent.getY() + 13;
            int myCenterX = scrollLeft + this.getX() + 13;
            int myCenterY = scrollTop + this.getY() + 13;
            if (shadow) {
                graphics.horizontalLine(parentEdgeX, parentCenterX, parentCenterY - 1, color);
                graphics.horizontalLine(parentEdgeX + 1, parentCenterX, parentCenterY, color);
                graphics.horizontalLine(parentEdgeX, parentCenterX, parentCenterY + 1, color);
                graphics.horizontalLine(myCenterX, parentEdgeX - 1, myCenterY - 1, color);
                graphics.horizontalLine(myCenterX, parentEdgeX - 1, myCenterY, color);
                graphics.horizontalLine(myCenterX, parentEdgeX - 1, myCenterY + 1, color);
                graphics.verticalLine(parentEdgeX - 1, myCenterY, parentCenterY, color);
                graphics.verticalLine(parentEdgeX + 1, myCenterY, parentCenterY, color);
            } else {
                graphics.horizontalLine(parentEdgeX, parentCenterX, parentCenterY, color);
                graphics.horizontalLine(myCenterX, parentEdgeX, myCenterY, color);
                graphics.verticalLine(parentEdgeX, myCenterY, parentCenterY, color);
            }
        }
        for (PlayerUnlockWidget child : this.children) {
            if (child.isVisibleAtAll()) {
                child.drawConnectivity(graphics, scrollLeft, scrollTop, shadow);
            }
        }
    }

    public void draw(GuiGraphicsExtractor graphics, int scrollLeft, int scrollTop) {
        if (!this.display.hidden() || this.unlocked) {
            Identifier frame = this.getSpriteType().frameSprite(this.getFrameShape());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, scrollLeft + this.getX() + 3, scrollTop + this.getY(), 26, 26);
            ItemStack icon = this.getIconItem();
            if (icon != null) {
                graphics.fakeItem(icon, scrollLeft + this.getX() + 8, scrollTop + this.getY() + 5);
            }
        }
        for (PlayerUnlockWidget child : this.children) {
            if (child.isVisibleAtAll()) {
                child.draw(graphics, scrollLeft, scrollTop);
            }
        }
    }

    /** Mystery unlocks always draw the challenge frame regardless of their real type. */
    private AdvancementType getFrameShape() {
        return this.visibility == PlayerUnlock.UnlockVisibility.MYSTERY
            ? AdvancementType.CHALLENGE
            : this.display.type();
    }

    /** Mystery unlocks hide their icon behind a poisonous potato; hidden ones draw nothing. */
    @Nullable
    private ItemStack getIconItem() {
        if (this.visibility == PlayerUnlock.UnlockVisibility.MYSTERY) {
            return Items.POISONOUS_POTATO.getDefaultInstance();
        }
        if (this.visibility == PlayerUnlock.UnlockVisibility.VISIBLE) {
            return this.display.icon().create();
        }
        return null;
    }

    private TooltipInfo tooltipInfo() {
        if (this.tooltipInfo == null) {
            this.tooltipInfo = new TooltipInfo(this);
        }
        return this.tooltipInfo;
    }

    public int getWidth() {
        return this.tooltipInfo().width;
    }

    public void unlock(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public void visibility(PlayerUnlock.UnlockVisibility visibility) {
        this.visibility = visibility;
    }

    public void isActiveExclusive(boolean active) {
        this.activeExclusive = active;
    }

    public void addChild(PlayerUnlockWidget child) {
        this.children.add(child);
    }

    /**
     * The full hover card. Flips to the left of the node when it would run
     * off the screen's right edge, and above the node when it would run off
     * the bottom.
     */
    public void drawHover(GuiGraphicsExtractor graphics, int scrollLeft, int scrollTop, float fade,
                          int mouseX, int mouseY, int viewWidth, int viewHeight) {
        if (!this.isVisibleAtAll()) {
            Identifier frame = WidgetSpriteType.UNOBTAINED.frameSprite(this.getFrameShape());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, scrollLeft + this.getX() + 3, scrollTop + this.getY(), 26, 26);
            return;
        }
        TooltipInfo info = this.tooltipInfo();
        Font font = this.minecraft.font;
        int titleBlockHeight = font.lineHeight * info.titleLines.size() + TITLE_PADDING_TOP + TITLE_PADDING_BOTTOM;
        int titleTop = scrollTop + this.getY() + (26 - titleBlockHeight) / 2;
        int titleBottom = titleTop + titleBlockHeight;
        int descriptionHeight = this.getDescriptionText().size() * font.lineHeight;
        int descriptionBlockHeight = 6 + descriptionHeight;
        boolean flipHorizontally = mouseX + scrollLeft + this.getX() + info.width + 26 >= this.tab.getScreen().width;

        MutableComponent statusLine = Component.empty();
        if (this.visibility == PlayerUnlock.UnlockVisibility.VISIBLE) {
            if (this.unlocked) {
                statusLine = this.hasExclusiveKey
                    ? (this.activeExclusive ? Component.translatable("unlocks.screen.active") : Component.translatable("unlocks.screen.inactive"))
                    : Component.translatable("unlocks.screen.unlocked");
            } else {
                boolean affordable = this.minecraft.player.experienceLevel >= this.cost;
                ChatFormatting color = affordable ? ChatFormatting.GREEN : ChatFormatting.RED;
                statusLine = Component.translatable("unlocks.screen.locked", Component.literal(String.valueOf(this.cost)).withStyle(color));
            }
        }
        int statusWidth = font.width(statusLine);
        boolean flipVertically = titleBottom + descriptionBlockHeight >= viewHeight;
        int cardLeft = flipHorizontally
            ? scrollLeft + this.getX() - info.width + 26 + 6
            : scrollLeft + this.getX();
        int cardHeight = titleBlockHeight + descriptionBlockHeight;
        if (!info.description.isEmpty()) {
            int cardTop = flipVertically ? titleBottom - cardHeight : titleTop;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TITLE_BOX_SPRITE, cardLeft, cardTop, info.width, cardHeight);
        }
        WidgetSpriteType spriteType = this.getSpriteType();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteType.boxSprite(), cardLeft, titleTop, info.width, titleBlockHeight);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteType.frameSprite(this.getFrameShape()),
            scrollLeft + this.getX() + 3, scrollTop + this.getY(), 26, 26);
        int textLeft = cardLeft + 5;
        if (flipHorizontally) {
            this.drawMultilineText(graphics, this.getTitleText(), textLeft, titleTop + TITLE_PADDING_TOP, -1);
            graphics.text(font, statusLine, scrollLeft + this.getX() - statusWidth, titleTop + TITLE_PADDING_TOP, -1);
        } else {
            this.drawMultilineText(graphics, this.getTitleText(), scrollLeft + this.getX() + 32, titleTop + TITLE_PADDING_TOP, -1);
            graphics.text(font, statusLine, scrollLeft + this.getX() + info.width - statusWidth - 5, titleTop + TITLE_PADDING_TOP, -1);
        }
        if (flipVertically) {
            this.drawMultilineText(graphics, this.getDescriptionText(), textLeft, titleTop - descriptionHeight + 1, -16711936);
        } else {
            this.drawMultilineText(graphics, this.getDescriptionText(), textLeft, titleBottom, -16711936);
        }
        ItemStack icon = this.getIconItem();
        if (icon != null) {
            graphics.fakeItem(icon, scrollLeft + this.getX() + 8, scrollTop + this.getY() + 5);
        }
    }

    private WidgetSpriteType getSpriteType() {
        if (this.unlocked) {
            return this.activeExclusive ? WidgetSpriteType.OBTAINED_ACTIVE : WidgetSpriteType.OBTAINED;
        }
        boolean parentUnlocked = this.parent == null || this.parent.unlocked;
        return parentUnlocked && this.visibility == PlayerUnlock.UnlockVisibility.VISIBLE
            ? WidgetSpriteType.UNOBTAINED
            : WidgetSpriteType.UNOBTAINED_LOCKED;
    }

    private List<FormattedCharSequence> getTitleText() {
        return this.visibility == PlayerUnlock.UnlockVisibility.VISIBLE ? this.tooltipInfo().titleLines : MYSTERY_TITLE;
    }

    private List<FormattedCharSequence> getDescriptionText() {
        return this.visibility == PlayerUnlock.UnlockVisibility.VISIBLE ? this.tooltipInfo().description : this.tooltipInfo().hint;
    }

    private void drawMultilineText(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int x, int y, int color) {
        Font font = this.minecraft.font;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y + i * font.lineHeight, color);
        }
    }

    public boolean isMouseOver(int scrollLeft, int scrollTop, int mouseX, int mouseY) {
        if (this.display.hidden() && !this.unlocked) {
            return false;
        }
        if (!this.isVisibleAtAll()) {
            return false;
        }
        int left = scrollLeft + this.getX();
        int top = scrollTop + this.getY();
        return mouseX >= left && mouseX <= left + 26 && mouseY >= top && mouseY <= top + 26;
    }

    private boolean isVisibleAtAll() {
        return this.clientUnlocks().isVisibleAtAll(this.node);
    }

    public void attachToParent() {
        if (this.parent == null && this.node.value().parent().isPresent()) {
            this.parent = this.getFirstVisibleParent(this.node);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }
    }

    public void onClicked() {
        this.onClicked(false);
    }

    /**
     * @param relock ctrl was held. With the unoriginal relock feature available (config on and the base
     *               relock unlock active) a ctrl+click ONLY ever relocks - it never buys - so a slip of the
     *               ctrl key cannot spend levels. Without the feature it behaves like a normal click.
     */
    public void onClicked(boolean relock) {
        if (relock && CraftmineConfig.unoriginalFeatures()) {
            ClientPlayerUnlocks relockUnlocks = this.clientUnlocks();
            if (relockUnlocks.isActive(PlayerUnlocks.QOL_RELOCK)) {
                if (relockUnlocks.canRelock(this.node)) {
                    this.minecraft.player.connection.send(new ServerboundPlayerRelockUnlockPacket(this.node));
                    this.playUiSound(CraftmineSoundEvents.UI_PLAYER_UNLOCK_SUCCESS, 1.0F, 0.5F);
                } else {
                    this.playUiSound(CraftmineSoundEvents.UI_PLAYER_UNLOCK_FAIL, 1.0F, 0.5F);
                }
                return;
            }
        }
        /*
         * FIX: the fork ran buyUnlock/reactivateUnlock directly on the player
         * (single-JVM game). The port is client/server-split - route through
         * the real serverbound packets (server handler applies the unlock and
         * the ClientboundUpdatePlayerUnlocksPacket syncs state back).
         * Task 38 (fork guz.d/e = the fork's client player, 1:1): the fork gates
         * the click client-side and plays the nothingtoseehere:ui.player_unlock_*
         * sounds as purchase prediction - SUCCESS when the purchase is sent,
         * FAIL when it is refused. The client gate mirrors the fork's two checks:
         *   - the server-synced per-unlock status == unlockable (the
         *     port's ClientPlayerUnlocks.isUnlockable), and
         *   - the fork's local check: xp level >= unlock price, not already
         *     owned, parent (if any) owned - the same rules the server
         *     re-validates in ServerPlayerMixin.craftmine$baseBuyUnlock.
         * The reactivate path has no fail branch: silently no-op when
         * the node is not visible at all.
         */
        ClientPlayerUnlocks unlocks = this.clientUnlocks();
        if (!this.unlocked) {
            boolean parentOwned = this.node.value().parent().isEmpty()
                || unlocks.isUnlocked(this.node.value().parent().get());
            boolean canPurchase = this.minecraft.player.experienceLevel >= this.node.value().unlockPrice()
                && !unlocks.isUnlocked(this.node)
                && parentOwned;
            if (unlocks.isUnlockable(this.node) && canPurchase) {
                this.minecraft.player.connection.send(new ServerboundPlayerBuyUnlockPacket(this.node));
                this.playUiSound(CraftmineSoundEvents.UI_PLAYER_UNLOCK_SUCCESS, 2.0F, 0.5F);
            } else {
                this.playUiSound(CraftmineSoundEvents.UI_PLAYER_UNLOCK_FAIL, 1.0F, 0.5F);
            }
        } else if (unlocks.isVisibleAtAll(this.node)) {
            this.minecraft.player.connection.send(new ServerboundPlayerReactivateUnlockPacket(this.node));
            this.playUiSound(CraftmineSoundEvents.UI_PLAYER_UNLOCK_SUCCESS, 2.0F, 0.5F);
        }
    }

    private ClientPlayerUnlocks clientUnlocks() {
        return ((CraftmineClientUnlocksDuck) this.minecraft.player.connection).craftmine$getUnlocks();
    }

    private void playUiSound(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /**
     * Lazily computed, per-widget tooltip metrics: title lines, description
     * (tinted with the unlock type color), and the mystery hint rebuilt from
     * the unlock key (26.3's DisplayInfo record no longer carries the
     * fork's hint component). Width accommodates the widest of the three.
     */
    class TooltipInfo {
        final List<FormattedCharSequence> titleLines;
        final int width;
        final List<FormattedCharSequence> description;
        final List<FormattedCharSequence> hint;

        TooltipInfo(PlayerUnlockWidget widget) {
            this.titleLines = widget.minecraft.font.split(widget.display.title(), TITLE_MAX_WIDTH);
            int minTitleWidth = Math.max(
                this.titleLines.stream().mapToInt(widget.minecraft.font::width).max().orElse(0), TITLE_MIN_WIDTH);
            int cardWidth = 29 + minTitleWidth + widget.getMaxStatusWidth();
            this.description = Language.getInstance().getVisualOrder(widget.findOptimalLines(
                ComponentUtils.mergeStyles(
                    widget.display.description().copy(),
                    Style.EMPTY.withColor(widget.display.type().getChatColor())),
                cardWidth));
            for (FormattedCharSequence line : this.description) {
                cardWidth = Math.max(cardWidth, widget.minecraft.font.width(line));
            }
            MutableComponent hintText = Component.translatable("unlocks.screen.hint",
                ComponentUtils.mergeStyles(
                    Component.translatable("unlocks.unlock." + widget.node.value().key() + ".hint").copy(),
                    Style.EMPTY.withColor(ChatFormatting.GRAY)));
            this.hint = Language.getInstance().getVisualOrder(widget.findOptimalLines(hintText, cardWidth));
            for (FormattedCharSequence line : this.hint) {
                cardWidth = Math.max(cardWidth, widget.minecraft.font.width(line));
            }
            this.width = cardWidth + TITLE_PADDING_LEFT + TITLE_PADDING_RIGHT;
        }
    }

    /*
     * Craftmine's snapshot expanded the vanilla AdvancementWidgetType enum with the
     * OBTAINED_ACTIVE and UNOBTAINED_LOCKED states; 26.3's enum only has OBTAINED/UNOBTAINED,
     * so the four-state sprite table lives here with the exact same sprite ids.
     */
    private enum WidgetSpriteType {
        OBTAINED_ACTIVE("advancements/box_obtained_active", "advancements/task_frame_obtained_active",
            "advancements/challenge_frame_obtained", "advancements/goal_frame_obtained"),
        OBTAINED("advancements/box_obtained", "advancements/task_frame_obtained",
            "advancements/challenge_frame_obtained", "advancements/goal_frame_obtained"),
        UNOBTAINED("advancements/box_unobtained", "advancements/task_frame_unobtained",
            "advancements/challenge_frame_unobtained", "advancements/goal_frame_unobtained"),
        UNOBTAINED_LOCKED("advancements/box_unobtained_locked", "advancements/task_frame_unobtained_locked",
            "advancements/challenge_frame_unobtained_locked", "advancements/goal_frame_unobtained_locked");

        private final Identifier boxSprite;
        private final Identifier taskFrameSprite;
        private final Identifier challengeFrameSprite;
        private final Identifier goalFrameSprite;

        WidgetSpriteType(String box, String task, String challenge, String goal) {
            this.boxSprite = Identifier.withDefaultNamespace(box);
            this.taskFrameSprite = Identifier.withDefaultNamespace(task);
            this.challengeFrameSprite = Identifier.withDefaultNamespace(challenge);
            this.goalFrameSprite = Identifier.withDefaultNamespace(goal);
        }

        public Identifier boxSprite() {
            return this.boxSprite;
        }

        public Identifier frameSprite(AdvancementType type) {
            return switch (type) {
                case TASK -> this.taskFrameSprite;
                case CHALLENGE -> this.challengeFrameSprite;
                case GOAL -> this.goalFrameSprite;
            };
        }
    }
}
