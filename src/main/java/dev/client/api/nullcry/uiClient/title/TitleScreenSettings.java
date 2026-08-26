package dev.client.api.nullcry.uiClient.title;

/** Настройки главного меню (задний фон). */
public final class TitleScreenSettings {
    private static volatile String backgroundPath = "images/title.png";

    public static String getBackgroundPath() {
        return backgroundPath;
    }

    public static void setBackgroundPath(String path) {
        if (path != null && !path.isBlank()) backgroundPath = path.trim();
    }
}
