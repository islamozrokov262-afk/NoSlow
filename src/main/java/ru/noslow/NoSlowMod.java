package ru.noslow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import ru.noslow.module.NoSlowModule;

public class NoSlowMod implements ClientModInitializer {
    public static NoSlowModule noSlow;
    public static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        noSlow = new NoSlowModule();

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (enabled) noSlow.onTick(client);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("noslow")
                .then(ClientCommandManager.literal("toggle").executes(context -> {
                    enabled = !enabled;
                    context.getSource().sendFeedback(Text.literal("NoSlow: " + (enabled ? "§aON" : "§cOFF")));
                    if (!enabled) noSlow.reset();
                    return 1;
                }))
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .suggests((context, builder) -> {
                            builder.suggest("All Servers");
                            builder.suggest("ReallyWorld");
                            builder.suggest("SpookyTime");
                            builder.suggest("Old Grim");
                            builder.suggest("FunTime");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String mode = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mode");
                            noSlow.itemMode = mode;
                            noSlow.reset();
                            context.getSource().sendFeedback(Text.literal("NoSlow Mode: §e" + mode));
                            return 1;
                        })
                    )
                )
            );
        });
    }
}
