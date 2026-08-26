package dev.client.api.nullcry.uiClient.clickGui.api;

import dev.client.LynxWave;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.ModuleComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.checkboxSetting.CheckBoxComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.clicksSetting.ClickComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.collectionSetting.CollectionComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.colorPickerSetting.ColorPickerComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.curveSetting.CurveComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.directionSetting.DirectionComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.inputSetting.InputComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.keybindSetting.KeyBindComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.modeElementSetting.ModeElementComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.selectElementsSetting.SelectElementsComponent;
import dev.client.api.nullcry.uiClient.clickGui.components.settings.core.sliderSetting.SliderComponent;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class Helper {

    public static List<ModuleComponent> moduleLayers(ModuleCategory moduleCategory, Predicate<Module> predicate) {
        return LynxWave.getInstance().getModuleManager().getModules().stream()
                .filter(e -> e.getModuleCategory().equals(moduleCategory))
                .filter(predicate)
                .map(ModuleComponent::new)
                .toList();
    }

    public static List<SettingComponent> settingComponents(Module module) {
        return module.getSettings().stream()
                .map(Helper::find)
                .filter(Objects::nonNull)
                .toList();
    }

    public static float moduleHeight(List<SettingComponent> settingComponents) {
        return 40f / 2 + settingComponents.stream()
                .filter(e -> e.getSetting().getShown().get())
                .map(e -> e.getHeight() + 5)
                .reduce(0f, Float::sum);
    }

    public static SettingComponent find(Setting setting) {
        if (setting instanceof CheckBox) return new CheckBoxComponent(setting);
        if (setting instanceof Collection collection) return new CollectionComponent(collection);
        if (setting instanceof Slider slider) return new SliderComponent(slider);
        if (setting instanceof KeyBind keyBind) return new KeyBindComponent(keyBind);
        if (setting instanceof ModeElement modeElement) return new ModeElementComponent(modeElement);
        if (setting instanceof SelectElements selectElements) return new SelectElementsComponent(selectElements);
        if (setting instanceof ColorPicker colorPicker) return new ColorPickerComponent(colorPicker);
        if (setting instanceof ClickSetting clickSetting) return new ClickComponent(clickSetting);
        if (setting instanceof Input input) return new InputComponent(input);
        if (setting instanceof DirectionSetting directionSetting) return new DirectionComponent(directionSetting);
        if (setting instanceof CurveSetting curveSetting) return new CurveComponent(curveSetting);
        return null;
    }

}
