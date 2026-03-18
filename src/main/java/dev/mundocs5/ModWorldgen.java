package dev.mundocs5;

import dev.mundocs5.world.PizzaBiomeSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModWorldgen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MundoCs5Mod.MOD_ID + "/worldgen");
    private static boolean registered;

    private ModWorldgen() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        Registry.register(Registries.BIOME_SOURCE, MundoCs5Mod.id("pizza"), PizzaBiomeSource.CODEC.codec);
        registered = true;
        LOGGER.info("Registered biome source {}", MundoCs5Mod.id("pizza"));
    }
}
