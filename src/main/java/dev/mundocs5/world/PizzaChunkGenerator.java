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
    private static final double FULL_TURN = Math.PI * 2.0;
    private static final double CENTER_ISLAND_RADIUS = 34.0;
    private static final double LAGOON_RADIUS = 185.0;
    private static final Brush[] LAND_STROKES = new Brush[] {
            new Brush(-400.0, -120.0, 280.0, 340.0, 1.05),
            new Brush(-250.0, -420.0, 250.0, 260.0, 0.92),
            new Brush(40.0, -560.0, 250.0, 265.0, 1.00),
            new Brush(345.0, -360.0, 280.0, 250.0, 1.00),
            new Brush(520.0, -40.0, 230.0, 310.0, 1.00),
            new Brush(370.0, 300.0, 280.0, 235.0, 1.04),
            new Brush(35.0, 520.0, 265.0, 220.0, 0.95),
            new Brush(-265.0, 430.0, 280.0, 235.0, 0.90),
            new Brush(-520.0, 170.0, 220.0, 260.0, 0.88),
            new Brush(-40.0, -250.0, 220.0, 160.0, 0.72)
    };
    private static final Brush[] WATER_CUTS = new Brush[] {
            new Brush(-40.0, 0.0, 235.0, 210.0, 1.20),
            new Brush(160.0, 220.0, 170.0, 120.0, 0.82),
            new Brush(-180.0, 235.0, 190.0, 120.0, 0.72),
            new Brush(250.0, 40.0, 150.0, 95.0, 0.65),
            new Brush(-315.0, -10.0, 140.0, 120.0, 0.58),
            new Brush(60.0, 145.0, 110.0, 82.0, 0.70)
    };
    private static final HeightBrush[] HEIGHT_STROKES = new HeightBrush[] {
            new HeightBrush(-120.0, 560.0, 210.0, 120.0, 30.0),
            new HeightBrush(40.0, 600.0, 170.0, 120.0, 26.0),
            new HeightBrush(-315.0, 520.0, 150.0, 100.0, 22.0),
            new HeightBrush(345.0, 280.0, 220.0, 180.0, 22.0),
            new HeightBrush(420.0, -305.0, 180.0, 150.0, 18.0),
            new HeightBrush(-420.0, -380.0, 180.0, 160.0, 18.0),
            new HeightBrush(-470.0, 250.0, 170.0, 170.0, 14.0),
            new HeightBrush(-20.0, -590.0, 200.0, 180.0, 16.0)
    };

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
    protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    public RegistryEntry<ChunkGeneratorSettings> settings() { return settings; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        int vanillaHeight = delegate.getHeight(x, z, heightmap, world, noiseConfig);
        RegistryEntry<Biome> biome = biomeSource.getBiome(x >> 2, vanillaHeight >> 2, z >> 2, noiseConfig.getMultiNoiseSampler());
        return calculateTargetHeight(x, z, vanillaHeight, biome);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int vanillaHeight = delegate.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noiseConfig);
        RegistryEntry<Biome> biome = biomeSource.getBiome(x >> 2, vanillaHeight >> 2, z >> 2, noiseConfig.getMultiNoiseSampler());
        int targetY = calculateTargetHeight(x, z, vanillaHeight, biome);

        int bottomY = world.getBottomY();
        BlockState[] states = new BlockState[world.getHeight()];
        for (int y = bottomY; y < bottomY + world.getHeight(); y++) {
            if (y > targetY) {
                states[y - bottomY] = y <= getSeaLevel() ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
            } else {
                states[y - bottomY] = pickStone(y, targetY, biome);
            }
        }
        return new VerticalBlockSample(bottomY, states);
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
    public int getWorldHeight() { return delegate.getWorldHeight(); }

    @Override
    public int getSeaLevel() { return delegate.getSeaLevel(); }

    @Override
    public int getMinimumY() { return delegate.getMinimumY(); }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        delegate.appendDebugHudText(text, noiseConfig, pos);
        text.add("Pizza terrain: hand-painted island shaping active");
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk).thenApply(generated -> {
            shapePizzaTerrain(generated);
            return generated;
        });
    }

    private int calculateTargetHeight(int x, int z, int vanillaHeight, RegistryEntry<Biome> biome) {
        int seaLevel = getSeaLevel();
        double angle = angleFor(x, z);
        double warpedX = x + OrganicNoise.sample(0x1212L, x, z, 240.0, 2) * 34.0;
        double warpedZ = z + OrganicNoise.sample(0x3434L, x, z, 240.0, 2) * 34.0;
        double distance = Math.sqrt(warpedX * warpedX + warpedZ * warpedZ);
        double landMask = paintedLandMask(warpedX, warpedZ);
        double lagoonMask = lagoonMask(warpedX, warpedZ, angle, distance);
        double heightBias = paintedHeightBias(warpedX, warpedZ) + OrganicNoise.sample(0x5656L, x, z, 170.0, 3) * 7.0 + Math.abs(OrganicNoise.sample(0x7878L, x, z, 84.0, 2)) * 5.0;

        if (distance <= CENTER_ISLAND_RADIUS) {
            return seaLevel + 20 + (int) (Math.abs(OrganicNoise.sample(0x9999L, x, z, 50.0, 3)) * 12.0);
        }

        if (lagoonMask > 0.18) {
            int lagoonFloor = seaLevel - 28 + (int) (OrganicNoise.sample(0xABABL, x, z, 90.0, 2) * 5.0);
            return Math.min(seaLevel - 5, lagoonFloor);
        }

        if (landMask > 0.0) {
            double rise = MathHelper.clamp(landMask, 0.0, 1.0);
            int shaped = (int) Math.round(MathHelper.lerp(0.58 + rise * 0.18, vanillaHeight, seaLevel + 14.0 + rise * 24.0 + heightBias));
            if (isMountainBiome(biome)) shaped += 16 + (int) (Math.abs(OrganicNoise.sample(0xCDCDL, x, z, 88.0, 3)) * 28.0);
            if (isDryBiome(biome)) shaped += 5;
            if (isWetBiome(biome)) shaped -= 8;
            if (!isRiverBiome(biome) && shaped < seaLevel + 2) shaped = seaLevel + 2;
            return shaped;
        }

        double shoreBand = Math.max(landMask, -0.55);
        if (shoreBand > -0.30) {
            double shoreRise = 1.0 - MathHelper.clamp((-shoreBand - 0.02) / 0.28, 0.0, 1.0);
            int shelf = seaLevel - 4 - (int) Math.round((1.0 - shoreRise) * 8.0) + (int) (OrganicNoise.sample(0xEFEFL, x, z, 130.0, 2) * 3.0);
            return Math.min(seaLevel - 2, shelf);
        }

        int deepOcean = seaLevel - 22 + (int) (OrganicNoise.sample(0xAAAA5555L, x, z, 180.0, 2) * 8.0);
        return Math.min(seaLevel - 8, Math.min(vanillaHeight, deepOcean));
    }

    private double paintedLandMask(double x, double z) {
        double land = -1.2;
        for (Brush brush : LAND_STROKES) {
            land = Math.max(land, brush.sample(x, z));
        }
        double cuts = 0.0;
        for (Brush cut : WATER_CUTS) {
            cuts = Math.max(cuts, cut.sample(x, z));
        }
        double edgeNoise = OrganicNoise.sample(0x2468L, x, z, 150.0, 2) * 0.16 + OrganicNoise.sample(0x1357L, x, z, 60.0, 2) * 0.08;
        return land - cuts * 1.18 + edgeNoise;
    }

    private double lagoonMask(double x, double z, double angle, double distance) {
        double cx = x * 0.92;
        double cz = z * 1.05;
        double ellipse = 1.0 - Math.sqrt((cx * cx) / (210.0 * 210.0) + (cz * cz) / (188.0 * 188.0));
        double opening = 1.0 - MathHelper.clamp(Math.abs(wrapSigned(angle - 0.76)) / 0.055, 0.0, 1.0);
        double openingRadial = 1.0 - MathHelper.clamp(Math.abs(distance - 190.0) / 70.0, 0.0, 1.0);
        double brokenEdge = opening * openingRadial * 0.56;
        double irregular = OrganicNoise.sample(0xBEEFL, x, z, 110.0, 2) * 0.10;
        return ellipse - brokenEdge + irregular;
    }

    private double paintedHeightBias(double x, double z) {
        double total = 0.0;
        for (HeightBrush stroke : HEIGHT_STROKES) {
            total += stroke.sample(x, z);
        }
        return total;
    }

    private void shapePizzaTerrain(Chunk chunk) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int seaLevel = getSeaLevel();
        int minY = getMinimumY();
        int maxY = chunk.getHeight() + minY - 1;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        BlockState water = Blocks.WATER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = startX + localX;
                int worldZ = startZ + localZ;
                int vanillaHeight = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, localX, localZ);
                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(localX >> 2, vanillaHeight >> 2, localZ >> 2);
                int targetHeight = calculateTargetHeight(worldX, worldZ, vanillaHeight, biome);

                for (int y = maxY; y >= minY; y--) {
                    mutable.set(worldX, y, worldZ);
                    BlockState existing = chunk.getBlockState(mutable);
                    if (y > targetHeight) {
                        if (y <= seaLevel) {
                            if (!existing.isOf(Blocks.WATER)) chunk.setBlockState(mutable, water, 0);
                        } else if (!existing.isAir()) {
                            chunk.setBlockState(mutable, air, 0);
                        }
                    } else if (existing.isAir() || existing.isOf(Blocks.WATER)) {
                        chunk.setBlockState(mutable, pickStone(y, targetHeight, biome), 0);
                    }
                }
            }
        }
    }

    private BlockState pickStone(int y, int targetHeight, RegistryEntry<Biome> biome) {
        if (isDryBiome(biome) && y > targetHeight - 18) return Blocks.RED_SANDSTONE.getDefaultState();
        if (isColdMountainBiome(biome) && y > targetHeight - 22) return Blocks.PACKED_ICE.getDefaultState();
        if (isMountainBiome(biome) && y > targetHeight - 10) return Blocks.ANDESITE.getDefaultState();
        return Blocks.STONE.getDefaultState();
    }

    private boolean isRiverBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.RIVER, BiomeKeys.FROZEN_RIVER);
    }

    private boolean isWetBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP, BiomeKeys.JUNGLE, BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.SPARSE_JUNGLE);
    }

    private boolean isDryBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.DESERT, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA);
    }

    private boolean isMountainBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS, BiomeKeys.STONY_PEAKS, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.CHERRY_GROVE);
    }

    private boolean isColdMountainBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS);
    }

    @SafeVarargs
    private static boolean matches(Optional<RegistryKey<Biome>> key, RegistryKey<Biome>... expected) {
        if (key.isEmpty()) return false;
        RegistryKey<Biome> actual = key.get();
        for (RegistryKey<Biome> biome : expected) {
            if (actual == biome) return true;
        }
        return false;
    }

    private static double angleFor(int x, int z) {
        double angle = Math.atan2(-x, z) / FULL_TURN;
        double wrapped = angle % 1.0;
        return wrapped < 0.0 ? wrapped + 1.0 : wrapped;
    }

    private static double wrapSigned(double delta) {
        double wrapped = (delta + 0.5) % 1.0;
        if (wrapped < 0.0) wrapped += 1.0;
        return wrapped - 0.5;
    }

    private record Brush(double centerX, double centerZ, double radiusX, double radiusZ, double strength) {
        private double sample(double x, double z) {
            double dx = (x - centerX) / Math.max(1.0, radiusX);
            double dz = (z - centerZ) / Math.max(1.0, radiusZ);
            return (1.0 - Math.sqrt(dx * dx + dz * dz)) * strength;
        }
    }

    private record HeightBrush(double centerX, double centerZ, double radiusX, double radiusZ, double height) {
        private double sample(double x, double z) {
            double dx = (x - centerX) / Math.max(1.0, radiusX);
            double dz = (z - centerZ) / Math.max(1.0, radiusZ);
            double mask = 1.0 - Math.sqrt(dx * dx + dz * dz);
            return Math.max(0.0, mask) * height;
        }
    }
}
