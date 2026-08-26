package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Input extends Setting {
    String value;
    final boolean onlyNumber;

    public Input(String name, Supplier<Boolean> shown) {
        super(name, shown);
        this.onlyNumber = false;
        this.value = "";
    }

    public Input(String name, Supplier<Boolean> shown, boolean onlyNumber) {
        super(name, shown);
        this.onlyNumber = onlyNumber;
        this.value = "";
    }

    public Input(String name, Supplier<Boolean> shown, String defaultValue, boolean onlyNumber) {
        super(name, shown);
        this.onlyNumber = onlyNumber;

        if (onlyNumber && (defaultValue == null || !defaultValue.matches("\\d*"))) {
            this.value = "";
        } else {
            this.value = defaultValue != null ? defaultValue : "";
        }
    }

    public Input applyDefault(String defaultValue) {
        if (onlyNumber && (defaultValue == null || !defaultValue.matches("\\d*"))) {
            this.value = "";
        } else {
            this.value = defaultValue != null ? defaultValue : "";
        }
        return this;
    }

    public Input set(String value) {
        if (onlyNumber && !value.matches("\\d*")) {
            return this;
        }
        this.value = value;
        return this;
    }

    @Override
    public Input collection(Collection collection) {
        collection.put(this);
        return this;
    }
}
