package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;

public class AresSpeed extends Module {
    public static AresSpeed INSTANCE;

    public AresSpeed() {
        super("AresSpeed", ModuleCategory.Movement, "Ускорение к цели на основе дистанции");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Дистанция").defaultValue("Дистанция").register(this);
    CheckBox pauseInLiquids = new CheckBox("Пауза в воде", () -> true).defaultValue(false).register(this);
    CheckBox pauseWhileSneaking = new CheckBox("Пауза при крадении", () -> true).defaultValue(false).register(this);
    CheckBox onlyPlayers = new CheckBox("Только игроки", () -> true).defaultValue(false).register(this);
    Slider speedFactor = new Slider("Скорость", () -> true).set(1.0f, 15.0f, 0.1f).defaultValue(8.0f).register(this);
    Slider distance = new Slider("Дистанция", () -> true).set(0.5f, 5.0f, 0.1f).defaultValue(3.0f).register(this);
    CheckBox predictMovement = new CheckBox("Предсказание движения", () -> true).defaultValue(true).register(this);
    Slider predictionFactor = new Slider("Множитель предсказания", () -> predictMovement.getEnabled()).set(1.0f, 5.0f, 0.1f).defaultValue(2.0f).register(this);
    CheckBox smoothMovement = new CheckBox("Плавное движение", () -> true).defaultValue(true).register(this);

    private Entity targetEntity = null;
    private Vec3d lastTargetPos = null;
    private Vec3d predictedPos = null;
    private double[] lastMotion = new double[]{0.0, 0.0};

    private void updateTarget() {
        if (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled() || KillAura.INSTANCE.getTarget() == null) {
            targetEntity = null;
            lastTargetPos = null;
            predictedPos = null;
            return;
        }

        targetEntity = KillAura.INSTANCE.getTarget();
        if (targetEntity != null) {
            if (lastTargetPos == null) {
                predictedPos = lastTargetPos = dev.client.api.compat.EntityCompat.pos(targetEntity);
            } else {
                Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(targetEntity);
                Vec3d velocity = new Vec3d(
                        currentPos.x - lastTargetPos.x,
                        currentPos.y - lastTargetPos.y,
                        currentPos.z - lastTargetPos.z
                );

                if (predictMovement.getEnabled()) {
                    predictedPos = currentPos.add(
                            velocity.x * predictionFactor.getValue(),
                            velocity.y * predictionFactor.getValue(),
                            velocity.z * predictionFactor.getValue()
                    );

                    if (!mc.world.isChunkLoaded((int) predictedPos.x >> 4, (int) predictedPos.z >> 4)) {
                        predictedPos = currentPos;
                    }
                } else {
                    predictedPos = currentPos;
                }
                lastTargetPos = currentPos;
            }
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity player
                && handlerClient().getFriendManager().isFriend(player.getName().getString())) return false;
        if (onlyPlayers.getEnabled() && !(entity instanceof PlayerEntity)) return false;
        return entity instanceof LivingEntity || entity instanceof BoatEntity;
    }

    private void handleDistanceMode() {
        if (targetEntity == null) return;
        if (mc.player.hurtTime > 0) return;
        if (!MovingUtil.isMoving()) return;

        Vec3d targetPos = (predictMovement.getEnabled() && predictedPos != null) ? predictedPos : dev.client.api.compat.EntityCompat.pos(targetEntity);
        double dist = mc.player.squaredDistanceTo(targetPos);

        if (dist <= (distance.getValue() * distance.getValue())) {
            float slipperiness = mc.world.getBlockState(mc.player.getVelocityAffectingPos()).getBlock().getSlipperiness();
            float horizontalFriction = mc.player.isOnGround() ? slipperiness * 0.91f : 0.91f;
            float verticalFriction = mc.player.isOnGround() ? slipperiness : 0.99f;

            double actualSpeed = speedFactor.getValue() * 0.01 * horizontalFriction * verticalFriction;
            double[] directionMotion = getDirectionToPoint(dev.client.api.compat.EntityCompat.pos(mc.player), targetPos, actualSpeed);

            if (smoothMovement.getEnabled()) {
                double accelFactor = 0.6;
                directionMotion[0] = lastMotion[0] + (directionMotion[0] - lastMotion[0]) * accelFactor;
                directionMotion[1] = lastMotion[1] + (directionMotion[1] - lastMotion[1]) * accelFactor;
            }

            lastMotion[0] = directionMotion[0];
            lastMotion[1] = directionMotion[1];
            mc.player.addVelocity(directionMotion[0], 0.0, directionMotion[1]);
        }
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double speed) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len == 0.0) {
            return new double[]{0.0, 0.0};
        }

        return new double[]{dx / len * speed, dz / len * speed};
    }

    @Override
    public void onEnabled() {
        targetEntity = null;
        lastTargetPos = null;
        predictedPos = null;
        lastMotion = new double[]{0.0, 0.0};
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        LynxWave.tickTimer = 1.0f;
        super.onDisabled();
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInLiquids.getEnabled()) return;
        if (mc.player.isSneaking() && pauseWhileSneaking.getEnabled()) return;
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInLiquids.getEnabled()) return;
        if (mc.player.isSneaking() && pauseWhileSneaking.getEnabled()) return;

        if (mode.isSelected("Дистанция")) {
            updateTarget();
            handleDistanceMode();
        }
    }
}
