package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.curveSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.CurveSetting;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.CursorsUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class CurveComponent extends SettingComponent {
    private static final float GRID_PADDING = 4f;
    private static final int GRID_DIVS = 8;
    private static final float POINT_RADIUS = 4f;
    private static final float CURVE_POINT_SIZE = 2f;
    private static final float HANDLE_LINE_SIZE = 1.5f;
    private static final int CURVE_SAMPLES = 80;

    private boolean dragging = false;
    private int draggingPoint = -1;

    public CurveComponent(CurveSetting setting) {
        super(setting);
    }

    @Override
    public void init() {
        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 6f;
        float width = 240f / 2 - 10;
        float gridSize = width - 2f;
        size(width, nameH + gridSize + GRID_PADDING + 2f);
    }

    @Override
    public CurveComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        CurveSetting setting = (CurveSetting) getSetting();
        float visibility = getGlobalAlpha();

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 6f;
        float gridSize = getWidth() - 2f;
        size(getWidth(), nameH + gridSize + GRID_PADDING + 2f);

        Builder.TEXT_BUILDER
                .size(7)
                .color(HelperElements.themedText(Math.round(ColorUtils.getAlpha(-1) * visibility)))
                .text(getSetting().getName())
                .font(Builder.INTER.get())
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), getX(), getY() - 1);

        float gridX = getX() - 1f;
        float gridY = getY() + nameH;

        if (!Interface.INSTANCE.isExcellentMode() && Interface.INSTANCE.blurStrength.getValue() > 0) {
            Builder.BLUR_BUILDER
                    .blurRadius(Math.min(10f, Math.max(0f, Interface.INSTANCE.blurStrength.getValue())))
                    .radius(new QuadRadiusState(3f))
                    .size(new SizeState(gridSize + 2f, gridSize + 2f))
                    .alpha(visibility)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), gridX, gridY);
        }

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(gridSize + 2f, gridSize + 2f))
                .color(new QuadColorState(
                        HelperElements.themedPanel(28, 30, 35, (int) (150 * visibility)),
                        HelperElements.themedPanel(24, 26, 30, (int) (150 * visibility)),
                        HelperElements.themedPanel(22, 24, 28, (int) (150 * visibility)),
                        HelperElements.themedPanel(26, 28, 33, (int) (150 * visibility))
                ))
                .radius(new QuadRadiusState(3f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), gridX, gridY);

        float cell = gridSize / GRID_DIVS;
        int gridAlpha = Math.round(60 * visibility);
        int gridColor = ColorUtils.setAlpha(ColorUtils.rgb(55, 58, 64), gridAlpha);
        for (int i = 1; i < GRID_DIVS; i++) {
            float lx = gridX + 1f + i * cell;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(1f, gridSize))
                    .color(new QuadColorState(gridColor))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), lx, gridY + 1f);

            float ly = gridY + 1f + i * cell;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(gridSize, 1f))
                    .color(new QuadColorState(gridColor))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), gridX + 1f, ly);
        }

        float p1x = gridX + 1f + setting.getX1() * gridSize;
        float p1y = gridY + 1f + (1f - setting.getY1()) * gridSize;
        float p2x = gridX + 1f + setting.getX2() * gridSize;
        float p2y = gridY + 1f + (1f - setting.getY2()) * gridSize;

        float startX = gridX + 1f;
        float startY = gridY + 1f + gridSize;
        float endX = gridX + 1f + gridSize;
        float endY = gridY + 1f;

        int handleAlpha = Math.round(160 * visibility);
        int handleColor = ColorUtils.setAlpha(ColorUtils.rgb(110, 115, 125), handleAlpha);
        drawLine(context, startX, startY, p1x, p1y, HANDLE_LINE_SIZE, handleColor);
        drawLine(context, p2x, p2y, endX, endY, HANDLE_LINE_SIZE, handleColor);
        drawLine(context, p1x, p1y, p2x, p2y, HANDLE_LINE_SIZE, handleColor);

        int curveAlpha = Math.round(220 * visibility);
        int curveColor = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), curveAlpha);
        for (int i = 0; i < CURVE_SAMPLES; i++) {
            float t = i / (float) (CURVE_SAMPLES - 1);
            float cx = setting.curveX(t);
            float cy = setting.curveY(t);
            float px = gridX + 1f + cx * gridSize;
            float py = gridY + 1f + (1f - cy) * gridSize;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(CURVE_POINT_SIZE, CURVE_POINT_SIZE))
                    .color(new QuadColorState(curveColor))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), px - CURVE_POINT_SIZE / 2f, py - CURVE_POINT_SIZE / 2f);
        }

        int pointAlpha = Math.round(220 * visibility);
        int pointColor = HelperElements.themedWhite(pointAlpha);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(POINT_RADIUS * 2f, POINT_RADIUS * 2f))
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(pointColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), p1x - POINT_RADIUS, p1y - POINT_RADIUS);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(POINT_RADIUS * 2f, POINT_RADIUS * 2f))
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(pointColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), p2x - POINT_RADIUS, p2y - POINT_RADIUS);

        boolean hoverP1 = MouseClick.isClick(mouseX, mouseY, p1x - POINT_RADIUS, p1y - POINT_RADIUS, POINT_RADIUS * 2f, POINT_RADIUS * 2f);
        boolean hoverP2 = MouseClick.isClick(mouseX, mouseY, p2x - POINT_RADIUS, p2y - POINT_RADIUS, POINT_RADIUS * 2f, POINT_RADIUS * 2f);
        boolean hoverGrid = MouseClick.isClick(mouseX, mouseY, gridX + 1f, gridY + 1f, gridSize, gridSize);

        if (hoverP1 || hoverP2 || dragging) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        if (dragging) {
            updateSetting(setting, mouseX, mouseY, gridX + 1f, gridY + 1f, gridSize);
        }

        if (!hoverGrid && !hoverP1 && !hoverP2 && !dragging) {
            draggingPoint = -1;
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 6f;
        float gridX = getX() - 1f + 1f;
        float gridY = getY() + nameH + 1f;
        float gridSize = getWidth() - 2f;

        CurveSetting setting = (CurveSetting) getSetting();
        float p1x = gridX + setting.getX1() * gridSize;
        float p1y = gridY + (1f - setting.getY1()) * gridSize;
        float p2x = gridX + setting.getX2() * gridSize;
        float p2y = gridY + (1f - setting.getY2()) * gridSize;

        boolean hitP1 = MouseClick.isClick(mouseX, mouseY, p1x - POINT_RADIUS, p1y - POINT_RADIUS, POINT_RADIUS * 2f, POINT_RADIUS * 2f);
        boolean hitP2 = MouseClick.isClick(mouseX, mouseY, p2x - POINT_RADIUS, p2y - POINT_RADIUS, POINT_RADIUS * 2f, POINT_RADIUS * 2f);

        if (hitP1 || hitP2) {
            dragging = true;
            draggingPoint = hitP1 ? 0 : 1;
            updateSetting(setting, mouseX, mouseY, gridX, gridY, gridSize);
            return true;
        }

        if (MouseClick.isClick(mouseX, mouseY, gridX, gridY, gridSize, gridSize)) {
            float distP1 = distance(mouseX, mouseY, p1x, p1y);
            float distP2 = distance(mouseX, mouseY, p2x, p2y);
            draggingPoint = distP1 <= distP2 ? 0 : 1;
            dragging = true;
            updateSetting(setting, mouseX, mouseY, gridX, gridY, gridSize);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    private void updateSetting(CurveSetting setting, double mouseX, double mouseY, float gridX, float gridY, float gridSize) {
        if (draggingPoint < 0) return;

        float relX = (float) ((mouseX - gridX) / gridSize);
        float relY = (float) ((mouseY - gridY) / gridSize);

        float x = MathHelper.clamp(relX, 0f, 1f);
        float y = MathHelper.clamp(1f - relY, 0f, 1f);

        if (draggingPoint == 0) {
            setting.setControl1(x, y);
        } else {
            setting.setControl2(x, y);
        }
    }

    private static float distance(double x1, double y1, float x2, float y2) {
        float dx = (float) (x1 - x2);
        float dy = (float) (y1 - y2);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void drawLine(DrawContext context, float x1, float y1, float x2, float y2, float size, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) (len / 1.5f));
        float half = size / 2f;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x1 + dx * t;
            float py = y1 + dy * t;
            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(size, size))
                    .color(new QuadColorState(color))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), px - half, py - half);
        }
    }
}
