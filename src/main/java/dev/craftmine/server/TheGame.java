/*
 * Ported from 25w14craftmine (dev.craftmine.server.TheGame).
 * Adapted to the 26.3 APIs: ServerLevel(MinecraftServer,...) ctor, LevelLoadListener,
 * SavedDataStorage, data-driven brewing/fuel (removed here), ServerScoreboard(MinecraftServer),
 * and static hooks instead of fork-added MinecraftServer members.
 */
package dev.craftmine.server;

import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.world.level.CustomSpawner;
import com.mojang.datafixers.DataFixer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.server.players.PlayerList;
import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import dev.craftmine.server.PackStuff;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.WorldStem;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LevelLoadListener.Stage;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import dev.craftmine.server.CraftmineTime;

public class TheGame
implements AutoCloseable {
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BlockPos DEFAULT_SPAWN = new BlockPos(13, 2, 8);
    public static final float SPAWN_ANGLE = 90.0f;
    private final LevelStorageSource.LevelStorageAccess storageSource;
    // Non-final: CraftmineRegistryMaterialization rebuilds the WORLD layer when a
    // mine dimension materializes live (the snapshot achieved the same through its
    // full WorldReloader pass); the generation counter tracks client-visible bumps.
    private LayeredRegistryAccess<RegistryLayer> registries;
    private int craftmine$registryGeneration;
    private final Map<ResourceKey<Level>, ServerLevel> levels = new LinkedHashMap<ResourceKey<Level>, ServerLevel>();
    private final PackRepository packRepository;
    private ReloadableResources resources;
    private final WorldData worldData;
    private final ServerScoreboard scoreboard;
    @Nullable
    private CommandStorage commandStorage;
    private final MinecraftServer server;
    private final PlayerList playerList;
    private final ServerTickRateManager tickRateManager;
    private final StructureTemplateManager structureTemplateManager;

    private TheGame(MinecraftServer minecraftServer, PackRepository packRepository, WorldStem worldStem, LevelStorageSource.LevelStorageAccess levelStorageAccess, Function<TheGame, PlayerList> function) {
        this.storageSource = levelStorageAccess;
        this.server = minecraftServer;
        this.registries = worldStem.registries();
        this.worldData = worldStem.worldDataAndGenSettings().data();
        this.playerList = function.apply(this);
        this.scoreboard = new ServerScoreboard(minecraftServer);
        if (!this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) {
            throw new IllegalStateException("Missing Overworld dimension data");
        }
        this.packRepository = packRepository;
        this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), levelStorageAccess, minecraftServer.getFixerUpper(), this.registries.compositeAccess().lookupOrThrow(Registries.BLOCK));
        this.resources = new ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
        this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
        this.tickRateManager = new ServerTickRateManager(minecraftServer);
    }

    public MinecraftServer server() {
        return this.server;
    }

    public BlockableEventLoop<?> eventLoop() {
        return this.server;
    }

    public ChunkIOErrorReporter chunkIOErrorReporter() {
        return this.server;
    }

    public PlayerList playerList() {
        return this.playerList;
    }

    @Override
    public void close() {
        for (ServerLevel serverLevel : this.levels.values()) {
            if (serverLevel == null) continue;
            try {
                serverLevel.close();
            }
            catch (IOException iOException) {
                LOGGER.error("Exception closing the level", (Throwable)iOException);
            }
        }
        this.resources.close();
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.server.getFunctions();
    }

    public final ServerLevel overworld() {
        return this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> resourceKey) {
        return this.levels.get(resourceKey);
    }

    /**
     * Runtime creation of a generated mine dimension (craftmine's live datapack reload
     * equivalent, which 26.3 cannot do). Mirrors the non-overworld branch of
     * createLevels(); the LevelStem comes pre-decoded from the dimension's
     * self-contained datapack JSON (biomes are inlined by the generator encoder).
     */
    @Nullable
    public ServerLevel createGeneratedLevel(ResourceKey<LevelStem> stemKey, LevelStem levelStem) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, stemKey.identifier());
        ServerLevel existing = this.levels.get(levelKey);
        if (existing != null) {
            return existing;
        }
        ServerLevelData serverLevelData = this.worldData.overworldData();
        long seed = this.server.getWorldGenSettings().options().seed();
        long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);
        boolean debug = this.worldData.isDebugWorld();
        DerivedLevelData derivedLevelData = new DerivedLevelData(this.worldData, serverLevelData);
        ServerLevel serverLevel = new ServerLevel(this.server, this.server.executor, this.storageSource, derivedLevelData, levelKey, levelStem, debug, obfuscatedSeed, List.of(), false);
        this.levels.put(levelKey, serverLevel);
        // ALSO register in the VANILLA server's level map: the server tick loop
        // (tickChildren -> getAllLevels), chunk-send phase and save-on-stop all
        // iterate MinecraftServer.levels. A level that only lives in this.levels
        // never ticks - its distance manager never processes tickets, chunks
        // never reach the sendable (BLOCK_TICKING) state and clients hang on
        // "Loading terrain..." forever. The snapshot achieved the same through
        // its WorldReloader (full server rebuild with the new level present).
        //
        // Task 64 CRASH FIX: this method runs from ServerLevel.tick (the mine-enter
        // countdown in ServerLevelMixin) - i.e. in the MIDDLE of tickChildren's
        // iteration over MinecraftServer.levels.values(). An immediate put on a
        // LinkedHashMap under an open iterator throws ConcurrentModificationException
        // the moment the loop advances ("entering the second mine a crash happened").
        // The registration is now DEFERRED to tickChildren TAIL via the queue in
        // CraftmineLevelRegistration - same between-ticks timing the original's
        // WorldReloader had. The returned ServerLevel is fully functional
        // immediately (this.levels already holds it; the same-tick teleport
        // pipeline uses the object directly), it only starts TICKING from the
        // next tick - the level is visible to the whole tick loop by then.
        dev.craftmine.server.CraftmineLevelRegistration.enqueue(this.server, levelKey, serverLevel);
        this.updateMobSpawningFlags();
        return serverLevel;
    }

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Collection<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    /**
     * Installs a rebuilt registry access (mine materialization) and bumps the
     * generation counter. Callers must also update the server's own field
     * (MinecraftServerMixin/Accessor) — the configuration phase registry sync
     * reads MinecraftServer.registries().
     */
    public void craftmine$updateRegistries(LayeredRegistryAccess<RegistryLayer> newRegistries) {
        this.registries = newRegistries;
        this.craftmine$registryGeneration++;
    }

    /** How many times the registry access was replaced since boot. */
    public int craftmine$registryGeneration() {
        return this.craftmine$registryGeneration;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public void setDefaultGameType(GameType gameType) {
        this.worldData.setGameType(gameType);
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public StructureTemplateManager structureManager() {
        return this.structureTemplateManager;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public DataFixer getFixerUpper() {
        return this.server.getFixerUpper();
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        }
        return this.commandStorage;
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.server.getCustomBossEvents();
    }

    public static TheGame create(MinecraftServer minecraftServer, PackRepository packRepository, WorldStem worldStem, LevelStorageSource.LevelStorageAccess levelStorageAccess, Function<TheGame, PlayerList> function) {
        TheGame theGame = new TheGame(minecraftServer, packRepository, worldStem, levelStorageAccess, function);
        theGame.worldData.setModdedInfo(minecraftServer.getServerModName(), minecraftServer.getModdedStatus().shouldReportAsModified());
        theGame.createLevels();
        theGame.prepareLevels();
        return theGame;
    }

    /**
     * Adopts an already-booted vanilla server: 26.3 creates the levels, player
     * list and datapack resources itself, so instead of TheGame.create()'s
     * duplicate boot path we wrap the live server. The level-creation and
     * chunk-preparation phases are skipped — vanilla already did them.
     */
    public static TheGame adopt(MinecraftServer minecraftServer) {
        return new TheGame(minecraftServer);
    }

    private TheGame(MinecraftServer minecraftServer) {
        this.server = minecraftServer;
        this.storageSource = ((dev.craftmine.mixin.MinecraftServerAccessor) (Object) minecraftServer).craftmine$storageSource();
        this.registries = minecraftServer.registries();
        this.worldData = minecraftServer.getWorldData();
        this.playerList = minecraftServer.getPlayerList();
        this.scoreboard = minecraftServer.getScoreboard();
        this.packRepository = minecraftServer.getPackRepository();
        this.structureTemplateManager = minecraftServer.getStructureTemplateManager();
        var vanillaResources = minecraftServer.resources;
        this.resources = new ReloadableResources(vanillaResources.resourceManager(), vanillaResources.managers());
        this.tickRateManager = minecraftServer.tickRateManager();
        for (ServerLevel serverLevel : minecraftServer.getAllLevels()) {
            this.levels.put(serverLevel.dimension(), serverLevel);
        }
    }

    private void createLevels() {
        ServerLevelData serverLevelData = this.worldData.overworldData();
        boolean bl = this.worldData.isDebugWorld();
        Registry<LevelStem> registryLookup = this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
        WorldOptions worldOptions = this.server.getWorldGenSettings().options();
        long l = worldOptions.seed();
        long l2 = BiomeManager.obfuscateSeed(l);
        ImmutableList<CustomSpawner> immutableList = ImmutableList.of(new PhantomSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(this.server.getDataStorage()));
        LevelStem levelStem = registryLookup.getValue(LevelStem.OVERWORLD);
        ServerLevel serverLevel = new ServerLevel(this.server, this.server.executor, this.storageSource, serverLevelData, Level.OVERWORLD, levelStem, bl, l2, (List<CustomSpawner>)immutableList, true);
        this.levels.put(Level.OVERWORLD, serverLevel);
        SavedDataStorage savedDataStorage = serverLevel.getDataStorage();
        this.commandStorage = new CommandStorage(savedDataStorage);
        WorldBorder worldBorder = serverLevel.getWorldBorder();
        if (!serverLevelData.isInitialized()) {
            try {
                TheGame.setInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(), bl);
                serverLevelData.setInitialized(true);
                if (bl) {
                    this.setupDebugLevel(this.worldData);
                }
            }
            catch (Throwable throwable) {
                CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception initializing level");
                try {
                    serverLevel.fillReportDetails(crashReport);
                }
                catch (Throwable throwable2) {
                    // empty catch block
                }
                throw new ReportedException(crashReport);
            }
            serverLevelData.setInitialized(true);
        }
        this.playerList().addWorldborderListener(serverLevel);
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : registryLookup.entrySet()) {
            ResourceKey<LevelStem> resourceKey = entry.getKey();
            if (resourceKey == LevelStem.OVERWORLD) continue;
            ResourceKey<Level> resourceKey2 = ResourceKey.create(Registries.DIMENSION, resourceKey.identifier());
            DerivedLevelData derivedLevelData = new DerivedLevelData(this.worldData, serverLevelData);
            ServerLevel serverLevel2 = new ServerLevel(this.server, this.server.executor, this.storageSource, derivedLevelData, resourceKey2, entry.getValue(), bl, l2, List.of(), false);
            this.levels.put(resourceKey2, serverLevel2);
        }
    }

    private static void setInitialSpawn(ServerLevel serverLevel, ServerLevelData serverLevelData, boolean bl, boolean bl2) {
        serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), DEFAULT_SPAWN, SPAWN_ANGLE, 0.0f));
    }

    private void setupDebugLevel(WorldData worldData) {
        worldData.setDifficulty(Difficulty.PEACEFUL);
        worldData.setDifficultyLocked(true);
        ServerLevelData serverLevelData = worldData.overworldData();
        // 26.3: level weather/time setters were removed (clock manager +
        // environment attributes). The fixed daytime is applied in prepareLevels;
        // debug worlds are never rained on since ADVANCE_WEATHER stays on and
        // the debug dimension uses a clear-sky environment profile.
        this.craftmine$debugLevelFixedTime = true;
        serverLevelData.setGameType(GameType.SPECTATOR);
    }

    private boolean craftmine$debugLevelFixedTime = false;

    private void prepareLevels() {
        ServerLevel serverLevel = this.overworld();
        LOGGER.info("Preparing start region for dimension {}", (Object)serverLevel.dimension().identifier());
        BlockPos blockPos = serverLevel.getRespawnData().pos();
        LevelLoadListener levelLoadListener = this.server.getLevelLoadListener();
        levelLoadListener.updateFocus(Level.OVERWORLD, ChunkPos.containing(blockPos));
        CraftmineServerHooks.theGameButSuperSpecialOneForLoading = this;
        ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
        CraftmineServerHooks.runNextTickNow(this.server);
        serverLevel.setRespawnData(LevelData.RespawnData.of(serverLevel.dimension(), blockPos, serverLevel.getRespawnData().yaw(), serverLevel.getRespawnData().pitch()));
        if (this.craftmine$debugLevelFixedTime) {
            CraftmineTime.setDayTime(serverLevel, 6000L);
        }
        net.minecraft.server.level.ChunkLoadCounter chunkLoadCounter = new net.minecraft.server.level.ChunkLoadCounter();
        chunkLoadCounter.track(serverLevel, () -> {});
        while (chunkLoadCounter.pendingChunks() > 0) {
            CraftmineServerHooks.runTicksDuringServerPrepare(this.server);
        }
        CraftmineServerHooks.runTicksDuringServerPrepare(this.server);
        for (ServerLevel serverLevel2 : this.levels.values()) {
            TicketStorage ticketStorage = serverLevel2.getDataStorage().get(TicketStorage.TYPE);
            if (ticketStorage == null) continue;
            ticketStorage.activateAllDeactivatedTickets();
        }
        CraftmineServerHooks.runTicksDuringServerPrepare(this.server);
        CraftmineServerHooks.theGameButSuperSpecialOneForLoading = null;
        levelLoadListener.finish(Stage.LOAD_INITIAL_CHUNKS);
        this.updateMobSpawningFlags();
    }

    public boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3) {
        boolean bl4 = false;
        for (ServerLevel object2 : this.getAllLevels()) {
            if (!bl) {
                LOGGER.info("Saving chunks for level '{}'/{}", (Object)object2, (Object)object2.dimension().identifier());
            }
            object2.save(null, bl2, object2.noSave && !bl3);
            bl4 = true;
        }
        ServerLevel serverLevel2 = this.overworld();
        ServerLevelData serverLevelData = this.worldData.overworldData();
        this.storageSource.saveDataTag(this.worldData);
        if (bl2) {
            Stream.concat(Stream.of("world", "DIM-1", "DIM1", "Clive", "Sardines"), this.getAllLevels().stream().map(serverLevel -> String.valueOf(serverLevel.dimension().identifier()))).distinct().forEach(string -> LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", string));
            LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }
        return bl4;
    }

    public void fillSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
        systemReport.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
        systemReport.setDetail("Enabled Feature Flags", () -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(Identifier::toString).collect(Collectors.joining(", ")));
        systemReport.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
        systemReport.setDetail("World Seed", () -> String.valueOf(this.server.getWorldGenSettings().options().seed()));
    }

    public void saveDebugReport(Path path) throws IOException {
        Path path2 = path.resolve("levels");
        try {
            for (Map.Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
                Identifier identifier = entry.getKey().identifier();
                Path path3 = path2.resolve(identifier.getNamespace()).resolve(identifier.getPath());
                Files.createDirectories(path3, new FileAttribute[0]);
                entry.getValue().saveDebugReport(path3);
            }
            this.dumpGameRules(path.resolve("gamerules.txt"));
        }
        catch (IOException iOException) {
            LOGGER.warn("Failed to save debug report", (Throwable)iOException);
        }
    }

    private void dumpGameRules(Path path) throws IOException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, new java.nio.file.OpenOption[0]);){
            final ArrayList<String> arrayList = new ArrayList<>();
            final GameRules gameRules = this.getGameRules();
            gameRules.visitGameRuleTypes(new GameRuleTypeVisitor(){
                @Override
                public <T> void visit(GameRule<T> rule) {
                    arrayList.add(String.format(Locale.ROOT, "%s=%s\n", rule.getIdentifier(), gameRules.get(rule)));
                }
            });
            for (String string : arrayList) {
                bufferedWriter.write(string);
            }
        }
    }

    public void setDifficulty(Difficulty difficulty, boolean bl) {
        if (!bl && this.worldData.isDifficultyLocked()) {
            return;
        }
        this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : difficulty);
        this.updateMobSpawningFlags();
        this.playerList().getPlayers().forEach(TheGame::sendDifficultyUpdate);
    }

    public void setDifficultyLocked(PlayerList playerList, boolean bl) {
        this.worldData.setDifficultyLocked(bl);
        playerList.getPlayers().forEach(TheGame::sendDifficultyUpdate);
    }

    public static void sendDifficultyUpdate(ServerPlayer serverPlayer) {
        LevelData levelData = serverPlayer.level().getLevelData();
        serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
    }

    private void updateMobSpawningFlags() {
        for (ServerLevel serverLevel : this.getAllLevels()) {
            serverLevel.setSpawnSettings(this.isSpawningMonsters());
        }
    }

    public CompletableFuture<Void> reloadResources(Collection<String> collection) {
        CompletableFuture<Void> completionStage = CompletableFuture.supplyAsync(() -> collection.stream().map(this.packRepository::getPack).filter(Objects::nonNull).flatMap(Pack::open).collect(ImmutableList.toImmutableList()), this.server).thenCompose(immutableList -> {
            MultiPackResourceManager multiPackResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, (List<PackResources>)immutableList);
            List<Registry.PendingTags<?>> list = TagLoader.loadTagsForExistingRegistries(multiPackResourceManager, this.registries.compositeAccess());
            return ReloadableServerResources.loadResources(multiPackResourceManager, this.registries, list, this.worldData.enabledFeatures(), this.server.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED, this.server.getFunctionCompilationPermissions(), this.server.executor, this.server).whenComplete((reloadableServerResources, throwable) -> {
                if (throwable != null) {
                    multiPackResourceManager.close();
                }
            }).thenApply(reloadableServerResources -> new ReloadableResources(multiPackResourceManager, reloadableServerResources));
        }).thenAcceptAsync(reloadableResources -> {
            this.resources.close();
            this.resources = reloadableResources;
            // Task 60 (locate root cause): mirror vanilla MinecraftServer.reloadResources -
            // the server's OWN resources field drives server.getCommands() (the ONLY
            // dispatcher used by performUnsignedChatCommand / handleCommand and the
            // console). Leaving it boot-time kept the boot command context alive even
            // after a successful reload, so /locate biome minecraft:levelN/<biome>
            // never parsed for in-session created mines.
            ((dev.craftmine.mixin.MinecraftServerAccessor) (Object) this.server).craftmine$setResources(new MinecraftServer.ReloadableResources(reloadableResources.resourceManager(), reloadableResources.managers()));
            this.packRepository.setSelected(collection);
            WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(PackStuff.getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures());
            this.worldData.setDataConfiguration(worldDataConfiguration);
            // Task 58: vanilla MinecraftServer.reloadResources applies
            // postponed tag merges + data-component initializers here
            // (updateComponentsAndStaticRegistryTags); without it the reload's
            // own tag updates never landed. bootstrapCustomRegistries (fired
            // from the ReloadableServerResourcesMixin on that method) is
            // documented idempotent.
            this.resources.managers.updateComponentsAndStaticRegistryTags();
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.playerList().saveAll();
            this.playerList().reloadResources();
            this.server.getFunctions().replaceLibrary(this.resources.managers.getFunctionLibrary());
            this.updateMobSpawningFlags();
        }, (Executor)this.server);
        if (this.server.isSameThread()) {
            this.server.managedBlock(completionStage::isDone);
        }
        return completionStage;
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverLevel = this.overworld();
        return new CommandSourceStack(this.server, serverLevel == null ? Vec3.ZERO : Vec3.atLowerCornerOf(serverLevel.getRespawnData().pos()), Vec2.ZERO, serverLevel, this.server.getFunctionCompilationPermissions(), Component.literal("Server"), this.server);
    }

    public void synchronizeTime(ServerLevel serverLevel) {
        // 26.3: time is global clock state; a full sync packet carries all clocks
        this.playerList().broadcastAll(this.server.clockManager().createFullSyncPacket());
    }

    public void forceTimeSynchronization() {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("timeSync");
        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.synchronizeTime(serverLevel);
        }
        profilerFiller.pop();
    }

    static final class ReloadableResources
    implements AutoCloseable {
        final CloseableResourceManager resourceManager;
        final ReloadableServerResources managers;

        ReloadableResources(CloseableResourceManager closeableResourceManager, ReloadableServerResources reloadableServerResources) {
            this.resourceManager = closeableResourceManager;
            this.managers = reloadableServerResources;
        }

        @Override
        public void close() {
            this.resourceManager.close();
        }

            public String toString() {
        return "ReloadableResources[" + "resourceManager=" + this.resourceManager + ", " + "managers=" + this.managers + "]";
    }

            public int hashCode() {
        int result = 0;
        result = 31 * result + java.util.Objects.hashCode(this.resourceManager);
        result = 31 * result + java.util.Objects.hashCode(this.managers);
        return result;
    }

            public boolean equals(Object object) {
        if (!(object instanceof ReloadableResources that)) return false;
        if (!java.util.Objects.equals(this.resourceManager, that.resourceManager)) return false;
        if (!java.util.Objects.equals(this.managers, that.managers)) return false;
        return true;
    }

        public CloseableResourceManager resourceManager() {
            return this.resourceManager;
        }

        public ReloadableServerResources managers() {
            return this.managers;
        }
    }
}
