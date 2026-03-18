package dev.mundocs5;

import dev.mundocs5.world.PizzaBiomeSource;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MundoCs5Mod implements ModInitializer {
    public static final String MOD_ID = "mundocs5";

    @Override
    public void onInitialize() {
        Registry.register(Registries.BIOME_SOURCE, id("pizza"), PizzaBiomeSource.CODEC);
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
