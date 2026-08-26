package dev.client.api.nullcry.cmdHelper.interfaces;

public interface Command {
    void execute(Parameters parameters);
    String name();
    String description();
}
