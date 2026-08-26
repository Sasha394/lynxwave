package dev.client.api.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.util.PlayerInput;

public final class InputCompat {
    private InputCompat() {
    }

    public static boolean isShiftDown() {
        Window window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isControlDown() {
        Window window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isAltDown() {
        Window window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_ALT);
    }

    public static float movementForward(Input input) {
        if (input == null || input.playerInput == null) return 0.0f;
        return (input.playerInput.forward() ? 1.0f : 0.0f) - (input.playerInput.backward() ? 1.0f : 0.0f);
    }

    public static float movementSideways(Input input) {
        if (input == null || input.playerInput == null) return 0.0f;
        return (input.playerInput.left() ? 1.0f : 0.0f) - (input.playerInput.right() ? 1.0f : 0.0f);
    }

    public static void setMovement(Input input, float forward, float sideways) {
        if (input == null) return;
        PlayerInput old = input.playerInput != null ? input.playerInput : PlayerInput.DEFAULT;
        input.playerInput = new PlayerInput(
                forward > 0.0f,
                forward < 0.0f,
                sideways > 0.0f,
                sideways < 0.0f,
                old.jump(),
                old.sneak(),
                old.sprint()
        );
    }

    public static void setMovementForward(Input input, float forward) {
        setMovement(input, forward, movementSideways(input));
    }

    public static void setMovementSideways(Input input, float sideways) {
        setMovement(input, movementForward(input), sideways);
    }
}
