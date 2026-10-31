package dev.craftmine.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Toast shown when the shared mine-crafter level increases: level number,
 * a hint pointing at the unlocks keybind, and an XP bottle icon. Hides
 * itself instantly if the unlocks screen is already open (the player is
 * looking at the tree anyway).
 */
public class LevelUpToast implements Toast {

    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
    /** Toast duration in ms before the display-time multiplier is applied. */
    private static final long DISPLAY_DURATION_MS = 5000;
    private static final int TOAST_WIDTH = 240;
    private static final int TEXT_X = 30;
    private static final int LEVEL_TEXT_Y = 7;
    private static final int HINT_TEXT_Y = 18;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 8;

    private int level;
    private Toast.Visibility visibility;

    public LevelUpToast(int level) {
        this.level = level;
        this.visibility = Toast.Visibility.SHOW;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            // Keep the toast live-updating while levels pour in (donate spam).
            this.level = player.experienceLevel;
        }
        if (this.visibility == Toast.Visibility.SHOW) {
            boolean unlocksScreenOpen = minecraft.gui.screen() instanceof PlayerUnlocksScreen;
            boolean expired = time >= DISPLAY_DURATION_MS * manager.getNotificationDisplayTimeMultiplier();
            this.visibility = unlocksScreenOpen || expired ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
        }
    }

    @Override
    public int width() {
        return TOAST_WIDTH;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
        graphics.text(font, Component.translatableEscape("level.gained", this.level), TEXT_X, LEVEL_TEXT_Y, -256, false);
        graphics.text(font, Component.translatable("level.unlock_hint", Component.keybind("key.unlocks")), TEXT_X, HINT_TEXT_Y, -1, false);
        graphics.fakeItem(Items.EXPERIENCE_BOTTLE.getDefaultInstance(), ICON_X, ICON_Y);
    }
}
