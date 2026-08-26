package dev.client.api.nullcry.cmdHelper.managers.configuration;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.modules.core.misc.IRC;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationManager {
    public final File Configuration_DIR = new File(LynxWave.getInstance().getClientDir(), "config");
    public final File Custom_DIR = new File(Configuration_DIR, "custom");
    public final File Last_Selected_Configuration = new File(LynxWave.getInstance().getClientDir(), "last_config.file");
    @Getter
    public String currentConfigurationName = "default";

    public void init() throws IOException {
        setupFolder();
        loadLastConfig();
        registerShutdownHook();
    }

    public void setupFolder() {
        if (!Configuration_DIR.exists()) {
            Configuration_DIR.mkdirs();
            Console.logManager("Configuration -> Создана папка для конфигов: " + Configuration_DIR.getAbsolutePath());
            applyFirstLaunchDefaults();
            createDefaultConfig();
        }

        if (!Custom_DIR.exists()) {
            Custom_DIR.mkdirs();
            Console.logManager("Configuration -> Создана папка для кастомных конфигов: " + Custom_DIR.getAbsolutePath());
        }

        if (!Last_Selected_Configuration.exists()) {
            try {
                Last_Selected_Configuration.createNewFile();
                Console.logManager("Configuration -> Файл для последнего конфига создан: " + Last_Selected_Configuration.getAbsolutePath());
                saveLastConfig("default");
            } catch (IOException e) {
                Console.logManager("Configuration -> Не удалось создать файл для последнего конфига", e);
            }
        } else {
            validateLastConfig();
        }
    }

    private void validateLastConfig() {
        try {
            String lastConfigRaw = new String(java.nio.file.Files.readAllBytes(Last_Selected_Configuration.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String decrypted = LynxWave.getInstance().cryptEnabled() ? decrypt(lastConfigRaw) : lastConfigRaw;

            JsonObject json;
            try {
                json = JsonParser.parseString(decrypted).getAsJsonObject();
            } catch (Exception e) {
                Console.logManager("Configuration -> Некорректный формат last_config, устанавливается default", e);
                saveLastConfig("default");
                return;
            }

            String configName = json.has("config") ? json.get("config").getAsString() : "default";

            if (configName == null || configName.isEmpty() || findConfig(configName) == null) {
                Console.logManager("Configuration -> Последний конфиг отсутствует или некорректен. Устанавливается default.");
                saveLastConfig("default");
            }
        } catch (IOException e) {
            Console.logManager("Configuration -> Ошибка проверки последнего конфига", e);
        }
    }

    public void loadLastConfig() {
        try {
            String raw = new String(java.nio.file.Files.readAllBytes(Last_Selected_Configuration.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String decrypted = LynxWave.getInstance().cryptEnabled() ? decrypt(raw) : raw;

            JsonObject json;
            try {
                json = JsonParser.parseString(decrypted).getAsJsonObject();
            } catch (Exception e) {
                Console.logManager("Configuration -> Некорректный формат last_config, устанавливается default.", e);
                saveLastConfig("default");
                loadConfiguration("default");
                currentConfigurationName = "default";
                return;
            }

            String configName = json.has("config") ? json.get("config").getAsString() : "default";

            if (loadConfiguration(configName)) {
                currentConfigurationName = configName;
            } else {
                saveLastConfig("default");
                File def = new File(Configuration_DIR, "default.file");
                if (!def.exists()) createDefaultConfig();
                saveConfiguration("default", true);
                loadConfiguration("default");
                currentConfigurationName = "default";
            }

        } catch (IOException e) {
            saveLastConfig("default");
            File def = new File(Configuration_DIR, "default.file");
            if (!def.exists()) createDefaultConfig();
            saveConfiguration("default", true);
            loadConfiguration("default");
            currentConfigurationName = "default";
        }
    }

    public void saveLastConfig(String configName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Last_Selected_Configuration))) {
            JsonObject json = new JsonObject();
            json.addProperty("config", configName);

            String content = LynxWave.getInstance().cryptEnabled() ? encrypt(json.toString()) : json.toString();
            writer.write(content);
            Console.logManager("Configuration -> Последний выбранный конфиг сохранен: " + configName);
        } catch (IOException e) {
            Console.logManager("Configuration -> Не удалось сохранить последний выбранный конфиг", e);
        }
    }

    public String removeConfiguration(String configName) {
        if (configName == null || configName.trim().isEmpty()) {
            Console.logManager("Configuration -> Имя конфигурации не указано, обновление менеджера пропущено.");
            return null;
        }

        if (isDefaultName(configName)) {
            Console.logManager("Configuration -> Системный конфиг '" + configName + "' не может быть удалён из менеджера.");
            return null;
        }

        if (configName.equalsIgnoreCase(currentConfigurationName)) {
            Console.logManager("Configuration -> Удалён активный конфиг '" + configName + "'. Загружается конфигурация по умолчанию.");
            return ensureFallbackConfiguration();
        }

        Console.logManager("Configuration -> Конфиг '" + configName + "' удалён (не был активен).");
        return null;
    }

    private String ensureFallbackConfiguration() {
        String fallbackName = "default";
        File fallbackFile = new File(Configuration_DIR, fallbackName + ".file");

        if (!fallbackFile.exists()) {
            createDefaultConfig();
        }

        boolean loaded = loadConfiguration(fallbackName);
        if (!loaded) {
            Console.logManager("Configuration -> Не удалось автоматически загрузить конфигурацию '" + fallbackName + "' после удаления. Актуальный конфиг: " + currentConfigurationName);
            return currentConfigurationName;
        }

        return fallbackName;
    }

    public void resetConfiguration(String configName) {
        if (configName == null || configName.trim().isEmpty()) {
            Console.logManager("Configuration -> Имя конфигурации не задано, сброс невозможен.");
            return;
        }

        boolean isCurrent = configName.equalsIgnoreCase(currentConfigurationName);

        File configFile = isDefaultName(configName)
                ? new File(Configuration_DIR, "default.file")
                : new File(Custom_DIR, configName + ".file");

        if (!configFile.exists()) {
            Console.logManager("Configuration -> Конфигурация " + configName + " не найдена, сброс невозможен.");
            return;
        }

        try {
            JsonObject factoryDefault = buildFactoryDefaultConfigJson();
            writeConfigJsonToFile(configFile, factoryDefault, configName);

            if (isCurrent) {
                if (loadConfiguration(configName)) {
                    Console.logManager("Configuration -> Сброшенная конфигурация " + configName + " успешно загружена.");
                } else {
                    Console.logManager("Configuration -> Сброшенная конфигурация " + configName + " записана, но загрузить не удалось.");
                }
            } else {
                Console.logManager("Configuration -> Конфигурация " + configName + " сброшена к дефолту (без загрузки).");
            }

        } catch (IOException e) {
            Console.logManager("Configuration -> Ошибка при сбросе конфигурации " + configName, e);
        }
    }

    private void writeFactoryDefaultSetting(JsonObject moduleObject, Setting setting) {
        String name = setting.getName();

        switch (setting) {
            case CheckBox cb -> moduleObject.addProperty(name, resolveDefaultBoolean(cb));
            case Slider sl -> moduleObject.addProperty(name, resolveDefaultSlider(sl));
            case ModeElement me -> moduleObject.addProperty(name, resolveDefaultMode(me));
            case ColorPicker cp -> moduleObject.addProperty(name, resolveDefaultColor(cp));
            case KeyBind kb -> moduleObject.addProperty(name, resolveDefaultKey(kb));
            case SelectElements se -> moduleObject.add(name, resolveDefaultSelect(se));
            case Input in -> moduleObject.addProperty(name, resolveDefaultInput(in));
            case DirectionSetting direction -> moduleObject.add(name, resolveDefaultDirection(direction));
            case CurveSetting curve -> moduleObject.add(name, resolveDefaultCurve(curve));
            case Collection col -> {
                JsonObject nested = new JsonObject();
                for (Setting sub : col.getSettings()) {
                    writeFactoryDefaultSetting(nested, sub);
                }
                moduleObject.add(name, nested);
            }
            default -> {
            }
        }
    }

    private boolean resolveDefaultBoolean(CheckBox cb) {
        Object[] defaults = cb.getInitialDefaults();
        if (defaults != null && defaults.length > 0) {
            Object first = defaults[0];
            if (first instanceof Boolean bool) {
                return bool;
            }
            if (first instanceof Number number) {
                return number.floatValue() != 0.0f;
            }
        }
        return false;
    }

    private float resolveDefaultSlider(Slider slider) {
        Object[] defaults = slider.getInitialDefaults();
        Float min = slider.getMin();
        Float max = slider.getMax();
        if (defaults != null && defaults.length > 0 && defaults[0] instanceof Number number) {
            float value = number.floatValue();
            if (min != null && max != null) {
                value = Math.max(min, Math.min(max, value));
            }
            return value;
        }
        if (min != null && max != null) {
            return (min + max) / 2f;
        }
        return slider.getValue() != null ? slider.getValue() : 0f;
    }

    private String resolveDefaultMode(ModeElement mode) {
        Object[] defaults = mode.getInitialDefaults();
        if (defaults != null && defaults.length > 0 && defaults[0] instanceof String str && !str.isEmpty()) {
            return str;
        }
        String defaultValue = mode.getDefaultValue();
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return defaultValue;
        }
        if (!mode.getValues().isEmpty()) {
            return mode.getValues().getFirst();
        }
        return mode.getValue() != null ? mode.getValue() : "";
    }

    private int resolveDefaultColor(ColorPicker picker) {
        Object[] defaults = picker.getInitialDefaults();
        if (defaults != null && defaults.length > 0 && defaults[0] instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private int resolveDefaultKey(KeyBind keyBind) {
        Object[] defaults = keyBind.getInitialDefaults();
        if (defaults != null && defaults.length > 0 && defaults[0] instanceof Number number) {
            return number.intValue();
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    private JsonObject resolveDefaultSelect(SelectElements select) {
        JsonObject object = new JsonObject();
        var defaults = select.getDefaultSelected();
        select.getValues().forEach(option -> {
            boolean selected = defaults.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(option.getName()));
            object.addProperty(option.getName(), selected);
        });
        return object;
    }

    private String resolveDefaultInput(Input input) {
        Object[] defaults = input.getInitialDefaults();
        String value = (defaults != null && defaults.length > 0 && defaults[0] instanceof String str) ? str : "";
        if (input.isOnlyNumber() && (value == null || !value.matches("\\d*"))) {
            return "";
        }
        return value != null ? value : "";
    }

    private JsonObject resolveDefaultDirection(DirectionSetting direction) {
        Object[] defaults = direction.getInitialDefaults();
        float x = direction.getX();
        float y = direction.getY();
        if (defaults != null && defaults.length >= 2 && defaults[0] instanceof Number dx && defaults[1] instanceof Number dy) {
            x = dx.floatValue();
            y = dy.floatValue();
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        return obj;
    }

    private JsonObject resolveDefaultCurve(CurveSetting curve) {
        Object[] defaults = curve.getInitialDefaults();
        float x1 = curve.getX1();
        float y1 = curve.getY1();
        float x2 = curve.getX2();
        float y2 = curve.getY2();
        if (defaults != null && defaults.length >= 4
                && defaults[0] instanceof Number dx1
                && defaults[1] instanceof Number dy1
                && defaults[2] instanceof Number dx2
                && defaults[3] instanceof Number dy2) {
            x1 = dx1.floatValue();
            y1 = dy1.floatValue();
            x2 = dx2.floatValue();
            y2 = dy2.floatValue();
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("x1", x1);
        obj.addProperty("y1", y1);
        obj.addProperty("x2", x2);
        obj.addProperty("y2", y2);
        return obj;
    }

    public boolean isEmpty() {
        return getConfigs().isEmpty();
    }

    public List<Configuration> getConfigs() {
        List<Configuration> configurations = new ArrayList<>();

        File defaultFile = new File(Configuration_DIR, "default.file");
        if (defaultFile.exists()) {
            configurations.add(new Configuration("default", defaultFile));
        }

        File[] customFiles = Custom_DIR.listFiles();
        if (customFiles != null) {
            for (File configFile : customFiles) {
                if (configFile.isFile() && configFile.getName().endsWith(".file")) {
                    String configName = configFile.getName().replace(".file", "");
                    configurations.add(new Configuration(configName, configFile));
                }
            }
        }

        return configurations;
    }

    public boolean loadConfiguration(String configuration) {
        String targetName = (configuration == null || configuration.trim().isEmpty()) ? "default" : configuration.trim();

        if (currentConfigurationName != null && !currentConfigurationName.trim().isEmpty() && !currentConfigurationName.equalsIgnoreCase(targetName)) {
            saveConfiguration(currentConfigurationName, false);
        }

        Configuration config = findConfig(targetName);

        if (config == null) {
            Console.logManager("Configuration -> Конфиг не найден: " + targetName);
            saveLastConfig("default");
            File def = new File(Configuration_DIR, "default.file");
            if (!def.exists()) createDefaultConfig();
            saveConfiguration("default", true);
            currentConfigurationName = "default";
            return loadConfiguration("default");
        }

        try {
            String encryptedContent = new String(java.nio.file.Files.readAllBytes(config.getFile().toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String jsonContent = decrypt(encryptedContent);

            JsonElement element = JsonParser.parseString(jsonContent);
            JsonObject object = element.getAsJsonObject();

            if (object == null || object.entrySet().isEmpty()) {
                Console.logManager("Configuration -> Файл конфига пуст, загружается default.");
                saveLastConfig("default");
                File def = new File(Configuration_DIR, "default.file");
                if (!def.exists()) createDefaultConfig();
                saveConfiguration("default", true);
                currentConfigurationName = "default";
                return loadConfiguration("default");
            }

            config.loadConfig(object);
            currentConfigurationName = targetName;

            saveLastConfig(targetName);

            Console.logManager("Configuration -> Загружен конфиг: " + targetName);
            return true;
        } catch (Exception e) {
            Console.logManager("Configuration -> Ошибка при чтении конфига: " + targetName, e);
            saveLastConfig("default");
            File def = new File(Configuration_DIR, "default.file");
            if (!def.exists()) createDefaultConfig();
            saveConfiguration("default", true);
            currentConfigurationName = "default";
            return loadConfiguration("default");
        }
    }

    public void saveConfiguration(String configuration) {
        saveConfiguration(configuration, true);
    }

    public boolean saveConfiguration(String configuration, boolean explicit) {
        if (configuration == null || configuration.trim().isEmpty()) {
            configuration = "default";
        }

        final boolean isCurrent = configuration.equalsIgnoreCase(currentConfigurationName);

        if (!explicit && !isCurrent) {
            Console.logManager("Configuration -> Пропускаю сохранение '" + configuration + "': конфиг не активен.");
            return false;
        }

        File targetFile = isDefaultName(configuration)
                ? new File(Configuration_DIR, "default.file")
                : new File(Custom_DIR, configuration + ".file");

        Configuration config = new Configuration(configuration, targetFile);
        String jsonRaw = config.saveConfig().toString();
        String encryptedData = encrypt(jsonRaw);

        try {
            if (!targetFile.exists()) targetFile.createNewFile();
            try (FileWriter writer = new FileWriter(targetFile)) {
                writer.write(encryptedData);
                writer.flush();
            }

            if (isCurrent) {
                saveLastConfig(configuration);
            }

            Console.logManager("Configuration -> Конфиг '" + configuration + "' сохранён в " + targetFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Console.logManager("Configuration -> Ошибка при сохранении конфига: " + configuration, e);
            return false;
        }
    }

    public Configuration findConfig(String configName) {
        if (configName == null) return null;
        File configFile = configName.equals("default") ? new File(Configuration_DIR, "default.file") : new File(Custom_DIR, configName + ".file");

        if (configFile.exists()) {
            return new Configuration(configName, configFile);
        }

        return null;
    }

    /**
     * Модули, включаемые по умолчанию при самом первом запуске клиента
     * (когда папки с конфигами ещё не существует). Дальше состояние берётся из конфига,
     * поэтому пользователь может выключить их, и это сохранится.
     */
    private void applyFirstLaunchDefaults() {
        if (IRC.INSTANCE != null && !IRC.INSTANCE.isEnabled()) {
            IRC.INSTANCE.setEnabled(true);
            Console.logManager("Configuration -> Первый запуск: IRC включён по умолчанию");
        }
    }

    private void createDefaultConfig() {
        File defaultFile = new File(Configuration_DIR, "default.file");
        Configuration defaultConfiguration = new Configuration("default", defaultFile);
        saveConfiguration(defaultConfiguration.getName(), true);
        Console.logManager("Configuration -> Системная конфигурация создана: default.file");
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveConfiguration(currentConfigurationName, false);
                saveLastConfig(currentConfigurationName);
                Console.logManager("Shutdown -> Конфигурация " + currentConfigurationName + " сохранена при выходе.");
            } catch (Exception e) {
                Console.logManager("Shutdown -> Ошибка при сохранении конфигурации при выходе", e);
            }
        }));
    }

    public void saveCurrentConfigSafely() {
        try {
            LynxWave.getInstance().getModuleManager().getModules().forEach(module -> module.getSettings().forEach(setting -> {}));
            saveConfiguration(currentConfigurationName, false);
            saveLastConfig(currentConfigurationName);
            Console.logManager("Shutdown -> Конфигурация сохранена");
        } catch (Throwable t) {
            Console.logManager("Shutdown -> Ошибка при сохранении конфигурации", t);
        }
    }

    private boolean isDefaultName(String name) {
        return name != null && name.equalsIgnoreCase("default");
    }

    public String encrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.encrypt(data) : data;
    }

    public String decrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.decrypt(data) : data;
    }

    private JsonObject buildFactoryDefaultConfigJson() {
        JsonObject modulesObject = new JsonObject();
        for (Module module : LynxWave.getInstance().getModuleManager().getModules()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.addProperty("bind", GLFW.GLFW_KEY_UNKNOWN);
            moduleObject.addProperty("enable", false);

            for (Setting setting : module.getSettings()) {
                writeFactoryDefaultSetting(moduleObject, setting);
            }

            modulesObject.add(module.getName().toLowerCase(), moduleObject);
        }

        JsonObject finalConfig = new JsonObject();
        finalConfig.add("modules", modulesObject);
        return finalConfig;
    }

    private void writeConfigJsonToFile(File configFile, JsonObject configJson, String configNameForLogs) throws IOException {
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(configJson);
        String encrypted = encrypt(pretty);
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(encrypted);
        }
        Console.logManager("Configuration -> Конфигурация " + configNameForLogs + " перезаписана дефолтом.");
    }
}
