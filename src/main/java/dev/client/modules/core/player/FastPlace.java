package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;

public class FastPlace extends Module {

    public FastPlace() {
        super("FastPlace", ModuleCategory.Player, "Убирает задержку при установке блоков");
    }

    Slider delay = new Slider("Задержка", () -> true).set(0, 3, 1).defaultValue(0).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        mc.itemUseCooldown = delay.getValue().intValue();
    }
}