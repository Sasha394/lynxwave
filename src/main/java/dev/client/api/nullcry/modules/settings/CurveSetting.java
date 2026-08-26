package dev.client.api.nullcry.modules.settings;

import dev.client.api.nullcry.uiClient.clickGui.api.setting.Setting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.MathHelper;

import java.util.function.Supplier;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CurveSetting extends Setting {
    float x1;
    float y1;
    float x2;
    float y2;

    public CurveSetting(String name, Supplier<Boolean> shown) {
        super(name, shown);
        applyDefault(0.2f, 0.0f, 0.8f, 1.0f);
    }

    public CurveSetting applyDefault(float x1, float y1, float x2, float y2) {
        float cx1 = clamp01(x1);
        float cy1 = clamp01(y1);
        float cx2 = clamp01(x2);
        float cy2 = clamp01(y2);

        if (cx1 > cx2) {
            float tmpX = cx1;
            cx1 = cx2;
            cx2 = tmpX;

            float tmpY = cy1;
            cy1 = cy2;
            cy2 = tmpY;
        }

        this.x1 = cx1;
        this.y1 = cy1;
        this.x2 = cx2;
        this.y2 = cy2;
        return this;
    }

    public CurveSetting set(float x1, float y1, float x2, float y2) {
        return applyDefault(x1, y1, x2, y2);
    }

    public void setControl1(float x, float y) {
        float cx = clamp01(x);
        float cy = clamp01(y);
        if (cx > x2) cx = x2;
        x1 = cx;
        y1 = cy;
    }

    public void setControl2(float x, float y) {
        float cx = clamp01(x);
        float cy = clamp01(y);
        if (cx < x1) cx = x1;
        x2 = cx;
        y2 = cy;
    }

    public float evaluate(float t) {
        float clamped = MathHelper.clamp(t, 0f, 1f);
        if (clamped <= 0f || clamped >= 1f) {
            return clamped;
        }

        float low = 0f;
        float high = 1f;
        float mid = clamped;
        for (int i = 0; i < 16; i++) {
            mid = (low + high) * 0.5f;
            float x = curveX(mid);
            if (Math.abs(x - clamped) < 1.0e-4f) {
                break;
            }
            if (x < clamped) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return MathHelper.clamp(curveY(mid), 0f, 1f);
    }

    public float curveX(float t) {
        return cubic(t, 0f, x1, x2, 1f);
    }

    public float curveY(float t) {
        return cubic(t, 0f, y1, y2, 1f);
    }

    @Override
    public CurveSetting collection(Collection collection) {
        collection.put(this);
        return this;
    }

    private static float cubic(float t, float p0, float p1, float p2, float p3) {
        float u = 1f - t;
        float tt = t * t;
        float uu = u * u;
        float uuu = uu * u;
        float ttt = tt * t;
        return uuu * p0 + 3f * uu * t * p1 + 3f * u * tt * p2 + ttt * p3;
    }

    private static float clamp01(float value) {
        return MathHelper.clamp(value, 0f, 1f);
    }
}
