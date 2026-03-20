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

    // Sincroniza a altura das estruturas (Vilas, Barcos, Árvores) com a nossa ilha
    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        int vanillaHeight = delegate.getHeight(x, z, heightmap, world, noiseConfig);
        RegistryEntry<Biome> biome = biomeSource.getBiome(x >> 2, vanillaHeight >> 2, z >> 2, noiseConfig.getMultiNoiseSampler());
        return calculateTargetHeight(x, z, vanillaHeight, biome);
    }

    // Usado pelo jogo para prever buracos para as Cavernas e Construções (corrige o bug das paredes)
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
                states[y - bottomY] = Blocks.STONE.getDefaultState();
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
        text.add("Pizza terrain: Organic sync active");
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk).thenApply(generated -> {
            shapePizzaTerrain(generated);
            return generated;
        });
    }

    // Calcula a forma da ilha distorcida (Sem recortes de Chunks)
    private double getPizzaMask(int x, int z) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        
        // Distorções altíssimas para ficar com forma natural do Minecraft
        double centerWarp = distance + OrganicNoise.sample(0x1111L, x, z, 100.0, 3) * 35.0;
        double centerMask = 1.0 - smoothRise(centerWarp, 45.0, 85.0); 
        
        double ringWarp = distance 
            + OrganicNoise.sample(0x2222L, x, z, 200.0, 3) * 70.0 
            + OrganicNoise.sample(0x3333L, x, z, 60.0, 2) * 20.0;
            
        double innerCoast = smoothRise(ringWarp, 170.0, 230.0);
        double outerCoast = 1.0 - smoothRise(ringWarp, 750.0, 880.0); 
        double ringMask = Math.min(innerCoast, outerCoast); 
        
        double totalLandMask = Math.max(centerMask, ringMask);
        return totalLandMask * 2.0 - 1.0; // Retorna entre -1.0 (Fundo do Mar) e 1.0 (Terra Alta)
    }

    // Mistura o mapa natural com o formato que queremos
    private int calculateTargetHeight(int x, int z, int vanillaHeight, RegistryEntry<Biome> biome) {
        double mask = getPizzaMask(x, z);
        int seaLevel = getSeaLevel();
        
        if (mask > 0) { // Estamos na TERRA
            int height = vanillaHeight;
            double distance = Math.sqrt((double) x * x + (double) z * z);
            
            // Relevo na Ilha de Cogumelo Central
            if (distance < 120.0) {
                height += (int) (Math.abs(OrganicNoise.sample(0x5555L, x, z, 60.0, 3)) * 25.0 * mask);
                height = Math.max(height, seaLevel + 5 + (int)(mask * 10)); // Base elevada
            }
            
            // Garante que a terra fique acima d'água, A MENOS que seja um Rio
            if (!isRiverBiome(biome) && height < seaLevel + 1) {
                height = seaLevel + 1;
            }
            
            // Mistura suavemente a praia (seaLevel) com a montanha Vanilla (height)
            return (int) MathHelper.lerp(mask, seaLevel, height);
        } else { // Estamos no OCEANO
            // Força as montanhas de fora do anel para debaixo d'água
            int targetOcean = Math.min(vanillaHeight, seaLevel - 4 + (int)(mask * 25));
            return (int) MathHelper.lerp(-mask, seaLevel, targetOcean);
        }
    }

    private void shapePizzaTerrain(Chunk chunk) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int seaLevel = getSeaLevel();
        int minY = getMinimumY();
        int maxY = chunk.getHeight() + minY - 1;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState water = Blocks.WATER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = startX + localX;
                int worldZ = startZ + localZ;

                int vanillaHeight = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, localX, localZ);
                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(localX >> 2, vanillaHeight >> 2, localZ >> 2);
                int targetHeight = calculateTargetHeight(worldX, worldZ, vanillaHeight, biome);

                // Aplica a escultura perfeitamente nas cavernas
                for (int y = maxY; y >= minY; y--) {
                    mutable.set(worldX, y, worldZ);
                    BlockState existing = chunk.getBlockState(mutable);

                    if (y > targetHeight) {
                        if (y <= seaLevel) {
                            if (!existing.isOf(Blocks.WATER)) chunk.setBlockState(mutable, water, 0);
                        } else {
                            if (!existing.isAir()) chunk.setBlockState(mutable, air, 0);
                        }
                    } else {
                        // Preenche buracos novos apenas se for ar ou água. Cavernas do Minecraft serão cravadas DEPOIS disso!
                        if (existing.isAir() || existing.isOf(Blocks.WATER)) {
                            chunk.setBlockState(mutable, stone, 0);
                        }
                    }
                }
            }
        }
    }

    private boolean isRiverBiome(RegistryEntry<Biome> biome) {
        return matches(biome.getKey(), BiomeKeys.RIVER, BiomeKeys.FROZEN_RIVER);
    }

    private double smoothRise(double value, double start, double end) {
        return MathHelper.clamp((value - start) / Math.max(1.0, end - start), 0.0, 1.0);
    }

    @SafeVarargs
    private final boolean matches(Optional<RegistryKey<Biome>> actual, RegistryKey<Biome>... expected) {
        if (actual.isEmpty()) return false;
        for (RegistryKey<Biome> key : expected) {
            if (actual.get().equals(key)) return true;
        }
        return false;
    }
}
