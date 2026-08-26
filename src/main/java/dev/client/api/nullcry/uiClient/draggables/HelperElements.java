package dev.client.api.nullcry.uiClient.draggables;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.modules.core.render.Interface;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;

@UtilityClass
public class HelperElements implements ClientApi {

    public void rectElements(DrawContext context, float x, float y, float width, float height) {
        rectElements(context, x, y, width, height, 1.0f);
    }

    public void rect(Matrix4f matrix4f, float x, float y, float width, float height) {
        rectElements(matrix4f, x, y, width, height, 1.0f);
    }

    public void rectElements(Matrix4f matrix4f, float x, float y, float width, float height) {
        rectElements(matrix4f, x, y, width, height, 1.0f);
    }

    public void rect(DrawContext context, float x, float y, float width, float height) {
        rectElements(context, x, y, width, height, 1.0f);
    }

    public void rectGui(DrawContext context, float x, float y, float width, float height) {
        rectGui(context, x, y, width, height, 1.0f);
    }

    public void rect(DrawContext context, float x, float y, float width, float height, float show) {
        rectElements(context, x, y, width, height, show);
    }

    public void rect(Matrix4f matrix4f, float x, float y, float width, float height, float show) {
        rectElements(matrix4f, x, y, width, height, show);
    }

    public void rectElements(DrawContext context, float x, float y, float width, float height, float show) {
        renderPanel(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y, width, height, getRadius(), show);
    }

    public void rectElements(Matrix4f context, float x, float y, float width, float height, float show) {
        renderPanel(context, x, y, width, height, getRadius(), show);
    }

    public void rectGui(DrawContext context, float x, float y, float width, float height, float show) {
        renderPanel(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), x, y, width, height, 10 / 2f, show);
    }

    private void renderPanel(Matrix4f matrix, float x, float y, float width, float height, float radius, float show) {
        float s = clamp01(show);
        if (isExcellentMode()) {
            renderExcellentPanel(matrix, x, y, width, height, s);
        } else if (isLiquidGlassMode()) {
            renderLiquidGlassPanel(matrix, x, y, width, height, radius, s);
        } else {
            renderMinimalismPanel(matrix, x, y, width, height, radius, s);
        }
    }

    private void renderExcellentPanel(Matrix4f matrix, float x, float y, float width, float height, float showProgress) {
        Interface module = Interface.INSTANCE;
        if (module == null || width <= 0.0f || height <= 0.0f || showProgress <= 0.0f) {
            return;
        }

        float radius = 2.0f;
        int topLeft = module.getExcellentColor(0);
        int topRight = module.getExcellentColor(90);
        int bottomRight = module.getExcellentColor(180);
        int bottomLeft = module.getExcellentColor(270);

        // Original Excellent uses a soft coloured shadow around a very dark
        // translucent panel. The border itself is not a solid rainbow outline.
        QuadColorState glowColors = new QuadColorState(
                ColorUtils.setAlpha(topLeft, Math.round(44 * showProgress)),
                ColorUtils.setAlpha(bottomLeft, Math.round(44 * showProgress)),
                ColorUtils.setAlpha(bottomRight, Math.round(44 * showProgress)),
                ColorUtils.setAlpha(topRight, Math.round(44 * showProgress))
        );

        Builder.GLOW_BUILDER
                .size(new SizeState(width, height))
                .radius(new QuadRadiusState(radius))
                .color(glowColors)
                .softness(0.82f)
                .glowRadius(5.5f)
                .build()
                .render(matrix, x, y);

        QuadColorState fillColors = new QuadColorState(
                excellentDarkColor(topLeft, 0.35f, Math.round(180 * showProgress)),
                excellentDarkColor(bottomLeft, 0.35f, Math.round(180 * showProgress)),
                excellentDarkColor(bottomRight, 0.35f, Math.round(180 * showProgress)),
                excellentDarkColor(topRight, 0.35f, Math.round(180 * showProgress))
        );

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(fillColors)
                .radius(new QuadRadiusState(radius))
                .build()
                .render(matrix, x, y);

        int lineAlpha = Math.round(205 * showProgress);
        float lineSize = 0.7f;
        int topCenter = ColorUtils.interpolate(topLeft, topRight, 0.5f);
        int bottomCenter = ColorUtils.interpolate(bottomLeft, bottomRight, 0.5f);
        int leftCenter = ColorUtils.interpolate(topLeft, bottomLeft, 0.5f);
        int rightCenter = ColorUtils.interpolate(topRight, bottomRight, 0.5f);

        drawExcellentHorizontalEdge(matrix, x + radius, y, width - radius * 2f,
                lineSize, topCenter, lineAlpha);
        drawExcellentHorizontalEdge(matrix, x + radius, y + height - lineSize, width - radius * 2f,
                lineSize, bottomCenter, lineAlpha);
        drawExcellentVerticalEdge(matrix, x, y + radius, height - radius * 2f,
                lineSize, leftCenter, lineAlpha);
        drawExcellentVerticalEdge(matrix, x + width - lineSize, y + radius, height - radius * 2f,
                lineSize, rightCenter, lineAlpha);
    }

    public void excellentSeparator(Matrix4f matrix, float x, float y, float width, float showProgress) {
        Interface module = Interface.INSTANCE;
        if (module == null || !module.isExcellentMode()) return;
        int centerColor = ColorUtils.interpolate(module.getExcellentColor(0), module.getExcellentColor(90), 0.5f);
        drawExcellentHorizontalEdge(matrix, x + 2f, y, Math.max(0f, width - 4f),
                0.7f, centerColor, Math.round(190 * clamp01(showProgress)));
    }

    private void drawExcellentHorizontalEdge(Matrix4f matrix, float x, float y, float width,
                                             float height, int color, int alpha) {
        if (width <= 0f || height <= 0f) return;
        float half = width / 2f;
        int transparent = ColorUtils.setAlpha(color, 0);
        int center = ColorUtils.setAlpha(color, alpha);

        drawExcellentLinePart(matrix, x, y, half, height,
                new QuadColorState(transparent, transparent, center, center));
        drawExcellentLinePart(matrix, x + half, y, width - half, height,
                new QuadColorState(center, center, transparent, transparent));
    }

    private void drawExcellentVerticalEdge(Matrix4f matrix, float x, float y, float height,
                                           float width, int color, int alpha) {
        if (width <= 0f || height <= 0f) return;
        float half = height / 2f;
        int transparent = ColorUtils.setAlpha(color, 0);
        int center = ColorUtils.setAlpha(color, alpha);

        drawExcellentLinePart(matrix, x, y, width, half,
                new QuadColorState(transparent, center, center, transparent));
        drawExcellentLinePart(matrix, x, y + half, width, height - half,
                new QuadColorState(center, transparent, transparent, center));
    }

    private void drawExcellentLinePart(Matrix4f matrix, float x, float y, float width,
                                       float height, QuadColorState color) {
        Builder.GLOW_BUILDER
                .size(new SizeState(width, height))
                .radius(new QuadRadiusState(0.35f))
                .color(color)
                .softness(0.85f)
                .glowRadius(3.5f)
                .build()
                .render(matrix, x, y);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(color)
                .radius(new QuadRadiusState(0.35f))
                .build()
                .render(matrix, x, y);
    }

    private int excellentDarkColor(int color, float factor, int alpha) {
        int red = Math.round(((color >>> 16) & 0xFF) * factor);
        int green = Math.round(((color >>> 8) & 0xFF) * factor);
        int blue = Math.round((color & 0xFF) * factor);
        return ColorUtils.setAlpha(ColorUtils.rgb(red, green, blue), clamp255(alpha));
    }

    private void renderMinimalismPanel(Matrix4f matrix, float x, float y, float width, float height, float radius, float showProgress) {
        if (isBlurEnabled(showProgress)) {
            Builder.BLUR_BUILDER
                    .blurRadius(getBlurRadius())
                    .radius(new QuadRadiusState(radius))
                    .size(new SizeState(width, height))
                    .alpha(getBlurAlpha(showProgress))
                    .build()
                    .render(matrix, x, y);
        }

        drawBackground(matrix, x, y, width, height, radius, getMinimalismBackgroundAlpha(showProgress));
        drawAccentBorder(matrix, x, y, width, height, radius, showProgress);
    }

    private void renderLiquidGlassPanel(Matrix4f matrix, float x, float y, float width, float height, float radius, float showProgress) {
        float glassAlpha = getGlassLayerAlpha(showProgress);
        if (glassAlpha > 0.0f) {
            Builder.LIQUID_GLASS_BUILDER
                    .size(new SizeState(width, height))
                    .radius(new QuadRadiusState(radius))
                    .color(QuadColorState.WHITE)
                    .smoothness(0.5f)
                    .cornerSmoothness(2.0f)
                    .globalAlpha(glassAlpha)
                    .fresnelPower(getGlassFresnelPower(height))
                    .fresnelColor(0xFFFFFFFF)
                    .baseAlpha(1.0f)
                    .fresnelInvert(true)
                    .fresnelMix(0.0f)
                    .distortStrength(getGlassDistortion())
                    .build()
                    .render(matrix, x, y);
        }

        drawBackground(matrix, x, y, width, height, radius, getGlassBackgroundAlpha(showProgress));
        drawAccentBorder(matrix, x, y, width, height, radius, showProgress);
    }

    private void drawBackground(Matrix4f matrix, float x, float y, float width, float height, float radius, int alpha) {
        QuadColorState color;
        if (isMinimalismCustom()) {
            int bg = getMinimalismCustomBg();
            int r = (bg >> 16) & 0xFF;
            int g = (bg >> 8) & 0xFF;
            int b = bg & 0xFF;
            int rm = clamp255(r - 2), gm = clamp255(g - 2), bm = clamp255(b - 2);
            int rp = clamp255(r + 2), gp = clamp255(g + 2), bp = clamp255(b + 2);
            int rd = clamp255(r - 4), gd = clamp255(g - 4), bd = clamp255(b - 4);
            color = new QuadColorState(
                    ColorUtils.setAlpha(ColorUtils.rgb(rm, gm, bm), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(rp, gp, bp), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(rd, gd, bd), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(r, g, b), alpha));
        } else if (isMinimalismLight()) {
            color = new QuadColorState(
                    ColorUtils.setAlpha(ColorUtils.rgb(238, 239, 244), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(245, 246, 250), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(230, 231, 236), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(236, 237, 242), alpha));
        } else {
            color = new QuadColorState(
                    ColorUtils.setAlpha(ColorUtils.rgb(18, 19, 24), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(22, 23, 28), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(16, 17, 22), alpha),
                    ColorUtils.setAlpha(ColorUtils.rgb(20, 21, 26), alpha));
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(width, height))
                .color(color)
                .radius(new QuadRadiusState(radius))
                .build()
                .render(matrix, x, y);
    }

    public int themedWhite(int alpha) {
        int base;
        if (isMinimalismLight()) {
            base = ColorUtils.rgb(24, 26, 32);
        } else {
            base = -1;
        }
        return ColorUtils.setAlpha(base, alpha);
    }

    public int themedWhite() {
        return themedWhite(255);
    }

    public int themedText(int alpha) {
        if (isMinimalismCustom()) {
            return ColorUtils.setAlpha(getMinimalismCustomText(), alpha);
        }
        return themedWhite(alpha);
    }

    public int themedText() {
        return themedText(255);
    }

    public int themedPanel(int r, int g, int b, int alpha) {
        if (isExcellentMode()) {
            int accent = Interface.INSTANCE.getMainColor();
            float requestedBrightness = (r + g + b) / (255.0f * 3.0f);
            float darkFactor = Math.max(0.28f, Math.min(0.42f, 0.25f + requestedBrightness));
            return excellentDarkColor(accent, darkFactor, alpha);
        }
        if (isMinimalismCustom()) {
            int bg = getMinimalismCustomBg();
            int cr = (bg >> 16) & 0xFF;
            int cg = (bg >> 8) & 0xFF;
            int cb = bg & 0xFF;
            int delta = (r + g + b) / 3 - 27;
            cr = Math.max(0, Math.min(255, cr + delta));
            cg = Math.max(0, Math.min(255, cg + delta));
            cb = Math.max(0, Math.min(255, cb + delta));
            return ColorUtils.setAlpha(ColorUtils.rgb(cr, cg, cb), alpha);
        }
        if (isMinimalismLight()) {
            int lr = Math.min(255, 255 - r + 200);
            int lg = Math.min(255, 255 - g + 200);
            int lb = Math.min(255, 255 - b + 200);
            return ColorUtils.setAlpha(ColorUtils.rgb(lr, lg, lb), alpha);
        }
        return ColorUtils.setAlpha(ColorUtils.rgb(r, g, b), alpha);
    }

    public int themedShadow(int alpha) {
        int base = isMinimalismLight() ? ColorUtils.rgb(0, 0, 0) : ColorUtils.rgb(0, 0, 0);
        return ColorUtils.setAlpha(base, alpha);
    }

    private boolean isMinimalismLight() {
        Interface module = Interface.INSTANCE;
        return module != null && module.isMinimalismLight();
    }

    private boolean isMinimalismCustom() {
        Interface module = Interface.INSTANCE;
        return module != null && module.isMinimalismCustom();
    }

    private int getMinimalismCustomBg() {
        Interface module = Interface.INSTANCE;
        return module != null ? module.getMinimalismCustomBg() : ColorUtils.rgb(20, 21, 26);
    }

    private int getMinimalismCustomText() {
        Interface module = Interface.INSTANCE;
        return module != null ? module.getMinimalismCustomText() : -1;
    }

    private void drawAccentBorder(Matrix4f matrix, float x, float y, float width, float height, float radius, float showProgress) {
        if (Interface.INSTANCE == null) return;
        int mainColor = Interface.INSTANCE.getMainColor();
        int borderAlpha = Math.max(0, Math.min(255, (int) (80 * clamp01(showProgress))));
        int accentColor = ColorUtils.setAlpha(mainColor, borderAlpha);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(1.0f, Math.max(0, height - radius * 2)))
                .color(new QuadColorState(accentColor, ColorUtils.setAlpha(mainColor, borderAlpha / 3), ColorUtils.setAlpha(mainColor, borderAlpha / 3), accentColor))
                .radius(new QuadRadiusState(0.5f))
                .build()
                .render(matrix, x + 0.5f, y + radius);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private boolean isBlurEnabled(float showProgress) {
        if (isLiquidGlassMode()) {
            return false;
        }
        float alpha = getBlurAlpha(showProgress);
        return alpha > 0f && getBlurRadius() > 0f;
    }

    private float getBlurRadius() {
        return Interface.INSTANCE != null && Interface.INSTANCE.blurStrength.getValue() != null
                ? Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue()))
                : 0f;
    }

    private float getRadius() {
        return 4f;
    }

    private int getMinimalismBackgroundAlpha(float showProgress) {
        int base = 150;
        int alpha = Math.round(Math.min(255f, Math.max(0f, base)) * clamp01(showProgress));
        return Math.max(0, Math.min(255, alpha));
    }

    private float getBlurAlpha(float showProgress) {
        return clamp01(showProgress);
    }

    private boolean isLiquidGlassMode() {
        Interface module = Interface.INSTANCE;
        return module != null && module.isLiquidGlassMode();
    }

    public boolean isExcellentMode() {
        Interface module = Interface.INSTANCE;
        return module != null && module.isExcellentMode();
    }

    private float getGlassOpacityFactor() {
        Interface module = Interface.INSTANCE;
        return module != null ? module.getGlassOpacityFactor() : 1.0f;
    }

    private float getGlassStrengthFactor() {
        Interface module = Interface.INSTANCE;
        return module != null ? module.getGlassStrengthFactor() : 0.5f;
    }

    private float getGlassDistortion() {
        Interface module = Interface.INSTANCE;
        return module != null ? module.getGlassDistortionValue() : 0.08f;
    }

    private float getGlassFresnelPower(float height) {
        float base = Math.abs(height - 240.0f) < 0.1f ? 100.0f : 50.0f;
        return base * getGlassStrengthFactor();
    }

    private float getGlassLayerAlpha(float showProgress) {
        return clamp01(showProgress) * clamp01(getGlassOpacityFactor());
    }

    private int getGlassBackgroundAlpha(float showProgress) {
        int base = 64;
        float alphaFactor = clamp01(showProgress) * clamp01(getGlassOpacityFactor());
        return Math.max(0, Math.min(255, Math.round(base * alphaFactor)));
    }

    public int getTotalCount(Item item) {
        if (mc == null || mc.player == null) return 0;
        var inv = mc.player.getInventory();
        int total = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && st.getItem() == item) {
                total += st.getCount();
            }
        }

        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty() && off.getItem() == item) total += off.getCount();

        return total;
    }

    public float smoothAnimation(float speed, float current, float target, long lastTimeMillis) {
        long now = System.currentTimeMillis();
        float dt = lastTimeMillis == 0 ? 0f : (now - lastTimeMillis) / 1000f;
        float t = 1f - (float) Math.exp(-speed * dt);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return current + (target - current) * t;
    }
}
