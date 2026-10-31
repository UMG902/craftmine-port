package dev.craftmine.client.screen;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec2;
import dev.craftmine.CraftmineConfig;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.duck.CraftmineSlotDuck;
import dev.craftmine.inventory.GhettoSpline;
import dev.craftmine.inventory.MineCraftingDrawerSlot;
import dev.craftmine.inventory.MineCraftingMenu;
import dev.craftmine.inventory.MineCraftingResultSlot;
import dev.craftmine.inventory.MineCraftingSlot;
import dev.craftmine.network.packet.ServerboundMineCrafterRerollPacket;
import dev.craftmine.network.packet.ServerboundPlayerDonateExperiencePacket;
import dev.craftmine.unlock.PlayerUnlocks;

/**
 * The mine crafter UI: the 3x3 ingredient board (with the little spinning
 * slot animation), the effects drawer with its scrollbar, the XP bar +
 * donate button, and the unoriginal reroll button. Background art flips
 * through seven states (idle/active/won/fail/boss variants).
 *
 * <p>26.3 removed the mutable {@code Slot.active} flag, so the drawer's
 * scrolled-out slots are hidden from interaction via
 * {@link #activeDrawerSlots} plus a hit-swallow in
 * {@link #mouseClicked} — 1:1 with the original scrollTo()/setActive()
 * behaviour.</p>
 */
public class MineCraftingScreen extends AbstractContainerScreen<MineCraftingMenu> {

    // ------------------------------------------------------------------
    // Sprites
    // ------------------------------------------------------------------
    private static final Identifier CONTAINER_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier CONTAINER_SLOT_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/crafter/disabled_slot");
    private static final Identifier BANNER_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/level");
    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/loom/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/loom/scroller_disabled");
    private static final Identifier DONATE_BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_donate.png");
    private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter.png");
    private static final Identifier BG_HINTS_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_hints.png");
    private static final Identifier BG_BOSS_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_boss.png");
    private static final Identifier BG_BOSS_ACTIVE_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_boss_active.png");
    private static final Identifier BG_ACTIVE_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_active.png");
    private static final Identifier BG_WON_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_won.png");
    private static final Identifier BG_FAIL_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/mine_crafter_fail.png");
    private static final Identifier EXPERIENCE_BAR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_BAR_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_progress");
    private static final Identifier NO_HINTS_SPRITE = Identifier.withDefaultNamespace("icon/checkmark");

    // ------------------------------------------------------------------
    // Layout constants
    // ------------------------------------------------------------------
    private static final int SCROLLER_FULL_HEIGHT = 70;
    public static final int SCROLL_X_POS = 174;
    public static final int SCROLL_Y_POS = 124;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    /** Slot label text color: the fork's 0x404040 plus an alpha byte (26.3 drops alpha-0 text). */
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int XP_BAR_WIDTH = 160;
    private static final int XP_BAR_HEIGHT = 11;
    private static final int DONATE_XP_COST = 20;

    // ------------------------------------------------------------------
    // Animation state
    // ------------------------------------------------------------------
    /** Full orbit period (ticks) for the crafting-board slot spline. */
    private static final int ORBIT_PERIOD_TICKS = 240;
    private static final float MAP_SPIN_PER_TICK = 0.5F;
    private static final float SLOT_SPIN_PER_TICK = 0.3F;

    private final SpriteIconButton donateButton;
    /** Unoriginal feature: rerolls the random ingredients. Only shown with the QoL reroll unlock. */
    private final SpriteIconButton rerollButton;
    private float scrollOffs;
    private boolean scrolling;
    private int startRow;
    private final float[] rot = {0.0F, 0.0F};
    private final float[] mapRot = {0.0F, 0.0F};
    private int ticks = 0;

    /*
     * 26.3 removed the mutable Slot.active flag (Slot.isActive() is a constant true now),
     * but the mine-crafter drawer scrolling toggles per-slot visibility from the screen only.
     * The active bookkeeping therefore lives here (client-side), 1:1 with the original
     * scrollTo()/setActive() behaviour.
     */
    private final Set<Slot> activeDrawerSlots = new HashSet<>();

    public MineCraftingScreen(MineCraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 193, 205);
        this.donateButton = SpriteIconButton.builder(Component.empty(), this::donateExperienceToMineCrafter, true)
            .sprite(Identifier.withDefaultNamespace("icon/donate_experience"), 24, 24).size(24, 24).build();
        this.donateButton.setTooltip(Tooltip.create(Component.translatable("container.mine_crafter.donate", DONATE_XP_COST)));
        this.rerollButton = SpriteIconButton.builder(Component.empty(), this::rerollMine, true)
            .sprite(Identifier.withDefaultNamespace("icon/reroll"), 12, 12).size(18, 18).build();
        this.rerollButton.setTooltip(Tooltip.create(Component.translatable("container.mine_crafter.reroll")));
        this.rerollButton.visible = false;
    }

    private MineCraftingMenu menu() {
        return this.menu;
    }

    private void rerollMine(Button button) {
        this.minecraft.player.connection.send(ServerboundMineCrafterRerollPacket.INSTANCE);
    }

    /** Reroll button visibility: config on + reroll unlock active (+ special unlock for special mines), mine not started. */
    private boolean canReroll() {
        if (!CraftmineConfig.unoriginalFeatures() || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.menu().isMineActive() || this.menu().isMineCompleted()) {
            return false;
        }
        if (!CraftmineDuckBridge.isActive(this.minecraft.player, PlayerUnlocks.QOL_REROLL)) {
            return false;
        }
        if (this.menu().isBossMine()) {
            return CraftmineDuckBridge.isActive(this.minecraft.player, PlayerUnlocks.QOL_REROLL_SPECIAL);
        }
        return true;
    }

    private void donateExperienceToMineCrafter(Button button) {
        this.minecraft.player.connection.send(ServerboundPlayerDonateExperiencePacket.INSTANCE);
    }

    /** Vanilla XP curve, duplicated from Player.getExperiencePoints (levels -> points). */
    private static int getXpNeededForLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    private int getTotalExperienceBasedOnLevels() {
        int currentLevelProgress = (int) ((float) this.minecraft.player.getXpNeededForNextLevel() * this.minecraft.player.experienceProgress);
        int total = 0;
        for (int level = 0; level < this.minecraft.player.experienceLevel; level++) {
            total += getXpNeededForLevel(level);
        }
        return total + currentLevelProgress;
    }

    private boolean canDonateExperienceToMineCrafter() {
        return this.getTotalExperienceBasedOnLevels() >= DONATE_XP_COST;
    }

    @Override
    protected void init() {
        super.init();
        this.addWidget(this.donateButton);
        this.addWidget(this.rerollButton);
        this.startRow = 0;
        this.scrollTo(this.startRow);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 26.3 text() drops alpha-0 colors: fork's RGB constants need 0xFF alpha bytes.
        if (this.menu().isMineCompleted()) {
            Component outcome = Component.translatable(this.menu().wasMineSuccess()
                ? "container.mine_crafter.won"
                : "container.mine_crafter.fail");
            graphics.text(this.font, outcome, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        }
        if (!this.menu().isBossMine() || this.menu().isMineCompleted()) {
            if (this.menu().isMineActive()) {
                graphics.text(this.font, Component.translatable("container.mine_crafter.active"), this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
            } else if (!this.menu().isMineCompleted()) {
                graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
            }
            // Fork gfq draws this at inventoryLabelY_fork + 39. In 1.21.5 the parent
            // constructor computed inventoryLabelY from the DEFAULT imageHeight (166):
            // 166 - 94 = 72, and gfq only set imageHeight=205 later in init() without
            // re-assigning the label, so the fork's effective label Y was 72 + 39 = 111.
            // 26.3's parent constructor receives the real imageHeight (205), so
            // inventoryLabelY is ALREADY 205 - 94 = 111 - adding the fork's + 39 again
            // double-counted and drew the label 39px too low ("mine effects" offset
            // bug). 111 == 111, so the + 39 must be dropped.
            graphics.text(this.font, Component.translatable("container.mine_crafter.drawer"), this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
            String hintsKey = this.menu().allEffectsAreUnlocked()
                ? "container.mine_crafter.no_hints"
                : "container.mine_crafter.hints";
            MutableComponent hints = Component.translatable(hintsKey);
            int hintsWidth = this.getFont().width(hints);
            graphics.text(this.font, hints, this.titleLabelX + 250 - hintsWidth / 2, this.titleLabelY, LABEL_COLOR, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!this.menu().isBossMine() || this.menu().isMineCompleted()) {
            this.renderExperienceBar(graphics, mouseX, mouseY, partialTick);
            this.renderExperienceLevel(graphics);
        }
    }

    private void renderExperienceBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int barLeft = this.leftPos + 4;
        int required = MineCrafterBlockEntity.experienceRequiredForLevel(this.menu().getCurrentLevel());
        if (required > 0) {
            int progressWidth = (int) ((float) this.menu().getCurrentExp() / (float) required * 161.0F);
            int barTop = this.topPos - 16;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE, barLeft, barTop, XP_BAR_WIDTH, XP_BAR_HEIGHT);
            if (progressWidth > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS_SPRITE, 182, 11, 0, 0, barLeft, barTop, progressWidth, XP_BAR_HEIGHT);
            }
        }
        this.donateButton.setPosition(this.leftPos + this.imageWidth - 28, this.topPos - 23);
        this.donateButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Top right of the panel, directly under the donate ("level up") button.
        this.rerollButton.visible = this.canReroll();
        if (this.rerollButton.visible) {
            this.rerollButton.setPosition(this.leftPos + this.imageWidth - 25, this.topPos + 4);
            this.rerollButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderExperienceLevel(GuiGraphicsExtractor graphics) {
        int nextLevel = this.menu().getCurrentLevel() + 1;
        String label = Component.translatable("container.mine_crafter.level", nextLevel,
            this.menu().getCurrentExp(),
            MineCrafterBlockEntity.experienceRequiredForLevel(this.menu().getCurrentLevel())).getString();
        int labelX = this.leftPos + this.font.width(label) / 2;
        int labelY = this.topPos - 15;
        // Outline passes: fork passed 0 (old font renderer treated alpha-0 as opaque);
        // 26.3 drops alpha-0 text entirely, so use opaque black. Body: fork's RGB triple
        // 0x80FF20 (128,255,32 light green, matches the original snapshot) with 0xFF alpha.
        graphics.text(this.font, label, labelX + 1, labelY, 0xFF000000, false);
        graphics.text(this.font, label, labelX - 1, labelY, 0xFF000000, false);
        graphics.text(this.font, label, labelX, labelY + 1, 0xFF000000, false);
        graphics.text(this.font, label, labelX, labelY - 1, 0xFF000000, false);
        graphics.text(this.font, label, labelX, labelY, 0xFF80FF20, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.ticks = (this.ticks + 1) % ORBIT_PERIOD_TICKS;
        this.mapRot[1] = this.mapRot[1] + MAP_SPIN_PER_TICK;
        this.rot[1] = this.rot[1] + SLOT_SPIN_PER_TICK;
        long filledSlots = this.menu().getCraftingSlots().stream().filter(Slot::hasItem).count();
        for (Slot slot : this.menu().slots) {
            if (((CraftmineSlotDuck) slot).shouldMove()) {
                this.moveSlot(slot, filledSlots);
            }
        }
        this.donateButton.active = this.canDonateExperienceToMineCrafter();
        this.donateButton.setFocused(false);
        this.rerollButton.visible = this.canReroll();
        this.rerollButton.active = this.rerollButton.visible;
        this.rerollButton.setFocused(false);
    }

    /** Board slots orbit along the menu's spline, phase-shifted by their index. */
    private void moveSlot(Slot slot, long filledSlots) {
        GhettoSpline spline = this.menu().getSpline();
        double phase = 0.5 / (double) filledSlots * (double) slot.getContainerSlot();
        double orbit = (double) this.ticks / ORBIT_PERIOD_TICKS + phase;
        Vec2 target = spline.interpolate((float) (orbit + phase));
        ((CraftmineSlotDuck) slot).craftmine$setAnimTarget(target.x, target.y);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        float partialTick = this.minecraft.getDeltaTracker().getRealtimeDeltaTicks();
        CraftmineSlotDuck slotDuck = (CraftmineSlotDuck) slot;
        if (slotDuck.shouldMove()) {
            slotDuck.craftmine$setPosition(
                (int) Mth.lerp(partialTick, (float) slot.x, slotDuck.craftmine$animTargetX()),
                (int) Mth.lerp(partialTick, (float) slot.y, slotDuck.craftmine$animTargetY()));
        }
        if (slotDuck.shouldRotate()) {
            // crafting-board slots and drawer slots spin on separate dials
            float angle = slot instanceof MineCraftingSlot
                ? (this.mapRot[0] = Mth.lerp(partialTick, this.mapRot[0], this.mapRot[1]))
                : (this.rot[0] = Mth.lerp(partialTick, this.rot[0], this.rot[1]));
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) slot.x + 8.0F, (float) slot.y + 8.0F);
            // the old GuiGraphicsExtractor.rotation hack rotated inside the 3D item pipeline where the
            // -16 y-scale flipped the axis, so the 2D rotation has to run inverted to match
            graphics.pose().rotate(-angle);
            graphics.pose().translate(-((float) slot.x + 8.0F), -((float) slot.y + 8.0F));
            super.extractSlot(graphics, slot, mouseX, mouseY);
            graphics.pose().popMatrix();
        } else {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        if (!this.menu().isBossMine() || this.menu().isMineCompleted()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, DONATE_BG_LOCATION, left, top - 20, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        }
        Identifier background;
        if (this.menu().isMineActive()) {
            background = this.menu().isBossMine() ? BG_BOSS_ACTIVE_LOCATION : BG_ACTIVE_LOCATION;
        } else if (this.menu().isMineCompleted()) {
            background = this.menu().wasMineSuccess() ? BG_WON_LOCATION : BG_FAIL_LOCATION;
        } else {
            background = this.menu().isBossMine() ? BG_BOSS_LOCATION : BG_LOCATION;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, left, top, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        if (!this.menu().isMineActive() && !this.menu().isMineCompleted()) {
            for (MineCraftingSlot slot : this.menu().getCraftingSlots()) {
                if (!slot.isActive()) {
                    continue;
                }
                Identifier slotSprite = slot.randomSlot || this.menu().isBossMine()
                    ? CONTAINER_SLOT_DISABLED_SPRITE
                    : CONTAINER_SLOT_SPRITE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slotSprite, left + slot.x - 1, top + slot.y - 1, 18, 18);
            }
            for (MineCraftingDrawerSlot drawerSlot : this.menu().getDrawerSlots()) {
                if (!this.activeDrawerSlots.contains(drawerSlot)
                    || drawerSlot.couldBeAddedToMine() && !this.menu().isBossMine()) {
                    continue;
                }
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CONTAINER_SLOT_DISABLED_SPRITE, left + drawerSlot.x - 1, top + drawerSlot.y - 1, 18, 18);
            }
        }
        MineCraftingResultSlot resultSlot = this.menu().getResultSlot();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BANNER_SLOT_SPRITE, left + resultSlot.x, top + resultSlot.y, 16, 16);
        if (!this.menu().isBossMine() || this.menu().isMineCompleted()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, BG_HINTS_LOCATION, left + 200, top, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
            if (this.menu().allEffectsAreUnlocked()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, NO_HINTS_SPRITE, left + 230, top + 70, 50, 50);
            }
            int scrollerOffset = (int) (55.0F * this.scrollOffs);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_DISABLED_SPRITE,
                left + SCROLL_X_POS, top + SCROLL_Y_POS + scrollerOffset, SCROLLER_WIDTH, SCROLLER_HEIGHT);
        }
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            if (slot instanceof MineCraftingDrawerSlot && !this.activeDrawerSlots.contains(slot)) {
                continue;
            }
            this.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.scrolling = false;
        int scrollerLeft = this.leftPos + SCROLL_X_POS;
        int scrollerTop = this.topPos + SCROLL_Y_POS;
        if (event.x() >= scrollerLeft && event.x() < scrollerLeft + SCROLLER_WIDTH
            && event.y() >= scrollerTop && event.y() < scrollerTop + SCROLLER_FULL_HEIGHT) {
            this.scrolling = this.canScroll();
            return true;
        }
        // 26.3 dropped Slot.active, so hidden (scrolled-out) drawer slots would otherwise be
        // hovered/clicked through the AbstractContainerScreen handlers; swallow those hits
        if (this.hitsInactiveDrawerSlot(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean hitsInactiveDrawerSlot(double x, double y) {
        for (Slot slot : this.menu().slots) {
            if (!slot.isActive() || !this.isHovering(slot.x, slot.y, 16, 16, x, y)) {
                continue;
            }
            return slot instanceof MineCraftingDrawerSlot && !this.activeDrawerSlots.contains(slot);
        }
        return false;
    }

    private boolean canScroll() {
        return this.drawerItems() > 36;
    }

    private int drawerItems() {
        return (int) this.menu().getDrawerSlots().stream().filter(MineCraftingDrawerSlot::hasItem).count();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        int scrollableRows = Mth.ceil((float) this.drawerItems() / 9.0F) - 4;
        if (this.scrolling && scrollableRows > 0) {
            int scrollerTop = this.topPos + SCROLL_Y_POS;
            int scrollerBottom = scrollerTop + SCROLLER_FULL_HEIGHT;
            this.scrollOffs = ((float) event.y() - (float) scrollerTop - 7.5F) / ((float) (scrollerBottom - scrollerTop) - SCROLLER_HEIGHT);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startRow = Math.max((int) ((double) (this.scrollOffs * (float) scrollableRows) + 0.5), 0);
            this.scrollTo(this.startRow);
            return true;
        }
        return super.mouseDragged(event, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        if (!this.canScroll()) {
            return false;
        }
        int scrollableRows = Mth.ceil((float) this.drawerItems() / 9.0F) - 4;
        if (scrollableRows > 0) {
            float step = (float) scrollY / (float) scrollableRows;
            this.scrollOffs = Mth.clamp(this.scrollOffs - step, 0.0F, 1.0F);
            this.startRow = Math.max((int) (this.scrollOffs * (float) scrollableRows + 0.5F), 0);
            this.scrollTo(this.startRow);
        }
        return true;
    }

    @Override
    protected boolean hasClickedOutside(double x, double y, int left, int top) {
        return x < left || y < top || x >= left + this.imageWidth || y >= top + this.imageHeight;
    }

    /** Shows drawer rows [startRow, startRow+4) in the 4 visible drawer rows. */
    private void scrollTo(int startRow) {
        for (int i = 0; i < this.menu().getDrawerSlots().size(); i++) {
            MineCraftingDrawerSlot drawerSlot = this.menu().getDrawerSlots().get(i);
            int visibleRow = Mth.floor((float) i / 9.0F) - startRow;
            if (visibleRow < 0) {
                this.activeDrawerSlots.remove(drawerSlot);
            } else if (visibleRow < 4) {
                this.activeDrawerSlots.add(drawerSlot);
                ((CraftmineSlotDuck) drawerSlot).craftmine$setPosition(drawerSlot.x, SCROLL_Y_POS + visibleRow * 18);
            } else {
                this.activeDrawerSlots.remove(drawerSlot);
            }
        }
    }
}
