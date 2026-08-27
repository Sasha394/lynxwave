package dev.client.api.nullcry.uiClient.draggables.core.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LrcLibClient {
    private static final String API_GET = "https://lrclib.net/api/get";
    private static final String API_SEARCH = "https://lrclib.net/api/search";
    private static final Map<String, List<LyricLine>> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrcLibClient() {
    }

    public static CompletableFuture<List<LyricLine>> fetchLyricsAsync(String title, String artist) {
        if (title == null || title.isBlank()) return CompletableFuture.completedFuture(List.of());

        String key = cacheKey(title, artist);
        List<LyricLine> cached = CACHE.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            List<LyricLine> lines = fetchExact(title, artist);
            if (lines.isEmpty()) lines = fetchSearch(title, artist);
            if (!lines.isEmpty()) CACHE.put(key, lines);
            return lines;
        });
    }

    private static List<LyricLine> fetchExact(String title, String artist) {
        String query = "?track_name=" + encode(cleanTitle(title)) + "&artist_name=" + encode(safe(artist));
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonObject json = JsonParser.parseString(send(API_GET + query)).getAsJsonObject();
                List<LyricLine> result = parseSyncedLyrics(json);
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private static List<LyricLine> fetchSearch(String title, String artist) {
        try {
            String query = "?q=" + encode((cleanTitle(title) + " " + safe(artist)).trim());
            JsonArray results = JsonParser.parseString(send(API_SEARCH + query)).getAsJsonArray();
            for (JsonElement result : results) {
                if (!result.isJsonObject()) continue;
                List<LyricLine> lines = parseSyncedLyrics(result.getAsJsonObject());
                if (!lines.isEmpty()) return lines;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private static String send(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "LynxWave/1.6")
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("LRCLIB returned " + response.statusCode());
        return response.body();
    }

    private static List<LyricLine> parseSyncedLyrics(JsonObject json) {
        if (!json.has("syncedLyrics") || json.get("syncedLyrics").isJsonNull()) return List.of();
        return LrcParser.parse(json.get("syncedLyrics").getAsString());
    }

    private static String cacheKey(String title, String artist) {
        return (title.trim() + " - " + safe(artist)).toLowerCase(Locale.ROOT);
    }

    private static String cleanTitle(String title) {
        return title.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?]", "").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
