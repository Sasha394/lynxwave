package dev.client.api.nullcry.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Stack;

public class ScissorUtil {

    static Stack<Builder> stack = new Stack<>();
    private static float globalScale = 1f;
    private static float globalCenterX = 0f;
    private static float globalCenterY = 0f;

    public static void setTransform(float scale, float centerX, float centerY) {
        globalScale = scale;
        globalCenterX = centerX;
        globalCenterY = centerY;
    }

    public static void resetTransform() {
        setTransform(1f, 0f, 0f);
    }

    private static float transformX(float x) {
        return globalCenterX + globalScale * (x - globalCenterX);
    }

    private static float transformY(float y) {
        return globalCenterY + globalScale * (y - globalCenterY);
    }

    public static void enable(float x, float y, float width, float height) {
        float tx1 = transformX(x);
        float ty1 = transformY(y);
        float tx2 = transformX(x + width);
        float ty2 = transformY(y + height);

        float rx = Math.min(tx1, tx2);
        float ry = Math.min(ty1, ty2);
        float rw = Math.abs(tx2 - tx1);
        float rh = Math.abs(ty2 - ty1);

        int scale = (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        int winHeight = MinecraftClient.getInstance().getWindow().getHeight();

        int sx = Math.round(rx * scale);
        int sy = winHeight - Math.round((ry + rh) * scale);
        int sw = Math.round(rw * scale);
        int sh = Math.round(rh * scale);

        if (!stack.isEmpty()) {
            Builder p = stack.peek();
            int ex = Math.max(sx, p.x);
            int ey = Math.max(sy, p.y);
            int ex2 = Math.min(sx + sw, p.x + p.width);
            int ey2 = Math.min(sy + sh, p.y + p.height);
            sx = ex;
            sy = ey;
            sw = Math.max(0, ex2 - ex);
            sh = Math.max(0, ey2 - ey);
        }

        Builder builder = new Builder().set(sx, sy, sw, sh);
        stack.push(builder);
        builder.apply();
    }

    public static void enableContext(DrawContext context, float x, float y, float width, float height) {
        var win = net.minecraft.client.MinecraftClient.getInstance().getWindow();
        double s = win.getScaleFactor();
        int fbH = win.getFramebufferHeight();

        float tx1 = transformX(x);
        float ty1 = transformY(y);
        float tx2 = transformX(x + width);
        float ty2 = transformY(y + height);

        float rx = Math.min(tx1, tx2);
        float ry = Math.min(ty1, ty2);
        float rw = Math.abs(tx2 - tx1);
        float rh = Math.abs(ty2 - ty1);

        if (context != null) {
            int cx1 = (int) Math.floor(rx);
            int cy1 = (int) Math.floor(ry);
            int cx2 = (int) Math.ceil(rx + rw);
            int cy2 = (int) Math.ceil(ry + rh);
            context.enableScissor(cx1, cy1, cx2, cy2);
        }

        int sx = (int) Math.floor(rx * s);
        int sy = (int) Math.floor(fbH - (ry + rh) * s);
        int sw = (int) Math.ceil(rw * s);
        int sh = (int) Math.ceil(rh * s);

        if (!stack.isEmpty()) {
            Builder p = stack.peek();
            int ex = Math.max(sx, p.x);
            int ey = Math.max(sy, p.y);
            int ex2 = Math.min(sx + sw, p.x + p.width);
            int ey2 = Math.min(sy + sh, p.y + p.height);
            sx = ex;
            sy = ey;
            sw = Math.max(0, ex2 - ex);
            sh = Math.max(0, ey2 - ey);
        }

        Builder b = new Builder().set(sx, sy, sw, sh);
        stack.addLast(b);
        com.mojang.blaze3d.systems.RenderSystem.enableScissorForRenderTypeDraws(b.x, b.y, b.width, b.height);
    }

    public static void disable() {
        if (!stack.isEmpty()) {
            stack.pop();
            if (stack.isEmpty()) {
                RenderSystem.disableScissorForRenderTypeDraws();
            } else {
                stack.peek().apply();
            }
        }
    }

    public static void disableContext(DrawContext context) {
        if (stack.isEmpty()) return;

        if (context != null) {
            context.disableScissor();
        }

        stack.removeLast();
        if (stack.isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
        } else {
            Builder p = stack.peek();
            com.mojang.blaze3d.systems.RenderSystem.enableScissorForRenderTypeDraws(p.x, p.y, p.width, p.height);
        }
    }

    private static class Builder {
        int x, y, width, height;

        public Builder set(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;

            return this;
        }

        public void apply() {
            RenderSystem.enableScissorForRenderTypeDraws(x, y, width, height);
        }
    }
}
