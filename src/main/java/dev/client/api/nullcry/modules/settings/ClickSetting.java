package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClickSetting extends Setting {
    Supplier<String> nameSupplier = null;
    Runnable action = () -> {};
    long cooldownMs = 0;
    long lastClickAt = 0;

    public ClickSetting(String name, Supplier<Boolean> visible) {
        super(name, visible);
        getAnimation().setMs(250);
    }

    public ClickSetting(Supplier<String> nameSupplier, Supplier<Boolean> visible) {
        super("", visible);
        this.nameSupplier = nameSupplier;
        getAnimation().setMs(250);
    }

    public String getDisplayName() {
        return nameSupplier != null ? nameSupplier.get() : getName();
    }

    public ClickSetting onClick(Runnable action) {
        this.action = (action != null ? action : () -> {});
        return this;
    }

    public ClickSetting cooldown(long ms) {
        this.cooldownMs = Math.max(0, ms);
        return this;
    }

    public void tryClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickAt < cooldownMs) return;

        lastClickAt = now;
        if (action != null) action.run();
        getAnimation().reset();
    }

    @Override
    public ClickSetting collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
