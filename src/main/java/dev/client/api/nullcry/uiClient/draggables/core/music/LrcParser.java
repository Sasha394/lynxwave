package dev.client.api.nullcry.uiClient.draggables.core.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.(\\d{2,3})](.*)");

    private LrcParser() {
    }

    public static List<LyricLine> parse(String content) {
        if (content == null || content.isBlank()) return List.of();

        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            Matcher matcher = TIMESTAMP.matcher(rawLine.trim());
            if (!matcher.matches()) continue;

            try {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = Long.parseLong(fraction) * (fraction.length() == 2 ? 10L : 1L);
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    lines.add(new LyricLine(minutes * 60_000L + seconds * 1_000L + millis, text));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Collections.sort(lines);
        return List.copyOf(lines);
    }
}
