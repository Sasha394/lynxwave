package dev.client.api.nullcry.render.core;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

public class GLUtils implements ClientApi {
    private static final Deque<RenderState> STATE_STACK = new ArrayDeque<>();
    private static RawProjectionMatrix overlayProjection;

    private static final class RenderState {
        private final int scaleFactor;
        private final GpuBufferSlice projectionMatrix;
        private final ProjectionType projectionType;

        private RenderState(int scaleFactor, GpuBufferSlice projectionMatrix, ProjectionType projectionType) {
            this.scaleFactor = scaleFactor;
            this.projectionMatrix = projectionMatrix;
            this.projectionType = projectionType;
        }
    }

    public static void setupOverlayRendering(int scale) {
        STATE_STACK.push(new RenderState(
                (int) mc.getWindow().getScaleFactor(),
                RenderSystem.getProjectionMatrixBuffer(),
                RenderSystem.getProjectionType()
        ));

        mc.getWindow().setScaleFactor(scale);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        Matrix4f projectionMatrix = new Matrix4f();
        projectionMatrix.setOrtho(0.0f, (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight(), 0.0f, 1000.0f, 11000.0f);

        RenderSystem.setProjectionMatrix(getOverlayProjection().set(projectionMatrix), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.getModelViewStack().translate(0.0f, 0.0f, -11000.0f);
    }

    public static void setupOverlayRendering() {
        int scale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        setupOverlayRendering(scale);
    }

    public static void restoreRendering() {
        if (STATE_STACK.isEmpty()) {
            return;
        }

        RenderState state = STATE_STACK.pop();
        mc.getWindow().setScaleFactor(state.scaleFactor);
        RenderSystem.getModelViewStack().popMatrix();

        if (state.projectionMatrix != null && state.projectionType != null) {
            RenderSystem.setProjectionMatrix(state.projectionMatrix, state.projectionType);
        }
    }

    public static boolean isOverlayRenderingActive() {
        return !STATE_STACK.isEmpty();
    }

    public static double scaleMouseX(double mouseX, double targetScale) {
        double sourceScale = getSourceScaleFactor();
        if (targetScale <= 0) {
            return mouseX;
        }
        return mouseX * (sourceScale / targetScale);
    }

    public static double scaleMouseY(double mouseY, double targetScale) {
        double sourceScale = getSourceScaleFactor();
        if (targetScale <= 0) {
            return mouseY;
        }
        return mouseY * (sourceScale / targetScale);
    }

    public static int getScaledWidthFor(int scale) {
        if (scale <= 0) {
            return mc.getWindow().getScaledWidth();
        }
        return getScaledDimension(mc.getWindow().getFramebufferWidth(), scale);
    }

    public static int getScaledHeightFor(int scale) {
        if (scale <= 0) {
            return mc.getWindow().getScaledHeight();
        }
        return getScaledDimension(mc.getWindow().getFramebufferHeight(), scale);
    }

    public static int getScaledWidth() {
        return mc.getWindow().getScaledWidth();
    }

    public static int getScaledHeight() {
        return mc.getWindow().getScaledHeight();
    }

    public static double getScaleFactor() {
        return mc.getWindow().getScaleFactor();
    }

    public static Window getWindow() {
        return mc.getWindow();
    }

    private static double getSourceScaleFactor() {
        RenderState state = STATE_STACK.peek();
        if (state != null) {
            return state.scaleFactor;
        }
        return mc.getWindow().getScaleFactor();
    }

    private static int getScaledDimension(int framebufferSize, int scale) {
        double scaledExact = (double) framebufferSize / (double) scale;
        int scaled = (int) scaledExact;
        if (scaledExact > scaled) {
            scaled++;
        }
        return scaled;
    }

    private static RawProjectionMatrix getOverlayProjection() {
        if (overlayProjection == null) {
            overlayProjection = new RawProjectionMatrix("lynxwave_overlay_projection");
        }
        return overlayProjection;
    }
}
