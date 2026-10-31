package dev.craftmine.unlock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import dev.craftmine.CraftmineConfig;
import dev.craftmine.network.packet.ClientboundUpdatePlayerUnlocksPacket;
import dev.craftmine.registry.CraftmineRegistries;
import dev.craftmine.client.screen.PlayerUnlocksTree;
import net.minecraft.core.Holder;

/**
 * Client mirror of {@link ServerPlayerUnlocks}: unlock/visibility/exclusive state as
 * broadcast by the server, plus the tree built from the {@code player_unlock} registry
 * for the hub unlock screen. The server is authoritative - this class only renders
 * and pre-checks interactions (which the server re-validates).
 */
public class ClientPlayerUnlocks {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object2BooleanMap<Holder<PlayerUnlock>> unlocks = new Object2BooleanOpenHashMap<>();
    private final Map<Holder<PlayerUnlock>, PlayerUnlock.UnlockVisibility> visibility = new HashMap<>();
    private final Object2BooleanMap<Holder<PlayerUnlock>> activeExclusive = new Object2BooleanOpenHashMap<>();
    private final PlayerUnlocksTree tree = new PlayerUnlocksTree();
    @Nullable
    private Listener listener;
    @Nullable
    private Holder<PlayerUnlock> selectedTab;

    public ClientPlayerUnlocks() {
        HashSet<Holder<PlayerUnlock>> added = new HashSet<>();
        CraftmineRegistries.PLAYER_UNLOCK.listElements().forEach(holder -> this.addUnlock(holder, added));
        int rootCount = 0;
        for (Holder<PlayerUnlock> ignored : this.tree.roots()) {
            rootCount++;
        }
        LOGGER.info("Craftmine: client unlocks tree built (player_unlock entries: {}, roots: {})",
            CraftmineRegistries.PLAYER_UNLOCK.keySet().size(), rootCount);
    }

    /** Adds an unlock and (recursively) its ancestors so tree children never dangle; disabled (unoriginal) unlocks are skipped entirely. */
    private void addUnlock(Holder<PlayerUnlock> holder, Set<Holder<PlayerUnlock>> added) {
        if (added.contains(holder) || !holder.value().isEnabled()) {
            return;
        }
        Optional<Holder<PlayerUnlock>> parent = holder.value().parent();
        parent.ifPresent(value -> this.addUnlock(value, added));
        this.tree.addAll(List.of(holder));
        added.add(holder);
    }

    /** Applies a server update, then notifies the listener for every changed entry. */
    public void update(ClientboundUpdatePlayerUnlocksPacket packet) {
        if (packet.reset()) {
            this.unlocks.clear();
            this.visibility.clear();
            this.activeExclusive.clear();
        }
        this.unlocks.putAll(packet.obtained());
        this.visibility.putAll(packet.visibility());
        this.activeExclusive.putAll(packet.isActiveExclusive());
        if (this.listener != null) {
            this.unlocks.forEach(this.listener::onUnlockStatusChange);
            this.visibility.forEach(this.listener::onVisibilityStatusChange);
            this.activeExclusive.forEach(this.listener::onActiveExclusiveStatusChange);
        }
    }

    public void setSelectedTab(@Nullable Holder<PlayerUnlock> holder) {
        if (this.selectedTab != holder) {
            this.selectedTab = holder;
            if (this.listener != null) {
                this.listener.onSelectedTabChanged(holder);
            }
        }
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        this.tree.setListener(listener);
        if (listener != null) {
            this.unlocks.forEach(listener::onUnlockStatusChange);
            this.visibility.forEach(listener::onVisibilityStatusChange);
            this.activeExclusive.forEach(listener::onActiveExclusiveStatusChange);
            listener.onSelectedTabChanged(this.selectedTab);
        }
    }

    public PlayerUnlocksTree getTree() {
        return this.tree;
    }

    /** Mirror of ServerPlayerUnlocks.isEligible: obtained, enabled by config, and any gate unlock eligible too. */
    private boolean isEligible(Holder<PlayerUnlock> holder) {
        return UnlockEligibility.isEligible(holder, key -> this.unlocks.getBoolean(key));
    }

    public Set<Holder<PlayerUnlock>> getActiveUnlocks() {
        return UnlockEligibility.computeActive(this.unlocks, holder -> this.isEligible(holder), this::isActiveExclusive);
    }

    public boolean isUnlocked(Holder<PlayerUnlock> holder) {
        return this.unlocks.getBoolean(holder);
    }

    public boolean isActive(Holder<PlayerUnlock> holder) {
        return this.getActiveUnlocks().contains(holder);
    }

    public PlayerUnlock.UnlockVisibility getVisibility(Holder<PlayerUnlock> holder) {
        return this.visibility.getOrDefault(holder, PlayerUnlock.UnlockVisibility.INVISIBLE);
    }

    public boolean isActiveExclusive(Holder<PlayerUnlock> holder) {
        return this.activeExclusive.getBoolean(holder);
    }

    /** Visible itself AND every ancestor visible - hidden parents mask their subtree. */
    public boolean isVisibleAtAll(Holder<PlayerUnlock> holder) {
        if (!holder.value().isEnabled()) {
            return false;
        }
        Optional<Holder<PlayerUnlock>> requires = holder.value().requires();
        if (requires.isPresent() && !this.isEligible(requires.get())) {
            return false;
        }
        if (this.getVisibility(holder) == PlayerUnlock.UnlockVisibility.INVISIBLE) {
            return false;
        }
        return holder.value().parent().isEmpty() || this.isVisibleAtAll(holder.value().parent().get());
    }

    public boolean isUnlockable(Holder<PlayerUnlock> holder) {
        if (!holder.value().isEnabled()) {
            return false;
        }
        Optional<Holder<PlayerUnlock>> requires = holder.value().requires();
        if (requires.isPresent() && !this.isEligible(requires.get())) {
            return false;
        }
        return this.getVisibility(holder) == PlayerUnlock.UnlockVisibility.VISIBLE;
    }

    /** True when any unlocked unlock hangs below this one (tree child or extra unlock gated on it). */
    public boolean hasUnlockedChild(Holder<PlayerUnlock> holder) {
        return UnlockEligibility.hasUnlockedChild(holder, this::isUnlocked);
    }

    /** Unoriginal relock: client-side pre-check (the server re-validates, and also enforces hub-only). */
    public boolean canRelock(Holder<PlayerUnlock> holder) {
        return CraftmineConfig.unoriginalFeatures()
            && this.isActive(PlayerUnlocks.QOL_RELOCK)
            && this.isUnlocked(holder)
            && holder.value().parent().isPresent()
            && !holder.equals(PlayerUnlocks.QOL_RELOCK)
            && !this.hasUnlockedChild(holder);
    }

    public interface Listener extends PlayerUnlocksTree.Listener {
        void onUnlockStatusChange(Holder<PlayerUnlock> holder, boolean unlocked);

        void onVisibilityStatusChange(Holder<PlayerUnlock> holder, PlayerUnlock.UnlockVisibility visibility);

        void onActiveExclusiveStatusChange(Holder<PlayerUnlock> holder, boolean activeExclusive);

        void onSelectedTabChanged(@Nullable Holder<PlayerUnlock> holder);
    }
}
