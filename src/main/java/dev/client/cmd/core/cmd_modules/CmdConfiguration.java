package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.cmdHelper.managers.configuration.Configuration;
import dev.client.api.nullcry.cmdHelper.managers.configuration.ConfigurationManager;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.cmd.core.util.CommandTextUtil;
import dev.client.modules.core.render.Interface;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdConfiguration implements Command, CommandWithAdvice {
    final ConfigurationManager configurationManager;
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElse("");

        switch (commandType) {
            case "load" -> loadConfig(parameters);
            case "save" -> saveConfig(parameters);
            case "delete" -> deleteConfig(parameters);
            case "list" -> configList();
            case "dir" -> getDirectory();
            case "reset" -> resetConfig(parameters);
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "load, save, list, dir, delete");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("load", "save", "delete", "list", "dir", "reset");
    }

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String description() {
        return "Позволяет взаимодействовать с конфигами.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "config load <config> - Загрузить конфиг",
                prefix.get() + "config save <config> - Сохранить конфиг",
                prefix.get() + "config delete <config> - Удалить конфиг навсегда",
                prefix.get() + "config reset <config> - Сбросить конфиг к стандартным настройкам",
                prefix.get() + "config list - Получить список конфигов",
                prefix.get() + "config dir - Открыть папку с конфигами",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "config save nameConfig",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + "Пример: " + prefix.get() + "config load nameConfig",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + "Пример: " + prefix.get() + "config reset nameConfig"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if (List.of("load", "delete", "reset", "save").contains(subCommand)) {
            return configurationManager.getConfigs().stream()
                    .map(Configuration::getName)
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if (step == 0 && List.of("load", "delete", "reset", "save").contains(subCommand)) {
            return configurationManager.getConfigs().stream()
                    .map(Configuration::getName)
                    .toList();
        }
        return List.of();
    }

    private void loadConfig(Parameters parameters) {
        String configName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название конфига!" + Formatting.RESET));
        Configuration config = configurationManager.findConfig(configName);

        if (config != null && config.getFile().exists()) {
            configurationManager.loadConfiguration(configName);
            logger.log(Formatting.GRAY + "Конфигурация " + Formatting.WHITE + "'" + configName + "'" + Formatting.GRAY + " загружена!" + Formatting.RESET);
            if (Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Notifications")) {
                LynxWave.getInstance().getNotificationManager().add("Конфигурация" + " " + configName + " загружена!", 3, NotificationType.CONFIG, RenderType.WORLD);
            }
        } else {
            logger.log(Formatting.RED + "Конфигурация " + Formatting.WHITE + "'" + configName + "'" + Formatting.RED + " не найдена." + Formatting.RESET);
            if (Interface.INSTANCE.isEnabled() && Interface.INSTANCE.elements.isSelected("Notifications")) {
                LynxWave.getInstance().getNotificationManager().add("Конфигурация" + " " + configName + " не найдена.", 3, NotificationType.CONFIG, RenderType.WORLD);
            }
        }
    }

    private void saveConfig(Parameters parameters) {
        String configName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название конфига!" + Formatting.RESET));

        configurationManager.saveConfiguration(configName);
        logger.log(
                Formatting.GRAY + "Конфигурация " +
                        Formatting.WHITE + "'" + configName + "'" +
                        Formatting.GRAY + " успешно " +
                        Formatting.GRAY + "сохранена." +
                        Formatting.RESET
        );
    }

    private void configList() {
        if (configurationManager == null) {
            logger.log(
                    Formatting.RED + "Ошибка: " +
                            Formatting.WHITE + "ConfigStorage" +
                            Formatting.RED + " не инициализирован!" +
                            Formatting.RESET
            );
            return;
        }

        if (configurationManager.isEmpty()) {
            logger.log(
                    Formatting.RED + "Список конфигураций пуст." +
                            Formatting.RESET
            );
            return;
        }

        logger.log(Text.literal("Список доступных конфигураций:").formatted(Formatting.GRAY));

        String prefixValue = prefix.get();
        String currentConfig = configurationManager.getCurrentConfigurationName();

        for (Configuration configuration : configurationManager.getConfigs()) {
            String name = configuration.getName();
            MutableText line = Text.literal(name).formatted(Formatting.WHITE);
            boolean isActive = name.equalsIgnoreCase(currentConfig);

            if (isActive) {
                line.append(CommandTextUtil.bracketedButton("Загружен", Formatting.GREEN, null, null, "§aЭтот конфиг активен"));
                if (!"default".equalsIgnoreCase(name)) {
                    line.append(CommandTextUtil.bracketedButton(
                            "Удалить",
                            Formatting.RED,
                            ClickEvent.Action.SUGGEST_COMMAND,
                            prefixValue + "config delete " + name,
                            "§cУдалить конфигурацию"
                    ));
                }
                line.append(CommandTextUtil.bracketedButton(
                        "Сохранить",
                        Formatting.AQUA,
                        ClickEvent.Action.SUGGEST_COMMAND,
                        prefixValue + "config save " + name,
                        "§7Сохранить текущие настройки"
                ));
                line.append(CommandTextUtil.bracketedButton(
                        "Сбросить",
                        Formatting.YELLOW,
                        ClickEvent.Action.SUGGEST_COMMAND,
                        prefixValue + "config reset " + name,
                        "§eСбросить конфигурацию к стандартным настройкам"
                ));
            } else {
                line.append(CommandTextUtil.bracketedButton(
                        "Загрузить",
                        Formatting.GREEN,
                        ClickEvent.Action.SUGGEST_COMMAND,
                        prefixValue + "config load " + name,
                        "§aЗагрузить конфигурацию"
                ));
                if (!"default".equalsIgnoreCase(name)) {
                    line.append(CommandTextUtil.bracketedButton(
                            "Удалить",
                            Formatting.RED,
                            ClickEvent.Action.SUGGEST_COMMAND,
                            prefixValue + "config delete " + name,
                            "§cУдалить конфигурацию"
                    ));
                }
                line.append(CommandTextUtil.bracketedButton(
                        "Сбросить",
                        Formatting.YELLOW,
                        ClickEvent.Action.SUGGEST_COMMAND,
                        prefixValue + "config reset " + name,
                        "§eСбросить конфигурацию к стандартным настройкам"
                ));
            }

            logger.log(line);
        }
    }

    private void getDirectory() {
        try {
            Runtime.getRuntime().exec("explorer " + configurationManager.Custom_DIR.getAbsolutePath());
        } catch (IOException e) {
            logger.log(Formatting.RED + "Папка с конфигурациями не найдена! " + e.getMessage());
        }
    }

    private void deleteConfig(Parameters parameters) {
        String configName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название конфига для удаления!" + Formatting.RESET));

        if ("default".equalsIgnoreCase(configName)) {
            logger.log(
                    Formatting.RED + "Системная конфигурация " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.RED + " не может быть удалена." +
                            Formatting.RESET
            );
            return;
        }

        Configuration config = configurationManager.findConfig(configName);
        File configFile = config != null ? config.getFile() : null;

        if (configFile == null || !configFile.exists()) {
            logger.log(
                    Formatting.RED + "Конфигурация " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.RED + " не найдена, удаление невозможно." +
                            Formatting.RESET
            );
            return;
        }

        boolean wasActive = configName.equalsIgnoreCase(configurationManager.getCurrentConfigurationName());

        if (configFile.delete()) {
            String fallback = configurationManager.removeConfiguration(configName);
            logger.log(
                    Formatting.GRAY + "Конфигурация " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.GRAY + " была успешно " +
                            Formatting.GRAY + "удалена." +
                            Formatting.RESET
            );

            if (wasActive) {
                String fallbackName = (fallback != null && !fallback.isBlank())
                        ? fallback
                        : configurationManager.getCurrentConfigurationName();

                logger.log(
                        Formatting.YELLOW + "Активная конфигурация была удалена. Загружен конфиг " +
                                Formatting.WHITE + "'" + fallbackName + "'" +
                                Formatting.YELLOW + "." +
                                Formatting.RESET
                );
            }
        } else {
            logger.log(
                    Formatting.RED + "Ошибка при удалении конфига " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.RESET
            );
        }
    }

    private void resetConfig(Parameters parameters) {
        String configName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название конфига для сброса!" + Formatting.RESET));
        Configuration config = configurationManager.findConfig(configName);
        File configFile = config != null ? config.getFile() : null;

        if (configFile != null && !configFile.exists()) {
            logger.log(
                    Formatting.RED + "Конфигурация " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.RED + " не найдена, сброс невозможен." +
                            Formatting.RESET
            );
            return;
        }

        configurationManager.resetConfiguration(configName);

        if ("default".equalsIgnoreCase(configName)) {
            logger.log(
                    Formatting.WHITE + "Системная конфигурация " +
                            Formatting.WHITE + "'default'" +
                            Formatting.WHITE + " была успешно сброшена." +
                            Formatting.RESET
            );
        } else {
            logger.log(
                    Formatting.GRAY + "Конфигурация " +
                            Formatting.WHITE + "'" + configName + "'" +
                            Formatting.GRAY + " успешно сброшена к " +
                            Formatting.GRAY + "стандартным настройкам." +
                            Formatting.RESET
            );
        }
    }
}
