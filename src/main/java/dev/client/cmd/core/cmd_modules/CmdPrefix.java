package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CmdPrefix implements Command, CommandWithAdvice {
    Prefix prefix;
    Logger logger;

    @Override
    public String name() {
        return "prefix";
    }

    @Override
    public String description() {
        return "Показывает/меняет префикс команд.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "prefix — показать текущий префикс",
                prefix.get() + "prefix <символ> — установить префикс (пример: " + prefix.get() + "prefix . )",
                prefix.get() + "prefix set <символ> — установить префикс",
                prefix.get() + "prefix default — установить стандартный префикс (.)",
                Formatting.GRAY + "Разрешён только один символ, не буква/цифра/пробел."
        );
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("get", "set", "default");
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        return List.of("get", "set", "default", ".", ",", "!", "?", "-", ";", ":");
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> prev) {
        if ("set".equalsIgnoreCase(subCommand) && step == 1) {
            return List.of(".", ",", "!", "?", "-", ";", ":");
        }
        return List.of();
    }

    @Override
    public void execute(Parameters p) {
        String a0 = p.asString(0).orElse("").trim();

        if (a0.isEmpty() || "get".equalsIgnoreCase(a0)) {
            logger.log(Formatting.GREEN + "Текущий префикс: " + Formatting.WHITE + prefix.get());
            return;
        }

        String candidate = a0;
        if ("default".equalsIgnoreCase(a0)) {
            candidate = ".";
        }
        if ("set".equalsIgnoreCase(a0)) {
            candidate = p.asString(1).orElseThrow(() ->
                    new CommandException(Formatting.RED + "Укажи символ префикса."));
        }

        candidate = candidate.trim();
        validatePrefixOrThrow(candidate);

        try {
            prefix.set(candidate);
            logger.log(Formatting.GREEN + "Префикс установлен: " + Formatting.WHITE + candidate);
        } catch (IllegalArgumentException ex) {
            throw new CommandException(Formatting.RED + ex.getMessage());
        }
    }

    private void validatePrefixOrThrow(String s) {
        if (s == null || s.length() != 1) {
            throw new CommandException(Formatting.RED + "Префикс должен быть ОДНИМ символом.");
        }
        char c = s.charAt(0);
        if (c == '/' || Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
            throw new CommandException(Formatting.RED + "Запрещены буквы, цифры и пробел. Разрешены только знаки препинания и т.п.");
        }
    }
}
