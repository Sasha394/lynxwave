package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.api.nullcry.uiClient.draggables.settings.PanelAlphaProvider;
import dev.client.modules.core.render.Interface;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public class PlayerInfo implements IHelper, SettingProvider, PanelAlphaProvider {
    final DraggableHandler draggableHandler;
    private final java.util.List<Setting> settings = new java.util.ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);

    @Override
    public DraggableHandler draggable() {
        return draggableHandler;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    public final SelectElements elements = new SelectElements("Элементы PlayerInfo", () -> true)
            .set("Coordination", "Tps", "Bps")
            .defaultValue("Coordination", "Tps", "Bps")
            .register(this);

    public PlayerInfo() {
        this.draggableHandler = addDraggable("PlayerInfo", 4, 25);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("PlayerInfo");
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    final float animSpeed = 20f;

    float displayedTps = -1;
    float displayedBps = -1;
    float displayedX = Float.NaN;
    float displayedY = Float.NaN;
    float displayedZ = Float.NaN;

    long lastCoordUpdateTime = 0;
    long lastTpsUpdateTime = 0;
    long lastBpsUpdateTime = 0;

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        final boolean showCoordination = elements.isSelected("Coordination");
        final boolean showTps = elements.isSelected("Tps");
        final boolean showBps = elements.isSelected("Bps");

        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        if (!showCoordination && !showTps && !showBps) return;

        final int screenW = mc.getWindow().getScaledWidth();
        final int screenH = mc.getWindow().getScaledHeight();

        final float spacing = 4f;
        final float iconSize = 9f;
        final float textSize = 8f;
        final float rowHeight = 18f;
        final float gap = 2f;

        String coordText = null, tpsText = null, bpsText = null;

        if (showCoordination) {
            Vec3d pos = dev.client.api.compat.EntityCompat.pos(mc.player);
            float xTarget = (float) pos.x;
            float yTarget = (float) pos.y;
            float zTarget = (float) pos.z;

            if (Float.isNaN(displayedX)) displayedX = xTarget;
            if (Float.isNaN(displayedY)) displayedY = yTarget;
            if (Float.isNaN(displayedZ)) displayedZ = zTarget;

            displayedX = HelperElements.smoothAnimation(animSpeed, displayedX, xTarget, lastCoordUpdateTime);
            displayedY = HelperElements.smoothAnimation(animSpeed, displayedY, yTarget, lastCoordUpdateTime);
            displayedZ = HelperElements.smoothAnimation(animSpeed, displayedZ, zTarget, lastCoordUpdateTime);
            lastCoordUpdateTime = System.currentTimeMillis();

            coordText = String.format(Locale.US, "X: %.0f Y: %.0f Z: %.0f", displayedX, displayedY, displayedZ);
        }

        if (showTps) {
            float tpsTarget = handlerClient().getServerUtils().getTPS();
            if (displayedTps < 0) displayedTps = tpsTarget;
            displayedTps = HelperElements.smoothAnimation(animSpeed, displayedTps, tpsTarget, lastTpsUpdateTime);
            lastTpsUpdateTime = System.currentTimeMillis();
            tpsText = String.format("TPS: %.1f", displayedTps);
        }

        if (showBps) {
            double dx = mc.player.getX() - mc.player.lastX;
            double dz = mc.player.getZ() - mc.player.lastZ;
            double speedTarget = Math.sqrt(dx * dx + dz * dz) * 20;
            if (displayedBps < 0) displayedBps = (float) speedTarget;
            displayedBps = HelperElements.smoothAnimation(animSpeed, displayedBps, (float) speedTarget, lastBpsUpdateTime);
            lastBpsUpdateTime = System.currentTimeMillis();
            bpsText = String.format("BPS: %.2f", displayedBps);
        }

        float wCoord = 0f, wTps = 0f, wBps = 0f;

        if (showCoordination) wCoord = measureEntryWidth("M", coordText, spacing, iconSize, textSize);
        if (showTps) wTps = measureEntryWidth("O", tpsText, spacing, iconSize, textSize);
        if (showBps) wBps = measureEntryWidth("L", bpsText, spacing, iconSize, textSize);

        float maxWidth = Math.max(wCoord, Math.max(wTps, wBps));
        int count = 0;
        if (showCoordination) count++;
        if (showTps) count++;
        if (showBps) count++;

        float totalHeight = count * rowHeight + Math.max(0, count - 1) * gap;

        boolean alignRight = x > (screenW / 2.0f);
        boolean alignBottom = y > (screenH / 2.0f);

        if (!alignRight && (x + maxWidth) > screenW) alignRight = true;
        if (alignRight && (x - maxWidth) < 0) alignRight = false;

        if (!alignBottom && (y + totalHeight) > screenH) alignBottom = true;
        if (alignBottom && (y - totalHeight) < 0) alignBottom = false;

        draggableHandler.setWidth(maxWidth);
        draggableHandler.setHeight(totalHeight);

        float yOffset = alignBottom ? (y - totalHeight) : y;

        boolean renderedSomething = false;
        if (!alignBottom) {
            if (showCoordination) {
                renderEntry(event.getContext(), "M", coordText, yOffset, spacing, iconSize, textSize, alignRight, wCoord, rowHeight);
                yOffset += rowHeight;
                renderedSomething = true;
            }
            if (showTps) {
                if (renderedSomething) yOffset += gap;
                renderEntry(event.getContext(), "O", tpsText, yOffset, spacing, iconSize, textSize, alignRight, wTps, rowHeight);
                yOffset += rowHeight;
                renderedSomething = true;
            }
            if (showBps) {
                if (renderedSomething) yOffset += gap;
                renderEntry(event.getContext(), "N", bpsText, yOffset, spacing, iconSize, textSize, alignRight, wBps, rowHeight);
            }
        } else {
            if (showBps) {
                renderEntry(event.getContext(), "N", bpsText, yOffset, spacing, iconSize, textSize, alignRight, wBps, rowHeight);
                yOffset += rowHeight;
                renderedSomething = true;
            }
            if (showTps) {
                if (renderedSomething) yOffset += gap;
                renderEntry(event.getContext(), "O", tpsText, yOffset, spacing, iconSize, textSize, alignRight, wTps, rowHeight);
                yOffset += rowHeight;
                renderedSomething = true;
            }
            if (showCoordination) {
                if (renderedSomething) yOffset += gap;
                renderEntry(event.getContext(), "M", coordText, yOffset, spacing, iconSize, textSize, alignRight, wCoord, rowHeight);
            }
        }
    }

    private float measureEntryWidth(String icon, String text, float spacing, float iconSize, float textSize) {
        float iconWidth = Builder.ICONS.get().getWidth(icon, iconSize);
        float textWidth = Builder.INTER.get().getWidth(text, textSize);
        return iconWidth + spacing + 2 + spacing + textWidth + 14f;
    }

    private void renderEntry(DrawContext context, String icon, String text, float yOffset, float spacing, float iconSize, float textSize, boolean alignRight, float rowWidth, float rowHeight) {
        float iconWidth = Builder.ICONS.get().getWidth(icon, iconSize);
        float textWidth = Builder.INTER.get().getWidth(text, textSize);

        float rectX = alignRight ? (draggableHandler.getX() - rowWidth) : draggableHandler.getX();
        HelperElements.rectElements(context, rectX, yOffset, rowWidth, rowHeight);

        int accentColor = Interface.INSTANCE.getMainColor();
        int iconColor = ColorUtils.setAlpha(accentColor, 200);
        int textColor = HelperElements.themedText(190);
        int sepColor = ColorUtils.setAlpha(accentColor, 120);
        float baseY = yOffset + rowHeight / 2f;

        if (!alignRight) {
            float baseX = rectX + 7;

            Builder.TEXT_BUILDER
                    .text(icon).size(iconSize).font(Builder.ICONS.get())
                    .color(iconColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), baseX, baseY - iconSize / 2f + 0.5f);

            baseX += iconWidth + spacing;

            float sepHeight = 10f;
            float sepX = Math.round(baseX) - 0.5f;
            float sepY = Math.round(baseY - sepHeight / 2f) + 0.5f;

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(1f, sepHeight))
                    .color(new QuadColorState(sepColor))
                    .radius(new QuadRadiusState(0f))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), sepX, sepY);

            baseX += spacing;

            Builder.TEXT_BUILDER
                    .text(text).size(textSize).font(Builder.INTER.get())
                    .color(textColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), baseX, baseY - textSize / 2f - 0.5f);

        } else {
            float rightX = rectX + rowWidth - 7;

            float iconX = rightX - iconWidth;
            Builder.TEXT_BUILDER
                    .text(icon).size(iconSize).font(Builder.ICONS.get())
                    .color(iconColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), iconX, baseY - iconSize / 2f + 0.5f);

            float sepHeight = 10f;
            float sepX = Math.round(iconX - spacing);
            float sepY = Math.round(baseY - sepHeight / 2f) + 0.5f;

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(1f, sepHeight))
                    .color(new QuadColorState(sepColor))
                    .radius(new QuadRadiusState(0f))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), sepX, sepY);

            float coordinationX = "M".equals(icon) ? 1.5f : 0f;
            float textX = sepX - 1f - spacing - textWidth - coordinationX;

            Builder.TEXT_BUILDER
                    .text(text).size(textSize).font(Builder.INTER.get())
                    .color(textColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(context), textX, baseY - textSize / 2f - 0.5f);
        }
    }

    @Override
    public java.util.List<Setting> getSettings() {
        return settings;
    }

    @Override
    public float getPanelAlpha() {
        return 1f;
    }
}
