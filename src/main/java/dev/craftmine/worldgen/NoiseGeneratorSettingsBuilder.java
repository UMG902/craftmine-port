/*
 * Ported from 25w14craftmine (NoiseGeneratorSettings.Builder + salt field).
 * Adapted to the 26.3 NoiseGeneratorSettings record component set.
 */
package dev.craftmine.worldgen;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SpawnTargetPoint;
import net.minecraft.world.level.levelgen.Aquifer;

public class NoiseGeneratorSettingsBuilder {
    private NoiseSettings noiseSettings;
    private BlockState defaultBlock;
    private BlockState defaultFluid;
    private NoiseRouter noiseRouter;
    private Holder<MaterialRule> materialRule;
    private List<SpawnTargetPoint> spawnTarget;
    private int seaLevel;
    private boolean disableMobGeneration;
    private Optional<Aquifer.Config> aquifers;
    private boolean useLegacyRandomSource;
    private NoiseGeneratorSettings.DebugFunctions debugFunctions = NoiseGeneratorSettings.DebugFunctions.EMPTY;
    private long salt;

    public NoiseGeneratorSettingsBuilder(NoiseGeneratorSettings settings) {
        this.noiseSettings = settings.noiseSettings();
        this.defaultBlock = settings.defaultBlock();
        this.defaultFluid = settings.defaultFluid();
        this.noiseRouter = settings.noiseRouter();
        this.materialRule = settings.materialRule();
        this.spawnTarget = settings.spawnTarget();
        this.seaLevel = settings.seaLevel();
        this.disableMobGeneration = settings.disableMobGeneration();
        this.aquifers = settings.aquifers();
        this.useLegacyRandomSource = settings.useLegacyRandomSource();
        this.debugFunctions = settings.debugFunctions();
    }

    public NoiseGeneratorSettingsBuilder noiseSettings(NoiseSettings noiseSettings) {
        this.noiseSettings = noiseSettings;
        return this;
    }

    public NoiseGeneratorSettingsBuilder defaultBlock(BlockState blockState) {
        this.defaultBlock = blockState;
        return this;
    }

    public NoiseGeneratorSettingsBuilder defaultFluid(BlockState blockState) {
        this.defaultFluid = blockState;
        return this;
    }

    public NoiseGeneratorSettingsBuilder noiseRouter(NoiseRouter noiseRouter) {
        this.noiseRouter = noiseRouter;
        return this;
    }

    /** Current router value - lets effect configurers rebuild it from the base settings. */
    public NoiseRouter noiseRouter() {
        return this.noiseRouter;
    }

    public NoiseGeneratorSettingsBuilder surfaceRule(Holder<MaterialRule> materialRule) {
        this.materialRule = materialRule;
        return this;
    }

    public NoiseGeneratorSettingsBuilder modifySurfaceRule(UnaryOperator<MaterialRule> unaryOperator) {
        this.materialRule = Holder.direct(unaryOperator.apply(this.materialRule.value()));
        return this;
    }

    public NoiseGeneratorSettingsBuilder spawnTarget(List<SpawnTargetPoint> list) {
        this.spawnTarget = list;
        return this;
    }

    public NoiseGeneratorSettingsBuilder seaLevel(int n) {
        this.seaLevel = n;
        return this;
    }

    public int seaLevel() {
        return this.seaLevel;
    }

    public NoiseGeneratorSettingsBuilder disableMobGeneration(boolean bl) {
        this.disableMobGeneration = bl;
        return this;
    }

    public NoiseGeneratorSettingsBuilder aquifersEnabled(boolean bl) {
        // 26.3 aquifer configs are embedded in the noise router construction;
        // kept for API parity (no-op).
        return this;
    }

    public NoiseGeneratorSettingsBuilder oreVeinsEnabled(boolean bl) {
        // 26.3 folded ore veins into the material rules; kept for API parity (no-op).
        return this;
    }

    public NoiseGeneratorSettingsBuilder useLegacyRandomSource(boolean bl) {
        this.useLegacyRandomSource = bl;
        return this;
    }

    public NoiseGeneratorSettingsBuilder salt(long l) {
        this.salt = l;
        return this;
    }

    public long salt() {
        return this.salt;
    }

    public NoiseGeneratorSettings create() {
        return new NoiseGeneratorSettings(this.noiseSettings, this.defaultBlock, this.defaultFluid,
            this.noiseRouter, this.materialRule, this.spawnTarget, this.seaLevel,
            this.disableMobGeneration, this.aquifers, this.useLegacyRandomSource, this.debugFunctions);
    }
}
