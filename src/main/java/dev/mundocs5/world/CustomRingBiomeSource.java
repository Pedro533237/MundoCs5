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
 * BiomeSource wrapper para anel continental orgânico com listas de biomas por macro-região.
 */
public class CustomRingBiomeSource extends BiomeSource {
    private static final Codec<RegistryEntry<Biome>> BIOME_CODEC = RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC);

    public static final MapCodec<CustomRingBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MultiNoiseBiomeSource.CODEC.fieldOf("vanilla").forGetter(CustomRingBiomeSource::vanilla),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(CustomRingBiomeSource::seed),
            RingConfig.CODEC.optionalFieldOf("ring", RingConfig.DEFAULT).forGetter(CustomRingBiomeSource::ring),
            BiomeConfig.CODEC.fieldOf("biomes").forGetter(CustomRingBiomeSource::biomes)
    ).apply(instance, CustomRingBiomeSource::new));

    private final MultiNoiseBiomeSource vanilla;
    private final long seed;
    private final RingConfig ring;
    private final BiomeConfig biomes;
    private final List<RegistryEntry<Biome>> streamBiomes;

    public CustomRingBiomeSource(MultiNoiseBiomeSource vanilla, long seed, RingConfig ring, BiomeConfig biomes) {
        this.vanilla = vanilla;
        this.seed = seed;
        this.ring = ring;
        this.biomes = biomes;
        this.streamBiomes = collectBiomes();
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return streamBiomes.stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int biomeX, int biomeY, int biomeZ, MultiNoiseUtil.MultiNoiseSampler noise) {
        double x = biomeX * 4.0;
        double y = biomeY * 4.0;
        double z = biomeZ * 4.0;
        WarpPoint wp = warped(seed, ring, x, z);

        // > 3000: vanilla puro
        if (wp.radius >= ring.vanillaRadius()) {
            return vanilla.getBiome(biomeX, biomeY, biomeZ, noise);
        }

        // 2800..3000: blend suave probabilístico para vanilla
        if (wp.radius >= ring.outerBlendStart()) {
            double t = smooth01((wp.radius - ring.outerBlendStart()) / Math.max(1.0, ring.vanillaRadius() - ring.outerBlendStart()));
            double chooser = OrganicNoise.sample(seed ^ 0x9E3779B97F4A7C15L, x, z, 104.0, 2) * 0.5 + 0.5;
            if (chooser < t) {
                return vanilla.getBiome(biomeX, biomeY, biomeZ, noise);
            }
        }

        // 0..60: campo/costa de cogumelos
        if (wp.radius < ring.centerRadius() - 8.0) {
            return biomes.mushroomFields();
        }
        if (wp.radius < ring.centerRadius()) {
            return biomes.mushroomShore();
        }

        // 60..300: lago interno profundo
        if (wp.radius < ring.innerLakeEnd()) {
            return biomes.deepOcean();
        }

        // 300..1300: anel continental + rios
        if (wp.radius < ring.continentEnd()) {
            if (y < -40.0) {
                return biomes.deepDark();
            }
            if (y < 30.0) {
                double caveNoise = OrganicNoise.sample(seed ^ 0xC6EF372FE94F82BL, x, z, 110.0, 2);
                return caveNoise > 0.0 ? biomes.lushCaves() : biomes.dripstoneCaves();
            }
            if (isRiver(x, z, wp.radius)) {
                return biomes.river();
            }
            return pickRingBiome(wp.angle, x, z);
        }

        // 1300..2800: oceano externo profundo
        return biomes.deepOcean();
    }

    private boolean isRiver(double x, double z, double radius) {
        if (radius < ring.innerLakeEnd() || radius > ring.continentEnd()) {
            return false;
        }
        double river = OrganicNoise.sample(seed ^ 0xBB67AE8584CAA73BL, x, z, ring.riverFrequency(), 3);
        return Math.abs(river) < ring.riverThreshold();
    }

    private RegistryEntry<Biome> pickRingBiome(double angle01, double x, double z) {
        double regionNoise = OrganicNoise.sample(seed ^ 0x3C6EF372FE94F82BL, x, z, ring.subBiomeFrequency(), 3);
        double patchNoise = OrganicNoise.sample(seed ^ 0xA54FF53A5F1D36F1L, x, z, ring.patchFrequency(), 4);
        double mountainNoise = OrganicNoise.sample(seed ^ 0x510E527FADE682D1L, x, z, ring.mountainFrequency(), 3);

        // 0.60..0.75 => faixa noroeste nevada (taiga/frio + picos)
        if (angle01 >= 0.60 && angle01 < 0.75) {
            if (mountainNoise > 0.28) {
                return pickFrom(biomes.mountainsRelief(), x, z, 31.0, 1.7);
            }
            return pickFrom(biomes.taigaCold(), x, z, 37.0, 1.9);
        }

        // 0.75..1.0 e 0.0..0.15 => florestas escuras / vegetação densa
        if (angle01 >= 0.75 || angle01 < 0.15) {
            if (regionNoise > 0.42) {
                return biomes.paleGarden();
            }
            return pickFrom(biomes.forestsVegetation(), x, z, 33.0, 1.8);
        }

        // 0.15..0.25 => selva/tropical
        if (angle01 < 0.25) {
            return pickFrom(biomes.jungleTropical(), x, z, 29.0, 1.6);
        }

        // 0.25..0.40 => quente (deserto/savana/badlands)
        if (angle01 < 0.40) {
            if (patchNoise > 0.18) {
                return pickFrom(biomes.badlandsFamily(), x, z, 27.0, 1.9);
            }
            return pickFrom(biomes.savannaArid(), x, z, 35.0, 1.7);
        }

        // 0.40..0.60 => plains/fields com manchas de floresta + stony peaks pontual
        if (mountainNoise > ring.stonyPeaksThreshold()) {
            return biomes.stonyPeaks();
        }
        if (patchNoise > 0.42) {
            return pickFrom(biomes.forestsVegetation(), x, z, 31.0, 2.2);
        }
        if (patchNoise < -0.30) {
            return pickFrom(biomes.othersWetAndCoasts(), x, z, 41.0, 1.4);
        }
        return pickFrom(biomes.plainsAndFields(), x, z, 39.0, 1.3);
    }

    private RegistryEntry<Biome> pickFrom(List<RegistryEntry<Biome>> pool, double x, double z, double freq, double stretch) {
        if (pool.isEmpty()) {
            return biomes.mushroomFields();
        }
        double n = OrganicNoise.sample(seed ^ 0x1F83D9ABFB41BD6BL, x * stretch, z, freq, 2) * 0.5 + 0.5;
        int index = MathHelper.clamp((int) Math.floor(n * pool.size()), 0, pool.size() - 1);
        return pool.get(index);
    }

    private List<RegistryEntry<Biome>> collectBiomes() {
        Set<RegistryEntry<Biome>> unique = new LinkedHashSet<>();
        unique.add(biomes.mushroomFields());
        unique.add(biomes.mushroomShore());
        unique.add(biomes.deepOcean());
        unique.add(biomes.river());
        unique.add(biomes.paleGarden());
        unique.add(biomes.stonyPeaks());
        unique.add(biomes.dripstoneCaves());
        unique.add(biomes.lushCaves());
        unique.add(biomes.deepDark());

        unique.addAll(biomes.forestsVegetation());
        unique.addAll(biomes.jungleTropical());
        unique.addAll(biomes.plainsAndFields());
        unique.addAll(biomes.savannaArid());
        unique.addAll(biomes.badlandsFamily());
        unique.addAll(biomes.taigaCold());
        unique.addAll(biomes.mountainsRelief());
        unique.addAll(biomes.othersWetAndCoasts());

        return new ArrayList<>(unique);
    }

    public static WarpPoint warped(long seed, RingConfig config, double x, double z) {
        double warpedX = x + OrganicNoise.sample(seed ^ 0x243F6A8885A308D3L, x, z, config.warpFrequency(), 3) * config.warpAmplitude();
        double warpedZ = z + OrganicNoise.sample(seed ^ 0x13198A2E03707344L, x + 999.0, z + 999.0, config.warpFrequency(), 3) * config.warpAmplitude();
        double radius = Math.sqrt(warpedX * warpedX + warpedZ * warpedZ);
        double angle = (Math.atan2(warpedZ, warpedX) / (Math.PI * 2.0) + 1.0) % 1.0;
        return new WarpPoint(warpedX, warpedZ, radius, angle);
    }

    private static double smooth01(double t) {
        double c = MathHelper.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - (2.0 * c));
    }

    private MultiNoiseBiomeSource vanilla() {
        return vanilla;
    }

    private long seed() {
        return seed;
    }

    private RingConfig ring() {
        return ring;
    }

    private BiomeConfig biomes() {
        return biomes;
    }

    public record WarpPoint(double warpedX, double warpedZ, double radius, double angle) {
    }

    public record RingConfig(
            int centerRadius,
            int innerLakeEnd,
            int continentEnd,
            int outerBlendStart,
            int vanillaRadius,
            double warpFrequency,
            double warpAmplitude,
            double riverFrequency,
            double riverThreshold,
            double subBiomeFrequency,
            double patchFrequency,
            double mountainFrequency,
            double stonyPeaksThreshold
    ) {
        public static final RingConfig DEFAULT = new RingConfig(60, 300, 1300, 2800, 3000, 760.0, 180.0, 160.0, 0.065, 96.0, 54.0, 120.0, 0.56);

        public static final MapCodec<RingConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 256).optionalFieldOf("center_radius", DEFAULT.centerRadius).forGetter(RingConfig::centerRadius),
                Codec.intRange(1, 2000).optionalFieldOf("inner_lake_end", DEFAULT.innerLakeEnd).forGetter(RingConfig::innerLakeEnd),
                Codec.intRange(1, 5000).optionalFieldOf("continent_end", DEFAULT.continentEnd).forGetter(RingConfig::continentEnd),
                Codec.intRange(1, 9000).optionalFieldOf("outer_blend_start", DEFAULT.outerBlendStart).forGetter(RingConfig::outerBlendStart),
                Codec.intRange(1, 12000).optionalFieldOf("vanilla_radius", DEFAULT.vanillaRadius).forGetter(RingConfig::vanillaRadius),
                Codec.doubleRange(8.0, 4000.0).optionalFieldOf("warp_frequency", DEFAULT.warpFrequency).forGetter(RingConfig::warpFrequency),
                Codec.doubleRange(0.0, 800.0).optionalFieldOf("warp_amplitude", DEFAULT.warpAmplitude).forGetter(RingConfig::warpAmplitude),
                Codec.doubleRange(8.0, 2400.0).optionalFieldOf("river_frequency", DEFAULT.riverFrequency).forGetter(RingConfig::riverFrequency),
                Codec.doubleRange(0.001, 0.9).optionalFieldOf("river_threshold", DEFAULT.riverThreshold).forGetter(RingConfig::riverThreshold),
                Codec.doubleRange(8.0, 1600.0).optionalFieldOf("sub_biome_frequency", DEFAULT.subBiomeFrequency).forGetter(RingConfig::subBiomeFrequency),
                Codec.doubleRange(8.0, 600.0).optionalFieldOf("patch_frequency", DEFAULT.patchFrequency).forGetter(RingConfig::patchFrequency),
                Codec.doubleRange(8.0, 1000.0).optionalFieldOf("mountain_frequency", DEFAULT.mountainFrequency).forGetter(RingConfig::mountainFrequency),
                Codec.doubleRange(-1.0, 1.0).optionalFieldOf("stony_peaks_threshold", DEFAULT.stonyPeaksThreshold).forGetter(RingConfig::stonyPeaksThreshold)
        ).apply(instance, RingConfig::new));

        public static final Codec<RingConfig> CODEC = MAP_CODEC.codec();
    }

    public record BiomeConfig(
            CoreBiomes core,
            List<RegistryEntry<Biome>> forestsVegetation,
            List<RegistryEntry<Biome>> jungleTropical,
            List<RegistryEntry<Biome>> plainsAndFields,
            List<RegistryEntry<Biome>> savannaArid,
            List<RegistryEntry<Biome>> badlandsFamily,
            List<RegistryEntry<Biome>> taigaCold,
            List<RegistryEntry<Biome>> mountainsRelief,
            List<RegistryEntry<Biome>> othersWetAndCoasts
    ) {
        private static final Codec<List<RegistryEntry<Biome>>> BIOME_LIST_CODEC = BIOME_CODEC.listOf().xmap(List::copyOf, List::copyOf);

        public static final MapCodec<BiomeConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CoreBiomes.CODEC.fieldOf("core").forGetter(BiomeConfig::core),
                BIOME_LIST_CODEC.fieldOf("forests_vegetation").forGetter(BiomeConfig::forestsVegetation),
                BIOME_LIST_CODEC.fieldOf("jungle_tropical").forGetter(BiomeConfig::jungleTropical),
                BIOME_LIST_CODEC.fieldOf("plains_fields").forGetter(BiomeConfig::plainsAndFields),
                BIOME_LIST_CODEC.fieldOf("savanna_arid").forGetter(BiomeConfig::savannaArid),
                BIOME_LIST_CODEC.fieldOf("badlands_family").forGetter(BiomeConfig::badlandsFamily),
                BIOME_LIST_CODEC.fieldOf("taiga_cold").forGetter(BiomeConfig::taigaCold),
                BIOME_LIST_CODEC.fieldOf("mountains_relief").forGetter(BiomeConfig::mountainsRelief),
                BIOME_LIST_CODEC.fieldOf("others_wet_coasts").forGetter(BiomeConfig::othersWetAndCoasts)
        ).apply(instance, BiomeConfig::new));

        public static final Codec<BiomeConfig> CODEC = MAP_CODEC.codec();

        public RegistryEntry<Biome> mushroomFields() { return core.mushroomFields(); }
        public RegistryEntry<Biome> mushroomShore() { return core.mushroomShore(); }
        public RegistryEntry<Biome> deepOcean() { return core.deepOcean(); }
        public RegistryEntry<Biome> river() { return core.river(); }
        public RegistryEntry<Biome> dripstoneCaves() { return core.dripstoneCaves(); }
        public RegistryEntry<Biome> lushCaves() { return core.lushCaves(); }
        public RegistryEntry<Biome> deepDark() { return core.deepDark(); }
        public RegistryEntry<Biome> paleGarden() { return core.paleGarden(); }
        public RegistryEntry<Biome> stonyPeaks() { return core.stonyPeaks(); }
    }

    public record CoreBiomes(
            RegistryEntry<Biome> mushroomFields,
            RegistryEntry<Biome> mushroomShore,
            RegistryEntry<Biome> deepOcean,
            RegistryEntry<Biome> river,
            RegistryEntry<Biome> dripstoneCaves,
            RegistryEntry<Biome> lushCaves,
            RegistryEntry<Biome> deepDark,
            RegistryEntry<Biome> paleGarden,
            RegistryEntry<Biome> stonyPeaks
    ) {
        public static final MapCodec<CoreBiomes> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BIOME_CODEC.fieldOf("mushroom_fields").forGetter(CoreBiomes::mushroomFields),
                BIOME_CODEC.fieldOf("mushroom_shore").forGetter(CoreBiomes::mushroomShore),
                BIOME_CODEC.fieldOf("deep_ocean").forGetter(CoreBiomes::deepOcean),
                BIOME_CODEC.fieldOf("river").forGetter(CoreBiomes::river),
                BIOME_CODEC.fieldOf("dripstone_caves").forGetter(CoreBiomes::dripstoneCaves),
                BIOME_CODEC.fieldOf("lush_caves").forGetter(CoreBiomes::lushCaves),
                BIOME_CODEC.fieldOf("deep_dark").forGetter(CoreBiomes::deepDark),
                BIOME_CODEC.fieldOf("pale_garden").forGetter(CoreBiomes::paleGarden),
                BIOME_CODEC.fieldOf("stony_peaks").forGetter(CoreBiomes::stonyPeaks)
        ).apply(instance, CoreBiomes::new));

        public static final Codec<CoreBiomes> CODEC = MAP_CODEC.codec();
    }
}
