package dev.client.api.nullcry.render;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class CursorsUtil {
    public static final long ARROW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
    public static final long HAND = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
    public static final long IBEAM = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);
    public static final long HRESIZE = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);

    private static long lastCursor = -1L;
    private static long pendingCursor = ARROW;
    private static boolean frameActive = false;

    public static void setCursor(long cursor) {
        if (frameActive) {
            pendingCursor = cursor;
            return;
        }

        applyCursor(cursor);
    }

    public static void beginFrame() {
        frameActive = true;
        pendingCursor = ARROW;
    }

    public static void applyFrameCursor() {
        if (!frameActive) return;
        frameActive = false;
        applyCursor(pendingCursor);
    }

    public static void resetState() {
        frameActive = false;
        pendingCursor = ARROW;
        lastCursor = -1L;
        applyCursor(ARROW);
    }

    private static void applyCursor(long cursor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        long window = mc.getWindow().getHandle();
        if (window == 0L) return;

        if (lastCursor != cursor) {
            GLFW.glfwSetCursor(window, cursor);
            lastCursor = cursor;
        }
    }

    public static void resetCursor() {
        if (frameActive) {
            pendingCursor = ARROW;
        } else {
            applyCursor(ARROW);
        }
    }
}
