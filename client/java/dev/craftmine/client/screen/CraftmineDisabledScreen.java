package dev.craftmine.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Full-screen notice shown when the user's 25w14craftmine snapshot jar is not
 * installed: the mod loads ALL of its art, sounds and texts from the user's
 * own snapshot installation at runtime ("bring your own files"), so without
 * it there is nothing to run - the mod stays off and this screen explains why
 * and how to fix it. Replaces the earlier one-line gold warning that was easy
 * to miss under the title screen.
 */
public class CraftmineDisabledScreen extends Screen {
    private static final int LINE_SPACING = 12;

    public CraftmineDisabledScreen() {
        super(Component.translatable("craftmine.disabled.title"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int wrapWidth = Math.max(160, this.width - 80);
        graphics.centeredText(this.font, this.title, centerX, this.height / 2 - 44, 0xFFFFFF);

        Component body = Component.translatable("craftmine.disabled.body").withStyle(ChatFormatting.GRAY);
        int y = this.height / 2 - 18;
        for (FormattedCharSequence line : wrap(body, wrapWidth)) {
            graphics.centeredText(this.font, line, centerX, y, 0xA0A0A0);
            y += LINE_SPACING;
        }
        y += 8;
        Component fix = Component.translatable("craftmine.disabled.fix").withStyle(ChatFormatting.GOLD);
        for (FormattedCharSequence line : wrap(fix, wrapWidth)) {
            graphics.centeredText(this.font, line, centerX, y, 0xFFAA00);
            y += LINE_SPACING;
        }
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
