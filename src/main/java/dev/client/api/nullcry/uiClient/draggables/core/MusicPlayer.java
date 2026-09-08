package dev.client.api.nullcry.uiClient.draggables.core;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.MouseClick;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.ScissorUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.animations.nova.CompactAnimation;
import dev.client.api.nullcry.render.core.animations.nova.Easing;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.api.nullcry.render.core.builders.states.QuadColorState;
import dev.client.api.nullcry.render.core.builders.states.QuadRadiusState;
import dev.client.api.nullcry.render.core.builders.states.SizeState;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.core.music.AlbumArtClient;
import dev.client.api.nullcry.uiClient.draggables.core.music.LrcLibClient;
import dev.client.api.nullcry.uiClient.draggables.core.music.LyricLine;
import dev.client.api.nullcry.uiClient.draggables.core.music.LyricLineSplitter;
import dev.client.api.nullcry.uiClient.draggables.core.music.LyricParticle3D;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Interface;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class MusicPlayer implements IHelper, ClientApi, SettingProvider {

    private final DraggableHandler drag;
    private final List<Setting> settings = new ArrayList<>();
    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);
    public final CheckBox lyrics3d = new CheckBox("3D-текст", () -> true).defaultValue(false).register(this);
    public final CompactAnimation alpha = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private static final int MAX_LYRIC_PARTICLES = 3;
    private final List<LyricParticle3D> activeLyrics = new ArrayList<>();
    private List<LyricLine> lyricTimeline = List.of();
    private String lyricTrackKey = "";
    private int lyricIndex;
    private long predictedPositionMs;
    private long lastLyricsUpdateMs;

    @Override
    public DraggableHandler draggable() {
        return drag;
    }

    @Override
    public float hudScale() {
        Float scaleValue = scale.getValue();
        return scaleValue == null ? 1f : scaleValue / 100f;
    }

    @Override
    public List<Setting> getSettings() {
        return settings;
    }

    private MediaInfo media = new MediaInfo("Название Трека", "Артист", new byte[0], 0, 0, false);
    private final Identifier artId = Identifier.of("lynxwave", "dynamic/music_art");
    private final TimerUtil lastMedia = new TimerUtil();
    public IMediaSession session;
    private volatile boolean artworkReady;
    private volatile boolean artworkLoading;
    private volatile String artworkTrackKey = "";
    /** Когда можно повторить неудавшуюся загрузку обложки; 0 — повтор не нужен. */
    private volatile long artworkRetryAt;
    private static final long ARTWORK_RETRY_DELAY_MS = 15_000L;

    private static final float PANEL_W = 145f;
    private static final float PANEL_H = 45f;
    private static final float PAD = 6f;
    private static final float ART_SIZE = 32f;
    private static final float ART_RADIUS = 3f;

    private static final float ICON_S = 7f;

    private float playX, playY;
    private float prevX, prevY;
    private float nextX, nextY;

    private float smoothProgress = 0f;

    private float titleScroll = 0f;
    private long titleScrollT = System.currentTimeMillis();
    private float artistScroll = 0f;
    private long artistScrollT = System.currentTimeMillis();
    private boolean visibleLastFrame = false;

    public MusicPlayer() {
        this.drag = addDraggable("MusicPlayer", 4, 200);
        drag.setWidth(PANEL_W);
        drag.setHeight(PANEL_H);
        drag.setActiveCondition(() -> {
            Interface module = Interface.INSTANCE;
            return module != null && module.isEnabled() && module.elements.isSelected("MusicPlayer");
        });
        drag.setSettingsPanel(new DraggableSettingsPanel(drag, this));

        drag.setClickHandler((mx, my, button, self) -> {
            if (button != 0) return false;

            if (MouseClick.isClick(mx, my, playX, playY, ICON_S, ICON_S)) {
                togglePlayPause();
                return true;
            }
            if (MouseClick.isClick(mx, my, nextX, nextY, ICON_S, ICON_S)) {
                skipNext();
                return true;
            }
            if (MouseClick.isClick(mx, my, prevX + ICON_S, prevY, ICON_S, ICON_S)) {
                skipPrev();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onUpdate(UpdateEvent e) {
        if (mc.player == null) return;

        if (mc.player.age % 5 == 0) {
            io.execute(() -> {

                IMediaSession currentSession = session = MediaPlayerInfo.Instance.getMediaSessions().stream().max(Comparator.comparing(s -> s.getMedia().getPlaying())).orElse(null);
                if (currentSession != null) {
                    MediaInfo info = currentSession.getMedia();
                    if (!info.getTitle().isEmpty() || !info.getArtist().isEmpty()) {
                        updateArtwork(info);
                        media = info;
                        lastMedia.reset();
                    }
                }

            });
        }

        updateLyrics();
    }

    public void onRender3D(RenderEvent.Draw3D event) {
        if (!lyrics3d.getEnabled() || mc.player == null || mc.world == null || activeLyrics.isEmpty()) return;

        long now = System.currentTimeMillis();
        activeLyrics.removeIf(lyric -> lyric.isDead(now));
        if (activeLyrics.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        int color = Interface.INSTANCE.getMainColor();
        for (LyricParticle3D lyric : activeLyrics) {
            lyric.render(event.getMatrices(), camera, color, now);
        }
    }

    @Override
    public void onRender(RenderEvent.Draw2D ev) {
        float x = drag.getX();
        float y = drag.getY();

        alpha.run(1.0);
        alpha.update();
        float a = (float) alpha.getValue();
        if (a <= 0f) {
            visibleLastFrame = false;
            return;
        }

        if (!visibleLastFrame) {
            titleScroll = 0f;
            artistScroll = 0f;
        }

        visibleLastFrame = true;

        drag.setWidth(PANEL_W);
        drag.setHeight(PANEL_H);

        HelperElements.rectElements(ev.getContext(), x, y, PANEL_W, PANEL_H, a);
        ScissorUtil.enable(x, y, PANEL_W, PANEL_H);

        boolean hasArt = artworkReady;
        boolean hasMeta = !(isEmpty(media.getTitle()) && isEmpty(media.getArtist())) || media.getDuration() > 0;
        boolean hasTrack = session != null && (hasMeta || hasArt);

        float artX = x + PAD;
        float artY = y + (PANEL_H - ART_SIZE) * 0.5f;

        // The media-session API does not provide album art for every player/track.
        // Never render artId in that case: Minecraft displays an invalid texture as
        // the black-and-magenta checkerboard seen in the HUD.
        if (hasArt) {
            DrawUtil.renderTexture(
                    ev.getContext(),
                    artX, artY,
                    ART_SIZE, ART_SIZE,
                    ART_RADIUS,
                    0f, 0f, 1f, 1f,
                    artId,
                    new Color(HelperElements.themedWhite((int) (255 * a)), true)
            );
        } else {
            renderArtworkPlaceholder(ev, artX, artY, a);
        }

        float textL = artX + ART_SIZE + PAD;
        float titleY = y + 7f;
        float artistY = y + 18.5f;
        var mc = MinecraftClient.getInstance();
        String title = hasTrack
                ? normalizeRenderable(safe(media.getTitle(), "Название Трека"))
                : normalizeRenderable( LynxWave.getInstance().getUserProfile().getUser() + " ОПЯТЬ ЗАПЕНИЛ ВСЕ СЕРВЕРА?");
        String artist = hasTrack
                ? normalizeRenderable(safe(media.getArtist(), "Артист"))
                : normalizeRenderable( LynxWave.getInstance().getUserProfile().getUser());

        float titleSize = 8f;
        float artistSize = 6f;

        float clipW = Math.max(2f, (x + PANEL_W - PAD) - textL);
        float titleW = Builder.INTER.get().getWidth(title, titleSize);

        if (titleW > clipW + 0.5f) {
            long now = System.currentTimeMillis();
            if (now - titleScrollT > 15) {
                titleScroll += 1.25f;
                float loop = titleW + 20f;
                if (titleScroll > loop) titleScroll = 0f;
                titleScrollT = now;
            }
        } else {
            titleScroll = 0f;
        }

        ScissorUtil.enable(textL, y, clipW, PANEL_H);
        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(title)
                .color(HelperElements.themedText((int) (255 * a)))
                .size(titleSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL - titleScroll, titleY);

        if (titleW > clipW + 0.5f) {
            float loop = titleW + 20f;
            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(title)
                    .color(HelperElements.themedText((int) (255 * a)))
                    .size(titleSize)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL - titleScroll + loop, titleY);
        }
        ScissorUtil.disable();

        float artistW = Builder.INTER.get().getWidth(artist, artistSize);
        if (artistW > clipW + 0.5f) {
            long now = System.currentTimeMillis();
            if (now - artistScrollT > 15) {
                artistScroll += 1.25f;
                float loop = artistW + 20f;
                if (artistScroll > loop) artistScroll = 0f;
                artistScrollT = now;
            }
        } else {
            artistScroll = 0f;
        }

        ScissorUtil.enable(textL, y, clipW, PANEL_H);
        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(artist)
                .color(HelperElements.themedText((int) (200 * a)))
                .size(artistSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL - artistScroll, artistY);

        if (artistW > clipW + 0.5f) {
            float loopA = artistW + 20f;
            Builder.TEXT_BUILDER
                    .font(Builder.INTER.get())
                    .text(artist)
                    .color(HelperElements.themedText((int) (200 * a)))
                    .size(artistSize)
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL - artistScroll + loopA, artistY);
        }
        ScissorUtil.disable();

        float dur = hasTrack ? media.getDuration() : 0f;
        float pos = hasTrack ? MathHelper.clamp(media.getPosition(), 0f, Math.max(0f, dur)) : 0f;

        float barY = y + PANEL_H - 8.5f;
        float barW = (x + PANEL_W - PAD) - textL;
        float barH = 3f;

        float target = dur > 0f ? (pos / dur) : 0f;
        float dt = Math.max(0.001f, mc.getRenderTickCounter().getDynamicDeltaTicks());
        smoothProgress = MathHelper.lerp(MathHelper.clamp(0.35f * dt * 60f, 0f, 1f), smoothProgress, target);
        float filledW = barW * MathHelper.clamp(smoothProgress, 0f, 1f);

        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(0.5f))
                .color(new QuadColorState(ColorUtils.setAlpha(ColorUtils.rgb(30, 30, 35), (int) (180 * a))))
                .size(new SizeState(barW, barH + 0.5f))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL, barY);

        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(0.5f))
                .color(new QuadColorState(ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), (int) (255 * a))))
                .size(new SizeState(filledW, barH))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL, barY);

        float timeSize = 6f;
        String leftTime = toTime((int) pos);
        String totalTime = toTime((int) dur);

        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(normalizeRenderable(leftTime))
                .color(HelperElements.themedText((int) (255 * a)))
                .size(timeSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL, y + 29f);

        float totalW = Builder.INTER.get().getWidth(totalTime, timeSize);
        Builder.TEXT_BUILDER
                .font(Builder.INTER.get())
                .text(normalizeRenderable(totalTime))
                .color(HelperElements.themedText((int) (255 * a)))
                .size(timeSize)
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), textL + barW - totalW - 2, y + 29f);

        float centerX = textL + barW * 0.5f;
        playX = centerX - ICON_S * 0.5f;
        playY = barY - ICON_S - 1.5f;

        prevX = playX - ICON_S - 10f;
        prevY = playY;

        nextX = playX + ICON_S + 4f;
        nextY = playY;

        String playIcon = media.getPlaying() ? "music/pause.png" : "music/play.png";
        Builder.TEXTURE_BUILDER
                .size(new SizeState(ICON_S, ICON_S))
                .texture(0, 0, 1, 1, ClientTexture.of(playIcon))
                .color(new QuadColorState(HelperElements.themedWhite((int) (255 * a))))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), playX, playY);

        Builder.TEXTURE_BUILDER
                .size(new SizeState(ICON_S, ICON_S))
                .texture(0, 0, 1, 1, ClientTexture.of("music/next.png"))
                .color(new QuadColorState(HelperElements.themedWhite((int) (255 * a))))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), nextX, nextY);

        Builder.TEXTURE_BUILDER
                .size(new SizeState(ICON_S, ICON_S))
                .texture(1, 0, -1, 1, ClientTexture.of("music/next.png"))
                .color(new QuadColorState(HelperElements.themedWhite((int) (255 * a))))
                .build()
                .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), prevX + ICON_S, prevY);

        ScissorUtil.disable();
    }

    private void renderArtworkPlaceholder(RenderEvent.Draw2D event, float x, float y, float alpha) {
        var matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext());
        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(ART_RADIUS))
                .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, (int) (200 * alpha))))
                .size(new SizeState(ART_SIZE, ART_SIZE))
                .build()
                .render(matrix, x, y);

        int noteColor = HelperElements.themedWhite((int) (190 * alpha));
        // A small music-note fallback while album art is unavailable.
        renderPlaceholderShape(matrix, x + 16f, y + 8f, 2f, 14f, 1f, noteColor);
        renderPlaceholderShape(matrix, x + 11f, y + 8f, 7f, 2f, 1f, noteColor);
        renderPlaceholderShape(matrix, x + 9f, y + 20f, 8f, 6f, 3f, noteColor);
    }

    private void renderPlaceholderShape(org.joml.Matrix4f matrix, float x, float y, float width, float height, float radius, int color) {
        Builder.RECTANGLE_BUILDER
                .radius(new QuadRadiusState(radius))
                .color(new QuadColorState(color))
                .size(new SizeState(width, height))
                .build()
                .render(matrix, x, y);
    }

    private void togglePlayPause() {
        if (session == null) return;
        try {
            session.playPause();
        } catch (Throwable ignored) {
            try {
                if (media.getPlaying()) session.pause();
                else session.play();
            } catch (Throwable ignored2) {
            }
        }
    }

    private void skipNext() {
        if (session == null) return;
        try {
            session.next();
        } catch (Throwable ignored) {
        }
    }

    private void skipPrev() {
        if (session == null) return;
        try {
            session.previous();
        } catch (Throwable ignored) {
        }
    }

    private void updateLyrics() {
        if (!lyrics3d.getEnabled()) {
            clearLyrics();
            return;
        }

        String title = media.getTitle() == null ? "" : media.getTitle().trim();
        String artist = media.getArtist() == null ? "" : media.getArtist().trim();
        if (session == null || title.isEmpty()) {
            clearLyrics();
            return;
        }

        long now = System.currentTimeMillis();
        long reportedPositionMs = Math.max(0L, media.getPosition()) * 1_000L;
        String trackKey = (title + " - " + artist).toLowerCase(Locale.ROOT);
        if (!trackKey.equals(lyricTrackKey)) {
            startLyricsTrack(trackKey, title, artist, reportedPositionMs, now);
            return;
        }

        long deltaMs = lastLyricsUpdateMs == 0L ? 0L : Math.max(0L, now - lastLyricsUpdateMs);
        lastLyricsUpdateMs = now;
        if (Math.abs(reportedPositionMs - predictedPositionMs) > 1_500L) {
            predictedPositionMs = reportedPositionMs;
            resyncLyrics(predictedPositionMs, now);
        } else if (media.getPlaying()) {
            predictedPositionMs += deltaMs;
        }
        long durationMs = Math.max(0L, media.getDuration()) * 1_000L;
        if (durationMs > 0L) predictedPositionMs = Math.min(predictedPositionMs, durationMs);
        advanceLyrics(predictedPositionMs, now);
        activeLyrics.removeIf(lyric -> lyric.isDead(now));
    }

    private void startLyricsTrack(String trackKey, String title, String artist, long positionMs, long now) {
        lyricTrackKey = trackKey;
        lyricTimeline = List.of();
        lyricIndex = 0;
        activeLyrics.clear();
        predictedPositionMs = positionMs;
        lastLyricsUpdateMs = now;

        LrcLibClient.fetchLyricsAsync(title, artist).whenComplete((lines, error) -> mc.execute(() -> {
            if (!lyrics3d.getEnabled() || !trackKey.equals(lyricTrackKey)) return;
            lyricTimeline = error == null ? LyricLineSplitter.splitLongLines(lines) : List.of();
            resyncLyrics(predictedPositionMs, System.currentTimeMillis());
        }));
    }

    private void advanceLyrics(long audioTimeMs, long now) {
        while (lyricIndex < lyricTimeline.size()) {
            LyricLine line = lyricTimeline.get(lyricIndex);
            if (audioTimeMs < line.timestampMs()) break;

            long durationMs = lyricDuration(lyricIndex);
            long elapsed = audioTimeMs - line.timestampMs();
            if (elapsed < durationMs) {
                spawnLyric(line.text(), now - elapsed, durationMs);
            }
            lyricIndex++;
        }
    }

    private void resyncLyrics(long audioTimeMs, long now) {
        activeLyrics.clear();
        lyricIndex = 0;
        for (int i = 0; i < lyricTimeline.size(); i++) {
            LyricLine line = lyricTimeline.get(i);
            if (audioTimeMs < line.timestampMs()) break;

            long durationMs = lyricDuration(i);
            long elapsed = audioTimeMs - line.timestampMs();
            if (elapsed < durationMs) {
                spawnLyric(line.text(), now - elapsed, durationMs);
            }
            lyricIndex = i + 1;
        }
    }

    private long lyricDuration(int index) {
        LyricLine line = lyricTimeline.get(index);
        long nextTimestamp = index + 1 < lyricTimeline.size()
                ? lyricTimeline.get(index + 1).timestampMs()
                : line.timestampMs() + 3_500L;
        return MathHelper.clamp(nextTimestamp - line.timestampMs(), 1_400L, 5_500L);
    }

    private void spawnLyric(String text, long spawnTimeMs, long durationMs) {
        if (mc.player == null || text == null || text.isBlank()) return;

        while (activeLyrics.size() >= MAX_LYRIC_PARTICLES) activeLyrics.removeFirst();
        Vec3d position = findLyricPosition(mc.player.getEyePos(), mc.gameRenderer.getCamera().getYaw());
        activeLyrics.add(new LyricParticle3D(text, position, spawnTimeMs, durationMs));
    }

    private Vec3d findLyricPosition(Vec3d eyePosition, float cameraYaw) {
        Vec3d best = eyePosition;
        double bestSpacing = -1d;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = Math.toRadians(cameraYaw + random.nextDouble(-70d, 70d));
            double distance = random.nextDouble(2d, 4.5d);
            Vec3d candidate = eyePosition.add(
                    -Math.sin(angle) * distance,
                    random.nextDouble(-0.25d, 0.55d),
                    Math.cos(angle) * distance
            );
            if (activeLyrics.isEmpty()) return candidate;

            double spacing = Double.MAX_VALUE;
            for (LyricParticle3D active : activeLyrics) {
                spacing = Math.min(spacing, candidate.distanceTo(active.getBasePosition()));
            }
            if (spacing >= 1.35d) return candidate;
            if (spacing > bestSpacing) {
                bestSpacing = spacing;
                best = candidate;
            }
        }
        return best;
    }

    private void clearLyrics() {
        if (lyricTrackKey.isEmpty() && lyricTimeline.isEmpty() && activeLyrics.isEmpty()) return;
        lyricTrackKey = "";
        lyricTimeline = List.of();
        lyricIndex = 0;
        predictedPositionMs = 0L;
        lastLyricsUpdateMs = 0L;
        activeLyrics.clear();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String safe(String s, String def) {
        return isEmpty(s) ? def : s;
    }

    private static String normalizeRenderable(String s) {
        return (s == null || s.isEmpty()) ? " " : s;
    }

    private static String toTime(int total) {
        if (total <= 0) return "0:00";
        int m = total / 60, s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private void updateArtwork(MediaInfo info) {
        String trackKey = artworkKey(info.getTitle(), info.getArtist());
        boolean trackChanged = !trackKey.equals(artworkTrackKey);
        boolean sourceArtworkChanged = !Arrays.equals(media.getArtworkPng(), info.getArtworkPng());
        byte[] embeddedArtwork = info.getArtworkPng();

        if (trackChanged) {
            artworkTrackKey = trackKey;
            artworkReady = false;
            artworkLoading = false;
            artworkRetryAt = 0L;
        }

        if (embeddedArtwork != null && embeddedArtwork.length > 0 && (trackChanged || sourceArtworkChanged || (!artworkReady && !artworkLoading))) {
            artworkLoading = true;
            registerTexture(artId, embeddedArtwork, trackKey);
            return;
        }

        // Обложку у сессии дают не все плееры, поэтому докачиваем её из сети.
        // Сеть может отвалиться — тогда пробуем ещё раз, но не чаще раза в 15 секунд.
        boolean retryDue = !artworkReady && !artworkLoading
                && artworkRetryAt > 0L && System.currentTimeMillis() >= artworkRetryAt;

        if (trackChanged || retryDue) {
            artworkLoading = true;
            artworkRetryAt = 0L;
            AlbumArtClient.fetchArtworkAsync(info.getTitle(), info.getArtist())
                    .thenAccept(artwork -> {
                        if (!trackKey.equals(artworkTrackKey)) return;

                        if (artwork.length > 0 && !artworkReady) {
                            registerTexture(artId, artwork, trackKey);
                        } else {
                            artworkLoading = false;
                            artworkRetryAt = System.currentTimeMillis() + ARTWORK_RETRY_DELAY_MS;
                        }
                    });
        }
    }

    private static String artworkKey(String title, String artist) {
        return ((title == null ? "" : title.trim()) + "\u0000" + (artist == null ? "" : artist.trim())).toLowerCase(Locale.ROOT);
    }

    private void registerTexture(Identifier id, byte[] content, String trackKey) {
        if (content == null || content.length == 0) return;

        NativeImage image;
        try {
            image = decodeArtwork(content);
        } catch (Exception ignored) {
            if (trackKey.equals(artworkTrackKey)) artworkLoading = false;
            return;
        }

        // NativeImageBackedTexture прямо в конструкторе создаёт GPU-текстуру и заливает
        // её через command encoder, а это можно делать только с рендер-потока. Раньше
        // текстура собиралась здесь же, в потоке загрузки, падала с исключением — и
        // обложка не появлялась никогда. Декодирование остаётся вне рендер-потока.
        mc.execute(() -> {
            if (!trackKey.equals(artworkTrackKey)) {
                image.close();
                return;
            }

            try {
                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> "lynxwave-music-art/" + id, image);
                mc.getTextureManager().registerTexture(id, texture);
                artworkReady = true;
            } catch (Exception ignored) {
                image.close();
            } finally {
                artworkLoading = false;
            }
        });
    }

    /** Декодирование обложки в CPU-картинку: GPU здесь не участвует, поток любой. */
    private static NativeImage decodeArtwork(byte[] content) throws IOException {
        try {
            ByteBuffer data = BufferUtils.createByteBuffer(content.length).put(content);
            data.flip();
            return NativeImage.read(data);
        } catch (Exception ignored) {
        }

        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(content));
        if (bufferedImage == null) throw new IOException("Unsupported album art format");

        NativeImage image = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), true);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                image.setColorArgb(x, y, bufferedImage.getRGB(x, y));
            }
        }
        return image;
    }
}
