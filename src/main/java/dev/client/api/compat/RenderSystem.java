package dev.client.api.compat;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class RenderSystem {
    public static final int SRC_ALPHA = GL11.GL_SRC_ALPHA;
    public static final int ONE_MINUS_SRC_ALPHA = GL11.GL_ONE_MINUS_SRC_ALPHA;
    public static final int ONE = GL11.GL_ONE;
    public static final int ZERO = GL11.GL_ZERO;

    private static final Matrix4f projectionMatrix = new Matrix4f();

    private RenderSystem() {
    }

    public static void enableBlend() {
        GlStateManager._enableBlend();
    }

    public static void disableBlend() {
        GlStateManager._disableBlend();
    }

    public static void defaultBlendFunc() {
        blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO);
    }

    public static void blendFunc(int srcFactor, int dstFactor) {
        blendFuncSeparate(srcFactor, dstFactor, ONE, ZERO);
    }

    public static void blendFuncSeparate(int srcFactor, int dstFactor, int srcAlpha, int dstAlpha) {
        GlStateManager._blendFuncSeparate(srcFactor, dstFactor, srcAlpha, dstAlpha);
    }

    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    public static void disableDepthTest() {
        GlStateManager._disableDepthTest();
    }

    public static void depthMask(boolean mask) {
        GlStateManager._depthMask(mask);
    }

    public static void depthFunc(int function) {
        GlStateManager._depthFunc(function);
    }

    public static void enableCull() {
        GlStateManager._enableCull();
    }

    public static void disableCull() {
        GlStateManager._disableCull();
    }

    public static void lineWidth(float width) {
        GL11.glLineWidth(width);
    }

    public static void setShaderTexture(int slot, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + slot);
        GlStateManager._bindTexture(texture);
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        // Color is now pipeline/vertex driven in 1.21.11. Kept for legacy call sites.
    }

    public static void enableScissorForRenderTypeDraws(int x, int y, int width, int height) {
        com.mojang.blaze3d.systems.RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
    }

    public static void disableScissorForRenderTypeDraws() {
        com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
    }

    public static Matrix4fStack getModelViewStack() {
        return com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
    }

    public static Matrix4f getModelViewMatrix() {
        return com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
    }

    public static Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public static void setProjectionMatrixSnapshot(Matrix4f matrix) {
        projectionMatrix.set(matrix);
    }
}
