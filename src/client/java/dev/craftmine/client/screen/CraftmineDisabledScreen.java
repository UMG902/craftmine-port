package dev.craftmine.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Full-screen notice shown when the user's 25w14craftmine snapshot jar is not
 * installed: the mod loads ALL of its content - blocks, items, world
 * generation, art, sounds and texts - from the user's own snapshot jar at
 * runtime ("bring your own files"), so without it there is nothing to run and
 * the mod stays off. The screen says so explicitly and offers exactly one
 * action: quitting the game. There is deliberately no "continue" and ESC does
 * not close it - the game is unplayable in this state, so the only way out is
 * installing the snapshot and restarting.
 */
public class CraftmineDisabledScreen extends Screen {
    private static final int LINE_SPACING = 12;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    public CraftmineDisabledScreen() {
        super(Component.translatable("craftmine.disabled.title"));
    }

    @Override
    protected void init() {
        // The single exit: stop the client. No continue, no ESC.
        this.addRenderableWidget(Button.builder(Component.translatable("craftmine.disabled.quit"),
                        button -> this.minecraft.stop())
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, this.quitButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Colors are ARGB with EXPLICIT opaque alpha: the 26.3 text pipeline no
        // longer re-sets a zero alpha byte (pre-26.3 Font.adjustColor did), so
        // 0xFFFFFF-style colors render fully transparent.
        int centerX = this.width / 2;
        int wrapWidth = this.wrapWidth();
        int titleX = centerX - this.font.width(this.title) / 2;
        graphics.text(this.font, this.title, titleX, this.height / 2 - 44, 0xFFFFFFFF, true);

        int y = this.bodyY();
        Component body = Component.translatable("craftmine.disabled.body").withStyle(ChatFormatting.GRAY);
        for (FormattedCharSequence line : this.wrap(body, wrapWidth)) {
            graphics.text(this.font, line, centerX - this.font.width(line) / 2, y, 0xFFA0A0A0, true);
            y += LINE_SPACING;
        }
        y += 8;
        Component fix = Component.translatable("craftmine.disabled.fix").withStyle(ChatFormatting.GOLD);
        for (FormattedCharSequence line : this.wrap(fix, wrapWidth)) {
            graphics.text(this.font, line, centerX - this.font.width(line) / 2, y, 0xFFFFAA00, true);
            y += LINE_SPACING;
        }
    }

    /**
     * Shared text layout so the button in {@link #init()} is placed below the
     * wrapped text no matter how many lines the message breaks into.
     */
    private int wrapWidth() {
        return Math.max(160, this.width - 80);
    }

    private int bodyY() {
        return this.height / 2 - 18;
    }

    private int textEndY() {
        int y = this.bodyY();
        y += this.wrap(Component.translatable("craftmine.disabled.body").withStyle(ChatFormatting.GRAY), this.wrapWidth()).size() * LINE_SPACING;
        y += 8;
        y += this.wrap(Component.translatable("craftmine.disabled.fix").withStyle(ChatFormatting.GOLD), this.wrapWidth()).size() * LINE_SPACING;
        return y;
    }

    private int quitButtonY() {
        return Math.min(this.height - BUTTON_HEIGHT - 4, this.textEndY() + 16);
    }

    private List<FormattedCharSequence> wrap(Component component, int width) {
        return this.font.split(component, width);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
