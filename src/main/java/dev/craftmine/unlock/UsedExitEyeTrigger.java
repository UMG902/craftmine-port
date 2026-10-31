package dev.craftmine.unlock;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Criterion fired by {@link dev.craftmine.unlock.UnlockCondition} plumbing when a player
 * uses the exit eye: matches by squared horizontal distance travelled from the eye's block,
 * so triggers can demand "exited at least N blocks away".
 */
public class UsedExitEyeTrigger extends SimpleCriterionTrigger<UsedExitEyeTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos exitPos) {
        double dx = player.getX() - exitPos.getX();
        double dz = player.getZ() - exitPos.getZ();
        this.trigger(player, instance -> instance.matches(dx * dx + dz * dz));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player, MinMaxBounds.Doubles distance)
        implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance", MinMaxBounds.Doubles.ANY).forGetter(TriggerInstance::distance)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(double squaredDistance) {
            return this.distance.matchesSqr(squaredDistance);
        }
    }
}
