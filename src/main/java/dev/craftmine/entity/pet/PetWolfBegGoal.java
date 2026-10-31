package dev.craftmine.entity.pet;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The pet wolf's beg routine: while a nearby player holds a bone or wolf
 * food, the wolf stares at them with the tilted-head "interested" animation
 * for a short randomized window. Drives {@link PetWolf#setIsInterested(boolean)}.
 */
public class PetWolfBegGoal extends Goal {

    private static final int MIN_BEG_TICKS = 40;
    private static final int BEG_TICKS_VARIANCE = 40;

    private final PetWolf wolf;
    private final float lookDistance;
    private final TargetingConditions begTargeting;
    @Nullable
    private Player beggingFrom;
    private int ticksLeft;

    public PetWolfBegGoal(PetWolf wolf, float lookDistance) {
        this.wolf = wolf;
        this.lookDistance = lookDistance;
        this.begTargeting = TargetingConditions.forNonCombat().range(lookDistance);
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.beggingFrom = this.serverLevel().getNearestPlayer(this.begTargeting, this.wolf);
        return this.beggingFrom != null && this.isHoldingTemptation(this.beggingFrom);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.beggingFrom == null || !this.beggingFrom.isAlive()) {
            return false;
        }
        if (this.wolf.distanceToSqr(this.beggingFrom) > (double) (this.lookDistance * this.lookDistance)) {
            return false;
        }
        return this.ticksLeft > 0 && this.isHoldingTemptation(this.beggingFrom);
    }

    @Override
    public void start() {
        this.wolf.setIsInterested(true);
        this.ticksLeft = this.adjustedTickDelay(MIN_BEG_TICKS + this.wolf.getRandom().nextInt(BEG_TICKS_VARIANCE));
    }

    @Override
    public void stop() {
        this.wolf.setIsInterested(false);
        this.beggingFrom = null;
    }

    @Override
    public void tick() {
        Player player = this.beggingFrom;
        if (player == null) {
            return;
        }
        this.wolf.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 10.0F, this.wolf.getMaxHeadXRot());
        this.ticksLeft--;
    }

    /** A bone in either hand — or any item the wolf considers food — starts the beg. */
    private boolean isHoldingTemptation(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.BONE) || this.wolf.isFood(held)) {
                return true;
            }
        }
        return false;
    }

    private ServerLevel serverLevel() {
        return (ServerLevel) this.wolf.level();
    }
}
