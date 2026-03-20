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
 * Reshapes the overworld into a ring map near the origin and gradually returns
 * to vanilla generation by radius 3000.
 */
public class PizzaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PizzaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CustomRingBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> (CustomRingBiomeSource) generator.getBiomeSource()),
            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(PizzaChunkGenerator::settings),
            TerrainConfig.CODEC.fieldOf("terrain").forGetter(PizzaChunkGenerator::terrain)
    ).apply(instance, PizzaChunkGenerator::new));

    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final TerrainConfig terrain;
    private final NoiseChunkGenerator delegate;

    public PizzaChunkGenerator(CustomRingBiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, TerrainConfig terrain) {
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
        text.add("Ring map blend active");
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
                    chunk.setBlockState(pos, blockFor(y, surfaceY, radius), 0);
                }
            }
        }
    }

    private BlockState blockFor(int y, int surfaceY, double radius) {
        if (y > surfaceY) {
            return y <= terrain.seaLevel() ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        }

        if (isOceanRadius(radius)) {
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
        int vanillaHeight = delegate.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noiseConfig);

        if (radius >= terrain.vanillaBlendRadius()) {
            return vanillaHeight;
        }

        if (radius <= terrain.centerIslandRadius()) {
            double normalizedRadius = radius / Math.max(1.0, terrain.centerIslandRadius());
            double domeFactor = 1.0 - normalizedRadius;
            double centerNoise = (OrganicNoise.sample(0x11112222L, x, z, 28.0, 2) + 1.0) * 0.5;
            return seaLevel + 3 + MathHelper.floor(domeFactor * 7.0 + centerNoise * 3.0);
        }

        if (radius <= terrain.innerOceanRadius()) {
            double floorNoise = (OrganicNoise.sample(0x33334444L, x, z, 40.0, 2) + 1.0) * 0.5;
            return seaLevel - terrain.innerOceanDepth() - MathHelper.floor(floorNoise * terrain.innerOceanVariance());
        }

        if (radius <= terrain.outerRingRadius()) {
            double innerFade = MathHelper.clamp((radius - terrain.innerOceanRadius()) / terrain.coastBlendWidth(), 0.0, 1.0);
            double outerFade = MathHelper.clamp((terrain.outerRingRadius() - radius) / terrain.coastBlendWidth(), 0.0, 1.0);
            double coastalBlend = Math.min(innerFade, outerFade);
            double ringNoise = Math.sin((radius - terrain.innerOceanRadius()) / Math.max(1.0, terrain.outerRingRadius() - terrain.innerOceanRadius()) * Math.PI) * terrain.ringLift();
            int ringTarget = Math.max(seaLevel + 2, Math.min(vanillaHeight + MathHelper.floor(ringNoise), seaLevel + terrain.maxRingRise()));
            return MathHelper.floor(MathHelper.lerp(coastalBlend, seaLevel + 1, ringTarget));
        }

        double oceanNoise = (OrganicNoise.sample(0x55556666L, x, z, 60.0, 2) + 1.0) * 0.5;
        int oceanTarget = seaLevel - terrain.outerOceanDepth() - MathHelper.floor(oceanNoise * terrain.outerOceanVariance());

        // Blend the transition ocean back into vanilla between outerRingRadius and vanillaBlendRadius.
        double blend = MathHelper.clamp((radius - terrain.outerRingRadius()) / Math.max(1.0, terrain.vanillaBlendRadius() - terrain.outerRingRadius()), 0.0, 1.0);
        return MathHelper.floor(MathHelper.lerp(blend, oceanTarget, vanillaHeight));
    }

    private boolean isOceanRadius(double radius) {
        return (radius > terrain.centerIslandRadius() && radius <= terrain.innerOceanRadius())
                || (radius > terrain.outerRingRadius() && radius < terrain.vanillaBlendRadius());
    }

    private double radius(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    public record TerrainConfig(
            int seaLevel,
            int centerIslandRadius,
            int innerOceanRadius,
            int outerRingRadius,
            int vanillaBlendRadius,
            int innerOceanDepth,
            int innerOceanVariance,
            int outerOceanDepth,
            int outerOceanVariance,
            double coastBlendWidth,
            int ringLift,
            int maxRingRise
    ) {
        public static final MapCodec<TerrainConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(-64, 320).optionalFieldOf("sea_level", 62).forGetter(TerrainConfig::seaLevel),
                Codec.intRange(1, 256).optionalFieldOf("center_island_radius", 70).forGetter(TerrainConfig::centerIslandRadius),
                Codec.intRange(1, 1024).optionalFieldOf("inner_ocean_radius", 300).forGetter(TerrainConfig::innerOceanRadius),
                Codec.intRange(1, 4096).optionalFieldOf("outer_ring_radius", 2000).forGetter(TerrainConfig::outerRingRadius),
                Codec.intRange(1, 8192).optionalFieldOf("vanilla_blend_radius", 3000).forGetter(TerrainConfig::vanillaBlendRadius),
                Codec.intRange(1, 64).optionalFieldOf("inner_ocean_depth", 12).forGetter(TerrainConfig::innerOceanDepth),
                Codec.intRange(0, 64).optionalFieldOf("inner_ocean_variance", 10).forGetter(TerrainConfig::innerOceanVariance),
                Codec.intRange(1, 64).optionalFieldOf("outer_ocean_depth", 18).forGetter(TerrainConfig::outerOceanDepth),
                Codec.intRange(0, 64).optionalFieldOf("outer_ocean_variance", 12).forGetter(TerrainConfig::outerOceanVariance),
                Codec.DOUBLE.optionalFieldOf("coast_blend_width", 128.0).forGetter(TerrainConfig::coastBlendWidth),
                Codec.intRange(0, 96).optionalFieldOf("ring_lift", 10).forGetter(TerrainConfig::ringLift),
                Codec.intRange(1, 256).optionalFieldOf("max_ring_rise", 110).forGetter(TerrainConfig::maxRingRise)
        ).apply(instance, TerrainConfig::new));
    }
}
