package dev.craftmine.client.screen;

import java.util.List;
import com.google.common.collect.Lists;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.Holder;
import dev.craftmine.duck.CraftmineDisplayInfoDuck;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import dev.craftmine.unlock.PlayerUnlock;
import org.jetbrains.annotations.Nullable;

/**
 * Lays out one unlocks tab's nodes with the Buchheim variation of the
 * Reingold-Tilford tidy-tree algorithm (the same family the vanilla
 * advancement screen uses): one depth-first pass to compute preliminary
 * rows, sibling-contour apportioning with threading to resolve overlaps,
 * then normalization and a final write of (column, row) into each unlock's
 * display info via the {@link CraftmineDisplayInfoDuck} bridge.
 *
 * <p>Terms: {@code mod/change/shift} are the standard aggregate-offset
 * fields of the algorithm; x = tree depth (screen column), y = row.</p>
 */
public class PlayerUnlockTreeNodePosition {

    private final Holder<PlayerUnlock> unlock;
    @Nullable
    private final PlayerUnlockTreeNodePosition parent;
    @Nullable
    private final PlayerUnlockTreeNodePosition previousSibling;
    /** 1-based index among the parent's placed children (used by moveSubtree). */
    private final int childIndex;
    private final List<PlayerUnlockTreeNodePosition> children = Lists.newArrayList();

    /** Nearest common ancestor shortcut for the apportioning walk. */
    private PlayerUnlockTreeNodePosition ancestor;
    /** Thread = synthetic link across a contour gap, replacing a missing child. */
    @Nullable
    private PlayerUnlockTreeNodePosition thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    public PlayerUnlockTreeNodePosition(Holder<PlayerUnlock> unlock, @Nullable PlayerUnlockTreeNodePosition parent,
                                        @Nullable PlayerUnlockTreeNodePosition previousSibling,
                                        ClientPlayerUnlocks unlocks, int childIndex, int depth) {
        this.unlock = unlock;
        this.parent = parent;
        this.previousSibling = previousSibling;
        this.childIndex = childIndex;
        this.ancestor = this;
        this.x = depth;
        this.y = -1.0F;
        PlayerUnlockTreeNodePosition lastChild = null;
        for (Holder<PlayerUnlock> childUnlock : unlocks.getTree().getChildren(unlock)) {
            lastChild = this.addChild(childUnlock, unlocks, lastChild);
        }
    }

    /**
     * Invisible unlocks are skipped but their children bubble up in their
     * place, so hidden branches collapse into their visible parent.
     */
    private PlayerUnlockTreeNodePosition addChild(Holder<PlayerUnlock> childUnlock, ClientPlayerUnlocks unlocks,
                                                  @Nullable PlayerUnlockTreeNodePosition previousSibling) {
        if (unlocks.isVisibleAtAll(childUnlock)) {
            PlayerUnlockTreeNodePosition child = new PlayerUnlockTreeNodePosition(
                childUnlock, this, previousSibling, unlocks, this.children.size() + 1, this.x + 1);
            this.children.add(child);
            return child;
        }
        PlayerUnlockTreeNodePosition last = previousSibling;
        for (Holder<PlayerUnlock> grandchild : unlocks.getTree().getChildren(childUnlock)) {
            last = this.addChild(grandchild, unlocks, last);
        }
        return last;
    }

    /** Post-order pass: preliminary rows + per-node mod offsets. */
    private void firstWalk() {
        if (this.children.isEmpty()) {
            this.y = this.previousSibling != null ? this.previousSibling.y + 1.0F : 0.0F;
            return;
        }
        PlayerUnlockTreeNodePosition previous = null;
        for (PlayerUnlockTreeNodePosition child : this.children) {
            child.firstWalk();
            previous = child.apportion(previous == null ? child : previous);
        }
        this.executeShifts();
        float middle = (this.children.get(0).y + this.children.get(this.children.size() - 1).y) / 2.0F;
        if (this.previousSibling != null) {
            this.y = this.previousSibling.y + 1.0F;
            this.mod = this.y - middle;
        } else {
            this.y = middle;
        }
    }

    /** Pre-order pass: applies accumulated mods, returns the smallest row seen. */
    private float secondWalk(float accumulatedMod, int depth, float minRow) {
        this.y += accumulatedMod;
        this.x = depth;
        if (this.y < minRow) {
            minRow = this.y;
        }
        for (PlayerUnlockTreeNodePosition child : this.children) {
            minRow = child.secondWalk(accumulatedMod + this.mod, depth + 1, minRow);
        }
        return minRow;
    }

    /** Whole-subtree shift to pull everything back to non-negative rows. */
    private void thirdWalk(float delta) {
        this.y += delta;
        for (PlayerUnlockTreeNodePosition child : this.children) {
            child.thirdWalk(delta);
        }
    }

    /** Distributes this node's change/shift aggregates over its children. */
    private void executeShifts() {
        float shift = 0.0F;
        float change = 0.0F;
        for (int i = this.children.size() - 1; i >= 0; i--) {
            PlayerUnlockTreeNodePosition child = this.children.get(i);
            child.y += shift;
            child.mod += shift;
            change += child.change;
            shift += child.shift + change;
        }
    }

    @Nullable
    private PlayerUnlockTreeNodePosition previousOrThread() {
        if (this.thread != null) {
            return this.thread;
        }
        return this.children.isEmpty() ? null : this.children.get(0);
    }

    @Nullable
    private PlayerUnlockTreeNodePosition nextOrThread() {
        if (this.thread != null) {
            return this.thread;
        }
        return this.children.isEmpty() ? null : this.children.get(this.children.size() - 1);
    }

    /**
     * The contour-resolving heart of the algorithm: pushes overlapping
     * subtrees apart, threading across gaps, and returns the new outside
     * contour node for the caller's next apportion step.
     */
    private PlayerUnlockTreeNodePosition apportion(PlayerUnlockTreeNodePosition previousNode) {
        if (this.previousSibling == null) {
            return previousNode;
        }
        PlayerUnlockTreeNodePosition innerRight = this;
        PlayerUnlockTreeNodePosition outerRight = this;
        PlayerUnlockTreeNodePosition innerLeft = this.previousSibling;
        PlayerUnlockTreeNodePosition outerLeft = this.parent.children.get(0);
        float innerRightMod = this.mod;
        float outerRightMod = this.mod;
        float innerLeftMod = innerLeft.mod;
        float outerLeftMod = outerLeft.mod;
        while (innerLeft.nextOrThread() != null && innerRight.previousOrThread() != null) {
            innerLeft = innerLeft.nextOrThread();
            innerRight = innerRight.previousOrThread();
            outerLeft = outerLeft.previousOrThread();
            outerRight = outerRight.nextOrThread();
            outerRight.ancestor = this;
            float separation = innerLeft.y + innerLeftMod - (innerRight.y + innerRightMod) + 1.0F;
            if (separation > 0.0F) {
                innerLeft.getAncestor(this, previousNode).moveSubtree(this, separation);
                innerRightMod += separation;
                outerRightMod += separation;
            }
            innerLeftMod += innerLeft.mod;
            innerRightMod += innerRight.mod;
            outerLeftMod += outerLeft.mod;
            outerRightMod += outerRight.mod;
        }
        if (innerLeft.nextOrThread() != null && outerRight.nextOrThread() == null) {
            // Right contour ran out: thread the left side's remainder onto it.
            outerRight.thread = innerLeft.nextOrThread();
            outerRight.mod += innerLeftMod - outerRightMod;
        } else {
            if (innerRight.previousOrThread() != null && outerLeft.previousOrThread() == null) {
                // Left contour ran out: thread the right side's remainder onto it.
                outerLeft.thread = innerRight.previousOrThread();
                outerLeft.mod += innerRightMod - outerLeftMod;
            }
            previousNode = this;
        }
        return previousNode;
    }

    private void moveSubtree(PlayerUnlockTreeNodePosition pivot, float separation) {
        float subtreeCount = pivot.childIndex - this.childIndex;
        if (subtreeCount != 0.0F) {
            pivot.change -= separation / subtreeCount;
            this.change += separation / subtreeCount;
        }
        pivot.shift += separation;
        pivot.y += separation;
        pivot.mod += separation;
    }

    private PlayerUnlockTreeNodePosition getAncestor(PlayerUnlockTreeNodePosition self, PlayerUnlockTreeNodePosition fallback) {
        if (this.ancestor != null && self.parent.children.contains(this.ancestor)) {
            return this.ancestor;
        }
        return fallback;
    }

    /** Publishes the final (column, row) into each unlock's display info. */
    private void finalizePosition() {
        DisplayInfo display = this.unlock.value().display();
        ((CraftmineDisplayInfoDuck) (Object) display).craftmine$setLocation(this.x, this.y);
        for (PlayerUnlockTreeNodePosition child : this.children) {
            child.finalizePosition();
        }
    }

    public static void run(Holder<PlayerUnlock> root, ClientPlayerUnlocks unlocks) {
        PlayerUnlockTreeNodePosition rootNode = new PlayerUnlockTreeNodePosition(root, null, null, unlocks, 1, 0);
        rootNode.firstWalk();
        float minRow = rootNode.secondWalk(0.0F, 0, rootNode.y);
        if (minRow < 0.0F) {
            rootNode.thirdWalk(-minRow);
        }
        rootNode.finalizePosition();
    }
}
