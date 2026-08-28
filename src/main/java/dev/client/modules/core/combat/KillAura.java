package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.game.GameUpdateEvent;
import dev.client.api.nullcry.events.core.game.RotationUpdateEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.player.ChangeSprintEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.server.ServerUtils;
import dev.client.api.nullcry.helper.entity.SimulatedPlayer;
import dev.client.api.nullcry.helper.entity.world.HandSwapHelper;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Collection;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.api.nullcry.render.core.Draw3DUtil;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import dev.client.modules.core.combat.killaura.elytra.BestPoint;
import dev.client.modules.core.combat.killaura.elytra.PredictUtil;
import dev.client.modules.core.combat.killaura.rotation.*;
import dev.client.modules.core.combat.killaura.rotation.angle.*;
import dev.client.modules.core.combat.killaura.target.AuraUtil;
import dev.client.modules.core.movement.AirStuck;
import dev.client.modules.core.movement.FreeCamera;
import dev.client.modules.core.render.Interface;
import dev.other.task.TaskPriority;
import dev.other.vector.Vec2i;
import lombok.Getter;
import lombok.experimental.NonFinal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.Window;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

// атвей привет
public class KillAura extends Module {
    public static KillAura INSTANCE;

    public KillAura() {
        super("KillAura", ModuleCategory.Combat, "пиздит голую мамашу или монашку");
        INSTANCE = this;
        setupCollections();
    }

    public ModeElement aimMode = new ModeElement("Ротация", () -> true).set("Smooth", "Matrix", "Snap", "FunTime", "SpookyTime", "HolyWorld", "ReallyWorld", "Neuro", "TestAngle", "TecT", "SuperSine").defaultValue("Smooth").register(this);
    public Slider distance = new Slider("Дистанция", () -> true).set(2.0f, 6.0f, 0.1f).defaultValue(3.0f).register(this);
    public Slider snapFov = new Slider("Радиус Snap", () -> aimMode.isSelected("Snap")).set(10f, 180f, 1f).defaultValue(90f).register(this);

    Collection generalSettings = new Collection("Настройки", () -> true).register(this);
    CheckBox breakShield = new CheckBox("Ломать щит", () -> true).defaultValue(false);
    CheckBox unPressShield = new CheckBox("Отжимать щит", () -> true).defaultValue(false);
    CheckBox dynamicCooldown = new CheckBox("Синхронизация с тиками", () -> true).defaultValue(false);
    public CheckBox tpsSync = new CheckBox("Адаптация под TPS", () -> dynamicCooldown.getEnabled()).defaultValue(true);
    CheckBox wallsAttack = new CheckBox("Бить через стены", () -> true).defaultValue(false);
    CheckBox onlySword = new CheckBox("Бить только с мечом", () -> true).defaultValue(false);
    CheckBox correction = new CheckBox("Коррекция движения", () -> true).defaultValue(true);
    private void setupCollections() {
        breakShield.collection(generalSettings);
        unPressShield.collection(generalSettings);
        dynamicCooldown.collection(generalSettings);
        tpsSync.collection(generalSettings);
        wallsAttack.collection(generalSettings);
        onlySword.collection(generalSettings);
        correction.collection(generalSettings);
    }

    public SelectElements filter = new SelectElements("Фильтр на", () -> true).set("Игроков", "Мобов", "Животных", "Жителей", "Друзей", "Невидимых", "Голых").defaultValue("Игроков", "Невидимых", "Голых").register(this);

    public final ModeElement filterMode = new ModeElement("Режим таргета", () -> true).set("Одиночный", "Динамичный").defaultValue("Одиночный").register(this);
    public final ModeElement targetSort = new ModeElement("Сортировать по", () -> true).set("Здоровью", "Броне", "Дистанции", "Полю зрения", "Всему").defaultValue("Всему").register(this);

    public ModeElement correctionType = new ModeElement("Режим коррекции", () -> correction.getEnabled()).set("Незаметный", "Сфокусированная").defaultValue("Незаметный").register(this);
    public ModeElement criticalType = new ModeElement("Режим критов", () -> true).set("Только криты", "Умные криты").defaultValue("Только криты").register(this);
    public SelectElements hungerAction = new SelectElements("Не бить если", () -> true).set("Открыт контейнер", "Используешь еду").defaultValue("Открыт контейнер").register(this);
    public SelectElements attackSettings = new SelectElements("Настройки атаки", () -> true)
            .set("Сбрасывать бег")
            .defaultValue("Сбрасывать бег")
            .register(this);
    public ModeElement sprintResetMode = new ModeElement("Режим сброса бега", () -> attackSettings.isSelected("Сбрасывать бег")).set("Адаптивный", "Обычный").defaultValue("Адаптивный").register(this);

    public SelectElements elytraAttackSettings = new SelectElements("Настройки атаки на элитрах", () -> true).set("Перегонять", "Резольвер").defaultValue("Перегонять", "Резольвер").register(this);
    public Slider predictDistance = new Slider("Дистанция перегона", () -> elytraAttackSettings.isSelected("Перегонять")).set(1f, 6f, 0.1f).register(this);
    SelectElements elytraPredictSettings = new SelectElements("Настройки перегона на элитрах", () -> elytraAttackSettings.isSelected("Перегонять")).set("Отображать точку перегона", "Атаковать только после перегона").defaultValue("Отображать точку перегона", "Атаковать только после перегона").register(this);

    private boolean isResolving = false;
    private Vec3d resolverPoint = null;
    private final TimerUtil resolverTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil shieldWatch = new TimerUtil();
    private final ServerUtils serverUtils = new ServerUtils();
    @NonFinal @Getter
    private int ticksToAttack;
    private float predict;
    @Getter
    public LivingEntity target;
    private int rotationReturnTicks;
    private final SpookyTimeAngleSmoothMode spookyTimeRotation = new SpookyTimeAngleSmoothMode();
    private final TecTRotation tecTRotation = new TecTRotation();


    private void findResolverPoint() {
        if (mc.player == null || mc.world == null) return;
        Vec3d eye = mc.player.getEyePos();

        float oppositeYaw = mc.player.getYaw() + 180f;
        float searchPitch = -50f;
        int[] yawOffsets = {0, 30, -30, 45, -45, 60, -60, 90, -90};

        for (int offset : yawOffsets) {
            float testYaw = oppositeYaw + offset;

            float radYaw = (float) Math.toRadians(testYaw);
            float radPitch = (float) Math.toRadians(searchPitch);

            double x = -Math.sin(radYaw) * Math.cos(radPitch);
            double y = -Math.sin(radPitch);
            double z = Math.cos(radYaw) * Math.cos(radPitch);

            Vec3d checkVec = new Vec3d(x, y, z).normalize().multiply(8.0);
            Vec3d endPoint = eye.add(checkVec);

            if (mc.world.raycast(new RaycastContext(eye, endPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                resolverPoint = endPoint;
                return;
            }
        }
        resolverPoint = null;
    }

    @Subscribe
    private void onGameUpdate(GameUpdateEvent e) {
        if (mc.player == null || target == null) return;

        if (isResolving) {
            if (resolverTimer.isFinished(300)) {
                isResolving = false;
                resolverPoint = null;
            } else if (resolverPoint != null) {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d direction = resolverPoint.subtract(eyePos);

                float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

                Angle angle = new Angle(yaw, pitch);
                Angle.VecRotation rotation = new Angle.VecRotation(angle, angle.toVector());

                RotationConfig resolverConfig = new RotationConfig(getSmoothMode(), true, true);
                RotationController.INSTANCE.rotateTo(rotation, null, 1, resolverConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
            }
        }
    }
    @Subscribe
    private void onSprint(ChangeSprintEvent e) {
        if (mc.player == null) return;
        if (!attackSettings.isSelected("Сбрасывать бег")) return;

        if (mc.player.isSubmergedInWater() || mc.player.isSwimming()) return;

        if (sprintResetMode.isSelected("Адаптивный") && target != null) {
            e.setSprinting(false);
            return;
        }

        if (sprintResetMode.isSelected("Обычный")) {
            critSprintLogic(e);
        }
    }

    private void critSprintLogic(ChangeSprintEvent e) {
        float threshold = 0.8f;
        if (!mc.player.isOnGround() && target != null && mc.player.getAttackCooldownProgress(0.5f) > threshold && mc.player.getVelocity().y < -0.073) {
            e.setSprinting(false);
        }
    }


    @Subscribe
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() == 0) {
            updateTarget();
            if (rotationReturnTicks > 0) {
                RotationController.INSTANCE.smoothReturnToCamera(getSmoothMode());
                rotationReturnTicks--;
                return;
            }

            if (target != null && !isResolving) {
                rotateToTarget();
            }
        }
    }



    private void sendAttack() {
        if (unPressShield.getEnabled() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem().equals(Items.SHIELD)) {
            mc.interactionManager.stopUsingItem(mc.player);
            shieldWatch.reset();
        }

        if (mc.player.getActiveItem().contains(DataComponentTypes.FOOD) && hungerAction.isSelected("Используешь еду")) {
            return;
        }

        if (hungerAction.isSelected("Открыт контейнер") && mc.currentScreen instanceof HandledScreen<?> && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (aimMode.isSelected("Snap") && !AuraUtil.isInSnapRange(target, getEffectiveSnapFov())) {
            return;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        breakShield();

        ticksToAttack = 10;
    }

    private void breakShield() {
        if (target == null) return;
        if (!(target instanceof PlayerEntity playerTarget)) return;

        if (!breakShield.getEnabled()) return;

        boolean targetOnShield = playerTarget.isUsingItem() && playerTarget.getActiveItem().getItem().equals(Items.SHIELD);
        if (!targetOnShield) return;

        Angle angleToPlayer = AngleUtil.fromVec3d(mc.player.getBoundingBox().getCenter().subtract(playerTarget.getEyePos()));
        boolean isFrontal = Math.abs(RotationController.computeAngleDifference(playerTarget.getYaw(), angleToPlayer.getYaw())) < 90;
        if (!isFrontal) return;

        Slot axeSlot = InventoryHelper.getSlot(s -> s.getItem().getDefaultStack().getItem() instanceof AxeItem);
        if (axeSlot == null) return;

        if (PlayerInventoryComponent.script.isFinished()) {
            HandSwapHelper.swapHand(axeSlot, Hand.MAIN_HAND, false);
            HandSwapHelper.closeScreen(true);
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            handlerClient().getNotificationManager().add("Щит игрока " + target.getDisplayName() + " был сломан!", (int) 1.5f, NotificationType.SUCCESS, RenderType.WORLD);

            HandSwapHelper.swapHand(axeSlot, Hand.MAIN_HAND, false);
            HandSwapHelper.closeScreen(true);

            shieldWatch.reset();
        }
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!aimMode.isSelected("Snap")) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.options == null || mc.options.getPerspective() != Perspective.FIRST_PERSON) return;

        DrawContext context = event.getContext();
        if (context == null) return;

        float radius = getSnapCircleRadius();
        if (radius <= 0.0f) return;

        Window window = mc.getWindow();
        Vec2i center = getMouseInteger(window.getScaledWidth(), window.getScaledHeight());
        float centerX = center.x / 2f;
        float centerY = center.y / 2f;

        int themeColor = Interface.INSTANCE != null ? Interface.INSTANCE.getMainColor(4) : 0x80FFFFFF;
        Color color = new Color(themeColor, true);
        DrawUtil.drawCircle(context.getMatrices(), centerX, centerY, radius, color);
    }

    @Subscribe
    private void onTick(TickEvent tickEvent) {
        if (mc.player == null || mc.world == null) return;

        if (ticksToAttack > 0) ticksToAttack--;

        updateTarget();

        if (target != null) {
            if (canStopSprinting()) mc.player.setSprinting(false);

            if (canAttack()) {
                if (elytraAttackSettings.isSelected("Резольвер") && mc.player.isGliding()) {
                    mc.player.setVelocity(0, 0, 0);
                    findResolverPoint();
                    if (resolverPoint != null) {
                        isResolving = true;
                        resolverTimer.reset();
                    }
                }

                sendAttack();
            }
        } else {
            isResolving = false;
            resolverPoint = null;
        }
    }

    private Comparator<LivingEntity> getTargetComparator() {
        if (mc.player == null) return Comparator.comparingDouble(LivingEntity::getHealth);
        Comparator<LivingEntity> base = Comparator.comparingInt(o -> AuraUtil.inFront(o) ? 0 : 1);
        Comparator<LivingEntity> distanceComparator = Comparator.comparingDouble(o -> mc.player.distanceTo(o));

        if (targetSort.isSelected("Здоровью")) {
            return base.thenComparingDouble(AuraUtil::fixHealth).thenComparingDouble(AuraUtil::fovValue).thenComparing(distanceComparator);
        }

        if (targetSort.isSelected("Броне")) {
            return base.thenComparingDouble(o -> {
                if (o instanceof PlayerEntity player) {
                    return -AuraUtil.AttributePointer.getArmor(player);
                }
                return Double.MAX_VALUE;
            }).thenComparingDouble(AuraUtil::fovValue).thenComparing(distanceComparator);
        }

        if (targetSort.isSelected("Дистанции")) {
            return base.thenComparing(distanceComparator).thenComparingDouble(AuraUtil::fovValue).thenComparingDouble(AuraUtil::fixHealth);
        }

        if (targetSort.isSelected("Полю зрения")) {
            return base.thenComparingDouble(AuraUtil::fovValue).thenComparing(distanceComparator).thenComparingDouble(AuraUtil::fixHealth);
        }

        return base.thenComparing(distanceComparator).thenComparingDouble(AuraUtil::fovValue).thenComparingDouble(AuraUtil::fixHealth).thenComparingInt(o -> {
            if (o instanceof PlayerEntity player) {
                return (int) AuraUtil.AttributePointer.getArmor(player);
            }
            return 0;
        });
    }

    private void sortTargets(List<LivingEntity> targets, Comparator<LivingEntity> comparator) {
        if (targets == null || comparator == null) return;
        if (targetSort.isSelected("Броне")) {targets.removeIf(e -> !(e instanceof PlayerEntity));}
        targets.sort(comparator);
    }

    private void rotateToTarget() {
        Angle angle = getTargetAngle();
        Angle.VecRotation rotation = new Angle.VecRotation(angle, angle.toVector());
        RotationConfig turnConfig = getTurnConfiguration();

        switch (aimMode.getValue()) {
            case "Snap", "HolyWorld" -> {
                if (canAttack() || !attackTimer.isFinished(40)) {
                    RotationController.INSTANCE.rotateTo(rotation, target, 1, turnConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
                }
            }
            case "FunTime" -> {
                if (canAttack()) {
                    RotationController.INSTANCE.clear();
                    RotationController.INSTANCE.rotateTo(rotation, target, 40, turnConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
                }
            }

            case "Smooth", "Matrix", "Neuro", "ReallyWorld", "TestAngle", "TecT", "SuperSine" -> RotationController.INSTANCE.rotateTo(rotation, target, 1, turnConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
            case "SpookyTime" -> RotationController.INSTANCE.rotateTo(rotation, target, 40, turnConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
        }
    }

    private float getSnapCircleRadius() {
        if (mc.options == null) return 0.0f;
        float snap = getEffectiveSnapFov();
        if (snap <= 0.0f) return 0.0f;

        Window window = mc.getWindow();
        float minDim = Math.min(window.getScaledWidth(), window.getScaledHeight());
        float screenRadius = minDim / 2f;
        if (screenRadius <= 0.0f) return 0.0f;

        float cameraFov = getCameraFov();
        if (cameraFov <= 0.0f) return 0.0f;

        double snapRad = Math.toRadians(snap * 0.5f);
        double fovRad = Math.toRadians(cameraFov * 0.5f);
        double ratio = Math.tan(snapRad) / Math.tan(fovRad);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return (float) (screenRadius * ratio);
    }

    private float getEffectiveSnapFov() {
        float snap = snapFov.getValue();
        float cameraFov = getCameraFov();
        if (cameraFov <= 0.0f) return snap;
        return Math.min(snap, cameraFov);
    }

    private float getCameraFov() {
        if (mc.options == null) return 70.0f;
        return mc.options.getFov().getValue().floatValue();
    }

    private RotationConfig getTurnConfiguration() {
        AngleSmoothMode smoothMode = getSmoothMode();
        return new RotationConfig(smoothMode, correction.getEnabled(), correctionType.isSelected("Незаметный"));
    }

    private AngleSmoothMode getSmoothMode() {
        return switch (aimMode.getValue()) {
            case "Smooth" -> new LinearSmoothMode();
            case "Matrix" -> new MatrixSmoothMode();
            case "Snap" -> new LinearSmoothMode();
            case "FunTime" -> new FunTimeSnapMode();
            case "HolyWorld" -> new HolyWorldAngle();
            case "ReallyWorld" -> new ReallyWorldAngleSmoothMode();
            case "Neuro" -> new NeuroSmoothMode();
            case "SpookyTime" -> spookyTimeRotation;
            case "TestAngle" -> new TestAngleRotation();
            case "TecT" -> tecTRotation;
            default -> new LinearSmoothMode();
        };
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return true;
        PlayerEntity player = mc.player;
        if (entity instanceof ClientPlayerEntity) return true;
        if (entity instanceof ArmorStandEntity) return true;

        if (entity instanceof PlayerEntity p && AntiBot.isBot(p)) return true;

        if (handlerClient().getFriendManager().isFriend(entity.getName().getString()) && !filter.isSelected("Друзей")) return true;

        if (entity instanceof PlayerEntity p) {
            boolean hasArmor = false;
            for (ItemStack item : dev.client.api.compat.InventoryCompat.armorItems(p)) {
                if (!item.isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }

            if (p.isInvisible()) {
                if (hasArmor) {
                    if (!filter.isSelected("Игроков")) return true;
                } else {
                    if (!filter.isSelected("Невидимых")) return true;
                }
            } else {
                if (hasArmor) {
                    if (!filter.isSelected("Игроков")) return true;
                } else {
                    if (!filter.isSelected("Голых")) return true;
                }
            }
        }

        if ((entity instanceof MobEntity) && !filter.isSelected("Мобов")) return true;
        if ((entity instanceof AnimalEntity) && !filter.isSelected("Животных")) return true;
        if ((entity instanceof VillagerEntity) && !filter.isSelected("Жителей")) return true;
        if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(entity)) > (player.isGliding() ? 64 : AirStuck.INSTANCE.isEnabled() ? 6f : distance.getValue())) return true;

        if (!wallsAttack.getEnabled()) {
            Vec3d eyePos = player.getEyePos();
            Vec3d targetPoint = BestPoint.getNearestPoint(entity);
            Vec3d direction = targetPoint.subtract(eyePos).normalize();
            double distanceToTarget = eyePos.distanceTo(targetPoint);
            Vec3d endPoint = eyePos.add(direction.multiply(distanceToTarget));

            BlockHitResult raycast = mc.world.raycast(new RaycastContext(eyePos, endPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

            if (raycast.getType() != HitResult.Type.MISS && raycast.getPos().distanceTo(eyePos) < distanceToTarget - 0.5) {
                return true;
            }
        }

        return false;
    }

    public boolean canAttack() {
        if (target == null) {
            return false;
        }

        PlayerEntity player = mc.player;


        if (dynamicCooldown.getEnabled() && tpsSync.getEnabled()) {
            float tps = serverUtils.getTPS();
            float tpsFactor = 20 / MathHelper.clamp(tps, 1, 20);
            float cooldownProgress = player.getAttackCooldownProgress(0.5f);
            if (cooldownProgress < 1 / tpsFactor) {
                return false;
            }
        } else {
            if (!LynxWave.getInstance().getIdealHitUtil().cooldownIsReached(false)) {
                return false;
            }
        }

        if (ticksToAttack > 0) {
            return false;
        }

        if (target.isGliding()) {
            Vec3d predict = PredictUtil.predict(target, predictDistance.getValue());
            double distToPredict = player.getEyePos().distanceTo(predict);
            this.predict = elytraPredictSettings.isSelected("Атаковать только после перегона") ? 2.7f : 4f;
            if (distToPredict > this.predict) {
                return false;
            }
            return true;
        } else {
            if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) > (AirStuck.INSTANCE.isEnabled() ? 6f : distance.getValue())) return false;
        }

        if (criticalType.isSelected("Только криты")) {
            boolean canCrit = LynxWave.getInstance().getIdealHitUtil().canCritical();
            if (canCrit) {
                attackTimer.reset();
            }
            return canCrit;
        }

        return true;
    }

    public boolean canStopSprinting() {
        if (target == null) return false;
        if (!LynxWave.getInstance().getIdealHitUtil().cooldownIsReached(true)) return false;
        if (ticksToAttack > 1) return false;
        if (SimulatedPlayer.simulateLocalPlayer(1).fallDistance == 0) return false;
        return true;
    }

    private void updateTarget() {
        if (mc.player == null || mc.world == null) {
            setTarget(null);
            return;
        }
        if (FreeCamera.INSTANCE.isEnabled()) {
            setTarget(null);
            return;
        }

        LivingEntity best = null;

        List<LivingEntity> validTargets = new java.util.ArrayList<>();

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity) {
                if (isValidEntity(entity)) continue;
                validTargets.add((LivingEntity) entity);
            }
        }

        sortTargets(validTargets, getTargetComparator());

        double bestFovDot = -1;
        for (LivingEntity entity : validTargets) {
            Vec3d targetVec = BestPoint.getNearestPoint(entity).subtract(eyePos).normalize();
            double dot = lookVec.dotProduct(targetVec);
            if (dot > bestFovDot) {
                bestFovDot = dot;
                best = entity;
            }
        }

        if (target == null || isValidEntity(target)) {
            setTarget(best);
        }
    }

    private void setTarget(LivingEntity newTarget) {
        if (this.target != newTarget && this.target != null) {
            rotationReturnTicks = 5;
        }
        if (LynxWave.getInstance().getNeuroRotationManager() != null) {
            if (newTarget == null) {
                LynxWave.getInstance().getNeuroRotationManager().onNoTarget();
            } else if (this.target != newTarget) {
                LynxWave.getInstance().getNeuroRotationManager().onTargetTracked(newTarget);
            }
        }
        this.target = newTarget;
    }

    @Subscribe
    private void onWorldRender(RenderEvent.Draw3D worldRenderEvent) {
        if (!elytraAttackSettings.isSelected("Перегонять") || !elytraPredictSettings.isSelected("Отображать точку перегона") || target == null || !target.isGliding()) return;
        Vec3d predictPos = PredictUtil.predict(target, predictDistance.getValue());
        float size = 0.35f;
        Box box = new Box(predictPos.x - size, predictPos.y - size, predictPos.z - size, predictPos.x + size, predictPos.y + size, predictPos.z + size);
        Draw3DUtil.drawBox(box, Interface.INSTANCE.getMainColor(), 1, true, true, false);
    }

    private Angle getTargetAngle() {
        float range = mc.player.isGliding() ? distance.getValue() + 2f : distance.getValue();
        Vec3d targetPoint = BestPoint.getAimPoint(target, range);

        if (elytraAttackSettings.isSelected("Перегонять") && target.isGliding()) {
            targetPoint = PredictUtil.predict(target, predictDistance.getValue());
        }

        targetPoint = resolveMultipoint(target, targetPoint, range);
        return AngleUtil.fromVec3d(targetPoint.subtract(mc.player.getEyePos()));
    }

    private Vec3d resolveMultipoint(LivingEntity target, Vec3d point, double range) {
        if (target == null) {
            return point;
        }

        return BestPoint.getNearestVisiblePoint(target, point, range);
    }

    @Override
    public void onEnabled() {
        target = null;
        rotationReturnTicks = 0;
        spookyTimeRotation.reset();
        tecTRotation.reset();
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        setTarget(null);
        RotationController.INSTANCE.smoothReturnToCamera(getSmoothMode());
        spookyTimeRotation.reset();
        tecTRotation.reset();
        isResolving = false;
        resolverPoint = null;
        super.onDisabled();
    }
}
