package dev.client.api.nullcry.uiClient.clickGui.components.search;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;

/**
 * Куда перейти после выбора строки в поиске: модуль раскрывается в панели, а
 * найденная настройка подсвечивается. Подсветка держится 1.6 с и гаснет за
 * последние 400 мс — как в панели, откуда перенесён поиск.
 */
public final class SearchSpotlight {

    private static final long DURATION_MS = 1600L;
    private static final long FADE_MS = 400L;

    private static Module module;
    private static Setting setting;
    private static long expiresAt;

    private SearchSpotlight() {
    }

    /** @param setting настройка для подсветки или {@code null}, если открывали сам модуль */
    public static void focus(Module module, Setting setting) {
        SearchSpotlight.module = module;
        SearchSpotlight.setting = setting;
        SearchSpotlight.expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    public static void clear() {
        module = null;
        setting = null;
        expiresAt = 0L;
    }

    /** Нужно ли раскрыть настройки этого модуля — искали именно настройку. */
    public static boolean shouldOpenSettings(Module candidate) {
        return active() && module == candidate && setting != null;
    }

    public static boolean highlightsModule(Module candidate) {
        return active() && module == candidate && setting == null;
    }

    public static boolean highlightsSetting(Setting candidate) {
        return active() && setting != null && setting == candidate;
    }

    /** Плавно гаснет к концу окна подсветки. */
    public static float alpha() {
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) return 0f;
        return Math.min(1f, remaining / (float) FADE_MS);
    }

    private static boolean active() {
        return module != null && System.currentTimeMillis() < expiresAt;
    }
}
