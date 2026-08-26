package dev.client.modules.core.render.worldparticles;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.animations.nova.extended.Direction;
import dev.client.api.nullcry.render.core.animations.nova.extended.core.EaseInOutQuad;
import dev.client.api.nullcry.render.core.LynxEspPipelines;
import dev.client.modules.core.render.particles.RichStyleParticleRenderer;
import dev.client.modules.core.render.particles.WorldParticleLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

public class WorldParticle implements ClientApi {

    public enum ParticleType {
        CUBE_3D,
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
        GLOW,
        RANDOM
    }

    private static final ParticleType[] RANDOM_TYPES = {
            ParticleType.CRYSTAL,
            ParticleType.CROWN,
            ParticleType.CUBE_BLAST,
            ParticleType.DOLLAR,
            ParticleType.HEART,
            ParticleType.LIGHTNING,
            ParticleType.LINE,
            ParticleType.RHOMBUS,
            ParticleType.SNOWFLAKE,
            ParticleType.STAR,
            ParticleType.STAR_ALT,
            ParticleType.TRIANGLE,
            ParticleType.GLOW
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
    private static final Identifier TEXTURE_GLOW = ClientTexture.id("particle/dashbloom.png");

    private static final Identifier GLOW_BLOOM = ClientTexture.id("particle/dashbloom.png");
    private static final Identifier GLOW_BLOOM_SAMPLE = ClientTexture.id("particle/dashbloomsample.png");

    double x, y, z;
    double prevX, prevY, prevZ;
    double mX, mY, mZ;
    long start;
    float phase;
    EaseInOutQuad fadeInAnimation;
    EaseInOutQuad fadeOutAnimation;
    float cachedAlpha = 0.0F;
    long lastAlphaUpdate = 0L;
    boolean fadingOut = false;
    long lifeTimeMs;
    boolean forceFadeOut = false;

    private ParticleType type;
    private ParticleType actualType;
    private Identifier texture;
    private float rotation;
    private float rotationSpeed;
    private int randomColor;
    private long spawnTime;
    private boolean physicsEnabled = true;
    private float size = 0.5f;

    // Фиксированная ориентация кристалла на частицу (как у отдельных кристаллов в TargetEsp).
    private final float crystalRotX = (float) (Math.random() * 360.0);
    private final float crystalRotY = (float) (Math.random() * 360.0);
    private final float crystalRotZ = (float) (Math.random() * 360.0);
    private final float crystalRotationSpeed = 0.5f + (float) (Math.random() * 1.5f);

    public WorldParticle(double x, double y, double z, long lifeTimeMs) {
        this.start = System.currentTimeMillis();
        this.spawnTime = System.currentTimeMillis();
        this.phase = (float) (Math.random() * 100.0);
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.mX = (Math.random() - 0.5) * 0.04;
        this.mY = (Math.random() - 0.5) * 0.04;
        this.mZ = (Math.random() - 0.5) * 0.04;
        this.fadeInAnimation = new EaseInOutQuad(600, 1.0, Direction.FORWARDS);
        this.fadeOutAnimation = new EaseInOutQuad(400, 1.0, Direction.FORWARDS);
        this.lifeTimeMs = lifeTimeMs;
        this.rotation = (float) (Math.random() * 360.0);
        this.rotationSpeed = (float) (Math.random() * 1.5 + 0.5);
        this.randomColor = generateRandomColor();
        this.type = ParticleType.CUBE_3D;
        this.actualType = ParticleType.CUBE_3D;
    }

    public WorldParticle(double x, double y, double z, double mx, double my, double mz, long lifeTimeMs) {
        this(x, y, z, lifeTimeMs);
        this.mX = mx;
        this.mY = my;
        this.mZ = mz;
    }

    private int generateRandomColor() {
        int[] colors = {
                0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00, 0xFF00FF00,
                0xFF00FFFF, 0xFF0000FF, 0xFF8B00FF, 0xFFFF00FF,
                0xFFFF1493, 0xFFFFFFFF, 0xFF00FF7F, 0xFFFF6347
        };
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    public WorldParticle setType(ParticleType type) {
        this.type = type;
        if (type == ParticleType.RANDOM) {
            this.actualType = RANDOM_TYPES[ThreadLocalRandom.current().nextInt(RANDOM_TYPES.length)];
        } else {
            this.actualType = type;
        }
        this.texture = getTextureForType(this.actualType);
        return this;
    }

    public WorldParticle setPhysics(boolean enabled) {
        this.physicsEnabled = enabled;
        return this;
    }

    public WorldParticle setSize(float size) {
        this.size = size;
        return this;
    }

    private Identifier getTextureForType(ParticleType type) {
        return switch (type) {
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
            case GLOW -> TEXTURE_GLOW;
            default -> null;
        };
    }

    public double getDistanceSquaredTo(Vec3d pos) {
        double dx = this.x - pos.x;
        double dy = this.y - pos.y;
        double dz = this.z - pos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double getHorizontalDistanceSquaredTo(Vec3d pos) {
        double dx = this.x - pos.x;
        double dz = this.z - pos.z;
        return dx * dx + dz * dz;
    }

    public void startFadeOut() {
        if (!fadingOut) {
            fadingOut = true;
            forceFadeOut = true;
            this.fadeOutAnimation.setDirection(Direction.BACKWARDS);
        }
    }

    public void update(long now) {
        if (mc.world == null) return;

        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;

        double velMagSq = this.mX * this.mX + this.mY * this.mY + this.mZ * this.mZ;
        if (velMagSq > 1.0E-4) {
            if (this.isHit(this.x + this.mX, this.y, this.z)) {
                this.mX *= -0.8;
            } else {
                this.x = this.x + this.mX;
            }
            if (this.isHit(this.x, this.y + this.mY, this.z)) {
                this.mY *= -0.8;
            } else {
                this.y = this.y + this.mY;
            }
            if (this.isHit(this.x, this.y, this.z + this.mZ)) {
                this.mZ *= -0.8;
            } else {
                this.z = this.z + this.mZ;
            }
        } else {
            this.x = this.x + this.mX;
            this.y = this.y + this.mY;
            this.z = this.z + this.mZ;
        }

        this.mX *= 0.99;
        this.mY *= 0.99;
        this.mZ *= 0.99;

        if (physicsEnabled) {
            this.mY -= 0.0002;
        }

        this.rotation += this.rotationSpeed;

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

    public float getAlpha() {
        return this.cachedAlpha;
    }

    private boolean isHit(double px, double py, double pz) {
        if (mc.world == null) return false;
        BlockPos pos = BlockPos.ofFloored(px, py, pz);
        var state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    public boolean shouldRemove() {
        return fadingOut && this.cachedAlpha <= 0.0F;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public int getColor(int baseColor, boolean useRandomColor, boolean whiteOnSpawn) {
        if (useRandomColor) {
            return ColorUtils.multAlpha(randomColor, cachedAlpha);
        }

        if (whiteOnSpawn) {
            long currentTime = System.currentTimeMillis();
            long transitionDuration = 7000;
            long timeSinceSpawn = currentTime - spawnTime;

            if (timeSinceSpawn < transitionDuration) {
                float progress = (float) timeSinceSpawn / transitionDuration;

                int targetR = (baseColor >> 16) & 0xFF;
                int targetG = (baseColor >> 8) & 0xFF;
                int targetB = baseColor & 0xFF;
                int targetA = (baseColor >> 24) & 0xFF;

                int r = (int) (255 + ((targetR - 255) * progress));
                int g = (int) (255 + ((targetG - 255) * progress));
                int b = (int) (255 + ((targetB - 255) * progress));

                int color = (targetA << 24) | (r << 16) | (g << 8) | b;
                return ColorUtils.multAlpha(color, cachedAlpha);
            }
        }

        return ColorUtils.multAlpha(baseColor, cachedAlpha);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider immediate, Vec3d cameraPos,
                       int baseColor, float globalRotation, float cameraYaw, float cameraPitch,
                       float glowSize, boolean useRandomColor, boolean whiteOnSpawn, boolean whiteCenter) {

        float alpha = this.getAlpha();
        if (alpha <= 0.0F) return;

        float partialTicks = mc.getRenderTickCounter().getTickProgress(true);
        double interpX = prevX + (x - prevX) * partialTicks;
        double interpY = prevY + (y - prevY) * partialTicks;
        double interpZ = prevZ + (z - prevZ) * partialTicks;

        float relX = (float) (interpX - cameraPos.x);
        float relY = (float) (interpY - cameraPos.y);
        float relZ = (float) (interpZ - cameraPos.z);

        int color = getColor(baseColor, useRandomColor, whiteOnSpawn);

        if (actualType == ParticleType.CUBE_3D) {
            renderCube3D(matrices, immediate, relX, relY, relZ, alpha, color, globalRotation, cameraYaw, cameraPitch, glowSize);
        } else if (actualType == ParticleType.CRYSTAL) {
            renderCrystal(matrices, immediate, relX, relY, relZ, alpha, color, cameraYaw, cameraPitch, glowSize);
        } else {
            renderTextured(matrices, immediate, relX, relY, relZ, alpha, color, cameraYaw, cameraPitch, glowSize, whiteCenter);
        }
    }

    private void renderCube3D(MatrixStack matrices, VertexConsumerProvider immediate,
                              float relX, float relY, float relZ, float alpha, int color,
                              float globalRotation, float cameraYaw, float cameraPitch, float glowSize) {

        float alpha02 = alpha * 0.2F;
        float alpha04 = alpha * 0.4F;
        float cubeSize = size * 0.5f;
        float cubeGlow1 = cubeSize * glowSize;
        float cubeGlow2 = cubeSize * (glowSize / 3.0f);

        float rotY = globalRotation + this.phase;
        float rotX = globalRotation * 0.5F;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        RichStyleParticleRenderer.drawCube(immediate.getBuffer(RichStyleParticleRenderer.getQuadsLayer()), mat, ColorUtils.multAlpha(color, alpha02), cubeSize);
        RichStyleParticleRenderer.drawLines(immediate.getBuffer(RichStyleParticleRenderer.getLinesLayer()), mat, ColorUtils.multAlpha(color, alpha04), cubeSize);
        matrices.pop();

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();
        RenderLayer layerBloom = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM);
        RenderLayer layerSample = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM_SAMPLE);
        RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerBloom), gMat, color, (int) (80.0F * alpha), cubeGlow1);
        RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerSample), gMat, color, (int) (140.0F * alpha), cubeGlow2);
        matrices.pop();
    }

    // Точный порт TargetEsp -> Кристаллы: заливка + две светящиеся оболочки (1.15 / 1.3) + bloom-крест.
    private void renderCrystal(MatrixStack matrices, VertexConsumerProvider immediate,
                               float relX, float relY, float relZ, float alpha, int color,
                               float cameraYaw, float cameraPitch, float glowSize) {
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
        float s = size * 0.05f;

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

    private static int withAlpha(int color, int a) {
        a = Math.max(0, Math.min(255, a));
        return (color & 0x00FFFFFF) | (a << 24);
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

    // Слой WORLD_PARTICLES_GLOW использует формат POSITION_TEXTURE_COLOR —
    // заполняем только position, UV и цвет.
    private static void emitVertex(VertexConsumer buffer, Matrix4f mat,
                                   float x, float y, float z, float u, float v,
                                   int r, int g, int b, int a) {
        buffer.vertex(mat, x, y, z).texture(u, v).color(r, g, b, a);
    }

    private void renderTextured(MatrixStack matrices, VertexConsumerProvider immediate,
                                float relX, float relY, float relZ, float alpha, int color,
                                float cameraYaw, float cameraPitch, float glowSize, boolean whiteCenter) {

        if (texture == null) return;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int) (255 * alpha);

        float textureSize = size * 0.5f;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderLayer layer = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(texture);
        VertexConsumer buffer = immediate.getBuffer(layer);

        float half = textureSize / 2.0f;
        emitVertex(buffer, mat, -half, -half, 0, 0, 0, r, g, b, a);
        emitVertex(buffer, mat, -half, half, 0, 0, 1, r, g, b, a);
        emitVertex(buffer, mat, half, half, 0, 1, 1, r, g, b, a);
        emitVertex(buffer, mat, half, -half, 0, 1, 0, r, g, b, a);

        if (whiteCenter) {
            float centerSize = half * 0.5f;
            int whiteA = (int) (200 * alpha);
            emitVertex(buffer, mat, -centerSize, -centerSize, 0.001f, 0, 0, 255, 255, 255, whiteA);
            emitVertex(buffer, mat, -centerSize, centerSize, 0.001f, 0, 1, 255, 255, 255, whiteA);
            emitVertex(buffer, mat, centerSize, centerSize, 0.001f, 1, 1, 255, 255, 255, whiteA);
            emitVertex(buffer, mat, centerSize, -centerSize, 0.001f, 1, 0, 255, 255, 255, whiteA);
        }

        matrices.pop();

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();

        float glowSizePrimary = textureSize * glowSize * 0.5f;
        float glowSizeSecondary = textureSize * glowSize * 0.2f;

        RenderLayer layerBloom = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM);
        RenderLayer layerSample = WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM_SAMPLE);
        RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerBloom), gMat, color, (int) (80.0F * alpha), glowSizePrimary);
        RichStyleParticleRenderer.drawGlow(immediate.getBuffer(layerSample), gMat, color, (int) (140.0F * alpha), glowSizeSecondary);

        matrices.pop();
    }
}
