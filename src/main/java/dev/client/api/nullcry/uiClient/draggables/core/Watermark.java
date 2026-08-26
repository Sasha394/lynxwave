package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
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

import java.util.ArrayList;
import java.util.List;

public class Watermark implements IHelper, SettingProvider, PanelAlphaProvider {
    final DraggableHandler draggableHandler;
    private final List<Setting> settings = new ArrayList<>();

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

    public final SelectElements watermarkElements = new SelectElements("Элементы Watermark", () -> true)
            .set("Login", "Fps", "Ping", "Server")
            .defaultValue("Login", "Fps", "Ping")
            .register(this);

    public final CheckBox shortName = new CheckBox("Сокращенное название", () -> true).defaultValue(true).register(this);

    public Watermark() {
        this.draggableHandler = addDraggable("Watermark", 4, 4);
        this.draggableHandler.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("Watermark");
        });
        this.draggableHandler.setSettingsPanel(new DraggableSettingsPanel(draggableHandler, this));
    }

    float width;
    float height;

    final float ANIMATION_SPEED = 20;

    float displayedFps = -1;
    float displayedPing = -1;

    long lastFpsUpdateTime = 0;
    long lastPingUpdateTime = 0;
    private final List<Entry> renderEntries = new ArrayList<>(5);

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        final boolean showName = watermarkElements.isSelected("Login");
        final boolean showFps = watermarkElements.isSelected("Fps");
        final boolean showPing = watermarkElements.isSelected("Ping");
        final boolean showServer = watermarkElements.isSelected("Server");

        float x = draggableHandler.getX();
        float y = draggableHandler.getY();

        int fpsTarget = mc.getCurrentFps();
        int pingTarget;
        try {
            pingTarget = Integer.parseInt(PlayerHelper.getLocalPing());
        } catch (Exception ignored) {
            pingTarget = 0;
        }

        if (displayedFps < 0) displayedFps = fpsTarget;
        if (displayedPing < 0) displayedPing = pingTarget;

        displayedFps = HelperElements.smoothAnimation(ANIMATION_SPEED, displayedFps, fpsTarget, lastFpsUpdateTime);
        displayedPing = HelperElements.smoothAnimation(ANIMATION_SPEED, displayedPing, pingTarget, lastPingUpdateTime);

        long now = System.currentTimeMillis();
        lastFpsUpdateTime = now;
        lastPingUpdateTime = now;

        List<Entry> entries = renderEntries;
        entries.clear();
        entries.add(new Entry("A", ""));
        if (showName) entries.add(new Entry("B", LynxWave.getInstance().getUserProfile().getUser()));
        if (showFps) entries.add(new Entry("C", Math.round(displayedFps) + " fps"));
        if (showPing) entries.add(new Entry("D", Math.round(displayedPing) + " ping"));
        if (showServer) entries.add(new Entry("E", ConnectionHelper.getServerIP() + " server"));

        float spacing = 5f;
        float separatorWidth = 1f;
        float iconSize = 9f;
        float clientSize = 12.5f;
        float textSize = 8f;
        float paddingX = 6f;

        Entry clientEntry = entries.get(0);
        float clientIconWidth = Builder.ICONS.get().getWidth(clientEntry.icon, clientSize);
        float buildTextWidth = shortName.getEnabled() ? 0 : Builder.INTER.get().getWidth(handlerClient().getClientInfo().getType().name(), textSize);

        float clientRectWidth = shortName.getEnabled()
                ? clientIconWidth + paddingX * 1.2f
                : clientIconWidth + spacing + separatorWidth + spacing + buildTextWidth + paddingX * 1.2f;

        int accentColor = Interface.INSTANCE.getMainColor();

        if (entries.size() == 1) {
            height = 18;
            draggableHandler.setWidth(clientRectWidth);
            draggableHandler.setHeight(height);

            HelperElements.rectElements(event.getContext(), x, y, clientRectWidth, height);

            float iconBaseX = x + paddingX + 1;
            float iconBaseY = y + (height - clientSize) / 2f + 0.5f;

            Builder.TEXT_BUILDER
                    .text(clientEntry.icon)
                    .size(clientSize)
                    .font(Builder.ICONS.get())
                    .color(accentColor)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), iconBaseX, iconBaseY);

            if (!shortName.getEnabled()) {
                float separatorX = Math.round(x + paddingX + clientIconWidth - 1);
                float separatorY = Math.round(y + (height - 9f) / 2f) + 0.5f;

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(1f, 9f))
                        .color(new QuadColorState(Interface.INSTANCE.getMainColor()))
                        .radius(new QuadRadiusState(1f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), separatorX, separatorY);

                float buildTextX = separatorX + spacing + separatorWidth;
                float buildTextY = y + height / 2f - textSize / 2f - 0.5f;
                Builder.TEXT_BUILDER
                        .text(handlerClient().getClientInfo().getType().name())
                        .size(textSize)
                        .font(Builder.INTER.get())
                        .color(HelperElements.themedText())
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), buildTextX, buildTextY);
            }
            return;
        }

        float totalOtherWidth = 0;
        for (int i = 1; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float iconW = Builder.ICONS.get().getWidth(e.icon, iconSize);
            float textW = Builder.INTER.get().getWidth(e.text, textSize);
            totalOtherWidth += iconW + spacing + textW;
            if (i < entries.size() - 1) totalOtherWidth += separatorWidth + spacing * 2;
        }

        width = clientRectWidth + 7f + totalOtherWidth + paddingX;
        height = 18;
        draggableHandler.setWidth(width);
        draggableHandler.setHeight(height);

        HelperElements.rectElements(event.getContext(), x, y, clientRectWidth, height);

        float iconBaseX = x + paddingX + 1;
        float iconBaseY = y + (height - clientSize) / 2f + 0.5f;

        Builder.TEXT_BUILDER
                .text(clientEntry.icon)
                .size(clientSize)
                .font(Builder.ICONS.get())
                .color(accentColor)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), iconBaseX, iconBaseY);

        if (!shortName.getEnabled()) {
            float separatorX = Math.round(x + paddingX + clientIconWidth - 1);
            float separatorY = Math.round(y + (height - 9f) / 2f) + 0.5f;

            Builder.RECTANGLE_BUILDER
                    .size(new SizeState(1f, 9f))
                    .color(new QuadColorState(accentColor))
                    .radius(new QuadRadiusState(1f))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), separatorX, separatorY);

            float buildTextX = separatorX + spacing + separatorWidth;
            float buildTextY = y + height / 2f - textSize / 2f - 0.5f;
            Builder.TEXT_BUILDER
                    .text(handlerClient().getClientInfo().getType().name())
                    .size(textSize)
                    .font(Builder.INTER.get())
                    .color(HelperElements.themedText(200))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), buildTextX, buildTextY);
        }

        float othersRectX = x + clientRectWidth + 3f;
        float othersRectWidth = Math.max(0, width - clientRectWidth);
        HelperElements.rectElements(event.getContext(), othersRectX, y, othersRectWidth, height);

        float baseX = othersRectX + paddingX;
        float baseY = y + height / 2f;

        int dimmedWhite = HelperElements.themedText(180);

        for (int i = 1; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float iconW = Builder.ICONS.get().getWidth(e.icon, iconSize);
            float textW = Builder.INTER.get().getWidth(e.text, textSize);

            Builder.TEXT_BUILDER
                    .text(e.icon)
                    .size(iconSize)
                    .font(Builder.ICONS.get())
                    .color(ColorUtils.setAlpha(accentColor, 200))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()),
                            baseX, baseY - iconSize / 2f + 0.5f);

            baseX += iconW + spacing;

            Builder.TEXT_BUILDER
                    .text(e.text)
                    .size(textSize)
                    .font(Builder.INTER.get())
                    .color(dimmedWhite)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()),
                            baseX, baseY - textSize / 2f - 0.5f);

            baseX += textW;

            if (i < entries.size() - 1) {
                baseX += spacing;
                float separatorX = Math.round(baseX);
                float separatorY = Math.round(y + (height - 9f) / 2f) + 0.5f;

                Builder.RECTANGLE_BUILDER
                        .size(new SizeState(1f, 9f))
                        .color(new QuadColorState(ColorUtils.setAlpha(accentColor, 100)))
                        .radius(new QuadRadiusState(1f))
                        .build()
                        .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()), separatorX, separatorY);

                baseX += separatorWidth + spacing;
            }
        }
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    @Override
    public float getPanelAlpha() {
        return 1f;
    }

    private static final class Entry {
        private final String icon;
        private final String text;

        private Entry(String icon, String text) {
            this.icon = icon;
            this.text = text;
        }
    }
}
