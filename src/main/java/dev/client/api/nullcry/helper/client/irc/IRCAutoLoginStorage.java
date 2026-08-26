package dev.client.api.nullcry.helper.client.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

public final class IRCAutoLoginStorage {
    private static final IRCAutoLoginStorage INSTANCE = new IRCAutoLoginStorage();
    private static final String FILE_NAME = "irc_autologin.file";

    private Credentials cachedCredentials;

    private IRCAutoLoginStorage() {
    }

    public static IRCAutoLoginStorage getInstance() {
        return INSTANCE;
    }

    private File resolveFile() {
        return new File(LynxWave.getInstance().getFilesDir(), FILE_NAME);
    }

    public synchronized Optional<Credentials> loadCredentials() {
        if (cachedCredentials != null) {
            return Optional.of(cachedCredentials);
        }

        File file = resolveFile();
        if (!file.exists()) {
            return Optional.empty();
        }

        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }

            if (LynxWave.getInstance().cryptEnabled()) {
                raw = AESEncryptor.decrypt(raw);
            }

            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            String username = json.has("username") ? json.get("username").getAsString() : null;
            String password = json.has("password") ? json.get("password").getAsString() : null;

            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                return Optional.empty();
            }

            cachedCredentials = new Credentials(username.trim(), password);
            return Optional.of(cachedCredentials);
        } catch (Exception e) {
            Console.logManager("IRC -> Ошибка чтения автологина IRC", e);
            return Optional.empty();
        }
    }

    public synchronized void saveCredentials(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            clear();
            return;
        }

        File file = resolveFile();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("username", username.trim());
            json.addProperty("password", password);

            String raw = json.toString();
            if (LynxWave.getInstance().cryptEnabled()) {
                raw = AESEncryptor.encrypt(raw);
            }

            Files.writeString(file.toPath(), raw, StandardCharsets.UTF_8);
            cachedCredentials = new Credentials(username.trim(), password);
        } catch (IOException e) {
            Console.logManager("IRC -> Ошибка сохранения автологина IRC", e);
        }
    }

    public synchronized void clear() {
        cachedCredentials = null;
        File file = resolveFile();
        if (file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                Console.logManager("IRC -> Ошибка удаления файла автологина IRC", e);
            }
        }
    }

    public record Credentials(String username, String password) {}
}
