package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.RotationUpdateEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.events.types.EventType;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.entity.world.ItemUseHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.RotationConfig;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.combat.killaura.rotation.angle.AngleSmoothMode;
import dev.other.task.TaskPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class AutoPilot extends Module {
    public static AutoPilot INSTANCE;

    private final CheckBox swapOnArrive = new CheckBox("Свапать при долете", () -> true).defaultValue(true).register(this);
    private final CheckBox legitUse = new CheckBox("Использовать легитно", () -> true).defaultValue(false).register(this);

    private Vec3d targetPos;
    private boolean inputsCaptured = false;
    private boolean prevForward;
    private boolean prevSprint;
    private int swappedElytraSlot = -1;
    private boolean takingOff = false;
    private Float takeoffYaw = null;
    private int takeoffTicks = 0;
    private double lastHorizontal = Double.NaN;

    private Angle rotationTarget;
    private float rotationYawStep = 8f;
    private float rotationPitchStep = 6f;

    private double avoidanceY = Double.NaN;
    private int avoidanceTicks = 0;

    private final TimerUtil rocketTimer = new TimerUtil();
    private final TimerUtil failMessageTimer = new TimerUtil();

    public AutoPilot() {
        super("AutoPilot", ModuleCategory.Player, "Полет на элитрах к заданным координатам.");
    }

    public boolean hasTarget() {
        return targetPos != null;
    }

    public Vec3d getTargetPos() {
        return targetPos;
    }

    public void setTarget(int x, int z) {
        int y = resolveGroundY(x, z);
        setTarget(x, y, z);
    }

    public void setTarget(int x, int y, int z) {
        this.targetPos = new Vec3d(x + 0.5, y, z + 0.5);
    }

    public void clearTarget() {
        this.targetPos = null;
        stopMovement();
        takingOff = false;
        takeoffYaw = null;
        takeoffTicks = 0;
        rotationTarget = null;
        avoidanceY = Double.NaN;
        avoidanceTicks = 0;
        lastHorizontal = Double.NaN;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        rotationTarget = null;
        if (mc.player == null || mc.world == null) {
            clearTarget();
            return;
        }

        if (!hasRequiredItems()) {
            if (failMessageTimer.hasTimeElapsed(1000L, true)) {
                printClient("Для AutoPilot нужны элитры и фейерверки.");
            }
            stopMovement();
            setEnabled(false);
            return;
        }

        if (targetPos == null) {
            stopMovement();
            return;
        }

        if (!ensureElytraEquipped()) {
            printClient("Элитры не найдены.");
            stopMovement();
            setEnabled(false);
            return;
        }

        handleTakeoff();
        if (!mc.player.isGliding()) {
            stopMovement();
            return;
        }

        moveToTarget();
    }

    @Subscribe
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            if (rotationTarget == null) {
                return;
            }
            RotationController.INSTANCE.rotateTo(rotationTarget, getRotationConfig(), TaskPriority.HIGH_IMPORTANCE_1, this);
            return;
        }
        if (event.getType() == EventType.POST) {
            if (rotationTarget == null) {
                return;
            }
            applyClientRotation(RotationController.INSTANCE.getPreviousRotation(), RotationController.INSTANCE.getRotation());
        }
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!isEnabled() || mc.player == null || rotationTarget == null) {
            return;
        }
        float delta = event.getTickCounter().getTickProgress(false);
        Angle prev = RotationController.INSTANCE.getPreviousRotation();
        Angle cur = RotationController.INSTANCE.getRotation();
        float yaw = MathHelper.lerpAngleDegrees(delta, prev.getYaw(), cur.getYaw());
        float pitch = MathHelper.lerp(delta, prev.getPitch(), cur.getPitch());
        applyClientRotation(new Angle(yaw, pitch));
    }

    private boolean hasRequiredItems() {
        if (mc.player == null) {
            return false;
        }
        boolean hasElytra = hasElytraInInventory() || isElytraEquipped();
        boolean hasFirework = hasFirework();
        return hasElytra && hasFirework;
    }

    private boolean hasFirework() {
        if (mc.player == null) {
            return false;
        }
        return InventoryHelper.getItem(Items.FIREWORK_ROCKET, true) != -1
                || InventoryHelper.getItem(Items.FIREWORK_ROCKET, false) != -1;
    }

    private boolean isElytraEquipped() {
        if (mc.player == null) {
            return false;
        }
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean hasElytraInInventory() {
        if (mc.player == null) {
            return false;
        }
        return getItemSlot(Items.ELYTRA) != -1;
    }

    private boolean ensureElytraEquipped() {
        if (mc.player == null) {
            return false;
        }
        if (isElytraEquipped()) {
            return true;
        }

        int elytraSlot = getItemSlot(Items.ELYTRA);
        if (elytraSlot == -1) {
            return false;
        }

        if (swappedElytraSlot == -1) {
            swappedElytraSlot = elytraSlot;
        }

        InventoryHelper.moveItem(elytraSlot, 6);
        return true;
    }

    private void handleTakeoff() {
        if (mc.player.isGliding() || mc.player.abilities.flying) {
            takingOff = false;
            takeoffYaw = null;
            takeoffTicks = 0;
            return;
        }

        if (!isUsableElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            printClient("Элитры сломаны.");
            setEnabled(false);
            return;
        }

        boolean onGround = mc.player.isOnGround();
        if (onGround) {
            float desiredYaw = resolveTakeoffYaw();
            if (Float.isNaN(desiredYaw)) {
                return;
            }
            takeoffYaw = desiredYaw;
            takeoffTicks = 0;
            setRotationTarget(desiredYaw, -35f, 10f, 8f);

            mc.player.setSprinting(true);
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            mc.player.jump();
            takingOff = true;
        } else if (takingOff) {
            takeoffTicks++;
            float yaw = takeoffYaw != null ? takeoffYaw : mc.player.getYaw();
            float pitch = takeoffTicks < 2 ? -35f : -25f;
            setRotationTarget(yaw, pitch, 10f, 8f);

            if (mc.player.getVelocity().y < 0 || takeoffTicks > 3) {
                mc.player.startGliding();
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                takingOff = false;
            }
        }
    }

    private void moveToTarget() {
        Vec3d pos = dev.client.api.compat.EntityCompat.pos(mc.player);
        Vec3d diff = targetPos.subtract(pos);
        double horizontal = Math.hypot(diff.x, diff.z);
        double distance = diff.length();

        boolean arrived = horizontal <= 3.0;
        if (!arrived && !Double.isNaN(lastHorizontal) && lastHorizontal < 8.0 && horizontal > lastHorizontal) {
            arrived = true;
        }
        if (arrived) {
            clearTarget();
            printClient("AutoPilot достиг точки.");
            if (swapOnArrive.getEnabled()) {
                restoreChestplate();
                setEnabled(false);
            }
            return;
        }
        lastHorizontal = horizontal;

        Vec3d horizontalDir = new Vec3d(diff.x, 0, diff.z);
        if (horizontalDir.lengthSquared() > 1.0E-4) {
            updateObstacleAvoidance(horizontalDir, horizontal);
        } else {
            updateObstacleAvoidance(Vec3d.fromPolar(0, mc.player.getYaw()), horizontal);
        }

        double targetY = targetPos.y;
        double cruiseY = computeCruiseAltitude(pos.y, targetY, horizontal);

        Angle baseAngle = AngleUtil.fromVec3d(diff);
        float yaw = baseAngle.getYaw();
        float pitch = computePitch(diff, horizontal, cruiseY, pos.y, distance);
        double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        updateRotationSteps(distance, speed);
        setRotationTarget(yaw, pitch, rotationYawStep, rotationPitchStep);

        captureInputs();
        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);

        if (shouldUseRocket(distance, horizontal, pos.y, cruiseY)) {
            useFirework();
        }
    }

    private double computeCruiseAltitude(double currentY, double targetY, double horizontal) {
        double base;
        if (horizontal < 35.0) {
            base = targetY + 6.0;
        } else if (horizontal < 60.0) {
            base = Math.max(currentY + 8.0, targetY + 8.0);
        } else {
            base = Math.max(currentY, targetY) + 18.0;
        }
        if (horizontal > 200) {
            base = Math.max(base, 140.0);
        } else if (horizontal > 120) {
            base = Math.max(base, 110.0);
        } else if (horizontal > 60) {
            base = Math.max(base, 90.0);
        }

        if (!Double.isNaN(avoidanceY)) {
            base = Math.max(base, avoidanceY);
        }

        double minTarget = horizontal < 18 ? targetY + 3.0 : targetY + 6.0;
        return Math.max(base, minTarget);
    }

    private float computePitch(Vec3d diff, double horizontal, double cruiseY, double currentY, double distance) {
        float targetPitch = (float) (-(Math.atan2(diff.y, Math.max(0.001, horizontal)) * 180.0 / Math.PI));
        float pitch = MathHelper.clamp(targetPitch, -30f, 30f);

        double climbDelta = cruiseY - currentY;
        if (climbDelta > 14) {
            pitch = -30f;
        } else if (climbDelta > 6) {
            pitch = Math.min(pitch, -18f);
        } else if (currentY > cruiseY + 12) {
            pitch = 20f;
        }

        if (distance < 40 && Math.abs(climbDelta) < 8) {
            pitch = MathHelper.clamp(pitch, -25f, 25f);
        }

        return pitch;
    }

    private boolean shouldUseRocket(double distance, double horizontal, double currentY, double cruiseY) {
        if (!hasFirework()) {
            return false;
        }
        if (horizontal < 25) {
            return false;
        }
        if (distance < 70) {
            return false;
        }
        double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        boolean belowCruise = currentY < cruiseY - 8;
        boolean veryLow = currentY < cruiseY - 18;
        boolean slow = speed < 0.85;
        if (!(slow || veryLow)) {
            return false;
        }
        long delay = belowCruise ? 1500L : 1900L;
        return rocketTimer.hasTimeElapsed(delay, true);
    }

    private void updateRotationSteps(double distance, double speed) {
        float baseYaw = 6f;
        float distanceBoost = (float) MathHelper.clamp(distance / 40.0, 0.0, 6.0);
        float speedBoost = (float) MathHelper.clamp(speed * 4.0, 0.0, 4.0);
        rotationYawStep = MathHelper.clamp(baseYaw + distanceBoost + speedBoost, 4f, 16f);

        float basePitch = 4f;
        float pitchBoost = (float) MathHelper.clamp(distance / 60.0, 0.0, 4.0);
        rotationPitchStep = MathHelper.clamp(basePitch + pitchBoost, 3f, 12f);
    }

    private void setRotationTarget(float yaw, float pitch, float yawStep, float pitchStep) {
        rotationTarget = new Angle(yaw, pitch);
        rotationYawStep = yawStep;
        rotationPitchStep = pitchStep;
    }

    private RotationConfig getRotationConfig() {
        return new RotationConfig(new AutoPilotSmoothMode(rotationYawStep, rotationPitchStep), true, true);
    }

    private void updateObstacleAvoidance(Vec3d direction, double horizontal) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (horizontal < 20.0) {
            avoidanceTicks = 0;
            avoidanceY = Double.NaN;
            return;
        }
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }

        double range = Math.min(24.0, Math.max(6.0, horizontal * 0.4));
        double obstacle = findObstacleHeight(direction.normalize(), range);
        if (!Double.isNaN(obstacle)) {
            double desired = obstacle + 8.0;
            avoidanceY = Double.isNaN(avoidanceY) ? desired : Math.max(avoidanceY, desired);
            avoidanceTicks = 25;
            return;
        }

        if (avoidanceTicks > 0) {
            avoidanceTicks--;
        } else {
            avoidanceY = Double.NaN;
        }
    }

    private double findObstacleHeight(Vec3d direction, double range) {
        Vec3d start = mc.player.getEyePos();
        Vec3d end = start.add(direction.multiply(range));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return Double.NaN;
        }
        return hit.getBlockPos().getY() + 1.0;
    }

    private void useFirework() {
        int hbSlot = InventoryHelper.getItem(Items.FIREWORK_ROCKET, true);
        int invSlot = InventoryHelper.getItem(Items.FIREWORK_ROCKET, false);

        if (hbSlot == -1 && invSlot == -1) {
            return;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean hwLegit = isHW() && legitUse.getEnabled();

        if (hwLegit) {
            ItemUseHelper.spoofUseItemHwLegit(previousSlot, hbSlot, invSlot, Hand.MAIN_HAND, true);
        } else {
            ItemUseHelper.spoofUseItem(previousSlot, hbSlot, invSlot, Hand.MAIN_HAND, true, legitUse.getEnabled());
        }
    }

    private void restoreChestplate() {
        if (mc.player == null) {
            return;
        }
        if (swappedElytraSlot != -1) {
            InventoryHelper.moveItem(swappedElytraSlot, 6);
            swappedElytraSlot = -1;
            return;
        }

        int chestplateSlot = getChestplateSlot();
        if (chestplateSlot != -1) {
            InventoryHelper.moveItem(chestplateSlot, 6);
            return;
        }

        int emptySlot = findEmptyInventorySlot();
        if (emptySlot != -1) {
            int containerSlot = toContainerIndex(emptySlot);
            InventoryHelper.moveItem(6, containerSlot);
        }
    }

    private void captureInputs() {
        if (inputsCaptured) return;
        prevForward = mc.options.forwardKey.isPressed();
        prevSprint = mc.options.sprintKey.isPressed();
        inputsCaptured = true;
    }

    private void stopMovement() {
        if (!inputsCaptured) return;
        mc.options.forwardKey.setPressed(prevForward);
        mc.options.sprintKey.setPressed(prevSprint);
        inputsCaptured = false;
    }

    private int getItemSlot(Item input) {
        if (mc.player == null) {
            return -1;
        }
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == input) {
                slot = i;
                break;
            }
        }
        if (slot < 9 && slot != -1) {
            slot = slot + 36;
        }
        return slot;
    }

    private boolean isUsableElytra(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER) && !stack.willBreakNextUse();
    }

    private float resolveTakeoffYaw() {
        float baseYaw = mc.player.getYaw();
        if (targetPos != null) {
            Vec3d diff = targetPos.subtract(dev.client.api.compat.EntityCompat.pos(mc.player));
            baseYaw = (float) (Math.atan2(diff.z, diff.x) * 180.0 / Math.PI - 90.0);
        }

        float[] offsets = new float[]{0f, 30f, -30f, 60f, -60f, 90f, -90f, 180f};
        for (float offset : offsets) {
            float yaw = baseYaw + offset;
            if (isTakeoffPathClear(yaw)) {
                return yaw;
            }
        }
        return Float.NaN;
    }

    private boolean isTakeoffPathClear(float yaw) {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        Vec3d dir = Vec3d.fromPolar(0, yaw).normalize();
        Vec3d start = mc.player.getEyePos();
        Vec3d end = start.add(dir.multiply(6.0));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return false;
        }

        BlockPos head = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 1.8, mc.player.getZ());
        return mc.world.getBlockState(head).isAir() && mc.world.getBlockState(head.up()).isAir();
    }

    private int getChestplateSlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (PlayerUtil.isArmorForSlot(stack, EquipmentSlot.CHEST)) {
                int slot = i;
                if (i < 9) {
                    slot += 36;
                }
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int toContainerIndex(int idx) {
        return idx >= 0 && idx <= 8 ? 36 + idx : idx;
    }

    private int resolveGroundY(int x, int z) {
        if (mc.world != null) {
            BlockPos top = mc.world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z));
            if (top != null) {
                return top.getY();
            }
        }
        return mc.player != null ? (int) mc.player.getY() : 0;
    }

    private void applyClientRotation(Angle angle) {
        if (mc.player == null || angle == null) {
            return;
        }
        mc.player.setYaw(angle.getYaw());
        mc.player.setPitch(angle.getPitch());
        mc.player.headYaw = angle.getYaw();
        mc.player.bodyYaw = angle.getYaw();
    }

    private void applyClientRotation(Angle previous, Angle current) {
        if (mc.player == null || current == null) {
            return;
        }
        if (previous != null) {
            mc.player.lastYaw = previous.getYaw();
            mc.player.lastPitch = previous.getPitch();
            mc.player.lastHeadYaw = previous.getYaw();
            mc.player.lastBodyYaw = previous.getYaw();
        }
        applyClientRotation(current);
    }

    private static final class AutoPilotSmoothMode extends AngleSmoothMode {
        private final float maxYaw;
        private final float maxPitch;

        private AutoPilotSmoothMode(float maxYaw, float maxPitch) {
            super("AutoPilot");
            this.maxYaw = maxYaw;
            this.maxPitch = maxPitch;
        }

        @Override
        public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
            Angle delta = AngleUtil.calculateDelta(currentAngle, targetAngle);
            float yaw = MathHelper.clamp(delta.getYaw(), -maxYaw, maxYaw);
            float pitch = MathHelper.clamp(delta.getPitch(), -maxPitch, maxPitch);
            return new Angle(currentAngle.getYaw() + yaw, MathHelper.clamp(currentAngle.getPitch() + pitch, -89f, 89f));
        }

        @Override
        public Vec3d randomValue() {
            return Vec3d.ZERO;
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!hasRequiredItems()) {
            printClient("Нужны элитры и фейерверки.");
            setEnabled(false);
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        clearTarget();
        if (swapOnArrive.getEnabled() && mc.player != null) {
            restoreChestplate();
        }
    }
}
