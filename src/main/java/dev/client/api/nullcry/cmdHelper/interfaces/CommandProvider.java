package dev.client.api.nullcry.cmdHelper.interfaces;

import java.util.Map;

public interface CommandProvider {
    Command command(String alias);
    Map<String, Command> getCommandMap();
}
