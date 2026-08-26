package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.world.FogEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

import java.time.LocalTime;

public class CustomWorld extends Module {
    public static CustomWorld INSTANCE;

    public CustomWorld() {
        super("CustomWorld", ModuleCategory.Render, "Позволяет изменять время суток и туман");
        PostEffectRenderer.init();
        INSTANCE = this;
    }

    public SelectElements mode = new SelectElements("Менять", () -> true).set("Время","Туман","Насыщенность").defaultValue("Время").register(this);
    ModeElement time = new ModeElement("Время суток",() -> mode.isSelected("Время")).set("Рассвет","Утро","День","Вечер","Заход солнца", "Ночь", "Реальное").defaultValue("День").register(this);

    Slider distance = new Slider("Дистанция туманна", () -> mode.isSelected("Туман")).set(10, 225, 5).defaultValue(80).register(this);

    ModeElement colorMode = new ModeElement("Режим цвета", () -> mode.isSelected("Туман")).set("Клиентский", "Свой").defaultValue("Клиентский").register(this);
    ColorPicker customColor = new ColorPicker("Цвет тумана", () -> colorMode.isSelected("Свой")).set(-1).defaultValue(-1).register(this);

    public Slider saturation = new Slider("Сила насыщенности", () -> mode.isSelected("Насыщенность")).set(0f, 100f, 0.05f).defaultValue(1f).register(this);

    long newTime;

    @Subscribe
    public void onFog(FogEvent e) {
        if (mode.isSelected("Туман")) {
            e.setDistance(255 - distance.getValue());
            e.setColor(colorMode.isSelected("Клиентский") ? Interface.INSTANCE.getMainColor() : customColor.getColorRGBA());
            e.setCancelled(true);
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket packet) {
            if (mc.world == null) return;

            event.setCancelled(true);

            switch (time.getValue()) {
                case "Рассвет":
                    newTime = 0L;
                    break;
                case "Утро":
                    newTime = 1000L;
                    break;
                case "День":
                    newTime = 6000L;
                    break;
                case "Вечер":
                    newTime = 12000L;
                    break;
                case "Заход солнца":
                    newTime = 13000L;
                    break;
                case "Ночь":
                    newTime = 18000L;
                    break;
                case "Реальное":
                    newTime = getRealWorldTime();
                    break;
                default:
                    return;
            }

            mc.world.setTime(newTime, newTime, true);
        }
    }

    private long getRealWorldTime() {
        LocalTime now = LocalTime.now();
        int hours = now.getHour();
        int minutes = now.getMinute();
        int adjustedHours = (hours + 18) % 24;
        long mcTime = (adjustedHours * 1000) + (minutes * (1000 / 60));
        return mcTime % 24000;
    }
}
