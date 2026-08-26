package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.render.core.animations.client.Direction;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckBox extends Setting {
    Boolean enabled = false;

    public CheckBox(String name, Supplier<Boolean> visible) {
        super(name, visible);
        this.getAnimation().setDirection(this.enabled ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    public CheckBox applyDefault(Boolean enabled) {
        this.enabled = enabled;
        this.getAnimation().setDirection(this.enabled ? Direction.FORWARDS : Direction.BACKWARDS);
        this.getAnimation().reset();

        return this;
    }

    @Override
    public CheckBox collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
