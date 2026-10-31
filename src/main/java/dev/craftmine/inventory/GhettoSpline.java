package dev.craftmine.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * Minimal closed-spline walker over the minecrafter's spiral slot positions:
 * the input points plus their reverse form a closed loop; {@link #interpolate}
 * walks it by normalized arc length so slot animations glide around the spiral.
 */
public class GhettoSpline {
    private final List<Entry> entries;
    private final double totalLength;

    public GhettoSpline(List<Vec2> points) {
        List<Vec2> loop = new ArrayList<>(points);
        loop.addAll(points.reversed());
        this.entries = new ArrayList<>(loop.size());
        double length = 0.0;
        for (int i = 0; i < loop.size(); i++) {
            Vec2 current = loop.get(i);
            Vec2 next = i + 1 < loop.size() ? loop.get(i + 1) : loop.get(i);
            double distance = Mth.sqrt(current.distanceToSqr(next));
            this.entries.add(new Entry(current, distance));
            length += distance;
        }
        this.totalLength = length;
    }

    /** Position at {@code t} along the closed loop (t wraps modulo 1). */
    public Vec2 interpolate(float t) {
        double remaining = this.totalLength * (t % 1.0);
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            remaining -= entry.distance();
            if (remaining > 0.0) {
                continue;
            }
            Entry next = i + 1 < this.entries.size() ? this.entries.get(i + 1) : this.entries.get(i - 1);
            Vec2 direction = next.pos().add(entry.pos().negated()).normalized();
            float overshoot = (float)(entry.distance() + remaining);
            return entry.pos().add(direction.scale(overshoot));
        }
        return this.entries.getFirst().pos();
    }

    private record Entry(Vec2 pos, double distance) {}
}
