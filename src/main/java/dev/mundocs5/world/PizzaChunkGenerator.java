package dev.mundocs5.world;

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
 * Chunk generator that applies a radial terrain mask centered at X=0/Z=0.
 */
public class PizzaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PizzaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PizzaBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> (PizzaBiomeSource) generator.getBiomeSource()),
            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(PizzaChunkGenerator::settings),
            TerrainConfig.CODEC.fieldOf("terrain").forGetter(PizzaChunkGenerator::terrain)
    ).apply(instance, PizzaChunkGenerator::new));

    private final PizzaBiomeSource biomeSource;
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final TerrainConfig terrain;
    private final NoiseChunkGenerator delegate;

    public PizzaChunkGenerator(PizzaBiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, TerrainConfig terrain) {
        super(biomeSource);
        this.biomeSource = biomeSource;
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
        text.add("Polar terrain active");
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
            carveHubAndTunnels(generated);
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

        if (radius > terrain.centerIslandRadius() && radius <= terrain.innerOceanRadius()) {
            if (y == surfaceY) return Blocks.GRAVEL.getDefaultState();
            if (y >= surfaceY - 2) return Blocks.SAND.getDefaultState();
        }

        if (radius > terrain.outerRingRadius()) {
            if (y == surfaceY) return Blocks.GRAVEL.getDefaultState();
            if (y >= surfaceY - 2) return Blocks.SAND.getDefaultState();
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
            // The center island rises from Y=65 to Y=70.
            double normalized = radius / Math.max(1.0, terrain.centerIslandRadius());
            double dome = 1.0 - normalized;
            double noise = (OrganicNoise.sample(0xC0FFEE1L, x, z, 24.0, 2) + 1.0) * 0.5;
            return seaLevel + 3 + MathHelper.floor(dome * 3.0 + noise * 2.0);
        }

        if (radius <= terrain.innerOceanRadius()) {
            double lakeNoise = (OrganicNoise.sample(0xC0FFEE2L, x, z, 36.0, 2) + 1.0) * 0.5;
            return seaLevel - 10 - MathHelper.floor(lakeNoise * 8.0);
        }

        if (radius <= terrain.outerRingRadius()) {
            int vanillaHeight = delegate.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noiseConfig);
            double ringWidth = Math.max(1.0, terrain.outerRingRadius() - terrain.innerOceanRadius());
            double ringProgress = (radius - terrain.innerOceanRadius()) / ringWidth;
            double continentalLift = Math.sin(ringProgress * Math.PI) * 6.0;
            int boosted = vanillaHeight + MathHelper.floor(continentalLift);
            return Math.max(seaLevel + 2, Math.min(boosted, seaLevel + 70));
        }

        double oceanNoise = (OrganicNoise.sample(0xC0FFEE3L, x, z, 52.0, 2) + 1.0) * 0.5;
        return seaLevel - 12 - MathHelper.floor(oceanNoise * 10.0);
    }

    private void carveHubAndTunnels(Chunk chunk) {
        int tunnelY = terrain.tunnelY();
        int roomRadius = terrain.hubRoomRadius();
        int centerX = 0;
        int centerZ = 0;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunk.getPos().getStartX() + localX;
                int worldZ = chunk.getPos().getStartZ() + localZ;

                if (Math.abs(worldX - centerX) <= roomRadius && Math.abs(worldZ - centerZ) <= roomRadius) {
                    carveAirBox(chunk, worldX, tunnelY, worldZ, 1);
                }
            }
        }

        int sectorCount = terrain.tunnelSectorCount();
        double sliceSize = (Math.PI * 2.0) / sectorCount;
        for (int sector = 0; sector < sectorCount; sector++) {
            double angle = (sector + 0.5) * sliceSize;
            for (int step = 0; step <= terrain.outerRingRadius(); step++) {
                int worldX = MathHelper.floor(Math.cos(angle) * step);
                int worldZ = MathHelper.floor(Math.sin(angle) * step);

                if (worldX >= chunk.getPos().getStartX() && worldX <= chunk.getPos().getEndX() && worldZ >= chunk.getPos().getStartZ() && worldZ <= chunk.getPos().getEndZ()) {
                    carveAirBox(chunk, worldX, tunnelY, worldZ, 1);
                }
            }
        }
    }

    private void carveAirBox(Chunk chunk, int worldX, int centerY, int worldZ, int radius) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = getMinimumY();
        int maxY = minY + chunk.getHeight() - 1;
        for (int x = worldX - radius; x <= worldX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                if (y < minY || y > maxY) continue;
                for (int z = worldZ - radius; z <= worldZ + radius; z++) {
                    if (x < chunk.getPos().getStartX() || x > chunk.getPos().getEndX() || z < chunk.getPos().getStartZ() || z > chunk.getPos().getEndZ()) continue;
                    pos.set(x, y, z);
                    chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), 0);
                }
            }
        }
    }

    private double radius(int x, int z) {
        // Exact Euclidean distance from (0, 0): sqrt(x² + z²).
        return Math.sqrt((double) x * x + (double) z * z);
    }

    public record TerrainConfig(
            int seaLevel,
            int centerIslandRadius,
            int innerOceanRadius,
            int outerRingRadius,
            int tunnelY,
            int hubRoomRadius,
            int tunnelSectorCount
    ) {
        public static final MapCodec<TerrainConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.intRange(-64, 320).optionalFieldOf("sea_level", 62).forGetter(TerrainConfig::seaLevel),
                com.mojang.serialization.Codec.intRange(1, 256).optionalFieldOf("center_island_radius", 50).forGetter(TerrainConfig::centerIslandRadius),
                com.mojang.serialization.Codec.intRange(1, 1024).optionalFieldOf("inner_ocean_radius", 300).forGetter(TerrainConfig::innerOceanRadius),
                com.mojang.serialization.Codec.intRange(1, 4096).optionalFieldOf("outer_ring_radius", 1500).forGetter(TerrainConfig::outerRingRadius),
                com.mojang.serialization.Codec.intRange(-64, 320).optionalFieldOf("tunnel_y", 30).forGetter(TerrainConfig::tunnelY),
                com.mojang.serialization.Codec.intRange(1, 16).optionalFieldOf("hub_room_radius", 4).forGetter(TerrainConfig::hubRoomRadius),
                com.mojang.serialization.Codec.intRange(1, 16).optionalFieldOf("tunnel_sector_count", 6).forGetter(TerrainConfig::tunnelSectorCount)
        ).apply(instance, TerrainConfig::new));
    }
}
