package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.modules.core.player.AutoPilot;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdAssists implements Command, CommandWithAdvice {
    final CommandProvider commandProvider;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandName = parameters.asString(0)
                .orElseThrow(() -> new CommandException("Вы не указали имя команды."));
        Command command = commandProvider.command(commandName);

        if (command == null) {
            throw new CommandException(Formatting.RED + "Нет такой команды.");
        }
        if ("autopilot".equalsIgnoreCase(commandName)
                && (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled())) {
            throw new CommandException(Formatting.RED + "AutoPilot отключен.");
        }

        if (!(command instanceof CommandWithAdvice commandWithAdvice)) {
            throw new CommandException(Formatting.RED + "К данной команде нет помощи.");
        }

        logger.log(Formatting.WHITE + "Пример использования команды:");
        for (String advice : commandWithAdvice.adviceMessage()) {
            logger.log(Formatting.GRAY + advice);
        }
    }

    @Override
    public String name() {
        return "assists";
    }

    @Override
    public String description() {
        return "Показывает советы для использования других команд.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of();
    }

    @Override
    public List<String> parametersCommand() {
        return commandProvider.getCommandMap().keySet().stream()
                .filter(cmdName -> !cmdName.equalsIgnoreCase("assists") && !cmdName.equalsIgnoreCase("help"))
                .filter(cmdName -> {
                    if (!"autopilot".equalsIgnoreCase(cmdName)) {
                        return true;
                    }
                    return AutoPilot.INSTANCE != null && AutoPilot.INSTANCE.isEnabled();
                })
                .toList();
    }
}
