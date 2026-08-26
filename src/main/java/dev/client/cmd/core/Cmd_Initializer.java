package dev.client.cmd.core;

import dev.client.api.nullcry.cmdHelper.AdviceCommandFactoryImpl;
import dev.client.api.nullcry.cmdHelper.ParametersFactoryImpl;
import dev.client.api.nullcry.cmdHelper.PrefixImpl;
import dev.client.api.nullcry.cmdHelper.StandaloneCommandDispatcher;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.cmdHelper.managers.configuration.ConfigurationManager;
import dev.client.api.nullcry.cmdHelper.managers.macro.MacroManager;
import dev.client.cmd.core.cmd_modules.*;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class Cmd_Initializer {
    private final StandaloneCommandDispatcher commandDispatcher;

    public Cmd_Initializer(MinecraftClient mc, Logger logger, MacroManager macroManager, ConfigurationManager configurationManager) {
        Prefix prefix = new PrefixImpl();
        AdviceCommandFactory adviceCommandFactory = new AdviceCommandFactoryImpl(logger);
        ParametersFactory parametersFactory = new ParametersFactoryImpl();

        List<Command> commands = new ArrayList<>();
        CommandProvider commandProvider = new CommandProvider() {
            @Override
            public Command command(String alias) {
                return commands.stream()
                        .filter(cmd -> cmd.name().equalsIgnoreCase(alias))
                        .findFirst()
                        .orElse(null);
            }

            @Override
            public Map<String, Command> getCommandMap() {
                return commandDispatcher.getCommandMap();
            }
        };

        commands.add(new CmdList(commands, prefix, logger));
        commands.add(new CmdFriend(mc, prefix, logger));
        commands.add(new CmdBind(prefix, logger));
        commands.add(new CmdGps(mc, prefix, logger));
        commands.add(new CmdAutoPilot(mc, prefix, logger));
        commands.add(new CmdConfiguration(configurationManager, prefix, logger));
        commands.add(new CmdMacro(macroManager, prefix, logger));
        commands.add(new CmdStaffList(mc, prefix, logger));
        commands.add(new CmdStaffPrefix(prefix, logger));
        commands.add(new CmdParser(mc, prefix, logger));
        commands.add(new CmdReport(prefix, logger));
        commands.add(new CmdHClip(mc, prefix, logger));
        commands.add(new CmdVClip(prefix, logger, mc));
        commands.add(new CmdRCT(mc, prefix, logger));
        commands.add(new CmdPrefix(prefix, logger));
        commands.add(new CmdXray(prefix, logger));
        commands.add(new CmdIRC(prefix, logger));
        commands.add(new CmdFakePlayer(prefix, logger));
        commands.add(new CmdNeuro(prefix, logger));

        this.commandDispatcher = new StandaloneCommandDispatcher(
                commands,
                adviceCommandFactory,
                prefix,
                parametersFactory,
                logger
        );
    }
}
