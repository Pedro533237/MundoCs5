package dev.mundocs5.world;

/**
 * Lightweight deterministic value noise used to soften large biome regions without touching terrain noise.
 */
public final class OrganicNoise {
    private OrganicNoise() {
    }

    public static double sample(long seed, double x, double z, double scale, int octaves) {
        double scaledX = x / scale;
        double scaledZ = z / scale;
        double amplitude = 1.0;
        double frequency = 1.0;
        double total = 0.0;
        double max = 0.0;

        for (int octave = 0; octave < octaves; octave++) {
            total += valueNoise(seed + octave * 341873128712L, scaledX * frequency, scaledZ * frequency) * amplitude;
            max += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }

        return max == 0.0 ? 0.0 : total / max;
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = x - x0;
        double tz = z - z0;
        
        // CORREÇÃO: Utilizando o Fade Quíntico para transições perfeitas
        double u = fade(tx);
        double v = fade(tz);

        double n00 = random(seed, x0, z0);
        double n10 = random(seed, x1, z0);
        double n01 = random(seed, x0, z1);
        double n11 = random(seed, x1, z1);

        double nx0 = lerp(n00, n10, u);
        double nx1 = lerp(n01, n11, u);
        return lerp(nx0, nx1, v);
    }

    private static double random(long seed, int x, int z) {
        long hash = seed;
        // CORREÇÃO: Casting explícito para long e proteção contra eixos zerados para evitar padrões repetitivos
        hash ^= (x == 0 ? 0x1B873593L : (long) x) * 0x632BE59BD9B4E019L;
        hash ^= (z == 0 ? 0x27491C75L : (long) z) * 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return ((hash >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static int fastFloor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /**
     * CORREÇÃO: Interpolação Quíntica de Ken Perlin (6x^5 - 15x^4 + 10x^3).
     * Substitui o smoothstep antigo (3x^2 - 2x^3) para eliminar completamente 
     * a aparência de grade/linhas retas na geração de biomas e montanhas.
     */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }
}
