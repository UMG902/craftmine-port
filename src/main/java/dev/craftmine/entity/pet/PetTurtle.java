package dev.craftmine.entity.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.craftmine.entity.pet.AbstractPet;
import org.jetbrains.annotations.Nullable;

/**
 * The pet turtle companion: a slow amphibious follower that is most at home
 * in water (swim-boosted travel, water-prefering pathing) and shambles on
 * land. Movement borrows the vanilla turtle's drag-y swim control; land
 * strolls are deliberately rare (1-in-100 idle rolls).
 */
public class PetTurtle extends AbstractPet {

    /** Idle land-stroll chance denominator (vanilla turtle tuning). */
    private static final int LAND_STROLL_INTERVAL = 100;
    private static final int AMBIENT_SOUND_INTERVAL = 200;

    public PetTurtle(EntityType<? extends PetTurtle> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.DOOR_IRON_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_OPEN, -1.0F);
        this.moveControl = new TurtleSwimControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(8, new LandStrollGoal(this, 1.0, LAND_STROLL_INTERVAL));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // ------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------

    @Override
    public int getAmbientSoundInterval() {
        return AMBIENT_SOUND_INTERVAL;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        if (!this.isInWater() && this.onGround() && !this.isBaby()) {
            return SoundEvents.TURTLE_AMBIENT_LAND;
        }
        return super.getAmbientSound();
    }

    @Override
    protected void playSwimSound(float volume) {
        super.playSwimSound(volume * 1.5F);
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.TURTLE_SHAMBLE, 0.15F, 1.0F);
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    // ------------------------------------------------------------------
    // Movement / pathing
    // ------------------------------------------------------------------

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new TurtlePathNavigation(this, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return 10.0F;
        }
        if (TurtleEggBlock.onSand(level, pos)) {
            return 10.0F;
        }
        return level.getPathfindingCostFromLightLevels(pos);
    }

    /** Water travel with a gentle sink when idle — the vanilla turtle drift. */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater()) {
            this.moveRelative(0.1F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
            }
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Quirk kept 1:1 from the fork: "breeding" yields a real vanilla turtle. Unreachable in play. */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityTypes.TURTLE.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.TURTLE_FOOD);
    }

    // ------------------------------------------------------------------
    // Turtle control rig
    // ------------------------------------------------------------------

    /** Drag-heavy swim control lifted from the vanilla turtle. */
    static class TurtleSwimControl extends MoveControl {
        private final PetTurtle turtle;

        TurtleSwimControl(PetTurtle turtle) {
            super(turtle);
            this.turtle = turtle;
        }

        /** Babies paddle in place slower; grounded turtles halve their speed. */
        private void updateSpeed() {
            if (this.turtle.isInWater()) {
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0, 0.005, 0.0));
                if (this.turtle.isBaby()) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 3.0F, 0.06F));
                }
            } else if (this.turtle.onGround()) {
                this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.06F));
            }
        }

        @Override
        public void tick() {
            this.updateSpeed();
            if (this.operation != MoveControl.Operation.MOVE_TO || this.turtle.getNavigation().isDone()) {
                this.turtle.setSpeed(0.0F);
                return;
            }
            double dx = this.wantedX - this.turtle.getX();
            double dy = this.wantedY - this.turtle.getY();
            double dz = this.wantedZ - this.turtle.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1.0E-5) {
                this.turtle.setSpeed(0.0F);
                return;
            }
            float targetYRot = (float) (Mth.atan2(dz, dx) * 57.2957763671875) - 90.0F;
            this.turtle.setYRot(this.rotlerp(this.turtle.getYRot(), targetYRot, 90.0F));
            this.turtle.yBodyRot = this.turtle.getYRot();
            float targetSpeed = (float) (this.speedModifier * this.turtle.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.turtle.setSpeed(Mth.lerp(0.125F, this.turtle.getSpeed(), targetSpeed));
            double ascent = (double) this.turtle.getSpeed() * (dy / distance) * 0.1;
            this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0, ascent, 0.0));
        }
    }

    /** Water turtles never idle-stroll on their own; land ones use the vanilla interval. */
    static class LandStrollGoal extends RandomStrollGoal {
        LandStrollGoal(PetTurtle turtle, double speed, int interval) {
            super(turtle, speed, interval);
        }

        @Override
        public boolean canUse() {
            return !this.mob.isInWater() && super.canUse();
        }
    }

    /** Destinations must be swimmable water or solid ground below. */
    static class TurtlePathNavigation extends AmphibiousPathNavigation {
        TurtlePathNavigation(PetTurtle turtle, Level level) {
            super(turtle, level);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            return !this.level.getBlockState(pos.below()).isAir();
        }
    }
}
