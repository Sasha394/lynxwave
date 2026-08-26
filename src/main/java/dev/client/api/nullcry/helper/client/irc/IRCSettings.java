package dev.client.api.nullcry.helper.client.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.other.Console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class IRCSettings {
    private static final IRCSettings INSTANCE = new IRCSettings();
    private static final String FILE_NAME = "irc_settings.json";

    public static IRCSettings get() {
        return INSTANCE;
    }

    // Notifications
    private boolean notificationsEnabled = true;
    private boolean notifyMentionsOnly = false;
    private boolean notifyDirectMessages = true;
    private boolean notifyOnlyWhenMinimized = true;

    // Chat / UI
    private boolean showTimestamps = true;
    private boolean compactMode = false;
    private boolean showClientPrefix = true;

    // Presence
    private boolean hideMcServer = false;
    private boolean invisibleMode = false;

    // Auth
    private boolean autoLogin = true;

    private boolean loaded = false;

    private IRCSettings() {}

    public synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = resolveFile();
        if (!file.exists()) return;
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) return;
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            notificationsEnabled = readBool(json, "notificationsEnabled", notificationsEnabled);
            notifyMentionsOnly = readBool(json, "notifyMentionsOnly", notifyMentionsOnly);
            notifyDirectMessages = readBool(json, "notifyDirectMessages", notifyDirectMessages);
            notifyOnlyWhenMinimized = readBool(json, "notifyOnlyWhenMinimized", notifyOnlyWhenMinimized);
            showTimestamps = readBool(json, "showTimestamps", showTimestamps);
            compactMode = readBool(json, "compactMode", compactMode);
            showClientPrefix = readBool(json, "showClientPrefix", showClientPrefix);
            hideMcServer = readBool(json, "hideMcServer", hideMcServer);
            invisibleMode = readBool(json, "invisibleMode", invisibleMode);
            autoLogin = readBool(json, "autoLogin", autoLogin);
        } catch (Exception e) {
            Console.logManager("IRC -> Ошибка чтения настроек", e);
        }
    }

    public synchronized void save() {
        File file = resolveFile();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("notificationsEnabled", notificationsEnabled);
            json.addProperty("notifyMentionsOnly", notifyMentionsOnly);
            json.addProperty("notifyDirectMessages", notifyDirectMessages);
            json.addProperty("notifyOnlyWhenMinimized", notifyOnlyWhenMinimized);
            json.addProperty("showTimestamps", showTimestamps);
            json.addProperty("compactMode", compactMode);
            json.addProperty("showClientPrefix", showClientPrefix);
            json.addProperty("hideMcServer", hideMcServer);
            json.addProperty("invisibleMode", invisibleMode);
            json.addProperty("autoLogin", autoLogin);
            Files.writeString(file.toPath(), json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Console.logManager("IRC -> Ошибка сохранения настроек", e);
        }
    }

    private File resolveFile() {
        return new File(LynxWave.getInstance().getFilesDir(), FILE_NAME);
    }

    private static boolean readBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    // ----- getters / setters -----

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v) { notificationsEnabled = v; save(); }

    public boolean isNotifyMentionsOnly() { return notifyMentionsOnly; }
    public void setNotifyMentionsOnly(boolean v) { notifyMentionsOnly = v; save(); }

    public boolean isNotifyDirectMessages() { return notifyDirectMessages; }
    public void setNotifyDirectMessages(boolean v) { notifyDirectMessages = v; save(); }

    public boolean isNotifyOnlyWhenMinimized() { return notifyOnlyWhenMinimized; }
    public void setNotifyOnlyWhenMinimized(boolean v) { notifyOnlyWhenMinimized = v; save(); }

    public boolean isShowTimestamps() { return showTimestamps; }
    public void setShowTimestamps(boolean v) { showTimestamps = v; save(); }

    public boolean isCompactMode() { return compactMode; }
    public void setCompactMode(boolean v) { compactMode = v; save(); }

    public boolean isShowClientPrefix() { return showClientPrefix; }
    public void setShowClientPrefix(boolean v) { showClientPrefix = v; save(); }

    public boolean isHideMcServer() { return hideMcServer; }
    public void setHideMcServer(boolean v) { hideMcServer = v; save(); }

    public boolean isInvisibleMode() { return invisibleMode; }
    public void setInvisibleMode(boolean v) { invisibleMode = v; save(); }

    public boolean isAutoLogin() { return autoLogin; }
    public void setAutoLogin(boolean v) { autoLogin = v; save(); }
}
