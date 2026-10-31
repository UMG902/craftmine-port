package dev.craftmine.mines;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.craftmine.unlock.UnlockCondition;
import net.minecraft.world.level.block.Blocks;
import dev.craftmine.CraftmineConfig;
import dev.craftmine.duck.CraftmineLevelDuck;

/**
 * The catalogue of special mines. Each entry is a {@link SpecialMine} recipe built from
 * world effects; entries without an unlock condition are always rolled
 * ({@link #DEFAULT_UNLOCKED}), the rest unlock through conditions (kills, items,
 * advancements, block interactions...).
 *
 * <p>The two "unoriginal" boss mines (elder guardian, breeze) gate themselves behind
 * the {@code unoriginal_features} config at unlock time, with the roll path re-checking
 * the config as defence in depth.</p>
 */
public interface SpecialMines {

    /** Special mines that can be rolled without any unlock. */
    List<SpecialMine> DEFAULT_UNLOCKED = new ArrayList<>();

    SpecialMine ENDERMAN_BOSS = SpecialMine.builder("enderman_boss")
        .withRequiredEffects(WorldEffects.ETERNAL_NIGHT, WorldEffects.SURFACE_WORLD, WorldEffects.DESERT,
            WorldEffects.THE_ENDERMAN_BOSS_FIGHT, WorldEffects.EVENT_EXIT)
        .register();

    SpecialMine KUIPER_BELT = SpecialMine.builder("kuiper_belt")
        .withRequiredEffects(WorldEffects.KUIPER_WORLD)
        .register();

    SpecialMine SMALL_BUT_DEADLY_BOSS = SpecialMine.builder("small_but_deadly_boss")
        .withRequiredEffects(WorldEffects.SMALL_BUT_DEADLY, WorldEffects.EVENT_EXIT)
        .withOneOf(WorldEffects.BIOMES)
        .register();

    SpecialMine SPOOKY_SCARY_SKELETONS_BOSS = SpecialMine.builder("spooky_scary_skeletons_boss")
        .withRequiredEffects(WorldEffects.CAVE_WORLD, WorldEffects.DESERT, WorldEffects.SPOOKY_SCARY_SKELETONS,
            WorldEffects.EVENT_EXIT)
        .addUnlockedBy(UnlockCondition.playerKilledEntity(EntityTypes.SKELETON))
        .register();

    SpecialMine ANGRY_GHAST_BOSS = SpecialMine.builder("angry_ghast_boss")
        .withRequiredEffects(WorldEffects.FLOATING_ISLANDS_WORLD, WorldEffects.ANGRY_GHAST_BOSS_FIGHT,
            WorldEffects.EVENT_EXIT)
        .withOneOf(WorldEffects.BIOMES)
        .addUnlockedBy(UnlockCondition.playerAdvancement((level, advancement) ->
            advancement.id().equals(Identifier.withDefaultNamespace("feats/return_to_sender"))))
        .register();

    SpecialMine RAID = SpecialMine.builder("raid")
        .withRequiredEffects(WorldEffects.SURFACE_WORLD, WorldEffects.RAID, WorldEffects.PLAINS,
            WorldEffects.EVENT_EXIT)
        .addUnlockedBy(UnlockCondition.playerKilledEntity(EntityTypes.PILLAGER))
        .register();

    SpecialMine WITHER_BOSS = SpecialMine.builder("wither_boss")
        .withRequiredEffects(WorldEffects.CAVE_WORLD, WorldEffects.WITHER_BOSS_FIGHT, WorldEffects.EVENT_EXIT)
        .withOneOf(WorldEffects.BIOMES)
        .addUnlockedBy(UnlockCondition.obtainedItem((level, player, stack) -> stack.is(Items.WITHER_SKELETON_SKULL)))
        .register();

    SpecialMine ENDER_DRAGON_BOSS = SpecialMine.builder("ender_dragon_boss")
        .withRequiredEffects(WorldEffects.FLOATING_ISLANDS_WORLD, WorldEffects.ENDER_DRAGON_BOSS_FIGHT)
        .withOneOf(WorldEffects.BIOMES)
        .addUnlockedBy(UnlockCondition.mineCompletedWith(true, WorldEffects.END))
        .register();

    SpecialMine WARDEN_BOSS = SpecialMine.builder("warden_boss")
        .withRequiredEffects(WorldEffects.WARDEN_BOSS_FIGHT, WorldEffects.EVENT_EXIT)
        .withOneOf(WorldEffects.BIOMES)
        .addUnlockedBy(UnlockCondition.blockBreak((level, state) -> state.is(Blocks.AMETHYST_BLOCK)
            || state.is(Blocks.AMETHYST_CLUSTER)
            || state.is(Blocks.BUDDING_AMETHYST)
            || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD)
            || state.is(Blocks.LARGE_AMETHYST_BUD)))
        .register();

    // Unoriginal feature (config unoriginal_features + unoriginal_guardian_boss sub-toggle):
    // unlockable by beating a water world (mineCompletedWith) while both switches are on - a
    // custom UnlockCondition so the config check happens at unlock time;
    // PrimaryLevelDataMixin.getNextSpecialMine also re-checks the config at roll time as
    // defense in depth.
    SpecialMine ELDER_GUARDIAN_BOSS = SpecialMine.builder("elder_guardian_boss")
        .withRequiredEffects(WorldEffects.WATER_WORLD, WorldEffects.FISH_OUT_OF_WATER,
            WorldEffects.ELDER_GUARDIAN_BOSS_FIGHT, WorldEffects.EVENT_EXIT)
        .withOneOf(WorldEffects.BIOMES)
        .addUnlockedBy(new UnlockCondition() {
            @Override
            public boolean mapCompleted(ServerLevel level, ServerPlayer player, java.util.Set<WorldEffect> effects, boolean won) {
                return won && CraftmineConfig.guardianBoss() && effects.contains(WorldEffects.WATER_WORLD);
            }
        })
        .register();

    // Unoriginal feature (config unoriginal_features + unoriginal_breeze_boss sub-toggle): the
    // aerial breeze boss. Unlocked by SMASHING A MOB IN A MINE (vanilla MACE_SMASH damage type -
    // a mace is inherently required) while both switches are on;
    // PrimaryLevelDataMixin.getNextSpecialMine re-checks the config at roll time as defense in
    // depth. Normal plains mine: 3x3 obsidian platform at the spawn, the NoAI breeze boss 50
    // blocks above it, mace smashes push him down.
    SpecialMine BREEZE_BOSS = SpecialMine.builder("breeze_boss")
        .withRequiredEffects(WorldEffects.PLAINS, WorldEffects.BREEZE_BOSS_FIGHT, WorldEffects.EVENT_EXIT)
        .addUnlockedBy(new UnlockCondition() {
            @Override
            public boolean playerSmashedEntity(ServerLevel level, ServerPlayer player, Entity target, float damage) {
                return CraftmineConfig.breezeBoss()
                    && target instanceof Mob
                    && ((CraftmineLevelDuck) (Object) level).isMine();
            }
        })
        .register();

    /** Registry bootstrap marker; all mines registered themselves in the constants above. */
    static SpecialMine bootstrap(Registry<SpecialMine> registry) {
        return WITHER_BOSS;
    }
}
