package dev.client.api.nullcry.uiClient.draggables.core.music;

public record LyricLine(long timestampMs, String text) implements Comparable<LyricLine> {
    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(timestampMs, other.timestampMs);
    }
}
