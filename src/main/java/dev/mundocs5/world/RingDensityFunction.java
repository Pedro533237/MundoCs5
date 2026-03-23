package dev.mundocs5.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mundocs5.MundoCs5Mod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Wrapper de DensityFunction para injetar a matemática do anel orgânico sem quebrar o pipeline vanilla.
 */
public record RingDensityFunction(DensityFunction input, long seed) implements DensityFunction {
    public static final MapCodec<RingDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.FUNCTION_CODEC.fieldOf("input").forGetter(RingDensityFunction::input),
            com.mojang.serialization.Codec.LONG.optionalFieldOf("seed", 0L).forGetter(RingDensityFunction::seed)
    ).apply(instance, RingDensityFunction::new));

    public static final CodecHolder<RingDensityFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    public static DensityFunction wrap(DensityFunction input, long seed) {
        return input instanceof RingDensityFunction ? input : new RingDensityFunction(input, seed);
    }

    public static void register() {
        Registry.register(Registries.DENSITY_FUNCTION_TYPE, MundoCs5Mod.id("ring_density"), MAP_CODEC);
    }

    @Override
    public double sample(NoisePos pos) {
        double base = input.sample(pos);
        return RingTerrainMath.applyDensityRule(seed, pos.blockX(), pos.blockY(), pos.blockZ(), base);
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {
        input.fill(densities, applier);
        for (int i = 0; i < densities.length; i++) {
            NoisePos pos = applier.at(i);
            densities[i] = RingTerrainMath.applyDensityRule(seed, pos.blockX(), pos.blockY(), pos.blockZ(), densities[i]);
        }
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return visitor.apply(new RingDensityFunction(input.apply(visitor), seed));
    }

    @Override
    public double minValue() {
        return Math.min(-2.0, input.minValue() - 2.0);
    }

    @Override
    public double maxValue() {
        return Math.max(2.0, input.maxValue() + 2.0);
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return CODEC_HOLDER;
    }
}
