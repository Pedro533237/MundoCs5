package dev.mundocs5.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

public class PizzaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PizzaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PizzaBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> (PizzaBiomeSource) generator.getBiomeSource()),
            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(PizzaChunkGenerator::settings)
    ).apply(instance, PizzaChunkGenerator::new));

    private static final double FULL_TURN = Math.PI * 2.0;
    private final PizzaBiomeSource biomeSource;
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final NoiseChunkGenerator delegate;

    public PizzaChunkGenerator(PizzaBiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.settings = settings;
        this.delegate = new NoiseChunkGenerator(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<ChunkGeneratorSettings> settings() {
        return settings;
    }

    @Override
    public CompletableFuture<Chunk> populateBiomes(NoiseConfig noiseConfig, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk) {
        delegate.carve(region, seed, noiseConfig, biomeAccess, structureAccessor, chunk);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structureAccessor, NoiseConfig noiseConfig, Chunk chunk) {
        delegate.buildSurface(region, structureAccessor, noiseConfig, chunk);
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        delegate.populateEntities(region);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk).thenApply(generated -> {
            shapePizzaTerrain(generated);
            return generated;
        });
    }

    @Override
    public int getWorldHeight() {
        return delegate.getWorldHeight();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getMinimumY() {
        return delegate.getMinimumY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getHeight(x, z, heightmap, world, noiseConfig);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getColumnSample(x, z, world, noiseConfig);
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        delegate.appendDebugHudText(text, noiseConfig, pos);
        text.add("Pizza terrain: radial chunk generator active");
    }

    private void shapePizzaTerrain(Chunk chunk) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int seaLevel = getSeaLevel();
        int minY = getMinimumY();
        int maxY = minY + chunk.getHeight() - 1;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = startX + localX;
                int worldZ = startZ + localZ;
                double distance = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);

                if (distance > 2048.0) {
                    continue;
                }

                int existingTop = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(localX >> 2, Math.max(minY, existingTop) >> 2, localZ >> 2);
                int targetHeight = sampleTerrainHeight(worldX, worldZ, seaLevel, existingTop, biome);
                BlockState topBlock = surfaceBlockFor(biome, targetHeight, seaLevel);
                BlockState fillerBlock = fillerBlockFor(biome);

                for (int y = minY; y <= maxY; y++) {
                    mutable.set(worldX, y, worldZ);
                    if (y > targetHeight) {
                        if (y <= seaLevel) {
                            chunk.setBlockState(mutable, Blocks.WATER.getDefaultState(), 0);
                        } else {
                            chunk.setBlockState(mutable, Blocks.AIR.getDefaultState(), 0);
                        }
                        continue;
                    }

                    if (y == targetHeight) {
                        chunk.setBlockState(mutable, topBlock, 0);
                    } else if (y >= targetHeight - 3) {
                        chunk.setBlockState(mutable, fillerBlock, 0);
                    } else {
                        chunk.setBlockState(mutable, stoneFor(biome, y, targetHeight), 0);
                    }
                }
            }
        }
    }

    private int sampleTerrainHeight(int x, int z, int seaLevel, int existingTop, RegistryEntry<Biome> biome) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        double warpedDistance = distance
                + OrganicNoise.sample(0x600DF00DL, x, z, 210.0, 3) * 24.0
                + OrganicNoise.sample(0x13579BDFL, x, z, 90.0, 2) * 8.0;
        double angle = southClockwiseAngle(x, z);
        double continentNoise = OrganicNoise.sample(0x51A51A51L, x, z, 150.0, 4);
        double ridgeNoise = Math.abs(OrganicNoise.sample(0x77AA33CCL, x, z, 170.0, 3));
        double detailNoise = OrganicNoise.sample(0x1B1A1D5L, x, z, 48.0, 3);
        double erosionNoise = OrganicNoise.sample(0xABCD1234L, x, z, 95.0, 2);
        double coastlineNoise = Math.abs(OrganicNoise.sample(0x77889911L, x, z, 70.0, 2));

        int profileHeight;
        if (warpedDistance < 60.0) {
            profileHeight = seaLevel + 4 + (int) Math.round(continentNoise * 3.0 + detailNoise * 2.0);
        } else if (warpedDistance < 230.0) {
            double ring = smoothBand(warpedDistance, 60.0, 145.0, 230.0);
            int deepFloor = seaLevel - 26 + (int) Math.round(continentNoise * 4.0);
            int shoreShelf = seaLevel - 8 + (int) Math.round(detailNoise * 2.0 + coastlineNoise * 2.0);
            profileHeight = (int) Math.round(MathHelper.lerp(ring, deepFloor, shoreShelf));
            if (detailNoise > 0.72 && warpedDistance > 110.0 && warpedDistance < 195.0) {
                profileHeight = seaLevel - 1 + (int) Math.round((detailNoise - 0.72) * 18.0);
            }
        } else if (warpedDistance < 875.0) {
            double coastIn = smoothRise(warpedDistance, 230.0, 320.0);
            double coastOut = 1.0 - smoothRise(warpedDistance, 760.0, 875.0);
            double landMass = Math.min(coastIn, coastOut);
            double inlandProgress = MathHelper.clamp((warpedDistance - 260.0) / 520.0, 0.0, 1.0);
            double mountainMask = mountainMaskForBiome(biome, angle, x, z);
            double hillMask = hillMaskForBiome(biome, x, z);

            double baseLowland = seaLevel + 2.0 + coastlineNoise * 2.0;
            double rollingInterior = seaLevel + 10.0 + continentNoise * 7.0 + detailNoise * 3.0;
            double alpineCore = seaLevel + 26.0 + ridgeNoise * 34.0 + continentNoise * 10.0;
            double profile = MathHelper.lerp(landMass, baseLowland, rollingInterior);
            profile = MathHelper.lerp(Math.max(mountainMask, hillMask * 0.55), profile, alpineCore);
            profile -= Math.max(0.0, erosionNoise - 0.25) * 7.0;
            profile += inlandProgress * hillMask * 8.0;
            profileHeight = (int) Math.round(profile);

            if (landMass < 0.16) {
                profileHeight = Math.min(profileHeight, seaLevel + (coastIn < coastOut ? 1 : 2));
            }
        } else {
            double oceanDrop = smoothRise(warpedDistance, 875.0, 1080.0);
            int nearShelf = seaLevel - 6 + (int) Math.round(continentNoise * 3.0 + coastlineNoise * 2.0);
            int deepOcean = seaLevel - 30 + (int) Math.round(continentNoise * 4.0);
            profileHeight = (int) Math.round(MathHelper.lerp(oceanDrop, nearShelf, deepOcean));
        }

        double preserveWeight = distance < 230.0 ? 0.25 : distance < 875.0 ? 0.68 : 0.35;
        int blended = (int) Math.round(MathHelper.lerp(preserveWeight, profileHeight, existingTop));

        if (warpedDistance >= 230.0 && warpedDistance <= 875.0) {
            blended = Math.max(blended, seaLevel + 1);
        }
        if (warpedDistance > 930.0) {
            blended = Math.min(blended, seaLevel - 3);
        }
        return MathHelper.clamp(blended, Math.max(getMinimumY() + 4, existingTop - 48), existingTop + 42);
    }

    private BlockState stoneFor(RegistryEntry<Biome> biome, int y, int terrainHeight) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (matches(key, BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.ERODED_BADLANDS)) {
            return y > terrainHeight - 10 ? Blocks.TERRACOTTA.getDefaultState() : Blocks.STONE.getDefaultState();
        }
        return Blocks.STONE.getDefaultState();
    }

    private double mountainMaskForBiome(RegistryEntry<Biome> biome, double angle, int x, int z) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        double noise = Math.abs(OrganicNoise.sample(0x4242AABBL, x, z, 120.0, 3));
        if (matches(key, BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS, BiomeKeys.STONY_PEAKS)) {
            return MathHelper.clamp(0.7 + noise * 0.4, 0.0, 1.0);
        }
        if (matches(key, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_SAVANNA, BiomeKeys.CHERRY_GROVE)) {
            return MathHelper.clamp(0.35 + noise * 0.35, 0.0, 0.82);
        }
        double polarBoost = Math.max(0.0, Math.cos(angle * FULL_TURN));
        return MathHelper.clamp(noise * 0.2 + polarBoost * 0.15, 0.0, 0.45);
    }

    private double hillMaskForBiome(RegistryEntry<Biome> biome, int x, int z) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        double noise = Math.abs(OrganicNoise.sample(0x9090CCDDL, x, z, 180.0, 2));
        if (matches(key, BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW)) {
            return noise * 0.18;
        }
        if (matches(key, BiomeKeys.DESERT, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU)) {
            return 0.22 + noise * 0.18;
        }
        if (matches(key, BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST,
                BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.JUNGLE, BiomeKeys.BAMBOO_JUNGLE,
                BiomeKeys.SPARSE_JUNGLE, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP)) {
            return 0.28 + noise * 0.22;
        }
        return 0.2 + noise * 0.2;
    }

    private double southClockwiseAngle(double x, double z) {
        double radians = Math.atan2(-x, z);
        double normalized = radians / FULL_TURN;
        normalized %= 1.0;
        return normalized < 0.0 ? normalized + 1.0 : normalized;
    }

    private double smoothRise(double value, double start, double end) {
        return MathHelper.clamp((value - start) / Math.max(1.0, end - start), 0.0, 1.0);
    }

    private double smoothBand(double value, double start, double mid, double end) {
        if (value <= mid) {
            return smoothRise(value, start, mid);
        }
        return 1.0 - smoothRise(value, mid, end);
    }

    private BlockState surfaceBlockFor(RegistryEntry<Biome> biome, int terrainHeight, int seaLevel) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (terrainHeight <= seaLevel) {
            return Blocks.SAND.getDefaultState();
        }
        if (matches(key, BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.ERODED_BADLANDS)) {
            return Blocks.RED_SAND.getDefaultState();
        }
        if (matches(key, BiomeKeys.DESERT, BiomeKeys.BEACH)) {
            return Blocks.SAND.getDefaultState();
        }
        if (matches(key, BiomeKeys.SNOWY_TAIGA, BiomeKeys.SNOWY_PLAINS, BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS, BiomeKeys.SNOWY_BEACH, BiomeKeys.FROZEN_RIVER)) {
            return Blocks.SNOW_BLOCK.getDefaultState();
        }
        if (matches(key, BiomeKeys.STONY_PEAKS, BiomeKeys.WINDSWEPT_HILLS)) {
            return Blocks.STONE.getDefaultState();
        }
        if (matches(key, BiomeKeys.MUSHROOM_FIELDS)) {
            return Blocks.MYCELIUM.getDefaultState();
        }
        return Blocks.GRASS_BLOCK.getDefaultState();
    }

    private BlockState fillerBlockFor(RegistryEntry<Biome> biome) {
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (matches(key, BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.DESERT, BiomeKeys.BEACH)) {
            return Blocks.SAND.getDefaultState();
        }
        if (matches(key, BiomeKeys.MUSHROOM_FIELDS)) {
            return Blocks.DIRT.getDefaultState();
        }
        return Blocks.DIRT.getDefaultState();
    }

    @SafeVarargs
    private final boolean matches(Optional<RegistryKey<Biome>> actual, RegistryKey<Biome>... expected) {
        if (actual.isEmpty()) {
            return false;
        }
        for (RegistryKey<Biome> key : expected) {
            if (actual.get().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
