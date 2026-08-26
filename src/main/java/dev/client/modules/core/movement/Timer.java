package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;

public class Timer extends Module {

    public Timer() {
        super("Timer", ModuleCategory.Movement, "Увеличивает скорость игры");
    }

    Slider speed = new Slider("Скорость", () -> true).set(0.1f, 10f, 0.1f).defaultValue(2f).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        LynxWave.tickTimer = speed.getValue();
    }

    private void reset() {
        LynxWave.tickTimer = 1;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        reset();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        reset();
    }
}
