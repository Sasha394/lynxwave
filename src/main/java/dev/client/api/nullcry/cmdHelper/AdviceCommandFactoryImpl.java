package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.AdviceCommandFactory;
import dev.client.api.nullcry.cmdHelper.interfaces.CommandProvider;
import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import dev.client.cmd.core.cmd_modules.CmdAssists;

public class AdviceCommandFactoryImpl implements AdviceCommandFactory {
    private final Logger logger;

    public AdviceCommandFactoryImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public CmdAssists createAssistsCommand(CommandProvider commandProvider, Logger logger) {
        return new CmdAssists(commandProvider, logger);
    }
}
