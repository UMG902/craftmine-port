package dev.craftmine.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * leftPos/topPos/imageWidth are declared on AbstractContainerScreen; mixins on
 * container screen subclasses (InventoryScreenMixin) read them through here.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int craftmine$leftPos();

    @Accessor("topPos")
    int craftmine$topPos();

    @Accessor("imageWidth")
    int craftmine$imageWidth();
}
