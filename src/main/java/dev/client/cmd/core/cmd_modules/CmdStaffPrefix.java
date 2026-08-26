package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.cmd.core.util.CommandTextUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdStaffPrefix implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите тип команды:" + Formatting.GRAY + " add, delete, clear, list"));
        switch (commandType) {
            case "add" -> addPrefix(parameters);
            case "delete" -> deletePrefix(parameters);
            case "clear" -> clearPrefixes();
            case "list" -> listPrefixes();
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, clear, list");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public String name() {
        return "staffPrefix";
    }

    @Override
    public String description() {
        return "Позволяет управлять префиксами стаффа.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "staffPrefix add <prefix> ➜ Добавить префикс в список",
                prefix.get() + "staffPrefix delete <prefix> ➜ Удалить префикс из списка",
                prefix.get() + "staffPrefix list ➜ Показать список префиксов",
                prefix.get() + "staffPrefix clear ➜ Очистить список префиксов",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "staffPrefix add Гл.Админ"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("delete".equalsIgnoreCase(subCommand)) {
            return List.copyOf(LynxWave.getInstance().getStaffPrefixManager().getStaffs());
        }
        if ("add".equalsIgnoreCase(subCommand)) {
            return List.of("prefix");
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if (step == 0) {
            if ("delete".equalsIgnoreCase(subCommand)) {
                return List.copyOf(LynxWave.getInstance().getStaffPrefixManager().getStaffs());
            }
            if ("add".equalsIgnoreCase(subCommand)) {
                return List.of("prefix");
            }
        }
        return List.of();
    }

    private void addPrefix(Parameters parameters) {
        String prefixStr = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите префикс для добавления." + Formatting.RESET));

        if (LynxWave.getInstance().getStaffPrefixManager().isPrefix(prefixStr)) {
            logger.log(
                    Formatting.RED + "Префикс " +
                            Formatting.WHITE + "'" + prefixStr + "'" +
                            Formatting.RED + " уже есть в списке." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getStaffPrefixManager().add(prefixStr);
        logger.log(
                Formatting.GRAY + "Префикс " +
                        Formatting.WHITE + "'" + prefixStr + "'" +
                        Formatting.GRAY + " успешно " +
                        Formatting.WHITE + "добавлен." +
                        Formatting.RESET
        );
    }

    private void deletePrefix(Parameters parameters) {
        String prefixStr = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите префикс для удаления." + Formatting.RESET));

        if (!LynxWave.getInstance().getStaffPrefixManager().isPrefix(prefixStr)) {
            logger.log(
                    Formatting.RED + "Префикс " +
                            Formatting.WHITE + "'" + prefixStr + "'" +
                            Formatting.RED + " не найден." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getStaffPrefixManager().remove(prefixStr);
        logger.log(
                Formatting.GRAY + "Префикс " +
                        Formatting.WHITE + "'" + prefixStr + "'" +
                        Formatting.GRAY + " был успешно " +
                        Formatting.WHITE + "удалён." +
                        Formatting.RESET
        );
    }

    private void clearPrefixes() {
        boolean hadPrefixes = !LynxWave.getInstance().getStaffPrefixManager().getStaffs().isEmpty();

        if (!hadPrefixes) {
            logger.log(
                    Formatting.RED + "Список префиксов уже пуст." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getStaffPrefixManager().clear();
        logger.log(
                Formatting.GRAY + "Все префиксы были успешно " +
                        Formatting.WHITE + "очищены." +
                        Formatting.RESET
        );
    }

    private void listPrefixes() {
        var prefixes = LynxWave.getInstance().getStaffPrefixManager().getStaffs();
        if (prefixes.isEmpty()) {
            logger.log(Formatting.RED + "Список префиксов пуст.");
            return;
        }

        logger.log(Text.literal("Список префиксов стаффа:").formatted(Formatting.GRAY));

        String prefixValue = prefix.get();
        prefixes.forEach(p -> {
            MutableText line = Text.literal(p).formatted(Formatting.WHITE);
            line.append(CommandTextUtil.bracketedButton(
                    "Скопировать",
                    Formatting.AQUA,
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    p,
                    "§7Скопировать префикс"
            ));
            line.append(CommandTextUtil.bracketedButton(
                    "Удалить",
                    Formatting.RED,
                    ClickEvent.Action.SUGGEST_COMMAND,
                    prefixValue + "staffPrefix delete " + p,
                    "§cУдалить префикс"
            ));
            logger.log(line);
        });
    }
}
