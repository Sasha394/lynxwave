package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.world.FogEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;

import java.time.LocalTime;
import java.util.OptionalInt;

public class CustomWorld extends Module {
    public static CustomWorld INSTANCE;

    private static final int SKY_SHADER_DATA_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();
    private static final RenderPipeline SKY_CAUSTIC_PIPELINE = createSkyPipeline("caustic");
    private static final RenderPipeline SKY_GALAXY_PIPELINE = createSkyPipeline("galaxy");
    private static final RenderPipeline SKY_NEBULA_PIPELINE = createSkyPipeline("nebula");
    private static final RenderPipeline SKY_RADIANT_PIPELINE = createSkyPipeline("radiant");
    private static final RenderPipeline SKY_SPACE_PIPELINE = createSkyPipeline("space");
    private static final RenderPipeline SKY_SUNSET_PIPELINE = createSkyPipeline("sunset");

    private static RenderPipeline createSkyPipeline(String shaderName) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                        .withLocation(Identifier.of("lynxwave", "pipeline/world/custom_sky_" + shaderName))
                        .withVertexShader(Identifier.of("lynxwave", "core/custom_sky"))
                        .withFragmentShader(Identifier.of("lynxwave", "core/custom_sky_" + shaderName))
                        .withUniform("SkyShaderData", UniformType.UNIFORM_BUFFER)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .build()
        );
    }

    public CustomWorld() {
        super("CustomWorld", ModuleCategory.Render, "Позволяет изменять время суток и туман");
        PostEffectRenderer.init();
        INSTANCE = this;
    }

    public SelectElements mode = new SelectElements("Менять", () -> true).set("Время","Туман","Насыщенность").defaultValue("Время").register(this);
    public ModeElement skyMode = new ModeElement("Небо", () -> true)
            .set("Обычный", "Шейдер")
            .defaultValue("Обычный")
            .register(this);
    public ModeElement skyShader = new ModeElement("Шейдер", this::isShaderSky)
            .set("Caustic", "Galaxy", "Nebula", "Radiant", "Space", "Sunset")
            .defaultValue("Caustic")
            .register(this);
    private final Slider skyIntensity = new Slider("Интенсивность неба", this::isShaderSky)
            .set(0.1f, 2.0f, 0.05f).defaultValue(1.0f).register(this);
    private final Slider skyOpacity = new Slider("Прозрачность неба", this::isShaderSky)
            .set(0.1f, 1.0f, 0.05f).defaultValue(1.0f).register(this);
    private final Slider skySpeed = new Slider("Скорость неба", this::isShaderSky)
            .set(0.1f, 3.0f, 0.1f).defaultValue(1.0f).register(this);
    ModeElement time = new ModeElement("Время суток",() -> mode.isSelected("Время")).set("Рассвет","Утро","День","Вечер","Заход солнца", "Ночь", "Реальное").defaultValue("День").register(this);

    Slider distance = new Slider("Дистанция туманна", () -> mode.isSelected("Туман")).set(10, 225, 5).defaultValue(80).register(this);

    ModeElement colorMode = new ModeElement("Режим цвета", () -> mode.isSelected("Туман")).set("Клиентский", "Свой").defaultValue("Клиентский").register(this);
    ColorPicker customColor = new ColorPicker("Цвет тумана", () -> colorMode.isSelected("Свой")).set(-1).defaultValue(-1).register(this);

    public Slider saturation = new Slider("Сила насыщенности", () -> mode.isSelected("Насыщенность")).set(0f, 100f, 0.05f).defaultValue(1f).register(this);

    long newTime;

    public boolean isShaderSky() {
        return skyMode.isSelected("Шейдер");
    }

    public void renderShaderSky(Framebuffer framebuffer, Camera camera) {
        if (framebuffer == null || framebuffer.getColorAttachmentView() == null) {
            return;
        }

        try (GpuBuffer shaderData = createSkyShaderData(framebuffer, camera);
             RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                     () -> "lynxwave custom sky",
                     framebuffer.getColorAttachmentView(),
                     OptionalInt.empty()
             )) {
            pass.setPipeline(getSkyPipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("SkyShaderData", shaderData);
            pass.draw(0, 3);
        }
    }

    private RenderPipeline getSkyPipeline() {
        return switch (skyShader.getValue()) {
            case "Galaxy" -> SKY_GALAXY_PIPELINE;
            case "Nebula" -> SKY_NEBULA_PIPELINE;
            case "Radiant" -> SKY_RADIANT_PIPELINE;
            case "Space" -> SKY_SPACE_PIPELINE;
            case "Sunset" -> SKY_SUNSET_PIPELINE;
            default -> SKY_CAUSTIC_PIPELINE;
        };
    }

    private GpuBuffer createSkyShaderData(Framebuffer framebuffer, Camera camera) {
        float width = Math.max(1.0f, framebuffer.textureWidth);
        float height = Math.max(1.0f, framebuffer.textureHeight);
        float shaderTime = (System.currentTimeMillis() % 3_600_000L) / 1000.0f;
        float fov = mc.options != null ? mc.options.getFov().getValue() : 70.0f;
        Quaternionf rotation = camera.getRotation();

        int zenith = interfaceColor(0);
        int horizon = interfaceColor(4);
        int nebula1 = interfaceColor(0);
        int nebula2 = interfaceColor(8);
        int stars = interfaceColor(12);
        int fog = interfaceColor(4);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, SKY_SHADER_DATA_SIZE)
                    .putVec4(width, height, shaderTime, skySpeed.getValue())
                    .putVec4(skyIntensity.getValue(), skyOpacity.getValue(), fov, 0.0f)
                    .putVec4(rotation.x, rotation.y, rotation.z, rotation.w);
            putColor(builder, zenith);
            putColor(builder, horizon);
            putColor(builder, nebula1);
            putColor(builder, nebula2);
            putColor(builder, stars);
            putColor(builder, fog);
            builder.putVec4(0.0f, 1.0f, 0.0f, 0.0f);
            return RenderSystem.getDevice().createBuffer(
                    () -> "lynxwave custom sky shader data",
                    GpuBuffer.USAGE_UNIFORM,
                    builder.get()
            );
        }
    }

    private int interfaceColor(int index) {
        return Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor(index) : 0xFFFFFFFF;
    }

    private static void putColor(Std140Builder builder, int color) {
        builder.putVec4(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >>> 24) & 0xFF) / 255.0f
        );
    }

    @Subscribe
    public void onFog(FogEvent e) {
        if (mode.isSelected("Туман")) {
            e.setDistance(255 - distance.getValue());
            e.setColor(colorMode.isSelected("Клиентский") ? Interface.INSTANCE.getMainColor() : customColor.getColorRGBA());
            e.setCancelled(true);
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket packet) {
            if (mc.world == null) return;

            event.setCancelled(true);

            switch (time.getValue()) {
                case "Рассвет":
                    newTime = 0L;
                    break;
                case "Утро":
                    newTime = 1000L;
                    break;
                case "День":
                    newTime = 6000L;
                    break;
                case "Вечер":
                    newTime = 12000L;
                    break;
                case "Заход солнца":
                    newTime = 13000L;
                    break;
                case "Ночь":
                    newTime = 18000L;
                    break;
                case "Реальное":
                    newTime = getRealWorldTime();
                    break;
                default:
                    return;
            }

            mc.world.setTime(newTime, newTime, true);
        }
    }

    private long getRealWorldTime() {
        LocalTime now = LocalTime.now();
        int hours = now.getHour();
        int minutes = now.getMinute();
        int adjustedHours = (hours + 18) % 24;
        long mcTime = (adjustedHours * 1000) + (minutes * (1000 / 60));
        return mcTime % 24000;
    }
}
