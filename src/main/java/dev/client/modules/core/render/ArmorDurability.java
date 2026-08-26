package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;

public class ArmorDurability extends Module {
    public static ArmorDurability INSTANCE;

    public ArmorDurability() {
        super("ArmorDurability", ModuleCategory.Render, "Заменяет цвет брони на цвет прочности");
    }
}
