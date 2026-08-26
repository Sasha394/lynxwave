package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.interfaces.Command;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import dev.client.api.nullcry.cmdHelper.interfaces.Parameters;
import dev.client.api.nullcry.cmdHelper.interfaces.Prefix;
import dev.client.modules.core.player.AutoPilot;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdList implements Command {
    final List<Command> commands;
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        logger.log(Formatting.RED + prefix.get() + "help" + Formatting.GRAY + " ➜ " + "Выдает список всех команд.");
        logger.log(Formatting.RED + prefix.get() + "assists <command>" + Formatting.GRAY + " - " + "Показывает советы по команде.");
        for (Command command : commands) {
            if (command instanceof CmdAutoPilot
                    && (AutoPilot.INSTANCE == null || !AutoPilot.INSTANCE.isEnabled())) {
                continue;
            }
            logger.log(Formatting.RED + command.name() + Formatting.GRAY + " - " + command.description());
        }
    }


    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "Выдает список всех команд.";
    }
}
