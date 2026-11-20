package ru.noslow;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
                    .then(ClientCommandManager.literal("AllServers").executes(ctx -> setMode(ctx, "All Servers")))
                    .then(ClientCommandManager.literal("ReallyWorld").executes(ctx -> setMode(ctx, "ReallyWorld")))
                    .then(ClientCommandManager.literal("SpookyTime").executes(ctx -> setMode(ctx, "SpookyTime")))
                    .then(ClientCommandManager.literal("OldGrim").executes(ctx -> setMode(ctx, "Old Grim")))
                    .then(ClientCommandManager.literal("FunTime").executes(ctx -> setMode(ctx, "FunTime")))
                    .then(ClientCommandManager.literal("Speed").executes(ctx -> setMode(ctx, "Speed")))
                )
                .then(ClientCommandManager.literal("speed")
                    .then(ClientCommandManager.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(context -> {
                            double val = DoubleArgumentType.getDouble(context, "value");
                            noSlow.speedVelocity = val;
                            context.getSource().sendFeedback(Text.literal("NoSlow Speed Boost: §e" + val));
                            return 1;
                        })
                    )
                )
            );
        });
    }

    private static int setMode(CommandContext<FabricClientCommandSource> context, String mode) {
        noSlow.itemMode = mode;
        noSlow.reset();
        context.getSource().sendFeedback(Text.literal("NoSlow Mode: §e" + mode));
        return 1;
    }
}
