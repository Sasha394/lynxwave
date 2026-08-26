package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StandaloneCommandDispatcher implements CommandDispatcher, CommandProvider {
    private static final String DELIMITER = " ";
    final Prefix prefix;
    final ParametersFactory parametersFactory;
    final Logger logger;
    final Map<String, Command> commandMap;

    public StandaloneCommandDispatcher(List<Command> commands, AdviceCommandFactory adviceCommandFactory, Prefix prefix, ParametersFactory parametersFactory, Logger logger) {
        this.prefix = prefix;
        this.parametersFactory = parametersFactory;
        this.logger = logger;
        this.commandMap = commandsToMap(commandsWithHelpsCommand(adviceCommandFactory, commands));
    }

    @Override
    public DispatchResult dispatch(String message) {
        String prefixValue = this.prefix.get();

        if (!message.startsWith(prefixValue)) {
            return DispatchResult.NOT_DISPATCHED;
        }

        String[] split = message.split(DELIMITER);
        String commandName = split[0].substring(prefixValue.length());
        Command command = commandMap.get(commandName);

        if (command == null) {
            logger.log("§cКоманда '" + commandName + "' не найдена.");
            return DispatchResult.DISPATCHED;
        }

        try {
            String parameters = extractParametersFromMessage(message, split);
            command.execute(parametersFactory.createParameters(parameters, DELIMITER));
        } catch (Exception e) {
            handleCommandException(e);
        }
        return DispatchResult.DISPATCHED;
    }

    @Override
    public Command command(String name) {
        return commandMap.get(name);
    }

    @Override
    public Map<String, Command> getCommandMap() {
        return this.commandMap;
    }

    public Prefix getPrefix() {
        return this.prefix;
    }

    public <T extends Command> T getCommand(Class<T> clazz) {
        return commandMap.values().stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Command> commandsToMap(List<Command> commands) {
        return commands.stream().collect(Collectors.toMap(Command::name, cmd -> cmd));
    }

    private void handleCommandException(Exception e) {
        if (e instanceof CommandException) {
            logger.log(e.getMessage());
        } else {
            String errorMessage = (e instanceof NullPointerException)
                    ? "§cТакой команды не существует."
                    : e.getMessage();
            logger.log("§cОшибка: " + errorMessage);
        }
    }

    private String extractParametersFromMessage(String message, String[] split) {
        return message.substring((split.length != 1 ? DELIMITER.length() : 0) + split[0].length());
    }

    private List<Command> commandsWithHelpsCommand(AdviceCommandFactory adviceCommandFactory, List<Command> commands) {
        List<Command> commandsWithHelps = new ArrayList<>(commands);

        boolean helpsExists = commands.stream().anyMatch(cmd -> "helps".equalsIgnoreCase(cmd.name()));
        if (!helpsExists) {
            commandsWithHelps.add(adviceCommandFactory.createAssistsCommand(this, logger));
        }

        return commandsWithHelps;
    }
}
