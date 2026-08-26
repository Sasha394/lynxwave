package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.world.ItemUseHelper;
import dev.client.api.nullcry.helper.entity.world.SlotHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.AngleUtil;
import dev.client.modules.core.combat.killaura.rotation.RotationConfig;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.other.task.TaskPriority;
import dev.other.task.scripts.Script;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;
import java.util.stream.StreamSupport;

public class TargetPearl extends Module {
    public static TargetPearl INSTANCE;

    private final ModeElement activationMode = new ModeElement("Режим", () -> true).set("Авто", "По кнопке").applyDefault("Авто").register(this);
    private final ModeElement targetMode = new ModeElement("Цели", () -> true).set("Цель киллауры", "Все").applyDefault("Цель киллауры").register(this);

    private final KeyBind throwKey = new KeyBind("Клавиша броска", () -> activationMode.isSelected("По кнопке")).set(-1).register(this);
    private final Slider autoDelay = new Slider("Задержка авто", () -> activationMode.isSelected("Авто")).set(100.0f, 1500.0f, 50.0f).applyDefault(500.0f).register(this);
    private final Slider triggerDistance = new Slider("Дистанция", () -> true).set(3.0f, 15.0f, 0.5f).applyDefault(6.0f).register(this);
    private final CheckBox legitUse = new CheckBox("Легитно выкидывать жемчуг", () -> true).defaultValue(false).register(this);

    private final TimerUtil cooldown = new TimerUtil();
    private final Script script = new Script();

    private final Set<Integer> ignoredPearls = new HashSet<>();
    private final Map<Integer, Long> detectionTimes = new HashMap<>();

    private LivingEntity lastAuraTarget;
    @Getter private boolean throwing;
    boolean throwRequested;

    public TargetPearl() {
        super("TargetPearl", ModuleCategory.Movement, "Автоматически кидает эндер-жемчуг за целью ауры или последней близкой целью (игнорит друзей)");
    }

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (!isEnabled()) return;
        if (!activationMode.isSelected("По кнопке")) return;
        if (throwKey.getKey() == null) return;
        if (event.isKeyDown(throwKey.getKey())) {
            throwRequested = true;
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        script.update();

        updateAuraTarget();
        assignOwners();
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!isEnabled()) return;
        if (throwing) return;

        if (!cooldown.isReached(1000)) return;
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) return;
        if (!hasPearl()) return;

        if (hasOwnPearl()) {
            cooldown.reset();
            throwRequested = false;
            return;
        }

        if (activationMode.isSelected("По кнопке") && !throwRequested) {
            return;
        }

        long now = System.currentTimeMillis();
        Optional<PearlTarget> optionalTarget = findPearlTarget(now);
        if (optionalTarget.isEmpty()) {
            throwRequested = false;
            return;
        }

        PearlTarget target = optionalTarget.get();
        Vec3d targetPos = target.landing().getPos();
        if (dev.client.api.compat.EntityCompat.pos(mc.player).distanceTo(targetPos) <= triggerDistance.getValue()) {
            throwRequested = false;
            return;
        }

        Angle yawAngle = AngleUtil.fromVec3d(targetPos.subtract(mc.player.getEyePos()));
        Angle bestAngle = findLaunchAngle(yawAngle.getYaw(), targetPos);
        if (bestAngle == null) {
            throwRequested = false;
            return;
        }

        RotationController controller = RotationController.INSTANCE;
        RotationConfig config = new RotationConfig(false, true);
        controller.rotateTo(bestAngle, config, TaskPriority.HIGH_IMPORTANCE_1, this);

        throwing = true;
        cooldown.reset();
        ignoredPearls.add(target.pearl().getId());
        throwRequested = false;

        script.cleanup()
                .addTickStep(0, () -> {
                    int hbSlot = SlotHelper.getItemInventoryOrHotBar(Items.ENDER_PEARL, true);
                    int invSlot = SlotHelper.getItemInventoryOrHotBar(Items.ENDER_PEARL, false);
                    int previousSelected = mc.player.getInventory().getSelectedSlot();
                    boolean hwLegit = isHW() && legitUse.getEnabled();

                    Runnable useAction = () -> {
                        if (!isEnabled()) {
                            throwing = false;
                            return;
                        }

                        int usedSlot = hwLegit
                                ? ItemUseHelper.spoofUseItemHwLegit(previousSelected, hbSlot, invSlot, Hand.MAIN_HAND, false)
                                : ItemUseHelper.spoofUseItem(previousSelected, hbSlot, invSlot, Hand.MAIN_HAND, false, legitUse.getEnabled());
                        if (usedSlot == -1) {
                            throwing = false;
                        }
                    };

                    useAction.run();
                })
                .addTickStep(1, () -> throwing = false);
    }

    private void updateAuraTarget() {
        KillAura aura = KillAura.INSTANCE;
        if (aura == null) return;
        LivingEntity current = aura.getTarget();
        if (current != null && current.isAlive()) {
            lastAuraTarget = current;
        } else if (lastAuraTarget != null && (!lastAuraTarget.isAlive() || lastAuraTarget.distanceTo(mc.player) > 32)) {
            lastAuraTarget = null;
        }
    }

    private void assignOwners() {
        Set<Integer> present = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            present.add(pearl.getId());
            detectionTimes.putIfAbsent(pearl.getId(), now);
            if (ignoredPearls.contains(pearl.getId())) continue;
            if (pearl.getOwner() != null) continue;
            PlayerEntity nearest = findNearestPlayer(pearl, 3.0);
            if (nearest != null) {
                pearl.setOwner(nearest);
            }
        }
        ignoredPearls.retainAll(present);
        detectionTimes.keySet().retainAll(present);
    }

    private Optional<PearlTarget> findPearlTarget(long now) {
        Vec3d eyePos = mc.player.getEyePos();
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(EnderPearlEntity.class::isInstance)
                .map(EnderPearlEntity.class::cast)
                .filter(pearl -> !ignoredPearls.contains(pearl.getId()))
                .map(pearl -> createTarget(pearl, eyePos, now))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(PearlTarget::rotationDiff));
    }

    private Optional<PearlTarget> createTarget(EnderPearlEntity pearl, Vec3d eyePos, long now) {
        Entity owner = pearl.getOwner();
        if (!(owner instanceof LivingEntity living)) {
            return Optional.empty();
        }
        if (owner == mc.player) {
            return Optional.empty();
        }
        long firstSeen = detectionTimes.computeIfAbsent(pearl.getId(), id -> now);
        if (activationMode.isSelected("Авто") && now - firstSeen < autoDelay.getValue().longValue()) {
            return Optional.empty();
        }
        if (owner instanceof PlayerEntity player && handlerClient().getFriendManager().isFriend(player.getName().getString())) {
            return Optional.empty();
        }
        if (!targetMode.isSelected("Все")) {
            if (lastAuraTarget == null || !owner.equals(lastAuraTarget)) {
                return Optional.empty();
            }
        }

        HitResult landing = calcTrajectory(pearl);
        if (landing == null) {
            return Optional.empty();
        }

        Angle angle = AngleUtil.fromVec3d(landing.getPos().subtract(eyePos));
        double diff = rotationDifference(angle);
        return Optional.of(new PearlTarget(pearl, living, landing, diff));
    }

    private Angle findLaunchAngle(float yaw, Vec3d targetPos) {
        ItemStack pearlStack = findPearlStack();
        if (pearlStack.isEmpty()) return null;

        Angle best = null;
        double bestPitch = Double.NEGATIVE_INFINITY;

        for (int pitch = -89; pitch <= 89; pitch++) {
            Angle angle = new Angle(yaw, pitch);
            Vec3d direction = toDirection(angle);
            HitResult result = checkTrajectory(direction, new EnderPearlEntity(mc.world, mc.player, pearlStack.copy()), 1.5);
            if (result == null) continue;
            if (result.getPos().distanceTo(targetPos) > 3.0) continue;
            if (angle.getPitch() > bestPitch) {
                bestPitch = angle.getPitch();
                best = angle;
            }
        }
        return best;
    }

    private HitResult calcTrajectory(EnderPearlEntity pearl) {
        EnderPearlEntity copy = new EnderPearlEntity(mc.world, pearl.getOwner() instanceof LivingEntity living ? living : mc.player, pearl.getStack().copy());
        copy.setPosition(pearl.getX(), pearl.getY(), pearl.getZ());
        copy.setVelocity(pearl.getVelocity());
        return traceTrajectory(dev.client.api.compat.EntityCompat.pos(copy), copy.getVelocity(), copy);
    }

    private HitResult checkTrajectory(Vec3d lookVec, ProjectileEntity entity, double velocity) {
        if (mc.player == null) return null;
        float length = MathHelper.sqrt((float) lookVec.lengthSquared());
        if (length == 0) return null;

        Vec3d normalized = lookVec.multiply(1.0 / length);
        Vec3d motion = mc.player.getVelocity();
        Vec3d start = mc.player.getEyePos();
        entity.setOwner(mc.player);
        entity.setPosition(start.x, start.y, start.z);
        return traceTrajectory(start, normalized.multiply(velocity).add(motion), entity);
    }

    private HitResult traceTrajectory(Vec3d pos, Vec3d motion, ProjectileEntity entity) {
        Vec3d currentPos = pos;
        Vec3d velocity = motion;

        for (int i = 0; i < 300; i++) {
            Vec3d prevPos = currentPos;
            Vec3d nextPos = currentPos.add(velocity);

            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    prevPos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity));
            if (blockHit.getType() != HitResult.Type.MISS) {
                return blockHit;
            }

            Box searchBox = new Box(prevPos, prevPos).stretch(velocity).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.getEntityCollision(mc.world, entity, prevPos, nextPos, searchBox,
                    candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != entity.getOwner());
            if (entityHit != null) {
                return entityHit;
            }

            currentPos = nextPos;
            velocity = calculateMotion(entity, prevPos, velocity);
            if (currentPos.y < -128) break;
        }
        return null;
    }

    private Vec3d calculateMotion(ProjectileEntity entity, Vec3d prevPos, Vec3d motion) {
        boolean inWater = mc.world.getFluidState(BlockPos.ofFloored(prevPos)).isIn(FluidTags.WATER);
        double drag = inWater ? 0.8 : 0.99;
        return motion.multiply(drag).add(0, -entity.getFinalGravity(), 0);
    }

    private Vec3d toDirection(Angle angle) {
        float yawRad = (float) Math.toRadians(angle.getYaw());
        float pitchRad = (float) Math.toRadians(angle.getPitch());
        float cosPitch = MathHelper.cos(pitchRad);
        float x = -MathHelper.sin(yawRad) * cosPitch;
        float y = -MathHelper.sin(pitchRad);
        float z = MathHelper.cos(yawRad) * cosPitch;
        return new Vec3d(x, y, z);
    }

    private ItemStack findPearlStack() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENDER_PEARL)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean hasPearl() {
        return mc.player.getInventory().count(Items.ENDER_PEARL) > 0;
    }

    private boolean hasOwnPearl() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity pearl && pearl.getOwner() == mc.player) {
                return true;
            }
        }
        return false;
    }

    private PlayerEntity findNearestPlayer(EnderPearlEntity pearl, double range) {
        PlayerEntity nearest = null;
        double maxSq = range * range;
        for (PlayerEntity player : mc.world.getPlayers()) {
            double dist = player.squaredDistanceTo(pearl);
            if (dist <= maxSq && (nearest == null || dist < nearest.squaredDistanceTo(pearl))) {
                nearest = player;
            }
        }
        return nearest;
    }

    private double rotationDifference(Angle angle) {
        RotationController controller = RotationController.INSTANCE;
        Angle current = controller.getRotation();
        float yawDiff = MathHelper.abs(MathHelper.wrapDegrees(angle.getYaw() - current.getYaw()));
        float pitchDiff = MathHelper.abs(MathHelper.wrapDegrees(angle.getPitch() - current.getPitch()));
        return Math.hypot(yawDiff, pitchDiff);
    }

    @Override
    public void onDisabled() {
        throwing = false;
        throwRequested = false;
        ignoredPearls.clear();
        detectionTimes.clear();
        script.cleanup();
        super.onDisabled();
    }

    private record PearlTarget(EnderPearlEntity pearl, LivingEntity owner, HitResult landing, double rotationDiff) {}
}
