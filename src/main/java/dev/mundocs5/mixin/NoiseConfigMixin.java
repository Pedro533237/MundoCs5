package dev.mundocs5.mixin;

import dev.mundocs5.world.RingDensityFunction;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injeta RingDensityFunction na finalDensity do NoiseRouter vanilla.
 */
@Mixin(NoiseConfig.class)
public abstract class NoiseConfigMixin {
    @Unique
    private static final long MUNDOCS5_RING_SEED = 0x4D554E444F435335L;

    @Mutable
    @Shadow
    @Final
    private NoiseRouter noiseRouter;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mundocs5$wrapFinalDensity(ChunkGeneratorSettings chunkGeneratorSettings, RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noiseParametersRegistry, long seed, CallbackInfo ci) {
        NoiseRouter router = this.noiseRouter;
        this.noiseRouter = new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.preliminarySurfaceLevel(),
                RingDensityFunction.wrap(router.finalDensity(), MUNDOCS5_RING_SEED ^ seed),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap()
        );
    }
}
