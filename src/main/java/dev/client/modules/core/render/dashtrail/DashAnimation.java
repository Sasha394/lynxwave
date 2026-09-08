package dev.client.modules.core.render.dashtrail;

/**
 * Значение, догоняющее цель с фиксированным шагом по 5 мс реального времени.
 * <p>
 * Скорость не зависит от FPS: сколько прошло миллисекунд — столько шагов и делается,
 * но не больше чем за 400 мс, иначе после лага или паузы след «телепортируется».
 */
public final class DashAnimation {

    private static final float STEP_MS = 5.0f;
    private static final float MAX_CATCH_UP_MS = 400.0f;
    private static final float SNAP_EPSILON = 1.0E-4F;

    public float value;
    public float target;
    public final float speed;

    private long lastUpdate;

    public DashAnimation(float value, float target, float speed) {
        this.value = value;
        this.target = target;
        this.speed = speed;
        this.lastUpdate = System.currentTimeMillis();
    }

    public float get() {
        if (Math.abs(this.value - this.target) < SNAP_EPSILON) {
            this.value = this.target;
        }

        long now = System.currentTimeMillis();
        int steps = (int) (Math.min(now - this.lastUpdate, MAX_CATCH_UP_MS) / STEP_MS);
        if (steps > 0) {
            this.lastUpdate = now;
        }

        for (int i = 0; i < steps; i++) {
            this.value += (this.target - this.value) * this.speed;
        }

        return this.value;
    }

    public void set(float value) {
        this.value = value;
        this.lastUpdate = System.currentTimeMillis();
    }
}
