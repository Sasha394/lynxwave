package dev.client.api.nullcry.helper.entity.world;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.compat.InputCompat;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import lombok.experimental.UtilityClass;
import net.minecraft.client.option.KeyBinding;

import java.util.Arrays;

@UtilityClass
public class LegitUseScheduler {

    public void run(boolean shouldDelay, Runnable action) {
        if (action == null) {
            return;
        }

        if (!shouldDelay) {
            action.run();
            return;
        }

        MovementSnapshot snapshot = MovementSnapshot.capture();

        Runnable freezeAndRun = () -> {
            stopMovement();
            try {
                action.run();
            } finally {
                if (snapshot != null) {
                    snapshot.restore();
                }
            }
        };

        PlayerInventoryComponent.addTask(freezeAndRun);
    }

    private void stopMovement() {
        if (ClientApi.mc == null || ClientApi.mc.player == null) {
            return;
        }

        Arrays.stream(MovingUtil.getMovementKeys(true)).forEach(keyBinding -> {
            if (keyBinding != null) {
                keyBinding.setPressed(false);
            }
        });

        if (ClientApi.mc.player.input != null) {
            InputCompat.setMovement(ClientApi.mc.player.input, 0f, 0f);
        }

        ClientApi.mc.player.setSprinting(false);
        ClientApi.mc.player.setVelocity(0.0, ClientApi.mc.player.getVelocity().y, 0.0);
    }

    private record MovementSnapshot(KeyBinding[] bindings, boolean[] pressedStates, float forward, float sideways, boolean sprinting) {

        private static MovementSnapshot capture() {
            if (ClientApi.mc == null || ClientApi.mc.player == null) {
                return null;
            }

            KeyBinding[] movementKeys = MovingUtil.getMovementKeys(true);
            boolean[] states = new boolean[movementKeys.length];

            for (int i = 0; i < movementKeys.length; i++) {
                KeyBinding keyBinding = movementKeys[i];
                states[i] = keyBinding != null && keyBinding.isPressed();
            }

            float forward = 0f;
            float sideways = 0f;

            if (ClientApi.mc.player.input != null) {
                forward = InputCompat.movementForward(ClientApi.mc.player.input);
                sideways = InputCompat.movementSideways(ClientApi.mc.player.input);
            }

            boolean sprinting = ClientApi.mc.player.isSprinting();

            return new MovementSnapshot(movementKeys, states, forward, sideways, sprinting);
        }

        private void restore() {
            if (ClientApi.mc == null || ClientApi.mc.player == null) {
                return;
            }

            for (int i = 0; i < bindings.length; i++) {
                KeyBinding keyBinding = bindings[i];
                if (keyBinding != null) {
                    keyBinding.setPressed(pressedStates[i]);
                }
            }

            if (ClientApi.mc.player.input != null) {
                InputCompat.setMovement(ClientApi.mc.player.input, forward, sideways);
            }

            ClientApi.mc.player.setSprinting(sprinting);
            KeyBinding.updatePressedStates();
        }
    }
}
