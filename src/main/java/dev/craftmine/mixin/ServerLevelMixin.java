/*
 * Ports the craftmine additions to ServerLevel (25w14craftmine fork, ~640 lines) as a mixin.
 * All mine gameplay logic lives here: mine state, travelling countdown, teleporting,
 * unlock drops, mine events, rewards.
 */
package dev.craftmine.mixin;

import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import dev.craftmine.duck.CraftmineLevelDataDuck;
import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.registry.CraftmineAttributes;
import dev.craftmine.registry.CraftmineDataComponents;
import dev.craftmine.registry.CraftmineFeatureKeys;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.registry.CraftmineTriggers;
import dev.craftmine.registry.CraftmineRegistryHelpers;
import dev.craftmine.server.CraftmineServerHooks;
import dev.craftmine.server.SkyData;
import dev.craftmine.server.SkyDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import dev.craftmine.network.packet.ClientboundUpdateUnlockedEffectsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.craftmine.item.component.ItemExchangeValue;
import dev.craftmine.item.component.WorldModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.craftmine.block.entity.MineCrafterBlockEntity;
import dev.craftmine.block.entity.MineTravellingBlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import dev.craftmine.level.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import dev.craftmine.worldgen.MineExitPools;
import dev.craftmine.mines.MineData;
import dev.craftmine.mines.MineEvent;
import dev.craftmine.mines.MineSpawnStrategy;
import dev.craftmine.mines.SpecialMine;
import dev.craftmine.mines.UnlockMode;
import dev.craftmine.mines.WorldEffect;
import dev.craftmine.mines.WorldEffects;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.craftmine.server.CraftmineTime;
import dev.craftmine.registry.CraftmineItemTags;
import dev.craftmine.mines.MineEventData;
import dev.craftmine.registry.CraftmineContent;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements CraftmineLevelDuck {
    @Unique
    private static final int craftmine$SPAWN_POSITION_SEARCH_RADIUS = 5;

    @Unique
    public BlockPos craftmine$wardenArenaPos = new BlockPos(0, 0, 0);
    @Unique
    public BlockPos craftmine$dirtyIceBallPos = new BlockPos(0, 0, 0);
    @Unique
    private MineData craftmine$mineData;
    @Unique
    private Optional<SpecialMine> craftmine$specialMine = Optional.empty();
    @Unique
    private Set<WorldEffect> craftmine$activeEffects = Set.of();
    @Unique
    private boolean craftmine$eventsStarted;
    @Unique
    private List<MineEvent> craftmine$events = new ArrayList<MineEvent>();
    @Unique
    private List<MineEvent> craftmine$uncompletedEvents = new ArrayList<MineEvent>();
    @Unique
    private boolean craftmine$mineStateInitialized;

    @Shadow
    public List<ServerPlayer> players;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftmine$initFields(CallbackInfo ci) {
        // Mixin does not merge field initializers on this class-file version.
        if (this.craftmine$wardenArenaPos == null) this.craftmine$wardenArenaPos = new BlockPos(0, 0, 0);
        if (this.craftmine$dirtyIceBallPos == null) this.craftmine$dirtyIceBallPos = new BlockPos(0, 0, 0);
        if (this.craftmine$specialMine == null) this.craftmine$specialMine = Optional.empty();
        if (this.craftmine$activeEffects == null) this.craftmine$activeEffects = Set.of();
        if (this.craftmine$events == null) this.craftmine$events = new ArrayList<MineEvent>();
        if (this.craftmine$uncompletedEvents == null) this.craftmine$uncompletedEvents = new ArrayList<MineEvent>();
    }

    private void craftmine$initMineState() {
        // Vanilla constructs ServerLevel instances before DedicatedServer.initServer()
        // finishes. During that constructor, weather/sky initialization can query
        // WorldEffect state before CraftmineServerHooks has adopted the server.
        // Do not mark the state initialized until the game context exists; the first
        // normal tick will retry this method after the server hook is installed.
        if (CraftmineServerHooks.theGame() == null) {
            return;
        }
        if (this.craftmine$mineStateInitialized) {
            return;
        }
        this.craftmine$mineStateInitialized = true;
        ServerLevel self = (ServerLevel) (Object) this;
        dev.craftmine.registry.CraftmineLevelStemData.Entry stemData = dev.craftmine.registry.CraftmineLevelStemData.get(CraftmineRegistryHelpers.levelToLevelStem(self.dimension()));
        this.craftmine$activeEffects = new ObjectArraySet<WorldEffect>(stemData.effects());
        this.craftmine$specialMine = stemData.mine();
        this.craftmine$mineData = self.getDataStorage().computeIfAbsent(MineData.TYPE);
        this.craftmine$mineData.setMine(!this.craftmine$activeEffects.isEmpty());
        var worldData = CraftmineServerHooks.theGame().getWorldData();
        MineEventData holder = craftmine$savedEventData();
        if (holder != null) {
            this.craftmine$eventsStarted = holder.started();
            this.craftmine$events = new ArrayList<MineEvent>(holder.events());
            this.craftmine$uncompletedEvents.addAll(holder.events());
            this.craftmine$uncompletedEvents.removeIf(mineEvent -> mineEvent.getStatus() == MineEvent.Status.WON);
        }
    }

    @Unique
    private MineEventData craftmine$savedEventData() {
        var events = ((CraftmineLevelDataDuck) (Object) CraftmineServerHooks.theGame().getWorldData()).events();
        ServerLevel self = (ServerLevel) (Object) this;
        return events.get(self.dimension());
    }

    @Override
    public void tickEvents() {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        // PARITY NOTE (corrected after user decompile check, 2026-10-31): the snapshot's
        // ast.aK() IS player-gated - "if (!this.U.isEmpty())" wraps the whole event tick,
        // so empty mine levels do NOT tick events there. We deliberately deviate: the port
        // allows leaving a mine mid-battle (travelling block, death, hub return), and with
        // the fork-faithful gate an abandoned battle froze with the leaver still in its
        // bar's audience ("boss bars still in the hub", one frozen bar per visited battle
        // mine). Ticking unconditionally lets setPlayers(empty) clear the audience the
        // tick after leaving and lets abandoned battles wind down (mobs unload -> waves
        // empty out -> WON -> removeBossBar), purging the GLOBAL CustomBossEvents
        // SavedData. The user-visible bar lifecycle itself is snapshot-faithful either
        // way: bars are cleared AS SOON AS THEY ARE DONE by Battle.tick (countdown bar
        // removed the tick the wave spawns; remaining bar removed the tick the last mob
        // dies), never in a leave/win flow - the snapshot has no bar cleanup in aM/aL
        // either. (The AD/AE stacked-bars symptom was actually 26.3's non-broadcasting
        // CustomBossEvents.remove - fixed with removeAllPlayers() in Battle.removeBossBar.)
        this.craftmine$events.forEach(mineEvent -> mineEvent.tick(self));
        if (!this.players.isEmpty()) {
            CraftmineLevelDataDuck worldData = (CraftmineLevelDataDuck) (Object) CraftmineServerHooks.theGame().getWorldData();
            if (worldData.events().get(self.dimension()) == null) {
                this.onMineEntered();
                this.craftmine$eventsStarted = true;
                worldData.events().put(self.dimension(), new MineEventData(true, this.craftmine$events));
            }
            if (this.craftmine$events.stream().anyMatch(mineEvent -> mineEvent.getStatus() == MineEvent.Status.FAILED)) {
                this.craftmine$handleMineLoss();
            } else if (this.isActive(WorldEffects.EVENT_EXIT) && !this.craftmine$uncompletedEvents.isEmpty() && this.craftmine$uncompletedEvents.stream().allMatch(mineEvent -> mineEvent.getStatus() == MineEvent.Status.WON)) {
                JigsawPlacement.generateJigsaw(self, self.registryAccess().getOrThrow(MineExitPools.STARTS), Identifier.withDefaultNamespace("start"), 7, this.craftmine$uncompletedEvents.getFirst().getPosition(), false);
            }
            this.craftmine$uncompletedEvents.removeIf(mineEvent -> mineEvent.getStatus() == MineEvent.Status.WON);
        }
    }

    @Override
    public void startEvent(MineEvent mineEvent) {
        this.craftmine$initMineState();
        this.craftmine$events.add(mineEvent);
        this.craftmine$uncompletedEvents.add(mineEvent);
    }

    @Override
    public List<MineEvent> events() {
        this.craftmine$initMineState();
        return this.craftmine$events;
    }

    @Unique
    private void craftmine$handleMineLoss() {
        ServerLevel self = (ServerLevel) (Object) this;
        this.players.forEach(CraftmineTriggers.LEVEL_FAILED::trigger);
        if (!this.isMineCompleted()) {
            this.markMineCompleted(false);
        }
        this.craftmine$respawnPlayersIntoHub(serverPlayer -> {});
    }

    @Unique
    private void craftmine$handleMineCountDown(Consumer<BlockPos> consumer) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (this.players.isEmpty()) {
            return;
        }
        Optional<BlockPos> optional = this.craftmine$mineData.getTravellingBlockActivated();
        if (optional.isPresent()) {
            if (this.craftmine$mineData.countDown()) {
                consumer.accept(optional.get());
                this.craftmine$mineData.resetCountdown();
            } else if (this.craftmine$mineData.getLeaveCountdown() % 20 == 0) {
                String string = this.isMine() ? "leave" : "enter";
                CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine." + string, this.craftmine$mineData.getLeaveCountdown() / 20), true);
                self.playSound(null, optional.get(), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, (float)(0.15 + 0.04 * 0.75), (float)(0.3 + 0.08 * 0.75));
            }
        } else if (this.craftmine$mineData.getLeaveCountdown() != 200) {
            this.craftmine$mineData.resetCountdown();
        }
    }

    @Unique
    private void craftmine$respawnPlayersIntoHub(Consumer<ServerPlayer> consumer) {
        ServerLevel self = (ServerLevel) (Object) this;
        for (ServerPlayer serverPlayer : new LinkedList<ServerPlayer>(self.players())) {
            if (((CraftminePlayerDuck) (Object) serverPlayer).isRevisiting()) continue;
            TeleportTransition teleportTransition = this.craftmine$getHubTeleport();
            if (serverPlayer.isDeadOrDying() || serverPlayer.isSpectator()) {
                serverPlayer.setExperienceLevels(0);
                serverPlayer.setExperiencePoints(0);
            }
            if (this.isMineWon()) {
                CraftmineTriggers.LEVEL_COMPLETED.trigger(serverPlayer);
                if (this.isSpecialMine()) {
                    CraftmineTriggers.SPECIAL_MINE_COMPLETED.trigger(serverPlayer);
                }
            }
            if (!serverPlayer.isSpectator() && !this.isMineWon()) continue;
            this.respawnPlayerIntoHub(serverPlayer, consumer);
        }
    }

    public void respawnPlayerIntoHub(ServerPlayer serverPlayer, Consumer<ServerPlayer> consumer) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (serverPlayer.isDeadOrDying() || serverPlayer.isSpectator()) {
            if (CraftmineServerHooks.theGame().isHardcore()) {
                serverPlayer.setGameMode(GameType.SPECTATOR);
            } else {
                serverPlayer.setGameMode(GameType.SURVIVAL);
            }
        }
        ServerPlayer serverPlayer2 = serverPlayer.teleport(this.craftmine$getHubTeleport());
        if (serverPlayer2 == null) {
            return;
        }
        CraftminePlayerDuck playerDuck = (CraftminePlayerDuck) (Object) serverPlayer;
        if (!playerDuck.isRevisiting()) {
            MutableComponent mutableComponent = this.isMineWon() ? Component.translatable("mine.won") : Component.translatable("mine.lost");
            mutableComponent.setStyle(Style.EMPTY.withBold(true));
            if (this.isMineWon()) {
                mutableComponent.withColor(-11010079);
            } else {
                mutableComponent.withColor(-65536);
            }
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(mutableComponent));
        } else if (!((CraftmineLevelDuck) (Object) CraftmineServerHooks.theGame().overworld()).mineData().hasPlayerDied(serverPlayer.getUUID())) {
            serverPlayer.setGameMode(GameType.SURVIVAL);
        }
        serverPlayer2.getFoodData().setFoodLevel(20);
        serverPlayer2.setHealth(serverPlayer2.getMaxHealth());
        playerDuck.setRevisiting(false);
        serverPlayer2.connection.resetPosition();
        consumer.accept(serverPlayer2);
        ((CraftminePlayerDuck) (Object) serverPlayer2).swapInventoryToHub();
    }

    @Unique
    private TeleportTransition craftmine$getHubTeleport() {
        ServerLevel serverLevel = CraftmineServerHooks.theGame().overworld();
        return new TeleportTransition(serverLevel, net.minecraft.world.phys.Vec3.atBottomCenterOf(serverLevel.getRespawnData().pos()), Vec3.ZERO, serverLevel.getRespawnData().yaw(), 0.0f, TeleportTransition.DO_NOTHING);
    }

    public void handleMineWin(BlockPos blockPos) {
        this.markMineCompleted(true);
        this.craftmine$respawnPlayersIntoHub(serverPlayer -> this.craftmine$cleanInventoryAndReward(serverPlayer, 0.0f));
    }

    @Unique
    private void craftmine$cleanInventoryAndReward(ServerPlayer serverPlayer, float f) {
        ServerLevel self = (ServerLevel) (Object) this;
        double d = 10.0;
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        for (ItemStack itemStack : serverPlayer.getInventory()) {
            WorldModifiers record = itemStack.get(CraftmineDataComponents.WORLD_MODIFIERS);
            if (record != null) {
                for (WorldEffect worldEffect : record.effects()) {
                    if (!itemStack.has(CraftmineDataComponents.WORLD_EFFECT_UNLOCK)) continue;
                    this.unlockEffect(worldEffect);
                }
            } else if (itemStack.is(CraftmineItemTags.CARRY_OVER)) {
                arrayList.add(itemStack.copy());
            } else {
                float f2 = dev.craftmine.exchange.CraftmineExchangeValues.forStack(itemStack).getValue(serverPlayer, itemStack);
                d += (double)((float)itemStack.getCount() * f2);
            }
            CraftmineTriggers.INVENTORY_CASHED_IN.trigger(serverPlayer, serverPlayer.blockPosition(), itemStack);
        }
        serverPlayer.getInventory().clearContent();
        float f3 = 1.0f;
        for (WorldEffect worldEffect : this.craftmine$activeEffects) {
            f3 += worldEffect.experienceModifier();
        }
        d *= (double)f3;
        int n = (int)((d *= serverPlayer.getAttributeValue(dev.craftmine.registry.CraftmineAttributes.EXPERIENCE_GAIN_MODIFIER)) * (double)f);
        int n2 = (int)(d * (double)(1.0f - f));
        serverPlayer.giveExperiencePoints(n);
        for (ItemStack itemStack : arrayList) {
            ((CraftminePlayerDuck) (Object) serverPlayer).addHubReward(itemStack);
        }
        ((CraftmineLevelDuck) (Object) serverPlayer.level()).mineData().addExperienceToDrop(n2);
        CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine.won.rewards", serverPlayer.getName(), n2), false);
    }

    @Unique
    private void craftmine$handleCompletedMine() {
        if (!this.isMineWon()) {
            this.craftmine$respawnPlayersIntoHub(serverPlayer -> serverPlayer.getInventory().clearContent());
            return;
        }
        this.craftmine$respawnPlayersIntoHub(serverPlayer -> this.craftmine$cleanInventoryAndReward(serverPlayer, 1.0f));
    }

    @Override
    public void dropRewards(BlockPos blockPos) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        int n2 = this.craftmine$mineData.getKeysToRoll();
        int experience = this.craftmine$mineData.getExperienceToDrop();
        if (experience <= 0 && n2 <= 0) {
            return;
        }
        this.addExperienceToMineCrafter(60);
        int n3 = Mth.ceil((float)experience / 20.0f);
        for (int n = experience; n > 0; n -= n3) {
            ExperienceOrb.awardWithDirection(self, net.minecraft.world.phys.Vec3.atCenterOf(blockPos).add(new Vec3(0.0, 2.00001f, 0.0)), new Vec3(0.0, 1.0, 0.0), n3);
        }
        for (int i = 0; i < n2 && !this.craftmine$getRewardKeys(blockPos).isEmpty(); ++i) {
            for (ItemStack itemStack : this.craftmine$getRewardKeys(blockPos)) {
                ItemEntity itemEntity = new ItemEntity(self, blockPos.getX(), (double)blockPos.getY() + 2.5 + (double)1.0E-5f, blockPos.getZ(), itemStack);
                Vec3 vec3 = new Vec3((self.random.nextDouble() * (double)0.2f - (double)0.1f) * 2.0, self.random.nextDouble() * 0.4, (self.random.nextDouble() * (double)0.2f - (double)0.1f) * 2.0);
                itemEntity.push(vec3);
                self.addFreshEntity(itemEntity);
            }
        }
        this.craftmine$mineData.resetKeysToRoll();
        this.craftmine$mineData.resetExperienceToDrop();
    }

    @Unique
    private List<ItemStack> craftmine$getRewardKeys(BlockPos blockPos) {
        ServerLevel self = (ServerLevel) (Object) this;
        return CraftmineServerHooks.theGame().reloadableRegistries().getLootTable(dev.craftmine.registry.CraftmineLootTables.ROOM_REWARD).getRandomItems(new LootParams.Builder(self).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos)).create(dev.craftmine.registry.CraftmineLootContextParamSets.HUB_REWARD));
    }

    @Override
    public void leaveForMine(BlockPos blockPos, boolean bl, Optional<UUID> optional) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        if (!bl) {
            this.craftmine$mineData.resetMineTravellingBlock();
        }
        BlockEntity blockEntity = self.getBlockEntity(blockPos);
        if (!(blockEntity instanceof MineTravellingBlockEntity mineTravellingBlockEntity)) {
            return;
        }
        ResourceKey<Level> resourceKey = mineTravellingBlockEntity.getTargetDimension();
        ServerLevel serverLevel = CraftmineServerHooks.theGame().getLevel(resourceKey);
        for (ServerPlayer serverPlayer : self.players()) {
            if (optional.isPresent() && !optional.get().equals(serverPlayer.getUUID()) || serverPlayer.isSpectator()) continue;
            ((CraftminePlayerDuck) (Object) serverPlayer).swapInventoryFromHub();
        }
        if (serverLevel == null) {
            // Live materialization of the generated dimension: decode the saved,
            // self-contained datapack JSON and construct the ServerLevel in place
            // (port equivalent of the original's WorldReloader config-phase reload).
            // Task 58: first entry of THIS level in the current session - bring
            // the freshly written datapack into the pack repository + resource
            // reload first, so the command dispatcher sees the mine's biomes
            // (same gap as the /level command path).
            dev.craftmine.server.CraftmineLiveReload.afterDimensionWritten(CraftmineServerHooks.theGame(), resourceKey.identifier());
            serverLevel = dev.craftmine.server.CraftmineLevelCreation.ensureGeneratedLevel(CraftmineServerHooks.theGame(), resourceKey);
            if (serverLevel == null) {
                return;
            }
        }
        // Vanilla calls teleportAllPlayersToMine on the RESOLVED MINE level, not on
        // this (the hub): the mine spawn strategy must be looked up in the mine's
        // own level stem and the cross-dimension transition must target the mine.
        //
        // Task 30 (seamless entry): teleport FIRST, then resync. Clients still
        // holding pre-materialization registry data cannot decode the mine's chunk
        // palettes, so they must go through the configuration-phase resync - but
        // the teleport itself only needs to happen BEFORE the resync's fake
        // disconnect: removePlayerFromWorld saves the player (now at the mine
        // spawn) and the config-phase rejoin loads that saved data, so every
        // client comes back already INSIDE the mine. One loading screen, no
        // intermediate "teleported back to my spot", and the old post-rejoin
        // settle delay is gone (entry takes ~1-2s like the original again).
        // The teleport packets sent to stale clients are superseded by the
        // configuration switch they precede.
        ((dev.craftmine.duck.CraftmineLevelDuck) (Object) serverLevel).teleportAllPlayersToMine(bl, optional);
        dev.craftmine.server.CraftmineClientResync.runWhenSynced(CraftmineServerHooks.theGame().server(),
            () -> {});
    }

    @Override
    public void teleportAllPlayersToMine(boolean bl, Optional<UUID> optional) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        MineSpawnStrategy mineSpawnStrategy = dev.craftmine.registry.CraftmineLevelStemData.get(CraftmineRegistryHelpers.levelToLevelStem(self.dimension())).spawn();
        Vec3 vec3 = mineSpawnStrategy.getSpawnPosition(self);
        BlockPos.MutableBlockPos mutableBlockPos = BlockPos.containing(vec3).mutable().move(net.minecraft.core.Direction.DOWN);
        if (mutableBlockPos.getY() < self.getMinY()) {
            mutableBlockPos.setY(self.getMinY());
        }
        boolean bl3 = !bl && !this.craftmine$mineData.hasPlacedStartStructures() && this.craftmine$mineData.isMine();
        boolean bl2 = mineSpawnStrategy == MineSpawnStrategy.SURFACE;
        if (bl3) {
            this.craftmine$mineData.setHasPlacedStartStructures(true);
            if (this.isActive(WorldEffects.WARDEN_BOSS_FIGHT)) {
                this.craftmine$wardenArenaPos = new BlockPos(mutableBlockPos.getX() + 40, mutableBlockPos.getY(), mutableBlockPos.getZ());
                this.craftmine$placeFeature(CraftmineFeatureKeys.WARDEN_ARENA, this.craftmine$wardenArenaPos);
                bl2 = false;
            }
            if (this.isActive(WorldEffects.KUIPER_WORLD)) {
                int n = mutableBlockPos.getX() + 15;
                int n2 = mutableBlockPos.getY();
                int n3 = mutableBlockPos.getZ() - 100;
                int n4 = mutableBlockPos.getX() + 225;
                int n5 = mutableBlockPos.getY() + 200;
                int n6 = mutableBlockPos.getZ() + 100;
                for (int i = 0; i < 150; ++i) {
                    this.craftmine$dirtyIceBallPos = new BlockPos(self.random.nextInt(n, n4), self.random.nextInt(n2, n5), self.random.nextInt(n3, n6));
                    this.craftmine$placeFeature(CraftmineFeatureKeys.DIRTY_ICE_BALL, this.craftmine$dirtyIceBallPos);
                    this.craftmine$dirtyIceBallPos = new BlockPos(self.random.nextInt(n, n4), self.random.nextInt(n2, n5), self.random.nextInt(n3, n6));
                    this.craftmine$placeFeature(CraftmineFeatureKeys.DIRTY_ICE_BALL_GOLEMS, this.craftmine$dirtyIceBallPos);
                    this.craftmine$dirtyIceBallPos = new BlockPos(self.random.nextInt(n, n4), self.random.nextInt(n2, n5), self.random.nextInt(n3, n6));
                    this.craftmine$placeFeature(CraftmineFeatureKeys.DIRTY_ICE_BALL_FOX, this.craftmine$dirtyIceBallPos);
                }
                this.craftmine$dirtyIceBallPos = mutableBlockPos.immutable();
                this.craftmine$placeFeature(CraftmineFeatureKeys.SPACE_IGLOO, this.craftmine$dirtyIceBallPos);
                JigsawPlacement.generateJigsaw(self, self.registryAccess().getOrThrow(MineExitPools.STARTS), Identifier.withDefaultNamespace("start"), 7, new BlockPos(mutableBlockPos.getX() + 200, mutableBlockPos.getY() + 180, mutableBlockPos.getZ()), false);
                mutableBlockPos.move(4, 10, 0);
                bl2 = false;
                bl3 = false;
            }
            if (this.isActive(WorldEffects.RAID)) {
                this.craftmine$generateRaidVillage(mutableBlockPos.immutable());
            }
        }
        while (bl2 && self.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos) <= self.getMinY()) {
            mutableBlockPos.move(net.minecraft.core.Direction.NORTH);
            // Fork-faithful (ast's teleportAllPlayersToMine): the original calls
            // getBlockState on EVERY step of this walk. It is NOT dead code:
            // on a live-created (unprepared) level getHeight reads empty chunks
            // without generating, so without this call the walk crosses the
            // whole level and stops at the ChunkPos range edge (z=-30000001),
            // placing the mine_start platform outside the world border - the
            // player logged in there and instantly died to border damage
            // ("left the confines of this world"). getBlockState force-loads
            // the chunk, so the walk ends at the first real terrain column.
            self.getBlockState(mutableBlockPos);
        }
        if (bl2) {
            mutableBlockPos.setY(self.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos));
        }
        net.minecraft.world.level.block.state.BlockState blockState = self.getBlockState(mutableBlockPos);
        while (blockState.isCollisionShapeFullBlock(self, mutableBlockPos)) {
            mutableBlockPos.move(net.minecraft.core.Direction.UP);
            blockState = self.getBlockState(mutableBlockPos);
        }
        if (bl3) {
            this.craftmine$placeFeature(CraftmineFeatureKeys.MINE_START, mutableBlockPos.below());
        }
        double d;
        net.minecraft.world.phys.shapes.VoxelShape voxelShape = blockState.getCollisionShape(self, mutableBlockPos);
        double d2 = d = voxelShape.isEmpty() ? 0.0 : voxelShape.max(net.minecraft.core.Direction.Axis.Y);
        if (!Double.isFinite(d)) {
            d = 0.0;
        }
        vec3 = new Vec3((double)mutableBlockPos.getX() + 0.5, (double)mutableBlockPos.getY() + d, (double)mutableBlockPos.getZ() + 0.5);
        // Live-created levels have no prepared spawn area: force-generate the spawn
        // chunks synchronously so players don't fall through ungenerated terrain
        // (the original did this implicitly via the config-phase world reload).
        net.minecraft.world.level.ChunkPos spawnChunkPos = new net.minecraft.world.level.ChunkPos(mutableBlockPos.getX() >> 4, mutableBlockPos.getZ() >> 4);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                self.getChunk(spawnChunkPos.x() + dx, spawnChunkPos.z() + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
        }
        for (ServerPlayer serverPlayer : CraftmineServerHooks.theGame().playerList().getPlayers()) {
            if (optional.isPresent() && !optional.get().equals(serverPlayer.getUUID()) || serverPlayer.isSpectator()) continue;
            serverPlayer.setGameMode(GameType.SURVIVAL);
            // Fork-faithful: Relative.DELTA = {DELTA_X, DELTA_Y, DELTA_Z, ROTATE_DELTA} —
            // position/rotation travel ABSOLUTE (the spawn vec is a real destination),
            // velocity is delta-encoded and re-rotated into the new frame. The previous
            // Set.of(Relative.values()) made X/Y/Z relative too, so the client applied the
            // spawn vec as a DELTA from its current position and landed far off the
            // mine_start platform ("moved wrongly!" + standing on air).
            TeleportTransition teleportTransition = new TeleportTransition(self, vec3, Vec3.ZERO, 0.0f, 0.0f, net.minecraft.world.entity.Relative.DELTA, TeleportTransition.PLACE_PORTAL_TICKET);
            ServerPlayer serverPlayer2 = serverPlayer.teleport(teleportTransition);
            if (serverPlayer2 == null) {
                return;
            }
            serverPlayer2.connection.resetPosition();
            // Task 31: the fork's ServerPlayer.onMineEntered (reset run stats,
            // food, health + fire every owned unlock's onMineEnter - starter
            // items, effects, pets). The port had the duck method but nothing
            // called it, so "gives X in mine" unlocks never delivered.
            ((CraftminePlayerDuck) (Object) serverPlayer2).onMineEntered();
            if (bl) {
                serverPlayer2.setGameMode(GameType.ADVENTURE);
                if (this.isMineWon()) {
                    serverPlayer2.sendSystemMessage(Component.translatable("world.mine.revisit.won"), true);
                } else {
                    serverPlayer2.sendSystemMessage(Component.translatable("world.mine.revisit.lost"), true);
                }
                ((CraftminePlayerDuck) (Object) serverPlayer2).setRevisiting(true);
                ((CraftminePlayerDuck) (Object) serverPlayer2).addOrDropItem(CraftmineContent.EXIT_EYE.getDefaultInstance().copyWithCount(1));
                continue;
            }
            this.onMineEntered();
        }
    }

    @Unique
    private void craftmine$placeFeature(ResourceKey<Feature> featureKey, BlockPos blockPos) {
        ServerLevel self = (ServerLevel) (Object) this;
        Holder<Feature> holder = self.registryAccess().getOrThrow(featureKey);
        holder.value().place(self, self.getChunkSource().getGenerator(), self.random, blockPos);
    }

    @Unique
    private void craftmine$generateRaidVillage(BlockPos blockPos) {
        ServerLevel self = (ServerLevel) (Object) this;
        Holder.Reference<Structure> reference = self.registryAccess().getOrThrow(BuiltinStructures.VILLAGE_PLAINS);
        ChunkGenerator chunkGenerator = self.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.RandomState randomState = self.getChunkSource().randomState();
        net.minecraft.world.level.biome.Climate.Sampler climateSampler = randomState.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
        StructureStart structureStart = reference.value().generate(reference, self.dimension(), self.registryAccess(), chunkGenerator, chunkGenerator.getBiomeSource(), climateSampler, randomState, self.getStructureTemplateManager(), self.getSeed(), ChunkPos.containing(blockPos), 0, self, holder -> true);
        if (structureStart == null || structureStart == StructureStart.INVALID_START) {
            return;
        }
        BoundingBox boundingBox = structureStart.getBoundingBox();
        ChunkPos chunkPos2 = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.minX()), SectionPos.blockToSectionCoord(boundingBox.minZ()));
        ChunkPos chunkPos3 = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.maxX()), SectionPos.blockToSectionCoord(boundingBox.maxZ()));
        ChunkPos.rangeClosed(chunkPos2, chunkPos3).forEach(chunkPos -> {
            self.setChunkForced(chunkPos.x(), chunkPos.z(), true);
            structureStart.placeInChunk(self, self.structureManager(), chunkGenerator, self.getRandom(), new BoundingBox(chunkPos.getMinBlockX(), self.getMinY(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), self.getMaxY() + 1, chunkPos.getMaxBlockZ()), chunkPos);
        });
    }

    @Override
    public void onMineEntered() {
        ServerLevel self = (ServerLevel) (Object) this;
        this.craftmine$initMineState();
        CraftmineTime.setDayTime(CraftmineServerHooks.theGame().overworld(), 1000L);
        for (WorldEffect worldEffect : this.craftmine$activeEffects) {
            worldEffect.onMineEnter().accept(self);
        }
    }

    @Override
    public void onMineLeave() {
        ServerLevel self = (ServerLevel) (Object) this;
        for (WorldEffect worldEffect : this.craftmine$activeEffects) {
            worldEffect.onMineLeave().accept(self);
        }
        CraftmineTime.setDayTime(CraftmineServerHooks.theGame().overworld(), 1000L);
    }

    @Override
    public boolean isMine() {
        this.craftmine$initMineState();
        return this.craftmine$mineData.isMine();
    }

    @Override
    public boolean isSpecialMine() {
        this.craftmine$initMineState();
        return this.craftmine$specialMine.isPresent();
    }

    @Override
    public Optional<SpecialMine> specialMine() {
        this.craftmine$initMineState();
        return this.craftmine$specialMine;
    }

    @Override
    public boolean isMineCompleted() {
        this.craftmine$initMineState();
        return this.craftmine$mineData.getMineState() != MineData.MineState.ONGOING;
    }

    @Override
    public boolean isMineWon() {
        this.craftmine$initMineState();
        return this.craftmine$mineData.getMineState() == MineData.MineState.WON;
    }

    @Override
    public void markMineCompleted(boolean bl) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).mineCompleted(this.craftmine$specialMine, bl);
        this.craftmine$mineData.setMineState(bl ? MineData.MineState.WON : MineData.MineState.FAILED);
        for (ServerPlayer object : CraftmineServerHooks.theGame().playerList().getPlayers()) {
            dev.craftmine.unlock.UnlockCondition.onMapCompleted(self, object, this.craftmine$activeEffects, bl);
            this.craftmine$specialMine.ifPresent(specialMine -> dev.craftmine.unlock.UnlockCondition.onSpecialMineCompleted(self, object, specialMine, bl));
        }
        this.craftmine$events.forEach(mineEvent -> mineEvent.end(self, bl));
        if (bl) {
            for (WorldEffect worldEffect : this.craftmine$activeEffects) {
                if (worldEffect.unlockMode() != UnlockMode.UNLOCKED_ON_WIN) continue;
                this.unlockEffect(worldEffect);
            }
            if (this.isSpecialMine()) {
                ((CraftmineLevelDuck) (Object) CraftmineServerHooks.theGame().overworld()).mineData().addKeysToRoll(1);
            } else if (self.random.nextInt(10) == 0) {
                ((CraftmineLevelDuck) (Object) CraftmineServerHooks.theGame().overworld()).mineData().addKeysToRoll(1);
            }
        }
        this.onMineLeave();
    }

    @Override
    public void toggledMineTravellingBlock(BlockPos blockPos) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        boolean bl = this.craftmine$mineData.toggledMineTravellingBlock(blockPos.immutable());
        String string = this.isMine() ? "leave" : "enter";
        if (bl) {
            CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine." + string + ".started"), true);
        } else {
            CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine." + string + ".aborted"), true);
        }
    }

    @Override
    public void unlockEffect(WorldEffect worldEffect) {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        if (((CraftmineLevelDataDuck) (Object) craftmine$levelData()).isEffectUnlocked(worldEffect) || worldEffect.unlockMode() == UnlockMode.NEVER_UNLOCKED) {
            return;
        }
        CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("world.effect.unlocked", worldEffect.name()), true);
        CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("world.effect.unlocked", worldEffect.name()), false);
        ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).unlockEffect(worldEffect);
        for (ServerPlayer serverPlayer : CraftmineServerHooks.theGame().playerList().getPlayers()) {
            dev.craftmine.unlock.UnlockCondition.onUnlockedMapEffect(self, serverPlayer, worldEffect);
            // Per-PLAYER sync: each player gets THEIR current level's unlocked +
            // active lists. The packet drives the client state of the level the
            // player is IN - a hub player must not receive this mine's active
            // effects (it would paint mine icons into their hub-level HUD state).
            dev.craftmine.duck.CraftmineLevelDuck playerLevel = (dev.craftmine.duck.CraftmineLevelDuck) (Object) serverPlayer.level();
            serverPlayer.connection.send(new ClientboundUpdateUnlockedEffectsPacket(playerLevel.getUnlockedEffects(), playerLevel.getActiveEffects()));
        }
    }

    @Override
    public boolean isEffectUnlocked(WorldEffect worldEffect) {
        this.craftmine$initMineState();
        return ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).isEffectUnlocked(worldEffect);
    }

    @Override
    public boolean isActive(WorldEffect worldEffect) {
        this.craftmine$initMineState();
        return this.craftmine$activeEffects.contains(worldEffect);
    }

    @Override
    public List<WorldEffect> getActiveEffects() {
        this.craftmine$initMineState();
        return new ArrayList<WorldEffect>(this.craftmine$activeEffects);
    }

    @Override
    public List<WorldEffect> getUnlockedEffects() {
        this.craftmine$initMineState();
        return CraftmineRegistries.WORLD_EFFECT.stream().filter(this::isEffectUnlocked).toList();
    }

    @Override
    public void dropUnlockEffect(Vec3 vec3, WorldEffect worldEffect, ServerPlayer serverPlayer) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (this.isMineCompleted()) {
            this.unlockEffect(worldEffect);
            return;
        }
        if (serverPlayer != null && serverPlayer.getInventory().getNonEquipmentItems().stream().anyMatch(itemStack -> itemStack.is(CraftmineContent.MINE_INGREDIENT) && itemStack.getOrDefault(CraftmineDataComponents.WORLD_MODIFIERS, WorldModifiers.EMPTY).effects().contains(worldEffect))) {
            return;
        }
        self.addFreshEntity(new ItemEntity(self, vec3.x, vec3.y, vec3.z, WorldEffects.createEffectItem(worldEffect, true)));
    }

    @Override
    public int getMineCrafterLevel() {
        this.craftmine$initMineState();
        return ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).getMineCrafterLevel();
    }

    @Override
    public int getMineCrafterExp() {
        this.craftmine$initMineState();
        return ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).getMineCrafterExp();
    }

    @Override
    public void addExperienceToMineCrafter(int n) {
        this.craftmine$initMineState();
        CraftmineLevelDataDuck levelData = (CraftmineLevelDataDuck) (Object) craftmine$levelData();
        int n2 = levelData.getMineCrafterLevel();
        levelData.addExperienceToMineCrafter(n);
        if (n2 != levelData.getMineCrafterLevel()) {
            for (ServerPlayer serverPlayer : this.players) {
                CraftmineTriggers.MINE_CRAFTER_UPGRADED.trigger(serverPlayer);
            }
        }
        dev.craftmine.server.CraftmineServerHooks.syncContainerMenuData(dev.craftmine.registry.CraftmineContent.MAP_MAKING, MineCrafterBlockEntity.getAdditionalData((ServerLevel) (Object) this));
    }

    @Override
    public void unlockSpecialMine(SpecialMine specialMine) {
        this.craftmine$initMineState();
        ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).unlockSpecialMine(specialMine);
    }

    @Override
    public boolean isSpecialMineUnlocked(SpecialMine specialMine) {
        this.craftmine$initMineState();
        return ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).isSpecialMineUnlocked(specialMine);
    }

    @Override
    public Optional<SpecialMine> getNextSpecialMine() {
        this.craftmine$initMineState();
        return ((CraftmineLevelDataDuck) (Object) craftmine$levelData()).getNextSpecialMine(((ServerLevel) (Object) this).random);
    }

    @Override
    public MineData mineData() {
        this.craftmine$initMineState();
        return this.craftmine$mineData;
    }

    @Unique
    private net.minecraft.world.level.storage.ServerLevelData craftmine$levelData() {
        // provided by the shadow mixin ServerLevelDataAccessor
        net.minecraft.world.level.storage.ServerLevelData levelData = ((ServerLevelDataAccessor) (Object) this).craftmine$getServerLevelData();
        // Mine dimensions (every non-first dimension) hold vanilla's DerivedLevelData
        // wrapper; all craftmine world state lives on the wrapped PrimaryLevelData.
        // Unwrap it so unlock purchases (kaizen), effect queries, mine-crafter level
        // and special-mine progression work inside mines too. Fixes the
        // ClassCastException reported on unlock purchase inside a mine.
        if (levelData instanceof net.minecraft.world.level.storage.DerivedLevelData) {
            return ((DerivedLevelDataAccessor) (Object) levelData).craftmine$getWrapped();
        }
        return levelData;
    }

    @Unique
    private void craftmine$handleMineCountdown(Consumer<BlockPos> consumer) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.players().isEmpty()) {
            return;
        }
        Optional<BlockPos> optional = this.craftmine$mineData.getTravellingBlockActivated();
        if (optional.isPresent()) {
            if (this.craftmine$mineData.countDown()) {
                consumer.accept(optional.get());
                this.craftmine$mineData.resetCountdown();
            } else if (this.craftmine$mineData.getLeaveCountdown() % 20 == 0) {
                String string = this.isMine() ? "leave" : "enter";
                float f = 1.0f - (float)this.craftmine$mineData.getLeaveCountdown() / 200.0f;
                float f2 = 0.75f + 6.0f * f * f;
                if (this.isMine()) {
                    CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine." + string, this.craftmine$mineData.getLeaveCountdown() / 20), true);
                } else {
                    CraftmineServerHooks.theGame().playerList().broadcastSystemMessage(Component.translatable("mine." + string, this.craftmine$mineData.getLeaveCountdown() / 20), true);
                }
                self.playSound(null, optional.get(), SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, (float)(0.15 + 0.04 * (double)f2), (float)(0.3 + 0.08 * (double)f2));
            }
        } else if (this.craftmine$mineData.getLeaveCountdown() != 200) {
            this.craftmine$mineData.resetCountdown();
        }
    }

    @Override
    public void handleInProgressMine() {
        this.craftmine$initMineState();
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.players().isEmpty()) {
            return;
        }
        if (!this.isMine()) {
            this.craftmine$handleMineCountdown(blockPos -> this.leaveForMine((BlockPos)blockPos, false, Optional.empty()));
            for (ServerPlayer object : this.players) {
                if (!this.craftmine$mineData.hasPlayerDied(object.getUUID()) || !CraftmineServerHooks.theGame().isHardcore()) continue;
                object.setGameMode(GameType.SPECTATOR);
            }
            if (!this.players.isEmpty()) {
                Optional<ServerLevel> optional = Optional.empty();
                for (ServerPlayer serverPlayer : CraftmineServerHooks.theGame().playerList().getPlayers()) {
                    if (((CraftminePlayerDuck) (Object) serverPlayer).isRevisiting() || serverPlayer.level() == self || ((CraftmineLevelDuck) (Object) serverPlayer.level()).isMineCompleted()) continue;
                    optional = Optional.of(serverPlayer.level());
                    break;
                }
                if (optional.isPresent()) {
                    for (ServerPlayer serverPlayer : new ArrayList<ServerPlayer>(self.players())) {
                        ((CraftmineLevelDuck) (Object) optional.get()).teleportAllPlayersToMine(false, Optional.of(serverPlayer.getUUID()));
                    }
                }
            }
            return;
        }
        if (!this.isMineCompleted()) {
            boolean bl = true;
            for (ServerPlayer serverPlayer : self.players()) {
                if (this.craftmine$mineData.hasPlayerDied(serverPlayer.getUUID())) continue;
                bl = false;
                break;
            }
            if (bl) {
                this.craftmine$handleMineLoss();
            } else {
                this.craftmine$handleMineCountDown(this::handleMineWin);
            }
        } else {
            this.craftmine$handleCompletedMine();
        }
        for (WorldEffect worldEffect : this.craftmine$activeEffects) {
            worldEffect.onMineTick().accept(self);
        }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void craftmine$tickMine(CallbackInfo ci) {
        // Outside a craftmine game context (plain vanilla boot) there is no
        // mine state to tick — the original never had a null theGame because
        // its ServerLevels were always game-owned.
        if (CraftmineServerHooks.theGame() == null) {
            return;
        }
        this.tickEvents();
        ServerLevel self = (ServerLevel) (Object) this;
        net.minecraft.util.profiling.Profiler.get().push("handleInProgressMine");
        this.handleInProgressMine();
        net.minecraft.util.profiling.Profiler.get().pop();
    }

    // ---- ETERNAL_LIGHTNING strike density (batch AI) -------------------------------
    //
    // The fork patches its chunk-tick "thunder" section (ast.a(eha), the 1.21.5
    // ServerLevel.tickChunk body it ships) with THREE effect-gated changes when the
    // level carries the eternal_lightning world effect (fcd.aT):
    //   1. the strike roll drops from nextInt(100000) to nextInt(100) - 1000x more
    //      strikes (that is the whole point of the challenge ingredient),
    //   2. the isThundering() gate gets an `|| hasEffect` bypass,
    //   3. the isRainingAt(target) gate gets an `|| hasEffect` bypass.
    // The fork also forces getRainLevel/getThunderLevel to 1.0 through its own
    // getter overrides (dmy.d/b return 1.0 when aS/aT are active), so its aw()
    // (isRaining) gate is true with the effect alone. 26.3's tickThunder keeps the
    // vanilla roll and gates, so the port produced a "pathetic amount" of lightning
    // (one 1/100000 roll per spawning chunk per tick, plus the isRainingAt check
    // failing on non-precipitating grafted nether/end patches) versus the snapshot's
    // constant storm. This handler replicates the fork's effective condition:
    // with the effect active the whole vanilla condition collapses to the roll.
    @Unique
    @Inject(method = "tickThunder(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("HEAD"), cancellable = true)
    private void craftmine$eternalLightningStrikes(LevelChunk chunk, CallbackInfo ci) {
        if (!dev.craftmine.server.CraftmineGameplayEffects.isActive((ServerLevel) (Object) this, WorldEffects.ETERNAL_LIGHTNING)) {
            return; // vanilla roll and gates
        }
        ci.cancel();
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.random.nextInt(100) != 0) {
            return;
        }
        // Target selection and the skeleton-horse trap roll mirror 26.3's own
        // tickThunder body (identical in the fork's decompiled section: d_() =
        // getCurrentDifficultyAt, ab().c(dmu.f) = SPAWN_MOBS gamerule, M.j() =
        // nextDouble, a_().a(dqj.tc) = lightning-rod check). getBlockRandomPos
        // is public on Level; only findLightningTargetAround is protected.
        ChunkPos pos = chunk.getPos();
        BlockPos target = this.findLightningTargetAround(
                self.getBlockRandomPos(pos.getMinBlockX(), 0, pos.getMinBlockZ(), 15));
        DifficultyInstance difficulty = self.getCurrentDifficultyAt(target);
        boolean isTrap = Boolean.TRUE.equals(self.getGameRules().get(GameRules.SPAWN_MOBS))
                && self.random.nextDouble() < (double) difficulty.getEffectiveDifficulty() * 0.01
                && !self.getBlockState(target.below()).is(BlockTags.LIGHTNING_RODS);
        if (isTrap) {
            SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(self, EntitySpawnReason.EVENT);
            if (horse != null) {
                horse.setTrap(true);
                horse.setAge(0);
                horse.setPos(target.getX(), target.getY(), target.getZ());
                self.addFreshEntity(horse);
            }
        }
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(self, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.snapTo(Vec3.atBottomCenterOf(target));
            bolt.setVisualOnly(isTrap);
            self.addFreshEntity(bolt);
        }
    }

    @Shadow protected abstract BlockPos findLightningTargetAround(BlockPos pos);

    @Override
    public BlockPos craftmine$wardenArenaPos() {
        return this.craftmine$wardenArenaPos;
    }

    @Override
    public void craftmine$setWardenArenaPos(BlockPos pos) {
        this.craftmine$wardenArenaPos = pos;
    }

    @Override
    public BlockPos craftmine$dirtyIceBallPos() {
        return this.craftmine$dirtyIceBallPos;
    }

    @Override
    public DimensionSpecialEffects.Sky craftmine$getActiveSky() {
        ServerLevel self = (ServerLevel) (Object) this;
        if (CraftmineServerHooks.theGame() == null) {
            return null;
        }
        SkyData data = self.getDataStorage().computeIfAbsent(SkyData.TYPE);
        return data.activeSky().orElseGet(() -> SkyDefaults.forLevel(self));
    }

    @Override
    public void craftmine$setActiveSky(DimensionSpecialEffects.Sky sky) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (CraftmineServerHooks.theGame() == null) {
            return;
        }
        SkyData data = self.getDataStorage().computeIfAbsent(SkyData.TYPE);
        data.setActiveSky(java.util.Optional.ofNullable(sky));
        // Sync the picked sky to every player in THIS level.  The matching
        // DimensionType swap (the picked sky's visual environment) is built
        // and broadcast by DimensionControlMenu.updateSky via
        // ClientboundChangeDimensionTypePacket (task 44; the task-28 holder
        // codec restored direct-holder encoding).
        CraftmineServerHooks.theGame().playerList().broadcastAll(
            new dev.craftmine.network.packet.ClientboundSetSkyPacket(self.dimension().identifier(), java.util.Optional.ofNullable(sky)),
            self.dimension()
        );
    }
}
