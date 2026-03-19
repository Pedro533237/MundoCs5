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
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
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

                int terrainHeight = sampleTerrainHeight(worldX, worldZ, seaLevel);
                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(localX >> 2, Math.max(minY, terrainHeight) >> 2, localZ >> 2);
                BlockState topBlock = surfaceBlockFor(biome, terrainHeight, seaLevel);
                BlockState fillerBlock = fillerBlockFor(biome);

                for (int y = minY; y <= maxY; y++) {
                    mutable.set(worldX, y, worldZ);
                    if (y > terrainHeight) {
                        if (y <= seaLevel) {
                            chunk.setBlockState(mutable, Blocks.WATER.getDefaultState(), 0);
                        } else {
                            chunk.setBlockState(mutable, Blocks.AIR.getDefaultState(), 0);
                        }
                        continue;
                    }

                    if (y == terrainHeight) {
                        chunk.setBlockState(mutable, topBlock, 0);
                    } else if (y >= terrainHeight - 3) {
                        chunk.setBlockState(mutable, fillerBlock, 0);
                    } else {
                        chunk.setBlockState(mutable, Blocks.STONE.getDefaultState(), 0);
                    }
                }
            }
        }
    }

    private int sampleTerrainHeight(int x, int z, int seaLevel) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        double angle = Math.atan2(z, x);
        double warpedAngle = angle + OrganicNoise.sample(0x600DF00DL, x, z, 240.0, 2) * 0.22;
        double terrainNoise = OrganicNoise.sample(0x51A51A51L, x, z, 90.0, 4);
        double ridgeNoise = Math.abs(OrganicNoise.sample(0x77AA33CCL, x, z, 160.0, 3));
        double islandNoise = OrganicNoise.sample(0x1B1A1D5L, x, z, 45.0, 3);
        double riverNoise = Math.abs(OrganicNoise.sample(0xABCD1234L, x, z, 70.0, 2));

        if (distance < 55.0) {
            return seaLevel + 6 + (int) Math.round(terrainNoise * 5.0 + ridgeNoise * 4.0);
        }

        if (distance < 235.0) {
            int baseOceanFloor = seaLevel - 18 + (int) Math.round(terrainNoise * 5.0);
            if (islandNoise > 0.63) {
                return seaLevel + 1 + (int) Math.round((islandNoise - 0.63) * 28.0);
            }
            return baseOceanFloor;
        }

        if (distance < 835.0) {
            double normalized = normalize(warpedAngle);
            boolean mountainZone = normalized > 0.23 && normalized < 0.37
                    || normalized > 0.61 && normalized < 0.73;
            boolean temperateHighlands = normalized > 0.37 && normalized < 0.44;
            boolean plainsZone = normalized > 0.44 && normalized < 0.52;
            boolean jungleZone = normalized > 0.88 || normalized < 0.08;

            int baseHeight = seaLevel + 6 + (int) Math.round(terrainNoise * 9.0);
            if (mountainZone) {
                baseHeight = seaLevel + 18 + (int) Math.round(ridgeNoise * 38.0 + terrainNoise * 12.0);
            } else if (temperateHighlands) {
                baseHeight = seaLevel + 11 + (int) Math.round(ridgeNoise * 18.0 + terrainNoise * 10.0);
            } else if (plainsZone) {
                baseHeight = seaLevel + 4 + (int) Math.round(terrainNoise * 5.0);
            } else if (jungleZone) {
                baseHeight = seaLevel + 10 + (int) Math.round(terrainNoise * 12.0);
            }

            if (riverNoise > 0.46 && riverNoise < 0.51) {
                baseHeight = Math.min(baseHeight, seaLevel - 1);
            }

            return baseHeight;
        }

        int outerOceanFloor = seaLevel - 22 + (int) Math.round(terrainNoise * 5.0);
        return Math.min(outerOceanFloor, seaLevel - 4);
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
    private boolean matches(Optional<RegistryKey<Biome>> actual, RegistryKey<Biome>... expected) {
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

    private double normalize(double angle) {
        double value = (angle + Math.PI) / (Math.PI * 2.0);
        double wrapped = value % 1.0;
        return wrapped < 0.0 ? wrapped + 1.0 : wrapped;
    }
}
