package dev.craftmine.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Screen declares `minecraft` on itself and addRenderableWidget as protected -
 * neither is reachable from a mixin on a Screen SUBclass (shadow/visibility
 * rules), so inventory-screen-style mixins go through this accessor.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Accessor("minecraft")
    Minecraft craftmine$minecraft();

    @Invoker("addRenderableWidget")
    <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T craftmine$addRenderableWidget(T widget);
}
