package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.other.NoPushEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.SelectElements;

public class NoPush extends Module {

    public NoPush() {
        super("No Push", ModuleCategory.Movement, "Убирает отталкивание от выбранных настроек");
    }

    SelectElements collisions = new SelectElements("Отменять от", () -> true)
            .set("Игроков", "Блоков", "Жидкостей").defaultValue("Игроков")
            .register(this);

    @Subscribe
    public void onPush(NoPushEvent event) {
        if (event.getCollisionEnum() == NoPushEvent.CollisionEnum.Blocks && collisions.isSelected("Блоков")) {
            event.cancelled();
        }
        if (event.getCollisionEnum() == NoPushEvent.CollisionEnum.Players && collisions.isSelected("Игроков")) {
            event.cancelled();
        }
        if (event.getCollisionEnum() == NoPushEvent.CollisionEnum.Fluids && collisions.isSelected("Жидкостей")) {
            event.cancelled();
        }
    }
}
