package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FullBright extends Module {
    public static FullBright INSTANCE;

    public FullBright() {
        super("Fullbright", ModuleCategory.Render, "Выкручивает яркость до максимума, не изменяя настройки игры");
    }

    public ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Гамма", "Ночное зрение")
            .defaultValue("Гамма")
            .register(this);

    public Slider value = new Slider("Значение яркости", () -> mode.isSelected("Гамма"))
            .set(1.5f, 5f, 0.1f)
            .defaultValue(3f)
            .register(this);

    private String lastMode = null;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        String curMode = mode.getValue();
        if (lastMode == null || !lastMode.equals(curMode)) {
            onModeChanged(lastMode, curMode);
            lastMode = curMode;
        }

        if (isNightVision() && mc.player != null) {
            var inst = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            boolean needReapply = false;
            if (inst == null) needReapply = true;
            else {
                needReapply = !inst.isInfinite()
                        || inst.getAmplifier() != 0
                        || inst.isAmbient() != true
                        || inst.shouldShowParticles() != false
                        || inst.shouldShowIcon() != false;
            }
            if (needReapply) {
                mc.player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.NIGHT_VISION,
                        StatusEffectInstance.INFINITE,
                        0,
                        true,
                        false,
                        true
                ));
            }
        }
    }

    private void onModeChanged(String oldMode, String newMode) {
        if ("Ночное зрение".equals(oldMode) && !"Ночное зрение".equals(newMode)) {
            removeNightVision();
        }
        if ("Ночное зрение".equals(newMode)) {
            applyNightVisionOnce();
        }
    }

    private void applyNightVisionOnce() {
        if (mc.player != null) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    StatusEffectInstance.INFINITE,
                    0, true, false, true
            ));
        }
    }

    private void removeNightVision() {
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    private void applyModeState() {
        if (isNightVision()) applyNightVisionOnce();
        else removeNightVision();
    }

    private boolean isNightVision() {
        return mode.isSelected("Ночное зрение");
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        lastMode = mode.getValue();
        applyModeState();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        removeNightVision();
    }
}
