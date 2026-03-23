package dev.mundocs5.world;

import net.minecraft.util.math.MathHelper;

/**
 * Matemática de topografia do anel continental orgânico para injeção em NoiseChunkGenerator.
 */
public final class RingTerrainMath {
    public static final int CENTER_MIN_Y = 65;
    public static final int CENTER_MAX_Y = 75;
    public static final int RIVER_FLOOR_Y = 62;

    private RingTerrainMath() {
    }

    public static int applyHeightRule(long seed, int x, int z, int vanillaHeight) {
        CustomRingBiomeSource.RingConfig config = CustomRingBiomeSource.RingConfig.DEFAULT;
        CustomRingBiomeSource.WarpPoint wp = CustomRingBiomeSource.warped(seed, config, x, z);

        if (wp.radius() >= config.vanillaRadius()) {
            return vanillaHeight;
        }

        int customHeight = computeRingHeight(seed, x, z, wp, vanillaHeight);

        if (wp.radius() < config.outerBlendStart()) {
            return customHeight;
        }

        double t = smooth01((wp.radius() - config.outerBlendStart()) / Math.max(1.0, config.vanillaRadius() - config.outerBlendStart()));
        return MathHelper.floor(MathHelper.lerp(t, customHeight, vanillaHeight));
    }

    public static double applyDensityRule(long seed, int x, int y, int z, double vanillaDensity) {
        CustomRingBiomeSource.RingConfig config = CustomRingBiomeSource.RingConfig.DEFAULT;
        CustomRingBiomeSource.WarpPoint wp = CustomRingBiomeSource.warped(seed, config, x, z);

        if (wp.radius() >= config.vanillaRadius()) {
            return vanillaDensity;
        }

        double miniMountains = miniMountainDensity(seed, x, y, z, wp.radius());
        double extremeMountains = extremeMountainDensity(seed, x, y, z, wp.angle(), wp.radius());
        double riverCut = riverCarvingDensity(seed, x, y, z, wp.radius());

        double custom = vanillaDensity + miniMountains + extremeMountains + riverCut;

        if (wp.radius() < config.outerBlendStart()) {
            return custom;
        }

        double t = smooth01((wp.radius() - config.outerBlendStart()) / Math.max(1.0, config.vanillaRadius() - config.outerBlendStart()));
        return MathHelper.lerp(t, custom, vanillaDensity);
    }

    private static int computeRingHeight(long seed, int x, int z, CustomRingBiomeSource.WarpPoint wp, int vanillaHeight) {
        if (wp.radius() < 60.0) {
            double n = OrganicNoise.sample(seed ^ 0x9E3779B9L, x, z, 44.0, 3) * 0.5 + 0.5;
            return CENTER_MIN_Y + MathHelper.floor(n * (CENTER_MAX_Y - CENTER_MIN_Y));
        }

        if (wp.radius() < 300.0) {
            return 36 + MathHelper.floor(OrganicNoise.sample(seed ^ 0x7F4A7C15L, x, z, 92.0, 2) * 8.0);
        }

        if (wp.radius() < 1300.0) {
            int base = 76 + MathHelper.floor(OrganicNoise.sample(seed ^ 0x6C8E9CF5L, x, z, 148.0, 3) * 18.0);
            base += MathHelper.floor(Math.abs(OrganicNoise.sample(seed ^ 0xBB67AE85L, x, z, 80.0, 4)) * 22.0); // mini-montanhas

            if (isRiver(seed, x, z, wp.radius())) {
                return RIVER_FLOOR_Y;
            }

            if (isFrozenOrBadlandsSector(wp.angle())) {
                base = Math.max(base, 150 + MathHelper.floor(Math.abs(OrganicNoise.sample(seed ^ 0xA54FF53AL, x, z, 132.0, 3)) * 70.0));
            }

            if (isPlainsWestSector(wp.angle()) && OrganicNoise.sample(seed ^ 0x510E527FL, x, z, 116.0, 3) > 0.58) {
                base = Math.max(base, 150 + MathHelper.floor(Math.abs(OrganicNoise.sample(seed ^ 0x1F83D9ABL, x, z, 88.0, 3)) * 60.0));
            }

            return base;
        }

        if (wp.radius() < 2800.0) {
            return 38 + MathHelper.floor(OrganicNoise.sample(seed ^ 0x5BE0CD19L, x, z, 210.0, 2) * 10.0);
        }

        return vanillaHeight;
    }

    private static boolean isRiver(long seed, int x, int z, double radius) {
        if (radius < 300.0 || radius > 1300.0) {
            return false;
        }
        double river = OrganicNoise.sample(seed ^ 0xCBBB9D5DL, x, z, 170.0, 3);
        return Math.abs(river) < 0.065;
    }

    private static double miniMountainDensity(long seed, int x, int y, int z, double radius) {
        if (radius < 300.0 || radius > 1300.0) {
            return 0.0;
        }

        double h1 = OrganicNoise.sample(seed ^ 0x629A292AL, x, z, 150.0, 3);
        double h2 = OrganicNoise.sample(seed ^ 0x9159015AL, x, z, 74.0, 3);
        double shape = (Math.abs(h1) * 0.36) + (Math.abs(h2) * 0.24);
        double yFactor = 1.0 - MathHelper.clamp((y - 68.0) / 96.0, 0.0, 1.0);
        return shape * yFactor;
    }

    private static double extremeMountainDensity(long seed, int x, int y, int z, double angle01, double radius) {
        if (radius < 300.0 || radius > 1300.0) {
            return 0.0;
        }

        boolean frozenOrBadlands = isFrozenOrBadlandsSector(angle01);
        boolean plainsStoneMountains = isPlainsWestSector(angle01) && OrganicNoise.sample(seed ^ 0x510E527FL, x, z, 116.0, 3) > 0.58;
        if (!frozenOrBadlands && !plainsStoneMountains) {
            return 0.0;
        }

        double topTarget = 155.0 + (Math.abs(OrganicNoise.sample(seed ^ 0xD807AA98L, x, z, 120.0, 3)) * 70.0);
        double belowTop = 1.0 - MathHelper.clamp((y - 72.0) / topTarget, 0.0, 1.0);
        return belowTop * (0.65 + Math.abs(OrganicNoise.sample(seed ^ 0x12835B01L, x, z, 66.0, 2)) * 0.35);
    }

    private static double riverCarvingDensity(long seed, int x, int y, int z, double radius) {
        if (!isRiver(seed, x, z, radius)) {
            return 0.0;
        }
        if (y <= RIVER_FLOOR_Y) {
            return 0.0;
        }
        double dy = (y - RIVER_FLOOR_Y) / 30.0;
        return -MathHelper.clamp(dy, 0.0, 1.85);
    }

    public static boolean isFrozenOrBadlandsSector(double angle01) {
        boolean frozen = angle01 >= 0.60 && angle01 < 0.75;
        boolean badlands = angle01 >= 0.25 && angle01 < 0.40;
        return frozen || badlands;
    }

    public static boolean isPlainsWestSector(double angle01) {
        return angle01 >= 0.40 && angle01 < 0.60;
    }

    private static double smooth01(double t) {
        double c = MathHelper.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }
}
