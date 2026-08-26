package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.injection.accessor.ILivingEntityAccessor;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;

public class NoJumpDelay extends Module {

    public NoJumpDelay() {
        super("NoJumpDelay", ModuleCategory.Movement, "Отключает задержку между прыжками");
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (((ILivingEntityAccessor) mc.player).getLastJumpCooldown() > 0) {
            ((ILivingEntityAccessor) mc.player).setLastJumpCooldown(0);
        }
    }
}
