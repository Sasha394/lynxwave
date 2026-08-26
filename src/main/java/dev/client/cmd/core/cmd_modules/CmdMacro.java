package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.cmdHelper.managers.macro.MacroManager;
import dev.client.api.nullcry.helper.client.keyboard.KeyboardStorage;
import dev.client.cmd.core.util.CommandTextUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdMacro implements Command, CommandWithAdvice {
    final MacroManager macrosManager;
    final Prefix prefix;
    final Logger logger;

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElseThrow();
        switch (commandType) {
            case "add" -> addMacro(parameters);
            case "delete" -> deleteMacro(parameters);
            case "clear" -> clearMacros();
            case "list" -> printMacrosList();
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, clear, list");
        }
    }

    @Override
    public String name() {
        return "macro";
    }

    @Override
    public String description() {
        return "Позволяет управлять макросами";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "macro add <name> <key> <message> - Добавить новый макрос",
                prefix.get() + "macro delete <name> - Удалить макрос",
                prefix.get() + "macro list - Получить список макросов",
                prefix.get() + "macro clear - Очистить список макросов",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "macro add home H /home home"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return List.of("name");
        }
        if ("delete".equalsIgnoreCase(subCommand)) {
            return macrosManager.getMacroNames();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> List.of("name");
                case 1 -> KeyboardStorage.getAllKeys();
                case 2 -> List.of("message");
                default -> List.of();
            };
        }
        if ("delete".equalsIgnoreCase(subCommand)) {
            return step == 0 ? macrosManager.getMacroNames() : List.of();
        }
        return List.of();
    }

    private void addMacro(Parameters parameters) {
        String macroName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название макроса." + Formatting.RESET));
        String macroKey = parameters.asString(2).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите кнопку, при нажатии которой сработает макрос." + Formatting.RESET));

        if (!KeyboardStorage.hasKey(macroKey)) {
            logger.log(
                    Formatting.RED + "Клавиша " +
                            Formatting.WHITE + "'" + macroKey + "'" +
                            Formatting.RED + " не поддерживается." +
                            Formatting.RESET
            );
            return;
        }

        String macroMessage = parameters.collectMessage(3);

        if (macroMessage.isEmpty()) {
            throw new CommandException(
                    Formatting.RED + "Укажите сообщение, которое будет писать макрос." + Formatting.RESET
            );
        }

        int key = KeyboardStorage.getKey(macroKey);
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            logger.log(
                    Formatting.RED + "Клавиша " +
                            Formatting.WHITE + "'" + macroKey + "'" +
                            Formatting.RED + " не найдена!" +
                            Formatting.RESET
            );
            return;
        }

        checkMacroExist(macroName);

        macrosManager.addMacro(macroName, macroMessage, key);

        logger.log(
                Formatting.GRAY + "Макрос " +
                        Formatting.WHITE + "'" + macroName + "'" +
                        Formatting.GRAY + " с клавишей " +
                        Formatting.WHITE + "'" + macroKey.toUpperCase() + "'" +
                        Formatting.GRAY + " и командой: " +
                        Formatting.WHITE + "\"" + macroMessage + "\"" +
                        Formatting.GRAY + " успешно добавлен." +
                        Formatting.RESET
        );
    }

    private void deleteMacro(Parameters parameters) {
        String macroName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите название макроса." + Formatting.RESET));

        if (!macrosManager.hasMacro(macroName)) {
            logger.log(
                    Formatting.RED + "Макрос " +
                            Formatting.WHITE + "'" + macroName + "'" +
                            Formatting.RED + " не найден в списке." +
                            Formatting.RESET
            );
            return;
        }

        macrosManager.deleteMacro(macroName);

        logger.log(
                Formatting.GRAY + "Макрос " +
                        Formatting.WHITE + "'" + macroName + "'" +
                        Formatting.GRAY + " был успешно " +
                        Formatting.GRAY + "удалён." +
                        Formatting.RESET
        );
    }

    private void clearMacros() {
        if (macrosManager.isEmpty()) {
            logger.log(
                    Formatting.RED + "Список макросов пуст, очистка невозможна." +
                            Formatting.RESET
            );
            return;
        }

        macrosManager.clearList();
        logger.log(
                Formatting.GRAY + "Все макросы были успешно " +
                        Formatting.WHITE + "удалены." +
                        Formatting.RESET
        );
    }

    private void printMacrosList() {
        if (macrosManager.isEmpty()) {
            logger.log(Formatting.RED + "Список макросов пуст.");
            return;
        }

        logger.log(Text.literal("Список макросов:").formatted(Formatting.GRAY));

        String prefixValue = prefix.get();

        LynxWave.getInstance().getMacroManager().macroList
                .forEach(macro -> {
                    String keyName = GLFW.glfwGetKeyName(macro.getKey(), 0);
                    if (keyName == null) {
                        keyName = "UNKNOWN";
                    }

                    MutableText line = Text.literal("Название: ").formatted(Formatting.GRAY)
                            .append(Text.literal(macro.getName()).formatted(Formatting.WHITE))
                            .append(Text.literal(", Команда: ").formatted(Formatting.GRAY))
                            .append(Text.literal(macro.getMessage()).formatted(Formatting.YELLOW))
                            .append(Text.literal(", Кнопка: ").formatted(Formatting.GRAY))
                            .append(Text.literal(keyName.toUpperCase()).formatted(Formatting.GREEN));

                    line.append(CommandTextUtil.bracketedButton(
                            "Удалить",
                            Formatting.RED,
                            ClickEvent.Action.SUGGEST_COMMAND,
                            prefixValue + "macro delete " + macro.getName(),
                            "§cУдалить макрос"
                    ));
                    line.append(CommandTextUtil.bracketedButton(
                            "Скопировать",
                            Formatting.AQUA,
                            ClickEvent.Action.COPY_TO_CLIPBOARD,
                            macro.getMessage(),
                            "§7Скопировать сообщение макроса"
                    ));

                    logger.log(line);
                });
    }

    private void checkMacroExist(String macroName) {
        if (macrosManager.hasMacro(macroName)) {
            throw new CommandException(Formatting.RED + "Макрос с таким именем уже есть в списке!");
        }
    }
}
