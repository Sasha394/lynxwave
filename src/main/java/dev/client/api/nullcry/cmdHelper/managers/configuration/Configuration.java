package dev.client.api.nullcry.cmdHelper.managers.configuration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.cmdHelper.managers.dragHandler.DraggableManager;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.api.nullcry.uiClient.altManager.MicrosoftAuthConfig;
import dev.client.api.nullcry.uiClient.title.TitleScreenSettings;
import lombok.Getter;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

@Getter
public class Configuration implements ClientApi {
    /** Имена HUD-элементов, позиции которых сохраняются в конфиг при .config save */
    private static final Set<String> DRAGGABLES_IN_CONFIG = Set.of(
            "ArmorHud", "Cooldowns", "InventoryHud", "Keybinds", "Party List", "PlayerInfo",
            "Potions", "Scoreboard", "Staff List", "Target Hud", "Watermark",
            "MusicPlayer", "Notifications"
    );
    private final File file;
    private final String name;

    public Configuration(String name, File file) {
        this.name = name;
        this.file = file;
    }

    public void loadConfig(JsonObject jsonObject) {
        if (jsonObject == null) {
            return;
        }

        if (jsonObject.has("modules")) {
            loadModulesSettings(jsonObject.getAsJsonObject("modules"));
        }
        if (jsonObject.has("titleBackground")) {
            JsonElement el = jsonObject.get("titleBackground");
            if (el != null && !el.isJsonNull() && el.isJsonPrimitive()) {
                TitleScreenSettings.setBackgroundPath(el.getAsString());
            }
        }
        if (jsonObject.has("microsoftClientId")) {
            JsonElement el = jsonObject.get("microsoftClientId");
            if (el != null && !el.isJsonNull() && el.isJsonPrimitive()) {
                MicrosoftAuthConfig.setClientId(el.getAsString());
            }
        }
        if (jsonObject.has("draggables")) {
            loadDraggables(jsonObject.getAsJsonObject("draggables"));
        }
    }

    private void loadModulesSettings(JsonObject modulesObject) {
        LynxWave.getInstance().getModuleManager().getModules().forEach(f -> {
            JsonObject moduleObject = modulesObject.getAsJsonObject(f.getName().toLowerCase());
            if (moduleObject == null) {
                return;
            }

            f.setEnabled(false);
            loadSettingFromJson(moduleObject, "bind", value -> f.setKey(value.getAsInt()));
            loadSettingFromJson(moduleObject, "enable", value -> f.setEnabled(value.getAsBoolean()));

            f.getSettings().forEach(setting -> loadIndividualSetting(moduleObject, setting));
        });
    }

    private void loadIndividualSetting(JsonObject moduleObject, Setting setting) {
        JsonElement settingElement = moduleObject.get(setting.getName());
        if (settingElement == null || settingElement.isJsonNull()) {
            return;
        }

        try {
            switch (setting) {
                case Slider slider -> slider.applyDefault(settingElement.getAsFloat());
                case CheckBox checkBox -> checkBox.applyDefault(settingElement.getAsBoolean());
                case ColorPicker colorPicker -> colorPicker.set(settingElement.getAsInt());
                case ModeElement modeElement -> modeElement.set(settingElement.getAsString());
                case KeyBind keyBind -> keyBind.set(settingElement.getAsInt());
                case SelectElements selectElements -> loadSelectElements(selectElements, settingElement.getAsJsonObject());
                case Input input -> input.set(settingElement.getAsString());
                case DirectionSetting direction -> loadDirectionSetting(direction, settingElement);
                case CurveSetting curve -> loadCurveSetting(curve, settingElement);
                case Collection collection -> {
                    JsonObject nested = settingElement.getAsJsonObject();
                    collection.getSettings().forEach(subSetting -> loadIndividualSetting(nested, subSetting));
                }
                default -> {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSelectElements(SelectElements setting, JsonObject elements) {
        setting.getValues().forEach(option -> {
            JsonElement optionElement = elements.get(option.getName());
            if (optionElement != null && !optionElement.isJsonNull()) {
                option.applyDefault(optionElement.getAsBoolean());
            }
        });
    }

    private void loadDirectionSetting(DirectionSetting setting, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            float x = obj.has("x") ? obj.get("x").getAsFloat() : 0f;
            float y = obj.has("y") ? obj.get("y").getAsFloat() : 0f;
            setting.applyDefault(x, y);
        }
    }

    private void loadCurveSetting(CurveSetting setting, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            float x1 = obj.has("x1") ? obj.get("x1").getAsFloat() : setting.getX1();
            float y1 = obj.has("y1") ? obj.get("y1").getAsFloat() : setting.getY1();
            float x2 = obj.has("x2") ? obj.get("x2").getAsFloat() : setting.getX2();
            float y2 = obj.has("y2") ? obj.get("y2").getAsFloat() : setting.getY2();
            setting.applyDefault(x1, y1, x2, y2);
        }
    }

    private void loadSettingFromJson(JsonObject jsonObject, String key, Consumer<JsonElement> consumer) {
        JsonElement element = jsonObject.get(key);
        if (element != null && !element.isJsonNull()) {
            consumer.accept(element);
        }
    }

    private void loadDraggables(JsonObject draggablesObject) {
        if (draggablesObject == null) return;
        for (String name : DRAGGABLES_IN_CONFIG) {
            if (!draggablesObject.has(name)) continue;
            JsonElement el = draggablesObject.get(name);
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            DraggableHandler handler = DraggableManager.draggable.get(name);
            if (handler == null) continue;
            if (obj.has("x") && obj.get("x").isJsonPrimitive()) {
                handler.setX(obj.get("x").getAsFloat());
            }
            if (obj.has("y") && obj.get("y").isJsonPrimitive()) {
                handler.setY(obj.get("y").getAsFloat());
            }
            loadDraggableSettings(handler, obj);
        }
    }

    private void loadDraggableSettings(DraggableHandler handler, JsonObject obj) {
        if (!obj.has("settings") || !obj.get("settings").isJsonObject()) return;
        SettingProvider provider = draggableProvider(handler);
        if (provider == null) return;
        JsonObject settingsObject = obj.getAsJsonObject("settings");
        provider.getSettings().forEach(setting -> loadIndividualSetting(settingsObject, setting));
    }

    private SettingProvider draggableProvider(DraggableHandler handler) {
        DraggableSettingsPanel panel = handler.getSettingsPanel();
        return panel == null ? null : panel.getProvider();
    }

    public JsonElement saveConfig() {
        JsonObject modulesObject = new JsonObject();

        saveModuleSettings(modulesObject);
        JsonObject newObject = new JsonObject();
        newObject.add("modules", modulesObject);
        newObject.addProperty("titleBackground", TitleScreenSettings.getBackgroundPath());
        newObject.addProperty("microsoftClientId", MicrosoftAuthConfig.getClientId());
        newObject.add("draggables", saveDraggables());

        return newObject;
    }

    private JsonObject saveDraggables() {
        JsonObject out = new JsonObject();
        for (String name : DRAGGABLES_IN_CONFIG) {
            DraggableHandler handler = DraggableManager.draggable.get(name);
            if (handler == null) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("x", handler.getX());
            obj.addProperty("y", handler.getY());

            SettingProvider provider = draggableProvider(handler);
            if (provider != null) {
                JsonObject settingsObject = new JsonObject();
                provider.getSettings().forEach(setting -> saveIndividualSetting(settingsObject, setting));
                obj.add("settings", settingsObject);
            }

            out.add(name, obj);
        }
        return out;
    }

    private void saveModuleSettings(JsonObject modulesObject) {
        LynxWave.getInstance().getModuleManager().getModules().forEach(module -> {
            JsonObject moduleObject = new JsonObject();

            moduleObject.addProperty("bind", module.getKey());
            moduleObject.addProperty("enable", module.isEnabled());

            module.getSettings().forEach(setting -> saveIndividualSetting(moduleObject, setting));
            modulesObject.add(module.getName().toLowerCase(), moduleObject);
        });
    }

    private void saveIndividualSetting(JsonObject moduleObject, Setting setting) {
        if (setting instanceof CheckBox) {
            moduleObject.addProperty(setting.getName(), ((CheckBox) setting).getEnabled());
        } else if (setting instanceof Slider) {
            moduleObject.addProperty(setting.getName(), ((Slider) setting).getValue());
        } else if (setting instanceof ModeElement) {
            moduleObject.addProperty(setting.getName(), ((ModeElement) setting).getValue());
        } else if (setting instanceof ColorPicker) {
            int argb = ((ColorPicker) setting).getColorRGBA();
            moduleObject.addProperty(setting.getName(), argb);
        } else if (setting instanceof KeyBind) {
            moduleObject.addProperty(setting.getName(), ((KeyBind) setting).getKey());
        } else if (setting instanceof SelectElements) {
            saveMultiSetting(moduleObject, (SelectElements) setting);
        } else if (setting instanceof Input) {
            moduleObject.addProperty(setting.getName(), ((Input) setting).getValue());
        } else if (setting instanceof DirectionSetting) {
            DirectionSetting direction = (DirectionSetting) setting;
            JsonObject obj = new JsonObject();
            obj.addProperty("x", direction.getX());
            obj.addProperty("y", direction.getY());
            moduleObject.add(setting.getName(), obj);
        } else if (setting instanceof CurveSetting) {
            CurveSetting curve = (CurveSetting) setting;
            JsonObject obj = new JsonObject();
            obj.addProperty("x1", curve.getX1());
            obj.addProperty("y1", curve.getY1());
            obj.addProperty("x2", curve.getX2());
            obj.addProperty("y2", curve.getY2());
            moduleObject.add(setting.getName(), obj);
        } else if (setting instanceof Collection) {
            JsonObject nestedSettings = new JsonObject();
            ((Collection) setting).getSettings()
                    .forEach(subSetting -> saveIndividualSetting(nestedSettings, subSetting));
            moduleObject.add(setting.getName(), nestedSettings);
        }
    }

    private void saveMultiSetting(JsonObject moduleObject, SelectElements setting) {
        JsonObject elements = new JsonObject();
        setting.getValues().forEach(option -> elements.addProperty(option.getName(), option.getEnabled()));
        moduleObject.add(setting.getName(), elements);
    }
}
