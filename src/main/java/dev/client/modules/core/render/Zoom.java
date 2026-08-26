package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.Slider;

public class Zoom extends Module implements ClientApi {

    public static Zoom INSTANCE;

    private final KeyBind zoomKey = new KeyBind("Клавиша зума", () -> true).set(-1).register(this);
    private final Slider zoomLevel = new Slider("Уровень зума", () -> true).set(1f, 10f, 0.1f).applyDefault(4f).register(this);
    private final CheckBox smooth = new CheckBox("Плавный зум", () -> true).defaultValue(true).register(this);
    private final Slider smoothSpeed = new Slider("Скорость плавности", () -> !smooth.getEnabled()).set(100f, 1000f, 50f).applyDefault(300f).register(this);

    private boolean isZooming = false;
    private long zoomStartTime = 0L;

    public Zoom() {
        super("Zoom", ModuleCategory.Render, "Зум камеры по клавише");
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (zoomKey.getKey() == null || event.getKey() != zoomKey.getKey()) return;
        if (mc.currentScreen != null) return;
        isZooming = !isZooming;
        zoomStartTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onTick(TickEvent event) {
        updateZoom();
    }

    public void updateZoom() {
    }

    public float getZoomMultiplier() {
        if (!isEnabled()) return 1f;
        if (smooth.getEnabled()) {
            long duration = (long) smoothSpeed.getValue().floatValue();
            long elapsed = System.currentTimeMillis() - zoomStartTime;
            float t = Math.min(1f, (float) elapsed / duration);
            float progress = isZooming ? t : 1f - t;
            // Ease out (decelerate): 1 - (1-x)^2
            float eased = 1f - (1f - progress) * (1f - progress);
            return 1f + (zoomLevel.getValue() - 1f) * eased;
        }
        return isZooming ? zoomLevel.getValue() : 1f;
    }

    public boolean isZooming() {
        return isZooming;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        isZooming = false;
    }
}
