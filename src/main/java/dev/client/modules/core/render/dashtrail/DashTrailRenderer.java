package dev.client.modules.core.render.dashtrail;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ClientTexture;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import dev.client.modules.core.render.particles.WorldParticleLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * След от рывка: за движущимся игроком сыплются светящиеся «кубики» с искрами.
 * <p>
 * Рисуется аддитивным слоем мировых частиц клиента — тем же, что и остальные
 * 3D-частицы, поэтому чёрные пиксели текстур не затемняют сцену.
 */
public final class DashTrailRenderer implements ClientApi {

    public static final DashTrailRenderer INSTANCE = new DashTrailRenderer();

    private static final Identifier BLOOM = ClientTexture.id("particle/dashbloomsample.png");

    /** Сколько кадров в каждой из анимированных групп текстур. */
    private static final int[] GROUP_FRAMES = {11, 23, 32, 16, 32};
    private static final int STATIC_TEXTURES = 21;

    // Идентификаторы считаются один раз: resolve() зовётся для каждой частицы каждый кадр,
    // а ClientTexture.id() на каждый вызов создаёт новый Identifier и лезет в реестр.
    private static final Identifier[] STATIC_CUBICS = new Identifier[STATIC_TEXTURES];
    private static final Identifier[][] ANIMATED_CUBICS = new Identifier[GROUP_FRAMES.length][];

    static {
        for (int i = 0; i < STATIC_TEXTURES; i++) {
            STATIC_CUBICS[i] = ClientTexture.id("particle/dashtrail/dashcubic" + (i + 1) + ".png");
        }
        for (int group = 0; group < GROUP_FRAMES.length; group++) {
            ANIMATED_CUBICS[group] = new Identifier[GROUP_FRAMES[group]];
            for (int frame = 0; frame < GROUP_FRAMES[group]; frame++) {
                ANIMATED_CUBICS[group][frame] =
                        ClientTexture.id("particle/dashtrail/group" + (group + 1) + "/dashcubic" + (frame + 1) + ".png");
            }
        }
    }

    private static final double MIN_SPEED_XZ = 0.08;
    private static final int MAX_CUBICS_PER_TICK = 16;
    private static final float TRAIL_RADIUS = 25.0F;

    private final Random random = new Random();
    private final List<DashCubic> cubics = new ArrayList<>();
    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final DashAnimation stateAnimation = new DashAnimation(0.0F, 0.0F, 0.08F);
    private final DashAnimation lightingAnimation = new DashAnimation(0.0F, 1.0F, 0.08F);

    private DashTrailRenderer() {
    }

    public void start() {
        clear();
        stateAnimation.set(0.0F);
        stateAnimation.target = 1.0F;
    }

    public void clear() {
        cubics.clear();
        lastPositions.clear();
        stateAnimation.target = 0.0F;
    }

    // ------------------------------------------------------------------ тик

    public void tick() {
        if (mc.player == null || mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null) continue;

            UUID id = player.getUuid();
            Vec3d current = player.getEntityPos();
            Vec3d previous = lastPositions.put(id, current);

            if (previous != null && shouldTrail(player)) {
                spawnForMovement(player, previous, current);
            }
        }

        lastPositions.keySet().removeIf(uuid -> mc.world.getPlayerByUuid(uuid) == null);
        updateCubics();
    }

    private boolean shouldTrail(PlayerEntity player) {
        if (mc.player == null) return false;
        if (player == mc.player) return true;
        if (mc.player.distanceTo(player) > TRAIL_RADIUS) return false;

        try {
            return LynxWave.getInstance().getFriendManager().isFriend(player.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void spawnForMovement(PlayerEntity player, Vec3d previous, Vec3d current) {
        double dx = current.x - previous.x;
        double dy = current.y - previous.y;
        double dz = current.z - previous.z;

        if (Math.sqrt(dx * dx + dz * dz) < MIN_SPEED_XZ) return;

        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = MathHelper.clamp((int) (speed / MIN_SPEED_XZ), 1, MAX_CUBICS_PER_TICK);
        int color = randomColor();

        for (int i = 0; i < count; i++) {
            cubics.add(new DashCubic(player, previous, i / (float) count, color));
        }
    }

    private void updateCubics() {
        stateAnimation.target = 1.0F;

        for (DashCubic cubic : cubics) {
            if (cubic.lifeProgress() >= 1.0F && cubic.alpha.target != 0.0F) {
                cubic.alpha.target = 0.0F;
            }
        }

        cubics.removeIf(cubic -> cubic.lifeProgress() >= 1.0F
                && cubic.alpha.target == 0.0F
                && cubic.alpha.get() < 0.02F);

        for (DashCubic cubic : cubics) {
            if (cubic.alpha.get() > 0.05F) {
                cubic.move();
            }
        }
    }

    // ------------------------------------------------------------------ рендер

    public void render(MatrixStack matrices, float tickDelta) {
        if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        float state = stateAnimation.get();
        if (state < 0.05F) return;

        List<DashCubic> visible = new ArrayList<>();
        for (DashCubic cubic : cubics) {
            if (cubic.alpha.get() > 0.05F) visible.add(cubic);
        }
        if (visible.isEmpty()) return;

        float lighting = lightingAnimation.get();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        for (DashCubic cubic : visible) {
            cubic.renderSparks(tickDelta, state);
        }

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        // Свечение идёт первым проходом, сами кубики — вторым, иначе кубики тонут в засветке.
        for (DashCubic cubic : visible) {
            cubic.renderBloom(matrices, immediate, camera, cameraYaw, cameraPitch, tickDelta, state, lighting);
        }
        for (DashCubic cubic : visible) {
            cubic.renderCubic(matrices, immediate, camera, tickDelta, state);
        }

        immediate.draw();
    }

    private void drawQuad(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Identifier texture,
                          Vec3d camera, double x, double y, double z,
                          float yaw, float pitch, float halfWidth, float halfHeight, int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0) return;

        matrices.push();
        matrices.translate(x - camera.x, y - camera.y, z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(WorldParticleLayers.WORLD_PARTICLES_GLOW.apply(texture));

        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        buffer.vertex(matrix, -halfWidth, -halfHeight, 0).texture(0, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, -halfWidth, halfHeight, 0).texture(0, 1).color(r, g, b, alpha);
        buffer.vertex(matrix, halfWidth, halfHeight, 0).texture(1, 1).color(r, g, b, alpha);
        buffer.vertex(matrix, halfWidth, -halfHeight, 0).texture(1, 0).color(r, g, b, alpha);

        matrices.pop();
    }

    // ------------------------------------------------------------------ цвета

    private int randomColor() {
        return Color.getHSBColor(random.nextInt(361) / 360.0F, 1.0F, 1.0F).getRGB();
    }

    private int randomLifetime() {
        return (int) ((550 + random.nextInt(300)) * 0.75F);
    }

    private static int overlay(int color, int over, float progress) {
        progress = MathHelper.clamp(progress, 0.0F, 1.0F);
        int r = (int) (((color >> 16) & 0xFF) + (((over >> 16) & 0xFF) - ((color >> 16) & 0xFF)) * progress);
        int g = (int) (((color >> 8) & 0xFF) + (((over >> 8) & 0xFF) - ((color >> 8) & 0xFF)) * progress);
        int b = (int) ((color & 0xFF) + ((over & 0xFF) - (color & 0xFF)) * progress);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float alpha) {
        return (MathHelper.clamp((int) alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private static int darken(int color, float factor) {
        factor = MathHelper.clamp(factor, 0.0F, 1.0F);
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Плавная волна: 0 → 1 → 0, чтобы искра разгоралась и гасла. */
    private static double wave(double x) {
        double half = x < 0.5 ? x * 2.0 : (1.0 - x) * 2.0;
        return half < 0.5 ? 2 * half * half : 1 - Math.pow(-2 * half + 2, 2) / 2;
    }

    // ------------------------------------------------------------------ текстуры

    private final class DashTexture {
        private final boolean animated;
        private final int group;
        private final int staticIndex;
        private final long spawnTime;
        private final int duration;

        private DashTexture() {
            this.animated = random.nextInt(100) > 40;
            if (this.animated) {
                this.group = random.nextInt(GROUP_FRAMES.length);
                this.staticIndex = 0;
                this.spawnTime = System.currentTimeMillis();
                this.duration = Math.max(1, randomLifetime());
            } else {
                this.group = -1;
                this.staticIndex = random.nextInt(STATIC_TEXTURES);
                this.spawnTime = 0L;
                this.duration = 0;
            }
        }

        private Identifier resolve() {
            if (!animated) {
                return STATIC_CUBICS[staticIndex];
            }

            int frames = GROUP_FRAMES[group];
            int elapsed = (int) (System.currentTimeMillis() - spawnTime);
            float progress = (elapsed % duration) / (float) duration;
            int frame = (int) MathHelper.clamp(progress * frames, 0, frames - 1);
            return ANIMATED_CUBICS[group][frame];
        }
    }

    // ------------------------------------------------------------------ кубик

    private final class DashCubic {
        private final DashAnimation alpha = new DashAnimation(0.0F, 1.0F, 0.035F);
        private final DashTexture texture = new DashTexture();
        private final List<DashSpark> sparks = new ArrayList<>();
        private final PlayerEntity owner;
        private final long spawnTime = System.currentTimeMillis();
        private final int lifetime = randomLifetime();
        private final int color;

        private double motionX, motionY, motionZ;
        private double x, y, z;
        private double prevX, prevY, prevZ;
        private final float[] rotation = new float[2];

        private DashCubic(PlayerEntity owner, Vec3d previousPos, float offset, int color) {
            this.owner = owner;
            this.color = color;

            this.motionX = owner.getX() - previousPos.x;
            this.motionY = owner.getY() - previousPos.y;
            this.motionZ = owner.getZ() - previousPos.z;

            double height = owner.getHeight();
            this.x = previousPos.x - motionX * offset + (-0.0875 + 0.175 * Math.random());
            this.y = previousPos.y - motionY * offset + (height / 3.0 + height / 4.0 * Math.random() * 0.7);
            this.z = previousPos.z - motionZ * offset + (-0.0875 + 0.175 * Math.random());
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;

            float speed = 0.04F;
            this.motionX *= speed;
            this.motionY *= speed;
            this.motionZ *= speed;

            updateRotation();
        }

        private float lifeProgress() {
            return Math.min((System.currentTimeMillis() - spawnTime) / (float) lifetime, 1.0F);
        }

        private double renderX(float tickDelta) {
            return prevX + (x - prevX) * tickDelta;
        }

        private double renderY(float tickDelta) {
            return prevY + (y - prevY) * tickDelta;
        }

        private double renderZ(float tickDelta) {
            return prevZ + (z - prevZ) * tickDelta;
        }

        private void updateRotation() {
            float cameraPitch = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                    ? mc.gameRenderer.getCamera().getPitch() : 0.0F;

            if (Math.sqrt(motionX * motionX + motionZ * motionZ) < 5.0E-4) {
                rotation[0] = (float) (360.0 * Math.random());
                rotation[1] = cameraPitch;
                return;
            }

            int motionYaw = (int) Math.toDegrees(Math.atan2(motionZ, motionX) - 90.0);
            if (motionYaw < 0) motionYaw += 360;

            rotation[0] = motionYaw - 60.0F;
            float yawDiff = Math.abs(MathHelper.wrapDegrees(owner.getYaw() - (motionYaw + 26.3F)));
            rotation[1] = (yawDiff < 10.0F || yawDiff > 160.0F) ? -90.0F : cameraPitch;
        }

        private void move() {
            prevX = x;
            prevY = y;
            prevZ = z;

            motionX /= 1.05;
            x += 5.0 * motionX;
            motionY /= 1.05;
            y += 5.0 * motionY / (motionY < 0.0 ? 1.0 : 3.5);
            motionZ /= 1.05;
            z += 5.0 * motionZ;

            updateRotation();

            if (lifeProgress() < 0.3F && random.nextInt(12) > 5) {
                sparks.add(new DashSpark());
                sparks.add(new DashSpark());
            }

            sparks.forEach(DashSpark::move);
            sparks.removeIf(DashSpark::finished);
        }

        private void renderSparks(float tickDelta, float state) {
            double cx = renderX(tickDelta);
            double cy = renderY(tickDelta);
            double cz = renderZ(tickDelta);
            int sourceAlpha = (color >>> 24) == 0 ? 255 : (color >>> 24);

            for (DashSpark spark : sparks) {
                double sx = spark.renderX(tickDelta);
                double sy = spark.renderY(tickDelta);
                double sz = spark.renderZ(tickDelta);

                float sparkAlpha = (float) (spark.alpha() * alpha.value);
                float pulse = (float) wave(sparkAlpha);
                int base = overlay(color, 0xFFFFFF, pulse);
                int alphaByte = MathHelper.clamp((int) (sourceAlpha * pulse / 1.33333F), 0, 255);
                int finalAlpha = MathHelper.clamp((int) (Math.min(1.0F, alphaByte / 255.0F * state) * 255.0F), 0, 255);

                Draw3DUtil.drawLine(
                        new Vec3d(cx + sx, cy + sy, cz + sz),
                        new Vec3d(cx - sx, cy - sy, cz - sz),
                        withAlpha(base, finalAlpha), 1.0F, true);
            }
        }

        private void renderBloom(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera,
                                 float cameraYaw, float cameraPitch, float tickDelta, float state, float lighting) {
            float visible = alpha.value * state;
            float scale = 0.033F * visible * 16.0F;
            float extent = (float) Math.sqrt(scale * scale + scale * scale);

            double px = renderX(tickDelta);
            double py = renderY(tickDelta);
            double pz = renderZ(tickDelta);
            int tinted = overlay(color, 0xFFFFFF, 0.15F);

            drawQuad(matrices, immediate, BLOOM, camera, px, py, pz, cameraYaw, cameraPitch,
                    extent / 1.75F, extent / 1.75F, withAlpha(tinted, 55.0F * visible));

            if (lighting != 0.0F) {
                float glow = visible * lighting;
                float wide = extent * (1.0F + 6.0F * (1.0F - lifeProgress()) * glow);
                drawQuad(matrices, immediate, BLOOM, camera, px, py, pz, cameraYaw, cameraPitch,
                        wide / 2.0F, wide / 2.0F, withAlpha(darken(tinted, glow / 4.0F), 90.0F * glow));
            }
        }

        private void renderCubic(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d camera,
                                 float tickDelta, float state) {
            float visible = alpha.value * state;
            float scale = 0.033F * visible * 16.0F;
            int tinted = overlay(color, 0xFFFFFF, 0.45F);
            int alphaByte = MathHelper.clamp((int) (255 * MathHelper.clamp(visible, 0.0F, 1.0F)), 0, 255);

            drawQuad(matrices, immediate, texture.resolve(), camera,
                    renderX(tickDelta), renderY(tickDelta), renderZ(tickDelta),
                    rotation[0], rotation[1], scale, scale, withAlpha(tinted, alphaByte));
        }
    }

    // ------------------------------------------------------------------ искра

    private final class DashSpark {
        private final double speed = Math.random() / 50.0;
        private final double yaw = Math.random() * 360.0;
        private final double pitch = -90.0 + Math.random() * 180.0;
        private final long spawnTime = System.currentTimeMillis();

        private double x, y, z;
        private double prevX, prevY, prevZ;

        private double progress() {
            return MathHelper.clamp((System.currentTimeMillis() - spawnTime) / 1000.0F, 0.0F, 1.0F);
        }

        private double alpha() {
            return 1.0 - progress();
        }

        private boolean finished() {
            return progress() >= 1.0;
        }

        private void move() {
            prevX = x;
            prevY = y;
            prevZ = z;

            double radians = Math.toRadians(yaw);
            x += Math.sin(radians) * speed;
            y += Math.cos(Math.toRadians(pitch - 90.0)) * speed;
            z += Math.cos(radians) * speed;
        }

        private double renderX(float tickDelta) {
            return prevX + (x - prevX) * tickDelta;
        }

        private double renderY(float tickDelta) {
            return prevY + (y - prevY) * tickDelta;
        }

        private double renderZ(float tickDelta) {
            return prevZ + (z - prevZ) * tickDelta;
        }
    }
}
