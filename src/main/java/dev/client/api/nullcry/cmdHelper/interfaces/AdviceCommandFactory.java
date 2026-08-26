package dev.client.api.nullcry.cmdHelper.interfaces;

import dev.client.cmd.core.cmd_modules.CmdAssists;

public interface AdviceCommandFactory {
    CmdAssists createAssistsCommand(CommandProvider commandProvider, Logger logger);
}
