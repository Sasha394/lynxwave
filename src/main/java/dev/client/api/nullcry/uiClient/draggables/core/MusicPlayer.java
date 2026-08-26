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
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import dev.client.api.nullcry.uiClient.clickGui.api.setting.SettingProvider;
import dev.client.api.nullcry.uiClient.draggables.HelperElements;
import dev.client.api.nullcry.uiClient.draggables.IHelper;
import dev.client.api.nullcry.uiClient.draggables.settings.DraggableSettingsPanel;
import dev.client.modules.core.render.Interface;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicPlayer implements IHelper, ClientApi, SettingProvider {

    private final DraggableHandler drag;
    private final List<Setting> settings = new ArrayList<>();
    public final Slider scale = new Slider("Масштаб", () -> true).set(50, 100, 5).applyDefault(100).register(this);
    public final CompactAnimation alpha = new CompactAnimation(Easing.EASE_OUT_CUBIC, 400);
    private final ExecutorService io = Executors.newSingleThreadExecutor();

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
    private final Identifier artId = Identifier.of("lynxwave", "textures/music/art.png");
    private final TimerUtil lastMedia = new TimerUtil();
    public IMediaSession session;

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
                        if (media.getTitle().equals("Название Трека") || !Arrays.equals(media.getArtworkPng(), info.getArtworkPng())) {
                            registerTexture(artId, info.getArtworkPng());
                        }
                        media = info;
                        lastMedia.reset();
                    }
                }

            });
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

        boolean hasArt = media.getArtworkPng() != null && media.getArtworkPng().length > 0;
        boolean hasMeta = !(isEmpty(media.getTitle()) && isEmpty(media.getArtist())) || media.getDuration() > 0;
        boolean hasTrack = session != null && (hasMeta || hasArt);

        float artX = x + PAD;
        float artY = y + (PANEL_H - ART_SIZE) * 0.5f;

        if (hasTrack) {
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
            Builder.RECTANGLE_BUILDER
                    .radius(new QuadRadiusState(ART_RADIUS))
                    .color(new QuadColorState(ColorUtils.rgba(0, 0, 0, (int) (200 * a))))
                    .size(new SizeState(ART_SIZE, ART_SIZE))
                    .build()
                    .render(dev.client.api.compat.GuiMatrixCompat.toMatrix4(ev.getContext()), artX, artY);
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

    public void registerTexture(Identifier id, byte[] content) {
        if (content == null || content.length == 0) return;
        try {
            ByteBuffer data = BufferUtils.createByteBuffer(content.length).put(content);
            data.flip();
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "lynxwave-music-art/" + id, NativeImage.read(data));
            mc.execute(() -> mc.getTextureManager().registerTexture(id, tex));
        } catch (Exception ignored) {
        }
    }
}
