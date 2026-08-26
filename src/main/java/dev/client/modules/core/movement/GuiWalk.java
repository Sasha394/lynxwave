package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.InventoryCloseEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GuiWalk extends Module {

    public GuiWalk() {
        super("GuiWalk", ModuleCategory.Movement, "Свободное перемещение в инвентаре");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Default", "Legit").defaultValue("Default").register(this);

    final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
    boolean wasTyping = false;

    enum MovePhase { READY, SLOWING_DOWN, ALLOW_MOVEMENT, SPEEDING_UP, SEND_PACKETS, FINISHED }

    MovePhase movePhase = MovePhase.READY;
    long actionStartTime = 0L;
    boolean wasForwardPressed, wasBackPressed, wasLeftPressed, wasRightPressed, wasJumpPressed;
    boolean keysOverridden = false;
    boolean inventoryOpened = false;
    boolean packetsHeld = false;

    @Subscribe
    public void onPacket(PacketEvent event) {
        final Packet<?> packet = event.getPacket();
        if (!(mc.currentScreen instanceof InventoryScreen)) return;
        if (mode.isSelected("Legit")) {
            if (packet instanceof ClickSlotC2SPacket wrapper && shouldHoldLegitPacket()) {
                packets.add(wrapper);
                packetsHeld = true;
                event.cancelled();
            }

            if (packet instanceof CloseScreenS2CPacket screen && screen.getSyncId() == 0) {
                event.cancelled();
            }
            return;
        }
        if (packet instanceof ClickSlotC2SPacket wrapper && shouldBufferClick(wrapper)) {
            packets.add(wrapper);
            event.cancelled();
        }

        if (packet instanceof CloseScreenS2CPacket) {
            event.cancelled();
        }
    }

    @Subscribe
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!mode.isSelected("Legit") && event.getScreen() instanceof InventoryScreen && shouldFlushPackets()) {
            PlayerInventoryComponent.addTask(() -> {
                packets.forEach(packet -> sendPacket(packet));
                packets.clear();
            });
        }
        if (mode.isSelected("Legit") && packetsHeld && movePhase == MovePhase.ALLOW_MOVEMENT) {
            movePhase = MovePhase.SLOWING_DOWN;
            actionStartTime = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mode.isSelected("Legit")) {
            processLegitMovement();
            return;
        }

        if (movePhase != MovePhase.READY) {
            resetState();
        }

        if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof SignEditScreen) {
            wasTyping = false;
            return;
        }

        final boolean typing = handlerClient().isFocusedRender();

        if (typing && !wasTyping) {
            Arrays.stream(MovingUtil.getMovementKeys(true)).forEach(k -> k.setPressed(false));

            if (mc.player != null && mc.player.input != null) {
                dev.client.api.compat.InputCompat.setMovementForward(mc.player.input, 0);
                dev.client.api.compat.InputCompat.setMovementSideways(mc.player.input, 0);
            }

            if (mc.player != null) {
                mc.player.setSprinting(false);
            }
        }

        wasTyping = typing;

        if (typing) {
            Arrays.stream(MovingUtil.getMovementKeys(true)).forEach(k -> k.setPressed(false));
            return;
        }

        Arrays.stream(MovingUtil.getMovementKeys(false)).forEach(keyBinding -> keyBinding.setPressed(InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode())));
    }

    private void processLegitMovement() {
        boolean hasOpenScreen = mc.currentScreen != null
                && !(mc.currentScreen instanceof ChatScreen)
                && !(mc.currentScreen instanceof SignEditScreen);

        if (hasOpenScreen && !inventoryOpened && movePhase == MovePhase.READY) {
            startLegitMovement();
            inventoryOpened = true;
        }

        if (!hasOpenScreen && inventoryOpened) {
            if (packetsHeld && movePhase == MovePhase.ALLOW_MOVEMENT) {
                movePhase = MovePhase.SLOWING_DOWN;
                actionStartTime = System.currentTimeMillis();
            } else if (!packetsHeld) {
                resetState();
            }
            inventoryOpened = false;
            return;
        }

        if (movePhase != MovePhase.READY) {
            handleMovementStates();
        }
    }

    private void startLegitMovement() {
        wasForwardPressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.forwardKey.getDefaultKey().getCode());
        wasBackPressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.backKey.getDefaultKey().getCode());
        wasLeftPressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.leftKey.getDefaultKey().getCode());
        wasRightPressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.rightKey.getDefaultKey().getCode());
        wasJumpPressed = InputUtil.isKeyPressed(mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode());

        movePhase = MovePhase.ALLOW_MOVEMENT;
        keysOverridden = false;
        packetsHeld = false;
    }

    private void handleMovementStates() {
        long elapsed = System.currentTimeMillis() - actionStartTime;

        switch (movePhase) {
            case SLOWING_DOWN -> {
                if (mc.player != null && mc.player.input != null) {
                    dev.client.api.compat.InputCompat.setMovementForward(mc.player.input, 0);
                    dev.client.api.compat.InputCompat.setMovementSideways(mc.player.input, 0);
                }

                if (!keysOverridden) {
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                    mc.options.jumpKey.setPressed(false);
                    keysOverridden = true;
                }

                if (elapsed > 1) {
                    movePhase = MovePhase.SEND_PACKETS;
                    actionStartTime = System.currentTimeMillis();
                }
            }

            case ALLOW_MOVEMENT -> updateMovementKeys();

            case SEND_PACKETS -> {
                if (!packets.isEmpty()) {
                    packets.forEach(GuiWalk::sendPacket);
                    packets.clear();
                }
                packetsHeld = false;
                movePhase = MovePhase.SPEEDING_UP;
                actionStartTime = System.currentTimeMillis();
            }

            case SPEEDING_UP -> {
                long speedupElapsed = System.currentTimeMillis() - actionStartTime;
                float speedupProgress = Math.min(1.0f, speedupElapsed / 1.0f);

                if (keysOverridden) {
                    restoreKeyStates();
                }

                if (mc.player != null && mc.player.input != null) {
                    boolean forward = InputUtil.isKeyPressed(mc.getWindow(), mc.options.forwardKey.getDefaultKey().getCode());
                    float targetForward = forward ? 1.0f : 0;
                    dev.client.api.compat.InputCompat.setMovementForward(mc.player.input, lerp(dev.client.api.compat.InputCompat.movementForward(mc.player.input), targetForward * speedupProgress, 0.4f));

                    if (speedupProgress > 0.5f && forward && !mc.player.isSprinting()) {
                        mc.player.setSprinting(true);
                    }
                }

                if (speedupElapsed > 1) {
                    movePhase = MovePhase.FINISHED;
                }
            }

            case FINISHED -> resetState();
            default -> {
            }
        }
    }

    private void restoreKeyStates() {
        boolean currentForward = InputUtil.isKeyPressed(mc.getWindow(), mc.options.forwardKey.getDefaultKey().getCode());
        boolean currentBack = InputUtil.isKeyPressed(mc.getWindow(), mc.options.backKey.getDefaultKey().getCode());
        boolean currentLeft = InputUtil.isKeyPressed(mc.getWindow(), mc.options.leftKey.getDefaultKey().getCode());
        boolean currentRight = InputUtil.isKeyPressed(mc.getWindow(), mc.options.rightKey.getDefaultKey().getCode());
        boolean currentJump = InputUtil.isKeyPressed(mc.getWindow(), mc.options.jumpKey.getDefaultKey().getCode());

        mc.options.forwardKey.setPressed(wasForwardPressed && currentForward);
        mc.options.backKey.setPressed(wasBackPressed && currentBack);
        mc.options.leftKey.setPressed(wasLeftPressed && currentLeft);
        mc.options.rightKey.setPressed(wasRightPressed && currentRight);
        mc.options.jumpKey.setPressed(wasJumpPressed && currentJump);
        keysOverridden = false;
    }

    private float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private void resetState() {
        if (keysOverridden) {
            restoreKeyStates();
        }
        movePhase = MovePhase.READY;
        inventoryOpened = false;
        packetsHeld = false;
        packets.clear();
    }

    private void updateMovementKeys() {
        Arrays.stream(MovingUtil.getMovementKeys(false)).forEach(keyBinding -> keyBinding.setPressed(InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode())));
    }

    private boolean shouldBufferClick(ClickSlotC2SPacket packet) {
        if (!MovingUtil.isMoving()) {
            return false;
        }

        if (isFT()) {
            return true;
        }

        if (isSpookyTime()) {
            return packet.actionType() == SlotActionType.PICKUP;
        }

        return false;
    }

    private boolean shouldFlushPackets() {
        return !packets.isEmpty() && (isFT() || isSpookyTime());
    }

    private boolean shouldHoldLegitPacket() {
        return packetsHeld || hasMovementKeyPressed();
    }

    private boolean hasMovementKeyPressed() {
        var window = mc.getWindow();
        return InputUtil.isKeyPressed(window, mc.options.forwardKey.getDefaultKey().getCode())
                || InputUtil.isKeyPressed(window, mc.options.backKey.getDefaultKey().getCode())
                || InputUtil.isKeyPressed(window, mc.options.leftKey.getDefaultKey().getCode())
                || InputUtil.isKeyPressed(window, mc.options.rightKey.getDefaultKey().getCode())
                || InputUtil.isKeyPressed(window, mc.options.jumpKey.getDefaultKey().getCode());
    }
}
