package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;

public class SeeInvisible extends Module {
    public static SeeInvisible INSTANCE;

    public SeeInvisible() {
        super("SeeInvisible", ModuleCategory.Render, "Позволяет видеть невидимых существ, с указанной альфа-прозрачностью");
    }

    public final Slider transparency = new Slider("Прозрачность", () -> true).set(0.1f, 1.0f, 0.1f).defaultValue(1.0f).register(this);
}
