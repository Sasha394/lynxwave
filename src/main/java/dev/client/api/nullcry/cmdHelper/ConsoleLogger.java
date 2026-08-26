package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.Logger;

public class ConsoleLogger implements Logger {

    @Override
    public void log(String message) {
        System.out.println("message = " + message);
    }
}
