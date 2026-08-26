package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.directionSetting;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.modules.settings.DirectionSetting;
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

public class DirectionComponent extends SettingComponent {
    private static final float GRID_PADDING = 4f;
    private static final int GRID_DIVS = 8;

    private boolean dragging = false;
    private boolean hovered = false;

    public DirectionComponent(DirectionSetting setting) {
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
    public DirectionComponent render(DrawContext context, int mouseX, int mouseY, float delta) {
        DirectionSetting setting = (DirectionSetting) getSetting();
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

        int centerAlpha = Math.round(120 * visibility);
        int centerColor = ColorUtils.setAlpha(ColorUtils.rgb(80, 84, 92), centerAlpha);
        float centerX = gridX + 1f + gridSize / 2f;
        float centerY = gridY + 1f + gridSize / 2f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(1f, gridSize))
                .color(new QuadColorState(centerColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), centerX, gridY + 1f);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(gridSize, 1f))
                .color(new QuadColorState(centerColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), gridX + 1f, centerY);

        float crossX = gridX + 1f + (setting.getX() + 1f) * 0.5f * gridSize;
        float crossY = gridY + 1f + (1f - (setting.getY() + 1f) * 0.5f) * gridSize;
        int crossAlpha = Math.round(200 * visibility);
        int crossColor = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), crossAlpha);

        Builder.RECTANGLE_BUILDER
                .size(new SizeState(1f, gridSize))
                .color(new QuadColorState(crossColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), crossX, gridY + 1f);
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(gridSize, 1f))
                .color(new QuadColorState(crossColor))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), gridX + 1f, crossY);

        float dotSize = 4f;
        Builder.RECTANGLE_BUILDER
                .size(new SizeState(dotSize, dotSize))
                .radius(new QuadRadiusState(1f))
                .color(new QuadColorState(HelperElements.themedWhite(crossAlpha)))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), crossX - dotSize / 2f, crossY - dotSize / 2f);

        float hitX = gridX + 1f;
        float hitY = gridY + 1f;
        hovered = MouseClick.isClick(mouseX, mouseY, hitX, hitY, gridSize, gridSize);
        if (hovered || dragging) {
            CursorsUtil.setCursor(CursorsUtil.HAND);
        }

        if (dragging) {
            updateSetting(setting, mouseX, mouseY, hitX, hitY);
        }

        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float nameH = Builder.INTER.get().getHeight(getSetting().getName(), 7) + 6f;
        float gridX = getX() - 1f + 1f;
        float gridY = getY() + nameH + 1f;
        float gridSize = getWidth() - 2f;

        if (button == 0 && MouseClick.isClick(mouseX, mouseY, gridX, gridY, gridSize, gridSize)) {
            dragging = true;
            updateSetting((DirectionSetting) getSetting(), mouseX, mouseY, gridX, gridY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    private void updateSetting(DirectionSetting setting, double mouseX, double mouseY, float gridX, float gridY) {
        float gridSize = getWidth() - 2f;
        float relX = (float) ((mouseX - gridX) / gridSize);
        float relY = (float) ((mouseY - gridY) / gridSize);

        float x = MathHelper.clamp(relX * 2f - 1f, -1f, 1f);
        float y = MathHelper.clamp(1f - relY * 2f, -1f, 1f);

        setting.set(x, y);
    }
}
