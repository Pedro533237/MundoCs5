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

public class PizzaBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);
    private static final double FULL_TURN = Math.PI * 2.0;
    private static final long DEFAULT_LAYOUT_SEED = 0x50A4C0FFEE42L;

    public static final MapCodec<PizzaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(PizzaBiomeSource::vanillaBiomeSource),
            Codec.LONG.optionalFieldOf("layout_seed", DEFAULT_LAYOUT_SEED).forGetter(PizzaBiomeSource::layoutSeed),
            BIOME_CODEC.fieldOf("center_biome").forGetter(PizzaBiomeSource::centerBiome),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("center_radius", 300).forGetter(PizzaBiomeSource::centerRadius),
            BIOME_CODEC.listOf().fieldOf("east_biomes").forGetter(PizzaBiomeSource::eastBiomes),
            BIOME_CODEC.listOf().fieldOf("southeast_biomes").forGetter(PizzaBiomeSource::southeastBiomes),
            BIOME_CODEC.listOf().fieldOf("south_biomes").forGetter(PizzaBiomeSource::southBiomes),
            BIOME_CODEC.listOf().fieldOf("southwest_biomes").forGetter(PizzaBiomeSource::southwestBiomes),
            BIOME_CODEC.listOf().fieldOf("west_biomes").forGetter(PizzaBiomeSource::westBiomes),
            BIOME_CODEC.listOf().fieldOf("northwest_biomes").forGetter(PizzaBiomeSource::northwestBiomes),
            BIOME_CODEC.listOf().fieldOf("north_biomes").forGetter(PizzaBiomeSource::northBiomes),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("pizza_radius", 3000).forGetter(PizzaBiomeSource::pizzaRadius),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("ocean_start", 3000).forGetter(PizzaBiomeSource::oceanStart),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("outer_world_start", 4048).forGetter(PizzaBiomeSource::outerWorldStart),
            BIOME_CODEC.fieldOf("temperate_ocean_biome").forGetter(PizzaBiomeSource::temperateOceanBiome),
            BIOME_CODEC.fieldOf("cold_ocean_biome").forGetter(PizzaBiomeSource::coldOceanBiome),
            BIOME_CODEC.fieldOf("deep_cold_ocean_biome").forGetter(PizzaBiomeSource::deepColdOceanBiome),
            BIOME_CODEC.fieldOf("warm_ocean_biome").forGetter(PizzaBiomeSource::warmOceanBiome),
            BIOME_CODEC.fieldOf("lukewarm_ocean_biome").forGetter(PizzaBiomeSource::lukewarmOceanBiome),
            BIOME_CODEC.fieldOf("beach_biome").forGetter(PizzaBiomeSource::beachBiome),
            BIOME_CODEC.fieldOf("snowy_beach_biome").forGetter(PizzaBiomeSource::snowyBeachBiome)
    ).apply(instance, PizzaBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final long layoutSeed;
    private final RegistryEntry<Biome> centerBiome;
    private final int centerRadius;
    private final List<RegistryEntry<Biome>> eastBiomes;
    private final List<RegistryEntry<Biome>> southeastBiomes;
    private final List<RegistryEntry<Biome>> southBiomes;
    private final List<RegistryEntry<Biome>> southwestBiomes;
    private final List<RegistryEntry<Biome>> westBiomes;
    private final List<RegistryEntry<Biome>> northwestBiomes;
    private final List<RegistryEntry<Biome>> northBiomes;
    private final int pizzaRadius;
    private final int oceanStart;
    private final int outerWorldStart;
    private final RegistryEntry<Biome> temperateOceanBiome;
    private final RegistryEntry<Biome> coldOceanBiome;
    private final RegistryEntry<Biome> deepColdOceanBiome;
    private final RegistryEntry<Biome> warmOceanBiome;
    private final RegistryEntry<Biome> lukewarmOceanBiome;
    private final RegistryEntry<Biome> beachBiome;
    private final RegistryEntry<Biome> snowyBeachBiome;
    private final List<BiomeSlice> slices;
    private final List<RegistryEntry<Biome>> allBiomes;

    public PizzaBiomeSource(
            MultiNoiseBiomeSource vanillaBiomeSource,
            long layoutSeed,
            RegistryEntry<Biome> centerBiome,
            int centerRadius,
            List<RegistryEntry<Biome>> eastBiomes,
            List<RegistryEntry<Biome>> southeastBiomes,
            List<RegistryEntry<Biome>> southBiomes,
            List<RegistryEntry<Biome>> southwestBiomes,
            List<RegistryEntry<Biome>> westBiomes,
            List<RegistryEntry<Biome>> northwestBiomes,
            List<RegistryEntry<Biome>> northBiomes,
            int pizzaRadius,
            int oceanStart,
            int outerWorldStart,
            RegistryEntry<Biome> temperateOceanBiome,
            RegistryEntry<Biome> coldOceanBiome,
            RegistryEntry<Biome> deepColdOceanBiome,
            RegistryEntry<Biome> warmOceanBiome,
            RegistryEntry<Biome> lukewarmOceanBiome,
            RegistryEntry<Biome> beachBiome,
            RegistryEntry<Biome> snowyBeachBiome
    ) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.layoutSeed = layoutSeed;
        this.centerBiome = centerBiome;
        this.centerRadius = centerRadius;
        this.eastBiomes = List.copyOf(eastBiomes);
        this.southeastBiomes = List.copyOf(southeastBiomes);
        this.southBiomes = List.copyOf(southBiomes);
        this.southwestBiomes = List.copyOf(southwestBiomes);
        this.westBiomes = List.copyOf(westBiomes);
        this.northwestBiomes = List.copyOf(northwestBiomes);
        this.northBiomes = List.copyOf(northBiomes);
        this.pizzaRadius = pizzaRadius;
        this.oceanStart = oceanStart;
        this.outerWorldStart = outerWorldStart;
        this.temperateOceanBiome = temperateOceanBiome;
        this.coldOceanBiome = coldOceanBiome;
        this.deepColdOceanBiome = deepColdOceanBiome;
        this.warmOceanBiome = warmOceanBiome;
        this.lukewarmOceanBiome = lukewarmOceanBiome;
        this.beachBiome = beachBiome;
        this.snowyBeachBiome = snowyBeachBiome;
        this.slices = List.of(
                new BiomeSlice(0.0, 0.125, this.eastBiomes, Climate.TEMPERATE),
                new BiomeSlice(0.125, 0.25, this.southeastBiomes, Climate.WARM),
                new BiomeSlice(0.25, 0.375, this.southBiomes, Climate.HOT),
                new BiomeSlice(0.375, 0.5, this.southwestBiomes, Climate.TEMPERATE),
                new BiomeSlice(0.5, 0.625, this.westBiomes, Climate.COOL),
                new BiomeSlice(0.625, 0.75, this.northwestBiomes, Climate.TEMPERATE),
                new BiomeSlice(0.75, 1.0, this.northBiomes, Climate.COLD)
        );
        this.allBiomes = collectBiomes();
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return allBiomes.stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double blockX = x * 4.0;
        double blockZ = z * 4.0;
        double baseDistance = Math.sqrt(blockX * blockX + blockZ * blockZ);

        if (baseDistance >= outerWorldStart + 384.0) {
            return vanillaBiomeSource.getBiome(x, y, z, noise);
        }

        double radiusWarp = OrganicNoise.sample(layoutSeed ^ 0x1A2B3C4DL, blockX, blockZ, 720.0, 3) * 180.0;
        double edgeWarp = OrganicNoise.sample(layoutSeed ^ 0x55AA12FFL, blockX, blockZ, 280.0, 2) * 54.0;
        double warpedDistance = Math.max(0.0, baseDistance + radiusWarp + edgeWarp);

        if (warpedDistance < centerRadius) {
            return centerBiome;
        }

        if (warpedDistance >= outerWorldStart) {
            return vanillaBiomeSource.getBiome(x, y, z, noise);
        }

        double normalizedAngle = normalizeAngle(Math.atan2(blockZ, blockX)
                + OrganicNoise.sample(layoutSeed ^ 0x0F0F0F0FL, blockX, blockZ, 900.0, 3) * 0.22);

        BiomeSlice primarySlice = findSlice(normalizedAngle);
        BiomeSlice secondarySlice = warpedDistance < oceanStart ? findNeighborSlice(normalizedAngle, primarySlice) : primarySlice;
        RegistryEntry<Biome> landBiome = pickLandBiome(primarySlice, secondarySlice, normalizedAngle, warpedDistance, blockX, blockZ);

        double coastlineNoise = OrganicNoise.sample(layoutSeed ^ 0x77777777L, blockX, blockZ, 190.0, 2);
        double beachWidth = 88.0 + coastlineNoise * 28.0;
        boolean nearCoast = warpedDistance >= oceanStart - beachWidth && warpedDistance < oceanStart + beachWidth * 0.35;

        if (warpedDistance < oceanStart) {
            return nearCoast ? beachFor(primarySlice.climate()) : landBiome;
        }

        RegistryEntry<Biome> oceanBiome = pickOceanBiome(normalizedAngle, warpedDistance, blockX, blockZ);
        double ringBlend = MathHelper.clamp((warpedDistance - oceanStart) / Math.max(1.0, (outerWorldStart - oceanStart)), 0.0, 1.0);
        double surfNoise = OrganicNoise.sample(layoutSeed ^ 0x31313131L, blockX, blockZ, 220.0, 2) * 0.14;

        if (ringBlend < 0.10 + surfNoise) {
            return beachFor(primarySlice.climate());
        }

        if (ringBlend < 0.22 + surfNoise) {
            return landBiome;
        }

        return oceanBiome;
    }

    private RegistryEntry<Biome> pickLandBiome(BiomeSlice primarySlice, BiomeSlice secondarySlice, double normalizedAngle, double distance, double x, double z) {
        List<RegistryEntry<Biome>> primaryGroup = ensureNotEmpty(primarySlice.biomes(), centerBiome);
        List<RegistryEntry<Biome>> secondaryGroup = ensureNotEmpty(secondarySlice.biomes(), primaryGroup.get(0));

        int primaryIndex = Math.floorMod((int) Math.floor((distance + OrganicNoise.sample(layoutSeed ^ 0xBEEFL, x, z, 300.0, 2) * 240.0) / 340.0), primaryGroup.size());
        RegistryEntry<Biome> primaryBiome = primaryGroup.get(primaryIndex);

        if (primarySlice == secondarySlice) {
            return primaryBiome;
        }

        double midpoint = wrapNormalized((primarySlice.end() + secondarySlice.start()) * 0.5);
        double angularDistance = shortestWrappedDistance(normalizedAngle, midpoint);
        double boundaryWidth = 0.024 + Math.abs(OrganicNoise.sample(layoutSeed ^ 0xCAFEF00DL, x, z, 680.0, 2)) * 0.02;
        double blend = MathHelper.clamp(0.5 + angularDistance / Math.max(boundaryWidth, 1.0E-4), 0.0, 1.0);
        blend += OrganicNoise.sample(layoutSeed ^ 0x12344321L, x, z, 250.0, 2) * 0.18;

        if (blend <= 0.57) {
            return primaryBiome;
        }

        int secondaryIndex = Math.floorMod((int) Math.floor((distance + OrganicNoise.sample(layoutSeed ^ 0xD00DL, x, z, 320.0, 2) * 220.0) / 360.0), secondaryGroup.size());
        return secondaryGroup.get(secondaryIndex);
    }

    private RegistryEntry<Biome> pickOceanBiome(double normalizedAngle, double warpedDistance, double x, double z) {
        double warmness = southBias(normalizedAngle) + OrganicNoise.sample(layoutSeed ^ 0x1EE7L, x, z, 620.0, 2) * 0.12;
        double depthBlend = MathHelper.clamp((warpedDistance - oceanStart) / Math.max(1.0, outerWorldStart - oceanStart), 0.0, 1.0);
        double depthNoise = OrganicNoise.sample(layoutSeed ^ 0x1DEA1234L, x, z, 420.0, 2) * 0.08;
        double deepThreshold = 0.58 + depthNoise;

        if (warmness > 0.28) {
            return depthBlend > 0.46 ? lukewarmOceanBiome : warmOceanBiome;
        }

        if (warmness < -0.3) {
            return depthBlend > deepThreshold ? deepColdOceanBiome : coldOceanBiome;
        }

        return temperateOceanBiome;
    }

    private RegistryEntry<Biome> beachFor(Climate climate) {
        return climate == Climate.COLD || climate == Climate.COOL ? snowyBeachBiome : beachBiome;
    }

    private BiomeSlice findSlice(double normalizedAngle) {
        for (BiomeSlice slice : slices) {
            if (normalizedAngle >= slice.start() && normalizedAngle < slice.end()) {
                return slice;
            }
        }
        return slices.getLast();
    }

    private BiomeSlice findNeighborSlice(double normalizedAngle, BiomeSlice primarySlice) {
        int primaryIndex = slices.indexOf(primarySlice);
        BiomeSlice left = slices.get(Math.floorMod(primaryIndex - 1, slices.size()));
        BiomeSlice right = slices.get(Math.floorMod(primaryIndex + 1, slices.size()));

        double leftDistance = shortestWrappedDistance(normalizedAngle, left.end());
        double rightDistance = shortestWrappedDistance(normalizedAngle, primarySlice.end());
        return Math.abs(leftDistance) < Math.abs(rightDistance) ? left : right;
    }

    private List<RegistryEntry<Biome>> ensureNotEmpty(List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> fallback) {
        return biomes.isEmpty() ? List.of(fallback) : biomes;
    }

    private double southBias(double normalizedAngle) {
        double radians = normalizedAngle * FULL_TURN;
        return -Math.sin(radians);
    }

    private double normalizeAngle(double radians) {
        return wrapNormalized(radians / FULL_TURN + 1.0);
    }

    private double wrapNormalized(double value) {
        double wrapped = value % 1.0;
        return wrapped < 0.0 ? wrapped + 1.0 : wrapped;
    }

    private double shortestWrappedDistance(double from, double to) {
        double delta = wrapNormalized(from) - wrapNormalized(to);
        if (delta > 0.5) {
            delta -= 1.0;
        } else if (delta < -0.5) {
            delta += 1.0;
        }
        return delta;
    }

    private List<RegistryEntry<Biome>> collectBiomes() {
        Set<RegistryEntry<Biome>> collected = new LinkedHashSet<>();
        collected.add(centerBiome);
        collected.addAll(eastBiomes);
        collected.addAll(southeastBiomes);
        collected.addAll(southBiomes);
        collected.addAll(southwestBiomes);
        collected.addAll(westBiomes);
        collected.addAll(northwestBiomes);
        collected.addAll(northBiomes);
        collected.add(temperateOceanBiome);
        collected.add(coldOceanBiome);
        collected.add(deepColdOceanBiome);
        collected.add(warmOceanBiome);
        collected.add(lukewarmOceanBiome);
        collected.add(beachBiome);
        collected.add(snowyBeachBiome);
        return new ArrayList<>(collected);
    }

    private MultiNoiseBiomeSource vanillaBiomeSource() { return vanillaBiomeSource; }
    private long layoutSeed() { return layoutSeed; }
    private RegistryEntry<Biome> centerBiome() { return centerBiome; }
    private int centerRadius() { return centerRadius; }
    private List<RegistryEntry<Biome>> eastBiomes() { return eastBiomes; }
    private List<RegistryEntry<Biome>> southeastBiomes() { return southeastBiomes; }
    private List<RegistryEntry<Biome>> southBiomes() { return southBiomes; }
    private List<RegistryEntry<Biome>> southwestBiomes() { return southwestBiomes; }
    private List<RegistryEntry<Biome>> westBiomes() { return westBiomes; }
    private List<RegistryEntry<Biome>> northwestBiomes() { return northwestBiomes; }
    private List<RegistryEntry<Biome>> northBiomes() { return northBiomes; }
    private int pizzaRadius() { return pizzaRadius; }
    private int oceanStart() { return oceanStart; }
    private int outerWorldStart() { return outerWorldStart; }
    private RegistryEntry<Biome> temperateOceanBiome() { return temperateOceanBiome; }
    private RegistryEntry<Biome> coldOceanBiome() { return coldOceanBiome; }
    private RegistryEntry<Biome> deepColdOceanBiome() { return deepColdOceanBiome; }
    private RegistryEntry<Biome> warmOceanBiome() { return warmOceanBiome; }
    private RegistryEntry<Biome> lukewarmOceanBiome() { return lukewarmOceanBiome; }
    private RegistryEntry<Biome> beachBiome() { return beachBiome; }
    private RegistryEntry<Biome> snowyBeachBiome() { return snowyBeachBiome; }

    private record BiomeSlice(double start, double end, List<RegistryEntry<Biome>> biomes, Climate climate) {
    }

    private enum Climate {
        COLD,
        COOL,
        TEMPERATE,
        WARM,
        HOT
    }
}
