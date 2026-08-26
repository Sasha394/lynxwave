package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.api.nullcry.modules.Module;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdBind implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElse("");

        switch (commandType) {
            case "add" -> bindModuleToKey(parameters, logger);
            case "delete" -> unbindModuleFromKey(parameters, logger);
            case "clear" -> clearAllBindings(logger);
            case "list" -> listBoundModules(logger);
            default -> throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, clear, list");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public String name() {
        return "bind";
    }

    @Override
    public String description() {
        return "Позволяет привязать модуль на определенную клавишу";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "bind add <module> <key> - Добавить новый бинд",
                prefix.get() + "bind delete <module> <key> - Удалить бинд",
                prefix.get() + "bind list - Получить список биндов",
                prefix.get() + "bind clear - Очистить список биндов",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "bind add AttackAura R"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return LynxWave.getInstance().getModuleManager().stream()
                    .filter(module -> module.getKey() == GLFW.GLFW_KEY_UNKNOWN)
                    .map(Module::getName)
                    .toList();
        } else if ("delete".equalsIgnoreCase(subCommand)) {
            return LynxWave.getInstance().getModuleManager().stream()
                    .filter(module -> module.getKey() != GLFW.GLFW_KEY_UNKNOWN)
                    .map(Module::getName)
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> LynxWave.getInstance().getModuleManager().stream()
                        .filter(module -> module.getKey() == GLFW.GLFW_KEY_UNKNOWN)
                        .map(Module::getName)
                        .toList();
                case 1 -> KeyboardStorage.getAllKeys();
                default -> List.of();
            };
        } else if ("delete".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> LynxWave.getInstance().getModuleManager().stream()
                        .filter(module -> module.getKey() != GLFW.GLFW_KEY_UNKNOWN)
                        .map(Module::getName)
                        .toList();
                case 1 -> {
                    String moduleName = previousArgs.get(0);
                    Module module = LynxWave.getInstance().getModuleManager().stream()
                            .filter(m -> m.getName().equalsIgnoreCase(moduleName) && m.getKey() != GLFW.GLFW_KEY_UNKNOWN)
                            .findFirst()
                            .orElse(null);

                    if (module != null) {
                        yield List.of("none");
                    } else {
                        yield List.of();
                    }
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private void bindModuleToKey(Parameters parameters, Logger logger) {
        String moduleName = extractModuleName(parameters);
        String keyName = parameters.asString(parameters.count() - 1)
                .orElseThrow(() -> new CommandException(Formatting.RED + "Укажите клавишу!"));

        Module module = findModuleByName(moduleName);

        if (module == null) {
            logger.log(Formatting.RED + "Модуль " + Formatting.WHITE + moduleName + Formatting.RED + " не найден.");
            return;
        }

        Integer key = KeyboardStorage.getKey(keyName.toUpperCase(Locale.ROOT));
        if (key == null || key == GLFW.GLFW_KEY_UNKNOWN) {
            logger.log(Formatting.RED + "Клавиша " + Formatting.WHITE + keyName + Formatting.RED + " не существует.");
            return;
        }

        module.setKey(key);
        logger.log(
                Formatting.GREEN + "Бинд " +
                        Formatting.GRAY + keyName.toUpperCase() +
                        Formatting.GREEN + " был установлен для модуля " +
                        Formatting.WHITE + moduleName +
                        Formatting.RESET
        );
    }

    private void unbindModuleFromKey(Parameters parameters, Logger logger) {
        String moduleName = extractModuleName(parameters);
        String keyName = parameters.asString(2).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите клавишу!"));

        Module module = findModuleByName(moduleName);
        if (module == null) {
            logger.log(Formatting.RED + "Модуль " + Formatting.WHITE + moduleName + Formatting.RED + " не найден.");
            return;
        }

        if (module.getKey() == GLFW.GLFW_KEY_UNKNOWN) {
            logger.log(Formatting.RED + "На модуле " + Formatting.WHITE + module.getName() + Formatting.RED + " нет привязанной клавиши.");
            return;
        }

        module.setKey(GLFW.GLFW_KEY_UNKNOWN);
        logger.log(
                Formatting.GREEN + "Клавиша " +
                        Formatting.GRAY + keyName.toUpperCase() +
                        Formatting.GREEN + " была отвязана от модуля " +
                        Formatting.WHITE + module.getName() +
                        Formatting.RESET
        );
    }

    private void clearAllBindings(Logger logger) {
        boolean hasBindings = LynxWave.getInstance().getModuleManager().stream()
                .anyMatch(module -> module.getKey() != GLFW.GLFW_KEY_UNKNOWN);

        if (!hasBindings) {
            logger.log(Formatting.RED + "Нет забинженных модулей для очистки.");
            return;
        }

        LynxWave.getInstance().getModuleManager().forEach(module -> module.setKey(GLFW.GLFW_KEY_UNKNOWN));
        logger.log(Formatting.GREEN + "Все клавиши были отвязаны от всех модулей.");
    }

    private void listBoundModules(Logger logger) {
        List<Module> boundModules = LynxWave.getInstance().getModuleManager().stream()
                .filter(module -> module.getKey() != GLFW.GLFW_KEY_UNKNOWN)
                .toList();

        if (boundModules.isEmpty()) {
            logger.log(Formatting.RED + "Нет привязанных модулей.");
            return;
        }

        logger.log(Text.literal("Список модулей с привязанными клавишами:").formatted(Formatting.GRAY));

        String commandPrefix = prefix.get();

        for (Module module : boundModules) {
            String keyName = getKeyName(module.getKey());
            MutableText entry = Text.literal(module.getName()).formatted(Formatting.WHITE)
                    .append(Text.literal(" [").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(keyName).formatted(Formatting.GREEN))
                    .append(Text.literal("]").formatted(Formatting.DARK_GRAY));

            entry = entry.styled(style -> style
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Нажмите, чтобы удалить бинд")))
                    .withClickEvent(new ClickEvent.SuggestCommand(commandPrefix + "bind delete " + module.getName() + " none")));

            logger.log(entry);
        }
    }

    private Module findModuleByName(String moduleName) {
        String normalizedInputName = moduleName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);

        return LynxWave.getInstance().getModuleManager().stream()
                .filter(m -> m.getName().replaceAll("\\s+", "").toLowerCase(Locale.ROOT).equals(normalizedInputName))
                .findFirst()
                .orElse(null);
    }

    private String extractModuleName(Parameters parameters) {
        StringBuilder moduleName = new StringBuilder();
        for (int i = 1; i < parameters.count() - 1; i++) {
            moduleName.append(parameters.asString(i).orElse("")).append(" ");
        }
        return moduleName.toString().trim();
    }

    private String getKeyName(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "None";
        return KeyboardStorage.getKey(keyCode).toUpperCase(Locale.ROOT);
    }
}
