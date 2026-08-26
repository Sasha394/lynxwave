package dev.client.cmd.core.cmd_modules;

import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.cmd.core.util.CommandTextUtil;
import dev.client.modules.core.misc.Xray;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdXray implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;
    final Xray xray = Xray.INSTANCE;

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "delete", "list", "clear");
    }

    @Override
    public void execute(Parameters parameters) {
        String type = parameters.asString(0).orElseThrow();

        switch (type) {
            case "add" -> addBlock(parameters);
            case "delete" -> deleteBlock(parameters);
            case "list" -> listBlocks();
            case "clear" -> clearBlocks();
            default -> throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, delete, list, clear");
        }
    }

    @Override
    public String name() {
        return "xray";
    }

    @Override
    public String description() {
        return "Управление Xray-блоками";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "xray add <block_id> - Добавить новый блок в Xray",
                prefix.get() + "xray delete <block_id> - Удалить выбранный блок из списка Xray",
                prefix.get() + "xray list - Получить список блоков в Xray",
                prefix.get() + "xray clear - Очистить список блоков в Xray",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "xray add diamond_ore"
        );
    }


    @Override
    public List<String> firstArguments(String subCommand) {
        return switch (subCommand.toLowerCase()) {
            case "add" -> Registries.BLOCK.getIds().stream()
                    .map(Identifier::toString)
                    .collect(Collectors.toList());
            case "delete" -> xray.getCustomXrayBlocks().stream()
                    .map(block -> Registries.BLOCK.getId(block).toString())
                    .collect(Collectors.toList());
            default -> List.of();
        };
    }

    @Override
    public List<String> getArguments(String subCommand, int step, List<String> previousArgs) {
        if ("add".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> Registries.BLOCK.getIds().stream()
                        .map(Identifier::toString)
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if ("delete".equalsIgnoreCase(subCommand)) {
            return switch (step) {
                case 0 -> xray.getCustomXrayBlocks().stream()
                        .map(Registries.BLOCK::getId)
                        .map(Identifier::toString)
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        return List.of();
    }

    private void addBlock(Parameters parameters) {
        String blockName = parameters.asString(1).orElse(null);

        if (blockName == null) {
            logger.log(Formatting.GRAY + "Введите: .xray add <block_id>");
            return;
        }

        Identifier id = Identifier.tryParse(blockName);
        if (id == null || !Registries.BLOCK.getIds().contains(id)) {
            throw new CommandException(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.RED + " не найден.");
        }

        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR) {
            throw new CommandException(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.RED + " не найден.");
        }

        if (xray.getCustomXrayBlocks().contains(block)) {
            logger.log(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.RED + " уже есть в Xray.");
            return;
        }

        xray.addXrayBlock(block);
        logger.log(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.GRAY + " добавлен в Xray.");
    }

    private void deleteBlock(Parameters parameters) {
        String blockName = parameters.asString(1).orElse(null);

        if (blockName == null) {
            logger.log(Formatting.GRAY + "Введите: .xray delete <block_id>");
            return;
        }

        Identifier id = Identifier.tryParse(blockName);
        if (id == null || !Registries.BLOCK.getIds().contains(id)) {
            logger.log(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.RED + " не найден в списке Xray.");
            return;
        }

        Block block = Registries.BLOCK.get(id);
        if (!xray.getCustomXrayBlocks().contains(block)) {
            logger.log(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.RED + " не найден в списке Xray.");
            return;
        }

        xray.removeXrayBlock(block);
        logger.log(Formatting.GRAY + "Блок " + Formatting.WHITE + blockName + Formatting.GRAY + " удалён из Xray.");
    }

    private void listBlocks() {
        if (xray.getCustomXrayBlocks().isEmpty()) {
            logger.log(Formatting.RED + "Список Xray пуст.");
            return;
        }

        logger.log(Text.literal("Список блоков Xray:").formatted(Formatting.GRAY));
        logger.log(Text.literal("Нажмите на кнопки, чтобы управлять блоками.").formatted(Formatting.DARK_GRAY));

        String prefixValue = prefix.get();
        xray.getCustomXrayBlocks().forEach(block -> {
            String name = Registries.BLOCK.getId(block).toString();
            MutableText line = Text.literal("- ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(name).formatted(Formatting.WHITE));

            line.append(CommandTextUtil.bracketedButton(
                    "Скопировать",
                    Formatting.AQUA,
                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                    name,
                    "§7Скопировать идентификатор блока"
            ));
            line.append(CommandTextUtil.bracketedButton(
                    "Удалить",
                    Formatting.RED,
                    ClickEvent.Action.SUGGEST_COMMAND,
                    prefixValue + "xray delete " + name,
                    "§cУдалить блок из Xray"
            ));

            logger.log(line);
        });
    }

    private void clearBlocks() {
        if (xray.getCustomXrayBlocks().isEmpty()) {
            logger.log(Formatting.RED + "Список Xray уже пуст.");
            return;
        }

        xray.getCustomXrayBlocks().clear();
        logger.log(Formatting.GRAY + "Все блоки были успешно" + Formatting.WHITE + " удалены.");
    }
}
