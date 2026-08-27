package dev.client.api.nullcry.uiClient.draggables.core.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricLineSplitter {
    private LyricLineSplitter() {
    }

    public static List<LyricLine> splitLongLines(List<LyricLine> source) {
        if (source == null || source.isEmpty()) return List.of();

        List<LyricLine> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            LyricLine line = source.get(i);
            String[] words = line.text().trim().split("\\s+");
            if (words.length <= 4) {
                result.add(line);
                continue;
            }

            int wordsPerChunk = words.length > 7 ? 3 : 2;
            List<String> chunks = new ArrayList<>();
            for (int word = 0; word < words.length; word += wordsPerChunk) {
                int end = Math.min(words.length, word + wordsPerChunk);
                String chunk = String.join(" ", java.util.Arrays.copyOfRange(words, word, end));
                if (end == words.length && end - word == 1 && !chunks.isEmpty()) {
                    int last = chunks.size() - 1;
                    chunks.set(last, chunks.get(last) + " " + chunk);
                } else {
                    chunks.add(chunk);
                }
            }

            long nextTimestamp = i + 1 < source.size()
                    ? source.get(i + 1).timestampMs()
                    : line.timestampMs() + 4_000L;
            long lineDuration = Math.max(1_500L, nextTimestamp - line.timestampMs());
            long chunkDuration = lineDuration / chunks.size();
            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                result.add(new LyricLine(line.timestampMs() + chunk * chunkDuration, chunks.get(chunk)));
            }
        }

        Collections.sort(result);
        return List.copyOf(result);
    }
}
