package dev.craftmine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.server.CraftmineClientResync;
import dev.craftmine.server.CraftmineLevelCreation;
import dev.craftmine.server.CraftmineLiveReload;
import dev.craftmine.server.CraftmineServerHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.craftmine.server.DimensionGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import dev.craftmine.item.component.WorldModifiers;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.WorldEffect;

/**
 * Command {@code /level}, the admin shortcut for mine creation and travel.
 *
 * <ul>
 *   <li>{@code /level hub} - teleports everyone back to the hub (handled as a
 *       mine win at BlockPos.ZERO, which restores hub inventories).</li>
 *   <li>{@code /level from item} - crafts a mine from the WORLD_MODIFIERS
 *       component of the held item.</li>
 *   <li>{@code /level from effects <e1> [<e2> ... <e21>]} - crafts a mine from
 *       explicitly listed world effects (the effectList tree supports 21
 *       arguments, matching the original snapshot's command surface).</li>
 *   <li>{@code /level from mine <special-mine>} - crafts a specific special mine.</li>
 * </ul>
 *
 * <p>All {@code from} variants share the same pipeline: generate the dimension,
 * synchronize it to clients, rescan the pack repository (see task-58 note
 * below), swap hub inventories out, then teleport players in before the final
 * client resync.
 */
public class LevelCommand {

	/** Maximum number of {@code /level from effects} arguments (matches the snapshot). */
	private static final int MAX_EFFECT_ARGS = 20;

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(Commands.literal("level")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.then(Commands.literal("hub")
				.executes(context -> teleportToHub(context.getSource())))
			.then(Commands.literal("from")
				.then(Commands.literal("item")
					.executes(context -> fromItem(context.getSource())))
				.then(Commands.literal("effects")
					.then(effectList(buildContext, 0, MAX_EFFECT_ARGS)))
				.then(Commands.literal("mine")
					.then(Commands.argument("mine", ResourceArgument.resource(buildContext, CraftmineRegistries.SPECIAL_MINE_KEY))
						.executes(context -> fromMine(context.getSource(),
							ResourceArgument.getResource(context, "mine", CraftmineRegistries.SPECIAL_MINE_KEY)))))));
	}

	/** Builds the recursive {@code effect0..effectN} argument tree of {@code /level from effects}. */
	private static RequiredArgumentBuilder<CommandSourceStack, Holder.Reference<WorldEffect>> effectList(
			CommandBuildContext buildContext, int index, int max) {
		RequiredArgumentBuilder<CommandSourceStack, Holder.Reference<WorldEffect>> node =
			Commands.argument("effect" + index, ResourceArgument.resource(buildContext, CraftmineRegistries.WORLD_EFFECT_KEY))
				.executes(context -> fromEffectList(context, index));
		return index == max ? node : node.then(effectList(buildContext, index + 1, max));
	}

	private static int fromEffectList(CommandContext<CommandSourceStack> context, int max) throws CommandSyntaxException {
		List<WorldEffect> effects = new ArrayList<>();
		for (int i = 0; i <= max; i++) {
			effects.add(ResourceArgument.getResource(context, "effect" + i, CraftmineRegistries.WORLD_EFFECT_KEY).value());
		}
		return fromEffects(context.getSource(), effects);
	}

	private static int fromItem(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack held = player.getMainHandItem();
		WorldModifiers modifiers = held.get(CraftmineDataComponents.WORLD_MODIFIERS);
		if (modifiers == null) {
			// snapshot string, kept verbatim for 1:1 feel
			throw new SimpleCommandExceptionType((Message) Component.literal("Item bork. Plz fix")).create();
		}
		return fromEffects(source, modifiers.effects());
	}

	private static int fromMine(CommandSourceStack source, Holder<SpecialMine> mine) throws CommandSyntaxException {
		List<WorldEffect> effects = mine.value().instantiate(source.getLevel());
		return fromEffects(source, effects, Optional.of(mine.value()));
	}

	private static int fromEffects(CommandSourceStack source, List<WorldEffect> effects) throws CommandSyntaxException {
		return fromEffects(source, effects, Optional.empty());
	}

	private static int fromEffects(CommandSourceStack source, List<WorldEffect> effects, Optional<SpecialMine> specialMine)
			throws CommandSyntaxException {
		DimensionGenerator.GeneratedDimension generated =
			DimensionGenerator.generateDimension(CraftmineServerHooks.theGame(), effects, specialMine);
		Identifier dimensionId = generated.id().identifier();
		source.sendSuccess(() -> Component.translatable("commands.level.success", Component.translationArg(dimensionId)), true);

		generated.synchronize().run();

		// Task 58: datapack is on disk - rescan the pack repository and run a
		// full resource reload so the command dispatcher's build context (and
		// the pack-derived registry layer) see the mine's biomes. Without this,
		// "/locate biome minecraft:levelN/<biome>" fails to parse on first
		// entry (stale CommandBuildContext) until the next server restart.
		CraftmineLiveReload.afterDimensionWritten(CraftmineServerHooks.theGame(), dimensionId);

		for (ServerPlayer player : source.getLevel().players()) {
			((CraftminePlayerDuck) player).swapInventoryFromHub();
		}

		// Task 30 (seamless entry, mirrors leaveForMine): teleport FIRST, then
		// resync. The config-phase rejoin loads the saved (already-teleported)
		// player data, so clients come back directly inside the mine.
		ServerLevel mineLevel = CraftmineLevelCreation.ensureGeneratedLevel(
			CraftmineServerHooks.theGame(), Registries.levelStemToLevel(generated.id()));
		if (mineLevel != null) {
			((CraftmineLevelDuck) mineLevel).teleportAllPlayersToMine(false, Optional.empty());
		}

		CraftmineClientResync.runWhenSynced(CraftmineServerHooks.theGame().server(), () -> {});
		return 1;
	}

	private static int teleportToHub(CommandSourceStack source) {
		for (ServerPlayer player : source.getLevel().players()) {
			((CraftminePlayerDuck) player).setRevisiting(false);
		}
		// a hub return is modelled as winning the mine at ZERO (handles rewards/inventory swap)
		CraftmineDuckBridge.level(source.getLevel()).handleMineWin(BlockPos.ZERO);
		return 1;
	}
}
