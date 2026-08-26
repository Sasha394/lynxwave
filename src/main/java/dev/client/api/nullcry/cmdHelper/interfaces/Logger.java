package dev.client.api.nullcry.cmdHelper.interfaces;

import net.minecraft.text.Text;

public interface Logger {
    void log(String message);

    default void log(Text message) {
        log(message.getString());
    }
}
