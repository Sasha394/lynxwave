package dev.client.api.nullcry.uiClient.clickGui.components.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Ранжирование результатов поиска по модулям и настройкам.
 * <p>
 * Оценка — «штраф»: чем меньше, тем точнее совпадение, {@link #NO_MATCH} — не подходит.
 * Уровни идут от точного совпадения к всё более приблизительным: полное совпадение,
 * совпадение без пробелов, префикс, начало слова, подстрока, все слова запроса вразнобой
 * и, наконец, расстояние Дамерау — Левенштейна для опечаток.
 */
public final class SearchRanker {

    public static final int NO_MATCH = Integer.MAX_VALUE;

    /** Раскладки для запросов, набранных не на том языке. */
    private static final String RU_LAYOUT = "йцукенгшщзхъфывапролджэячсмитьбю.";
    private static final String EN_LAYOUT = "qwertyuiop[]asdfghjkl;'zxcvbnm,./";

    private SearchRanker() {
    }

    /** Нижний регистр, снятые диакритики, всё лишнее схлопнуто в одиночные пробелы. */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";

        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(decomposed.length());
        boolean pendingSeparator = false;

        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;

            if (Character.isLetterOrDigit(c)) {
                if (pendingSeparator && !result.isEmpty()) result.append(' ');
                result.append(c);
                pendingSeparator = false;
            } else {
                pendingSeparator = true;
            }
        }

        return result.toString();
    }

    /** Перекладывает строку между ЙЦУКЕН и QWERTY, чтобы «rbkkfehf» находило «KillAura». */
    public static String swapLayout(String input) {
        if (input == null || input.isEmpty()) return "";

        StringBuilder result = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            int ru = RU_LAYOUT.indexOf(c);
            if (ru >= 0) {
                result.append(EN_LAYOUT.charAt(ru));
                continue;
            }

            int en = EN_LAYOUT.indexOf(c);
            result.append(en >= 0 ? RU_LAYOUT.charAt(en) : c);
        }

        return result.toString();
    }

    /**
     * Оценка кандидата для запроса, набранного в любой из двух раскладок.
     *
     * @param candidate       сырой текст (имя модуля или настройки)
     * @param query           нормализованный запрос
     * @param swappedQuery    нормализованный запрос с перевёрнутой раскладкой
     */
    public static int rank(String candidate, String query, String swappedQuery) {
        String normalized = normalize(candidate);
        return Math.min(score(normalized, query), score(normalized, swappedQuery));
    }

    /** Штраф за совпадение двух уже нормализованных строк. */
    public static int score(String candidate, String query) {
        if (candidate.isEmpty() || query.isEmpty()) return NO_MATCH;
        if (candidate.equals(query)) return 0;

        String candidateTight = withoutSpaces(candidate);
        String queryTight = withoutSpaces(query);
        if (candidateTight.equals(queryTight)) return 10;

        if (candidate.startsWith(query)) return 20 + candidate.length() - query.length();
        if (candidateTight.startsWith(queryTight)) return 40 + candidateTight.length() - queryTight.length();

        int wordStart = indexAtWordStart(candidate, query);
        if (wordStart >= 0) return 80 + wordStart;

        int inside = candidate.indexOf(query);
        if (inside >= 0) return 120 + inside;

        int insideTight = candidateTight.indexOf(queryTight);
        if (insideTight >= 0) return 160 + insideTight;

        int scattered = allWordsPresent(candidate, query);
        if (scattered != NO_MATCH) return 220 + scattered;

        // Последний шанс — опечатка. Длинным запросам прощаем больше.
        int lengthDelta = Math.abs(candidateTight.length() - queryTight.length());
        int allowed = queryTight.length() >= 8 ? 2 : (queryTight.length() >= 4 ? 1 : 0);
        if (allowed == 0 || lengthDelta > allowed) return NO_MATCH;

        int distance = damerauLevenshtein(candidateTight, queryTight);
        return distance <= allowed ? 300 + distance * 10 + lengthDelta : NO_MATCH;
    }

    private static String withoutSpaces(String value) {
        return value.indexOf(' ') < 0 ? value : value.replace(" ", "");
    }

    /** Позиция запроса, если он начинается на границе слова. */
    private static int indexAtWordStart(String candidate, String query) {
        int index = candidate.indexOf(query);
        while (index >= 0) {
            if (index == 0 || candidate.charAt(index - 1) == ' ') return index;
            index = candidate.indexOf(query, index + 1);
        }
        return -1;
    }

    /** Сумма позиций всех слов запроса, если каждое встречается в кандидате. */
    private static int allWordsPresent(String candidate, String query) {
        int total = 0;
        int cursor = 0;

        while (cursor < query.length()) {
            int space = query.indexOf(' ', cursor);
            if (space < 0) space = query.length();

            String word = query.substring(cursor, space);
            int position = candidate.indexOf(word);
            if (position < 0) return NO_MATCH;

            total += position;
            cursor = space + 1;
        }

        return total;
    }

    /** Расстояние Дамерау — Левенштейна: правки плюс перестановки соседних букв. */
    private static int damerauLevenshtein(String from, String to) {
        int[] beforePrevious = null;
        int[] previous = new int[to.length() + 1];
        int[] current = new int[to.length() + 1];

        for (int j = 0; j <= to.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= from.length(); i++) {
            current[0] = i;

            for (int j = 1; j <= to.length(); j++) {
                int substitution = previous[j - 1] + (from.charAt(i - 1) == to.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);

                boolean transposed = beforePrevious != null
                        && j > 1
                        && from.charAt(i - 1) == to.charAt(j - 2)
                        && from.charAt(i - 2) == to.charAt(j - 1);
                if (transposed) {
                    current[j] = Math.min(current[j], beforePrevious[j - 2] + 1);
                }
            }

            int[] recycled = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = recycled == null ? new int[to.length() + 1] : recycled;
        }

        return previous[to.length()];
    }
}
