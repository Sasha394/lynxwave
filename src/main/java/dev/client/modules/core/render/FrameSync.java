package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.render.framesync.RenderLimiter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * FrameSync — привязывает частоту кадров к частоте монитора.
 * Лишние кадры не рисуются вовсе, поэтому падает нагрузка на GPU и нагрев.
 */
public class FrameSync extends Module implements ClientApi {
    public static FrameSync INSTANCE;

    /** Значение, начиная с которого ванильный ограничитель считается «безлимитным». */
    private static final int VANILLA_UNLIMITED_FPS = 260;

    private final ModeElement source = new ModeElement("Частота", () -> true)
            .set("Авто", "Вручную").defaultValue("Авто").register(this);
    private final Slider targetHz = new Slider("Гц", () -> source.isSelected("Вручную"))
            .set(30f, 500f, 1f).defaultValue(60f).register(this);
    private final Slider minFps = new Slider("Минимальный FPS", () -> true)
            .set(15f, 120f, 1f).defaultValue(30f).register(this);
    private final ModeElement waitMode = new ModeElement("Ожидание кадра", () -> true)
            .set("Точное", "Слабый ПК").defaultValue("Точное").register(this);
    private final CheckBox indicator = new CheckBox("Индикатор FPS", () -> true)
            .defaultValue(false).register(this);

    private final RenderLimiter limiter = new RenderLimiter(RenderLimiter.DEFAULT_REFRESH_RATE);

    private int detectedHz = RenderLimiter.DEFAULT_REFRESH_RATE;

    public FrameSync() {
        super("FrameSync", ModuleCategory.Render, "Синхронизирует частоту кадров с монитором");
    }

    /**
     * Вызывается из GameRendererMixins перед отрисовкой кадра.
     * Возвращает false, если кадр нужно пропустить.
     */
    public static boolean shouldRenderFrame() {
        FrameSync module = INSTANCE;
        // limiter инициализируется последним, так что его наличие = модуль готов к работе.
        if (module == null || module.limiter == null || !module.isEnabled()) return true;

        module.limiter.setMonitorHz(module.getEffectiveHz());
        return module.limiter.awaitFrame(module.waitMode.isSelected("Слабый ПК"));
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        this.refreshDetectedHz();
        this.limiter.setMonitorHz(this.getEffectiveHz());
        this.limiter.reset();
        this.applyVanillaLimit(true);
    }

    @Override
    public void onDisabled() {
        this.applyVanillaLimit(false);
        super.onDisabled();
    }

    @Subscribe
    public void onTick(TickEvent event) {
        this.refreshDetectedHz();
        // Ваниль перезаписывает свой лимит при изменении настроек игры — держим его снятым.
        this.applyVanillaLimit(true);
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!this.indicator.getEnabled() || mc.options.hudHidden || mc.textRenderer == null) return;

        DrawContext context = event.getContext();
        int fps = mc.getCurrentFps();
        int target = this.getEffectiveHz();
        String text = String.format(Locale.ROOT, "FPS: %d / %d Hz", fps, target);

        int width = mc.textRenderer.getWidth(text);
        int x = context.getScaledWindowWidth() - width - 10;
        int y = 10;

        context.fill(x - 2, y - 2, x + width + 2, y + mc.textRenderer.fontHeight + 2, 0x80000000);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(text), x, y, colorFor(fps, target));
    }

    /** Целевая частота с учётом нижнего порога. */
    public int getEffectiveHz() {
        int hz = this.source.isSelected("Авто") ? this.detectedHz : Math.round(this.targetHz.getValue());
        return Math.max(hz, Math.round(this.minFps.getValue()));
    }

    private void refreshDetectedHz() {
        if (!this.source.isSelected("Авто")) return;

        int hz = RenderLimiter.detectMonitorHz();
        if (hz > 0) this.detectedHz = hz;
    }

    /**
     * Снимает ванильный лимит FPS, пока кадрами управляет FrameSync,
     * и возвращает пользовательское значение обратно при выключении.
     */
    private void applyVanillaLimit(boolean frameSyncActive) {
        if (mc.getInactivityFpsLimiter() == null) return;

        int limit = frameSyncActive ? VANILLA_UNLIMITED_FPS : mc.options.getMaxFps().getValue();
        mc.getInactivityFpsLimiter().setMaxFps(limit);
    }

    private static int colorFor(int fps, int target) {
        double ratio = target > 0 ? (double) fps / target : 1.0D;
        if (ratio >= 0.9D) return 0xFF00FF00;
        return ratio >= 0.7D ? 0xFFFFFF00 : 0xFFFF5555;
    }
}
