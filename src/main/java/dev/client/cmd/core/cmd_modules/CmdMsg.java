package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdMsg implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;

    @Override
    public List<String> parametersCommand() {
        return List.of("");
    }

    @Override
    public String name() {
        return "msg";
    }

    @Override
    public String description() {
        return "Показывает ваше сообщение в уведомлении и в чате.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                Formatting.GRAY + prefix.get() + "msg <сообщение> - Показать сообщение в уведомлении и чате",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "msg привет"
        );
    }

    @Override
    public void execute(Parameters parameters) throws CommandException {
        String message = parameters.collectMessage(0).trim();

        if (message.isEmpty()) {
            logger.log(Formatting.RED + "Укажите текст сообщения.");
            return;
        }

        LynxWave.getInstance().getNotificationManager()
                .add(message, 3, NotificationType.INFO, RenderType.WORLD);

        logger.log(message);
    }
}
