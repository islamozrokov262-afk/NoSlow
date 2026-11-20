package ru.noslow.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import ru.noslow.mixin.LivingEntityAccessor;

import java.util.ArrayDeque;

public class NoSlowModule {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    public String itemMode = "Speed"; 
    public double speedVelocity = 0.28; // Сила ускорения

    private int ticks;
    private int cycleCounter;
    private boolean cancelSlowThisTick = false;
    private boolean forcedSprintDuringEat;
    private boolean sprintWasPressedBeforeEat;
    private int crossbowSlot = -1;
    private int swapButton;
    private int phase;
    private int repeats;
    private long lastUseMs;
    private boolean slow = true;
    private boolean swapped;
    private volatile boolean restoreRequested;
    private Hand activeHand;
    private Hand otherHand;
    private ClientPlayerEntity owner;

    private GroundedPhase groundedPhase = GroundedPhase.IDLE;
    private final ArrayDeque<CommonPongC2SPacket> groundedPongs = new ArrayDeque<>();
    private ClientPlayNetworkHandler groundedConnection;
    private ClientWorld groundedLevel;
    private ClientPlayerEntity groundedPlayer;
    private Hand groundedUseHand;
    private int groundedSlot;
    private final ItemUseContinuation groundedContinuation = new ItemUseContinuation();
    private long groundedTick;
    private long groundedStartedAt;
    private boolean groundedWaitForRelease;
    private boolean groundedMainHandSession;
    private long groundedMainHandPreparedAtTick = -1L;
    private final MainHandTransactionWindow groundedMainHandWindow = new MainHandTransactionWindow();
    private long groundedMainHandPongStartedAt;

    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        if (itemMode.equals("FunTime")) {
            ae(client);
            ti();
        } else if (itemMode.equals("ReallyWorld") || itemMode.equals("SpookyTime")) {
            if (!client.player.isUsingRiptide()) {
                if (client.player.isUsingItem()) {
                    ticks++;
                } else {
                    ticks = 0;
                    cycleCounter = 0;
                }
            }

            if (itemMode.equals("ReallyWorld")) {
                int[] thresholds = client.player.isJumping() ? new int[]{2, 2, 4} : new int[]{2, 3, 4};
                int threshold = thresholds[cycleCounter % thresholds.length];
                if (ticks >= threshold) {
                    cancelSlowThisTick = true;
                    ticks = 0;
                    cycleCounter++;
                } else {
                    cancelSlowThisTick = false;
                }
            } else if (itemMode.equals("SpookyTime")) {
                int[] thresholds = new int[]{2, 2, 3};
                int threshold = thresholds[cycleCounter % 2];
                if (ticks >= threshold) {
                    cancelSlowThisTick = true;
                    ticks = 0;
                    cycleCounter++;
                } else {
                    cancelSlowThisTick = false;
                }
            }
        } else if (itemMode.equals("Old Grim")) {
            cancelSlowThisTick = true;
        } else { // All Servers / Speed
            if (client.player.getActiveHand() != Hand.MAIN_HAND && client.player.getActiveHand() != Hand.OFF_HAND) {
                ticks = 0;
            } else {
                ticks++;
            }
            ti();
            tl();
        }
    }

    public boolean shouldApplyNoSlow(ClientPlayerEntity player) {
        if (player == null || player != mc.player || !player.isUsingItem()) return false;

        if (itemMode.equals("All Servers") || itemMode.equals("Speed")) {
            return l(player) || itemMode.equals("Speed");
        }
        if (itemMode.equals("FunTime")) {
            return player.getActiveItem().getItem() instanceof CrossbowItem || !slow;
        }
        if (itemMode.equals("Old Grim")) {
            return player.getOffHandStack().getUseAction() != UseAction.NONE ||
                   player.getMainHandStack().getUseAction() != UseAction.NONE;
        }
        if (itemMode.equals("ReallyWorld") || itemMode.equals("SpookyTime")) {
            return cancelSlowThisTick;
        }
        return false;
    }

    private boolean l(ClientPlayerEntity player) {
        return tk() && groundedPhase == GroundedPhase.USING
                && (!groundedMainHandSession || groundedMainHandWindow.bZ())
                && tm()
                && player == groundedPlayer
                && player.isAlive()
                && !player.hasVehicle()
                && player.getActiveHand() == groundedUseHand;
    }

    private boolean tk() {
        return itemMode.equals("All Servers") || itemMode.equals("Speed");
    }

    public boolean onReceivePacket(Packet<?> packet) {
        if (itemMode.equals("FunTime")) {
            if (phase >= 3 && System.currentTimeMillis() - lastUseMs < 900L) {
                if (packet instanceof InventoryS2CPacket || packet instanceof SetPlayerInventoryS2CPacket) {
                    restoreRequested = true;
                    return true;
                }
            }
            return false;
        }

        if (!tk()) return false;

        if (groundedMainHandSession && (packet instanceof PlayerPositionLookS2CPacket || packet instanceof PlayerRespawnS2CPacket)) {
            J(true);
        }
        if (groundedMainHandSession && tm() && groundedPhase != GroundedPhase.IDLE
                && packet instanceof EntityDamageS2CPacket damage && damage.entityId() == groundedPlayer.getId()) {
            groundedWaitForRelease = true;
            to();
        } else if (groundedMainHandSession && tm() && groundedPhase == GroundedPhase.USING
                && packet instanceof EntityTrackerUpdateS2CPacket metadata && metadata.id() == groundedPlayer.getId()) {
            int flagsId = LivingEntityAccessor.getLivingEntityFlags().id();
            for (DataTracker.SerializedEntry<?> value : metadata.trackedValues()) {
                if (value.id() == flagsId && value.value() instanceof Byte flags) {
                    boolean offHandActive = (flags & 2) != 0;
                    if ((flags & 1) != 0 && offHandActive == (groundedUseHand == Hand.OFF_HAND)) {
                        groundedMainHandWindow.tK();
                    } else {
                        J(false);
                    }
                    break;
                }
            }
        }
        return false;
    }

    public boolean onSendPacket(Packet<?> packet) {
        if (!tk()) return false;

        if (groundedPhase != GroundedPhase.IDLE && !tm()) {
            to();
        } else {
            if (packet instanceof CommonPongC2SPacket pong
                    && (groundedPhase == GroundedPhase.WAIT_START || groundedPhase == GroundedPhase.USING)) {
                if (groundedMainHandSession) {
                    if (groundedMainHandPongStartedAt != 0L && System.nanoTime() - groundedMainHandPongStartedAt >= 2500000000L) {
                        J(true);
                        return false;
                    }
                    if (!groundedMainHandWindow.Y(pong.getParameter())) {
                        return false;
                    }
                    if (groundedPongs.isEmpty()) {
                        groundedMainHandPongStartedAt = System.nanoTime();
                    }
                }
                groundedPongs.addLast(pong);
                return true;
            }

            if (packet instanceof PlayerInteractItemC2SPacket use) {
                if (groundedPhase != GroundedPhase.IDLE) {
                    if (groundedPhase != GroundedPhase.USING || use.getHand() != groundedUseHand) {
                        return true;
                    } else if (groundedMainHandSession) {
                        tn();
                        groundedMainHandWindow.reset();
                        groundedMainHandPongStartedAt = 0L;
                    }
                    return false;
                }
                if (groundedWaitForRelease || mc.player == null || mc.world == null || mc.interactionManager == null
                        || mc.getNetworkHandler() == null || mc.currentScreen != null || !mc.player.isAlive()
                        || mc.player.hasVehicle() || mc.player.isSpectator()) {
                    return false;
                }

                ItemStack item = mc.player.getStackInHand(use.getHand());
                if (!a(mc.player, item)) return false;

                groundedConnection = mc.getNetworkHandler();
                groundedLevel = mc.world;
                groundedPlayer = mc.player;
                groundedSlot = groundedPlayer.getInventory().getSelectedSlot();
                groundedMainHandSession = true;
                groundedMainHandPongStartedAt = 0L;
                groundedMainHandWindow.reset();
                groundedMainHandPreparedAtTick = -1L;
                groundedUseHand = use.getHand();
                groundedStartedAt = System.nanoTime();
                groundedContinuation.reset();
                groundedPhase = GroundedPhase.WAIT_START;
                return true;
            } else {
                if (packet instanceof PlayerActionC2SPacket action && groundedPhase != GroundedPhase.IDLE) {
                    if (action.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
                        J(true);
                        return true;
                    } else if (action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
                        J(false);
                        return false;
                    }
                    return false;
                }
                if (packet instanceof UpdateSelectedSlotC2SPacket && groundedPhase != GroundedPhase.IDLE) {
                    J(true);
                }
            }
        }
        return false;
    }

    private synchronized void tl() {
        groundedTick++;
        if (mc.options == null || !mc.options.useKey.isPressed()) {
            groundedWaitForRelease = false;
        }

        if (groundedMainHandSession && groundedPhase == GroundedPhase.WAIT_START) {
            if (tk() && tm() && mc.interactionManager != null && mc.currentScreen == null
                    && mc.options.useKey.isPressed() && groundedPlayer.isAlive() && !groundedPlayer.hasVehicle()
                    && groundedPlayer.getInventory().getSelectedSlot() == groundedSlot) {
                if (groundedMainHandPreparedAtTick < 0L) {
                    groundedMainHandPreparedAtTick = groundedTick;
                }
            } else {
                J(true);
            }
        }

        if (groundedPhase != GroundedPhase.IDLE) {
            if (!tm()) {
                to();
            } else if (tk() && groundedPlayer.isAlive() && !groundedPlayer.hasVehicle()
                    && mc.currentScreen == null && groundedPlayer.getInventory().getSelectedSlot() == groundedSlot
                    && (groundedMainHandPongStartedAt == 0L || System.nanoTime() - groundedMainHandPongStartedAt < 2500000000L)
                    && (groundedPhase != GroundedPhase.WAIT_START || System.nanoTime() - groundedStartedAt <= 2000000000L)) {

                boolean useHeld = mc.options.useKey.isPressed();
                boolean usingSessionHand = groundedPlayer.isUsingItem() && groundedPlayer.getActiveHand() == groundedUseHand;
                boolean canRepeat = a(groundedPlayer, g(groundedUseHand));

                if (groundedContinuation.b(useHeld, usingSessionHand, canRepeat)) {
                    J(false);
                } else {
                    if (groundedPhase == GroundedPhase.WAIT_START && groundedMainHandPreparedAtTick >= 0L && groundedTick > groundedMainHandPreparedAtTick) {
                        if (mc.interactionManager == null) {
                            J(true);
                            return;
                        }
                        groundedPlayer.clearActiveItem();
                        groundedPhase = GroundedPhase.USING;
                        mc.interactionManager.interactItem(groundedPlayer, groundedUseHand);
                        if (!groundedPlayer.isUsingItem()) {
                            J(true);
                        }
                    }
                }
            } else {
                J(true);
            }
        }
    }

    private void ae(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            td();
        } else if (!(client.player.currentScreenHandler == client.player.playerScreenHandler)) {
            th();
        } else if (restoreRequested) {
            tg();
        } else {
            switch (phase) {
                case 1: phase = 2; break;
                case 2:
                    te();
                    client.interactionManager.interactItem(owner, activeHand);
                    client.interactionManager.interactItem(owner, otherHand);
                    lastUseMs = System.currentTimeMillis();
                    slow = false;
                    phase = 3;
                    break;
                case 3:
                    client.interactionManager.interactItem(owner, otherHand);
                    if (++repeats >= 15) phase = 4;
                    break;
                case 4:
                    client.interactionManager.interactItem(owner, otherHand);
                    client.interactionManager.interactItem(owner, otherHand);
                    phase = 5;
                    break;
                case 5:
                    client.interactionManager.interactItem(owner, otherHand);
                    phase = 6;
                    break;
                case 6:
                    client.interactionManager.interactItem(owner, otherHand);
                    phase = 7;
                    break;
                case 7: phase = 8; break;
                case 8: tg(); break;
                default: th();
            }
        }
    }

    public synchronized void td() {
        to();
        groundedWaitForRelease = false;
        th();
    }

    private void te() {
        mc.interactionManager.clickSlot(owner.playerScreenHandler.syncId, crossbowSlot, swapButton, SlotActionType.SWAP, owner);
        swapped = true;
    }

    private void tg() {
        if (swapped && mc.player == owner && mc.interactionManager != null && owner.currentScreenHandler == owner.playerScreenHandler) {
            te();
        }
        th();
        slow = false;
        lastUseMs = System.currentTimeMillis() - 900L;
    }

    private void th() {
        phase = 0;
        repeats = 0;
        crossbowSlot = -1;
        swapped = false;
        restoreRequested = false;
        slow = true;
        owner = null;
        activeHand = null;
        otherHand = null;
    }

    private void ti() {
        if (forcedSprintDuringEat && mc.options != null) {
            if (!sprintWasPressedBeforeEat) {
                mc.options.sprintKey.setPressed(false);
            }
            forcedSprintDuringEat = false;
            sprintWasPressedBeforeEat = false;
        }
    }

    private static boolean a(ClientPlayerEntity player, ItemStack stack) {
        if (!stack.isEmpty() && stack.getMaxUseTime(player) > 0) {
            ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
            return consumable == null || consumable.canConsume(player, stack);
        }
        return false;
    }

    private boolean tm() {
        return groundedConnection == mc.getNetworkHandler() && groundedPlayer == mc.player && groundedLevel == mc.world;
    }

    private ItemStack g(Hand hand) {
        return hand == Hand.MAIN_HAND ? groundedPlayer.getInventory().getStack(groundedSlot) : groundedPlayer.getOffHandStack();
    }

    private void tn() {
        while (!groundedPongs.isEmpty()) {
            Packet<?> p = groundedPongs.removeFirst();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(p);
            }
        }
    }

    private void J(boolean requireRelease) {
        groundedWaitForRelease = requireRelease;
        to();
    }

    private void to() {
        if (groundedConnection != null && groundedConnection == mc.getNetworkHandler()) {
            tn();
        }
        groundedPongs.clear();
        groundedPhase = GroundedPhase.IDLE;
        groundedConnection = null;
        groundedPlayer = null;
        groundedLevel = null;
        groundedMainHandSession = false;
        groundedMainHandPongStartedAt = 0L;
        groundedMainHandWindow.reset();
        groundedMainHandPreparedAtTick = -1L;
        groundedContinuation.reset();
    }

    public void reset() {
        td();
        ticks = 0;
        cycleCounter = 0;
        cancelSlowThisTick = false;
    }

    private enum GroundedPhase {
        IDLE, WAIT_START, USING
    }
}
