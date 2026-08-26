package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import java.util.*;
import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModeElement extends Setting {
    List<String> values = new ArrayList<>();
    Map<String, Integer> valueColors = new HashMap<>();
    @NonFinal final Boolean opened = false;
    @NonFinal String value = null;
    @NonFinal String defaultValue = null;

    public ModeElement(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public ModeElement set(String... strings) {
        values.addAll(Arrays.asList(strings));

        if (defaultValue != null && values.contains(defaultValue)) {
            this.value = defaultValue;
        } else if (this.value == null && !values.isEmpty()) {
            this.value = values.getFirst();
        }
        return this;
    }

    public ModeElement set(String value) {
        if (value == null) return this;

        if (!values.contains(value)) {
            values.add(value);
        }
        this.value = value;
        return this;
    }

    public boolean isSelected(String target) {
        return this.value != null && this.value.equalsIgnoreCase(target);
    }

    public ModeElement withColor(String option, int color) {
        if (option == null) {
            return this;
        }
        valueColors.put(option, color);
        return this;
    }

    public Integer getColor(String option) {
        if (option == null) {
            return null;
        }
        Integer color = valueColors.get(option);
        if (color != null) {
            return color;
        }
        for (Map.Entry<String, Integer> entry : valueColors.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(option)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public ModeElement applyDefault(String defaultValue) {
        this.defaultValue = defaultValue;

        if (values.contains(defaultValue)) {
            this.value = defaultValue;
        } else if (this.value == null && !values.isEmpty()) {
            this.value = values.getFirst();
        }
        return this;
    }

    @Override
    public ModeElement collection(Collection collection) {
        collection.put(this);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof String) || value == null) return false;
        return value.equalsIgnoreCase((String) obj);
    }
}
