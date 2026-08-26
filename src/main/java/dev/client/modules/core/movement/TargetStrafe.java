package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.InputEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TargetStrafe extends Module {
    public static TargetStrafe INSTANCE;

    public ModeElement mode = new ModeElement("Режим", () -> true).set("Matrix", "Grim").defaultValue("Matrix").register(this);
    ModeElement type = new ModeElement("Точка ходьбы", () -> mode.isSelected("Grim")).set("Cube", "Center", "Circle").defaultValue("Cube").register(this);
    ModeElement typeMatrix = new ModeElement("Точка для обхода", () -> mode.isSelected("Matrix")).set("Cube", "Circle").defaultValue("Circle").register(this);
    Slider grimRadius = new Slider("Радиус обхода", () -> mode.isSelected("Grim") && (type.isSelected("Cube") || type.isSelected("Circle"))).set(0.1f, 1.5f, 0.01f).defaultValue(0.87f).register(this);
    SelectElements settings = new SelectElements("Настройки", () -> true)
            .set("Auto Jump", "Only Key Pressed", "In front of the target", "Direction Mode")
            .defaultValue("Auto Jump")
            .register(this);
    ModeElement directionMode = new ModeElement("Направление", () -> settings.isSelected("Direction Mode")).set("Clockwise", "Counterclockwise", "Random").defaultValue("Clockwise").register(this);
    Slider radius = new Slider("Радиус", () -> mode.isSelected("Matrix")).set(0.1f, 7.0f, 0.1f).defaultValue(2.5f).register(this);
    Slider speed = new Slider("Скорость", () -> mode.isSelected("Matrix")).set(0.1f, 1.0f, 0.01f).defaultValue(0.3f).register(this);

    private int grimPointIndex = 0;

    public TargetStrafe() {
        super("TargetStrafe", ModuleCategory.Movement, "Обходит цель по выбранной траектории.");
    }

    @Subscribe
    public void onInput(InputEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mode.isSelected("Grim")) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (settings.isSelected("Only Key Pressed") && !isMovementKeyPressed()) {
            return;
        }

        Vec3d playerPos = dev.client.api.compat.EntityCompat.pos(mc.player);
        Vec3d targetPos = getTargetPos(target);
        double r = grimRadius.getValue();

        double dist = playerPos.distanceTo(targetPos);
        double desired = r;
        boolean catchUp = dist > desired + 0.3;

        Vec3d nextPoint;
        int directionMultiplier = getDirectionMultiplier();

        if (catchUp) {
            if (settings.isSelected("In front of the target")) {
                float targetYaw = target.getYaw();
                double chaseR = Math.min(dist, desired * 2.0);
                nextPoint = targetPos.add(
                        -Math.sin(Math.toRadians(targetYaw)) * chaseR,
                        0,
                        Math.cos(Math.toRadians(targetYaw)) * chaseR
                );
            } else {
                nextPoint = new Vec3d(targetPos.x, playerPos.y, targetPos.z);
            }
        } else {
            if (settings.isSelected("In front of the target")) {
                float targetYaw = target.getYaw();
                if (type.isSelected("Center")) {
                    nextPoint = targetPos.add(
                            -Math.sin(Math.toRadians(targetYaw)) * r * directionMultiplier,
                            0,
                            Math.cos(Math.toRadians(targetYaw)) * r * directionMultiplier
                    );
                } else {
                    double offset = Math.cos(System.currentTimeMillis() / 500.0) * r * directionMultiplier;
                    nextPoint = targetPos.add(
                            -Math.sin(Math.toRadians(targetYaw)) * r + Math.cos(Math.toRadians(targetYaw)) * offset,
                            0,
                            Math.cos(Math.toRadians(targetYaw)) * r + Math.sin(Math.toRadians(targetYaw)) * offset
                    );
                }
            } else {
                if (type.isSelected("Cube")) {
                    Vec3d[] points = new Vec3d[]{
                            new Vec3d(targetPos.x - r, playerPos.y, targetPos.z - r),
                            new Vec3d(targetPos.x - r, playerPos.y, targetPos.z + r),
                            new Vec3d(targetPos.x + r, playerPos.y, targetPos.z + r),
                            new Vec3d(targetPos.x + r, playerPos.y, targetPos.z - r)
                    };

                    if (playerPos.distanceTo(points[grimPointIndex]) < 0.5) {
                        grimPointIndex = (grimPointIndex + directionMultiplier + points.length) % points.length;
                    }

                    nextPoint = points[grimPointIndex];
                } else if (type.isSelected("Circle")) {
                    double baseAngle = (System.currentTimeMillis() % 3600L) / 3600.0 * 4 * Math.PI;
                    double angle = directionMultiplier > 0 ? baseAngle : (2 * Math.PI - baseAngle);
                    nextPoint = new Vec3d(
                            targetPos.x + Math.cos(angle) * r,
                            playerPos.y,
                            targetPos.z + Math.sin(angle) * r
                    );
                } else {
                    nextPoint = new Vec3d(targetPos.x, playerPos.y, targetPos.z);
                }
            }
        }

        Vec3d dir = nextPoint.subtract(playerPos);
        if (dir.lengthSquared() < 1.0E-6) return;
        Vec3d direction = dir.normalize();

        float yaw = RotationController.INSTANCE.getRotation().getYaw();
        float movementAngle = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90F;
        float angleDiff = MathHelper.wrapDegrees(movementAngle - yaw);

        boolean forward = false, back = false, left = false, right = false;

        if (angleDiff >= -22.5 && angleDiff < 22.5) {
            forward = true;
        } else if (angleDiff >= 22.5 && angleDiff < 67.5) {
            forward = true;
            right = true;
        } else if (angleDiff >= 67.5 && angleDiff < 112.5) {
            right = true;
        } else if (angleDiff >= 112.5 && angleDiff < 157.5) {
            back = true;
            right = true;
        } else if (angleDiff >= -67.5 && angleDiff < -22.5) {
            forward = true;
            left = true;
        } else if (angleDiff >= -112.5 && angleDiff < -67.5) {
            left = true;
        } else if (angleDiff >= -157.5 && angleDiff < -112.5) {
            back = true;
            left = true;
        } else {
            back = true;
        }

        event.setDirectional(forward, back, left, right);

        if (settings.isSelected("Auto Jump") && mc.player.isOnGround()) {
            event.setJumping(true);
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mode.isSelected("Matrix")) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (settings.isSelected("Only Key Pressed") && !isMovementKeyPressed()) {
            return;
        }

        if (settings.isSelected("Auto Jump") && mc.player.isOnGround()) {
            mc.player.jump();
        }

        Vec3d playerPos = dev.client.api.compat.EntityCompat.pos(mc.player);
        Vec3d targetPos = getTargetPos(target);
        double r = radius.getValue();

        int directionMultiplier = getDirectionMultiplier();

        if (settings.isSelected("In front of the target")) {
            float targetYaw = target.getYaw();
            double x = targetPos.x - Math.sin(Math.toRadians(targetYaw)) * r * directionMultiplier;
            double z = targetPos.z + Math.cos(Math.toRadians(targetYaw)) * r * directionMultiplier;

            float yaw = (float) Math.toDegrees(Math.atan2(z - playerPos.z, x - playerPos.x)) - 90F;
            double motionSpeed = speed.getValue();
            mc.player.setVelocity(
                    -Math.sin(Math.toRadians(yaw)) * motionSpeed,
                    mc.player.getVelocity().y,
                    Math.cos(Math.toRadians(yaw)) * motionSpeed
            );
            return;
        }

        if (typeMatrix.isSelected("Cube")) {
            Vec3d[] points = new Vec3d[]{
                    new Vec3d(targetPos.x - r, playerPos.y, targetPos.z - r),
                    new Vec3d(targetPos.x - r, playerPos.y, targetPos.z + r),
                    new Vec3d(targetPos.x + r, playerPos.y, targetPos.z + r),
                    new Vec3d(targetPos.x + r, playerPos.y, targetPos.z - r)
            };

            if (playerPos.distanceTo(points[grimPointIndex]) < 0.5) {
                grimPointIndex = (grimPointIndex + directionMultiplier + points.length) % points.length;
            }

            Vec3d nextPoint = points[grimPointIndex];
            Vec3d dirVec = nextPoint.subtract(playerPos);
            if (dirVec.lengthSquared() < 1.0E-6) return;
            Vec3d dir = dirVec.normalize();

            float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F;
            double motionSpeed = speed.getValue();
            mc.player.setVelocity(
                    -Math.sin(Math.toRadians(yaw)) * motionSpeed,
                    mc.player.getVelocity().y,
                    Math.cos(Math.toRadians(yaw)) * motionSpeed
            );
        } else if (typeMatrix.isSelected("Circle")) {
            double angle = Math.atan2(playerPos.z - targetPos.z, playerPos.x - targetPos.x);
            angle += directionMultiplier * speed.getValue() / Math.max(playerPos.distanceTo(targetPos), r);

            double x = targetPos.x + r * Math.cos(angle);
            double z = targetPos.z + r * Math.sin(angle);

            float yaw = (float) Math.toDegrees(Math.atan2(z - playerPos.z, x - playerPos.x)) - 90F;
            double motionSpeed = speed.getValue();

            mc.player.setVelocity(
                    -Math.sin(Math.toRadians(yaw)) * motionSpeed,
                    mc.player.getVelocity().y,
                    Math.cos(Math.toRadians(yaw)) * motionSpeed
            );
        }
    }

    @Override
    public void onEnabled() {
        grimPointIndex = 0;
        super.onEnabled();
    }

    private LivingEntity getTarget() {
        if (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled()) return null;
        return KillAura.INSTANCE.getTarget();
    }

    private Vec3d getTargetPos(LivingEntity target) {
        if (KillAura.INSTANCE == null) {
            return dev.client.api.compat.EntityCompat.pos(target);
        }
        Vec3d predicted = dev.client.api.compat.EntityCompat.pos(KillAura.INSTANCE.target);
        return predicted != null ? predicted : dev.client.api.compat.EntityCompat.pos(target);
    }

    private int getDirectionMultiplier() {
        if (directionMode.isSelected("Counterclockwise")) {
            return -1;
        }
        if (directionMode.isSelected("Random")) {
            long time = System.currentTimeMillis() / 3000;
            return (time % 2 == 0) ? 1 : -1;
        }
        return 1;
    }

    private boolean isMovementKeyPressed() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }
}
