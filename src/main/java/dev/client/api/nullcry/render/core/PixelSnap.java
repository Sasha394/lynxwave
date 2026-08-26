package dev.client.api.nullcry.render.core;

public final class PixelSnap {
    private PixelSnap() {
    }

    public static float position(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }

        double scale = GLUtils.getScaleFactor();
        if (scale <= 0.0d || !Double.isFinite(scale)) {
            return Math.round(value);
        }
        return (float) (Math.round(value * scale) / scale);
    }

    public static float size(float value) {
        if (!Float.isFinite(value)) {
            return 1.0f;
        }

        double scale = GLUtils.getScaleFactor();
        if (scale <= 0.0d || !Double.isFinite(scale)) {
            return Math.max(1.0f, Math.round(value));
        }
        return (float) Math.max(1.0d / scale, Math.round(value * scale) / scale);
    }

    public static float length(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            return 0.0f;
        }

        double scale = GLUtils.getScaleFactor();
        if (scale <= 0.0d || !Double.isFinite(scale)) {
            return Math.max(1.0f, Math.round(value));
        }
        return (float) Math.max(1.0d / scale, Math.round(value * scale) / scale);
    }
}
