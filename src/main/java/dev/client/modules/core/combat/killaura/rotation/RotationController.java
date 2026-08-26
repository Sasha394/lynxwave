package dev.client.modules.core.combat.killaura.rotation;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.game.RotationUpdateEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.player.PlayerVelocityStrafeEvent;
import dev.client.modules.core.combat.KillAura;
import dev.client.api.nullcry.events.types.EventType;
import dev.client.api.nullcry.modules.Module;
import dev.client.modules.core.combat.killaura.rotation.angle.AngleSmoothMode;
import dev.client.modules.core.combat.killaura.rotation.angle.LinearSmoothMode;
import dev.other.task.TaskPriority;
import dev.other.task.TaskProcessor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RotationController implements ClientApi {
    public static RotationController INSTANCE = new RotationController();

    RotationPlan lastRotationPlan;
    final TaskProcessor<RotationPlan> rotationPlanTaskProcessor = new TaskProcessor<>();
    Angle currentAngle, previousAngle, serverAngle = Angle.DEFAULT;

    public RotationController() {
        LynxWave.getInstance().getEventBus().register(this);
    }

    public void setRotation(Angle value) {
        if (value == null) {
            this.previousAngle = this.currentAngle != null ? this.currentAngle : AngleUtil.cameraAngle();
        } else {
            this.previousAngle = this.currentAngle;
        }
        this.currentAngle = value;
    }

    public Angle getRotation() {
        return currentAngle != null ? currentAngle : AngleUtil.cameraAngle();
    }

    public Angle getPreviousRotation() {
        if (currentAngle != null && previousAngle != null) {
            return previousAngle;
        }
        return mc.player != null ? new Angle(mc.player.lastYaw, mc.player.lastPitch) : Angle.DEFAULT;
    }

    public Angle getMoveRotation() {
        if (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled()) {
            return AngleUtil.cameraAngle();
        }
        RotationPlan rotationPlan = getCurrentRotationPlan();
        return currentAngle != null && rotationPlan != null && rotationPlan.isMoveCorrection() ? currentAngle : AngleUtil.cameraAngle();
    }

    public RotationPlan getCurrentRotationPlan() {
        return rotationPlanTaskProcessor.fetchActiveTaskValue() != null ? rotationPlanTaskProcessor.fetchActiveTaskValue() : lastRotationPlan;
    }

    public void rotateTo(Angle.VecRotation vecRotation, LivingEntity entity, int reset, RotationConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(vecRotation.getAngle(), vecRotation.getVec(), entity, reset), taskPriority, provider);
    }

    public void rotateTo(Angle angle, int reset, RotationConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(angle,angle.toVector(),null, reset), taskPriority, provider);
    }

    public void rotateTo(Angle angle, int reset, RotationConfig configurable, TaskPriority taskPriority, Module provider, int holdTicks) {
        rotateTo(configurable.createRotationPlan(angle, angle.toVector(), null, reset), taskPriority, provider, holdTicks);
    }

    public void rotateTo(Angle angle, RotationConfig configurable, TaskPriority taskPriority, Module provider) {
        rotateTo(configurable.createRotationPlan(angle,angle.toVector(),null,1), taskPriority, provider);
    }

    public void rotateTo(RotationPlan plan, TaskPriority taskPriority, Module provider) {
        rotationPlanTaskProcessor.addTask(new TaskProcessor.Task<>(1, taskPriority.getPriority(), provider, plan));
    }

    public void rotateTo(RotationPlan plan, TaskPriority taskPriority, Module provider, int holdTicks) {
        int ticks = Math.max(1, holdTicks);
        rotationPlanTaskProcessor.addTask(new TaskProcessor.Task<>(ticks, taskPriority.getPriority(), provider, plan));
    }

    public void smoothReturnToCamera() {
        smoothReturnToCamera(new LinearSmoothMode());
    }

    public void smoothReturnToCamera(AngleSmoothMode smoothMode) {
        if (mc.player == null || mc.world == null) {
            resetRotationState();
            return;
        }

        rotationPlanTaskProcessor.activeTasks.clear();
        rotationPlanTaskProcessor.tickCounter = 0;
        Angle cameraAngle = AngleUtil.cameraAngle();
        lastRotationPlan = new RotationPlan(cameraAngle, cameraAngle.toVector(), null, smoothMode, 1, 2.0F, false, true);
    }

    public void update() {
        if (mc.player == null || mc.world == null) {
            resetRotationState();
            return;
        }

        RotationPlan activePlan = getCurrentRotationPlan();
        if (activePlan == null) return;

        Angle clientAngle = AngleUtil.cameraAngle();
        if (lastRotationPlan != null) {
            double differenceFromCurrentToPlayer = computeRotationDifference(serverAngle, clientAngle);
            if (activePlan.getTicksUntilReset() <= rotationPlanTaskProcessor.tickCounter && differenceFromCurrentToPlayer < activePlan.getResetThreshold()) {
                setRotation(null);
                lastRotationPlan = null;
                rotationPlanTaskProcessor.tickCounter = 0;
                return;
            }
        }

        Angle newAngle = activePlan.nextRotation(currentAngle != null ? currentAngle : clientAngle, rotationPlanTaskProcessor.fetchActiveTaskValue() == null).adjustSensitivity();
        setRotation(newAngle);
        lastRotationPlan = activePlan;
        rotationPlanTaskProcessor.tick(1);
    }

    public static double computeRotationDifference(Angle a, Angle b) {
        return Math.hypot(Math.abs(computeAngleDifference(a.getYaw(), b.getYaw())), Math.abs(a.getPitch() - b.getPitch()));
    }

    public static float computeAngleDifference(float a, float b) {
        return MathHelper.wrapDegrees(a - b);
    }

    private Vec3d fixVelocity(Vec3d currVelocity, Vec3d movementInput, float speed) {
        if (currentAngle != null) {
            float yaw = currentAngle.getYaw();
            double d = movementInput.lengthSquared();

            if (d < 1.0E-7) {
                return Vec3d.ZERO;
            } else {
                Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);

                float f = MathHelper.sin(yaw * 0.017453292f);
                float g = MathHelper.cos(yaw * 0.017453292f);

                return new Vec3d(vec3d.getX() * g - vec3d.getZ() * f, vec3d.getY(), vec3d.getZ() * g + vec3d.getX() * f);
            }
        }
        return currVelocity;
    }

    public void clear() {
        resetRotationState();
    }

    private void resetRotationState() {
        rotationPlanTaskProcessor.activeTasks.clear();
        rotationPlanTaskProcessor.tickCounter = 0;
        lastRotationPlan = null;
        currentAngle = null;
        previousAngle = null;
    }

    @Subscribe
    public void onPlayerVelocityStrafe(PlayerVelocityStrafeEvent e) {
        if (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled()) {
            return;
        }
        RotationPlan currentRotationPlan = getCurrentRotationPlan();
        if (currentRotationPlan != null && currentRotationPlan.isMoveCorrection()) {
            e.setVelocity(fixVelocity(e.getVelocity(), e.getMovementInput(), e.getSpeed()));
        }
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) {
            resetRotationState();
            return;
        }

        EventManager.call(new RotationUpdateEvent(EventType.PRE));
        update();
        EventManager.call(new RotationUpdateEvent(EventType.POST));
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!event.isCancelled()) switch (event.getPacket()) {
            case PlayerMoveC2SPacket player when player.changesLook() -> serverAngle = new Angle(player.getYaw(1), player.getPitch(1));
            case PlayerPositionLookS2CPacket player -> serverAngle = new Angle(player.change().yaw(), player.change().pitch());
            default -> {}
        }
    }
}
