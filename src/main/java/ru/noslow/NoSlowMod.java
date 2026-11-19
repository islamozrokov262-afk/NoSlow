package ru.noslow;

import net.fabricmc.api.ClientModInitializer;
import ru.noslow.module.NoSlowModule;

public class NoSlowMod implements ClientModInitializer {
    public static NoSlowModule noSlow;

    @Override
    public void onInitializeClient() {
        noSlow = new NoSlowModule();
    }
}
