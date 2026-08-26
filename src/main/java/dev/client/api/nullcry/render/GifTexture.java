package dev.client.api.nullcry.render;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Анимированная текстура из GIF. При каждом запросе GPU texture handle обновляет кадр по времени.
 */
public class GifTexture extends AbstractTexture {

    private static int targetWidth() {
        var w = MinecraftClient.getInstance().getWindow();
        return w != null ? Math.max(1920, w.getFramebufferWidth()) : 1920;
    }
    private static int targetHeight() {
        var w = MinecraftClient.getInstance().getWindow();
        return w != null ? Math.max(1080, w.getFramebufferHeight()) : 1080;
    }

    private final List<NativeImage> frames = new ArrayList<>();
    private final int[] delaysMs;
    private final NativeImageBackedTexture backing;
    private final long startTimeMs;
    private int lastUploadedFrame = -1;

    public GifTexture(byte[] gifData) throws IOException {
        startTimeMs = System.currentTimeMillis();
        decodeGif(gifData);
        if (frames.isEmpty()) {
            throw new IOException("GIF has no frames");
        }
        delaysMs = new int[frames.size()];
        for (int i = 0; i < delaysMs.length; i++) {
            delaysMs[i] = Math.max(50, 100); // default 100ms, min 50ms
        }
        parseDelays(gifData);
        backing = new NativeImageBackedTexture(() -> "lynxwave_gif_texture", copyImage(frames.get(0)));
    }

    private void decodeGif(byte[] data) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            fallbackSingleFrame(data);
            return;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(iis, false);
            int numImages = reader.getNumImages(true);
            for (int i = 0; i < numImages; i++) {
                BufferedImage bi = reader.read(i);
                if (bi != null) {
                    frames.add(bufferedToNative(scaleIfNeeded(bi)));
                }
            }
        } finally {
            reader.dispose();
        }
        if (frames.isEmpty()) {
            fallbackSingleFrame(data);
        }
    }

    private void fallbackSingleFrame(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IOException("Failed to read GIF");
        frames.add(bufferedToNative(scaleIfNeeded(img)));
    }

    private static BufferedImage scaleIfNeeded(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int tw = targetWidth();
        int th = targetHeight();
        if (w >= tw && h >= th) return img;
        int targetW = Math.max(w, tw);
        int targetH = Math.max(h, th);
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, targetW, targetH, null);
        g.dispose();
        return scaled;
    }

    private void parseDelays(byte[] data) {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return;
            ImageReader reader = readers.next();
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                reader.setInput(iis, false);
                int n = Math.min(reader.getNumImages(true), delaysMs.length);
                for (int i = 0; i < n; i++) {
                    var meta = reader.getImageMetadata(i);
                    if (meta != null) {
                        String[] names = meta.getMetadataFormatNames();
                        for (String name : names) {
                            var root = (org.w3c.dom.Node) meta.getAsTree(name);
                            int d = getDelayFromNode(root);
                            if (d > 0) {
                                delaysMs[i] = d;
                                break;
                            }
                        }
                    }
                }
            }
            reader.dispose();
        } catch (Exception ignored) {
        }
    }

    private int getDelayFromNode(org.w3c.dom.Node node) {
        if (node instanceof IIOMetadataNode) {
            IIOMetadataNode n = (IIOMetadataNode) node;
            if ("GraphicControlExtension".equals(n.getNodeName())) {
                String delay = n.getAttribute("delayTime");
                if (delay != null && !delay.isEmpty()) {
                    try {
                        int centisec = Integer.parseInt(delay);
                        return Math.max(50, centisec * 10); // hundredths -> ms
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            int d = getDelayFromNode(children.item(i));
            if (d > 0) return d;
        }
        return 0;
    }

    private static NativeImage bufferedToNative(BufferedImage img) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        int tw = targetWidth();
        int th = targetHeight();
        if (w < tw || h < th) {
            int outW = Math.max(w, tw);
            int outH = Math.max(h, th);
            return scaleTo(img, outW, outH);
        }
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ni.setColorArgb(x, y, img.getRGB(x, y));
            }
        }
        return ni;
    }

    /** Билинейная интерполяция для сглаживания при увеличении. */
    private static NativeImage scaleTo(BufferedImage src, int outW, int outH) throws IOException {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        NativeImage out = new NativeImage(outW, outH, true);
        for (int oy = 0; oy < outH; oy++) {
            float sy = srcH > 1 ? (oy * (srcH - 1f) / (outH - 1f)) : 0;
            int iy0 = (int) sy;
            int iy1 = Math.min(iy0 + 1, srcH - 1);
            float fy = sy - iy0;
            for (int ox = 0; ox < outW; ox++) {
                float sx = srcW > 1 ? (ox * (srcW - 1f) / (outW - 1f)) : 0;
                int ix0 = (int) sx;
                int ix1 = Math.min(ix0 + 1, srcW - 1);
                float fx = sx - ix0;
                int c00 = src.getRGB(ix0, iy0);
                int c10 = src.getRGB(ix1, iy0);
                int c01 = src.getRGB(ix0, iy1);
                int c11 = src.getRGB(ix1, iy1);
                int r = lerp(lerp((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, fx),
                        lerp((c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, fx), fy);
                int g = lerp(lerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, fx),
                        lerp((c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, fx), fy);
                int b = lerp(lerp(c00 & 0xFF, c10 & 0xFF, fx),
                        lerp(c01 & 0xFF, c11 & 0xFF, fx), fy);
                int a = lerp(lerp((c00 >> 24) & 0xFF, (c10 >> 24) & 0xFF, fx),
                        lerp((c01 >> 24) & 0xFF, (c11 >> 24) & 0xFF, fx), fy);
                out.setColorArgb(ox, oy, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static NativeImage copyImage(NativeImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        NativeImage copy = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy.setColorArgb(x, y, src.getColorArgb(x, y));
            }
        }
        return copy;
    }

    private int getCurrentFrameIndex() {
        if (frames.size() <= 1) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        long totalCycle = 0;
        for (int d : delaysMs) totalCycle += d;
        if (totalCycle <= 0) return 0;
        long pos = elapsed % totalCycle;
        for (int i = 0; i < frames.size(); i++) {
            pos -= delaysMs[i];
            if (pos < 0) return i;
        }
        return 0;
    }

    public int getGlId() {
        updateFrame();
        return 0;
    }

    @Override
    public GpuTexture getGlTexture() {
        updateFrame();
        return backing.getGlTexture();
    }

    @Override
    public GpuTextureView getGlTextureView() {
        updateFrame();
        return backing.getGlTextureView();
    }

    @Override
    public GpuSampler getSampler() {
        updateFrame();
        return backing.getSampler();
    }

    private void updateFrame() {
        if (frames.isEmpty()) return;
        int frame = getCurrentFrameIndex();
        if (frame != lastUploadedFrame && frame < frames.size()) {
            NativeImage current = frames.get(frame);
            backing.setImage(copyImage(current));
            backing.upload();
            lastUploadedFrame = frame;
        }
    }

    public void close() {
        for (NativeImage frame : frames) {
            try {
                frame.close();
            } catch (Exception ignored) {
            }
        }
        backing.close();
    }
}
