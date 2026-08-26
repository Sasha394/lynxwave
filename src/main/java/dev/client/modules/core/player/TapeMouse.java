package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.util.hit.HitResult;

public class TapeMouse extends Module {

    public TapeMouse() {
        super("TapeMouse", ModuleCategory.Player, "Автоматически производиит атаку с указанной задержкой");
    }

    CheckBox wallsRayTrace = new CheckBox("Проверка на видимость", () -> true).defaultValue(false).register(this);
    Slider delay = new Slider("Задержка", () -> true).set(100, 5000, 100).defaultValue(1000).register(this);

    private final TimerUtil timerUtil = new TimerUtil();

    @Override
    public void onEnabled() {
        super.onEnabled();
        timerUtil.reset();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        timerUtil.reset();
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (wallsRayTrace.getEnabled() && (mc.crosshairTarget == null || !mc.crosshairTarget.getType().equals(HitResult.Type.ENTITY))) {
            return;
        }

        if (timerUtil.isReached(delay.getValue().intValue())) {
            mc.doAttack();
            timerUtil.reset();
        }
    }
}