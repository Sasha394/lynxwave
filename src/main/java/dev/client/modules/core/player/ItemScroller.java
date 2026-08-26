package dev.client.modules.core.player;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;

public class ItemScroller extends Module {
    public static ItemScroller INSTANCE;

    public ItemScroller() {
        super("ItemScroller", ModuleCategory.Player, "Убирает задержку на перетаскивание предметов");
    }

    public Slider delay = new Slider("Задержка", () -> true).set(0,1000,10).defaultValue(50).register(this);
}
