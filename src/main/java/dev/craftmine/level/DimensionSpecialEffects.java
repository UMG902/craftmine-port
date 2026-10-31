package dev.craftmine.level;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import dev.craftmine.tooltip.CraftmineTooltipProvider;
import net.minecraft.world.phys.Vec3;

public record DimensionSpecialEffects(Optional<Float> cloudLevel, boolean hasGround, Optional<Sky> sky, boolean forceBrightLightmap, boolean constantAmbientLight, FogScaler fogScaler, boolean isAlwaysFoggy, boolean hasSunriseAndSunset) {
    public static final Codec<DimensionSpecialEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.FLOAT.optionalFieldOf("cloud_level").forGetter(DimensionSpecialEffects::cloudLevel), Codec.BOOL.fieldOf("has_ground").forGetter(DimensionSpecialEffects::hasGround), Sky.CODEC.optionalFieldOf("sky_type").forGetter(DimensionSpecialEffects::sky), Codec.BOOL.fieldOf("force_bright_lightmap").forGetter(DimensionSpecialEffects::forceBrightLightmap), Codec.BOOL.fieldOf("constant_ambient_light").forGetter(DimensionSpecialEffects::constantAmbientLight), FogScaler.CODEC.fieldOf("fog_scaler").forGetter(DimensionSpecialEffects::fogScaler), Codec.BOOL.fieldOf("is_always_foggy").forGetter(DimensionSpecialEffects::isAlwaysFoggy), Codec.BOOL.fieldOf("has_sunrise_and_sunset").forGetter(DimensionSpecialEffects::hasSunriseAndSunset)).apply(instance, DimensionSpecialEffects::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionSpecialEffects> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional), DimensionSpecialEffects::cloudLevel, ByteBufCodecs.BOOL, DimensionSpecialEffects::hasGround, Sky.STREAM_CODEC.apply(ByteBufCodecs::optional), DimensionSpecialEffects::sky, ByteBufCodecs.BOOL, DimensionSpecialEffects::forceBrightLightmap, ByteBufCodecs.BOOL, DimensionSpecialEffects::constantAmbientLight, FogScaler.STREAM_CODEC, DimensionSpecialEffects::fogScaler, ByteBufCodecs.BOOL, DimensionSpecialEffects::isAlwaysFoggy, ByteBufCodecs.BOOL, DimensionSpecialEffects::hasSunriseAndSunset, DimensionSpecialEffects::new);
    private static final float SUNRISE_AND_SUNSET_TIMESPAN = 0.4f;

    public DimensionSpecialEffects withSkyType(Optional<Sky> optional) {
        return new DimensionSpecialEffects(this.cloudLevel, this.hasGround, optional, this.forceBrightLightmap, this.constantAmbientLight, this.fogScaler, this.isAlwaysFoggy, this.hasSunriseAndSunset);
    }

    public boolean isSunriseOrSunset(float f) {
        if (!this.hasSunriseAndSunset) {
            return false;
        }
        float f2 = Mth.cos(f * ((float)Math.PI * 2));
        return f2 >= -0.4f && f2 <= 0.4f;
    }

    public int getSunriseOrSunsetColor(float f) {
        if (!this.hasSunriseAndSunset) {
            return 0;
        }
        float f2 = Mth.cos(f * ((float)Math.PI * 2));
        float f3 = f2 / 0.4f * 0.5f + 0.5f;
        float f4 = Mth.square(1.0f - (1.0f - Mth.sin(f3 * (float)Math.PI)) * 0.99f);
        return ARGB.colorFromFloat(f4, f3 * 0.3f + 0.7f, f3 * f3 * 0.7f + 0.2f, 0.2f);
    }

    public float getCloudHeight() {
        return this.cloudLevel.orElse(Float.valueOf(Float.NaN)).floatValue();
    }

    public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
        return this.fogScaler.getBrightnessDependentFogColor(vec3, f);
    }

    public boolean isFoggyAt(int n, int n2) {
        return this.isAlwaysFoggy;
    }

    public static enum FogScaler implements StringRepresentable
    {
        UNSCALED(0, "unscaled"){

            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
                return vec3;
            }
        }
        ,
        OVERWORLD(1, "overworld"){

            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
                return vec3.multiply(f * 0.94f + 0.06f, f * 0.94f + 0.06f, f * 0.91f + 0.09f);
            }
        }
        ,
        END(2, "end"){

            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
                return vec3.scale(0.15f);
            }
        };

        private static final IntFunction<FogScaler> BY_ID;
        public static final Codec<FogScaler> CODEC;
        public static final StreamCodec<ByteBuf, FogScaler> STREAM_CODEC;
        private final int id;
        private final String name;

        FogScaler(int n2, String string2) {
            this.id = n2;
            this.name = string2;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public abstract Vec3 getBrightnessDependentFogColor(Vec3 var1, float var2);

        static {
            BY_ID = ByIdMap.continuous(fogScaler -> fogScaler.id, FogScaler.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
            CODEC = StringRepresentable.fromEnum(FogScaler::values);
            STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, fogScaler -> fogScaler.id);
        }
    }

    public static interface Sky
    extends CraftmineTooltipProvider {
        public static final Codec<Sky> CODEC = SkyType.CODEC.dispatch(Sky::type, skyType -> skyType.codec);
        public static final StreamCodec<RegistryFriendlyByteBuf, Sky> STREAM_CODEC = SkyType.STREAM_CODEC.cast().dispatch(Sky::type, skyType -> skyType.streamCodec);

        public SkyType type();

        public Component displayName();

        @Override
        default public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, @Nullable Player player, ItemStack itemStack) {
            consumer.accept(Component.translatable("sky.tooltip", this.displayName()));
        }
    }

    public static enum SkyType implements StringRepresentable
    {
        OVERWORLD(0, "overworld", OverworldSky.MAP_CODEC, OverworldSky.STREAM_CODEC),
        END(1, "end", EndSky.MAP_CODEC, EndSky.STREAM_CODEC),
        CUBE(2, "cube", CubeSky.MAP_CODEC, CubeSky.STREAM_CODEC),
        PANORAMA(3, "panorama", Panorama.MAP_CODEC, Panorama.STREAM_CODEC),
        CODE(4, "code", CodeSky.MAP_CODEC, CodeSky.STREAM_CODEC),
        /**
         * Task 27: the Dimension Control's "nothing" state as its own sky box
         * value (a black, sky-less sky). The original snapshot stores this as
         * an EMPTY sky in its save storage (Map<DimensionType,Optional<Sky>>
         * with a present-but-empty entry), distinct from an absent key (= the
         * dimension's default sky, "code" on the hub). The port models it as
         * a sentinel Sky so the same tri-state flows through the SkyData
         * codec, the set-sky packet and the client renderer. Appended LAST so
         * the wire ids of the five original sky types stay 0-4.
         */
        NOTHING(5, "nothing", NothingSky.MAP_CODEC, NothingSky.STREAM_CODEC);

        private static final IntFunction<SkyType> BY_ID;
        public static final Codec<SkyType> CODEC;
        public static final StreamCodec<RegistryFriendlyByteBuf, SkyType> STREAM_CODEC;
        private final int id;
        private final String name;
        final MapCodec<? extends Sky> codec;
        final StreamCodec<? super RegistryFriendlyByteBuf, ? extends Sky> streamCodec;

        private <T extends Sky> SkyType(int n2, String string2, MapCodec<T> mapCodec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
            this.id = n2;
            this.name = string2;
            this.codec = mapCodec;
            this.streamCodec = streamCodec;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        static {
            BY_ID = ByIdMap.continuous(skyType -> skyType.id, SkyType.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
            CODEC = StringRepresentable.fromEnum(SkyType::values);
            STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, skyType -> skyType.id).cast();
        }
    }

    public record CubeSky(Identifier textureId, int repeats, float size, Component title) implements Sky
    {
        public static final MapCodec<CubeSky> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(Identifier.CODEC.fieldOf("texture").forGetter(CubeSky::textureId), ExtraCodecs.POSITIVE_INT.fieldOf("repeats").forGetter(CubeSky::repeats), Codec.FLOAT.fieldOf("size").forGetter(CubeSky::size), ComponentSerialization.CODEC.fieldOf("name").forGetter(CubeSky::title)).apply(instance, CubeSky::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, CubeSky> STREAM_CODEC = StreamCodec.composite(Identifier.STREAM_CODEC, CubeSky::textureId, ByteBufCodecs.VAR_INT, CubeSky::repeats, ByteBufCodecs.FLOAT, CubeSky::size, ComponentSerialization.TRUSTED_STREAM_CODEC, CubeSky::title, CubeSky::new);

        @Override
        public SkyType type() {
            return SkyType.CUBE;
        }

        @Override
        public Component displayName() {
            return this.title;
        }
    }

    public static class CodeSky
    implements Sky {
        public static final Component NAME = Component.translatable("sky.code");
        public static final CodeSky INSTANCE = new CodeSky();
        public static final MapCodec<CodeSky> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, CodeSky> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public SkyType type() {
            return SkyType.CODE;
        }

        @Override
        public Component displayName() {
            return NAME;
        }
    }

    /**
     * The "nothing" sky: the snapshot renders an empty Dimension Control slot
     * as a fully BLACK sky (the fork's sky pass skips all sky content - no
     * disc, no celestial bodies, no sunrise - and clears the target to zero).
     * Sentinel singleton; the renderer cancels the vanilla sky pass and
     * clears the target black when this is the active sky (task 27).
     */
    public static class NothingSky
    implements Sky {
        public static final Component NAME = Component.translatable("sky.nothing");
        public static final NothingSky INSTANCE = new NothingSky();
        public static final MapCodec<NothingSky> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, NothingSky> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public SkyType type() {
            return SkyType.NOTHING;
        }

        @Override
        public Component displayName() {
            return NAME;
        }
    }

    public static class Panorama
    implements Sky {
        public static final Component NAME = Component.translatable("sky.panorama");
        public static final Panorama INSTANCE = new Panorama();
        public static final MapCodec<Panorama> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, Panorama> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public SkyType type() {
            return SkyType.PANORAMA;
        }

        @Override
        public Component displayName() {
            return NAME;
        }
    }

    public static class EndSky
    implements Sky {
        public static final Component NAME = Component.translatable("sky.end");
        public static final EndSky INSTANCE = new EndSky();
        public static final MapCodec<EndSky> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, EndSky> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public SkyType type() {
            return SkyType.END;
        }

        @Override
        public Component displayName() {
            return NAME;
        }
    }

    public static class OverworldSky
    implements Sky {
        public static final OverworldSky INSTANCE = new OverworldSky();
        public static final MapCodec<OverworldSky> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, OverworldSky> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final Component NAME = Component.translatable("sky.overworld");

        @Override
        public SkyType type() {
            return SkyType.OVERWORLD;
        }

        @Override
        public Component displayName() {
            return NAME;
        }
    }
}

