package dev.client.api.nullcry.uiClient.clickGui.components.search;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;

/**
 * Оценка модуля для строки поиска: сначала по названию, затем по названиям его настроек.
 */
public final class ModuleSearch {

    /** Совпадение по настройке всегда уступает совпадению по имени модуля. */
    private static final int SETTING_PENALTY = 1000;

    private ModuleSearch() {
    }

    /**
     * @param query        нормализованный запрос
     * @param swappedQuery нормализованный запрос с перевёрнутой раскладкой
     * @return штраф модуля; {@link SearchRanker#NO_MATCH}, если не подходит
     */
    public static int rank(Module module, String query, String swappedQuery) {
        int best = SearchRanker.rank(module.getName(), query, swappedQuery);

        for (Setting setting : module.getSettings()) {
            if (!isVisible(setting)) continue;

            int score = SearchRanker.rank(setting.getName(), query, swappedQuery);
            if (score == SearchRanker.NO_MATCH) continue;

            best = Math.min(best, SETTING_PENALTY + score);
        }

        return best;
    }

    /** Скрытые настройки в выдачу не попадают — их всё равно не видно в панели. */
    public static boolean isVisible(Setting setting) {
        if (setting == null || setting.getName() == null) return false;
        if (setting.getShown() == null) return true;

        try {
            return Boolean.TRUE.equals(setting.getShown().get());
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
