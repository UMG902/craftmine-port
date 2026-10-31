package dev.craftmine.unlock;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import dev.craftmine.registry.CraftmineRegistries;
import net.minecraft.core.Holder;

/**
 * Shared eligibility rules for the unlock system, used by both sides:
 * {@link ServerPlayerUnlocks} (authoritative, saved per player) and
 * {@link ClientPlayerUnlocks} (client mirror fed by packets).
 *
 * <p>Keeping the two computations in one place guarantees the client tree
 * renders exactly what the server would activate.</p>
 */
public final class UnlockEligibility {
    private UnlockEligibility() {}

    /**
     * "Obtained AND allowed to take effect": the unlock must be obtained, enabled by the
     * unoriginal-features config, and - for nodes gated by {@link PlayerUnlock#requires()} -
     * that gate unlock must itself be eligible (recursively).
     *
     * @param isObtained lookup answering "has this unlock been purchased"
     */
    public static boolean isEligible(Holder<PlayerUnlock> holder, Predicate<Holder<PlayerUnlock>> isObtained) {
        if (!isObtained.test(holder) || !holder.value().isEnabled()) {
            return false;
        }
        Optional<Holder<PlayerUnlock>> requires = holder.value().requires();
        return requires.isEmpty() || isEligible(requires.get(), isObtained);
    }

    /**
     * The active set: every obtained eligible unlock, minus unlocks disabled by another
     * active unlock, minus exclusive unlocks whose exclusive slot is not currently theirs.
     *
     * @param isActiveExclusive lookup answering "does this exclusive unlock currently hold its slot"
     */
    public static Set<Holder<PlayerUnlock>> computeActive(Map<Holder<PlayerUnlock>, Boolean> obtained,
        Predicate<Holder<PlayerUnlock>> isEligible, Predicate<Holder<PlayerUnlock>> isActiveExclusive) {
        Set<Holder<PlayerUnlock>> active = new HashSet<>();
        obtained.forEach((holder, isUnlocked) -> {
            if (isUnlocked && isEligible.test(holder)) {
                active.add(holder);
            }
        });
        for (Holder<PlayerUnlock> holder : Set.copyOf(active)) {
            active.removeAll(holder.value().disables());
            if (!holder.value().exclusiveKey().isEmpty() && !isActiveExclusive.test(holder)) {
                active.remove(holder);
            }
        }
        return active;
    }

    /**
     * True when any currently unlocked unlock hangs below this one: a tree child, or an
     * extra unlock gated on it via {@link PlayerUnlock#requires()}.
     */
    public static boolean hasUnlockedChild(Holder<PlayerUnlock> holder, Predicate<Holder<PlayerUnlock>> isObtained) {
        for (Holder<PlayerUnlock> other : CraftmineRegistries.PLAYER_UNLOCK.listElements().toList()) {
            if (!isObtained.test(other)) {
                continue;
            }
            Optional<Holder<PlayerUnlock>> parent = other.value().parent();
            Optional<Holder<PlayerUnlock>> requires = other.value().requires();
            if ((parent.isPresent() && parent.get().equals(holder)) || (requires.isPresent() && requires.get().equals(holder))) {
                return true;
            }
        }
        return false;
    }
}
