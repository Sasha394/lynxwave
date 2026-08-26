package dev.client.modules.core.render;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.RenderCoreCompat;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public class BlockHighLight extends Module {
    public static BlockHighLight INSTANCE;

    private static final int CONFIG_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final RenderPipeline BLOCK_HIGHLIGHT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("lynxwave", "pipeline/world/block_highlight"))
                    .withVertexShader(Identifier.of("lynxwave", "core/block_highlight"))
                    .withFragmentShader(Identifier.of("lynxwave", "core/block_highlight"))
                    .withUniform("BlockHighlightConfig", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public BlockHighLight() {
        super("BlockHighLight", ModuleCategory.Render, "Кастомная подсветка блока");
        INSTANCE = this;
    }

    private final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("Обычный", "Звёзды", "Волна", "Звёзды 2", "Туманность", "Фракталы", "Марш", "Космос", "Плазма", "Кислота", "Огонь", "Матрица", "Звёзды (космос)")
            .defaultValue("Обычный")
            .register(this);

    private final Slider lineWidth = new Slider("Толщина линий", () -> true)
            .set(0.5f, 5.0f, 0.5f).defaultValue(2.0f).register(this);

    private final Slider fillAlpha = new Slider("Прозрачность заливки", () -> true)
            .set(0f, 255f, 5f).defaultValue(50f).register(this);

    private final Slider animSpeed = new Slider("Скорость анимации", () -> true)
            .set(100f, 1000f, 50f).defaultValue(300f).register(this);

    private BlockPos targetBlock = null;
    private double renderMinX, renderMinY, renderMinZ;
    private double renderMaxX, renderMaxY, renderMaxZ;
    private boolean initialized = false;
    private long lastFrameTime = 0;

    @Override
    public void onDisabled() {
        targetBlock = null;
        initialized = false;
        lastFrameTime = 0;
        super.onDisabled();
    }

    @Subscribe
    public void onWorldRender(RenderEvent.Draw3D event) {
        if (mc.world == null || mc.player == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        Box shapeBox = shape.getBoundingBox().offset(pos);

        if (!initialized) {
            renderMinX = shapeBox.minX; renderMinY = shapeBox.minY; renderMinZ = shapeBox.minZ;
            renderMaxX = shapeBox.maxX; renderMaxY = shapeBox.maxY; renderMaxZ = shapeBox.maxZ;
            targetBlock = pos;
            initialized = true;
        } else {
            targetBlock = pos;
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameTime) / 1000.0f, 0.1f);
        lastFrameTime = now;

        float halfLife = (float) (animSpeed.getValue() / 1000.0) * 0.15f;
        halfLife = Math.max(0.005f, halfLife);
        float alpha = 1.0f - (float) Math.pow(0.5, dt / halfLife);

        renderMinX = lerp(renderMinX, shapeBox.minX, alpha);
        renderMinY = lerp(renderMinY, shapeBox.minY, alpha);
        renderMinZ = lerp(renderMinZ, shapeBox.minZ, alpha);
        renderMaxX = lerp(renderMaxX, shapeBox.maxX, alpha);
        renderMaxY = lerp(renderMaxY, shapeBox.maxY, alpha);
        renderMaxZ = lerp(renderMaxZ, shapeBox.maxZ, alpha);

        renderBox(event.getMatrices());
    }

    private void renderBox(MatrixStack matrices) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Box box = new Box(
                renderMinX - cam.x, renderMinY - cam.y, renderMinZ - cam.z,
                renderMaxX - cam.x, renderMaxY - cam.y, renderMaxZ - cam.z
        ).expand(0.001);

        int color = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : -1;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float fa = (float) (fillAlpha.getValue() / 255.0);
        int shaderMode = mode.getValues().indexOf(mode.getValue());
        if (shaderMode < 0) shaderMode = 0;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(RenderSystem.SRC_ALPHA, RenderSystem.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        if (fa > 0) {
            if (shaderMode > 0) {
                BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                drawFilled(matrices.peek().getPositionMatrix(), buf, box, r, g, b, fa);
                GpuBuffer config = createConfig(shaderMode);
                RenderCoreCompat.draw(BLOCK_HIGHLIGHT_PIPELINE, buf.end(),
                        new RenderCoreCompat.UniformBinding[] { RenderCoreCompat.uniform("BlockHighlightConfig", config) });
            } else {
                BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                drawFilled(matrices.peek().getPositionMatrix(), buf, box, r, g, b, fa);
                RenderCompat.draw(buf.end());
            }
        }
        RenderSystem.lineWidth(lineWidth.getValue());
        for (int i = 0; i < 3; i++) {
            float la = i == 0 ? 1.0f : (i == 1 ? 0.5f : 0.2f);
            Box glowBox = box.expand(i * 0.002);
            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            drawOutline(matrices.peek().getPositionMatrix(), buf, glowBox, r, g, b, la);
            RenderCompat.draw(buf.end());
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private GpuBuffer createConfig(int shaderMode) {
        float time = (float) ((System.currentTimeMillis() % 1000000L) / 1000.0);
        float resW = Math.max(1.0f, mc.getWindow().getFramebufferWidth());
        float resH = Math.max(1.0f, mc.getWindow().getFramebufferHeight());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(time, (float) shaderMode, resW, resH);
            return com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .createBuffer(() -> "lynxwave block highlight config", GpuBuffer.USAGE_UNIFORM, builder.get());
        }
    }

    private void drawFilled(Matrix4f m, BufferBuilder buf, Box b,
                            float r, float g, float bl, float a) {
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        buf.vertex(m, x1, y1, z1).color(r, g, bl, a); buf.vertex(m, x2, y1, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y1, z2).color(r, g, bl, a); buf.vertex(m, x1, y1, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y2, z1).color(r, g, bl, a); buf.vertex(m, x1, y2, z2).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z2).color(r, g, bl, a); buf.vertex(m, x2, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x1, y1, z1).color(r, g, bl, a); buf.vertex(m, x1, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z1).color(r, g, bl, a); buf.vertex(m, x2, y1, z1).color(r, g, bl, a);
        buf.vertex(m, x1, y1, z2).color(r, g, bl, a); buf.vertex(m, x2, y1, z2).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z2).color(r, g, bl, a); buf.vertex(m, x1, y2, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y1, z1).color(r, g, bl, a); buf.vertex(m, x1, y1, z2).color(r, g, bl, a);
        buf.vertex(m, x1, y2, z2).color(r, g, bl, a); buf.vertex(m, x1, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y1, z1).color(r, g, bl, a); buf.vertex(m, x2, y2, z1).color(r, g, bl, a);
        buf.vertex(m, x2, y2, z2).color(r, g, bl, a); buf.vertex(m, x2, y1, z2).color(r, g, bl, a);
    }

    private void drawOutline(Matrix4f m, BufferBuilder buf, Box b,
                             float r, float g, float bl, float a) {
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        line(buf, m, x1, y1, z1, x2, y1, z1, r, g, bl, a); line(buf, m, x2, y1, z1, x2, y1, z2, r, g, bl, a);
        line(buf, m, x2, y1, z2, x1, y1, z2, r, g, bl, a); line(buf, m, x1, y1, z2, x1, y1, z1, r, g, bl, a);
        line(buf, m, x1, y2, z1, x2, y2, z1, r, g, bl, a); line(buf, m, x2, y2, z1, x2, y2, z2, r, g, bl, a);
        line(buf, m, x2, y2, z2, x1, y2, z2, r, g, bl, a); line(buf, m, x1, y2, z2, x1, y2, z1, r, g, bl, a);
        line(buf, m, x1, y1, z1, x1, y2, z1, r, g, bl, a); line(buf, m, x2, y1, z1, x2, y2, z1, r, g, bl, a);
        line(buf, m, x2, y1, z2, x2, y2, z2, r, g, bl, a); line(buf, m, x1, y1, z2, x1, y2, z2, r, g, bl, a);
    }

    private void line(BufferBuilder buf, Matrix4f m,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float r, float g, float bl, float a) {
        buf.vertex(m, ax, ay, az).color(r, g, bl, a);
        buf.vertex(m, bx, by, bz).color(r, g, bl, a);
    }

    private double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }
}
