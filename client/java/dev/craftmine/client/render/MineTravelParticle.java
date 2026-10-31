package dev.craftmine.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The mine-travel swirl particle: a shrinking glowing dot that arcs from
 * the spawn point toward the travelling block, in blue or purple. Follows
 * the vanilla portal particle's arc math (ease-out along the start->target
 * line with a vertical bob), restated as named phases.
 *
 * <p>26.3 particle API: TextureSheetParticle is gone (SingleQuadParticle
 * takes a TextureAtlasSprite in its ctor), the render type is a Layer
 * record and ParticleProvider.createParticle gained a RandomSource
 * parameter.</p>
 */
public class MineTravelParticle extends SingleQuadParticle {

    private static final float QUAD_SIZE_BASE = 0.1F;
    private static final float QUAD_SIZE_VARIANCE = 0.4F;
    private static final float QUAD_SIZE_MIN_FACTOR = 0.2F;
    private static final float BRIGHTNESS_VARIANCE = 0.6F;
    private static final float BRIGHTNESS_MIN_FACTOR = 0.4F;
    private static final int LIFETIME_BASE = 50;
    private static final int LIFETIME_VARIANCE = 20;
    /** Ceiling for the self-glow the particle adds to its own light. */
    private static final int MAX_GLOW = 240;

    private final double startX;
    private final double startY;
    private final double startZ;

    protected MineTravelParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.quadSize = QUAD_SIZE_BASE * (this.random.nextFloat() * QUAD_SIZE_VARIANCE + QUAD_SIZE_MIN_FACTOR);
        float brightness = this.random.nextFloat() * BRIGHTNESS_VARIANCE + BRIGHTNESS_MIN_FACTOR;
        if (this.random.nextBoolean()) {
            this.rCol = brightness * 0.2F;
            this.gCol = brightness * 0.3F;
            this.bCol = brightness;      // blue phase
        } else {
            this.rCol = brightness * 0.2F;
            this.gCol = brightness;
            this.bCol = brightness * 0.2F; // purple phase
        }
        this.lifetime = (int) (Math.random() * LIFETIME_VARIANCE) + LIFETIME_BASE;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
        this.setLocationFromBoundingbox();
    }

    /** Shrinks toward the end of life with an ease-out curve. */
    @Override
    public float getQuadSize(float partialTick) {
        float lifeFraction = ((float) this.age + partialTick) / (float) this.lifetime;
        float eased = 1.0F - (1.0F - lifeFraction) * (1.0F - lifeFraction);
        return this.quadSize * eased;
    }

    /** Self-luminous: the block-light half of the light coordinate brightens over the lifetime. */
    @Override
    protected int getLightCoords(float partialTick) {
        int light = super.getLightCoords(partialTick);
        float lifeFraction = (float) this.age / (float) this.lifetime;
        float glow = lifeFraction * lifeFraction * lifeFraction * lifeFraction * 15.0F * 16.0F;
        int blockLight = (light >> 16 & 0xFF) + (int) glow;
        if (blockLight > MAX_GLOW) {
            blockLight = MAX_GLOW;
        }
        return (light & 0x55) | blockLight << 16;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // Position eases from the start point along the delta, with a bob
        // that starts at 1 and flattens out (the "sucked in" feel).
        float raw = (float) this.age / (float) this.lifetime;
        float eased = 1.0F - (raw * raw * 2.0F - raw);
        this.x = this.startX + this.xd * eased;
        this.y = this.startY + this.yd * eased + (1.0F - raw);
        this.z = this.startZ + this.zd * eased;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz, RandomSource random) {
            return new MineTravelParticle(level, x, y, z, dx, dy, dz, this.sprites.get(random));
        }
    }
}
