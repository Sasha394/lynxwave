package dev.client.api.nullcry.helper.client.irc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches player avatars (Minecraft skin heads) keyed by Minecraft username.
 * Downloads from mc-heads.net asynchronously, registers as a Texture once,
 * and returns Identifier ready for DrawContext#drawTexture.
 *
 * Returns null while loading; callers should fall back to initial-letter rendering.
 */
public final class PlayerHeadCache {
    private static final PlayerHeadCache INSTANCE = new PlayerHeadCache();

    private static final String URL_TEMPLATE = "https://mc-heads.net/avatar/%s/64";
    private static final int FETCH_TIMEOUT_MS = 6000;

    private final ExecutorService fetcher = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "IRC-HeadFetch");
        t.setDaemon(true);
        return t;
    });

    /** Names whose download has been kicked off. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** Names that failed (avoid retry storms). */
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    /** Identifier of registered texture per name. null means not yet ready. */
    private final Map<String, Identifier> ids = new ConcurrentHashMap<>();

    /** Resolved GL texture id (mc-heads.net image is 64×64). */
    private final Map<String, Integer> glIds = new ConcurrentHashMap<>();

    private PlayerHeadCache() {}

    public static PlayerHeadCache get() {
        return INSTANCE;
    }

    /**
     * Returns the texture id for the player's head, or null if it isn't loaded yet.
     * Triggers an async fetch if absent.
     */
    public Identifier headFor(String name) {
        String key = ensureLoaded(name);
        return key == null ? null : ids.get(key);
    }

    /**
     * Returns the GL texture id for the head, or 0 if not yet loaded.
     * Triggers an async fetch if absent.
     */
    public int glIdFor(String name) {
        String key = ensureLoaded(name);
        if (key == null) return 0;
        Integer id = glIds.get(key);
        return id == null ? 0 : id;
    }

    private String ensureLoaded(String name) {
        if (name == null || name.isBlank()) return null;
        String key = sanitize(name);
        if (key == null) return null;
        if (failed.contains(key)) return null;
        if (!ids.containsKey(key) && inFlight.add(key)) {
            fetcher.submit(() -> downloadAndRegister(key));
        }
        return key;
    }

    private void downloadAndRegister(String key) {
        try {
            byte[] bytes = download(String.format(URL_TEMPLATE, key));
            if (bytes == null || bytes.length == 0) {
                failed.add(key);
                return;
            }

            // Decode on the worker thread, register on the render thread.
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length).put(bytes);
            buffer.flip();
            NativeImage image;
            try {
                image = NativeImage.read(buffer);
            } catch (IOException e) {
                failed.add(key);
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) {
                image.close();
                inFlight.remove(key);
                return;
            }

            mc.execute(() -> {
                TextureManager tm = mc.getTextureManager();
                if (tm == null) {
                    image.close();
                    return;
                }
                Identifier id = Identifier.of("lynxwave", "irc_head/" + key);
                try {
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "lynxwave-irc-head/" + key, image);
                    tm.registerTexture(id, tex);
                    ids.put(key, id);
                } catch (Exception e) {
                    failed.add(key);
                    image.close();
                }
            });
        } catch (Exception e) {
            failed.add(key);
        } finally {
            inFlight.remove(key);
        }
    }

    private static byte[] download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(FETCH_TIMEOUT_MS);
            conn.setReadTimeout(FETCH_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "LynxWave-IRC");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Allow only [a-z0-9_], length 3..16. Mojang nick rules. */
    private static String sanitize(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.length() < 1 || n.length() > 16) return null;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return null;
        }
        return n;
    }
}
