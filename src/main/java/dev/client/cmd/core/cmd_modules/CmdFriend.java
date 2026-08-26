package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.cmd.core.util.CommandTextUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdFriend implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите тип команды: add, delete, clear, list."));

        switch (commandType.toLowerCase()) {
            case "add" -> addFriend(parameters);
            case "delete" -> deleteFriend(parameters);
            case "clear" -> clearFriendList();
            case "list" -> getFriendList();
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, clear, list.");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public String name() {
        return "friend";
    }

    @Override
    public String description() {
        return "Позволяет управлять списком друзей.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "friend add <name> - Добавить друга по имени.",
                prefix.get() + "friend delete <name> - Удалить друга по имени.",
                prefix.get() + "friend list - Получить список друзей.",
                prefix.get() + "friend clear - Очистить список друзей.",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "friend add NullCry | ник друга"
        );
    }

    @Override
    public List<String> firstArguments(String subCommand) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return mc.player.networkHandler.getPlayerList().stream()
                    .map(playerInfo -> playerInfo.getProfile().name())
                    .filter(name -> !name.equalsIgnoreCase(mc.player.getName().getString()))
                    .toList();
    } else if ("delete".equalsIgnoreCase(subCommand)) {
            return LynxWave.getInstance().getFriendManager().getFriends().stream().toList();
        }
        return List.of();
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if (step == 0) {
            if ("add".equalsIgnoreCase(subCommand)) {
                return mc.player.networkHandler.getPlayerList().stream()
                        .map(playerInfo -> playerInfo.getProfile().name())
                        .filter(name -> !name.equalsIgnoreCase(mc.player.getName().getString()))
                        .toList();
            } else if ("delete".equalsIgnoreCase(subCommand)) {
                return LynxWave.getInstance().getFriendManager().getFriends().stream().toList();
            }
        }

        return List.of();
    }

    private void addFriend(Parameters parameters) {
        String friendName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите имя друга для добавления." + Formatting.RESET));

        if (!PlayerHelper.isNameValid(friendName)) {
            logger.log(
                    Formatting.RED + "Недопустимое имя: " +
                            Formatting.GRAY + "'" + friendName + "'" +
                            Formatting.RESET
            );
            return;
        }

        if (friendName.equalsIgnoreCase(mc.player.getName().getString())) {
            logger.log(
                    Formatting.RED + "Вы не можете добавить себя в список друзей." +
                            Formatting.RESET
            );
            return;
        }

        if (LynxWave.getInstance().getFriendManager().isFriend(friendName)) {
            logger.log(
                    Formatting.RED + "Игрок " +
                            Formatting.WHITE + "'" + friendName + "'" +
                            Formatting.RED + " уже находится в вашем списке друзей." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getFriendManager().add(friendName);
        logger.log(
                Formatting.GRAY + "Игрок " +
                        Formatting.WHITE + "'" + friendName + "'" +
                        Formatting.GRAY + " успешно добавлен в список друзей!" +
                        Formatting.RESET
        );
    }

    private void deleteFriend(Parameters parameters) {
        String friendName = parameters.asString(1)
                .orElseThrow(() -> new CommandException(
                        Formatting.RED + "Укажите имя друга для удаления." + Formatting.RESET
                ));

        if (!LynxWave.getInstance().getFriendManager().isFriend(friendName)) {
            logger.log(
                    Formatting.RED + "Игрок " +
                            Formatting.GRAY + "'" + friendName + "'" +
                            Formatting.RED + " не найден в списке друзей." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getFriendManager().delete(friendName);
        logger.log(
                Formatting.GRAY + "Игрок " +
                        Formatting.WHITE + "'" + friendName + "'" +
                        Formatting.GRAY + " был успешно " +
                        Formatting.GRAY + "удалён из друзей." +
                        Formatting.RESET
        );
    }

    private void getFriendList() {
        Set<String> friends = LynxWave.getInstance().getFriendManager().getFriends();

        if (friends.isEmpty()) {
            logger.log(Formatting.RED + "Список друзей пуст.");
            return;
        }

        logger.log(Text.literal("Список друзей:").formatted(Formatting.GRAY));

        String prefixValue = prefix.get();
        friends.forEach(friend -> {
            MutableText line = Text.literal(friend).formatted(Formatting.WHITE);
            line.append(CommandTextUtil.bracketedButton(
                    "Скопировать",
                    Formatting.AQUA,
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    friend,
                    "§7Скопировать ник в буфер"
            ));
            line.append(CommandTextUtil.bracketedButton(
                    "Удалить",
                    Formatting.RED,
                    ClickEvent.Action.SUGGEST_COMMAND,
                    prefixValue + "friend delete " + friend,
                    "§cУдалить игрока из друзей"
            ));
            logger.log(line);
        });
    }

    private void clearFriendList() {
        if (LynxWave.getInstance().getFriendManager().getFriends().isEmpty()) {
            logger.log(Formatting.RED + "Список друзей уже пуст.");
            return;
        }

        LynxWave.getInstance().getFriendManager().clear();
        logger.log(Formatting.GREEN + "Список друзей успешно очищен.");
    }
}
