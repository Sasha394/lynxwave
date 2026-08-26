package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.render.ShaderHand2DEvent;
import dev.client.api.nullcry.events.core.render.ShaderHandEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.renderers.core.HandBlurRenderer;
import dev.client.api.nullcry.render.core.renderers.core.HandHighlightRenderer;
import dev.client.api.nullcry.render.core.renderers.core.handflame.HandShaderRenderer;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.joml.Matrix4f;

public class ShaderHand extends Module {
    public static ShaderHand INSTANCE;

    public final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Обычный", "Шейдер", "Пламя")
            .defaultValue("Обычный")
            .register(this);

    public final ModeElement shaderStyle = new ModeElement("Стиль шейдера", () -> mode.isSelected("Шейдер"))
            .set("Звёзды", "Волна", "Звёзды 2",
                    "Туманность", "Фракталы", "Марш", "Космос",
                    "Плазма", "Кислота", "Огонь", "Матрица", "Звёзды (космос)")
            .defaultValue("Звёзды")
            .register(this);

    public final ModeElement colorMode = new ModeElement("Режим цвета", () -> !mode.isSelected("Пламя"))
            .set("Клиентский", "Свой")
            .defaultValue("Клиентский")
            .register(this);

    public final ColorPicker tintColor = new ColorPicker("Цвет",
            () -> colorMode.isSelected("Свой") && !mode.isSelected("Пламя"))
            .set(-1).defaultValue(-1).register(this);

    public final Slider opacity = new Slider("Прозрачность", () -> true)
            .set(0.05f, 1.0f, 0.05f).defaultValue(1.0f).register(this);

    public final Slider blurRadius = new Slider("Радиус размытия", () -> mode.isSelected("Обычный"))
            .set(0f, 30f, 1f).defaultValue(12f).register(this);

    public final Slider threshold = new Slider("Порог маски", () -> !mode.isSelected("Пламя"))
            .set(0.001f, 0.1f, 0.001f).defaultValue(0.01f).register(this);

    // --- Настройки режима «Пламя» (trail-пламя, перенесено из w3y.soul) ---
    public final ModeElement fireColorMode = new ModeElement("Цвет пламени", () -> mode.isSelected("Пламя"))
            .set("По предмету", "Клиентский")
            .defaultValue("По предмету")
            .register(this);

    public final Slider fireIntensity = new Slider("Интенсивность", () -> mode.isSelected("Пламя"))
            .set(0.1f, 1.5f, 0.01f).defaultValue(0.85f).register(this);

    public final Slider fireSpeed = new Slider("Скорость", () -> mode.isSelected("Пламя"))
            .set(0.2f, 3.0f, 0.01f).defaultValue(1.15f).register(this);

    public final Slider fireLength = new Slider("Длина", () -> mode.isSelected("Пламя"))
            .set(0.1f, 1.0f, 0.01f).defaultValue(0.55f).register(this);

    public final Slider fireTrailFade = new Slider("Затухание шлейфа", () -> mode.isSelected("Пламя"))
            .set(0.55f, 0.96f, 0.01f).defaultValue(0.84f).register(this);

    public final Slider fireTrailSoftness = new Slider("Мягкость шлейфа", () -> mode.isSelected("Пламя"))
            .set(0.45f, 2.5f, 0.01f).defaultValue(1.35f).register(this);

    public final Slider fireTrailBlur = new Slider("Размытие шлейфа", () -> mode.isSelected("Пламя"))
            .set(0.2f, 3.0f, 0.01f).defaultValue(1.55f).register(this);

    public final Slider fireHandFade = new Slider("Затухание у руки", () -> mode.isSelected("Пламя"))
            .set(0.45f, 0.9f, 0.01f).defaultValue(0.68f).register(this);

    public final Slider fireHandSoftness = new Slider("Мягкость у руки", () -> mode.isSelected("Пламя"))
            .set(0.45f, 2.5f, 0.01f).defaultValue(1.3f).register(this);

    public final Slider fireHandBlur = new Slider("Размытие у руки", () -> mode.isSelected("Пламя"))
            .set(0.2f, 3.0f, 0.01f).defaultValue(1.45f).register(this);

    public final Slider fireSmoke = new Slider("Дым", () -> mode.isSelected("Пламя"))
            .set(0.0f, 0.8f, 0.01f).defaultValue(0.55f).register(this);

    private Framebuffer savedBefore;
    private Framebuffer savedAfter;
    private final long startTime = System.currentTimeMillis();

    public ShaderHand() {
        super("ShaderHand", ModuleCategory.Render, "Окрашивает руку от первого лица в цвет клиента");
        INSTANCE = this;
    }

    @Override
    public void onDisabled() {
        HandShaderRenderer.getInstance().setEnabled(false);
        super.onDisabled();
    }

    @Subscribe
    public void onShader(ShaderHandEvent event) {
        this.savedBefore = event.getBeforeHandFbo();
        this.savedAfter = event.getWithHandFbo();
    }

    @Subscribe
    public void onHandRender2D(ShaderHand2DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) return;
        if (isCameraInWater()) return;

        if (mode.isSelected("Пламя")) {
            updateFlameSettings();
            HandShaderRenderer.getInstance().renderFireEffect();
            return;
        }

        if (!hasValidFramebuffers(mc)) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        if (w <= 0 || h <= 0) return;

        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(event.getContext());
        float[] color = ColorUtils.rgba(getTintColor());
        float thr = threshold.getValue().floatValue();
        float op = opacity.getValue().floatValue();

        if (mode.isSelected("Шейдер")) {
            float time = (System.currentTimeMillis() - startTime) / 1000f;
            float[] baseColor = new float[] { color[0], color[1], color[2] };
            String shader;
            if (shaderStyle.isSelected("Волна")) shader = "hand_wave";
            else if (shaderStyle.isSelected("Звёзды 2")) shader = "hand_stars2";
            else if (shaderStyle.isSelected("Туманность")) shader = "hand_bh1";
            else if (shaderStyle.isSelected("Фракталы")) shader = "hand_bh2";
            else if (shaderStyle.isSelected("Марш")) shader = "hand_bh3";
            else if (shaderStyle.isSelected("Космос")) shader = "hand_bh4";
            else if (shaderStyle.isSelected("Плазма")) shader = "hand_bh5";
            else if (shaderStyle.isSelected("Кислота")) shader = "hand_bh6";
            else if (shaderStyle.isSelected("Огонь")) shader = "hand_bh7";
            else if (shaderStyle.isSelected("Матрица")) shader = "hand_bh8";
            else if (shaderStyle.isSelected("Звёзды (космос)")) shader = "hand_bh_space";
            else shader = "hand_highlight";
            new HandHighlightRenderer(savedAfter, savedBefore, time, thr, op, baseColor, w, h, shader).render(matrix);
        } else {
            float[] tint = new float[] { color[0], color[1], color[2], op };
            float radius = blurRadius.getValue().floatValue();
            new HandBlurRenderer(savedAfter, savedBefore, radius, thr, tint, w, h).render(matrix);
        }
    }

    private int getTintColor() {
        if (colorMode.isSelected("Клиентский")) {
            return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
        }
        return tintColor.getColorRGBA();
    }

    private void updateFlameSettings() {
        HandShaderRenderer r = HandShaderRenderer.getInstance();
        r.setEnabled(true);
        r.setIntensity(fireIntensity.getValue().floatValue());
        r.setRadius(0.35f);
        r.setSpeed(fireSpeed.getValue().floatValue());
        r.setFlameHeight(fireLength.getValue().floatValue());
        r.setTrailFade(fireTrailFade.getValue().floatValue());
        r.setTrailSoftness(fireTrailSoftness.getValue().floatValue());
        r.setTrailBlurRadius(fireTrailBlur.getValue().floatValue());
        r.setHandFade(fireHandFade.getValue().floatValue());
        r.setHandSoftness(fireHandSoftness.getValue().floatValue());
        r.setHandBlurRadius(fireHandBlur.getValue().floatValue());
        r.setSmoke(fireSmoke.getValue().floatValue());

        float[] c = resolveFlameColor();
        r.setColor(c[0], c[1], c[2], opacity.getValue().floatValue());
    }

    private float[] resolveFlameColor() {
        if (fireColorMode.isSelected("Клиентский")) {
            int rgb = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
            float[] c = ColorUtils.rgba(rgb);
            return new float[] { c[0], c[1], c[2] };
        }
        return heldItemColor();
    }

    // Цвет по материалу предмета в руке (как в исходнике w3y.soul), иначе светло-серый.
    private float[] heldItemColor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[] { 1f, 1f, 1f };

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) stack = mc.player.getOffHandStack();
        if (stack.isEmpty()) return new float[] { 1f, 1f, 1f };

        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.contains("diamond")) return rgb(0x55DDE0);
        if (path.contains("netherite")) return rgb(0x5B4A67);
        if (path.contains("gold")) return rgb(0xFFD45A);
        if (path.contains("iron")) return rgb(0xD8DEE8);
        if (path.contains("emerald")) return rgb(0x35D06F);
        if (path.contains("redstone")) return rgb(0xE23B3B);
        if (path.contains("lapis")) return rgb(0x3156D4);
        return rgb(0xE6E6E6);
    }

    private static float[] rgb(int hex) {
        return new float[] {
                ((hex >> 16) & 0xFF) / 255.0f,
                ((hex >> 8) & 0xFF) / 255.0f,
                (hex & 0xFF) / 255.0f
        };
    }

    /**
     * Активен ли режим «Пламя» прямо сейчас — используется в {@code GameRendererMixins}
     * для захвата кадра до/после руки. Должно совпадать с условиями в {@link #onHandRender2D}.
     */
    public static boolean flameActive() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (!INSTANCE.mode.isSelected("Пламя")) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) return false;
        return !isCameraInWater();
    }

    private boolean hasValidFramebuffers(MinecraftClient mc) {
        if (savedBefore == null || savedAfter == null) return false;
        if (savedBefore.getColorAttachment() == null || savedAfter.getColorAttachment() == null) return false;
        if (savedBefore.getColorAttachmentView() == null || savedAfter.getColorAttachmentView() == null) return false;
        if (savedBefore.textureWidth <= 0 || savedBefore.textureHeight <= 0) return false;
        if (savedAfter.textureWidth <= 0 || savedAfter.textureHeight <= 0) return false;
        if (savedBefore.textureWidth != savedAfter.textureWidth || savedBefore.textureHeight != savedAfter.textureHeight) return false;

        Framebuffer current = mc.getFramebuffer();
        return current == null
                || (savedBefore.textureWidth == current.textureWidth
                && savedBefore.textureHeight == current.textureHeight);
    }

    private static boolean isCameraInWater() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera == null) return false;
        CameraSubmersionType type = camera.getSubmersionType();
        return type == CameraSubmersionType.WATER || type == CameraSubmersionType.LAVA;
    }
}
