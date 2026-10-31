package dev.craftmine.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import dev.craftmine.duck.CraftmineDisplayInfoDuck;
import dev.craftmine.unlock.ClientPlayerUnlocks;
import dev.craftmine.unlock.PlayerUnlock;

/**
 * Radial fallback layout for a special unlocks tab (used for tabs whose
 * subtrees don't take the tidy-tree path): places every visible unlock on
 * a ring around its parent, then relaxes overlapping nodes by squeezing
 * each parent's angular window until no pair of unrelated nodes is closer
 * than the collision radius. Positions are normalized to (0,0) at the end
 * and written through the {@link CraftmineDisplayInfoDuck} bridge.
 */
public class RadialLayerLayout {

    /** Ring spacing per layer, in unlock-widget units. */
    private static final float RING_DISTANCE = 1.1F;
    /** Fraction of the angular window squeezed per relaxation step. */
    private static final float ADJUSTMENT_FRACTION = 0.025F;
    /** Hard cap on a single squeeze, in radians (10 degrees). */
    private static final float MAX_ADJUSTMENT_ANGLE = 0.17453294F;
    /** Below this per-child angle the parent is considered too crowded. */
    private static final float MIN_ANGLE_PER_CHILD = 0.08726647F;
    /** Node pairs closer than this squared distance trigger a squeeze. */
    private static final float COLLISION_DISTANCE_SQR = 1.21F;
    private static final int MAX_RELAX_ITERATIONS = 50;

    private final Map<Holder<PlayerUnlock>, RingData> ringData = new HashMap<>();
    private final Holder<PlayerUnlock> root;
    private final PlayerUnlocksTree tree;
    private final ClientPlayerUnlocks unlocks;

    private RadialLayerLayout(PlayerUnlocksTree tree, Holder<PlayerUnlock> root, ClientPlayerUnlocks unlocks) {
        this.root = root;
        this.tree = tree;
        this.unlocks = unlocks;
        this.setupRingData(root, 0);
    }

    /** Ring radius grows when a layer must fit many children around the circle. */
    private static float ringDistance(float fullCircle, int childCount) {
        float distance = RING_DISTANCE * Math.max(1.0F, (float) (childCount + 1) / fullCircle);
        if (Float.isNaN(distance)) {
            throw new IllegalStateException("Ring distance blew up: childCount=" + childCount);
        }
        return distance;
    }

    private RingData setupRingData(Holder<PlayerUnlock> unlock, int layer) {
        RingData data = new RingData();
        data.layer = layer;
        data.startAngle = (float) -Math.PI;
        data.endAngle = (float) Math.PI;
        List<Holder<PlayerUnlock>> children = this.tree.getChildren(unlock);
        data.childCount = (int) children.stream().filter(this.unlocks::isVisibleAtAll).count();
        data.distance = ringDistance((float) Math.PI * 2, data.childCount);
        this.ringData.put(unlock, data);
        int visibleIndex = 0;
        for (Holder<PlayerUnlock> child : children) {
            if (!this.unlocks.isVisibleAtAll(child)) {
                continue;
            }
            RingData childData = this.setupRingData(child, layer + 1);
            childData.index = visibleIndex++;
            // A crowded subtree pushes its ancestors' rings outward.
            if (childData.distance > data.distance) {
                data.distance = childData.distance;
            }
        }
        return data;
    }

    private void apply() {
        this.apply(this.root);
    }

    /**
     * Writes positions depth-first: children sit on a ring around the
     * parent, rotated so the ring opens away from the grandparent.
     */
    private void apply(Holder<PlayerUnlock> unlock) {
        DisplayInfo display = unlock.value().display();
        Optional<Holder<PlayerUnlock>> parent = unlock.value().parent();
        RingData data = this.ringData.get(unlock);
        if (parent.isPresent()) {
            Holder<PlayerUnlock> parentUnlock = parent.get();
            RingData parentData = this.ringData.get(parentUnlock);
            DisplayInfo parentDisplay = parentUnlock.value().display();
            float parentX = ((CraftmineDisplayInfoDuck) (Object) parentDisplay).craftmine$getX();
            float parentY = ((CraftmineDisplayInfoDuck) (Object) parentDisplay).craftmine$getY();
            int siblings = parentData.childCount;
            Vec2 awayFromGrandparent;
            int slotCount;
            if (parentUnlock.value().parent().isPresent()) {
                awayFromGrandparent = getCoordinates(parentUnlock)
                    .add(getCoordinates(parentUnlock.value().parent().get()).negated())
                    .normalized();
                slotCount = siblings + 1;
            } else {
                awayFromGrandparent = new Vec2(1.0F, 0.0F);
                slotCount = siblings;
            }
            float ringTilt = (float) Math.acos(awayFromGrandparent.x) * (awayFromGrandparent.y > 0.0F ? -1 : 1);
            float angleSpan = parentData.endAngle - parentData.startAngle;
            float angle = siblings == 1
                ? ringTilt + parentData.startAngle + angleSpan / 2.0F
                : ringTilt + parentData.startAngle + (float) (data.index + 1) * angleSpan / (float) slotCount;
            unwrap(display).craftmine$setLocation(
                parentX + Mth.cos(angle) * data.distance,
                parentY - Mth.sin(angle) * data.distance);
        } else {
            unwrap(display).craftmine$setLocation(0.0F, 0.0F);
        }
        for (Holder<PlayerUnlock> child : this.tree.getChildren(unlock)) {
            if (this.unlocks.isVisibleAtAll(child)) {
                this.apply(child);
            }
        }
    }

    private static Vec2 getCoordinates(Holder<PlayerUnlock> unlock) {
        DisplayInfo display = unlock.value().display();
        CraftmineDisplayInfoDuck duck = (CraftmineDisplayInfoDuck) (Object) display;
        return new Vec2(duck.craftmine$getX(), duck.craftmine$getY());
    }

    private static CraftmineDisplayInfoDuck unwrap(DisplayInfo display) {
        return (CraftmineDisplayInfoDuck) (Object) display;
    }

    /**
     * One relaxation pass: for every visible child of this node's parent,
     * check all placed nodes for overlap; squeeze on the first hit. Done
     * flag survives only when nothing moved anywhere in the subtree.
     */
    private boolean iterate(Holder<PlayerUnlock> unlock) {
        RingData data = this.ringData.get(unlock);
        data.done = true;
        Optional<Holder<PlayerUnlock>> parent = unlock.value().parent();
        if (parent.isPresent()) {
            siblings:
            for (Holder<PlayerUnlock> sibling : this.tree.getChildren(parent.get())) {
                if (!this.unlocks.isVisibleAtAll(sibling)) {
                    continue;
                }
                Vec2 siblingPos = getCoordinates(sibling);
                for (Map.Entry<Holder<PlayerUnlock>, RingData> entry : this.ringData.entrySet()) {
                    Holder<PlayerUnlock> other = entry.getKey();
                    // Skip self pairs and direct parent/child pairs (they are allowed to be close).
                    if (sibling == other
                        || sibling == other.value().parent().orElse(null)
                        || unlock == other
                        || unlock == other.value().parent().orElse(null)) {
                        continue;
                    }
                    Vec2 otherPos = getCoordinates(other);
                    if (siblingPos.distanceToSqr(otherPos) > COLLISION_DISTANCE_SQR) {
                        continue;
                    }
                    data.done = false;
                    this.squeeze(unlock, data, otherPos);
                    continue siblings;
                }
            }
        }
        for (Holder<PlayerUnlock> child : this.tree.getChildren(unlock)) {
            if (this.unlocks.isVisibleAtAll(child) && !this.iterate(child)) {
                data.done = false;
            }
        }
        return data.done;
    }

    /**
     * Narrows this node's angular window on the side the colliding node is
     * on, then recomputes the ring distance. Overcrowded windows bubble the
     * squeeze up to the first ancestor with room.
     */
    private void squeeze(Holder<PlayerUnlock> unlock, RingData data, Vec2 collidingPos) {
        Optional<Holder<PlayerUnlock>> parent = unlock.value().parent();
        if (parent.isEmpty()) {
            return;
        }
        float window = data.endAngle - data.startAngle;
        float adjustment = Math.min(window * ADJUSTMENT_FRACTION, MAX_ADJUSTMENT_ANGLE);
        if ((window - adjustment) / (float) data.childCount < MIN_ANGLE_PER_CHILD) {
            // Too crowded: find the nearest ancestor with more than one child and squeeze it instead.
            Optional<Holder<PlayerUnlock>> ancestor = parent;
            Optional<RingData> ancestorData = ancestor.map(this.ringData::get);
            while (ancestor.isPresent() && ancestorData.isPresent() && ancestorData.get().childCount == 1) {
                ancestor = ancestor.get().value().parent();
                ancestorData = ancestor.map(this.ringData::get);
            }
            if (ancestor.isPresent() && ancestor.get().value().parent().isPresent()) {
                this.squeeze(ancestor.get(), ancestorData.get(), collidingPos);
                return;
            }
        }
        Vec2 myPos = getCoordinates(unlock);
        Vec2 toParent = myPos.add(getCoordinates(parent.get()).negated()).normalized();
        Vec2 toCollider = collidingPos.add(myPos.negated()).normalized();
        // Cross product sign: which side of the parent ray the collider is on.
        float side = new Vec2(toParent.y, -toParent.x).dot(toCollider);
        if (side < 0.0F) {
            data.startAngle += adjustment;
        } else {
            data.endAngle -= adjustment;
        }
        data.distance = ringDistance(window, data.childCount);
    }

    private void iterate() {
        this.iterate(this.root);
    }

    private boolean isDone() {
        return this.ringData.get(this.root).done;
    }

    /** Shifts all positions so the bounding box starts at (0,0). */
    private void finalizePositions() {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (Holder<PlayerUnlock> unlock : this.ringData.keySet()) {
            DisplayInfo display = unlock.value().display();
            minX = Math.min(minX, unwrap(display).craftmine$getX());
            minY = Math.min(minY, unwrap(display).craftmine$getY());
        }
        for (Holder<PlayerUnlock> unlock : this.ringData.keySet()) {
            DisplayInfo display = unlock.value().display();
            CraftmineDisplayInfoDuck duck = unwrap(display);
            duck.craftmine$setLocation(duck.craftmine$getX() - minX, duck.craftmine$getY() - minY);
        }
    }

    public static void run(PlayerUnlocksTree tree, Holder<PlayerUnlock> root, ClientPlayerUnlocks unlocks) {
        RadialLayerLayout layout = new RadialLayerLayout(tree, root, unlocks);
        layout.apply();
        for (int i = 0; i < MAX_RELAX_ITERATIONS && !layout.isDone(); i++) {
            layout.iterate();
            layout.apply();
        }
        layout.finalizePositions();
    }

    /** Per-unlock ring placement state for the relaxation loop. */
    class RingData {
        public int layer;
        public float startAngle;
        public float endAngle;
        public int index;
        public int childCount;
        public float distance;
        public boolean done;
    }
}
