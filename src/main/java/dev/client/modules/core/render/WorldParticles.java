package dev.client.modules.core.render;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.*;
import dev.client.modules.core.render.worldparticles.ParticleSpawner;
import dev.client.modules.core.render.worldparticles.WorldParticle;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WorldParticles extends Module implements ClientApi {

    private final List<WorldParticle> particles = new ArrayList<>();
    private final TimerUtil timer = new TimerUtil();

    private Vec3d lastPlayerPos = Vec3d.ZERO;
    private Vec3d playerVelocity = Vec3d.ZERO;
    private double playerSpeed = 0;

    private final ModeElement mode = new ModeElement("Режим", () -> true)
            .set("3D Кубы", "Кристаллы", "Корона", "Куб", "Доллар", "Сердце", "Молния", "Линия", "Ромб", "Снежинка", "Звезда", "Звезда 2", "Треугольник", "Свечение", "Рандом")
            .applyDefault("Звезда")
            .register(this);

    private final Slider cubeCount = new Slider("Количество", () -> true)
            .set(10, 500, 1)
            .applyDefault(100)
            .register(this);

    private final Slider lifeTime = new Slider("Время жизни", () -> true)
            .set(2, 60, 1)
            .applyDefault(10)
            .register(this);

    private final Slider size = new Slider("Размер", () -> true)
            .set(0.1f, 1.5f, 0.01f)
            .applyDefault(1.5f)
            .register(this);

    private final Slider glowSize = new Slider("Свечение", () -> true)
            .set(0.1f, 5.0f, 0.1f)
            .applyDefault(3f)
            .register(this);

    private final CheckBox physics = new CheckBox("Физика", () -> true)
            .defaultValue(false)
            .register(this);

    private final CheckBox clientColor = new CheckBox("Клиентский цвет", () -> true)
            .defaultValue(false)
            .register(this);

    private final CheckBox randomColor = new CheckBox("Рандомный цвет", () -> !clientColor.getEnabled())
            .defaultValue(false)
            .register(this);

    private final CheckBox whiteOnSpawn = new CheckBox("Белые при спавне", () -> true)
            .defaultValue(true)
            .register(this);

    private final CheckBox whiteCenter = new CheckBox("Белый центр", () -> !mode.isSelected("3D Кубы"))
            .defaultValue(false)
            .register(this);

    private final ColorPicker cubeColor = new ColorPicker("Цвет", () -> !randomColor.getEnabled() && !clientColor.getEnabled())
            .set(0xFF896148)
            .defaultValue(0xFF896148)
            .register(this);

    public WorldParticles() {
        super("WorldParticles", ModuleCategory.Render, "Летающие частицы в мире");
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        particles.clear();
        lastPlayerPos = Vec3d.ZERO;
        playerVelocity = Vec3d.ZERO;
        playerSpeed = 0;
    }

    private WorldParticle.ParticleType getParticleType() {
        String selected = mode.getValue();
        return switch (selected) {
            case "3D Кубы" -> WorldParticle.ParticleType.CUBE_3D;
            case "Кристаллы" -> WorldParticle.ParticleType.CRYSTAL;
            case "Корона" -> WorldParticle.ParticleType.CROWN;
            case "Куб" -> WorldParticle.ParticleType.CUBE_BLAST;
            case "Доллар" -> WorldParticle.ParticleType.DOLLAR;
            case "Сердце" -> WorldParticle.ParticleType.HEART;
            case "Молния" -> WorldParticle.ParticleType.LIGHTNING;
            case "Линия" -> WorldParticle.ParticleType.LINE;
            case "Ромб" -> WorldParticle.ParticleType.RHOMBUS;
            case "Снежинка" -> WorldParticle.ParticleType.SNOWFLAKE;
            case "Звезда" -> WorldParticle.ParticleType.STAR;
            case "Звезда 2" -> WorldParticle.ParticleType.STAR_ALT;
            case "Треугольник" -> WorldParticle.ParticleType.TRIANGLE;
            case "Свечение" -> WorldParticle.ParticleType.GLOW;
            case "Рандом" -> WorldParticle.ParticleType.RANDOM;
            default -> WorldParticle.ParticleType.CUBE_3D;
        };
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(mc.player);

        if (lastPlayerPos != Vec3d.ZERO) {
            playerVelocity = currentPos.subtract(lastPlayerPos);
            playerSpeed = playerVelocity.horizontalLength();
        }
        lastPlayerPos = currentPos;

        double despawnDistSq = ParticleSpawner.getDespawnDistanceSquared();
        for (WorldParticle p : particles) {
            if (!p.isFadingOut()) {
                double distSq = p.getHorizontalDistanceSquaredTo(currentPos);
                if (distSq > despawnDistSq) {
                    p.startFadeOut();
                }
            }
        }

        int actualDelay = ParticleSpawner.calculateSpawnDelay(playerSpeed);

        if (particles.size() < cubeCount.getValue() && timer.isReached(actualDelay)) {
            int spawnCount = ParticleSpawner.calculateSpawnCount(playerSpeed, particles.size(), cubeCount.getValue().intValue());
            long lifeTimeMs = (long) (lifeTime.getValue() * 1000);
            WorldParticle.ParticleType type = getParticleType();

            for (int i = 0; i < spawnCount && particles.size() < cubeCount.getValue(); i++) {
                WorldParticle particle = ParticleSpawner.createParticle(currentPos, playerVelocity, playerSpeed, lifeTimeMs, type);
                particle.setPhysics(physics.getEnabled());
                particle.setSize(size.getValue());
                particles.add(particle);
            }

            timer.reset();
        }
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (particles.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = event.getMatrices();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        long now = System.currentTimeMillis();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();
        float rotation = (float) (now % 9000L) / 9000.0F * 360.0F;
        int baseColor = clientColor.getEnabled() ? Interface.INSTANCE.getMainColor() : cubeColor.getColorRGBA();
        float glow = glowSize.getValue();
        boolean useRandomColor = randomColor.getEnabled();
        boolean useWhiteOnSpawn = whiteOnSpawn.getEnabled();
        boolean useWhiteCenter = whiteCenter.getEnabled();

        Iterator<WorldParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            WorldParticle p = iterator.next();
            p.update(now);
            if (p.shouldRemove()) {
                iterator.remove();
            }
        }

        for (WorldParticle p : particles) {
            double distSq = p.getDistanceSquaredTo(cameraPos);
            if (distSq < 150 * 150) {
                p.render(matrices, immediate, cameraPos, baseColor, rotation, cameraYaw, cameraPitch, glow, useRandomColor, useWhiteOnSpawn, useWhiteCenter);
            }
        }

        immediate.draw();
    }
}
