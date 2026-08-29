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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Finds album art when the Windows media session does not provide it. */
public final class AlbumArtClient {
    private static final String SEARCH_API = "https://itunes.apple.com/search";
    private static final int MAX_ARTWORK_BYTES = 8 * 1024 * 1024;
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AlbumArtClient() {
    }

    public static CompletableFuture<byte[]> fetchArtworkAsync(String title, String artist) {
        if (title == null || title.isBlank()) return CompletableFuture.completedFuture(new byte[0]);

        String key = cacheKey(title, artist);
        byte[] cached = CACHE.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> fetchArtwork(title, artist))
                .thenApply(artwork -> {
                    if (artwork.length > 0) CACHE.putIfAbsent(key, artwork);
                    return artwork;
                })
                .exceptionally(ignored -> new byte[0]);
    }

    private static byte[] fetchArtwork(String title, String artist) {
        try {
            String term = (clean(title) + " " + safe(artist)).trim();
            String url = SEARCH_API
                    + "?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
                    + "&country=US&media=music&entity=song&limit=5";

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "LynxWave/1.6")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new byte[0];

            String artworkUrl = findArtworkUrl(response.body(), title, artist);
            if (artworkUrl.isEmpty()) return new byte[0];

            HttpRequest artworkRequest = HttpRequest.newBuilder(URI.create(artworkUrl))
                    .header("User-Agent", "LynxWave/1.6")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> artworkResponse = HTTP.send(artworkRequest, HttpResponse.BodyHandlers.ofByteArray());
            byte[] artwork = artworkResponse.statusCode() == 200 ? artworkResponse.body() : new byte[0];
            return artwork.length <= MAX_ARTWORK_BYTES ? artwork : new byte[0];
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static String findArtworkUrl(String body, String title, String artist) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray results = root.has("results") ? root.getAsJsonArray("results") : new JsonArray();
            JsonObject best = null;
            int bestScore = Integer.MIN_VALUE;

            for (JsonElement element : results) {
                if (!element.isJsonObject()) continue;
                JsonObject result = element.getAsJsonObject();
                if (!result.has("artworkUrl100") || result.get("artworkUrl100").isJsonNull()) continue;

                int score = matchScore(result, title, artist);
                if (score > bestScore) {
                    best = result;
                    bestScore = score;
                }
            }

            if (best == null) return "";
            return best.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int matchScore(JsonObject result, String title, String artist) {
        String expectedTitle = normalized(clean(title));
        String expectedArtist = normalized(artist);
        String foundTitle = normalized(result.has("trackName") ? result.get("trackName").getAsString() : "");
        String foundArtist = normalized(result.has("artistName") ? result.get("artistName").getAsString() : "");

        int score = 0;
        if (!expectedTitle.isEmpty() && !foundTitle.isEmpty() && (foundTitle.equals(expectedTitle) || foundTitle.contains(expectedTitle) || expectedTitle.contains(foundTitle))) score += 4;
        if (!expectedArtist.isEmpty() && !foundArtist.isEmpty() && (foundArtist.equals(expectedArtist) || foundArtist.contains(expectedArtist) || expectedArtist.contains(foundArtist))) score += 2;
        return score;
    }

    private static String cacheKey(String title, String artist) {
        return normalized(clean(title) + " - " + safe(artist));
    }

    private static String clean(String value) {
        return safe(value).replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?]", "").trim();
    }

    private static String normalized(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
