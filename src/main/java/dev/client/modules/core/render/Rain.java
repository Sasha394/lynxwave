package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderCompat;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ColorPicker;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.renderers.core.WetSurfaceRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Rain extends Module {
    public static Rain INSTANCE;

    private final ModeElement preset = new ModeElement("Тип осадков", () -> true)
            .set("Морось", "Дождь", "Ливень", "Шторм")
            .defaultValue("Дождь").register(this);
    private final Slider density = new Slider("Плотность", () -> true)
            .set(0.1f, 3.0f, 0.1f).defaultValue(1.0f).register(this);
    private final Slider radius = new Slider("Радиус", () -> true)
            .set(8.0f, 64.0f, 1.0f).defaultValue(26.0f).register(this);
    private final Slider altitude = new Slider("Высота появления", () -> true)
            .set(6.0f, 40.0f, 1.0f).defaultValue(16.0f).register(this);
    private final Slider dropSize = new Slider("Размер капель", () -> true)
            .set(0.3f, 2.5f, 0.1f).defaultValue(1.0f).register(this);
    private final Slider fallSpeed = new Slider("Скорость падения", () -> true)
            .set(0.3f, 2.5f, 0.1f).defaultValue(1.0f).register(this);
    private final Slider wind = new Slider("Ветер", () -> true)
            .set(0.0f, 3.0f, 0.1f).defaultValue(1.0f).register(this);
    private final Slider opacity = new Slider("Прозрачность", () -> true)
            .set(0.1f, 2.0f, 0.1f).defaultValue(1.0f).register(this);

    private final CheckBox splashes = new CheckBox("Всплески", () -> true)
            .defaultValue(true).register(this);
    private final CheckBox droplets = new CheckBox("Брызги", () -> splashes.getEnabled())
            .defaultValue(true).register(this);
    private final CheckBox groundMist = new CheckBox("Приземная дымка", () -> true)
            .defaultValue(true).register(this);
    private final CheckBox lightning = new CheckBox("Молнии", () -> true)
            .defaultValue(false).register(this);
    private final CheckBox skyCheck = new CheckBox("Проверять небо", () -> true)
            .defaultValue(true).register(this);
    private final CheckBox onlyWhenRaining = new CheckBox("Только при дожде", () -> true)
            .defaultValue(false).register(this);

    private final ModeElement colorMode = new ModeElement("Режим цвета", () -> true)
            .set("Реалистичный", "Клиентский", "Свой")
            .defaultValue("Реалистичный").register(this);
    private final ColorPicker customColor = new ColorPicker("Цвет капель", () -> colorMode.isSelected("Свой"))
            .set(0xFFBFD8E6).defaultValue(0xFFBFD8E6).register(this);

    private final CheckBox wetGround = new CheckBox("Мокрая поверхность", () -> true)
            .defaultValue(true).register(this);
    private final Slider wetness = new Slider("Сила намокания", () -> wetGround.getEnabled())
            .set(0.1f, 2.0f, 0.05f).defaultValue(1.25f).register(this);
    private final Slider puddleCoverage = new Slider("Количество луж", () -> wetGround.getEnabled())
            .set(0.05f, 1.0f, 0.05f).defaultValue(0.90f).register(this);
    private final Slider reflectionStrength = new Slider("Сила отражений", () -> wetGround.getEnabled())
            .set(0.1f, 2.0f, 0.05f).defaultValue(1.55f).register(this);
    private final Slider reflectionDistance = new Slider("Дальность отражений", () -> wetGround.getEnabled())
            .set(12.0f, 96.0f, 1.0f).defaultValue(56.0f).register(this);
    private final ModeElement reflectionQuality = new ModeElement("Качество отражений", () -> wetGround.getEnabled())
            .set("Быстро", "Баланс", "Высокое").defaultValue("Баланс").register(this);
    private final CheckBox puddleRipples = new CheckBox("Рябь в лужах", () -> wetGround.getEnabled())
            .defaultValue(true).register(this);
    private final Slider rippleStrength = new Slider("Сила ряби", () -> wetGround.getEnabled() && puddleRipples.getEnabled())
            .set(0.0f, 1.6f, 0.05f).defaultValue(0.8f).register(this);

    private static final double GRAVITY = 22.0;
    private static final double TERMINAL_VELOCITY = 34.0;
    private static final int RIPPLE_SEGMENTS = 14;
    private static final int MAX_DROPS = 4200;
    private static final int MAX_MIST = 90;

    private final List<Drop> drops = new ArrayList<>();
    private final List<Splash> splashList = new ArrayList<>();
    private final List<Droplet> dropletList = new ArrayList<>();
    private final List<Mist> mistList = new ArrayList<>();
    private final Map<Long, Integer> groundCache = new HashMap<>();
    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private final Random random = new Random();

    private long lastFrameNanos;
    private float clock;
    private float flash;
    private float nextFlashIn = 9.0f;
    private int cacheTicks;

    public Rain() {
        super("Rain", ModuleCategory.Render, "Реалистичный объёмный дождь и мокрые поверхности");
        INSTANCE = this;
    }

    @Override
    public void onEnabled() {
        lastFrameNanos = 0L;
        WetSurfaceRenderer.getInstance().reset();
        clearAll();
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        clearAll();
        WetSurfaceRenderer.getInstance().reset();
        super.onDisabled();
    }

    private void clearAll() {
        drops.clear();
        splashList.clear();
        dropletList.clear();
        mistList.clear();
        groundCache.clear();
        flash = 0.0f;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            clearAll();
            return;
        }
        if (++cacheTicks > 30) {
            cacheTicks = 0;
            groundCache.clear();
        }
    }

    private float presetDensity() {
        if (preset.isSelected("Морось")) return 0.45f;
        if (preset.isSelected("Ливень")) return 1.9f;
        if (preset.isSelected("Шторм")) return 2.8f;
        return 1.0f;
    }

    private float presetWind() {
        if (preset.isSelected("Морось")) return 0.5f;
        if (preset.isSelected("Ливень")) return 1.35f;
        if (preset.isSelected("Шторм")) return 2.4f;
        return 1.0f;
    }

    private float presetMass() {
        if (preset.isSelected("Морось")) return 0.55f;
        if (preset.isSelected("Ливень")) return 1.25f;
        if (preset.isSelected("Шторм")) return 1.5f;
        return 1.0f;
    }

    private int targetDropCount() {
        float r = radius.getValue();
        int count = (int) (r * r * 0.0155f * density.getValue() * presetDensity() * 26.0f);
        return MathHelper.clamp(count, 40, MAX_DROPS);
    }

    private float[] resolveColor() {
        int color;
        if (colorMode.isSelected("Свой")) {
            color = customColor.getColorRGBA();
        } else if (colorMode.isSelected("Клиентский")) {
            color = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor() : 0xFFFFFFFF;
        } else {
            color = 0xFFC8DCE8;
        }
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f
        };
    }

    private float random(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private int surfaceY(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer cached = groundCache.get(key);
        if (cached != null) return cached;

        int top;
        try {
            top = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        } catch (Throwable ignored) {
            top = mc.world.getBottomY();
        }
        if (groundCache.size() < 20_000) groundCache.put(key, top);
        return top;
    }

    private boolean isBlocking(double x, double y, double z) {
        if (mc.world == null) return false;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        if (!mc.world.getChunkManager().isChunkLoaded(scratchPos.getX() >> 4, scratchPos.getZ() >> 4)) return false;
        BlockState state = mc.world.getBlockState(scratchPos);
        if (!state.getFluidState().isEmpty()) return true;
        if (state.isAir()) return false;
        try {
            return !state.getCollisionShape(mc.world, scratchPos).isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isWater(double x, double y, double z) {
        if (mc.world == null) return false;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        try {
            return !mc.world.getBlockState(scratchPos).getFluidState().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean skyVisible(double x, double y, double z) {
        if (!skyCheck.getEnabled() || mc.world == null) return true;
        scratchPos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        try {
            return mc.world.isSkyVisible(scratchPos);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void spawnDrop(Vec3d center, boolean fromTop) {
        float range = radius.getValue();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * range;
        double x = center.x + Math.cos(angle) * distance;
        double z = center.z + Math.sin(angle) * distance;
        double y = fromTop
                ? center.y + altitude.getValue() * random(0.75f, 1.0f)
                : center.y + random(-4.0f, altitude.getValue());
        if (!skyVisible(x, y, z)) return;

        Drop drop = new Drop();
        drop.x = x;
        drop.y = y;
        drop.z = z;
        drop.mass = random(0.6f, 1.4f) * presetMass();
        drop.vy = -random(6.0f, 11.0f) * drop.mass;
        drop.phase = random(0.0f, 62.8f);
        drop.brightness = random(0.55f, 1.0f);
        drop.ground = surfaceY(MathHelper.floor(x), MathHelper.floor(z));
        drops.add(drop);
    }

    private void updateDrops(Vec3d center, float dt) {
        int target = targetDropCount();
        float despawnRadius = radius.getValue() + 6.0f;
        drops.removeIf(drop -> {
            double dx = drop.x - center.x;
            double dz = drop.z - center.z;
            return drop.dead || dx * dx + dz * dz > despawnRadius * despawnRadius
                    || drop.y < center.y - altitude.getValue() - 12.0;
        });

        boolean fill = drops.size() < target / 2;
        int spawnBudget = fill ? Math.min(target - drops.size(), 900) : Math.min(target - drops.size(), 260);
        for (int i = 0; i < spawnBudget; i++) spawnDrop(center, !fill);

        float speedMultiplier = fallSpeed.getValue();
        float windMultiplier = wind.getValue() * presetWind();
        for (Drop drop : drops) {
            double gust = 0.65 + 0.35 * Math.sin(clock * 0.55 + drop.phase * 0.15)
                    + 0.18 * Math.sin(clock * 1.7 + drop.phase);
            double windX = windMultiplier * 3.1 * gust;
            double windZ = windMultiplier * 1.7 * Math.sin(clock * 0.31 + drop.phase * 0.05) * gust;
            double drag = 1.0 - Math.min(0.92, Math.abs(drop.vy) / (TERMINAL_VELOCITY * drop.mass));
            drop.vy -= GRAVITY * drop.mass * drag * dt;
            drop.vy = Math.max(drop.vy, -TERMINAL_VELOCITY * drop.mass);
            drop.vx += (windX - drop.vx) * Math.min(1.0, dt * 3.2);
            drop.vz += (windZ - drop.vz) * Math.min(1.0, dt * 3.2);

            double nx = drop.x + drop.vx * dt * speedMultiplier;
            double ny = drop.y + drop.vy * dt * speedMultiplier;
            double nz = drop.z + drop.vz * dt * speedMultiplier;
            if (ny <= drop.ground + 2.5 && isBlocking(nx, ny, nz)) {
                if (splashes.getEnabled()) addSplash(nx, ny, nz, drop);
                drop.dead = true;
            } else if (ny <= drop.ground - 3.0) {
                drop.dead = true;
            } else {
                drop.x = nx;
                drop.y = ny;
                drop.z = nz;
            }
        }
    }

    private void addSplash(double x, double y, double z, Drop source) {
        if (splashList.size() > 700) return;
        Splash splash = new Splash();
        splash.x = x;
        splash.y = Math.floor(y) + 1.0005;
        splash.z = z;
        splash.maxLife = random(0.30f, 0.52f);
        splash.scale = (float) (0.32 + 0.22 * source.mass);
        splash.water = isWater(x, y - 0.15, z);
        splashList.add(splash);

        if (!droplets.getEnabled() || dropletList.size() > 1400) return;
        int count = splash.water ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = random(1.1f, 2.6f) * source.mass;
            Droplet droplet = new Droplet();
            droplet.x = x;
            droplet.y = splash.y + 0.02;
            droplet.z = z;
            droplet.vx = Math.cos(angle) * speed * 0.45 + source.vx * 0.08;
            droplet.vz = Math.sin(angle) * speed * 0.45 + source.vz * 0.08;
            droplet.vy = random(1.9f, 4.1f);
            droplet.maxLife = random(0.28f, 0.5f);
            dropletList.add(droplet);
        }
    }

    private void updateEffects(Vec3d center, float dt) {
        splashList.removeIf(splash -> (splash.life += dt) >= splash.maxLife);
        for (int i = dropletList.size() - 1; i >= 0; i--) {
            Droplet droplet = dropletList.get(i);
            droplet.life += dt;
            if (droplet.life >= droplet.maxLife) {
                dropletList.remove(i);
                continue;
            }
            droplet.vy -= GRAVITY * 0.55 * dt;
            droplet.x += droplet.vx * dt;
            droplet.y += droplet.vy * dt;
            droplet.z += droplet.vz * dt;
        }

        if (!groundMist.getEnabled()) {
            mistList.clear();
        } else {
            int target = (int) MathHelper.clamp(22 * density.getValue() * presetDensity(), 6, MAX_MIST);
            float range = radius.getValue();
            mistList.removeIf(mist -> {
                mist.life += dt;
                double dx = mist.x - center.x;
                double dz = mist.z - center.z;
                mist.x += mist.vx * dt;
                mist.z += mist.vz * dt;
                return mist.life > mist.maxLife || dx * dx + dz * dz > range * range;
            });
            while (mistList.size() < target) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double distance = Math.sqrt(random.nextDouble()) * range * 0.85;
                Mist mist = new Mist();
                mist.x = center.x + Math.cos(angle) * distance;
                mist.z = center.z + Math.sin(angle) * distance;
                mist.y = surfaceY(MathHelper.floor(mist.x), MathHelper.floor(mist.z)) + random(0.05f, 0.9f);
                mist.size = random(2.2f, 5.6f);
                mist.maxLife = random(2.4f, 5.5f);
                mist.life = random(0.0f, 1.0f);
                mist.vx = random(-0.35f, 0.35f) * wind.getValue();
                mist.vz = random(-0.35f, 0.35f) * wind.getValue();
                mistList.add(mist);
            }
        }

        if (!lightning.getEnabled()) {
            flash = 0.0f;
        } else {
            flash = Math.max(0.0f, flash - dt * 3.4f);
            nextFlashIn -= dt;
            if (nextFlashIn <= 0.0f) {
                flash = random(0.55f, 1.0f);
                nextFlashIn = random(6.0f, 16.0f);
            }
        }
    }

    @Subscribe
    public void onWorldRender(RenderEvent.Draw3D event) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;
        if (onlyWhenRaining.getEnabled() && !mc.world.isRaining()) {
            clearAll();
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = MathHelper.clamp((float) ((now - lastFrameNanos) / 1_000_000_000.0), 0.0f, 0.05f);
        lastFrameNanos = now;
        clock += dt;

        Vec3d cameraPos = camera.getCameraPos();
        updateDrops(cameraPos, dt);
        updateEffects(cameraPos, dt);

        float[] color = resolveColor();
        float brightness = MathHelper.clamp(ambientLight(camera) + flash * 0.9f, 0.0f, 1.65f);
        float alpha = opacity.getValue() * brightness;
        MatrixStack matrices = event.getMatrices();

        if (groundMist.getEnabled()) drawMist(matrices, camera, cameraPos, color, alpha);
        drawStreaks(matrices, camera, cameraPos, color, alpha, false);
        if (splashes.getEnabled()) drawRipples(matrices, cameraPos, color, alpha);
        drawStreaks(matrices, camera, cameraPos, color, alpha, true);
        if (splashes.getEnabled()) {
            drawCrowns(matrices, camera, cameraPos, color, alpha);
            if (droplets.getEnabled()) drawDroplets(matrices, camera, cameraPos, color, alpha);
        }
    }

    public void renderWetSurfaceFrame(Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!isEnabled() || !wetGround.getEnabled() || mc.player == null || mc.world == null) return;
        if (onlyWhenRaining.getEnabled() && !mc.world.isRaining()) return;
        WetSurfaceRenderer.Parameters parameters = new WetSurfaceRenderer.Parameters();
        parameters.wetness = wetness.getValue();
        parameters.puddleCoverage = puddleCoverage.getValue();
        parameters.reflectionStrength = reflectionStrength.getValue();
        parameters.maxDistance = reflectionDistance.getValue();
        parameters.rippleStrength = rippleStrength.getValue();
        parameters.rainAmount = MathHelper.clamp(0.55f + density.getValue() * presetDensity() * 0.28f, 0.55f, 1.45f);
        parameters.ripples = puddleRipples.getEnabled();
        parameters.reflectionSteps = reflectionQuality.isSelected("Высокое") ? 16
                : reflectionQuality.isSelected("Быстро") ? 6 : 10;
        WetSurfaceRenderer.getInstance().apply(camera, viewMatrix, projectionMatrix, parameters);
    }

    private void drawStreaks(MatrixStack matrices, Camera camera, Vec3d cameraPos,
                             float[] rgb, float alphaMultiplier, boolean highlight) {
        if (drops.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float sizeMultiplier = dropSize.getValue();
        float range = radius.getValue();
        float fadeStart = range * 0.62f;

        for (Drop drop : drops) {
            double dx = drop.x - cameraPos.x;
            double dy = drop.y - cameraPos.y;
            double dz = drop.z - cameraPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > range + 4.0) continue;
            float distanceFade = distance <= fadeStart ? 1.0f
                    : 1.0f - MathHelper.clamp((float) ((distance - fadeStart) / (range - fadeStart + 1.0f)), 0.0f, 1.0f);
            float nearFade = distance < 0.7 ? (float) (distance / 0.7) : 1.0f;
            double speed = Math.sqrt(drop.vx * drop.vx + drop.vy * drop.vy + drop.vz * drop.vz);
            if (distanceFade <= 0.02f || speed < 0.0001) continue;

            float length = (float) MathHelper.clamp(speed * 0.055 * sizeMultiplier, 0.16, 2.6);
            float width = (float) (0.014 + 0.019 * drop.mass) * sizeMultiplier * (highlight ? 0.45f : 1.0f);
            float alpha = Math.min(alphaMultiplier * drop.brightness * distanceFade * nearFade
                    * (highlight ? 0.30f : 0.42f), 0.95f);
            if (alpha <= 0.006f) continue;

            float yawRadians = (float) Math.toRadians(camera.getYaw());
            double horizontal = drop.vx * Math.cos(yawRadians) + drop.vz * Math.sin(yawRadians);
            float tilt = MathHelper.clamp((float) Math.toDegrees(Math.atan2(horizontal, Math.abs(drop.vy) + 0.0001)), -55.0f, 55.0f);
            matrices.push();
            matrices.translate(dx, dy, dz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-tilt));
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float half = width * 0.5f;
            float top = length * 0.5f;
            float bottom = -length * 0.5f;
            quad(buffer, matrix, -half * 0.35f, top, half * 0.35f, top,
                    half, bottom, -half, bottom, rgb, 0.0f, alpha);
            if (!highlight) {
                float halo = half * 2.6f;
                quad(buffer, matrix, -halo * 0.5f, top, halo * 0.5f, top,
                        halo, bottom, -halo, bottom, rgb, 0.0f, alpha * 0.22f);
            } else {
                float lensTop = bottom + length * 0.34f;
                float lensWidth = half * 1.25f;
                float[] white = {1.0f, 1.0f, 1.0f};
                quad(buffer, matrix, -lensWidth, lensTop, lensWidth, lensTop,
                        lensWidth, bottom, -lensWidth, bottom, white, 0.0f, alpha * 0.85f);
            }
            matrices.pop();
        }
        draw(buffer, highlight);
    }

    private void drawRipples(MatrixStack matrices, Vec3d cameraPos, float[] rgb, float alphaMultiplier) {
        if (splashList.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Splash splash : splashList) {
            float progress = MathHelper.clamp(splash.life / splash.maxLife, 0.0f, 1.0f);
            float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            float outer = splash.scale * (0.18f + eased * (splash.water ? 1.35f : 0.85f));
            float inner = outer * (0.55f + 0.35f * eased);
            float alpha = alphaMultiplier * (1.0f - progress) * (splash.water ? 0.5f : 0.38f);
            if (alpha <= 0.01f) continue;

            matrices.push();
            matrices.translate(splash.x - cameraPos.x, splash.y - cameraPos.y, splash.z - cameraPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            for (int segment = 0; segment < RIPPLE_SEGMENTS; segment++) {
                double angle0 = Math.PI * 2.0 / RIPPLE_SEGMENTS * segment;
                double angle1 = Math.PI * 2.0 / RIPPLE_SEGMENTS * (segment + 1);
                vertex(buffer, matrix, (float) (Math.cos(angle0) * inner), 0.0f, (float) (Math.sin(angle0) * inner), rgb, alpha);
                vertex(buffer, matrix, (float) (Math.cos(angle1) * inner), 0.0f, (float) (Math.sin(angle1) * inner), rgb, alpha);
                vertex(buffer, matrix, (float) (Math.cos(angle1) * outer), 0.0f, (float) (Math.sin(angle1) * outer), rgb, 0.0f);
                vertex(buffer, matrix, (float) (Math.cos(angle0) * outer), 0.0f, (float) (Math.sin(angle0) * outer), rgb, 0.0f);
            }
            matrices.pop();
        }
        draw(buffer, false);
    }

    private void drawCrowns(MatrixStack matrices, Camera camera, Vec3d cameraPos, float[] rgb, float alphaMultiplier) {
        if (splashList.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Splash splash : splashList) {
            float progress = MathHelper.clamp(splash.life / splash.maxLife, 0.0f, 1.0f);
            if (progress > 0.55f) continue;
            float local = progress / 0.55f;
            float height = splash.scale * 0.55f * (float) Math.sin(local * Math.PI);
            float alpha = alphaMultiplier * (1.0f - local) * 0.32f;
            if (alpha <= 0.01f || height <= 0.005f) continue;

            matrices.push();
            matrices.translate(splash.x - cameraPos.x, splash.y - cameraPos.y, splash.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float base = splash.scale * 0.16f;
            quad(buffer, matrix, -base * 0.25f, height, base * 0.25f, height,
                    base, 0.0f, -base, 0.0f, rgb, 0.0f, alpha);
            matrices.pop();
        }
        draw(buffer, true);
    }

    private void drawDroplets(MatrixStack matrices, Camera camera, Vec3d cameraPos, float[] rgb, float alphaMultiplier) {
        if (dropletList.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Droplet droplet : dropletList) {
            float progress = MathHelper.clamp(droplet.life / droplet.maxLife, 0.0f, 1.0f);
            float alpha = alphaMultiplier * (1.0f - progress) * 0.5f;
            if (alpha <= 0.01f) continue;
            double speed = Math.sqrt(droplet.vx * droplet.vx + droplet.vy * droplet.vy + droplet.vz * droplet.vz);
            float length = (float) MathHelper.clamp(speed * 0.03 * dropSize.getValue(), 0.04, 0.4);
            float width = 0.016f * dropSize.getValue();
            float yawRadians = (float) Math.toRadians(camera.getYaw());
            double horizontal = droplet.vx * Math.cos(yawRadians) + droplet.vz * Math.sin(yawRadians);
            float tilt = (float) Math.toDegrees(Math.atan2(horizontal, -droplet.vy + 0.0001));

            matrices.push();
            matrices.translate(droplet.x - cameraPos.x, droplet.y - cameraPos.y, droplet.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-tilt));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            quad(buffer, matrix, -width * 0.4f, length * 0.5f, width * 0.4f, length * 0.5f,
                    width, -length * 0.5f, -width, -length * 0.5f, rgb, 0.0f, alpha);
            matrices.pop();
        }
        draw(buffer, true);
    }

    private void drawMist(MatrixStack matrices, Camera camera, Vec3d cameraPos, float[] rgb, float alphaMultiplier) {
        if (mistList.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Mist mist : mistList) {
            float progress = MathHelper.clamp(mist.life / mist.maxLife, 0.0f, 1.0f);
            float alpha = alphaMultiplier * (float) Math.sin(progress * Math.PI) * 0.05f;
            if (alpha <= 0.004f) continue;
            matrices.push();
            matrices.translate(mist.x - cameraPos.x, mist.y - cameraPos.y, mist.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float half = mist.size * 0.5f;
            quad(buffer, matrix, -half, mist.size * 0.42f, half, mist.size * 0.42f,
                    half, 0.0f, -half, 0.0f, rgb, 0.0f, alpha);
            matrices.pop();
        }
        draw(buffer, false);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                             float x1, float y1, float x2, float y2,
                             float x3, float y3, float x4, float y4,
                             float[] rgb, float topAlpha, float bottomAlpha) {
        vertex(buffer, matrix, x1, y1, 0.0f, rgb, topAlpha);
        vertex(buffer, matrix, x2, y2, 0.0f, rgb, topAlpha);
        vertex(buffer, matrix, x3, y3, 0.0f, rgb, bottomAlpha);
        vertex(buffer, matrix, x4, y4, 0.0f, rgb, bottomAlpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float[] rgb, float alpha) {
        buffer.vertex(matrix, x, y, z).color(rgb[0], rgb[1], rgb[2], alpha);
    }

    private static void draw(BufferBuilder buffer, boolean additive) {
        if (additive) {
            RenderCompat.drawPositionColorAdditiveNoDepthWrite(buffer.endNullable());
        } else {
            RenderCompat.drawPositionColorNoDepthWrite(buffer.endNullable());
        }
    }

    private float ambientLight(Camera camera) {
        try {
            return MathHelper.clamp(mc.world.getLightLevel(camera.getBlockPos()) / 15.0f, 0.3f, 1.0f);
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private static final class Drop {
        double x, y, z;
        double vx, vy, vz;
        double mass = 1.0;
        double ground;
        float phase;
        float brightness = 1.0f;
        boolean dead;
    }

    private static final class Droplet {
        double x, y, z;
        double vx, vy, vz;
        float life;
        float maxLife = 0.4f;
    }

    private static final class Splash {
        double x, y, z;
        float life;
        float maxLife = 0.4f;
        float scale = 0.4f;
        boolean water;
    }

    private static final class Mist {
        double x, y, z;
        double vx, vz;
        float size = 3.0f;
        float life;
        float maxLife = 4.0f;
    }
}
