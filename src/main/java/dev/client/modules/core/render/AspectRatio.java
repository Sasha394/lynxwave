package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.world.AspectRatioEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;

public class AspectRatio extends Module {
    public static AspectRatio INSTANCE;

    public AspectRatio() {
        super("AspectRatio", ModuleCategory.Render, "Растягивает вашу игру");
    }

    public ModeElement mode = new ModeElement("Монитор", () -> true).set("21:9", "16:10", "16:9", "4:3", "Свое").defaultValue("16:10").register(this);
    public Slider width = new Slider("Ширина", () -> mode.isSelected("Свое")).set(0.6f, 2.5f, 0.1f).defaultValue(1.6f).register(this);

    @Subscribe
    public void onAspectRatio(AspectRatioEvent event) {
        if (mode.isSelected("21:9")) {
            event.setRatio(21.0f / 9.0f);
        } else if (mode.isSelected("16:9")) {
            event.setRatio(16.0f / 9.0f);
        } else if (mode.isSelected("16:10")) {
            event.setRatio(16.0f / 10.0f);
        } else if (mode.isSelected("4:3")) {
            event.setRatio(4.0f / 3.0f);
        } else if (mode.isSelected("Свое")) {
            event.setRatio(width.getValue());
        } else {
            event.setRatio((float) mc.getWindow().getFramebufferWidth() / (float) mc.getWindow().getFramebufferHeight());
        }

        event.cancelled();
    }
}

