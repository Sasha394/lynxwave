package dev.client.api.nullcry.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.ReloadableTexture;
import net.minecraft.client.texture.TextureContents;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientTexture {
    private static final Identifier DEFAULT_BG = Identifier.of("lynxwave", "images/title.png");
    private static final Set<Identifier> REGISTERED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Identifier> URL_IDS = new ConcurrentHashMap<>();
    private static final Set<Identifier> URL_REGISTERED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger URL_COUNTER = new AtomicInteger(0);

    private ClientTexture() {}

    public static AbstractTexture of(String path) {
        if (path == null || path.isBlank()) {
            return getTexture(DEFAULT_BG);
        }
        String trimmed = path.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            AbstractTexture t = getUrlTexture(trimmed);
            return t != null ? t : getTexture(DEFAULT_BG);
        }
        // Для ресурсных путей проверяем, что строка подходит под правила Identifier
        if (!isValidResourcePath(lower)) {
            System.err.println("[LynxWave] Invalid background path, fallback to default: " + trimmed);
            return getTexture(DEFAULT_BG);
        }
        AbstractTexture t = getTexture(Identifier.of("lynxwave", lower));
        return t != null ? t : getTexture(DEFAULT_BG);
    }

    public static Identifier id(String path) {
        Identifier identifier = Identifier.of("lynxwave", path);
        ensureRegistered(identifier);
        return identifier;
    }

    private static AbstractTexture getTexture(Identifier identifier) {
        TextureManager manager = MinecraftClient.getInstance().getTextureManager();
        if (manager == null) return null;
        ensureRegistered(manager, identifier);
        return manager.getTexture(identifier);
    }

    private static void ensureRegistered(Identifier identifier) {
        TextureManager manager = MinecraftClient.getInstance().getTextureManager();
        if (manager == null) return;
        ensureRegistered(manager, identifier);
    }

    private static void ensureRegistered(TextureManager manager, Identifier identifier) {
        if (manager == null || identifier == null) return;

        if (REGISTERED.add(identifier)) {
            manager.registerTexture(identifier, new DirectResourceTexture(identifier));
        }
    }

    private static AbstractTexture getUrlTexture(String url) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextureManager manager = mc != null ? mc.getTextureManager() : null;
        if (manager == null) {
            return getTexture(DEFAULT_BG);
        }

        // Ограничиваемся postimg.cc и imgur.com, как просил пользователь
        try {
            URL u = new URL(url);
            String host = u.getHost().toLowerCase();
            if (!host.contains("postimg.cc") && !host.contains("imgur.com")) {
                System.err.println("[LynxWave] Remote background host not allowed: " + host);
                return getTexture(DEFAULT_BG);
            }
        } catch (Exception e) {
            System.err.println("[LynxWave] Bad background URL: " + url);
            return getTexture(DEFAULT_BG);
        }

        Identifier id = URL_IDS.computeIfAbsent(url, u ->
                Identifier.of("lynxwave", "remote_bg/" + Integer.toHexString(u.hashCode()) + "_" + URL_COUNTER.incrementAndGet())
        );

        if (URL_REGISTERED.add(id)) {
            byte[] data = downloadBytes(url);
            if (data == null || data.length == 0) {
                System.err.println("[LynxWave] Failed to download background image: " + url);
                URL_REGISTERED.remove(id);
                return getTexture(DEFAULT_BG);
            }
            try {
                boolean isGif = url.toLowerCase().trim().endsWith(".gif");
                if (isGif) {
                    GifTexture tex = new GifTexture(data);
                    manager.registerTexture(id, tex);
                } else {
                    NativeImageBackedTexture tex = decodeToTexture(data);
                    manager.registerTexture(id, tex);
                }
            } catch (Exception e) {
                System.err.println("[LynxWave] Failed to decode background image: " + url + " (" + e.getMessage() + ")");
                URL_REGISTERED.remove(id);
                return getTexture(DEFAULT_BG);
            }
        }

        AbstractTexture t = manager.getTexture(id);
        return t != null ? t : getTexture(DEFAULT_BG);
    }

    /** Разрешаем только [a-z0-9/._-] в путях ресурсов. */
    private static boolean isValidResourcePath(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
                    c == '/' || c == '.' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static NativeImageBackedTexture decodeToTexture(byte[] data) throws IOException {
        // 1) Сначала пробуем как PNG через NativeImage.read (быстро и без AWT)
        try {
            ByteBuffer buffer = BufferUtils.createByteBuffer(data.length).put(data);
            buffer.flip();
            return new NativeImageBackedTexture(() -> "lynxwave_dynamic_texture", NativeImage.read(buffer));
        } catch (Exception ignored) {
        }

        // 2) JPG/WebP/etc через ImageIO → NativeImage
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            throw new IOException("Unsupported image format");
        }

        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                ni.setColorArgb(x, y, argb);
            }
        }
        return new NativeImageBackedTexture(() -> "lynxwave_dynamic_texture", ni);
    }

    private static byte[] downloadBytes(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(10000);
            conn.connect();
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                System.err.println("[LynxWave] HTTP " + code + " for background " + url);
                return null;
            }
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            System.err.println("[LynxWave] Error downloading background " + url + ": " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static final class DirectResourceTexture extends ReloadableTexture {
        private final Identifier id;

        private DirectResourceTexture(@NotNull Identifier id) {
            super(id);
            this.id = id;
        }

        @Override
        public TextureContents loadContents(ResourceManager manager) throws IOException {
            try {
                return TextureContents.load(manager, this.id);
            } catch (FileNotFoundException notFound) {
                Identifier prefixed = this.id.withPrefixedPath("textures/");
                if (!prefixed.equals(this.id)) {
                    try {
                        return TextureContents.load(manager, prefixed);
                    } catch (FileNotFoundException ignored) {
                    } catch (IOException io) {
                        System.err.println("[LynxWave] Failed to load texture (bad PNG?): " + prefixed + " (" + io.getMessage() + ")");
                        return TextureContents.createMissing();
                    }
                }
                System.err.println("[LynxWave] Texture not found: " + this.id);
                return TextureContents.createMissing();
            } catch (IOException io) {
                System.err.println("[LynxWave] Failed to load texture (bad PNG?): " + this.id + " (" + io.getMessage() + ")");
                return TextureContents.createMissing();
            }
        }

    }
}
