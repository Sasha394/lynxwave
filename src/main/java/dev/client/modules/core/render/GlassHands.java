package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;

public class GlassHands extends Module {
    public static GlassHands INSTANCE;

    public final Slider opacity = new Slider("Прозрачность", () -> true)
            .set(0.1f, 1.0f, 0.05f).defaultValue(0.45f).register(this);

    public final CheckBox blur = new CheckBox("Размытие", () -> true)
            .defaultValue(true).register(this);

    public final Slider blurStrength = new Slider("Сила размытия", blur::getEnabled)
            .set(1f, 20f, 1f).defaultValue(8f).register(this);

    public final ModeElement shaderMode = new ModeElement("Режим", () -> true)
            .set("Обычный", "Шейдер").defaultValue("Обычный").register(this);

    public final ModeElement colorMode = new ModeElement("Режим цвета", () -> shaderMode.isSelected("Шейдер"))
            .set("Клиентский", "Свой").defaultValue("Клиентский").register(this);

    public final ColorPicker shaderColor = new ColorPicker("Цвет шейдера",
            () -> shaderMode.isSelected("Шейдер") && colorMode.isSelected("Свой"))
            .set(-1).defaultValue(-1).register(this);

    public final Slider shaderIntensity = new Slider("Интенсивность шейдера", () -> shaderMode.isSelected("Шейдер"))
            .set(0f, 2f, 0.05f).defaultValue(0.6f).register(this);

    public GlassHands() {
        super("GlassHands", ModuleCategory.Render, "Делает руку и предмет в первом лице прозрачными с размытием");
        INSTANCE = this;
    }

    public int getShaderColor() {
        if (colorMode.isSelected("Клиентский")) {
            return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
        }
        return shaderColor.getColorRGBA();
    }
}
