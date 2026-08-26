package dev.client.api.nullcry.uiClient.clickGui.components.settings.core.collectionSetting;

import dev.client.api.nullcry.modules.settings.Collection;
import dev.client.api.nullcry.uiClient.clickGui.api.Helper;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;

import java.util.List;
import java.util.Objects;

public final class CollectionHelper {

    public static List<SettingComponent> childSettingComponents(Collection collection) {
        return collection.getSettings().stream()
                .map(Helper::find)
                .filter(Objects::nonNull)
                .toList();
    }

    public static float collectionHeight(List<SettingComponent> settingComponents) {
        return settingComponents.stream()
                .filter(e -> e.getSetting().getShown().get())
                .map(e -> e.getHeight() + 4f)
                .reduce(0f, Float::sum);
    }

}
