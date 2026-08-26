package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Slider extends Setting {
    @Setter Boolean dragging = false;
    public Float value, min, max, increments;

    public Slider(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public Slider set(float min, float max, float increments) {
        this.min = min;
        this.max = max;
        this.increments = increments;
        this.applyDefault(max / 2);

        return this;
    }

    public Slider applyDefault(float value) {
        float newValue = value;
        if (min != null && max != null) {
            newValue = Math.max(min, Math.min(max, newValue));
            if (increments != null && increments > 0f) {
                float offset = newValue - min;
                newValue = min + Math.round(offset / increments) * increments;
                newValue = Math.max(min, Math.min(max, newValue));
            }
        }
        this.value = newValue;
        return this;
    }

    public void setValue(float v) {
        if (min != null && max != null) {
            v = Math.max(min, Math.min(max, v));
        }
        this.value = v;
    }

    @Override
    public Slider collection(Collection collection) {
        collection.put(this);

        return this;
    }
}
