package dev.craftmine.server;

import com.mojang.serialization.Codec;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public record GeneratedMarkerMetadataSection() {
    public static final Codec<GeneratedMarkerMetadataSection> CODEC = com.mojang.serialization.MapCodec.unit(GeneratedMarkerMetadataSection::new).codec();
    public static final MetadataSectionType<GeneratedMarkerMetadataSection> TYPE = new MetadataSectionType<GeneratedMarkerMetadataSection>("generated", CODEC);
}

