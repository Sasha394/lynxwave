package dev.client.modules.core.render.particles;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.EaseInOutQuad;
import dev.client.api.nullcry.render.core.LynxEspPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

public class Particle3D implements ClientApi {

    public enum ParticleMode {
        CUBES,
        CRYSTAL,
        CROWN,
        CUBE_BLAST,
        DOLLAR,
        HEART,
        LIGHTNING,
        LINE,
        RHOMBUS,
        SNOWFLAKE,
        STAR,
        STAR_ALT,
        TRIANGLE,
        RANDOM
    }

    public enum GlowMode {
        BLOOM,
        BLOOM_SAMPLE,
        BOTH
    }

    private static final ParticleMode[] RANDOM_MODES = {
            ParticleMode.CUBES,
            ParticleMode.CRYSTAL,
            ParticleMode.CROWN,
            ParticleMode.CUBE_BLAST,
            ParticleMode.DOLLAR,
            ParticleMode.HEART,
            ParticleMode.LIGHTNING,
            ParticleMode.LINE,
            ParticleMode.RHOMBUS,
            ParticleMode.SNOWFLAKE,
            ParticleMode.STAR,
            ParticleMode.STAR_ALT,
            ParticleMode.TRIANGLE
    };

    private static final Identifier TEXTURE_CROWN = ClientTexture.id("particle/crown.png");
    private static final Identifier TEXTURE_CUBE_BLAST = ClientTexture.id("particle/cubeblast1.png");
    private static final Identifier TEXTURE_DOLLAR = ClientTexture.id("particle/dollar.png");
    private static final Identifier TEXTURE_HEART = ClientTexture.id("particle/heart.png");
    private static final Identifier TEXTURE_LIGHTNING = ClientTexture.id("particle/lightning.png");
    private static final Identifier TEXTURE_LINE = ClientTexture.id("particle/line.png");
    private static final Identifier TEXTURE_RHOMBUS = ClientTexture.id("particle/rhombus.png");
    private static final Identifier TEXTURE_SNOWFLAKE = ClientTexture.id("particle/snowflake.png");
    private static final Identifier TEXTURE_STAR = ClientTexture.id("particle/star.png");
    private static final Identifier TEXTURE_STAR_ALT = ClientTexture.id("particle/star1.png");
    private static final Identifier TEXTURE_TRIANGLE = ClientTexture.id("particle/triangle.png");

    private static final Identifier GLOW_BLOOM = ClientTexture.id("particle/dashbloom.png");
    private static final Identifier GLOW_BLOOM_SAMPLE = ClientTexture.id("particle/dashbloomsample.png");

    private double x, y, z;
    private double lastX, lastY, lastZ;
    private double velocityX, velocityY, velocityZ;
    private long start;
    private float phase;
    private int color;
    private float scale;
    private long lifeTimeMs;
    private float rotation;

    private EaseInOutQuad fadeInAnimation;
    private EaseInOutQuad fadeOutAnimation;
    private float cachedAlpha = 0.0F;
    private long lastAlphaUpdate = 0L;
    private boolean fadingOut = false;

    private float gravityStrength = 0.04f;
    private float velocityMultiplier = 0.98f;
    private boolean collidesWithWorld = true;

    private ParticleMode mode = ParticleMode.CUBES;
    private ParticleMode actualMode = ParticleMode.CUBES;
    private GlowMode glowMode = GlowMode.BOTH;
    private boolean spinning = true;

    // Фиксированная ориентация кристалла на частицу (как у отдельных кристаллов в TargetEsp).
    private final float crystalRotX = (float) (Math.random() * 360.0);
    private final float crystalRotY = (float) (Math.random() * 360.0);
    private final float crystalRotZ = (float) (Math.random() * 360.0);
    private final float crystalRotationSpeed = 0.5f + (float) (Math.random() * 1.5f);

    public Particle3D(Vec3d pos, Vec3d velocity, int color, float scale, float maxAgeSeconds) {
        this.start = System.currentTimeMillis();
        this.phase = (float) (Math.random() * 100.0);
        this.rotation = (float) (Math.random() * 360.0);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.lastX = pos.x;
        this.lastY = pos.y;
        this.lastZ = pos.z;
        this.velocityX = velocity.x;
        this.velocityY = velocity.y;
        this.velocityZ = velocity.z;
        this.color = color;
        this.scale = scale;
        this.lifeTimeMs = (long) (maxAgeSeconds * 1000);

        this.fadeInAnimation = new EaseInOutQuad(150, 1.0, Direction.FORWARDS);
        this.fadeOutAnimation = new EaseInOutQuad(250, 1.0, Direction.FORWARDS);
    }

    public Particle3D setGravity(float gravity) {
        this.gravityStrength = gravity;
        return this;
    }

    public Particle3D setVelocityMultiplier(float mult) {
        this.velocityMultiplier = mult;
        return this;
    }

    public Particle3D setCollision(boolean collision) {
        this.collidesWithWorld = collision;
        return this;
    }

    public Particle3D setMode(ParticleMode mode) {
        this.mode = mode;
        if (mode == ParticleMode.RANDOM) {
            this.actualMode = RANDOM_MODES[ThreadLocalRandom.current().nextInt(RANDOM_MODES.length)];
        } else {
            this.actualMode = mode;
        }
        return this;
    }

    public Particle3D setGlowMode(GlowMode glowMode) {
        this.glowMode = glowMode;
        return this;
    }

    public Particle3D setSpinning(boolean spinning) {
        this.spinning = spinning;
        return this;
    }

    public void update() {
        long now = System.currentTimeMillis();

        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;

        this.velocityY -= gravityStrength;

        if (collidesWithWorld && mc.world != null) {
            if (this.isHit(this.x + this.velocityX, this.y, this.z)) {
                this.velocityX *= -0.8;
            } else {
                this.x += this.velocityX;
            }
            if (this.isHit(this.x, this.y + this.velocityY, this.z)) {
                this.velocityX *= 0.999;
                this.velocityZ *= 0.999;
                this.velocityY *= -0.7;
            } else {
                this.y += this.velocityY;
            }
            if (this.isHit(this.x, this.y, this.z + this.velocityZ)) {
                this.velocityZ *= -0.8;
            } else {
                this.z += this.velocityZ;
            }
        } else {
            this.x += this.velocityX;
            this.y += this.velocityY;
            this.z += this.velocityZ;
        }

        this.velocityX /= 0.999999;
        this.velocityZ /= 0.999999;

        if (spinning) {
            this.rotation += 2.0f;
        }

        if (!fadingOut && now - this.start > lifeTimeMs) {
            fadingOut = true;
            this.fadeOutAnimation.setDirection(Direction.BACKWARDS);
        }

        if (now - this.lastAlphaUpdate > 16L) {
            if (fadingOut) {
                this.cachedAlpha = this.fadeOutAnimation.getOutput().floatValue();
            } else {
                this.cachedAlpha = this.fadeInAnimation.getOutput().floatValue();
            }
            this.lastAlphaUpdate = now;
        }
    }

    private boolean isHit(double px, double py, double pz) {
        if (mc.world == null) return false;
        BlockPos pos = BlockPos.ofFloored(px, py, pz);
        var state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    public boolean isDead() {
        return fadingOut && this.cachedAlpha <= 0.0F;
    }

    public float getAlpha() {
        return this.cachedAlpha;
    }

    private Identifier getTexture() {
        return switch (actualMode) {
            case CROWN -> TEXTURE_CROWN;
            case CUBE_BLAST -> TEXTURE_CUBE_BLAST;
            case DOLLAR -> TEXTURE_DOLLAR;
            case HEART -> TEXTURE_HEART;
            case LIGHTNING -> TEXTURE_LIGHTNING;
            case LINE -> TEXTURE_LINE;
            case RHOMBUS -> TEXTURE_RHOMBUS;
            case SNOWFLAKE -> TEXTURE_SNOWFLAKE;
            case STAR -> TEXTURE_STAR;
            case STAR_ALT -> TEXTURE_STAR_ALT;
            case TRIANGLE -> TEXTURE_TRIANGLE;
            default -> null;
        };
    }

    public void render(MatrixStack matrices, VertexConsumerProvider immediate, float glowSize, float partialTicks) {
        float alpha = this.getAlpha();
        if (alpha <= 0.0F) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        double interpX = MathHelper.lerp(partialTicks, this.lastX, this.x);
        double interpY = MathHelper.lerp(partialTicks, this.lastY, this.y);
        double interpZ = MathHelper.lerp(partialTicks, this.lastZ, this.z);

        float relX = (float) (interpX - cameraPos.x);
        float relY = (float) (interpY - cameraPos.y);
        float relZ = (float) (interpZ - cameraPos.z);

        if (actualMode == ParticleMode.CUBES) {
            renderCube(matrices, immediate, relX, relY, relZ, alpha, glowSize, cameraYaw, cameraPitch);
        } else if (actualMode == ParticleMode.CRYSTAL) {
            renderCrystal(matrices, immediate, relX, relY, relZ, alpha, glowSize, cameraYaw, cameraPitch);
        } else {
            renderTextured(matrices, immediate, relX, relY, relZ, alpha, glowSize, cameraYaw, cameraPitch);
        }
    }

    private void renderCube(MatrixStack matrices, VertexConsumerProvider immediate,
                            float relX, float relY, float relZ, float alpha, float glowSize,
                            float cameraYaw, float cameraPitch) {
        long now = System.currentTimeMillis();
        float rotationAnim = (float) (now % 9000L) / 9000.0F * 360.0F;

        float alpha02 = alpha * 0.2F;
        float alpha04 = alpha * 0.4F;
        int glowCol = ColorUtils.multAlpha(color, alpha);
        float size = scale * 0.25f;
        float cubeGlow1 = size * glowSize;
        float cubeGlow2 = size * (glowSize / 3.0f);

        float rotY = rotationAnim + this.phase;
        float rotX = rotationAnim * 0.5F;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        RichStyleParticleRenderer.drawCube(immediate.getBuffer(RichStyleParticleRenderer.getQuadsLayer()), mat, ColorUtils.multAlpha(color, alpha02), size);
        RichStyleParticleRenderer.drawLines(immediate.getBuffer(RichStyleParticleRenderer.getLinesLayer()), mat, ColorUtils.multAlpha(color, alpha04), size);
        matrices.pop();

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();

        renderGlowEffect(immediate, gMat, glowCol, alpha, cubeGlow1, cubeGlow2);

        matrices.pop();
    }

    // Точный порт TargetEsp -> Кристаллы: заливка + две светящиеся оболочки (1.15 / 1.3) + bloom-крест.
    private void renderCrystal(MatrixStack matrices, VertexConsumerProvider immediate,
                               float relX, float relY, float relZ, float alpha, float glowSize,
                               float cameraYaw, float cameraPitch) {
        long now = System.currentTimeMillis();

        matrices.push();
        matrices.translate(relX, relY, relZ);

        float timeSeconds = (now % 31416L) / 1000f;
        float pulsation = 1.0f + (float) (Math.sin(timeSeconds * 2f) * 0.1f);
        matrices.scale(pulsation, pulsation, pulsation);

        float selfRotation = (now % 36000L) / 100.0f * crystalRotationSpeed;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(crystalRotX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(crystalRotY + selfRotation));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(crystalRotZ));

        float userAlpha = 0.3f;
        float s = scale * 0.05f;

        VertexConsumer filledConsumer = immediate.getBuffer(LynxEspPipelines.CRYSTAL_FILLED);
        drawFilledCrystal(matrices, filledConsumer, color, userAlpha * 0.85f, alpha, s);

        VertexConsumer glowConsumer = immediate.getBuffer(LynxEspPipelines.CRYSTAL_GLOW);
        matrices.push();
        matrices.scale(1.15f, 1.15f, 1.15f);
        drawFilledCrystal(matrices, glowConsumer, color, userAlpha * 0.25f, alpha, s);
        matrices.pop();

        matrices.push();
        matrices.scale(1.3f, 1.3f, 1.3f);
        drawFilledCrystal(matrices, glowConsumer, color, userAlpha * 0.1f, alpha, s);
        matrices.pop();

        drawCrystalBloom(matrices, immediate, color, alpha, s);

        matrices.pop();
    }

    // Геометрия кристалла (8-гранная призма + две пирамиды) — порт из TargetEsp -> Кристаллы.
    private void drawFilledCrystal(MatrixStack stack, VertexConsumer consumer, int baseColor, float alphaMultiplier, float anim, float s) {
        float hPrism = s;
        float hPyramid = s * 1.5f;
        int numSides = 8;

        Vector3f[] top = new Vector3f[numSides];
        Vector3f[] bottom = new Vector3f[numSides];
        for (int i = 0; i < numSides; i++) {
            float ang = (float) (2 * Math.PI * i / numSides);
            float vx = (float) (s * Math.cos(ang));
            float vz = (float) (s * Math.sin(ang));
            top[i] = new Vector3f(vx, hPrism / 2f, vz);
            bottom[i] = new Vector3f(vx, -hPrism / 2f, vz);
        }
        Vector3f vTop = new Vector3f(0, hPrism / 2f + hPyramid, 0);
        Vector3f vBottom = new Vector3f(0, -hPrism / 2f - hPyramid, 0);

        int finalAlpha = (int) (alphaMultiplier * 255 * anim);
        int finalColor = withAlpha(baseColor, finalAlpha);
        int darkerColor = withAlpha(darkenColor(baseColor, 0.7f), finalAlpha);
        int lighterColor = withAlpha(lightenColor(baseColor, 1.2f), finalAlpha);

        Matrix4f matrix = stack.peek().getPositionMatrix();

        for (int i = 0; i < numSides; i++) {
            Vector3f v1 = bottom[i];
            Vector3f v2 = bottom[(i + 1) % numSides];
            Vector3f v3 = top[(i + 1) % numSides];
            Vector3f v4 = top[i];
            int sideColor = (i % 2 == 0) ? finalColor : darkerColor;
            quadFilled(consumer, matrix, v1, v2, v3, v4, sideColor);
        }
        for (int i = 0; i < numSides; i++) {
            Vector3f v1 = top[i];
            Vector3f v2 = top[(i + 1) % numSides];
            int pyramidColor = (i % 2 == 0) ? lighterColor : finalColor;
            triFilled(consumer, matrix, vTop, v2, v1, pyramidColor);
        }
        for (int i = 0; i < numSides; i++) {
            Vector3f v1 = bottom[i];
            Vector3f v2 = bottom[(i + 1) % numSides];
            int pyramidColor = (i % 2 == 0) ? darkerColor : finalColor;
            triFilled(consumer, matrix, vBottom, v1, v2, pyramidColor);
        }
    }

    private static void quadFilled(VertexConsumer b, Matrix4f m, Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF, a = (color >>> 24) & 0xFF;
        b.vertex(m, v1.x, v1.y, v1.z).color(r, g, bl, a);
        b.vertex(m, v2.x, v2.y, v2.z).color(r, g, bl, a);
        b.vertex(m, v3.x, v3.y, v3.z).color(r, g, bl, a);
        b.vertex(m, v4.x, v4.y, v4.z).color(r, g, bl, a);
    }

    private static void triFilled(VertexConsumer b, Matrix4f m, Vector3f v1, Vector3f v2, Vector3f v3, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF, a = (color >>> 24) & 0xFF;
        // Слой QUADS: треугольник дублирует последнюю вершину 4-й, чтобы получить вырожденный квад.
        b.vertex(m, v1.x, v1.y, v1.z).color(r, g, bl, a);
        b.vertex(m, v2.x, v2.y, v2.z).color(r, g, bl, a);
        b.vertex(m, v3.x, v3.y, v3.z).color(r, g, bl, a);
        b.vertex(m, v3.x, v3.y, v3.z).color(r, g, bl, a);
    }

    private static int withAlpha(int color, int a) {
        a = Math.max(0, Math.min(255, a));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int darkenColor(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int lightenColor(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // Bloom-крест из TargetEsp: два набора билбордов (вокруг Y и вокруг повёрнутой X).
    private void drawCrystalBloom(MatrixStack stack, VertexConsumerProvider provider, int baseColor, float anim, float s) {
        VertexConsumer bloomConsumer = provider
                .getBuffer(LynxEspPipelines.BLOOM_ESP.apply(Identifier.of("lynxwave", "images/particle/glow.png")));

        int bloomAlpha = (int) (0.3f * 60 * anim);
        int bloomColor = withAlpha(baseColor, bloomAlpha);
        float bloomSize = s * 15.0f;
        Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
        int segments = 6;

        for (int i = 0; i < segments; i++) {
            stack.push();
            float angle = (360.0f / segments) * i;
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            stack.multiply(camRot);
            bloomQuad(bloomConsumer, stack.peek().getPositionMatrix(), bloomSize, bloomColor);
            stack.pop();
        }
        for (int i = 0; i < segments; i++) {
            stack.push();
            float angle = (360.0f / segments) * i;
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            stack.multiply(camRot);
            bloomQuad(bloomConsumer, stack.peek().getPositionMatrix(), bloomSize, bloomColor);
            stack.pop();
        }
    }

    private static void bloomQuad(VertexConsumer b, Matrix4f m, float size, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF, a = (color >>> 24) & 0xFF;
        float h = size / 2.0f;
        b.vertex(m, -h, -h, 0).texture(0, 1).color(r, g, bl, a);
        b.vertex(m, h, -h, 0).texture(1, 1).color(r, g, bl, a);
        b.vertex(m, h, h, 0).texture(1, 0).color(r, g, bl, a);
        b.vertex(m, -h, h, 0).texture(0, 0).color(r, g, bl, a);
    }

    private void renderTextured(MatrixStack matrices, VertexConsumerProvider immediate,
                                float relX, float relY, float relZ, float alpha, float glowSize,
                                float cameraYaw, float cameraPitch) {
        Identifier texture = getTexture();
        if (texture == null) return;

        int glowCol = ColorUtils.multAlpha(color, alpha);
        float size = scale * 0.5f;

        int r = (glowCol >> 16) & 0xFF;
        int g = (glowCol >> 8) & 0xFF;
        int b = glowCol & 0xFF;
        int a = (int) (255 * alpha);

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        if (spinning) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        }

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderLayer layer = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(texture);
        VertexConsumer buffer = immediate.getBuffer(layer);

        float half = size / 2.0f;
        buffer.vertex(mat, -half, -half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(mat, -half, half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(mat, half, half, 0).texture(1, 1).color(r, g, b, a);
        buffer.vertex(mat, half, -half, 0).texture(1, 0).color(r, g, b, a);

        matrices.pop();

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();

        float glowSizePrimary = size * glowSize * 0.5f;
        float glowSizeSecondary = size * glowSize * 0.2f;
        renderGlowEffect(immediate, gMat, glowCol, alpha, glowSizePrimary, glowSizeSecondary);

        matrices.pop();
    }

    private void renderGlowEffect(VertexConsumerProvider immediate, Matrix4f matrix, int color, float alpha, float sizePrimary, float sizeSecondary) {
        switch (glowMode) {
            case BLOOM -> {
                RenderLayer layerBloom = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM);
                RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerBloom), matrix, color, (int) (80.0F * alpha), sizePrimary);
            }
            case BLOOM_SAMPLE -> {
                RenderLayer layerSample = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM_SAMPLE);
                RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerSample), matrix, color, (int) (140.0F * alpha), sizeSecondary);
            }
            case BOTH -> {
                RenderLayer layerBloom = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM);
                RenderLayer layerSample = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM_SAMPLE);
                RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerBloom), matrix, color, (int) (80.0F * alpha), sizePrimary);
                RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerSample), matrix, color, (int) (140.0F * alpha), sizeSecondary);
            }
        }
    }
}
