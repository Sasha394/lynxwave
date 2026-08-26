package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
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

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdStaffList implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "clear", "list");
    }

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElseThrow(() -> new CommandException(Formatting.RED
                + "Укажите тип команды:" + Formatting.GRAY + " add, delete, clear, list"));

        switch (commandType) {
            case "add" -> addStaffToList(parameters);
            case "delete" -> deleteStaffFromList(parameters);
            case "clear" -> clearStaffList();
            case "list" -> getStaffList();
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, clear, list");
        }
    }

    @Override
    public String name() {
        return "staff";
    }

    @Override
    public String description() {
        return "Позволяет управлять списком модераторов.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "staff add <name> ➜ Добавить ник в список модераторов",
                prefix.get() + "staff delete <name> ➜ Удалить ник из списка модераторов",
                prefix.get() + "staff list ➜ Получить список модераторов",
                prefix.get() + "staff clear ➜ Очистить список модераторов",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "staff add NullCry | Ник модератора"
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
            return LynxWave.getInstance().getStaffManager().getStaffs().stream().toList();
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
                return LynxWave.getInstance().getStaffManager().getStaffs().stream().toList();
            }
        }
        return List.of();
    }

    private void addStaffToList(Parameters parameters) {
        String staffName = parameters.asString(1)
                .orElseThrow(() -> new CommandException(
                        Formatting.RED + "Укажите имя модератора для добавления." + Formatting.RESET
                ));

        String realPlayerName = MinecraftClient.getInstance().player.getGameProfile().name();
        String displayedName = MinecraftClient.getInstance().player.getName().getString();

        if (staffName.equalsIgnoreCase(realPlayerName) || staffName.equalsIgnoreCase(displayedName)) {
            logger.log(
                    Formatting.RED + "Вы не можете добавить себя в список модераторов." +
                            Formatting.RESET
            );
            return;
        }

        if (LynxWave.getInstance().getStaffManager().isStaff(staffName)) {
            logger.log(
                    Formatting.RED + "Игрок " +
                            Formatting.WHITE + "'" + staffName + "'" +
                            Formatting.RED + " уже находится в списке модераторов." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getStaffManager().add(staffName);
        logger.log(
                Formatting.GRAY + "Игрок " +
                        Formatting.WHITE + "'" + staffName + "'" +
                        Formatting.GRAY + " был успешно добавлен в список модераторов." +
                        Formatting.RESET
        );
    }

    private void deleteStaffFromList(final Parameters parameters) {
        String staffName = parameters.asString(1).orElseThrow(() -> new CommandException(Formatting.RED + "Укажите имя модератора для удаления." + Formatting.RESET));

        if (!LynxWave.getInstance().getStaffManager().isStaff(staffName)) {
            logger.log(
                    Formatting.RED + "Модератор " +
                            Formatting.WHITE + "'" + staffName + "'" +
                            Formatting.RED + " не найден в списке." +
                            Formatting.RESET
            );
            return;
        }

        LynxWave.getInstance().getStaffManager().remove(staffName);
        logger.log(
                Formatting.GRAY + "Модератор " +
                        Formatting.WHITE + "'" + staffName + "'" +
                        Formatting.GRAY + " был успешно " +
                        Formatting.GRAY + "удалён из списка." +
                        Formatting.RESET
        );
    }

    private void getStaffList() {
        if (LynxWave.getInstance().getStaffManager().getStaffs().isEmpty()) {
            logger.log(Formatting.RED + "Список модераторов пуст.");
            return;
        }

        logger.log(Text.literal("Список модераторов:").formatted(Formatting.GRAY));

        String prefixValue = prefix.get();
        for (String staff : LynxWave.getInstance().getStaffManager().getStaffs()) {
            MutableText line = Text.literal(staff).formatted(Formatting.WHITE);
            line.append(CommandTextUtil.bracketedButton(
                    "Скопировать",
                    Formatting.AQUA,
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    staff,
                    "§7Скопировать ник модератора"
            ));
            line.append(CommandTextUtil.bracketedButton(
                    "Удалить",
                    Formatting.RED,
                    ClickEvent.Action.SUGGEST_COMMAND,
                    prefixValue + "staff delete " + staff,
                    "§cУдалить модератора из списка"
            ));
            logger.log(line);
        }
    }

    private void clearStaffList() {
        if (LynxWave.getInstance().getStaffManager().getStaffs().isEmpty()) {
            logger.log(Formatting.RED + "Список модераторов уже пуст.");
            return;
        }

        LynxWave.getInstance().getStaffManager().clear();
        logger.log(Formatting.GREEN + "Список модераторов был успешно очищен.");
    }
}
