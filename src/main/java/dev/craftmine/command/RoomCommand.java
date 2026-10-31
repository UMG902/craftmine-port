package dev.craftmine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.craftmine.mixin.MinecraftServerAccessor;
import dev.craftmine.registry.CraftmineContent;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.item.component.RoomerinoComponentino;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.FileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Map-making tool for hub rooms: {@code /room start|save|key}.
 *
 * <ul>
 *   <li>{@code start <structure> <x> <z> <y>} - draws a bedrock shell of the
 *       given size in front of the executor and plants a SAVE structure block
 *       for filling the interior.</li>
 *   <li>{@code save <structure>} - copies a finished template from the world's
 *       {@code generated/} directory into the level datapack
 *       ({@code datapack_structures/}), then hands you the shimmering key.</li>
 *   <li>{@code key <structure>} - just gives the shimmering key item bound to
 *       the structure id.</li>
 * </ul>
 *
 * <p>Template ids are namespaced through RoomerinoComponentino so hub rooms
 * live under the {@code hub/room/} path family; the save/key arguments
 * autocomplete from the structure template manager.
 *
 * <p><b>Deliberate parity divergence:</b> the snapshot registers this command
 * with no {@code requires} clause at all (apq.a in 25w14craftmine) - harmless
 * in a singleplayer April-Fools snapshot, but on a server any survival player
 * could build bedrock shells, write templates into the world's datapack and
 * mint shimmering keys. Gated at gamemaster (op level 2) like the port's other
 * craftmine commands, mirroring the snapshot's own gating of /unlock and
 * /unlock_world_effect ({@code hasPermission(2)}).
 */
public class RoomCommand {

        /** Where {@code /room start} builds, relative to the executing player. */
        public static final LocalCoordinates LAZY_DEV_TARGET = new LocalCoordinates(0.0, 0.0, 6.0);

        private static final SuggestionProvider<CommandSourceStack> SUGGEST_PACK_TEMPLATES = templateSuggestions();
        private static final SuggestionProvider<CommandSourceStack> SUGGEST_WORLD_TEMPLATES = templateSuggestions();

        private RoomCommand() {
        }

        /** Suggests every loaded template under {@code hub/room/}. */
        private static SuggestionProvider<CommandSourceStack> templateSuggestions() {
                return (context, builder) -> {
                        StructureTemplateManager templates = context.getSource().getLevel().getStructureTemplateManager();
                        return SharedSuggestionProvider.suggestResource(
                                templates.listTemplates()
                                        .filter(id -> id.getPath().startsWith("hub/room/"))
                                        .map(RoomerinoComponentino::structureToId),
                                builder);
                };
        }

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("room")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("start")
                                .then(Commands.argument("structure", IdentifierArgument.id())
                                        .then(Commands.argument("xSize", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("zSize", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("ySize", IntegerArgumentType.integer(1))
                                                                .executes(context -> createTemplate(context.getSource(),
                                                                        IdentifierArgument.getId(context, "structure"),
                                                                        IntegerArgumentType.getInteger(context, "xSize"),
                                                                        IntegerArgumentType.getInteger(context, "zSize"),
                                                                        IntegerArgumentType.getInteger(context, "ySize"))))))))
                        .then(Commands.literal("save")
                                .then(Commands.argument("structure", IdentifierArgument.id())
                                        .suggests(SUGGEST_WORLD_TEMPLATES)
                                        .executes(context -> save(context.getSource(), IdentifierArgument.getId(context, "structure")))))
                        .then(Commands.literal("key")
                                .then(Commands.argument("structure", IdentifierArgument.id())
                                        .suggests(SUGGEST_PACK_TEMPLATES)
                                        .executes(context -> giveKey(context.getSource(), IdentifierArgument.getId(context, "structure"))))));
        }

        private static int createTemplate(CommandSourceStack source, Identifier structureId, int xSize, int zSize, int ySize) {
                BlockPos origin = LAZY_DEV_TARGET.getBlockPos(source);
                ServerLevel level = source.getLevel();

                // hollow shell: walls + floor, open top (the player fills the inside and caps it)
                for (int x = 0; x < xSize; x++) {
                        for (int z = 0; z < zSize; z++) {
                                for (int y = 0; y < ySize; y++) {
                                        boolean isShell = x == 0 || x == xSize - 1 || z == 0 || z == zSize - 1 || y == 0;
                                        if (isShell) {
                                                level.setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                                        }
                                }
                        }
                }

                BlockPos structureBlockPos = origin.below();
                BlockState structureBlock = Blocks.STRUCTURE_BLOCK.defaultBlockState()
                        .setValue(StructureBlock.MODE, StructureMode.SAVE);
                level.setBlock(structureBlockPos, structureBlock, 3);
                Optional<StructureBlockEntity> blockEntity = level.getBlockEntity(structureBlockPos, BlockEntityTypes.STRUCTURE_BLOCK);
                blockEntity.ifPresent(te -> {
                        te.setStructureName(RoomerinoComponentino.idToStructure(structureId));
                        te.setIgnoreEntities(false);
                        // snapshot argument order is x z y; the structure block size takes x y z
                        te.setStructureSize(new Vec3i(xSize, ySize, zSize));
                });

                String saveCommand = "/room save " + structureId;
                source.sendSuccess(() -> Component.literal("When done, save structure in structure block below and type ")
                        .append(Component.literal(saveCommand).withStyle(ChatFormatting.GRAY))
                        .append(" or click ")
                        .append(Component.literal("here").withStyle(style -> style
                                .withColor(ChatFormatting.GRAY)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.SuggestCommand(saveCommand)))), true);
                return xSize * ySize * zSize;
        }

        private static int save(CommandSourceStack source, Identifier structureId) {
                Identifier templateId = RoomerinoComponentino.idToStructure(structureId);
                copyToFinal(source, templateId);
                giveKey(source, structureId);
                return 1;
        }

        private static int giveKey(CommandSourceStack source, Identifier structureId) {
                ServerPlayer player = source.getPlayer();
                if (player != null) {
                        ItemStack key = new ItemStack(CraftmineContent.SHIMMERING_KEY);
                        key.set(CraftmineDataComponents.ROOM, new RoomerinoComponentino(structureId));
                        player.getInventory().add(key);
                }
                source.sendSuccess(() -> Component.literal("\ud83d\udd11 ")
                        .append(Component.translatable(structureId.toLanguageKey("room"))), false);
                return 1;
        }

        /**
         * Copies a finished template from the world's {@code generated/} area into
         * the level datapack ({@code datapack_structures/}) so it ships with saves.
         * Port of craftmine's StructureTemplateManager.copyToFinal.
         */
        private static void copyToFinal(CommandSourceStack source, Identifier templateId) {
                ServerLevel level = source.getLevel();
                var storage = ((MinecraftServerAccessor) level.getServer()).craftmine$storageSource();

                Path generated = storage.getLevelPath(new LevelResource("generated"))
                        .normalize()
                        .resolve(templateId.getNamespace())
                        .resolve("structures")
                        .resolve(templateId.getPath() + ".nbt");
                if (!Files.isRegularFile(generated)) {
                        source.sendFailure(Component.literal("No such file: " + generated.toAbsolutePath()));
                        return;
                }

                Path target = storage.getLevelPath(new LevelResource("datapack_structures"))
                        .normalize()
                        .resolve(templateId.getNamespace())
                        .resolve(templateId.getPath() + ".nbt");
                try {
                        FileUtil.createDirectoriesSafe(target.getParent());
                        Files.copy(generated, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                        source.sendFailure(Component.literal("Failed to copy " + generated.toAbsolutePath() + " to " + target.toAbsolutePath()));
                        return;
                }
                source.sendSuccess(() -> Component.literal("Copied " + generated.toAbsolutePath() + " to " + target.toAbsolutePath()), false);
                source.sendSuccess(() -> Component.literal("Don't forget to commit!").withStyle(ChatFormatting.YELLOW), false);
        }
}
