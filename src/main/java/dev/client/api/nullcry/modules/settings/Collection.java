package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Collection extends Setting {
    List<Setting> settings = new ArrayList<>();

    public Collection(String name, Supplier<Boolean> visible) {
        super(name, visible);
    }

    public Collection put(Setting setting) {
        settings.add(setting);
        return this;
    }

    @Override
    public Setting collection(Collection collection) {
        collection.put(this);

        return this;
    }

}
