package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;

public class ExtraTab extends Module {
    public static ExtraTab INSTANCE;

    public ExtraTab() {
        super("ExtraTab", ModuleCategory.Render, "Количество игроков в табе больше");
    }

    public final Slider limit = new Slider("Лимит игроков", () -> true)
            .set(20, 200, 5)
            .defaultValue(80)
            .register(this);
}
