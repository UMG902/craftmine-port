package dev.craftmine.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import dev.craftmine.client.screen.PlayerUnlocksScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.craftmine.duck.CraftmineClientUnlocksDuck;
import dev.craftmine.duck.CraftmineCraftingMenuDuck;

/**
 * Ported from the fork's InventoryScreen (gfj):
 * - a 24x24 SpriteIconButton ("icon/player_unlocks") in the upper-right of
 *   the inventory GUI opening the Player Unlocks screen (pre-existing).
 * - Task 31: the background texture follows the unlock state (fork gfj.a:
 *   inventory_3x3 / inventory / inventory_no_crafting), the title switches
 *   between "Crafting" and nothing (fork gfj.aT_), and the crafting grid
 *   layout is refreshed every tick (fork gfj.E -> cze.e).
 *
 * leftPos/topPos/imageWidth/minecraft and addRenderableWidget are declared on
 * superclasses and are protected - per the project's mixin rule they are read
 * through ScreenAccessor / AbstractContainerScreenAccessor, not @Shadow.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Unique
    private static final Identifier craftmine$INVENTORY_3X3 = Identifier.withDefaultNamespace("textures/gui/container/inventory_3x3.png");

    @Unique
    private static final Identifier craftmine$INVENTORY = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    @Unique
    private static final Identifier craftmine$INVENTORY_NO_CRAFTING = Identifier.withDefaultNamespace("textures/gui/container/inventory_no_crafting.png");

    @Unique
    private SpriteIconButton craftmine$unlocksButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void craftmine$addUnlocksButton(CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        ScreenAccessor screenAccessor = (ScreenAccessor) screen;
        Minecraft minecraft = screenAccessor.craftmine$minecraft();
        this.craftmine$unlocksButton = SpriteIconButton.builder(
                PlayerUnlocksScreen.TITLE,
                button -> minecraft.gui.setScreen(new PlayerUnlocksScreen(
                    ((CraftmineClientUnlocksDuck) minecraft.player.connection).craftmine$getUnlocks(), screen)),
                true)
            .sprite(Identifier.withDefaultNamespace("icon/player_unlocks"), 24, 24)
            .size(24, 24)
            .tooltip(PlayerUnlocksScreen.TITLE)
            .build();
        AbstractContainerScreenAccessor containerAccessor = (AbstractContainerScreenAccessor) screen;
        this.craftmine$unlocksButton.setX(containerAccessor.craftmine$leftPos() + containerAccessor.craftmine$imageWidth());
        this.craftmine$unlocksButton.setY(containerAccessor.craftmine$topPos() - 22);
        screenAccessor.craftmine$addRenderableWidget(this.craftmine$unlocksButton);
    }

    /**
     * Task 31: the background texture follows the unlock state exactly like
     * the fork's gfj.a(fxk, float, int, int): 3x3 texture with "3x3 Inventory
     * Crafting", the normal one with "Inventory Crafting", the no-crafting
     * one otherwise. Implemented as a redirect on the static texture field
     * read inside extractBackground so the vanilla blit/entity code is kept.
     */
    @Redirect(
        method = "extractBackground",
        at = @At(
            value = "FIELD",
            // NOTE: javac emits the SUBCLASS as the Fieldref owner for inherited
            // statics - InventoryScreen.extractBackground reads the inherited
            // INVENTORY_LOCATION unqualified, so the instruction owner is
            // InventoryScreen (verified: constant pool #178 = InventoryScreen.INVENTORY_LOCATION).
            // Using the declaring class (AbstractContainerScreen) here scans 0 targets
            // and crashes production Knot.
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;INVENTORY_LOCATION:Lnet/minecraft/resources/Identifier;",
            opcode = org.objectweb.asm.Opcodes.GETSTATIC
        )
    )
    private Identifier craftmine$unlockBackgroundTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return craftmine$INVENTORY;
        }
        var unlocks = ((CraftmineClientUnlocksDuck) minecraft.player.connection).craftmine$getUnlocks();
        if (unlocks.isActive(dev.craftmine.unlock.PlayerUnlocks.INVENTORY_CRAFTING_3X3)) {
            return craftmine$INVENTORY_3X3;
        }
        if (unlocks.isActive(dev.craftmine.unlock.PlayerUnlocks.INVENTORY_CRAFTING)) {
            return craftmine$INVENTORY;
        }
        return craftmine$INVENTORY_NO_CRAFTING;
    }

    /**
     * Task 31: refresh the crafting grid layout/flags every tick - the
     * fork's gfj.E -> ((cze)this.C).e(this.m.t).
     */
    @Inject(method = "containerTick", at = @At("TAIL"))
    private void craftmine$updateCraftingLayout(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.inventoryMenu instanceof CraftmineCraftingMenuDuck duck) {
            duck.craftmine$updateUnlockSlots();
        }
    }

    /**
     * Task 31: the "Crafting" label only renders while inventory crafting is
     * unlocked (fork gfj.aT_ title switch: xi.c("container.crafting") vs the
     * empty default). 26.3 declares Screen.title final, so instead of
     * swapping the component the label render is cancelled - the vanilla
     * title is already Component.translatable("container.crafting").
     */
    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void craftmine$lockedNoTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var unlocks = ((CraftmineClientUnlocksDuck) minecraft.player.connection).craftmine$getUnlocks();
        if (!unlocks.isActive(dev.craftmine.unlock.PlayerUnlocks.INVENTORY_CRAFTING)) {
            ci.cancel();
        }
    }
}
