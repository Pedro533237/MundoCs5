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
 * 1) ilha inicial no centro
 * 2) lago central
 * 3) anel de biomas por fatias (pizza)
 * 4) transição para oceano
 * 5) oceano infinito fora do anel
 */
public class PizzaRingBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);

    public static final MapCodec<PizzaRingBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(PizzaRingBiomeSource::vanillaBiomeSource),
            BIOME_CODEC.fieldOf("island_biome").forGetter(PizzaRingBiomeSource::islandBiome),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("island_radius", 80).forGetter(PizzaRingBiomeSource::islandRadius),
            BIOME_CODEC.fieldOf("center_biome").forGetter(PizzaRingBiomeSource::centerBiome),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("center_radius", 450).forGetter(PizzaRingBiomeSource::centerRadius),
            BIOME_CODEC.listOf().fieldOf("pizza_biomes").forGetter(PizzaRingBiomeSource::pizzaBiomes),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("pizza_radius", 2400).forGetter(PizzaRingBiomeSource::pizzaRadius),
            BIOME_CODEC.listOf().fieldOf("ocean_biomes").forGetter(PizzaRingBiomeSource::oceanBiomes),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("ocean_radius", 3000).forGetter(PizzaRingBiomeSource::oceanRadius)
    ).apply(instance, PizzaRingBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final RegistryEntry<Biome> islandBiome;
    private final int islandRadius;
    private final RegistryEntry<Biome> centerBiome;
    private final int centerRadius;
    private final List<RegistryEntry<Biome>> pizzaBiomes;
    private final int pizzaRadius;
    private final List<RegistryEntry<Biome>> oceanBiomes;
    private final int oceanRadius;

    private MultiNoiseBiomeSource vanillaBiomeSource() {
        return vanillaBiomeSource;
    }

    private RegistryEntry<Biome> islandBiome() {
        return islandBiome;
    }

    private int islandRadius() {
        return islandRadius;
    }

    private RegistryEntry<Biome> centerBiome() {
        return centerBiome;
    }

    private int centerRadius() {
        return centerRadius;
    }

    private List<RegistryEntry<Biome>> pizzaBiomes() {
        return pizzaBiomes;
    }

    private int pizzaRadius() {
        return pizzaRadius;
    }

    private List<RegistryEntry<Biome>> oceanBiomes() {
        return oceanBiomes;
    }

    private int oceanRadius() {
        return oceanRadius;
    }

    public PizzaRingBiomeSource(
            MultiNoiseBiomeSource vanillaBiomeSource,
            RegistryEntry<Biome> islandBiome,
            int islandRadius,
            RegistryEntry<Biome> centerBiome,
            int centerRadius,
            List<RegistryEntry<Biome>> pizzaBiomes,
            int pizzaRadius,
            List<RegistryEntry<Biome>> oceanBiomes,
            int oceanRadius
    ) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.islandBiome = islandBiome;
        this.islandRadius = islandRadius;
        this.centerBiome = centerBiome;
        this.centerRadius = centerRadius;
        this.pizzaBiomes = pizzaBiomes;
        this.pizzaRadius = pizzaRadius;
        this.oceanBiomes = oceanBiomes;
        this.oceanRadius = oceanRadius;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.concat(
                Stream.concat(
                        Stream.concat(pizzaBiomes.stream(), oceanBiomes.stream()),
                        Stream.of(centerBiome)
                ),
                Stream.of(islandBiome)
        );
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double dist = Math.sqrt((double) x * x + (double) z * z);

        // 1) ILHA CENTRAL (spawn/hub)
        if (dist < islandRadius) {
            return islandBiome;
        }

        // 2) LAGO CENTRAL
        if (dist < centerRadius) {
            return centerBiome;
        }

        // 3) ANEL PIZZA
        if (dist < pizzaRadius) {
            return getPizzaBiome(x, z);
        }

        // 4) TRANSIÇÃO PARA OCEANO
        if (dist < oceanRadius) {
            RegistryEntry<Biome> pizza = getPizzaBiome(x, z);
            RegistryEntry<Biome> ocean = getOceanBiomeByAngle(x, z);
            double fade = MathHelper.clamp((dist - pizzaRadius) / (double) (oceanRadius - pizzaRadius), 0.0, 1.0);
            return fade < 0.5 ? pizza : ocean;
        }

        // 5) FORA DO MAPA: OCEANO INFINITO
        return getOceanBiomeByAngle(x, z);
    }

    private RegistryEntry<Biome> getPizzaBiome(int x, int z) {
        if (pizzaBiomes.isEmpty()) {
            return islandBiome;
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
