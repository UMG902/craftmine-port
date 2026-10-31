package dev.craftmine.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import dev.craftmine.command.LevelCommand;
import dev.craftmine.command.RoomCommand;
import dev.craftmine.command.UnlockCommand;
import dev.craftmine.command.UnlockWorldEffectCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the fork's own commands (craftmine registers Level/Unlock/Room/
 * UnlockWorldEffect in its vanilla Commands copy — the port overlays are
 * decompiled classes with no registration site, so the commands were dead).
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Shadow
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$registerForkCommands(Commands.CommandSelection commandSelection, CommandBuildContext context, CallbackInfo ci) {
        LevelCommand.register(this.dispatcher, context);
        UnlockCommand.register(this.dispatcher, context);
        RoomCommand.register(this.dispatcher);
        UnlockWorldEffectCommand.register(this.dispatcher, context);
    }
}
