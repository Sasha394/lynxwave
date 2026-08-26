package dev.client.modules.core.render.motionblur;

import dev.client.modules.core.render.PostEffectRenderer;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ShaderMotionBlur {
    private final MotionBlur config;

    private long lastNano;
    private float currentFPS = 0.0f;
    private boolean frameDataReady;

    public ShaderMotionBlur(MotionBlur config) {
        this.config = config;
    }

    public void onFrame() {
        long now = System.nanoTime();
        if (lastNano == 0L) {
            lastNano = now;
            return;
        }

        float deltaTime = (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;

        if (deltaTime > 0 && deltaTime < 1.0f) {
            currentFPS = 1.0f / deltaTime;
        } else {
            currentFPS = 0.0f;
        }

        if (shouldRenderMotionBlur()) {
            applyMotionBlur();
        }
    }

    private boolean shouldRenderMotionBlur() {
        if (!frameDataReady || !config.isEnabled() || config.strength.getValue() == 0) {
            return false;
        }
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            config.setEnabled(false);
            return false;
        }
        return true;
    }

    private void applyMotionBlur() {
        MonitorInfoProvider.updateDisplayInfo();
        int displayRefreshRate = MonitorInfoProvider.getRefreshRate();

        float baseStrength = config.strength.getValue();
        float scaledStrength = baseStrength;
        if (config.useRRC.getEnabled()) {
            float fpsOverRefresh = (displayRefreshRate > 0) ? currentFPS / displayRefreshRate : 1.0f;
            if (fpsOverRefresh < 1.0f) fpsOverRefresh = 1.0f;
            scaledStrength = baseStrength * fpsOverRefresh;
        }
        updateBlurStrength(Math.max(-2.0f, Math.min(2.0f, scaledStrength)));
    }

    private int getSampleAmountForFPS(float fps) {
        if (fps > 360) return 8;
        else if (fps > 120) return 10;
        else if (fps > 60) return 12;
        else return 20;
    }

    private final Matrix4f tempPrevModelView = new Matrix4f();
    private final Matrix4f tempPrevProjection = new Matrix4f();
    private final Matrix4f tempProjInverse = new Matrix4f();
    private final Matrix4f tempMvInverse = new Matrix4f();

    public void setFrameMotionBlur(Matrix4f modelView, Matrix4f prevModelView,
                                   Matrix4f projection, Matrix4f prevProjection,
                                   Vector3f cameraPos, Vector3f prevCameraPos) {
        tempMvInverse.set(modelView).invert();
        tempProjInverse.set(projection).invert();
        tempPrevModelView.set(prevModelView);
        tempPrevProjection.set(prevProjection);
        this.cameraPos.set(cameraPos);
        this.prevCameraPos.set(prevCameraPos);
        frameDataReady = true;
    }

    private final Vector3f cameraPos = new Vector3f();
    private final Vector3f prevCameraPos = new Vector3f();

    public void updateBlurStrength(float strength) {
        int samples = Math.max(1, getSampleAmountForFPS(currentFPS));
        int halfSamples = samples / 2;
        float inverseSamples = 1.0f / samples;
        int algorithm = MotionBlur.blurAlgorithm == MotionBlur.BlurAlgorithm.CENTERED ? 1 : 0;

        PostEffectRenderer.renderMotionBlur(
                strength,
                samples,
                halfSamples,
                inverseSamples,
                algorithm,
                tempMvInverse,
                tempProjInverse,
                tempPrevModelView,
                tempPrevProjection,
                cameraPos,
                prevCameraPos
        );
    }
}
