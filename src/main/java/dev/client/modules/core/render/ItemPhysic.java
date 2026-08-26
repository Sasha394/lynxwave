package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;

/**
 * Падение и вращение дропа в мире (логика из Rich-Modern + миксин ItemEntityRenderer).
 */
public class ItemPhysic extends Module {

    public static ItemPhysic INSTANCE;

    /** «Физика» — Rich-стиль; «Ваниль» — стандартный рендер. */
    public final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Физика", "Ваниль")
            .applyDefault("Физика")
            .register(this);

    public ItemPhysic() {
        super("ItemPhysic", ModuleCategory.Render, "Физика предметов на земле");
    }

    public boolean physicEnabled() {
        return isEnabled() && mode.isSelected("Физика");
    }
}
