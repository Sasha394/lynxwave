package dev.client.modules.core.combat;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;

public class NoEntityTrace extends Module {
    public static NoEntityTrace INSTANCE;

    public NoEntityTrace() {
        super("NoEntityTrace", ModuleCategory.Combat, "Убирает взаимодействие с хитбоксами игроков");
    }
}
