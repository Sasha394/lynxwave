package dev.client.modules.core.render.motionblur;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.render.PostEffectRenderer;

public class MotionBlur extends Module {
    public static MotionBlur INSTANCE;

    public final Slider strength = new Slider("Сила", () -> true)
            .set(-2f, 2f, 0.1f)
            .defaultValue(-0.8f)
            .register(this);

    public final CheckBox useRRC = new CheckBox("Масштаб по частоте", () -> true)
            .defaultValue(true)
            .register(this);

    public final ShaderMotionBlur shader;

    public MotionBlur() {
        super("MotionBlur", ModuleCategory.Render, "Размытие в движении");
        PostEffectRenderer.init();
        this.shader = new ShaderMotionBlur(this);
    }

    @Subscribe
    public void onRender(RenderEvent.Draw3D event) {
        shader.onFrame();
    }

    public enum BlurAlgorithm { BACKWARDS, CENTERED }
    public static BlurAlgorithm blurAlgorithm = BlurAlgorithm.CENTERED;
}
