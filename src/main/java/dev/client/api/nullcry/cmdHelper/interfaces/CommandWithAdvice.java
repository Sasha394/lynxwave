package dev.client.api.nullcry.cmdHelper.interfaces;

import java.util.List;

public interface CommandWithAdvice {
    List<String> adviceMessage();
    List<String> parametersCommand();

    default List<String> firstArguments(String subCommand) {
        return List.of();
    }

    default List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        return List.of();
    }
}
