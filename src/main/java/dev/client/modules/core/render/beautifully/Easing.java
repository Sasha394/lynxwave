package dev.client.modules.core.render.beautifully;

/**
 * Кривые для анимаций модуля Beautifully.
 * Сигнатура классическая (Robert Penner): время, начало, изменение, длительность.
 */
@FunctionalInterface
public interface Easing {

    float ease(float time, float begin, float change, float duration);

    /** Синус в обе стороны — плавный старт и плавный конец. */
    Easing SINE_IN_OUT = (t, b, c, d) -> -c / 2.0f * ((float) Math.cos(Math.PI * t / d) - 1.0f) + b;

    /** Кубический выход — резкий старт, мягкая остановка. */
    Easing CUBIC_OUT = (t, b, c, d) -> {
        float x = t / d - 1.0f;
        return c * (x * x * x + 1.0f) + b;
    };

    /** Кубический вход — мягкий старт, резкий конец. */
    Easing CUBIC_IN = (t, b, c, d) -> {
        float x = t / d;
        return c * x * x * x + b;
    };

    /** Лёгкий перелёт за цель — используется при открытии таба и инвентаря. */
    Easing BACK_OUT = Easing.cubicBezier(0.45, 1.45, 0.43, 0.91);

    /** Вход в третье лицо: камера отъезжает с лёгким перелётом. */
    Easing F5_IN = Easing.cubicBezier(0.31, 0.87, 0.41, 1.3);

    /** Возврат в первое лицо. */
    Easing F5_OUT = Easing.cubicBezier(0.17, 0.85, 0.29, 0.99);

    /** Разворот камеры при виде спереди. */
    Easing F5_INVERSE = Easing.cubicBezier(0.31, 0.87, 0.43, 0.94);

    /** Кривая CSS cubic-bezier(x1, y1, x2, y2). */
    static Easing cubicBezier(final double x1, final double y1, final double x2, final double y2) {
        return new Easing() {
            @Override
            public float ease(float time, float begin, float change, float duration) {
                if (duration <= 0.0f || time <= 0.0f) return begin;
                if (time >= duration) return begin + change;

                float progress = time / duration;
                float t = this.solveForX((float) x1, (float) x2, progress);
                return begin + change * this.bezier(t, (float) y1, (float) y2);
            }

            /** Метод Ньютона: ищем параметр кривой t, дающий нужный x. */
            private float solveForX(float p1, float p2, float x) {
                float t = x;
                for (int i = 0; i < 8; i++) {
                    float value = this.bezier(t, p1, p2);
                    float slope = this.bezierSlope(t, p1, p2);
                    if (Math.abs(value - x) < 1.0E-5f || Math.abs(slope) < 1.0E-6f) break;
                    t -= (value - x) / slope;
                    t = Math.max(0.0f, Math.min(1.0f, t));
                }
                return t;
            }

            private float bezier(float t, float p1, float p2) {
                float inv = 1.0f - t;
                return 3.0f * inv * inv * t * p1 + 3.0f * inv * t * t * p2 + t * t * t;
            }

            private float bezierSlope(float t, float p1, float p2) {
                float inv = 1.0f - t;
                return 3.0f * (inv * (1.0f - 3.0f * t) * p1 + (2.0f * t - 3.0f * t * t) * p2) + 3.0f * t * t;
            }
        };
    }
}
