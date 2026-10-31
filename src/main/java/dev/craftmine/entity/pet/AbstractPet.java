package dev.craftmine.entity.pet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import dev.craftmine.duck.CraftmineDuckBridge;
import dev.craftmine.unlock.PlayerUnlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base of every Craftmine pet companion.
 *
 * <p>Pets are created already-tamed by the unlock tree (never bred or spawned
 * naturally): they spawn invulnerable, follow their owner around, and expose
 * exactly one interaction — petting, which rains hearts as long as the owner
 * bought the {@link PlayerUnlocks#CAN_PET} unlock. Species behavior (goal
 * sets, sounds, variants) lives in the subclasses; the pet-specific effect
 * gating stays in {@code PlayerUnlocks}.</p>
 */
public abstract class AbstractPet extends TamableAnimal {

    /** Squared distance beyond which the pet teleports to its owner instead of walking. */
    private static final double TELEPORT_TO_OWNER_DISTANCE_SQR = 20.0 * 20.0;
    private static final int HEART_PARTICLE_COUNT = 7;

    protected AbstractPet(EntityType<? extends AbstractPet> type, Level level) {
        super(castType(type), level);
        this.setTame(true, false);
        this.setPermanentlyInvulnerable(true);
        // Pets would rather starve than path through powder snow.
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
    }

    /** Erases the {@code ? extends AbstractPet} vs {@code ? extends TamableAnimal} mismatch. */
    @SuppressWarnings("unchecked")
    private static EntityType<? extends TamableAnimal> castType(EntityType<? extends AbstractPet> type) {
        return (EntityType<? extends TamableAnimal>) type;
    }

    /**
     * Teleport-to-owner check: past {@link #TELEPORT_TO_OWNER_DISTANCE_SQR} the
     * FollowOwner goal gives up walking and warps instead.
     */
    @Override
    public boolean shouldTryTeleportToOwner() {
        LivingEntity owner = this.getOwner();
        return owner != null && this.distanceToSqr(owner) >= TELEPORT_TO_OWNER_DISTANCE_SQR;
    }

    /**
     * The common follower brain. Species subclasses may prepend goals but
     * always keep these priorities: swim(1), follow(6), wander(8), idle look(10).
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.FLYING_SPEED, 0.3F)
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    /** Pets are ambient companions, not combat targets — no attack hitbox affordance. */
    @Override
    public boolean isAttackable() {
        return false;
    }

    /** The unlock tree is the only source of pets; there is no breeding path. */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /**
     * Right-clicking a pet always "succeeds"; if the clicker owns the pet and
     * has the petting unlock, hearts burst out. No food, no items, no taming.
     */
    @Override
    @NotNull
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isOwnedBy(player) && CraftmineDuckBridge.isActive(player, PlayerUnlocks.CAN_PET)) {
            this.spawnPettingParticles();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    private void spawnPettingParticles() {
        SimpleParticleType hearts = ParticleTypes.HEART;
        for (int i = 0; i < HEART_PARTICLE_COUNT; i++) {
            double dx = this.random.nextGaussian() * 0.02;
            double dy = this.random.nextGaussian() * 0.02;
            double dz = this.random.nextGaussian() * 0.02;
            this.level().addParticle(hearts, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), dx, dy, dz);
        }
    }
}
