package dev.craftmine.client.screen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import dev.craftmine.unlock.PlayerUnlock;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Client-side mirror of the unlocks tree: which unlocks exist and how they
 * nest. Unlocks arrive from the server in arbitrary order, so insertion
 * retries until every parent is placed; the listener mirrors every change
 * into the tab widgets. Roots are tabs, everything else is a task under a
 * tab (or under another task).
 */
public class PlayerUnlocksTree {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Children by unlock. */
    private final Map<Holder<PlayerUnlock>, List<Holder<PlayerUnlock>>> nodes = new Object2ObjectOpenHashMap<>();
    /** Insertion-ordered set of tab roots. */
    private final Set<Holder<PlayerUnlock>> roots = new ObjectLinkedOpenHashSet<>();
    /** Insertion-ordered set of non-root unlocks. */
    private final Set<Holder<PlayerUnlock>> tasks = new ObjectLinkedOpenHashSet<>();
    @Nullable
    private Listener listener;

    private void removeSingle(Holder<PlayerUnlock> unlock) {
        // Remove the whole subtree: children first, then the node itself.
        List<Holder<PlayerUnlock>> children = this.nodes.get(unlock);
        for (Holder<PlayerUnlock> child : children) {
            this.removeSingle(child);
        }
        LOGGER.info("Forgot about unlock {}", unlock);
        this.nodes.remove(unlock);
        boolean isRoot = unlock.value().parent().isEmpty();
        if (isRoot) {
            this.roots.remove(unlock);
            if (this.listener != null) {
                this.listener.onRemoveUnlocksRoot(unlock);
            }
        } else {
            this.tasks.remove(unlock);
            if (this.listener != null) {
                this.listener.onRemoveUnlock(unlock);
            }
        }
    }

    public void remove(Set<Holder<PlayerUnlock>> unlocks) {
        for (Holder<PlayerUnlock> unlock : unlocks) {
            if (!this.nodes.containsKey(unlock)) {
                LOGGER.warn("Told to remove unlock {} but I don't know what that is", unlock);
                continue;
            }
            this.removeSingle(unlock);
        }
    }

    /**
     * Inserts unlocks in any order, retrying until a pass inserts nothing
     * (orphaned unlocks whose parent has not arrived yet).
     */
    public void addAll(Collection<Holder<PlayerUnlock>> unlocks) {
        List<Holder<PlayerUnlock>> pending = new ArrayList<>(unlocks);
        while (!pending.isEmpty()) {
            if (pending.removeIf(this::tryInsert)) {
                continue;
            }
            LOGGER.error("Couldn't load unlocks: {}", pending);
            break;
        }
    }

    private boolean tryInsert(Holder<PlayerUnlock> unlock) {
        Optional<Holder<PlayerUnlock>> parent = unlock.value().parent();
        List<Holder<PlayerUnlock>> siblings = parent.map(this.nodes::get).orElse(null);
        if (siblings == null && parent.isPresent()) {
            return false; // parent not known yet
        }
        if (siblings != null) {
            siblings.add(unlock);
        }
        this.nodes.put(unlock, new ArrayList<>());
        if (parent.isEmpty()) {
            this.roots.add(unlock);
            if (this.listener != null) {
                this.listener.onAddUnlocksRoot(unlock);
            }
        } else {
            this.tasks.add(unlock);
            if (this.listener != null) {
                this.listener.onAddUnlock(unlock);
            }
        }
        return true;
    }

    public void clear() {
        this.nodes.clear();
        this.roots.clear();
        this.tasks.clear();
        if (this.listener != null) {
            this.listener.onUnlocksCleared();
        }
    }

    public Iterable<Holder<PlayerUnlock>> roots() {
        return this.roots;
    }

    public List<Holder<PlayerUnlock>> getChildren(Holder<PlayerUnlock> unlock) {
        List<Holder<PlayerUnlock>> children = this.nodes.get(unlock);
        return children == null ? List.of() : children;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        if (listener != null) {
            // Late-attached listeners catch up with everything already placed.
            for (Holder<PlayerUnlock> root : this.roots) {
                listener.onAddUnlocksRoot(root);
            }
            for (Holder<PlayerUnlock> task : this.tasks) {
                listener.onAddUnlock(task);
            }
        }
    }

    public interface Listener {
        void onAddUnlocksRoot(Holder<PlayerUnlock> root);

        void onRemoveUnlocksRoot(Holder<PlayerUnlock> root);

        void onAddUnlock(Holder<PlayerUnlock> unlock);

        void onRemoveUnlock(Holder<PlayerUnlock> unlock);

        void onUnlocksCleared();
    }
}
