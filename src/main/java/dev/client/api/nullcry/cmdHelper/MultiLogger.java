package dev.client.api.nullcry.cmdHelper;

import dev.client.api.nullcry.cmdHelper.interfaces.Logger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MultiLogger implements Logger {
    final List<Logger> loggers;

    @Override
    public void log(String message) {
        for (Logger logger : loggers) {
            logger.log(message);
        }
    }

    @Override
    public void log(Text message) {
        for (Logger logger : loggers) {
            logger.log(message);
        }
    }
}
