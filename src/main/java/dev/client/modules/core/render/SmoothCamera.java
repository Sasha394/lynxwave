package dev.client.modules.core.render;

import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

public class SmoothCamera extends Module {

    public static SmoothCamera INSTANCE;

    private final CheckBox enableFirstPOV = new CheckBox("Включить первый просмотр от первого лица", () -> true)
            .defaultValue(false)
            .register(this);

    private final CheckBox resetOnPerspectiveChange = new CheckBox("Сброс при изменении перспективы", () -> true)
            .defaultValue(true)
            .register(this);

    private final Slider factorH = new Slider("Горизонтальный фактор", () -> true)
            .set(0.0f, 1.0f, 0.01f)
            .defaultValue(0.90f)
            .register(this);

    private final Slider factorV = new Slider("Вертикальный фактор", () -> true)
            .set(0.0f, 1.0f, 0.01f)
            .defaultValue(0.93f)
            .register(this);

    private Vec3d smoothPos = Vec3d.ZERO;

    private Perspective lastPerspective = null;

    public SmoothCamera() {
        super("SmoothCamera", ModuleCategory.Render, "Делает движение вашей камеры более плавным");
        INSTANCE = this;
    }

    @Override
    public void onDisabled() {
        smoothPos = Vec3d.ZERO;
        super.onDisabled();
    }

    public Vec3d getSmoothPos() {
        return smoothPos;
    }

    public void cameraUpdate(Vec3d pos) {
        Perspective perspective = mc.options != null ? mc.options.getPerspective() : null;

        if (!isEnabled()) {
            lastPerspective = perspective;
            return;
        }

        if (resetOnPerspectiveChange.getEnabled() && lastPerspective != null && perspective != null && lastPerspective != perspective) {
            smoothPos = pos;
            lastPerspective = perspective;
            return;
        }

        lastPerspective = perspective;

        if (!enableFirstPOV.getEnabled() && perspective != null && perspective.isFirstPerson()) {
            smoothPos = pos;
            return;
        }

        if (isLikelyZero(smoothPos)) {
            smoothPos = pos;
        }

        double fh = clamp01(factorH.getValue());
        double fv = clamp01(factorV.getValue());

        smoothPos = new Vec3d(
                smoothPos.x * fh + pos.x * (1.0 - fh),
                smoothPos.y * fv + pos.y * (1.0 - fv),
                smoothPos.z * fh + pos.z * (1.0 - fh)
        );
    }

    public boolean shouldApplyChanges() {
        return isEnabled();
    }

    private static boolean isLikelyZero(Vec3d v) {
        return v == null || (Math.abs(v.x) < 1.0E-12 && Math.abs(v.y) < 1.0E-12 && Math.abs(v.z) < 1.0E-12);
    }

    private static double clamp01(float v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

