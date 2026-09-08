package dev.client.modules.core.render.framesync;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.locks.LockSupport;

/**
 * Пейсер кадров: удерживает рендер на заданной частоте (обычно — частоте монитора).
 * Работает на рендер-потоке, поэтому кадр «придерживается» до нужного момента,
 * а не отрисовывается и выбрасывается.
 */
public final class RenderLimiter {
    public static final int DEFAULT_REFRESH_RATE = 60;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    /** Ниже этого остатка парковка уже не выгодна — точнее дождаться спином. */
    private static final long SPIN_THRESHOLD_NANOS = 1_000_000L;
    /** Запас, который добираем спином после парковки (компенсация точности таймера ОС). */
    private static final long SPIN_MARGIN_NANOS = 500_000L;

    private int monitorHz;
    private long frameInterval = NANOS_PER_SECOND / DEFAULT_REFRESH_RATE;
    private long nextRenderTime = System.nanoTime();

    public RenderLimiter(int monitorHz) {
        setMonitorHz(monitorHz);
    }

    public void setMonitorHz(int hz) {
        if (hz <= 0) hz = DEFAULT_REFRESH_RATE;
        if (hz == this.monitorHz) return;

        this.monitorHz = hz;
        this.frameInterval = Math.max(1L, NANOS_PER_SECOND / hz);
        this.reset();
    }

    public int getMonitorHz() {
        return this.monitorHz;
    }

    public void reset() {
        this.nextRenderTime = System.nanoTime();
    }

    /**
     * Ждёт момента следующего кадра.
     *
     * @param saveCpu режим для слабых ПК: только парковка потока, без спина
     * @return false, если кадр нужно пропустить (время ещё не пришло)
     */
    public boolean awaitFrame(boolean saveCpu) {
        long remaining = this.nextRenderTime - System.nanoTime();

        if (saveCpu) {
            if (remaining > 0L) {
                LockSupport.parkNanos(remaining);
            }
        } else {
            if (remaining > SPIN_THRESHOLD_NANOS) {
                LockSupport.parkNanos(remaining - SPIN_MARGIN_NANOS);
            }
            while (System.nanoTime() < this.nextRenderTime && !Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
        }

        return this.markFrame(System.nanoTime());
    }

    private boolean markFrame(long now) {
        if (now < this.nextRenderTime) return false;

        this.nextRenderTime += this.frameInterval;
        // Провалились больше чем на кадр (лаг, сворачивание окна) — не догоняем пачкой кадров.
        if (this.nextRenderTime <= now) {
            this.nextRenderTime = now + this.frameInterval;
        }
        return true;
    }

    /** Частота монитора по данным GLFW. Вызывать только с рендер-потока. */
    public static int detectMonitorHz() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                int refreshRate = client.getWindow().getRefreshRate();
                if (refreshRate > 0) return refreshRate;
            }
        } catch (RuntimeException ignored) {
        }
        return DEFAULT_REFRESH_RATE;
    }
}
