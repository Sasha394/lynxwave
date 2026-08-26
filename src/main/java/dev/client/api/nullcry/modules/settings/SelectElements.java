package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.*;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public class SelectElements extends Setting {
    List<CheckBox> values = new ArrayList<>();
    Set<String> defaultSelected = new HashSet<>();

    public SelectElements(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public SelectElements set(String... list) {
        Arrays.stream(list)
                .map(this::create)
                .forEach(values::add);

        for (CheckBox cb : values) {
            if (defaultSelected.contains(cb.getName())) {
                cb.applyDefault(true);
            }
        }

        return this;
    }

    public CheckBox create(String text) {
        return new CheckBox(text, () -> true);
    }

    public boolean empty() {
        return values.isEmpty();
    }

    public boolean isEmptySelected() {
        return values.stream().filter(CheckBox::getEnabled).toList().isEmpty();
    }

    public List<String> asStringList() {
        return values.stream().map(CheckBox::getName).toList();
    }

    public List<String> getSelected() {
        return values.stream().filter(CheckBox::getEnabled).map(CheckBox::getName).toList();
    }

    public Boolean isSelected(String text) {
        return values.stream()
                .filter(e -> e.getName().equalsIgnoreCase(text))
                .findFirst()
                .map(CheckBox::getEnabled)
                .orElse(false);
    }

    public CheckBox get(String text) {
        return values.stream()
                .filter(e -> e.getName().equalsIgnoreCase(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Не найден элемент \"" + text + "\" в SelectElements"));
    }

    public SelectElements applyDefault(String... names) {
        Collections.addAll(defaultSelected, names);

        if (!values.isEmpty()) {
            for (CheckBox cb : values) {
                if (defaultSelected.contains(cb.getName())) {
                    cb.applyDefault(true);
                }
            }
        }

        return this;
    }

    @Override
    public SelectElements collection(Collection collection) {
        collection.put(this);

        return this;
    }
}
