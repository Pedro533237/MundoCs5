package dev.mundocs5.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Chunk generator that keeps vanilla caves/structures but forces the requested
 * radial continent/ocean silhouette.
 */
public class PizzaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PizzaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PizzaBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> (PizzaBiomeSource) generator.getBiomeSource()),
            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(PizzaChunkGenerator::settings),
            TerrainConfig.CODEC.fieldOf("terrain").forGetter(PizzaChunkGenerator::terrain)
    ).apply(instance, PizzaChunkGenerator::new));

    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final TerrainConfig terrain;
    private final NoiseChunkGenerator delegate;

    public PizzaChunkGenerator(PizzaBiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, TerrainConfig terrain) {
        super(biomeSource);
        this.settings = settings;
        this.terrain = terrain;
        this.delegate = new NoiseChunkGenerator(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<ChunkGeneratorSettings> settings() {
        return settings;
    }

    public TerrainConfig terrain() {
        return terrain;
    }

    @Override
    public CompletableFuture<Chunk> populateBiomes(NoiseConfig noiseConfig, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk) {
        // Important: keep vanilla cave carving intact.
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
    public int getWorldHeight() {
        return delegate.getWorldHeight();
    }

    @Override
    public int getSeaLevel() {
        return terrain.seaLevel();
    }

    @Override
    public int getMinimumY() {
        return delegate.getMinimumY();
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        delegate.appendDebugHudText(text, noiseConfig, pos);
        text.add("Ring continent terrain active");
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return computeSurfaceHeight(x, z, noiseConfig, world);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int minY = world.getBottomY();
        int height = world.getHeight();
        int surfaceY = computeSurfaceHeight(x, z, noiseConfig, world);
        double radius = radius(x, z);

        BlockState[] states = new BlockState[height];
        for (int y = minY; y < minY + height; y++) {
            states[y - minY] = blockFor(y, surfaceY, radius);
        }
        return new VerticalBlockSample(minY, states);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk).thenApply(generated -> {
            reshapeChunk(generated, noiseConfig);
            return generated;
        });
    }

    private void reshapeChunk(Chunk chunk, NoiseConfig noiseConfig) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = getMinimumY();
        int maxY = minY + chunk.getHeight() - 1;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = startX + localX;
                int worldZ = startZ + localZ;
                double radius = radius(worldX, worldZ);
                int surfaceY = computeSurfaceHeight(worldX, worldZ, noiseConfig, chunk);

                for (int y = minY; y <= maxY; y++) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = blockFor(y, surfaceY, radius);
                    chunk.setBlockState(pos, state, 0);
                }
            }
        }
    }

    private BlockState blockFor(int y, int surfaceY, double radius) {
        if (y > surfaceY) {
            return y <= terrain.seaLevel() ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        }

        if (radius > terrain.centerIslandRadius() && radius <= terrain.innerOceanRadius()) {
            if (y == surfaceY) return Blocks.GRAVEL.getDefaultState();
            if (y >= surfaceY - 2) return Blocks.SAND.getDefaultState();
            return Blocks.STONE.getDefaultState();
        }

        if (radius > terrain.outerRingRadius()) {
            if (y == surfaceY) return Blocks.GRAVEL.getDefaultState();
            if (y >= surfaceY - 2) return Blocks.SAND.getDefaultState();
            return Blocks.STONE.getDefaultState();
        }

        if (y == surfaceY) {
            return radius <= terrain.centerIslandRadius() ? Blocks.MYCELIUM.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
        }

        if (y >= surfaceY - 3) {
            return Blocks.DIRT.getDefaultState();
        }

        return Blocks.STONE.getDefaultState();
    }

    private int computeSurfaceHeight(int x, int z, NoiseConfig noiseConfig, HeightLimitView world) {
        double radius = radius(x, z);
        int seaLevel = terrain.seaLevel();

        if (radius <= terrain.centerIslandRadius()) {
            // Radius 0..50 becomes a small dome at Y=65..70.
            double normalizedRadius = radius / Math.max(1.0, terrain.centerIslandRadius());
            double domeFactor = 1.0 - normalizedRadius;
            double centerNoise = (OrganicNoise.sample(0xC0FFEE1L, x, z, 24.0, 2) + 1.0) * 0.5;
            return seaLevel + 3 + MathHelper.floor(domeFactor * 3.0 + centerNoise * 2.0);
        }

        if (radius <= terrain.innerOceanRadius()) {
            // The internal lake stays below sea level so only water remains at Y=62.
            double lakeNoise = (OrganicNoise.sample(0xC0FFEE2L, x, z, 36.0, 2) + 1.0) * 0.5;
            return seaLevel - terrain.innerOceanDepth() - MathHelper.floor(lakeNoise * terrain.innerOceanFloorVariance());
        }

        if (radius <= terrain.outerRingRadius()) {
            // Reuse vanilla terrain to keep normal mountains/plains inside the ring.
            int vanillaHeight = delegate.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noiseConfig);

            // Fade the ring edges toward the sea so the donut shape is clearly visible.
            double innerFade = MathHelper.clamp((radius - terrain.innerOceanRadius()) / terrain.coastBlendWidth(), 0.0, 1.0);
            double outerFade = MathHelper.clamp((terrain.outerRingRadius() - radius) / terrain.coastBlendWidth(), 0.0, 1.0);
            double coastalBlend = Math.min(innerFade, outerFade);
            double bonusLift = Math.sin((radius - terrain.innerOceanRadius()) / Math.max(1.0, terrain.outerRingRadius() - terrain.innerOceanRadius()) * Math.PI) * terrain.ringHeightBonus();
            int inlandHeight = Math.max(seaLevel + 2, Math.min(vanillaHeight + MathHelper.floor(bonusLift), seaLevel + terrain.maxRingRise()));
            return MathHelper.floor(MathHelper.lerp(coastalBlend, seaLevel + 1, inlandHeight));
        }

        double outerOceanNoise = (OrganicNoise.sample(0xC0FFEE3L, x, z, 52.0, 2) + 1.0) * 0.5;
        return seaLevel - terrain.outerOceanDepth() - MathHelper.floor(outerOceanNoise * terrain.outerOceanFloorVariance());
    }

    private double radius(int x, int z) {
        // Exact Euclidean distance from the center: sqrt(x² + z²).
        return Math.sqrt((double) x * x + (double) z * z);
    }

    public record TerrainConfig(
            int seaLevel,
            int centerIslandRadius,
            int innerOceanRadius,
            int outerRingRadius,
            int innerOceanDepth,
            int innerOceanFloorVariance,
            int outerOceanDepth,
            int outerOceanFloorVariance,
            double coastBlendWidth,
            int ringHeightBonus,
            int maxRingRise
    ) {
        public static final MapCodec<TerrainConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(-64, 320).optionalFieldOf("sea_level", 62).forGetter(TerrainConfig::seaLevel),
                Codec.intRange(1, 256).optionalFieldOf("center_island_radius", 50).forGetter(TerrainConfig::centerIslandRadius),
                Codec.intRange(1, 1024).optionalFieldOf("inner_ocean_radius", 300).forGetter(TerrainConfig::innerOceanRadius),
                Codec.intRange(1, 4096).optionalFieldOf("outer_ring_radius", 1500).forGetter(TerrainConfig::outerRingRadius),
                Codec.intRange(1, 64).optionalFieldOf("inner_ocean_depth", 10).forGetter(TerrainConfig::innerOceanDepth),
                Codec.intRange(0, 64).optionalFieldOf("inner_ocean_floor_variance", 8).forGetter(TerrainConfig::innerOceanFloorVariance),
                Codec.intRange(1, 64).optionalFieldOf("outer_ocean_depth", 14).forGetter(TerrainConfig::outerOceanDepth),
                Codec.intRange(0, 64).optionalFieldOf("outer_ocean_floor_variance", 12).forGetter(TerrainConfig::outerOceanFloorVariance),
                Codec.DOUBLE.optionalFieldOf("coast_blend_width", 96.0).forGetter(TerrainConfig::coastBlendWidth),
                Codec.intRange(0, 64).optionalFieldOf("ring_height_bonus", 6).forGetter(TerrainConfig::ringHeightBonus),
                Codec.intRange(1, 128).optionalFieldOf("max_ring_rise", 70).forGetter(TerrainConfig::maxRingRise)
        ).apply(instance, TerrainConfig::new));
    }
}
