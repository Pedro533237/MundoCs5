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
            Codec.INT.optionalFieldOf("center_x", 0).forGetter(PizzaBiomeSource::centerX),
            Codec.INT.optionalFieldOf("center_z", 0).forGetter(PizzaBiomeSource::centerZ),
            LandConfig.CODEC.fieldOf("land_config").forGetter(PizzaBiomeSource::landConfig),
            OceanConfig.CODEC.fieldOf("ocean_config").forGetter(PizzaBiomeSource::oceanConfig)
    ).apply(instance, PizzaBiomeSource::new));

    private final MultiNoiseBiomeSource vanillaBiomeSource;
    private final long layoutSeed;
    private final int centerX;
    private final int centerZ;
    private final LandConfig landConfig;
    private final OceanConfig oceanConfig;
    private final List<Sector> sectors;
    private final List<RegistryEntry<Biome>> allBiomes;

    public PizzaBiomeSource(MultiNoiseBiomeSource vanillaBiomeSource, long layoutSeed, int centerX, int centerZ, LandConfig landConfig, OceanConfig oceanConfig) {
        this.vanillaBiomeSource = vanillaBiomeSource;
        this.layoutSeed = layoutSeed;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.landConfig = landConfig;
        this.oceanConfig = oceanConfig;
        this.sectors = buildSectors();
        this.allBiomes = collectBiomes();
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() { return allBiomes.stream(); }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double blockX = x * 4.0 - centerX;
        double blockZ = z * 4.0 - centerZ;
        double baseDistance = Math.sqrt(blockX * blockX + blockZ * blockZ);

        if (baseDistance >= landConfig.fallbackStart() + 256.0) return vanillaBiomeSource.getBiome(x, y, z, noise);

        double rawAngle = southClockwiseAngle(blockX, blockZ);
        double angle = wrapNormalized(rawAngle + 0.125) + OrganicNoise.sample(layoutSeed ^ 0x0F0F0F0FL, blockX, blockZ, 540.0, 2) * 0.018;
                
        // Distorção alinhada com as novas montanhas irregulares
        double radialWarp = OrganicNoise.sample(layoutSeed ^ 0x2222L, blockX, blockZ, 200.0, 3) * 70.0;
        double coastWarp = OrganicNoise.sample(layoutSeed ^ 0x3333L, blockX, blockZ, 60.0, 2) * 20.0;
        double irregularWarp = irregularRingWarp(blockX, blockZ, angle, baseDistance);
        double warpedDistance = Math.max(0.0, baseDistance + radialWarp + coastWarp + irregularWarp);
        angle = wrapNormalized(angle);

        if (warpedDistance <= landConfig.centerRadius() + centerNoise(blockX, blockZ)) return landConfig.centerBiome();
        double edgeProgress = MathHelper.clamp((warpedDistance - landConfig.mainRingEnd()) / Math.max(1.0, landConfig.fallbackStart() - landConfig.mainRingEnd()), 0.0, 1.0);
        if (warpedDistance >= landConfig.fallbackStart() || edgeProgress > 0.92) return vanillaBiomeSource.getBiome(x, y, z, noise);

        double sectorAngle = wrapNormalized(
                angle
                        + OrganicNoise.sample(layoutSeed ^ 0x51515151L, blockX, blockZ, 180.0, 3) * 0.05
                        + OrganicNoise.sample(layoutSeed ^ 0x61616161L, baseDistance * 0.2, angle * 2048.0, 1.0, 2) * 0.018
        );

        boolean coldRegion = isColdSector(sectorAngle);
        RegistryEntry<Biome> riverBiome = pickRiverBiome(warpedDistance, sectorAngle, blockX, blockZ, coldRegion);
        if (riverBiome != null) return riverBiome;

        if (warpedDistance < landConfig.innerOceanEnd() + innerIslandNoise(blockX, blockZ)) return pickInnerOceanBiome(sectorAngle, warpedDistance, blockX, blockZ);
        if (warpedDistance < landConfig.mainRingEnd()) return pickLandRingBiome(warpedDistance, sectorAngle, blockX, blockZ);

        return pickOuterOceanBiome(warpedDistance, sectorAngle, blockX, blockZ);
    }

    private RegistryEntry<Biome> pickLandRingBiome(double distance, double angle, double x, double z) {
        Sector sector = findSector(angle);
        double ringProgress = MathHelper.clamp((distance - landConfig.innerOceanEnd()) / Math.max(1.0, landConfig.mainRingEnd() - landConfig.innerOceanEnd()), 0.0, 1.0);
        double innerBeachWidth = 14.0 + Math.abs(OrganicNoise.sample(layoutSeed ^ 0x99887766L, x, z, 150.0, 2)) * 10.0;
        double outerBeachWidth = 18.0 + Math.abs(OrganicNoise.sample(layoutSeed ^ 0xAABBCCDDL, x, z, 180.0, 2)) * 10.0;

        if (distance < landConfig.innerOceanEnd() + innerBeachWidth) return edgeBiomeFor(sector, 0.08, distance, x, z);
        if (distance > landConfig.mainRingEnd() - outerBeachWidth) return edgeBiomeFor(sector, 0.92, distance, x, z);

        return pickBiomeFromSector(sector, ringProgress, distance, x, z, edgeBlend(angle, sector) > 0.70);
    }

    private RegistryEntry<Biome> pickBiomeFromSector(Sector sector, double ringProgress, double distance, double x, double z, boolean favorTransition) {
        List<RegistryEntry<Biome>> pool = sector.biomes();
        if (pool.isEmpty()) return landConfig.centerBiome();

        double mountainPulse = OrganicNoise.sample(layoutSeed ^ sector.name().hashCode(), x, z, 190.0, 2);
        if (sector.name().equals("plains") && ringProgress > 0.74) {
            boolean stoneMountainA = Math.abs(distance - (landConfig.mainRingEnd() - 52.0)) < 34.0 && Math.abs(wrapSigned(sector.localAngle(angleFor(x, z)) - 0.32)) < 0.09;
            boolean stoneMountainB = Math.abs(distance - (landConfig.mainRingEnd() - 78.0)) < 28.0 && Math.abs(wrapSigned(sector.localAngle(angleFor(x, z)) - 0.72)) < 0.07;
            if (stoneMountainA || stoneMountainB) return landConfig.temperateBiomes().stonyPeaksBiome();
        }

        if (sector.name().contains("mountain") || sector.name().contains("snow")) {
            if (mountainPulse > 0.45 && sector.accentBiome() != null) return sector.accentBiome();
        }

        int index = Math.floorMod((int) Math.floor(ringProgress * pool.size() * 1.8 + distance / 135.0 + OrganicNoise.sample(layoutSeed ^ 0x13572468L, x, z, 210.0, 2) * 1.8 + (favorTransition ? 1 : 0)), pool.size());
        return pool.get(index);
    }

    private RegistryEntry<Biome> pickInnerOceanBiome(double angle, double distance, double x, double z) {
        double southness = southBias(angle) + OrganicNoise.sample(layoutSeed ^ 0x12121212L, x, z, 260.0, 2) * 0.08;
        if (southness > 0.38) return oceanConfig.lukewarmOceanBiome();
        if (southness < -0.38) return oceanConfig.coldOceanBiome();
        return oceanConfig.temperateOceanBiome();
    }

    private RegistryEntry<Biome> pickOuterOceanBiome(double distance, double angle, double x, double z) {
        double northSouth = southBias(angle) + OrganicNoise.sample(layoutSeed ^ 0x76543210L, x, z, 430.0, 2) * 0.10;
        double progress = MathHelper.clamp((distance - landConfig.mainRingEnd()) / Math.max(1.0, landConfig.fallbackStart() - landConfig.mainRingEnd()), 0.0, 1.0);
        if (northSouth > 0.42) return progress > 0.55 ? oceanConfig.warmOceanBiome() : oceanConfig.lukewarmOceanBiome();
        if (northSouth < -0.55) return progress > 0.42 ? oceanConfig.frozenOceanBiome() : oceanConfig.coldOceanBiome();
        if (northSouth < -0.22) return progress > 0.68 ? oceanConfig.frozenOceanBiome() : oceanConfig.coldOceanBiome();
        return oceanConfig.temperateOceanBiome();
    }

    private RegistryEntry<Biome> pickRiverBiome(double distance, double angle, double x, double z, boolean coldRegion) {
        if (distance < landConfig.innerOceanEnd() - 8.0 || distance > landConfig.mainRingEnd() - 6.0) return null;

        double riverWarp = distance + OrganicNoise.sample(layoutSeed ^ 0x999L, x, z, 100.0, 2) * 30.0;
        double ringProgress = (riverWarp - landConfig.innerOceanEnd()) / Math.max(1.0, landConfig.mainRingEnd() - landConfig.innerOceanEnd());
        
        // Bloqueia e "mata" os rios no meio do anel (eles nunca atravessam de lado a lado)
        if (ringProgress > 0.25 && ringProgress < 0.75) {
            return null; 
        }

        RegistryEntry<Biome> boundaryRiver = pickBoundaryRiverBiome(distance, angle, x, z);
        if (boundaryRiver != null) return boundaryRiver;

        double largeRiver = radialRiverNoise(layoutSeed ^ 0xABCDABCDL, angle, distance, 0.92, 0.18, 0.07);
        double mediumRiver = radialRiverNoise(layoutSeed ^ 0xDDCCBBAAL, angle, distance, 0.61, 0.14, 0.05);

        if (largeRiver > 0.965 || mediumRiver > 0.978) return coldRegion ? oceanConfig.frozenRiverBiome() : oceanConfig.riverBiome();
        return null;
    }

    private double radialRiverNoise(long seed, double angle, double distance, double angleScale, double distanceScale, double width) {
        double curvedAngle = angle + OrganicNoise.sample(seed ^ 0x1111L, distance, angle * 2048.0, 180.0, 2) * 0.032;
        double line = OrganicNoise.sample(seed, curvedAngle * (1.0 / angleScale), distance * distanceScale, 1.0, 3);
        return 1.0 - Math.abs(line) / width;
    }

    private double irregularRingWarp(double x, double z, double angle, double distance) {
        double continentalWarp = OrganicNoise.sample(layoutSeed ^ 0x6E6F6DADL, x, z, 820.0, 3) * 52.0;
        double regionalWarp = OrganicNoise.sample(layoutSeed ^ 0x1234FEDCL, x, z, 330.0, 3) * 26.0;
        double jaggedWarp = OrganicNoise.sample(layoutSeed ^ 0x55FF11AAL, x, z, 140.0, 2) * 12.0;
        double lobeNoise = OrganicNoise.sample(layoutSeed ^ 0x77EE44CCL, x, z, 620.0, 2) * 0.18;
        double lobes = Math.sin((angle + lobeNoise) * FULL_TURN * 3.0) * 38.0 + Math.cos((angle * 1.7 - lobeNoise) * FULL_TURN * 2.0) * 18.0;
        double fade = MathHelper.clamp(distance / Math.max(1.0, landConfig.mainRingEnd()), 0.25, 1.0);
        return (continentalWarp + regionalWarp + jaggedWarp + lobes) * fade;
    }

    private RegistryEntry<Biome> pickBoundaryRiverBiome(double distance, double angle, double x, double z) {
        double ringProgress = MathHelper.clamp((distance - landConfig.innerOceanEnd()) / Math.max(1.0, landConfig.mainRingEnd() - landConfig.innerOceanEnd()), 0.0, 1.0);
        if (ringProgress < 0.20 || ringProgress > 0.84) return null;

        for (int i = 0; i < sectors.size(); i++) {
            Sector sector = sectors.get(i);
            Sector previous = sectors.get((i - 1 + sectors.size()) % sectors.size());
            double boundary = sector.start();
            double wiggle = OrganicNoise.sample(layoutSeed ^ (0x44AA7711L + i), x, z, 210.0, 2) * 0.018 + OrganicNoise.sample(layoutSeed ^ (0x9911BB22L + i), x, z, 92.0, 2) * 0.010;
            double distanceToBoundary = Math.abs(wrapSigned(angle - wrapNormalized(boundary + wiggle)));
            double boundaryWidth = 0.006 + Math.abs(OrganicNoise.sample(layoutSeed ^ (0xCAFED00DL + i), x, z, 150.0, 2)) * 0.010;
            double riverNoise = OrganicNoise.sample(layoutSeed ^ (0xAB12CD34L + i), x, z, 120.0, 3);
            double radialNoise = OrganicNoise.sample(layoutSeed ^ (0xF0E1D2C3L + i), distance * 0.23, angle * 2048.0, 1.0, 2);
            boolean corridor = distanceToBoundary < boundaryWidth && riverNoise + radialNoise * 0.35 > 0.20;
            
            if (!corridor) continue;

            boolean frozen = previous.climate() == Climate.COLD || previous.climate() == Climate.ALPINE || sector.climate() == Climate.COLD || sector.climate() == Climate.ALPINE;
            return frozen ? oceanConfig.frozenRiverBiome() : oceanConfig.riverBiome();
        }
        return null;
    }

    private RegistryEntry<Biome> edgeBiomeFor(Sector sector, double ringProgress, double distance, double x, double z) {
        if (!sector.hasBeach()) return pickBiomeFromSector(sector, ringProgress, distance, x, z, true);
        return beachFor(sector.climate());
    }

    private RegistryEntry<Biome> beachFor(Climate climate) {
        return climate == Climate.COLD || climate == Climate.ALPINE ? oceanConfig.snowyBeachBiome() : oceanConfig.beachBiome();
    }

    private boolean isColdSector(double angle) {
        Sector sector = findSector(angle);
        return sector.climate() == Climate.COLD || sector.climate() == Climate.ALPINE;
    }

    private Sector findSector(double angle) {
        for (Sector sector : sectors) {
            if (angle >= sector.start() && angle < sector.end()) return sector;
        }
        return sectors.get(sectors.size() - 1);
    }

    private double edgeBlend(double angle, Sector sector) {
        double nearestEdge = Math.min(Math.abs(wrapSigned(angle - sector.start())), Math.abs(wrapSigned(angle - sector.end())));
        double width = 0.012 + Math.abs(OrganicNoise.sample(layoutSeed ^ 0xDEADBEEFL, angle * 4096.0, sector.midpoint() * 4096.0, 1.0, 2)) * 0.02;
        return 1.0 - MathHelper.clamp(nearestEdge / width, 0.0, 1.0);
    }

    private double centerNoise(double x, double z) { return OrganicNoise.sample(layoutSeed ^ 0xACACACACL, x, z, 90.0, 2) * 4.5; }
    private double innerIslandNoise(double x, double z) { return OrganicNoise.sample(layoutSeed ^ 0xCDCDCDCDL, x, z, 100.0, 2) * 6.0; }
    private double southClockwiseAngle(double x, double z) { return wrapNormalized(Math.atan2(-x, z) / FULL_TURN); }
    private double angleFor(double x, double z) { return southClockwiseAngle(x, z); }
    private double southBias(double angle) { return Math.cos(angle * FULL_TURN); }

    private double wrapNormalized(double value) {
        double wrapped = value % 1.0;
        return wrapped < 0.0 ? wrapped + 1.0 : wrapped;
    }

    private double wrapSigned(double delta) {
        double wrapped = (delta + 0.5) % 1.0;
        if (wrapped < 0.0) wrapped += 1.0;
        return wrapped - 0.5;
    }

    private List<Sector> buildSectors() {
        List<Sector> built = new ArrayList<>();
        double cursor = 0.0;
        cursor = addSector(built, cursor, 0.11, "badlands", Climate.HOT, true, List.of(landConfig.warmBiomes().badlandsBiome(), landConfig.warmBiomes().woodedBadlandsBiome(), landConfig.warmBiomes().badlandsPlateauBiome(), landConfig.warmBiomes().erodedBadlandsBiome()), landConfig.warmBiomes().erodedBadlandsBiome());
        cursor = addSector(built, cursor, 0.065, "savanna", Climate.WARM, true, List.of(landConfig.warmBiomes().savannaBiome(), landConfig.warmBiomes().savannaPlateauBiome(), landConfig.warmBiomes().windsweptSavannaBiome()), landConfig.warmBiomes().windsweptSavannaBiome());
        cursor = addSector(built, cursor, 0.08, "desert", Climate.HOT, true, List.of(landConfig.warmBiomes().desertBiome(), landConfig.warmBiomes().desertBiome(), landConfig.warmBiomes().desertBiome()), null);
        cursor = addSector(built, cursor, 0.075, "mountain_transition", Climate.ALPINE, true, List.of(landConfig.temperateBiomes().windsweptHillsBiome(), landConfig.temperateBiomes().stonyPeaksBiome(), landConfig.temperateBiomes().stonyPeaksBiome()), landConfig.temperateBiomes().stonyPeaksBiome());
        cursor = addSector(built, cursor, 0.07, "temperate_mountain_edge", Climate.TEMPERATE, true, List.of(landConfig.temperateBiomes().cherryGroveBiome(), landConfig.temperateBiomes().forestBiome(), landConfig.temperateBiomes().flowerForestBiome()), landConfig.temperateBiomes().cherryGroveBiome());
        cursor = addSector(built, cursor, 0.09, "plains", Climate.TEMPERATE, true, List.of(landConfig.temperateBiomes().plainsBiome(), landConfig.temperateBiomes().plainsBiome(), landConfig.temperateBiomes().plainsBiome()), landConfig.temperateBiomes().stonyPeaksBiome());
        cursor = addSector(built, cursor, 0.08, "taiga", Climate.COOL, true, List.of(landConfig.temperateBiomes().taigaBiome(), landConfig.temperateBiomes().oldGrowthTaigaBiome()), landConfig.temperateBiomes().oldGrowthTaigaBiome());
        cursor = addSector(built, cursor, 0.055, "cold_taiga", Climate.COLD, true, List.of(landConfig.coldBiomes().snowyTaigaBiome(), landConfig.coldBiomes().snowyTaigaBiome()), landConfig.coldBiomes().snowyTaigaBiome());
        cursor = addSector(built, cursor, 0.09, "snow_mountains", Climate.ALPINE, true, List.of(landConfig.coldBiomes().snowyPlainsBiome(), landConfig.coldBiomes().frozenPeaksBiome(), landConfig.coldBiomes().jaggedPeaksBiome()), landConfig.coldBiomes().frozenPeaksBiome());
        cursor = addSector(built, cursor, 0.08, "forest_pale", Climate.TEMPERATE, true, List.of(landConfig.temperateBiomes().forestBiome(), landConfig.temperateBiomes().forestBiome(), landConfig.temperateBiomes().flowerForestBiome()), landConfig.temperateBiomes().paleGardenBiome());
        cursor = addSector(built, cursor, 0.075, "swamp", Climate.WARM, false, List.of(landConfig.wetBiomes().swampBiome(), landConfig.wetBiomes().mangroveSwampBiome()), landConfig.wetBiomes().mangroveSwampBiome());
        cursor = addSector(built, cursor, 0.05, "dark_forest_band", Climate.TEMPERATE, false, List.of(landConfig.temperateBiomes().darkForestBiome(), landConfig.temperateBiomes().darkForestBiome()), landConfig.temperateBiomes().paleGardenBiome());
        addSector(built, cursor, 1.0 - cursor, "jungle", Climate.WARM, false, List.of(landConfig.warmBiomes().jungleBiome(), landConfig.warmBiomes().sparseJungleBiome(), landConfig.warmBiomes().bambooJungleBiome()), landConfig.warmBiomes().bambooJungleBiome());
        return List.copyOf(built);
    }

    private double addSector(List<Sector> built, double start, double span, String name, Climate climate, boolean hasBeach, List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> accentBiome) {
        built.add(new Sector(name, start, start + span, climate, hasBeach, List.copyOf(biomes), accentBiome));
        return start + span;
    }

    private List<RegistryEntry<Biome>> collectBiomes() {
        Set<RegistryEntry<Biome>> collected = new LinkedHashSet<>();
        collected.add(landConfig.centerBiome());
        for (Sector sector : sectors) {
            collected.addAll(sector.biomes());
            if (sector.accentBiome() != null) collected.add(sector.accentBiome());
        }
        collected.add(landConfig.temperateBiomes().paleGardenBiome());
        collected.add(landConfig.temperateBiomes().darkForestBiome());
        collected.add(landConfig.temperateBiomes().stonyPeaksBiome());
        collected.add(landConfig.temperateBiomes().forestBiome());
        collected.add(landConfig.warmBiomes().savannaBiome());
        collected.add(oceanConfig.temperateOceanBiome());
        collected.add(oceanConfig.coldOceanBiome());
        collected.add(oceanConfig.frozenOceanBiome());
        collected.add(oceanConfig.warmOceanBiome());
        collected.add(oceanConfig.lukewarmOceanBiome());
        collected.add(oceanConfig.beachBiome());
        collected.add(oceanConfig.snowyBeachBiome());
        collected.add(oceanConfig.riverBiome());
        collected.add(oceanConfig.frozenRiverBiome());
        return new ArrayList<>(collected);
    }

    private MultiNoiseBiomeSource vanillaBiomeSource() { return vanillaBiomeSource; }
    private long layoutSeed() { return layoutSeed; }
    private int centerX() { return centerX; }
    private int centerZ() { return centerZ; }
    private LandConfig landConfig() { return landConfig; }
    private OceanConfig oceanConfig() { return oceanConfig; }

    private record Sector(String name, double start, double end, Climate climate, boolean hasBeach, List<RegistryEntry<Biome>> biomes, RegistryEntry<Biome> accentBiome) {
        private double midpoint() { return (start + end) * 0.5; }
        private double span() { return end - start; }
        private double localAngle(double angle) { return MathHelper.clamp((angle - start) / Math.max(1.0E-6, span()), 0.0, 1.0); }
    }

    public record LandConfig(RegistryEntry<Biome> centerBiome, int centerRadius, int innerOceanEnd, int mainRingEnd, int fallbackStart, WarmBiomes warmBiomes, TemperateBiomes temperateBiomes, ColdBiomes coldBiomes, WetBiomes wetBiomes) {
        public static final MapCodec<LandConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("center_biome").forGetter(LandConfig::centerBiome), Codec.intRange(1, 256).optionalFieldOf("center_radius", 54).forGetter(LandConfig::centerRadius), Codec.intRange(1, 2048).optionalFieldOf("inner_ocean_end", 234).forGetter(LandConfig::innerOceanEnd), Codec.intRange(1, 4096).optionalFieldOf("main_ring_end", 864).forGetter(LandConfig::mainRingEnd), Codec.intRange(256, 8192).optionalFieldOf("fallback_start", 2048).forGetter(LandConfig::fallbackStart), WarmBiomes.CODEC.fieldOf("warm_biomes").forGetter(LandConfig::warmBiomes), TemperateBiomes.CODEC.fieldOf("temperate_biomes").forGetter(LandConfig::temperateBiomes), ColdBiomes.CODEC.fieldOf("cold_biomes").forGetter(LandConfig::coldBiomes), WetBiomes.CODEC.fieldOf("wet_biomes").forGetter(LandConfig::wetBiomes)).apply(instance, LandConfig::new));
        public static final Codec<LandConfig> CODEC = MAP_CODEC.codec();
    }

    public record WarmBiomes(RegistryEntry<Biome> badlandsBiome, RegistryEntry<Biome> woodedBadlandsBiome, RegistryEntry<Biome> badlandsPlateauBiome, RegistryEntry<Biome> erodedBadlandsBiome, RegistryEntry<Biome> savannaBiome, RegistryEntry<Biome> savannaPlateauBiome, RegistryEntry<Biome> windsweptSavannaBiome, RegistryEntry<Biome> desertBiome, RegistryEntry<Biome> jungleBiome, RegistryEntry<Biome> sparseJungleBiome, RegistryEntry<Biome> bambooJungleBiome) {
        public static final MapCodec<WarmBiomes> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("badlands_biome").forGetter(WarmBiomes::badlandsBiome), BIOME_CODEC.fieldOf("wooded_badlands_biome").forGetter(WarmBiomes::woodedBadlandsBiome), BIOME_CODEC.fieldOf("badlands_plateau_biome").forGetter(WarmBiomes::badlandsPlateauBiome), BIOME_CODEC.fieldOf("eroded_badlands_biome").forGetter(WarmBiomes::erodedBadlandsBiome), BIOME_CODEC.fieldOf("savanna_biome").forGetter(WarmBiomes::savannaBiome), BIOME_CODEC.fieldOf("savanna_plateau_biome").forGetter(WarmBiomes::savannaPlateauBiome), BIOME_CODEC.fieldOf("windswept_savanna_biome").forGetter(WarmBiomes::windsweptSavannaBiome), BIOME_CODEC.fieldOf("desert_biome").forGetter(WarmBiomes::desertBiome), BIOME_CODEC.fieldOf("jungle_biome").forGetter(WarmBiomes::jungleBiome), BIOME_CODEC.fieldOf("sparse_jungle_biome").forGetter(WarmBiomes::sparseJungleBiome), BIOME_CODEC.fieldOf("bamboo_jungle_biome").forGetter(WarmBiomes::bambooJungleBiome)).apply(instance, WarmBiomes::new));
        public static final Codec<WarmBiomes> CODEC = MAP_CODEC.codec();
    }

    public record TemperateBiomes(RegistryEntry<Biome> windsweptHillsBiome, RegistryEntry<Biome> stonyPeaksBiome, RegistryEntry<Biome> cherryGroveBiome, RegistryEntry<Biome> forestBiome, RegistryEntry<Biome> flowerForestBiome, RegistryEntry<Biome> plainsBiome, RegistryEntry<Biome> taigaBiome, RegistryEntry<Biome> oldGrowthTaigaBiome, RegistryEntry<Biome> paleGardenBiome, RegistryEntry<Biome> darkForestBiome) {
        public static final MapCodec<TemperateBiomes> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("windswept_hills_biome").forGetter(TemperateBiomes::windsweptHillsBiome), BIOME_CODEC.fieldOf("stony_peaks_biome").forGetter(TemperateBiomes::stonyPeaksBiome), BIOME_CODEC.fieldOf("cherry_grove_biome").forGetter(TemperateBiomes::cherryGroveBiome), BIOME_CODEC.fieldOf("forest_biome").forGetter(TemperateBiomes::forestBiome), BIOME_CODEC.fieldOf("flower_forest_biome").forGetter(TemperateBiomes::flowerForestBiome), BIOME_CODEC.fieldOf("plains_biome").forGetter(TemperateBiomes::plainsBiome), BIOME_CODEC.fieldOf("taiga_biome").forGetter(TemperateBiomes::taigaBiome), BIOME_CODEC.fieldOf("old_growth_taiga_biome").forGetter(TemperateBiomes::oldGrowthTaigaBiome), BIOME_CODEC.fieldOf("pale_garden_biome").forGetter(TemperateBiomes::paleGardenBiome), BIOME_CODEC.fieldOf("dark_forest_biome").forGetter(TemperateBiomes::darkForestBiome)).apply(instance, TemperateBiomes::new));
        public static final Codec<TemperateBiomes> CODEC = MAP_CODEC.codec();
    }

    public record ColdBiomes(RegistryEntry<Biome> snowyTaigaBiome, RegistryEntry<Biome> snowyPlainsBiome, RegistryEntry<Biome> frozenPeaksBiome, RegistryEntry<Biome> jaggedPeaksBiome) {
        public static final MapCodec<ColdBiomes> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("snowy_taiga_biome").forGetter(ColdBiomes::snowyTaigaBiome), BIOME_CODEC.fieldOf("snowy_plains_biome").forGetter(ColdBiomes::snowyPlainsBiome), BIOME_CODEC.fieldOf("frozen_peaks_biome").forGetter(ColdBiomes::frozenPeaksBiome), BIOME_CODEC.fieldOf("jagged_peaks_biome").forGetter(ColdBiomes::jaggedPeaksBiome)).apply(instance, ColdBiomes::new));
        public static final Codec<ColdBiomes> CODEC = MAP_CODEC.codec();
    }

    public record WetBiomes(RegistryEntry<Biome> swampBiome, RegistryEntry<Biome> mangroveSwampBiome) {
        public static final MapCodec<WetBiomes> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("swamp_biome").forGetter(WetBiomes::swampBiome), BIOME_CODEC.fieldOf("mangrove_swamp_biome").forGetter(WetBiomes::mangroveSwampBiome)).apply(instance, WetBiomes::new));
        public static final Codec<WetBiomes> CODEC = MAP_CODEC.codec();
    }

    public record OceanConfig(RegistryEntry<Biome> temperateOceanBiome, RegistryEntry<Biome> coldOceanBiome, RegistryEntry<Biome> frozenOceanBiome, RegistryEntry<Biome> warmOceanBiome, RegistryEntry<Biome> lukewarmOceanBiome, RegistryEntry<Biome> beachBiome, RegistryEntry<Biome> snowyBeachBiome, RegistryEntry<Biome> riverBiome, RegistryEntry<Biome> frozenRiverBiome) {
        public static final MapCodec<OceanConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BIOME_CODEC.fieldOf("temperate_ocean_biome").forGetter(OceanConfig::temperateOceanBiome), BIOME_CODEC.fieldOf("cold_ocean_biome").forGetter(OceanConfig::coldOceanBiome), BIOME_CODEC.fieldOf("frozen_ocean_biome").forGetter(OceanConfig::frozenOceanBiome), BIOME_CODEC.fieldOf("warm_ocean_biome").forGetter(OceanConfig::warmOceanBiome), BIOME_CODEC.fieldOf("lukewarm_ocean_biome").forGetter(OceanConfig::lukewarmOceanBiome), BIOME_CODEC.fieldOf("beach_biome").forGetter(OceanConfig::beachBiome), BIOME_CODEC.fieldOf("snowy_beach_biome").forGetter(OceanConfig::snowyBeachBiome), BIOME_CODEC.fieldOf("river_biome").forGetter(OceanConfig::riverBiome), BIOME_CODEC.fieldOf("frozen_river_biome").forGetter(OceanConfig::frozenRiverBiome)).apply(instance, OceanConfig::new));
        public static final Codec<OceanConfig> CODEC = MAP_CODEC.codec();
    }

    private enum Climate { COLD, COOL, TEMPERATE, WARM, HOT, ALPINE }
}
