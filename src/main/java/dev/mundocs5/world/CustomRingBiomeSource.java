package dev.mundocs5.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

/**
 * Wrapper biome source for a ring-shaped overworld centered on (0, 0).
 */
public class CustomRingBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);

    public static final MapCodec<CustomRingBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(CustomRingBiomeSource::vanillaBiomeSource),
            RingBiomeConfig.CODEC.fieldOf("config").forGetter(CustomRingBiomeSource::config)
    ).apply(instance, CustomRingBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final RingBiomeConfig config;
    private final List<RegistryEntry<Biome>> cachedBiomes;

    public CustomRingBiomeSource(MultiNoiseBiomeSource vanillaBiomeSource, RingBiomeConfig config) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.config = config;
        this.cachedBiomes = collectBiomes();
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return cachedBiomes.stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double blockX = x * 4.0;
        double blockZ = z * 4.0;

        // Requested radial math: radius = sqrt(x*x + z*z)
        double radius = Math.sqrt(blockX * blockX + blockZ * blockZ);
        if (radius >= config.vanillaBlendStart()) {
            return vanillaBiomeSource.getBiome(x, y, z, noise);
        }

        if (radius <= config.centerIslandRadius()) {
            return config.centerIslandBiome();
        }

        if (radius <= config.innerOceanRadius() || radius >= config.outerOceanStart()) {
            return config.oceanBiome();
        }

        NoiseSettings angleWarp = config.angleWarp();
        double warpedX = blockX + OrganicNoise.sample(angleWarp.seed(), blockX, blockZ, angleWarp.scale(), 3) * angleWarp.strength();
        double warpedZ = blockZ + OrganicNoise.sample(angleWarp.seed() ^ 0x5F3759DFL, blockX, blockZ, angleWarp.scale(), 3) * angleWarp.strength();

        // Requested angle normalization based on atan2(z, x).
        double angle = Math.atan2(warpedZ, warpedX) / (Math.PI * 2.0);
        if (angle < 0.0) {
            angle += 1.0;
        }

        List<RegistryEntry<Biome>> sectorBiomes = biomesForAngle(angle);
        NoiseSettings secondaryNoise = config.secondarySelectionNoise();
        double biomeNoise = (OrganicNoise.sample(secondaryNoise.seed(), blockX, blockZ, secondaryNoise.scale(), 3) + 1.0) * 0.5;
        int biomeIndex = MathHelper.clamp((int) Math.floor(biomeNoise * sectorBiomes.size()), 0, sectorBiomes.size() - 1);
        return sectorBiomes.get(biomeIndex);
    }

    private List<RegistryEntry<Biome>> biomesForAngle(double angle) {
        SectorConfig sectors = config.sectors();
        if (angle < 0.125) return sectors.eastBiomes();
        if (angle < 0.250) return sectors.southEastBiomes();
        if (angle < 0.375) return sectors.southBiomes();
        if (angle < 0.500) return sectors.southWestBiomes();
        if (angle < 0.625) return sectors.westBiomes();
        if (angle < 0.750) return sectors.northWestBiomes();
        return sectors.northBiomes();
    }

    private List<RegistryEntry<Biome>> collectBiomes() {
        Set<RegistryEntry<Biome>> biomes = new LinkedHashSet<>();
        biomes.add(config.centerIslandBiome());
        biomes.add(config.oceanBiome());
        biomes.addAll(config.sectors().eastBiomes());
        biomes.addAll(config.sectors().southEastBiomes());
        biomes.addAll(config.sectors().southBiomes());
        biomes.addAll(config.sectors().southWestBiomes());
        biomes.addAll(config.sectors().westBiomes());
        biomes.addAll(config.sectors().northWestBiomes());
        biomes.addAll(config.sectors().northBiomes());
        biomes.addAll(vanillaBiomeSource.getBiomes());
        return new ArrayList<>(biomes);
    }

    private MultiNoiseBiomeSource vanillaBiomeSource() {
        return vanillaBiomeSource;
    }

    private RingBiomeConfig config() {
        return config;
    }

    public record RingBiomeConfig(
            int centerIslandRadius,
            int innerOceanRadius,
            int outerOceanStart,
            int vanillaBlendStart,
            RegistryEntry<Biome> centerIslandBiome,
            RegistryEntry<Biome> oceanBiome,
            SectorConfig sectors,
            NoiseSettings angleWarp,
            NoiseSettings secondarySelectionNoise
    ) {
        public static final MapCodec<RingBiomeConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 256).optionalFieldOf("center_island_radius", 70).forGetter(RingBiomeConfig::centerIslandRadius),
                Codec.intRange(1, 1024).optionalFieldOf("inner_ocean_radius", 300).forGetter(RingBiomeConfig::innerOceanRadius),
                Codec.intRange(1, 4096).optionalFieldOf("outer_ocean_start", 2000).forGetter(RingBiomeConfig::outerOceanStart),
                Codec.intRange(1, 8192).optionalFieldOf("vanilla_blend_start", 3000).forGetter(RingBiomeConfig::vanillaBlendStart),
                BIOME_CODEC.fieldOf("center_island_biome").forGetter(RingBiomeConfig::centerIslandBiome),
                BIOME_CODEC.fieldOf("ocean_biome").forGetter(RingBiomeConfig::oceanBiome),
                SectorConfig.CODEC.fieldOf("sectors").forGetter(RingBiomeConfig::sectors),
                NoiseSettings.CODEC.optionalFieldOf("angle_warp", new NoiseSettings(0xCAFEF00DL, 320.0, 90.0)).forGetter(RingBiomeConfig::angleWarp),
                NoiseSettings.CODEC.optionalFieldOf("secondary_selection_noise", new NoiseSettings(0xBADC0FFEE0DDF00DL, 220.0, 1.0)).forGetter(RingBiomeConfig::secondarySelectionNoise)
        ).apply(instance, RingBiomeConfig::new));
        public static final Codec<RingBiomeConfig> CODEC = MAP_CODEC.codec();
    }

    public record SectorConfig(
            List<RegistryEntry<Biome>> eastBiomes,
            List<RegistryEntry<Biome>> southEastBiomes,
            List<RegistryEntry<Biome>> southBiomes,
            List<RegistryEntry<Biome>> southWestBiomes,
            List<RegistryEntry<Biome>> westBiomes,
            List<RegistryEntry<Biome>> northWestBiomes,
            List<RegistryEntry<Biome>> northBiomes
    ) {
        public SectorConfig {
            requireNonEmpty(eastBiomes, "east_biomes");
            requireNonEmpty(southEastBiomes, "south_east_biomes");
            requireNonEmpty(southBiomes, "south_biomes");
            requireNonEmpty(southWestBiomes, "south_west_biomes");
            requireNonEmpty(westBiomes, "west_biomes");
            requireNonEmpty(northWestBiomes, "north_west_biomes");
            requireNonEmpty(northBiomes, "north_biomes");
        }

        private static void requireNonEmpty(List<?> biomes, String fieldName) {
            if (biomes.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must contain at least one biome");
            }
        }

        public static final MapCodec<SectorConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BIOME_CODEC.listOf().fieldOf("east_biomes").forGetter(SectorConfig::eastBiomes),
                BIOME_CODEC.listOf().fieldOf("south_east_biomes").forGetter(SectorConfig::southEastBiomes),
                BIOME_CODEC.listOf().fieldOf("south_biomes").forGetter(SectorConfig::southBiomes),
                BIOME_CODEC.listOf().fieldOf("south_west_biomes").forGetter(SectorConfig::southWestBiomes),
                BIOME_CODEC.listOf().fieldOf("west_biomes").forGetter(SectorConfig::westBiomes),
                BIOME_CODEC.listOf().fieldOf("north_west_biomes").forGetter(SectorConfig::northWestBiomes),
                BIOME_CODEC.listOf().fieldOf("north_biomes").forGetter(SectorConfig::northBiomes)
        ).apply(instance, SectorConfig::new));
        public static final Codec<SectorConfig> CODEC = MAP_CODEC.codec();
    }

    public record NoiseSettings(long seed, double scale, double strength) {
        public static final MapCodec<NoiseSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("seed").forGetter(NoiseSettings::seed),
                Codec.DOUBLE.fieldOf("scale").forGetter(NoiseSettings::scale),
                Codec.DOUBLE.fieldOf("strength").forGetter(NoiseSettings::strength)
        ).apply(instance, NoiseSettings::new));
        public static final Codec<NoiseSettings> CODEC = MAP_CODEC.codec();
    }
}
