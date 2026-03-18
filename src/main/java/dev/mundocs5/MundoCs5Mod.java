package dev.mundocs5;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public final class MundoCs5Mod implements ModInitializer {
    public static final String MOD_ID = "mundocs5";

    @Override
    public void onInitialize() {
        ModWorldgen.register();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
