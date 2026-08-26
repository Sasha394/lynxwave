package dev.client.api.nullcry.uiClient.notification;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Interface;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class NotificationDragging implements IHelper, ClientApi, SettingProvider {
    public static final NotificationDragging INSTANCE = new NotificationDragging();
    private final DraggableHandler drag = addDraggable("Notifications", 400, 282);
    @Getter private float width, height;
    private final List<Setting> settings = new ArrayList<>();

    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);

    @Override
    public DraggableHandler draggable() {
        return drag;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    public final SelectElements elementsNotifications = new SelectElements("Присылать если", () -> true)
            .set("Сломан щит", "Пиар варпа", "Просят спек", "Забанен игрок", "Зашел стафф", "Мало прочности брони", "Заканчивается эффект")
            .defaultValue("Сломан щит", "Забанен игрок", "Зашел стафф", "Мало прочности брони", "Заканчивается эффект")
            .register(this);
    public final Slider volume = new Slider("Громкость", () -> true)
            .set(1.0f, 100.0f, 1)
            .defaultValue(10.0f)
            .register(this);
    public final CheckBox notification = new CheckBox("Уведомлять о бане", () -> elementsNotifications.isSelected("Забанен игрок")).register(this);
    public final Slider spacing = new Slider("Расстояние между уведомлениями", () -> true)
            .set(2.0f, 6.0f, 0.5f)
            .defaultValue(2.0f)
            .register(this);

    private NotificationDragging() {
        drag.setWidth(220f);
        drag.setHeight(60f);
        this.width = 220f;
        this.height = 60f;
        drag.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("Notifications");
        });
        drag.setSettingsPanel(new DraggableSettingsPanel(drag, this));
    }

    @Override
    public void onRender(RenderEvent.Draw2D event) {
        var win = mc.getWindow();

        int mouseX = (int) (mc.mouse.getX() * win.getScaledWidth() / (double) win.getWidth());
        int mouseY = (int) (mc.mouse.getY() * win.getScaledHeight() / (double) win.getHeight());
        drag.drawElement(
                event.getContext(),
                dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext()),
                mouseX, mouseY,
                win
        );
    }

    public void updateSize(float width, float height) {
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);
        drag.setWidth(this.width);
        drag.setHeight(this.height);
    }

    public float getAnchorX() {return drag.getX();}
    public float getAnchorY() {return drag.getY();}
    public boolean isDragging() {return drag.isDragging();}

    public SelectElements getElementsNotifications() {
        return elementsNotifications;
    }

    public Slider getVolumeSetting() {
        return volume;
    }

    public CheckBox getNotificationToggle() {
        return notification;
    }

    public Slider getSpacingSetting() {
        return spacing;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }
}
