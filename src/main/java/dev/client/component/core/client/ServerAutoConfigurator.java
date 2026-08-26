package dev.client.component.core.client;

import com.google.common.eventbus.Subscribe;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.client.LynxWave;
import dev.client.api.nullcry.IModule;
import dev.client.api.nullcry.cmdHelper.managers.configuration.ConfigurationManager;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.component.Component;
import dev.client.modules.core.combat.AntiBot;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.misc.ClickHelper;
import dev.client.modules.core.misc.ElytraHelper;
import dev.client.modules.core.misc.ServerHelper;
import dev.client.modules.core.movement.TargetPearl;

import java.io.*;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerAutoConfigurator extends Component {
    private static final String STORAGE_FILE_NAME = "server_autoconfig.json";

    private final Set<IModule.ServerType> appliedServers = EnumSet.noneOf(IModule.ServerType.class);
    private boolean stateLoaded = false;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!stateLoaded) {
            loadState();
            stateLoaded = true;
        }

        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return;
        }

        IModule.ServerType serverType = detectTargetServer();
        if (serverType == null) {
            return;
        }

        if (appliedServers.contains(serverType)) {
            return;
        }

        if (!isFreshConfiguration()) {
            return;
        }

        if (applyPreset(serverType)) {
            appliedServers.add(serverType);
            saveState();

            ConfigurationManager configurationManager = LynxWave.getInstance().getConfigurationManager();
            if (configurationManager != null) {
                configurationManager.saveCurrentConfigSafely();
            }
        }
    }

    private static final Set<String> DEFAULT_KILL_AURA_FILTER = Set.of("Игроков", "Голых", "Невидимых");

    private boolean applyPreset(IModule.ServerType serverType) {
        KillAura killAura = getModule(KillAura.class);
        if (killAura == null) {
            return false;
        }

        return switch (serverType) {
            case FunTime -> applyKillAuraPreset(killAura, new KillAuraSettings(
                    "FunTime",
                    3.0f,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    DEFAULT_KILL_AURA_FILTER,
                    "Одиночный",
                    "Всему",
                    "Незаметный",
                    "Только криты",
                    Set.of()
            ));
            case HolyWorld -> applyKillAuraPreset(killAura, new KillAuraSettings(
                    "HolyWorld",
                    3.0f,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    DEFAULT_KILL_AURA_FILTER,
                    "Одиночный",
                    "Всему",
                    "Сфокусированная",
                    "Только криты",
                    Set.of("Открыт контейнер", "Используешь еду")
            )) && applyHolyWorldLegitUse();
            case SpookyTime -> applyKillAuraPreset(killAura, new KillAuraSettings(
                    "SpookyTime",
                    3.0f,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    DEFAULT_KILL_AURA_FILTER,
                    "Одиночный",
                    "Всему",
                    "Сфокусированная",
                    "Только криты",
                    Set.of("Открыт контейнер", "Используешь еду")
            ));
            case ReallyWorld -> applyReallyWorldPreset(killAura);
            default -> false;
        };
    }

    private boolean applyKillAuraPreset(KillAura killAura, KillAuraSettings settings) {
        applyKillAuraSettings(killAura, settings);
        return true;
    }

    private boolean applyReallyWorldPreset(KillAura killAura) {
        applyKillAuraSettings(killAura, new KillAuraSettings(
                "Matrix",
                3.0f,
                true,
                true,
                false,
                true,
                false,
                true,
                DEFAULT_KILL_AURA_FILTER,
                "Одиночный",
                "Всему",
                "Сфокусированная",
                "Только криты",
                Set.of("Используешь еду")
        ));

        AntiBot antiBot = getModule(AntiBot.class);
        if (antiBot != null) {
            setMode(antiBot, "Режим", "RW");
        }

        return true;
    }

    private boolean applyHolyWorldLegitUse() {
        ServerHelper serverHelper = getModule(ServerHelper.class);
        if (serverHelper != null) {
            setCheckBox(serverHelper, "Использовать легитно", true);
        }

        TargetPearl targetPearl = getModule(TargetPearl.class);
        if (targetPearl != null) {
            setCheckBox(targetPearl, "Легитно выкидывать жемчуг", true);
        }

        ClickHelper clickHelper = getModule(ClickHelper.class);
        if (clickHelper != null) {
            setCheckBox(clickHelper, "Использовать легитно", true);
        }

        ElytraHelper elytraHelper = getModule(ElytraHelper.class);
        if (elytraHelper != null) {
            setCheckBox(elytraHelper, "Использовать фейерверк легитно", true);
        }

        return true;
    }

    private void applyKillAuraSettings(KillAura killAura, KillAuraSettings settings) {
        setMode(killAura, "Ротация", settings.aimMode());
        setSlider(killAura, "Дистанция", settings.distance());
        setCheckBox(killAura, "Ломать щит", settings.breakShield());
        setCheckBox(killAura, "Отжимать щит", settings.unPressShield());
        setCheckBox(killAura, "Синхронизация с тиками", settings.dynamicCooldown());
        setCheckBox(killAura, "Бить через стены", settings.wallsAttack());
        setCheckBox(killAura, "Бить только с мечом", settings.onlySword());
        setCheckBox(killAura, "Коррекция движения", settings.correction());
        setSelectElements(killAura, "Фильтр на", settings.filterOptions());
        setMode(killAura, "Режим таргета", settings.filterMode());
        setMode(killAura, "Сортировать по", settings.targetSort());
        setMode(killAura, "Режим коррекции", settings.correctionType());
        setMode(killAura, "Режим критов", settings.criticalType());
        setSelectElements(killAura, "Не бить если", settings.hungerActions());
    }

    private void setSelectElements(Module module, String settingName, Set<String> enabledOptions) {
        SelectElements select = findSetting(module, SelectElements.class, settingName);
        if (select == null) {
            return;
        }

        Set<String> normalized = enabledOptions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (String option : select.asStringList()) {
            boolean enable = normalized.contains(option.toLowerCase());
            select.get(option).applyDefault(enable);
        }
    }

    private IModule.ServerType detectTargetServer() {
        if (ConnectionHelper.isFT()) {
            return IModule.ServerType.FunTime;
        }
        if (ConnectionHelper.isHW()) {
            return IModule.ServerType.HolyWorld;
        }
        if (ConnectionHelper.isSpookyTime()) {
            return IModule.ServerType.SpookyTime;
        }
        if (ConnectionHelper.isRW()) {
            return IModule.ServerType.ReallyWorld;
        }
        return null;
    }

    private record KillAuraSettings(String aimMode,
                                    float distance,
                                    boolean breakShield,
                                    boolean unPressShield,
                                    boolean dynamicCooldown,
                                    boolean wallsAttack,
                                    boolean onlySword,
                                    boolean correction,
                                    Set<String> filterOptions,
                                    String filterMode,
                                    String targetSort,
                                    String correctionType,
                                    String criticalType,
                                    Set<String> hungerActions) { }

    private boolean isFreshConfiguration() {
        ConfigurationManager configurationManager = LynxWave.getInstance().getConfigurationManager();
        if (configurationManager == null) {
            return false;
        }

        if (!"default".equalsIgnoreCase(configurationManager.getCurrentConfigurationName())) {
            return false;
        }

        File[] customConfigs = configurationManager.Custom_DIR.listFiles((dir, name) -> name.endsWith(".file"));
        boolean hasCustomConfigs = customConfigs != null && customConfigs.length > 0;
        return !hasCustomConfigs;
    }

    private void loadState() {
        File storageFile = getStorageFile();
        if (!storageFile.exists()) {
            return;
        }

        try (Reader reader = new FileReader(storageFile)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }

            JsonObject object = parsed.getAsJsonObject();
            for (IModule.ServerType type : IModule.ServerType.values()) {
                if (object.has(type.name()) && object.get(type.name()).getAsBoolean()) {
                    appliedServers.add(type);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void saveState() {
        File storageFile = getStorageFile();
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        JsonObject object = new JsonObject();
        for (IModule.ServerType type : appliedServers) {
            object.addProperty(type.name(), true);
        }

        try (Writer writer = new FileWriter(storageFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(object, writer);
        } catch (IOException ignored) {
        }
    }

    private File getStorageFile() {
        ConfigurationManager configurationManager = LynxWave.getInstance().getConfigurationManager();
        File baseDir = configurationManager != null ? configurationManager.Configuration_DIR : LynxWave.getInstance().getClientDir();
        return new File(baseDir, STORAGE_FILE_NAME);
    }

    private <T extends Module> T getModule(Class<T> clazz) {
        return LynxWave.getInstance().getModuleManager().get(clazz);
    }

    private void setCheckBox(Module module, String name, boolean value) {
        CheckBox checkBox = findSetting(module, CheckBox.class, name);
        if (checkBox != null) {
            checkBox.applyDefault(value);
        }
    }

    private void setSlider(Module module, String name, float value) {
        Slider slider = findSetting(module, Slider.class, name);
        if (slider != null) {
            slider.applyDefault(value);
        }
    }

    private void setMode(Module module, String name, String value) {
        ModeElement modeElement = findSetting(module, ModeElement.class, name);
        if (modeElement != null) {
            modeElement.set(value);
        }
    }

    private <T extends Setting> T findSetting(Module module, Class<T> type, String name) {
        if (module == null) {
            return null;
        }

        for (Setting setting : module.getSettings()) {
            if (type.isInstance(setting) && Objects.equals(setting.getName(), name)) {
                return type.cast(setting);
            }
        }
        return null;
    }

}
