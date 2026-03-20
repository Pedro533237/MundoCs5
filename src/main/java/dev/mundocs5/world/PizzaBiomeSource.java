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
 * Custom biome source for a ring-shaped world centered on X=0/Z=0.
 *
 * <p>The important math is explicit here:
 * <ul>
 *   <li>radius = Math.sqrt(x*x + z*z)</li>
 *   <li>angle = Math.atan2(z, x)</li>
 *   <li>sliceIndex = floor(normalizedAngle / (2π / 11))</li>
 * </ul>
 */
public class PizzaBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);
    private static final double FULL_TURN = Math.PI * 2.0;

    public static final MapCodec<PizzaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(PizzaBiomeSource::vanillaBiomeSource),
            PolarBiomeConfig.CODEC.fieldOf("config").forGetter(PizzaBiomeSource::config)
    ).apply(instance, PizzaBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final PolarBiomeConfig config;
    private final List<RegistryEntry<Biome>> biomes;

    public PizzaBiomeSource(MultiNoiseBiomeSource vanillaBiomeSource, PolarBiomeConfig config) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.config = config;
        this.biomes = collectBiomes();
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return biomes.stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        // Minecraft queries biomes in quart coordinates, so convert back to block space
        // before applying the requested world-scale geometry.
        double blockX = x * 4.0;
        double blockZ = z * 4.0;

        // Exact Euclidean distance to the origin.
        double radius = Math.sqrt(blockX * blockX + blockZ * blockZ);

        if (radius <= config.centerIslandRadius()) {
            return config.centerIslandBiome();
        }

        if (radius <= config.innerOceanRadius()) {
            return config.innerOceanBiome();
        }

        if (radius <= config.outerRingRadius()) {
            // atan2 returns an angle in [-π, π].
            double angleRadians = Math.atan2(blockZ, blockX);
            // Convert it into [0, 2π) so it can be split into equal pizza slices.
            double normalizedAngle = normalizeAngle(angleRadians);
            int sliceIndex = sliceIndex(normalizedAngle, config.ringBiomes().size());
            return config.ringBiomes().get(sliceIndex);
        }

        return config.outerOceanBiome();
    }

    private int sliceIndex(double normalizedAngle, int sliceCount) {
        double sliceSize = FULL_TURN / sliceCount;
        int index = MathHelper.floor(normalizedAngle / sliceSize);
        return MathHelper.clamp(index, 0, sliceCount - 1);
    }

    private double normalizeAngle(double angleRadians) {
        double wrapped = angleRadians % FULL_TURN;
        return wrapped < 0.0 ? wrapped + FULL_TURN : wrapped;
    }

    private List<RegistryEntry<Biome>> collectBiomes() {
        Set<RegistryEntry<Biome>> collected = new LinkedHashSet<>();
        collected.add(config.centerIslandBiome());
        collected.add(config.innerOceanBiome());
        collected.addAll(config.ringBiomes());
        collected.add(config.outerOceanBiome());
        return new ArrayList<>(collected);
    }

    private MultiNoiseBiomeSource vanillaBiomeSource() {
        return vanillaBiomeSource;
    }

    private PolarBiomeConfig config() {
        return config;
    }

    public record PolarBiomeConfig(
            int centerIslandRadius,
            int innerOceanRadius,
            int outerRingRadius,
            RegistryEntry<Biome> centerIslandBiome,
            RegistryEntry<Biome> innerOceanBiome,
            List<RegistryEntry<Biome>> ringBiomes,
            RegistryEntry<Biome> outerOceanBiome
    ) {
        public static final int REQUIRED_SLICE_COUNT = 11;

        public PolarBiomeConfig {
            if (ringBiomes.size() != REQUIRED_SLICE_COUNT) {
                throw new IllegalArgumentException("ring_biomes must contain exactly 11 entries, got " + ringBiomes.size());
            }
        }

        public static final MapCodec<PolarBiomeConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 256).optionalFieldOf("center_island_radius", 50).forGetter(PolarBiomeConfig::centerIslandRadius),
                Codec.intRange(1, 1024).optionalFieldOf("inner_ocean_radius", 300).forGetter(PolarBiomeConfig::innerOceanRadius),
                Codec.intRange(1, 4096).optionalFieldOf("outer_ring_radius", 1500).forGetter(PolarBiomeConfig::outerRingRadius),
                BIOME_CODEC.fieldOf("center_island_biome").forGetter(PolarBiomeConfig::centerIslandBiome),
                BIOME_CODEC.fieldOf("inner_ocean_biome").forGetter(PolarBiomeConfig::innerOceanBiome),
                BIOME_CODEC.listOf().fieldOf("ring_biomes").forGetter(PolarBiomeConfig::ringBiomes),
                BIOME_CODEC.fieldOf("outer_ocean_biome").forGetter(PolarBiomeConfig::outerOceanBiome)
        ).apply(instance, PolarBiomeConfig::new));
        public static final Codec<PolarBiomeConfig> CODEC = MAP_CODEC.codec();
    }
}
