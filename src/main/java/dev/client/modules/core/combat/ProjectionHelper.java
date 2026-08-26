package dev.client.modules.core.combat;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationConfig;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.other.task.TaskPriority;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProjectionHelper extends Module {
    public static ProjectionHelper INSTANCE;

    public ProjectionHelper() {
        super("ProjectionHelper", ModuleCategory.Combat, "Помогает в прицеливании предметов");
    }

    SelectElements gun = new SelectElements("Работать на", () -> true)
            .set("Арбалет", "Трезубец", "Лук", "Эндер-жемчуг")
            .defaultValue("Арбалет", "Трезубец")
            .register(this);

    SelectElements aim = new SelectElements("Аим", () -> gun.isSelected("Арбалет") || gun.isSelected("Трезубец"))
            .set("На Трезубец", "На Лук")
            .defaultValue("На Трезубец")
            .register(this);

    SelectElements filter = new SelectElements("Фильтр на", () -> !ProjectionHelper.INSTANCE.targetKillAura.getEnabled())
            .set("Игроков", "Друзей", "Невидимых", "Голых")
            .defaultValue("Игроков", "Невидимых")
            .register(this);

    CheckBox crosshair = new CheckBox("Прицел",
            () -> gun.isSelected("Арбалет") || gun.isSelected("Трезубец") || gun.isSelected("Лук") || gun.isSelected("Эндер-жемчуг"))
            .defaultValue(true)
            .register(this);

    CheckBox checkFilter = new CheckBox("Рендерить всегда",
            () -> crosshair.getEnabled() && (gun.isSelected("Арбалет") || gun.isSelected("Трезубец") || gun.isSelected("Лук") || gun.isSelected("Эндер-жемчуг")))
            .defaultValue(true)
            .register(this);

    CheckBox autoShoot = new CheckBox("Авто использование",
            () -> (gun.isSelected("Арбалет") || gun.isSelected("Трезубец") || gun.isSelected("Лук")))
            .defaultValue(true)
            .register(this);

    CheckBox wallsCheck = new CheckBox("Игнорировать игроков за стеной",
            () -> (gun.isSelected("Трезубец") && aim.isSelected("На Трезубец")))
            .defaultValue(true)
            .register(this);

    public CheckBox targetKillAura = new CheckBox("Таргетить цель киллауры",
            () -> (gun.isSelected("Трезубец") && aim.isSelected("На Трезубец")))
            .defaultValue(false)
            .register(this);

    Slider shootValue = new Slider("Сила натяга", () -> Boolean.TRUE.equals(autoShoot.getShown().get()) && autoShoot.getEnabled() && (gun.isSelected("Арбалет") || gun.isSelected("Трезубец") || gun.isSelected("Лук"))).set(11, 20, 1).defaultValue(15).register(this);
    Slider prediction = new Slider("Сила предикта", () -> gun.isSelected("Трезубец")).set(1, 3, 0.01f).defaultValue(1.1f).register(this);

    final Color MISS_COLOR = new Color(0, 255, 0, 150);
    final Color HIT_COLOR = new Color(255, 0, 0, 150);

    Entity target = null;
    Entity lastTarget = null;
    Direction hitSide = null;
    Vec3d hitPos = null;
    Vec3d renderPos = null;
    Vec3d lastHitPos = null;
    final List<Vec3d> trajectoryPoints = new ArrayList<>();
    final List<Vec3d> arrowHitPositions = new ArrayList<>();
    final float attackRange = 50f;
    final float ARROW_SPREAD_ANGLE = 9.0f;
    final double CROSSHAIR_SIZE = 0.25;
    final long SHOOT_DELAY = 1;
    final long MAX_TRIDENT_HOLD_TIME = 3000;
    private LivingEntity tridentTarget;
    private LivingEntity bowTarget;
    boolean willHit = false;
    boolean wasCharged = false;
    boolean waitingForCharge = false;
    boolean wasDrawn = false;
    long targetAcquiredTime = 0;
    long tridentDrawStartTime = 0;

    final double ARROW_GRAVITY = 0.05;
    final double ARROW_DRAG_AIR = 0.99;
    final double ARROW_DRAG_WATER = 0.6;

    final double TRIDENT_GRAVITY = 0.05;
    final double TRIDENT_DRAG_AIR = 0.99;
    final double TRIDENT_DRAG_WATER = 0.8;

    final double CROSSBOW_VELOCITY = 3.15;
    final double TRIDENT_VELOCITY = 2.5;
    final int MAX_TICKS = 300;

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (!shouldRenderCrosshairNow()) return;
        if (hitPos == null && renderPos == null) return;

        boolean hasCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW;
        boolean hasTrident = mc.player.getMainHandStack().getItem() == Items.TRIDENT || mc.player.getOffHandStack().getItem() == Items.TRIDENT;
        boolean hasBow = mc.player.getMainHandStack().getItem() == Items.BOW;
        boolean hasPearl = mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL;

        boolean validItem = (hasCrossbow && gun.isSelected("Арбалет"))
                || (hasTrident && gun.isSelected("Трезубец"))
                || (hasBow && gun.isSelected("Лук"))
                || (mc.player.getMainHandStack().getItem() == Items.SPLASH_POTION)
                || (hasPearl && gun.isSelected("Эндер-жемчуг"));

        if (!validItem) return;

        try {
            renderTrajectory(event.getMatrices());
        } catch (Exception ignored) {
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;
        boolean isCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW && gun.isSelected("Арбалет");
        boolean isTrident = (mc.player.getMainHandStack().getItem() == Items.TRIDENT || mc.player.getOffHandStack().getItem() == Items.TRIDENT) && gun.isSelected("Трезубец");
        boolean isBow = mc.player.getMainHandStack().getItem() == Items.BOW && gun.isSelected("Лук");
        boolean isPearl = mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL && gun.isSelected("Эндер-жемчуг");

        if (aim.isSelected("На Лук") && (!isBow || !mc.player.isUsingItem())) {
            if (wasDrawn) {
                bowTarget = null;
                wasDrawn = false;
            }
        }
        if (aim.isSelected("На Трезубец") && (!isTrident || !mc.player.isUsingItem())) {
            if (wasDrawn) {
                tridentTarget = null;
                wasDrawn = false;
            }
        }

        if (!isCrossbow && !isTrident && !isBow && !isPearl) {
            wasCharged = false;
            waitingForCharge = false;
            hitPos = null;
            renderPos = null;
            target = null;
            willHit = false;
            return;
        }

        if (aim.isSelected("На Трезубец") && isTrident) {
            handleTridentAim();
        } else if (aim.isSelected("На Лук") && isBow && mc.player.isUsingItem()) {
            handleBowAim();
        }

        calculateTrajectory();

        if (autoShoot.getEnabled()) {
            if (isCrossbow) {
                boolean isFullyCharged = CrossbowItem.isCharged(mc.player.getMainHandStack());
                handleCrossbowLogic(isFullyCharged);
            } else if (isTrident) {
                handleAutoShoot();
            } else if (isBow && mc.player.isUsingItem()) {
                handleBowAutoShoot();
            }
        }

        RotationController controller = RotationController.INSTANCE;
        if (!mc.player.isUsingItem() && controller.getCurrentRotationPlan() != null) {
            controller.clear();
            bowTarget = null;
            wasDrawn = false;
        }
    }

    private void handleTridentAim() {
        boolean isDrawn = mc.player.isUsingItem() && (mc.player.getStackInHand(mc.player.getActiveHand()).getItem() == Items.TRIDENT || mc.player.getOffHandStack().getItem() == Items.TRIDENT);

        if (isDrawn) {
            if (targetKillAura.getEnabled()) {
                updateTargetFromAura();
            } else {
                updateTridentTarget();
            }

            if (tridentTarget != null && mc.player.distanceTo(tridentTarget) <= attackRange) {
                aimAtTridentTarget(tridentTarget);
            }
        } else {
            tridentTarget = null;
        }

        wasDrawn = isDrawn;
    }

    private void handleBowAutoShoot() {
        if (!willHit || target == null) return;

        int useTicks = mc.player.getItemUseTime();
        if (useTicks < shootValue.getValue().intValue()) return;

        if (target instanceof LivingEntity living && !isFilter(living)) return;
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(mc.player.getMainHandStack().getItem())))
            return;
        mc.interactionManager.stopUsingItem(mc.player);
        willHit = false;
        target = null;
    }

    private void updateTargetFromAura() {
        KillAura killAura = KillAura.INSTANCE;
        LivingEntity currentTarget = null;
        if (killAura != null && killAura.isEnabled()) {
            currentTarget = killAura.getTarget();
            if (currentTarget != null) {
                tridentTarget = currentTarget;
            }
        }

        if (tridentTarget != null && tridentTarget.isAlive() && isFilter(tridentTarget) && mc.player.distanceTo(tridentTarget) <= attackRange) {
        } else if (currentTarget != null && isFilter(currentTarget) && mc.player.distanceTo(currentTarget) <= attackRange) {
            tridentTarget = currentTarget;
        } else {
            tridentTarget = null;
        }
    }

    private void updateTridentTarget() {
        java.util.List<LivingEntity> potentialTargets = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && isFilter(player)) {
                potentialTargets.add(player);
            }
        }

        if (potentialTargets.isEmpty()) {
            tridentTarget = null;
            return;
        }

        potentialTargets.sort(Comparator.comparingDouble(mc.player::distanceTo));
        tridentTarget = potentialTargets.get(0);
    }

    private void aimAtTridentTarget(LivingEntity target) {
        Vec3d playerPos = mc.player.getCameraPosVec(1.0F);
        Vec3d targetPos = dev.client.api.compat.EntityCompat.pos(target).add(0, target.getHeight() * 0.4, 0);

        double velocityX = target.getX() - target.lastX;
        double velocityY = target.getY() - target.lastY;
        double velocityZ = target.getZ() - target.lastZ;

        double gravity = 0.03;
        double initialVelocity = 2.5;

        double deltaX = targetPos.x - playerPos.x;
        double deltaY = targetPos.y - playerPos.y;
        double deltaZ = targetPos.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double flightTime = horizontalDistance / (initialVelocity * 0.8);

        Vec3d predictedPos = new Vec3d(
                targetPos.x + velocityX * flightTime * prediction.getValue(),
                targetPos.y + velocityY * Math.min(flightTime * 1.5, 0.5),
                targetPos.z + velocityZ * flightTime * prediction.getValue()
        );

        double predDeltaX = predictedPos.x - playerPos.x;
        double predDeltaY = predictedPos.y - playerPos.y;
        double predDeltaZ = predictedPos.z - playerPos.z;
        double predHorizontalDistance = Math.sqrt(predDeltaX * predDeltaX + predDeltaZ * predDeltaZ);

        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(predDeltaZ, predDeltaX)) - 90.0F);
        double optimalPitch = -Math.toDegrees(Math.atan((initialVelocity * initialVelocity - Math.sqrt(Math.pow(initialVelocity, 4) - gravity * (gravity * predHorizontalDistance * predHorizontalDistance + 2 * predDeltaY * initialVelocity * initialVelocity))) / (gravity * predHorizontalDistance)));
        if (Double.isNaN(optimalPitch) || Double.isInfinite(optimalPitch)) {
            float defaultPitch = (float) -Math.toDegrees(Math.atan2(predDeltaY, predHorizontalDistance));
            optimalPitch = defaultPitch;
        }

        RotationController controller = RotationController.INSTANCE;
        RotationConfig config = new RotationConfig(false, true);
        controller.rotateTo(new Angle(targetYaw, (float) optimalPitch), config, TaskPriority.HIGH_IMPORTANCE_1, this);
    }

    private void handleCrossbowLogic(boolean isFullyCharged) {
        if (!willHit || target == null) {
            wasCharged = false;
            lastTarget = null;
            return;
        }

        if (isFullyCharged) {
            if (target != lastTarget) {
                targetAcquiredTime = System.currentTimeMillis();
                lastTarget = target;
            }

            if (!wasCharged && System.currentTimeMillis() - targetAcquiredTime >= SHOOT_DELAY) {
                if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(mc.player.getMainHandStack().getItem()))) {
                    return;
                }
                wasCharged = true;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        } else {
            wasCharged = false;
        }
    }

    private void handleAutoShoot() {
        if (!mc.player.isUsingItem()) {
            if (tridentDrawStartTime != 0 && wasDrawn) {
                tridentDrawStartTime = 0;
                willHit = false;
                target = null;
            }
            return;
        }

        int useTicks = mc.player.getItemUseTime();

        if (tridentDrawStartTime == 0) {
            tridentDrawStartTime = System.currentTimeMillis();
        }

        boolean shouldRelease = false;

        if (useTicks >= shootValue.getValue().intValue()) {
            if (target != null && willHit) {
                if (target instanceof LivingEntity living && !isFilter(living)) {
                    willHit = false;
                    return;
                }
                shouldRelease = true;
            } else {
                shouldRelease = true;
            }
        }

        if (!shouldRelease && System.currentTimeMillis() - tridentDrawStartTime > MAX_TRIDENT_HOLD_TIME) {
            shouldRelease = true;
        }

        if (shouldRelease) {
            if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(mc.player.getMainHandStack().getItem()))) {
                return;
            }

            mc.interactionManager.stopUsingItem(mc.player);
            tridentDrawStartTime = 0;
            willHit = false;
            target = null;
        }
    }

    private void handleBowAim() {
        boolean isDrawn = isBowDrawn();

        if (isDrawn) {
            updateBowTarget();
            if (bowTarget != null && mc.player.distanceTo(bowTarget) <= attackRange) {
                aimAtBowTarget(bowTarget);
            }
        } else if (wasDrawn) {
            bowTarget = null;
        }
        wasDrawn = isDrawn;
    }

    private boolean isBowDrawn() {
        return mc.player.isUsingItem() && mc.player.getStackInHand(mc.player.getActiveHand()).getItem() == Items.BOW;
    }

    private void updateBowTarget() {
        List<LivingEntity> potentialTargets = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && isFilter(player)) {
                potentialTargets.add(player);
            }
        }
        if (potentialTargets.isEmpty()) {
            bowTarget = null;
            return;
        }
        potentialTargets.sort(Comparator.comparingDouble(mc.player::distanceTo));
        bowTarget = potentialTargets.get(0);
    }

    private void aimAtBowTarget(LivingEntity target) {
        Vec3d playerPos = mc.player.getCameraPosVec(1.0F);
        Vec3d targetPos = dev.client.api.compat.EntityCompat.pos(target).add(0, target.getHeight() * 0.4, 0);

        double velocityX = target.getX() - target.lastX;
        double velocityY = target.getY() - target.lastY;
        double velocityZ = target.getZ() - target.lastZ;

        double gravity = 0.05;
        double initialVelocity = 3.0;

        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double flightTime = horizontalDistance / (initialVelocity * 0.8);

        Vec3d predictedPos = new Vec3d(
                targetPos.x + velocityX * flightTime * prediction.getValue(),
                targetPos.y + velocityY * Math.min(flightTime * 1.5, 0.5),
                targetPos.z + velocityZ * flightTime * prediction.getValue()
        );

        double predDeltaX = predictedPos.x - playerPos.x;
        double predDeltaY = predictedPos.y - playerPos.y;
        double predDeltaZ = predictedPos.z - playerPos.z;
        double predHorizontalDistance = Math.sqrt(predDeltaX * predDeltaX + predDeltaZ * predDeltaZ);

        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(predDeltaZ, predDeltaX)) - 90.0F);

        double optimalPitch = -Math.toDegrees(Math.atan(
                (initialVelocity * initialVelocity - Math.sqrt(
                        Math.pow(initialVelocity, 4) - gravity * (gravity * predHorizontalDistance * predHorizontalDistance + 2 * predDeltaY * initialVelocity * initialVelocity)
                )) / (gravity * predHorizontalDistance)
        ));

        if (Double.isNaN(optimalPitch) || Double.isInfinite(optimalPitch)) {
            float defaultPitch = (float) -Math.toDegrees(Math.atan2(predDeltaY, predHorizontalDistance));
            optimalPitch = defaultPitch;
        }

        RotationController controller = RotationController.INSTANCE;
        RotationConfig config = new RotationConfig(false, true);
        controller.rotateTo(new Angle(targetYaw, (float) optimalPitch), config, TaskPriority.HIGH_IMPORTANCE_1, this);
    }

    private void calculateTrajectory() {
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d oldHitPos = hitPos;
        hitPos = null;
        renderPos = null;
        willHit = false;
        target = null;
        hitSide = null;
        arrowHitPositions.clear();

        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();

        boolean isCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW;
        boolean isBow = mc.player.getMainHandStack().getItem() == Items.BOW;

        if (aim.isSelected("На Трезубец") && tridentTarget != null && tridentTarget.isAlive()) {
            hitPos = getPredictedTargetPos(tridentTarget, 2.5);
            renderPos = hitPos;
            willHit = true;
            target = tridentTarget;
            return;
        }

        if (aim.isSelected("На Лук") && bowTarget != null && bowTarget.isAlive()) {
            hitPos = getPredictedTargetPos(bowTarget, 3.0);
            renderPos = hitPos;
            willHit = true;
            target = bowTarget;
            return;
        }

        if (isCrossbow && CrossbowItem.isCharged(mc.player.getMainHandStack())) {
            calculateArrowTrajectory(pos, pitch, yaw, oldHitPos);
            calculateArrowTrajectory(pos, pitch, yaw - ARROW_SPREAD_ANGLE, oldHitPos);
            calculateArrowTrajectory(pos, pitch, yaw + ARROW_SPREAD_ANGLE, oldHitPos);
        } else if (isBow && mc.player.isUsingItem()) {
            calculateArrowTrajectory(pos, pitch, yaw, oldHitPos);
        } else {
            calculateSingleProjectileTrajectory(pos, pitch, yaw, oldHitPos);
        }

        if (renderPos == null) renderPos = (hitPos != null ? hitPos : pos);
    }

    private void calculateArrowTrajectory(Vec3d pos, float pitch, float yaw, Vec3d oldHitPos) {
        boolean isCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW;
        double speed;
        if (isCrossbow) {
            speed = CROSSBOW_VELOCITY;
        } else {
            int useTicks = mc.player.getItemUseTime();
            float pull = BowItem.getPullProgress(useTicks);
            speed = Math.min(3.0, pull * 3.0);
            if (speed <= 0.05) speed = 0.05;
        }

        Vec3d vel = initialVelocityFromYawPitch(yaw, pitch, speed);
        SimResult res = simulateProjectile(pos, vel, ARROW_GRAVITY, ARROW_DRAG_AIR, ARROW_DRAG_WATER, true, true, 0.0);

        arrowHitPositions.add(res.hitPos() != null ? res.hitPos() : pos);
        if (hitPos == null) {
            hitPos = (res.hitPos() != null ? res.hitPos() : pos);
            hitSide = res.side();
        }
        renderPos = hitPos;

        if (res.hitEntity()) {
            willHit = true;
            target = res.entity();
        }
    }

    private void calculateSingleProjectileTrajectory(Vec3d pos, float pitch, float yaw, Vec3d oldHitPos) {
        boolean isTrident = mc.player.getMainHandStack().getItem() == Items.TRIDENT || mc.player.getOffHandStack().getItem() == Items.TRIDENT;
        double speed, gravity, dragAir, dragWater;

        if (isTrident) {
            speed = TRIDENT_VELOCITY;
            gravity = TRIDENT_GRAVITY;
            dragAir = TRIDENT_DRAG_AIR;
            dragWater = TRIDENT_DRAG_WATER;
        } else if (mc.player.getMainHandStack().getItem() == Items.SPLASH_POTION) {
            speed = 0.5;
            gravity = ARROW_GRAVITY;
            dragAir = ARROW_DRAG_AIR;
            dragWater = ARROW_DRAG_WATER;
        } else if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
            speed = 1.5;
            gravity = ARROW_GRAVITY;
            dragAir = ARROW_DRAG_AIR;
            dragWater = ARROW_DRAG_WATER;
        } else {
            speed = 0.5;
            gravity = ARROW_GRAVITY;
            dragAir = ARROW_DRAG_AIR;
            dragWater = ARROW_DRAG_WATER;
        }

        Vec3d vel = initialVelocityFromYawPitch(yaw, pitch, speed);

        SimResult res = simulateProjectile(
                pos, vel,
                gravity, dragAir, dragWater,
                true, true,
                0.0
        );

        trajectoryPoints.clear();
        if (res.hitPos() != null) {
            hitPos = res.hitPos();
            hitSide = res.side();
            renderPos = hitPos;
        } else {
            hitPos = pos;
            renderPos = pos;
        }

        if (res.hitEntity()) {
            willHit = true;
            target = res.entity();
        }

        lastHitPos = oldHitPos;
    }

    private void renderTrajectory(MatrixStack matrices) {
        if (renderPos == null) return;

        double camX = mc.getEntityRenderDispatcher().camera.getCameraPos().x;
        double camY = mc.getEntityRenderDispatcher().camera.getCameraPos().y;
        double camZ = mc.getEntityRenderDispatcher().camera.getCameraPos().z;

        Basis basis = null;
        if (hitSide != null) {
            basis = surfaceBasisFromSide(hitSide);
        } else {
            float pitch = mc.player.getPitch() * 0.017453292F;
            float yaw = mc.player.getYaw() * 0.017453292F;
            double upX = -MathHelper.sin(yaw) * MathHelper.sin(pitch);
            double upY = MathHelper.cos(pitch);
            double upZ = MathHelper.cos(yaw) * MathHelper.sin(pitch);
            double rightX = -MathHelper.cos(yaw);
            double rightY = 0.0;
            double rightZ = -MathHelper.sin(yaw);
            basis = new Basis(new Vec3d(rightX, rightY, rightZ), new Vec3d(upX, upY, upZ), new Vec3d(0, 0, 0));
        }

        Color c = willHit ? HIT_COLOR : MISS_COLOR;
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue(), a = c.getAlpha();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0F);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        boolean isCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW && CrossbowItem.isCharged(mc.player.getMainHandStack());

        final double Z_EPS = 0.01;

        if (isCrossbow && !arrowHitPositions.isEmpty()) {
            for (Vec3d pt : arrowHitPositions) {
                Vec3d pAdj = adjustNearCamera(pt);
                if (hitSide != null) pAdj = pAdj.add(basis.nx * Z_EPS, basis.ny * Z_EPS, basis.nz * Z_EPS);
                drawCrosshairLines(buf, mat,
                        pAdj.x - camX, pAdj.y - camY, pAdj.z - camZ,
                        basis.rx, basis.ry, basis.rz,
                        basis.ux, basis.uy, basis.uz,
                        r, g, b, a);
            }
        } else {
            Vec3d pRaw = (renderPos != null ? renderPos : hitPos);
            Vec3d pAdj = adjustNearCamera(pRaw);
            if (hitSide != null) pAdj = pAdj.add(basis.nx * Z_EPS, basis.ny * Z_EPS, basis.nz * Z_EPS);
            drawCrosshairLines(buf, mat,
                    pAdj.x - camX, pAdj.y - camY, pAdj.z - camZ,
                    basis.rx, basis.ry, basis.rz,
                    basis.ux, basis.uy, basis.uz,
                    r, g, b, a);
        }

        RenderCompat.draw(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawCrosshairLines(BufferBuilder buf, Matrix4f mat, double px, double py, double pz, double rx, double ry, double rz, double ux, double uy, double uz, int r, int g, int b, int a) {
        final double radius = CROSSHAIR_SIZE;
        final int stepDeg = 10;

        double prevX = px + rx * radius;
        double prevY = py + ry * radius;
        double prevZ = pz + rz * radius;

        for (int deg = stepDeg; deg <= 360; deg += stepDeg) {
            double rad = Math.toRadians(deg);
            double c = Math.cos(rad) * radius;
            double s = Math.sin(rad) * radius;

            double x = px + rx * c + ux * s;
            double y = py + ry * c + uy * s;
            double z = pz + rz * c + uz * s;

            line(buf, mat, prevX, prevY, prevZ, x, y, z, r, g, b, a);
            prevX = x;
            prevY = y;
            prevZ = z;
        }

        line(buf, mat, px + ux * radius, py + uy * radius, pz + uz * radius, px - ux * radius, py - uy * radius, pz - uz * radius, r, g, b, a);
        line(buf, mat, px + rx * radius, py + ry * radius, pz + rz * radius, px - rx * radius, py - ry * radius, pz - rz * radius, r, g, b, a);
    }

    private Vec3d getPredictedTargetPos(LivingEntity target, double velocity) {
        Vec3d targetPos = dev.client.api.compat.EntityCompat.pos(target).add(0, target.getHeight() * 0.4, 0);

        double velocityX = target.getX() - target.lastX;
        double velocityY = target.getY() - target.lastY;
        double velocityZ = target.getZ() - target.lastZ;

        Vec3d playerPos = mc.player.getCameraPosVec(1.0F);
        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double flightTime = horizontalDistance / (velocity * 0.8);

        return new Vec3d(
                targetPos.x + velocityX * flightTime * prediction.getValue(),
                targetPos.y + velocityY * Math.min(flightTime * 1.5, 0.5),
                targetPos.z + velocityZ * flightTime * prediction.getValue()
        );
    }

    private SimResult simulateProjectile(Vec3d startPos, Vec3d initialVel, double gravity, double dragAir, double dragWater, boolean entityCollisions, boolean waterAffects, double divergence) {
        Vec3d pos = startPos;
        Vec3d vel = initialVel;

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            Vec3d next = pos.add(vel);

            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos, next,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.ANY,
                    mc.player
            ));

            EntityHitResult entityHitRes = null;
            if (entityCollisions) {
                Box travelBox = new Box(pos, next).expand(0.3);
                entityHitRes = ProjectileUtil.getEntityCollision(mc.world, mc.player, pos, next, travelBox, e -> e instanceof LivingEntity && e != mc.player && isFilter((LivingEntity) e), 0.3f);
            }

            if (entityHitRes != null && entityHitRes.getPos().squaredDistanceTo(pos) <=
                    (blockHit != null ? blockHit.getPos().squaredDistanceTo(pos) : Double.MAX_VALUE)) {
                return new SimResult(entityHitRes.getPos(), null, true, entityHitRes.getEntity());
            }

            if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
                return new SimResult(blockHit.getPos(), ((BlockHitResult) blockHit).getSide(), false, null);
            }

            pos = next;
            vel = new Vec3d(vel.x, vel.y - gravity, vel.z);

            boolean inWater = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            double drag = (waterAffects && inWater) ? dragWater : dragAir;
            vel = vel.multiply(drag);

            if (divergence > 0.0 && tick == 0) {
            }

            if (pos.y < mc.world.getBottomY() - 8 || pos.squaredDistanceTo(startPos) > 300 * 300) {
                break;
            }
        }
        return new SimResult(pos, null, false, null);
    }

    public boolean isFilter(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive() || entity.isInvulnerable() || entity.getHealth() <= 0.0f) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (wallsCheck.getEnabled() && !mc.player.canSee(entity)) return false;

        if (entity instanceof PlayerEntity player) {
            if (AntiBot.isBot(player)) return false;
            if (!filter.get("Игроков").getEnabled()) return false;
            if (handlerClient().getFriendManager().isFriend(player.getName().getString()) && !filter.get("Друзей").getEnabled())
                return false;

            boolean hasArmor = false;
            for (ItemStack armorPiece : dev.client.api.compat.InventoryCompat.armorItems(player)) {
                if (!armorPiece.isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }

            boolean isInvisible = player.isInvisible();

            if (isInvisible && !filter.get("Невидимых").getEnabled()) return false;
            if (!hasArmor && !filter.get("Голых").getEnabled()) return false;
            if (isInvisible && !hasArmor) {
                return filter.get("Голых").getEnabled() && filter.get("Невидимых").getEnabled();
            }
        }

        return true;
    }

    private static Vec3d initialVelocityFromYawPitch(float yawDeg, float pitchDeg, double speed) {
        float yaw = yawDeg * 0.017453292F;
        float pitch = pitchDeg * 0.017453292F;

        double x = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double y = -MathHelper.sin(pitch);
        double z = MathHelper.cos(yaw) * MathHelper.cos(pitch);

        double len = Math.sqrt(x * x + y * y + z * z);
        if (len != 0.0) {
            x = x / len * speed;
            y = y / len * speed;
            z = z / len * speed;
        }
        return new Vec3d(x, y, z);
    }

    private Basis surfaceBasisFromSide(Direction side) {
        Vec3i v = side.getVector();
        Vec3d n = new Vec3d(v.getX(), v.getY(), v.getZ()).normalize();
        Vec3d ref = Math.abs(n.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d tangent = n.crossProduct(ref);

        if (tangent.lengthSquared() < 1e-6) tangent = new Vec3d(0, 0, 1);
        tangent = tangent.normalize();
        Vec3d bitangent = n.crossProduct(tangent).normalize();

        return new Basis(tangent, bitangent, n);
    }

    private Vec3d adjustNearCamera(Vec3d p) {
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();
        if (p.squaredDistanceTo(cam) < 0.25) {
            Vec3d dir = mc.player.getRotationVec(1.0F).normalize();
            return cam.add(dir.multiply(0.5));
        }
        return p;
    }

    private static void line(BufferBuilder buf, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2, int r, int g, int b, int a) {
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    private boolean hasFilteredTargetInWorld() {
        if (mc == null || mc.world == null) return false;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity le && isFilter(le)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRenderCrosshairNow() {
        if (mc.player == null || !crosshair.getEnabled()) return false;
        if (checkFilter.getEnabled()) return true;
        return hasFilteredTargetInWorld();
    }

    private record SimResult(Vec3d hitPos, Direction side, boolean hitEntity, Entity entity) {
    }

    private static class Basis {
        final double rx, ry, rz;
        final double ux, uy, uz;
        final double nx, ny, nz;

        Basis(Vec3d r, Vec3d u, Vec3d n) {
            this.rx = r.x;
            this.ry = r.y;
            this.rz = r.z;
            this.ux = u.x;
            this.uy = u.y;
            this.uz = u.z;
            this.nx = n.x;
            this.ny = n.y;
            this.nz = n.z;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        wasCharged = false;
        waitingForCharge = false;
        lastTarget = null;
        tridentTarget = null;
        wasDrawn = false;
        tridentDrawStartTime = 0;
    }
}
