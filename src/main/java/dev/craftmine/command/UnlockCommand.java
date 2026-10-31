package dev.craftmine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineRegistries;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.server.permissions.Permissions;
import dev.craftmine.unlock.PlayerUnlock;

/**
 * Debug/cheat command {@code /unlock <unlock>}: force-grants an unlock to the
 * executing player, walking up the parent chain so prerequisite unlocks are
 * granted too (buying normally enforces the chain; this command bypasses cost).
 * Requires gamemaster permissions.
 */
public class UnlockCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(Commands.literal("unlock")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.then(Commands.argument("unlock", ResourceArgument.resource(buildContext, CraftmineRegistries.PLAYER_UNLOCK_KEY))
				.executes(context -> unlock(context.getSource(),
					ResourceArgument.getResource(context, "unlock", CraftmineRegistries.PLAYER_UNLOCK_KEY)))));
	}

	private static int unlock(CommandSourceStack source, Holder<PlayerUnlock> unlock) throws CommandSyntaxException {
		// grant parents first so the unlock tree stays consistent
		unlock.value().parent().ifPresent(parent -> {
			try {
				unlock(source, parent);
			} catch (CommandSyntaxException e) {
				throw new RuntimeException(e);
			}
		});
		((CraftminePlayerDuck) source.getPlayerOrException()).forceUnlock(unlock);
		return 1;
	}
}
