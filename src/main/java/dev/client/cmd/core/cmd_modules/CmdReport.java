package dev.client.cmd.core.cmd_modules;

import de.jcm.discordgamesdk.user.DiscordUser;
import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.CommandException;
import dev.client.api.nullcry.cmdHelper.interfaces.*;
import dev.client.api.nullcry.helper.other.TimerUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CmdReport implements Command, CommandWithAdvice {
    final Prefix prefix;
    final Logger logger;

    static final Path COOLDOWN_FILE = Paths.get(System.getProperty("java.io.tmpdir"), "lynxwave_report_cooldowns.tmp");
    static final Map<String, Long> persistedCooldowns = new HashMap<>();

    static final TimerUtil addCooldownTimer = new TimerUtil();
    static final TimerUtil fixCooldownTimer = new TimerUtil();
    static final long COOLDOWN_MILLIS = 5 * 60 * 1000;

    // ахуеть у меня словарный запас
    static final List<String> BAN = List.of(
            "нахуй", "пошел нахуй", "иди нахуй", "хуй", "еблан", "мудак", "соси", "ебать", "пидор", "сука"
    );

    private boolean containsBadWords(String text) {
        String lower = text.toLowerCase();
        return BAN.stream().anyMatch(lower::contains);
    }

    static {
        loadCooldowns();
    }

    @Override
    public void execute(Parameters parameters) {
        String commandType = parameters.asString(0).orElse("").toLowerCase();

        switch (commandType) {
            case "add" -> addReport(parameters, logger);
            case "fix" -> fixReport(parameters, logger);
            default ->
                    throw new CommandException(Formatting.GRAY + "Укажите тип команды: " + Formatting.WHITE + "add, fix");
        }
    }

    @Override
    public List<String> parametersCommand() {
        return List.of("add", "fix");
    }

    @Override
    public String name() {
        return "report";
    }

    @Override
    public String description() {
        return "Отправляет баг или предложение разработчику.";
    }

    @Override
    public List<String> adviceMessage() {
        return List.of(
                prefix.get() + "report fix <описание> - Сообщить об ошибке",
                prefix.get() + "report add <описание> - Предложить улучшение",
                Formatting.GRAY + "Пример: " + Formatting.WHITE + prefix.get() + "report fix не работает esp"
        );
    }

    private void addReport(Parameters parameters, Logger logger) {
        if (!checkCooldown(addCooldownTimer, "add", "предложением", logger)) return;

        String description = parameters.collectMessage(1).trim();
        if (description.isEmpty()) {
            logger.log(Formatting.RED + "Вы не указали, что добавить.");
            return;
        }

        if (containsBadWords(description)) {
            logger.log(Formatting.WHITE + "Сообщение содержит" + Formatting.RED + "запрещённые слова.");
            return;
        }

        boolean success = sendToDiscord("Предложение", description);
        if (success) {
            logger.log(Formatting.GREEN + "Предложение успешно отправлено.");
            addCooldownTimer.reset();
        } else {
            logger.log(Formatting.RED + "Discord offline. Включи для отправки уведомления.");
        }
    }

    private void fixReport(Parameters parameters, Logger logger) {
        if (!checkCooldown(fixCooldownTimer, "fix", "репортом", logger)) return;

        String description = parameters.collectMessage(1).trim();
        if (description.isEmpty()) {
            logger.log(Formatting.RED + "Вы не указали описание проблемы.");
            return;
        }

        if (containsBadWords(description)) {
            logger.log(Formatting.WHITE + "Сообщение содержит" + Formatting.RED + "запрещённые слова.");
            return;
        }

        boolean success = sendToDiscord("Пофиксить", description);
        if (success) {
            logger.log(Formatting.GREEN + "Репорт об ошибке успешно отправлен.");
            fixCooldownTimer.reset();
        } else {
            logger.log(Formatting.RED + "Discord offline. Включи для отправки уведомления.");
        }
    }

    private static void loadCooldowns() {
        if (!COOLDOWN_FILE.toFile().exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(COOLDOWN_FILE.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    persistedCooldowns.put(parts[0], Long.parseLong(parts[1]));
                }
            }
            if (persistedCooldowns.containsKey("add")) {
                addCooldownTimer.setTime(persistedCooldowns.get("add"));
            }
            if (persistedCooldowns.containsKey("fix")) {
                fixCooldownTimer.setTime(persistedCooldowns.get("fix"));
            }
        } catch (IOException ignored) {}
    }

    private static void saveCooldown(String key, long timestamp) {
        persistedCooldowns.put(key, timestamp);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(COOLDOWN_FILE.toFile()))) {
            for (Map.Entry<String, Long> entry : persistedCooldowns.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException ignored) {}
    }

    private boolean checkCooldown(TimerUtil timer, String key, String label, Logger logger) {
        long lastTime = persistedCooldowns.getOrDefault(key, 0L);
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTime;

        if (elapsed < COOLDOWN_MILLIS) {
            long remaining = COOLDOWN_MILLIS - elapsed;
            long minutes = remaining / 1000 / 60;
            long seconds = (remaining / 1000) % 60;
            logger.log(Formatting.RED + "Подождите " + minutes + "м " + seconds + "с перед следующим " + label + ".");
            return false;
        }

        saveCooldown(key, currentTime);
        timer.reset();
        return true;
    }

    private boolean sendToDiscord(String title, String problemText) {
        try {
            URL url = new URL("https://discord.com/api/webhooks/1460546499732574356/r2OGnfhVSYIVMMN496jISUMTJNV28tmBpGwgSG5OMTRLLfIUMO_6dBHOG2FzUh5m3HoB");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            LynxWave lynxWave = LynxWave.getInstance();
            var discord = lynxWave.getDiscordManager();
           // DiscordUser user = waitForDiscordUser(discord, 20, 150);

            //String discordName = buildDiscordName(user);
            String version = LynxWave.getInstance().getClientInfo().getVersion();

            String payload = "{\n" +
                    "  \"embeds\": [\n" +
                    "    {\n" +
                    "      \"title\": \"" + escape("Новый репорт: " + title) + "\",\n" +
                    "      \"color\": 13369344,\n" +
                    "      \"description\": \"" + escape(problemText) + "\",\n" +
                    "      \"fields\": [\n" +
                    "        {\n" +
                    "          \"name\": \"Пользователь\",\n" +
         //           "        //  \"value\": \"" + escape(discordName) + "\",\n" +
                    "          \"inline\": true\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"name\": \"Версия клиента\",\n" +
                    "          \"value\": \"" + escape(version) + "\",\n" +
                    "          \"inline\": true\n" +
                    "        }\n" +
                    "      ],\n" +
                    "      \"footer\": {\n" +
                    "        \"text\": \"LynxWave Report System\"\n" +
                    "      },\n" +
                    "      \"timestamp\": \"" + Instant.now() + "\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(out);

            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
