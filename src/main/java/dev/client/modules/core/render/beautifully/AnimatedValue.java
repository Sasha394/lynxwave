package dev.client.modules.core.render.beautifully;

/**
 * Значение, догоняющее цель по заданной кривой.
 * Цель можно менять на лету — анимация продолжится от текущей позиции.
 */
public class AnimatedValue {
    private long duration;
    private Easing easing;

    private float current;
    private float from;
    private float target;
    private long startTime;
    private boolean finished = true;

    public AnimatedValue(long duration, Easing easing) {
        this(duration, 0.0f, easing);
    }

    public AnimatedValue(long duration, float value, Easing easing) {
        this.duration = duration;
        this.easing = easing;
        this.current = value;
        this.from = value;
        this.target = value;
    }

    /** Продвигает анимацию к цели и возвращает текущее значение. */
    public float update(float target) {
        long now = System.currentTimeMillis();

        if (target != this.target) {
            this.from = this.current;
            this.target = target;
            this.startTime = now;
            this.finished = false;
        }

        long elapsed = now - this.startTime;
        if (elapsed >= this.duration) {
            this.current = this.target;
            this.finished = true;
            return this.current;
        }

        float progress = (float) elapsed / (float) this.duration;
        float eased = this.easing.ease(progress, 0.0f, 1.0f, 1.0f);
        this.current = this.from + (this.target - this.from) * eased;
        return this.current;
    }

    /** Мгновенно ставит значение без анимации. */
    public void snap(float value) {
        this.current = value;
        this.from = value;
        this.target = value;
        this.finished = true;
    }

    public float get() {
        return this.current;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }
}
