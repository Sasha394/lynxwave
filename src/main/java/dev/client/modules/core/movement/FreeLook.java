package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.movement.freelook.FreeLookState;
import net.minecraft.client.option.Perspective;

public class FreeLook extends Module {

    private final KeyBind bind = new KeyBind("Кнопка", () -> true).set(-1).register(this);
    private Perspective previousPerspective;

    public FreeLook() {
        super("FreeLook", ModuleCategory.Movement, "Позволяет вращать камеру, не меняя направления движения");
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (event.getKey() != bind.getKey()) {
            return;
        }
        if (mc == null || mc.options == null) {
            return;
        }
        if (KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.getTarget() != null) {
            printClient("Нельзя использовать с Aura");
            return;
        }

        FreeLookState.active = !FreeLookState.active;

        if (FreeLookState.active) {
            previousPerspective = mc.options.getPerspective();
            if (previousPerspective != Perspective.THIRD_PERSON_FRONT) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
        } else {
            mc.options.setPerspective(previousPerspective != null ? previousPerspective : Perspective.FIRST_PERSON);
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        FreeLookState.active = false;
        if (mc != null && mc.options != null) {
            mc.options.setPerspective(previousPerspective != null ? previousPerspective : Perspective.FIRST_PERSON);
        }
        previousPerspective = null;
    }
}
