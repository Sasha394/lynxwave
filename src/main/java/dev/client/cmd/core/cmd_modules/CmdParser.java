package dev.client.cmd.core.cmd_modules;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Formatting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdParser implements Command, CommandWithAdvice {
    final MinecraftClient mc;
    final Prefix prefix;
    final Logger logger;

    @Override
    public List<String> parametersCommand() {
        return List.of("start", "dir", "list", "clear");
    }

    @Override
    public void execute(Parameters parameters) {
        String subCommand = parameters.asString(0).orElse("").toLowerCase(Locale.ROOT);

        String serverIP = ConnectionHelper.getServerIP();
        if (serverIP == null || serverIP.isEmpty()) {
            throw new CommandException(Formatting.RED + "Вы не подключены к серверу.");
        }

        File parserDir = new File(LynxWave.getInstance().getFilesDir(), "parser");
        if (!parserDir.exists() && !parserDir.mkdirs()) {
            throw new CommandException(Formatting.RED + "Не удалось создать папку parser.");
        }

        File file = new File(parserDir, serverIP + ".txt");

        switch (subCommand) {
            case "start" -> parseAndSave(file);
            case "dir" -> openExplorer(file.getParentFile());
            case "list" -> logger.log(Formatting.GRAY + "Файл: " + Formatting.WHITE + file.getName());
            case "clear" -> clearParserDirectory(parserDir);
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "start, dir, list, clear");
        }
    }

    @Override
    public String name() {
        return "parser";
    }

    @Override
    public String description() {
        return "Парсит никнеймы игроков с донатом";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                Formatting.GRAY + prefix.get() + "parser start" + " — начать парсинг",
                Formatting.GRAY + prefix.get() + "parser dir" + " — открыть папку",
                Formatting.GRAY + prefix.get() + "parser list" + " — список файлов",
                Formatting.GRAY + prefix.get() + "parser clear" + " — удаляет все файлы",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "parser start"
        );
    }

    private void parseAndSave(File file) {
        if (mc.world == null || mc.player == null) {
            logger.log(Formatting.RED + "Мир не загружен.");
            return;
        }

        Collection<PlayerListEntry> players = mc.getNetworkHandler().getPlayerList();
        List<String> lines = new ArrayList<>();

        for (PlayerListEntry info : players) {
            String displayName = info.getDisplayName() != null
                    ? info.getDisplayName().getString()
                    : info.getProfile().name();

            String[] parts = displayName.trim().split(" ", 2);
            if (parts.length >= 2) {
                String prefix = formatPrefix(parts[0]);
                String nickname = parts[1];
                lines.add(Formatting.AQUA + prefix + Formatting.GRAY + " - " + Formatting.WHITE + nickname);
            }
        }

        if (lines.isEmpty()) {
            logger.log(Formatting.RED + "Не найдено игроков с префиксами.");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(Formatting.strip(line));
                writer.newLine();
            }
            logger.log(Formatting.GREEN + "Сохранено в файл: " + Formatting.GRAY + file.getName());
            openExplorer(file);
        } catch (IOException e) {
            logger.log(Formatting.RED + "Ошибка при записи файла: " + Formatting.GRAY + e.getMessage());
        }
    }

    private void clearParserDirectory(File parserDir) {
        File[] files = parserDir.listFiles();
        if (files == null || files.length == 0) {
            logger.log(Formatting.YELLOW + "Файлы для удаления не найдены.");
            return;
        }

        int deletedCount = 0;
        for (File file : files) {
            if (file.isFile() && file.delete()) {
                deletedCount++;
            }
        }

        logger.log(Formatting.GRAY + "Удалено файлов: " + Formatting.WHITE + deletedCount);
    }

    private String formatPrefix(String prefix) {
        String clean = Formatting.strip(prefix);
        if (clean == null || clean.isEmpty()) return "Unknown";
        return clean.substring(0, 1).toUpperCase() + clean.substring(1).toLowerCase();
    }

    private void openExplorer(File fileOrDir) {
        try {
            Runtime.getRuntime().exec("explorer " + fileOrDir.getAbsolutePath());
        } catch (IOException e) {
            logger.log(Formatting.RED + "Не удалось открыть проводник: " + Formatting.GRAY + e.getMessage());
        }
    }
}
