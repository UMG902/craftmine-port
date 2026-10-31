package dev.craftmine.unlock;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.slf4j.Logger;

import dev.craftmine.duck.CraftmineLevelDuck;
import dev.craftmine.duck.CraftminePlayerDuck;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.server.CraftmineServerHooks;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;

/**
 * Authoritative per-player unlock state: what is obtained, how tree nodes are
 * visualised (visible / hidden / mystery), and which exclusive unlock currently
 * holds each exclusive slot. Mirrored client-side by {@link ClientPlayerUnlocks}.
 *
 * <p>State is persisted as JSON through the advancements {@link DataFixTypes} channel
 * (reusing vanilla's upgrade pipeline) and pushed to clients in batches whenever
 * dirty flags accumulate - {@link #flushDirty} sends a full snapshot on the first
 * packet and deltas afterwards.</p>
 */
public class ServerPlayerUnlocks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PlayerList playerList;
    private final Path playerSavePath;
    private ServerPlayer player;
    private final Codec<PersistedData> codec;
    private boolean isFirstPacket = true;

    private final Map<Holder<PlayerUnlock>, Boolean> obtained = new LinkedHashMap<>();
    private final Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> visibility = new LinkedHashMap<>();
    private final Map<Holder<PlayerUnlock>, Boolean> isActiveExclusive = new LinkedHashMap<>();
    private final Set<Holder<PlayerUnlock>> obtainedChanged = new HashSet<>();
    private final Set<Holder<PlayerUnlock>> updatedNodes = new HashSet<>();
    private final Set<Holder<PlayerUnlock>> activeExclusiveChanged = new HashSet<>();

    public ServerPlayerUnlocks(DataFixer dataFixer, PlayerList playerList, Path playerSavePath, ServerPlayer player) {
        this.playerList = playerList;
        this.playerSavePath = playerSavePath;
        this.player = player;
        this.codec = DataFixTypes.ADVANCEMENTS.wrapCodec(PersistedData.CODEC, dataFixer, 1343);
        this.load();
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    public void reload() {
        this.obtained.clear();
        this.visibility.clear();
        this.isActiveExclusive.clear();
        this.activeExclusiveChanged.clear();
        this.updatedNodes.clear();
        this.isFirstPacket = true;
        this.load();
    }

    private void load() {
        if (Files.isRegularFile(this.playerSavePath)) {
            try (JsonReader jsonReader = new JsonReader(Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8))) {
                jsonReader.setLenient(false);
                JsonElement parsedJson = Streams.parse(jsonReader);
                PersistedData data = this.codec.parse(JsonOps.INSTANCE, parsedJson).getOrThrow(JsonParseException::new);
                this.obtained.putAll(data.obtained());
                this.visibility.putAll(data.visibility());
                this.isActiveExclusive.putAll(data.isActiveExclusive());
            } catch (JsonIOException | IOException throwable) {
                LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, throwable);
            } catch (JsonParseException jsonParseException) {
                LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonParseException);
            }
        }
        for (Holder<PlayerUnlock> holder : CraftmineRegistries.PLAYER_UNLOCK.listElements().toList()) {
            this.isActiveExclusive.computeIfAbsent(holder, key -> false);
            if (this.obtained.computeIfAbsent(holder, key -> false) && (holder.value().exclusiveKey().isEmpty() || this.isActiveExclusive(holder))) {
                this.activate(holder);
            }
            this.visibility.computeIfAbsent(holder, key -> key.value().defaultVisibility());
            this.updatedNodes.add(holder);
        }
    }

    public void save() {
        JsonElement jsonElement = this.codec.encodeStart(JsonOps.INSTANCE, new PersistedData(this.obtained, this.visibility, this.isActiveExclusive)).getOrThrow();
        try {
            FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                GSON.toJson(jsonElement, GSON.newJsonWriter(bufferedWriter));
            }
        } catch (JsonIOException | IOException throwable) {
            LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, throwable);
        }
    }

    public Set<Holder<PlayerUnlock>> getActiveUnlocks() {
        return UnlockEligibility.computeActive(this.obtained, holder -> this.isEligible(holder), this::isActiveExclusive);
    }

    /**
     * Obtained AND allowed to take effect: unoriginal unlocks only count while the
     * unoriginal_features config is on, and an unlock gated by {@link PlayerUnlock#requires()}
     * only counts while that gate unlock is itself eligible.
     */
    private boolean isEligible(Holder<PlayerUnlock> holder) {
        return UnlockEligibility.isEligible(holder, key -> this.obtained.getOrDefault(key, false));
    }

    public boolean isUnlocked(Holder<PlayerUnlock> holder) {
        return this.obtained.getOrDefault(holder, false);
    }

    public boolean isActive(Holder<PlayerUnlock> holder) {
        return this.getActiveUnlocks().contains(holder);
    }

    public boolean isActiveExclusive(Holder<PlayerUnlock> holder) {
        return this.isActiveExclusive.getOrDefault(holder, false);
    }

    public boolean unlock(Holder<PlayerUnlock> holder) {
        if (this.obtained.getOrDefault(holder, false)) {
            return false;
        }
        this.obtained.put(holder, true);
        this.obtainedChanged.add(holder);
        DisplayInfo displayInfo = holder.value().display();
        if (displayInfo.announceToChat()) {
            this.playerList.broadcastSystemMessage(CraftmineServerHooks.createPlayerUnlockAnnouncement(holder, this.player), false);
        }
        this.markForVisibilityUpdate(holder, PlayerUnlock.UnlockVisibility.VISIBLE);
        this.activate(holder);
        if (((CraftmineLevelDuck)this.player.level()).isMine()) {
            this.onMineEnter(holder);
        }
        // Task 35 (persistence fix): the old player object dies at mine entry
        // (cross-dimension teleport creates a fresh ServerPlayer) and the new
        // one lazily re-loads unlock state from disk. Without an immediate
        // save every purchase made since the last player save was lost - the
        // "unlocks reset when entering a mine" report.
        this.save();
        return true;
    }

    /** Exclusive unlocks toggle instead of lock in permanently (hub interaction); unoriginal relock support. */
    public void reactivateUnlock(Holder<PlayerUnlock> holder) {
        if (!holder.value().exclusiveKey().isEmpty()) {
            if (this.isActiveExclusive(holder)) {
                this.deactivateExclusiveUnlock(holder);
            } else {
                this.activateExclusiveUnlock(holder);
            }
            // Task 35: exclusive toggles are player state too - persist them.
            this.save();
        }
    }

    /** Re-runs every activation hook - used when server-side hooks need replaying (e.g. after reload). */
    public void reactivateAllUnlocks() {
        for (Holder<PlayerUnlock> holder : this.getActiveUnlocks()) {
            this.activate(holder);
        }
    }

    private void activate(Holder<PlayerUnlock> holder) {
        holder.value().activation().accept(this.player);
        if (!holder.value().exclusiveKey().isEmpty()) {
            this.activateExclusiveUnlock(holder);
        }
    }

    /** Gives the unlock its exclusive slot and evicts every other holder of the same slot key. */
    private void activateExclusiveUnlock(Holder<PlayerUnlock> holder) {
        String exclusiveKey = holder.value().exclusiveKey();
        if (exclusiveKey.isEmpty()) {
            return;
        }
        for (Holder<PlayerUnlock> other : this.getActiveUnlocks()) {
            if (other.value().exclusiveKey().equals(exclusiveKey)) {
                this.isActiveExclusive.put(other, false);
                this.activeExclusiveChanged.add(other);
            }
        }
        this.isActiveExclusive.put(holder, true);
        this.activeExclusiveChanged.add(holder);
    }

    private void deactivateExclusiveUnlock(Holder<PlayerUnlock> holder) {
        if (this.isActiveExclusive(holder)) {
            this.isActiveExclusive.put(holder, false);
            this.activeExclusiveChanged.add(holder);
        }
    }

    /** Runs all on-mine-enter hooks for the active set - called when a player steps into a mine. */
    public void onMineEnter() {
        for (Holder<PlayerUnlock> holder : this.getActiveUnlocks()) {
            if (this.obtained.getOrDefault(holder, false)) {
                this.onMineEnter(holder);
            }
        }
    }

    private void onMineEnter(Holder<PlayerUnlock> holder) {
        holder.value().onMineEnter().accept(this.player);
    }

    public void makeVisible(Holder<PlayerUnlock> holder) {
        if (this.visibility.getOrDefault(holder, PlayerUnlock.UnlockVisibility.INVISIBLE) != PlayerUnlock.UnlockVisibility.VISIBLE) {
            this.markForVisibilityUpdate(holder, PlayerUnlock.UnlockVisibility.VISIBLE);
        }
    }

    public boolean revoke(Holder<PlayerUnlock> holder) {
        if (!this.obtained.getOrDefault(holder, false)) {
            return false;
        }
        this.obtained.put(holder, false);
        this.obtainedChanged.add(holder);
        this.markForVisibilityUpdate(holder, PlayerUnlock.UnlockVisibility.VISIBLE);
        // Task 35: persist revocations as well (commands / future flows).
        this.save();
        return true;
    }

    private void markForVisibilityUpdate(Holder<PlayerUnlock> holder, PlayerUnlock.UnlockVisibility unlockVisibility) {
        if (this.visibility.get(holder) != unlockVisibility) {
            this.visibility.put(holder, unlockVisibility);
            this.updatedNodes.add(holder);
        }
    }

    public void flushDirty(ServerPlayer receiver) {
        // TheGame adoption happens in each server type's initServer() TAIL; if a
        // future boot path misses it, skip the flush instead of crashing the tick.
        if (CraftmineServerHooks.theGame() == null) {
            return;
        }
        if (this.isFirstPacket) {
            CraftmineServerHooks.theGame().playerList().broadcastAll(new ClientboundUpdatePlayerUnlocksPacket(receiver.getId(), this.isFirstPacket, this.isActiveExclusive, this.visibility, this.obtained));
        }
        if (!this.updatedNodes.isEmpty() || !this.obtainedChanged.isEmpty() || !this.activeExclusiveChanged.isEmpty()) {
            Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> visibilityDelta = new HashMap<>();
            Map<Holder<PlayerUnlock>, Boolean> obtainedDelta = new HashMap<>();
            Map<Holder<PlayerUnlock>, Boolean> exclusiveDelta = new HashMap<>();
            for (Holder<PlayerUnlock> holder : this.updatedNodes) {
                this.updateTreeVisibility(holder, visibilityDelta);
                visibilityDelta.put(holder, this.visibility.get(holder));
            }
            this.updatedNodes.clear();
            for (Holder<PlayerUnlock> holder : this.obtainedChanged) {
                obtainedDelta.put(holder, this.obtained.getOrDefault(holder, false));
            }
            this.obtainedChanged.clear();
            for (Holder<PlayerUnlock> holder : this.activeExclusiveChanged) {
                exclusiveDelta.put(holder, this.isActiveExclusive.getOrDefault(holder, false));
            }
            this.activeExclusiveChanged.clear();
            if (!obtainedDelta.isEmpty() || !visibilityDelta.isEmpty() || !exclusiveDelta.isEmpty()) {
                CraftmineServerHooks.theGame().playerList().broadcastAll(new ClientboundUpdatePlayerUnlocksPacket(receiver.getId(), false, this.isActiveExclusive, visibilityDelta, obtainedDelta));
            }
        }
        this.isFirstPacket = false;
    }

    /**
     * Nodes whose own state changed may hide their ancestry: any parent currently
     * VISIBLE becomes MYSTERY when a child is set to a non-VISIBLE state, so the
     * tree shows a locked silhouette instead of a full reveal.
     */
    private void updateTreeVisibility(Holder<PlayerUnlock> holder, Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> delta) {
        if (holder.value().defaultVisibility() == this.visibility.getOrDefault(holder, PlayerUnlock.UnlockVisibility.VISIBLE)) {
            return;
        }
        PlayerUnlock.UnlockVisibility newVisibility = this.visibility.getOrDefault(holder, PlayerUnlock.UnlockVisibility.VISIBLE);
        if (newVisibility == PlayerUnlock.UnlockVisibility.INVISIBLE) {
            return;
        }
        Optional<Holder<PlayerUnlock>> parent = holder.value().parent();
        while (parent.isPresent()) {
            if (this.visibility.getOrDefault(parent.get(), PlayerUnlock.UnlockVisibility.VISIBLE) == PlayerUnlock.UnlockVisibility.INVISIBLE) {
                this.visibility.put(parent.get(), PlayerUnlock.UnlockVisibility.MYSTERY);
                delta.put(parent.get(), PlayerUnlock.UnlockVisibility.MYSTERY);
            }
            parent = parent.get().value().parent();
        }
    }

    public boolean isVisibleAtAll(Holder<PlayerUnlock> holder) {
        if (!holder.value().isEnabled()) {
            return false;
        }
        Optional<Holder<PlayerUnlock>> requires = holder.value().requires();
        if (requires.isPresent() && !this.isEligible(requires.get())) {
            return false;
        }
        return this.visibility.getOrDefault(holder, PlayerUnlock.UnlockVisibility.INVISIBLE) == PlayerUnlock.UnlockVisibility.VISIBLE;
    }

    /**
     * True when any currently unlocked unlock hangs below this one: a tree child, or an extra unlock
     * that is gated on this one via {@link PlayerUnlock#requires()}.
     */
    public boolean hasUnlockedChild(Holder<PlayerUnlock> holder) {
        return UnlockEligibility.hasUnlockedChild(holder, key -> this.obtained.getOrDefault(key, false));
    }

    /**
     * Unoriginal "relock": takes an unlock back. Undoes its activation effects, clears its
     * exclusive flag and hides unlocks that were gated on it. Rules (hub only, no children
     * unlocked, ...) are checked by the caller (CraftmineRelock); this just performs it.
     */
    public boolean relock(Holder<PlayerUnlock> holder) {
        if (!this.obtained.getOrDefault(holder, false)) {
            return false;
        }
        this.obtained.put(holder, false);
        this.obtainedChanged.add(holder);
        holder.value().deactivation().accept(this.player);
        if (!holder.value().exclusiveKey().isEmpty()) {
            this.isActiveExclusive.put(holder, false);
            this.activeExclusiveChanged.add(holder);
        }
        for (Holder<PlayerUnlock> other : CraftmineRegistries.PLAYER_UNLOCK.listElements().toList()) {
            Optional<Holder<PlayerUnlock>> requires = other.value().requires();
            if (requires.isPresent() && requires.get().equals(holder)) {
                this.markForVisibilityUpdate(other, PlayerUnlock.UnlockVisibility.INVISIBLE);
            }
        }
        this.save();
        return true;
    }

    /**
     * Snapshot written to disk. Keys are the raw map contents; the codec field names
     * ("obtained" / "visibility" / "isActiveExclusive") are the on-disk format and must
     * not change - older saves upgrade through vanilla's advancements datafix pipeline.
     */
    record PersistedData(Map<Holder<PlayerUnlock>, Boolean> obtained,
        Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> visibility,
        Map<Holder<PlayerUnlock>, Boolean> isActiveExclusive) {

        static final Codec<PersistedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(CraftmineRegistries.PLAYER_UNLOCK.holderByNameCodec(), Codec.BOOL).fieldOf("obtained").forGetter(PersistedData::obtained),
            Codec.unboundedMap(CraftmineRegistries.PLAYER_UNLOCK.holderByNameCodec(), StringRepresentable.fromEnum(PlayerUnlock.UnlockVisibility::values)).fieldOf("visibility").forGetter(PersistedData::visibility),
            Codec.unboundedMap(CraftmineRegistries.PLAYER_UNLOCK.holderByNameCodec(), Codec.BOOL).fieldOf("isActiveExclusive").forGetter(PersistedData::isActiveExclusive)
        ).apply(instance, PersistedData::new));
    }
}
