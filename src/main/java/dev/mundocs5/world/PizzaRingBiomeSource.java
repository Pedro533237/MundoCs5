package dev.mundocs5.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
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
 * Geração radial estilo "hub world":
 * 1) centro = mushroom fields
 * 2) anel = biomas por fatias (pizza)
 * 3) transição = oceano por ângulo
 * 4) fora = vanilla (fallback para MultiNoise)
 */
public class PizzaRingBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);

    public static final MapCodec<PizzaRingBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(PizzaRingBiomeSource::vanillaBiomeSource),
            BIOME_CODEC.listOf().fieldOf("pizza_biomes").forGetter(PizzaRingBiomeSource::pizzaBiomes),
            BIOME_CODEC.fieldOf("center_biome").forGetter(PizzaRingBiomeSource::centerBiome),
            BIOME_CODEC.listOf().fieldOf("ocean_biomes").forGetter(PizzaRingBiomeSource::oceanBiomes),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("center_radius", 200).forGetter(PizzaRingBiomeSource::centerRadius),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("pizza_radius", 2000).forGetter(PizzaRingBiomeSource::pizzaRadius),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("ocean_radius", 2600).forGetter(PizzaRingBiomeSource::oceanRadius)
    ).apply(instance, PizzaRingBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final List<RegistryEntry<Biome>> pizzaBiomes;
    private final RegistryEntry<Biome> centerBiome;
    private final List<RegistryEntry<Biome>> oceanBiomes;
    private final int centerRadius;
    private final int pizzaRadius;
    private final int oceanRadius;


    private MultiNoiseBiomeSource vanillaBiomeSource() {
        return vanillaBiomeSource;
    }

    private List<RegistryEntry<Biome>> pizzaBiomes() {
        return pizzaBiomes;
    }

    private RegistryEntry<Biome> centerBiome() {
        return centerBiome;
    }

    private List<RegistryEntry<Biome>> oceanBiomes() {
        return oceanBiomes;
    }

    private int centerRadius() {
        return centerRadius;
    }

    private int pizzaRadius() {
        return pizzaRadius;
    }

    private int oceanRadius() {
        return oceanRadius;
    }

    public PizzaRingBiomeSource(
            MultiNoiseBiomeSource vanillaBiomeSource,
            List<RegistryEntry<Biome>> pizzaBiomes,
            RegistryEntry<Biome> centerBiome,
            List<RegistryEntry<Biome>> oceanBiomes,
            int centerRadius,
            int pizzaRadius,
            int oceanRadius
    ) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.pizzaBiomes = pizzaBiomes;
        this.centerBiome = centerBiome;
        this.oceanBiomes = oceanBiomes;
        this.centerRadius = centerRadius;
        this.pizzaRadius = pizzaRadius;
        this.oceanRadius = oceanRadius;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.concat(Stream.concat(pizzaBiomes.stream(), oceanBiomes.stream()), Stream.of(centerBiome));
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double dist = Math.sqrt((double) x * x + (double) z * z);

        // 1) CENTRO
        if (dist < centerRadius) {
            return centerBiome;
        }

        // 2) ANEL PIZZA
        if (dist < pizzaRadius) {
            return getPizzaBiome(x, z);
        }

        // 3) TRANSIÇÃO PARA OCEANO
        if (dist < oceanRadius) {
            RegistryEntry<Biome> pizza = getPizzaBiome(x, z);
            RegistryEntry<Biome> ocean = getOceanBiomeByAngle(x, z);
            double fade = MathHelper.clamp((dist - pizzaRadius) / (double) (oceanRadius - pizzaRadius), 0.0, 1.0);
            return fade < 0.5 ? pizza : ocean;
        }

        // 4) Fallback vanilla infinito
        return vanillaBiomeSource.getBiome(x, y, z, noise);
    }

    private RegistryEntry<Biome> getPizzaBiome(int x, int z) {
        if (pizzaBiomes.isEmpty()) {
            return centerBiome;
        }

        double angle = Math.atan2(z, x);
        double normalized = (angle + Math.PI) / (2.0 * Math.PI);
        int idx = Math.min((int) (normalized * pizzaBiomes.size()), pizzaBiomes.size() - 1);
        return pizzaBiomes.get(idx);
    }

    private RegistryEntry<Biome> getOceanBiomeByAngle(int x, int z) {
        if (oceanBiomes.isEmpty()) {
            return centerBiome;
        }

        double angle = Math.atan2(z, x);
        double normalized = (angle + Math.PI) / (2.0 * Math.PI);
        int idx = Math.min((int) (normalized * oceanBiomes.size()), oceanBiomes.size() - 1);
        return oceanBiomes.get(idx);
    }
}
