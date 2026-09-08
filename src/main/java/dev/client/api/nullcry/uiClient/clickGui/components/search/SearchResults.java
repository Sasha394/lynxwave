package dev.client.api.nullcry.uiClient.clickGui.components.search;

import dev.client.LynxWave;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сборка выдачи поиска: модули и их настройки, отсортированные по точности совпадения.
 * <p>
 * Порядок ровно как в исходной панели, откуда перенесён поиск: если один модуль
 * совпал заметно точнее остальных — показываем его и все его настройки, иначе
 * собираем всё подходящее и сортируем по штрафу.
 */
public final class SearchResults {

    /** Насколько уверенным должно быть совпадение, чтобы показать один модуль целиком. */
    private static final int EXACT_ENOUGH = 10;

    /** Смещения рангов, чтобы строки разных видов не перемешивались. */
    private static final int SETTING_OF_EXACT_MODULE = 100;
    private static final int MATCHED_SETTING = 1000;
    private static final int SETTING_OF_MATCHED_MODULE = 2000;

    private SearchResults() {
    }

    public static List<SearchEntry> collect(String rawQuery) {
        List<Module> modules = LynxWave.getInstance().getModuleManager().getModules().stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        String query = SearchRanker.normalize(rawQuery);
        if (query.isEmpty()) {
            List<SearchEntry> all = new ArrayList<>(modules.size());
            for (Module module : modules) {
                all.add(new SearchEntry(module, null, module.getName(), 0));
            }
            return all;
        }

        String swapped = SearchRanker.normalize(SearchRanker.swapLayout(rawQuery));

        Module exact = null;
        int exactScore = Integer.MAX_VALUE;
        for (Module module : modules) {
            int score = SearchRanker.rank(module.getName(), query, swapped);
            if (score <= EXACT_ENOUGH && score < exactScore) {
                exact = module;
                exactScore = score;
            }
        }

        List<SearchEntry> results = new ArrayList<>();

        if (exact != null) {
            // Попали в модуль почти точно — показываем его и всё, что у него настраивается.
            results.add(new SearchEntry(exact, null, exact.getName(), exactScore));

            int index = 0;
            for (Setting setting : exact.getSettings()) {
                if (!ModuleSearch.isVisible(setting)) continue;
                results.add(new SearchEntry(exact, setting, setting.getName(), SETTING_OF_EXACT_MODULE + index++));
            }
            return results;
        }

        for (Module module : modules) {
            int moduleScore = SearchRanker.rank(module.getName(), query, swapped);
            boolean moduleMatched = moduleScore != SearchRanker.NO_MATCH;
            if (moduleMatched) {
                results.add(new SearchEntry(module, null, module.getName(), moduleScore));
            }

            int index = 0;
            for (Setting setting : module.getSettings()) {
                if (!ModuleSearch.isVisible(setting)) continue;

                int settingScore = SearchRanker.rank(setting.getName(), query, swapped);
                if (settingScore != SearchRanker.NO_MATCH) {
                    results.add(new SearchEntry(module, setting, setting.getName(), MATCHED_SETTING + settingScore));
                } else if (moduleMatched) {
                    // Модуль подошёл — показываем и его настройки, но ниже прямых совпадений.
                    results.add(new SearchEntry(module, setting, setting.getName(),
                            SETTING_OF_MATCHED_MODULE + moduleScore + index));
                }

                index++;
            }
        }

        results.sort(Comparator.comparingInt(SearchEntry::score)
                .thenComparing(entry -> entry.module().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SearchEntry::label, String.CASE_INSENSITIVE_ORDER));

        return results;
    }
}
