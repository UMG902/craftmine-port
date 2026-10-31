package dev.craftmine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.registry.CraftmineRegistries;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceSelectorArgument;
import net.minecraft.core.Holder;
import net.minecraft.server.permissions.Permissions;
import dev.craftmine.mines.WorldEffect;

/**
 * Debug/cheat command {@code /unlock_world_effect <effect-selector>}: unlocks
 * every matching world effect on the executing player's level without going
 * through the unlock tree or mine wins. Accepts any resource selector, so a
 * tag like {@code #craftmine:world_effects} unlocks many effects at once.
 * Requires gamemaster permissions.
 */
public class UnlockWorldEffectCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(Commands.literal("unlock_world_effect")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.then(Commands.argument("effect", ResourceSelectorArgument.resourceSelector(buildContext, CraftmineRegistries.WORLD_EFFECT_KEY))
				.executes(context -> unlock(context.getSource(),
					ResourceSelectorArgument.getSelectedResources(context, "effect")))));
	}

	private static int unlock(CommandSourceStack source, Collection<Holder.Reference<WorldEffect>> effects) {
		CraftmineLevelDuck level = (CraftmineLevelDuck) source.getLevel();
		effects.forEach(effect -> level.unlockEffect((WorldEffect) effect.value()));
		return 1;
	}
}
