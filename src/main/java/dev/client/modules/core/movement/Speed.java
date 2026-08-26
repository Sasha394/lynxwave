package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.LynxWave;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.*;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.player.PlayerVelocityStrafeEvent;
import dev.client.api.nullcry.helper.entity.MovingUtil;
import dev.client.api.nullcry.helper.entity.PlayerSpeedUtil;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.Simulations;
import dev.client.api.nullcry.helper.player.TimerManager;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Collection;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.KillAura;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import lombok.experimental.NonFinal;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Iterator;

public class Speed extends Module {

    public Speed() {
        super("Speed", ModuleCategory.Movement, "Увеличивает скорость движения");
        setupCollections();
    }

    // === Основные настройки ===
    ModeElement mode = new ModeElement("Режим", () -> true)
            .set("NCP", "StrictStrafe", "MatrixJB", "ElytraLowHop", "MatrixDamage",
                    "FireWork", "Vanilla", "Vulcan", "Matrix7", "GrimEntity",
                    "GrimEntity2", "GrimIce", "GrimEntity2Plus", "GrimComboPlus",
                    "EntityCollision", "Matrix", "Intave", "AresMine", "ReallyWorld")
            .defaultValue("NCP").register(this);

    Slider ncpSpeed = new Slider("Скорость страфа", () -> mode.isSelected("NCP") || mode.isSelected("StrictStrafe"))
            .set(0.3f, 1.0f, 0.001f).defaultValue(0.576f).register(this);
    CheckBox useTimer = new CheckBox("Таймер", () -> true).defaultValue(false).register(this);
    CheckBox pauseInLiquids = new CheckBox("Пауза в воде", () -> true).defaultValue(false).register(this);
    CheckBox pauseWhileSneaking = new CheckBox("Пауза при крадении", () -> true).defaultValue(false).register(this);

    // === MatrixDamage ===
    Slider hurttime = new Slider("ХёртТайм", () -> mode.isSelected("MatrixDamage"))
            .set(0, 10, 1).defaultValue(0).register(this);
    Slider boostFactor = new Slider("Множитель буста", () -> mode.isSelected("MatrixDamage") || mode.isSelected("Vanilla"))
            .set(0.0f, 10.0f, 0.1f).defaultValue(2.0f).register(this);
    CheckBox allowOffGround = new CheckBox("В воздухе", () -> mode.isSelected("MatrixDamage")).defaultValue(true).register(this);
    Slider shiftTicks = new Slider("Тики сдвига", () -> mode.isSelected("MatrixDamage"))
            .set(0, 10, 1).defaultValue(0).register(this);

    // === FireWork ===
    Slider fireWorkSlot = new Slider("Слот фейерверка", () -> mode.isSelected("FireWork"))
            .set(1, 9, 1).defaultValue(1).register(this);
    Slider delay = new Slider("Задержка", () -> mode.isSelected("FireWork"))
            .set(1, 20, 1).defaultValue(8).register(this);

    // === GrimIce ===
    CheckBox strict = new CheckBox("Строгий", () -> mode.isSelected("GrimIce")).defaultValue(false).register(this);

    // === MatrixJB ===
    Slider matrixJBSpeed = new Slider("Скорость таймера", () -> mode.isSelected("MatrixJB"))
            .set(1.0f, 2.0f, 0.001f).defaultValue(1.088f).register(this);

    // === ArmorStands (shared) ===
    CheckBox armorStands = new CheckBox("Стойки для брони", () -> mode.isSelected("GrimEntity2") || mode.isSelected("GrimEntity2Plus") || mode.isSelected("EntityCollision"))
            .defaultValue(false).register(this);

    // === GrimComboPlus ===
    Collection grimComboPlusSettings = new Collection("GrimCombo+ Настройки", () -> mode.isSelected("GrimComboPlus")).register(this);
    Slider grimComboPlusRange = new Slider("Дальность Combo+", () -> mode.isSelected("GrimComboPlus"))
            .set(0.5f, 25.0f, 0.1f).defaultValue(5.0f);
    Slider grimComboPlusFactor = new Slider("Множитель Combo+", () -> mode.isSelected("GrimComboPlus"))
            .set(0.01f, 0.3f, 0.01f).defaultValue(0.07f);
    CheckBox grimComboPlusPrioritizePlayers = new CheckBox("Приоритет игроков", () -> mode.isSelected("GrimComboPlus"))
            .defaultValue(true);

    // === EntityCollision ===
    Collection entityCollisionSettings = new Collection("EntityCollision Настройки", () -> mode.isSelected("EntityCollision")).register(this);
    CheckBox entityCollisionOnlyPlayers = new CheckBox("Только игроки", () -> mode.isSelected("EntityCollision")).defaultValue(false);
    Slider entityCollisionSpeed = new Slider("Скорость", () -> mode.isSelected("EntityCollision"))
            .set(1.0f, 15.0f, 0.1f).defaultValue(8.0f);
    Slider entityCollisionRadius = new Slider("Радиус коллизии", () -> mode.isSelected("EntityCollision"))
            .set(0.5f, 1.5f, 0.01f).defaultValue(1.0f);
    CheckBox entityCollisionOtherCollision = new CheckBox("Другие коллизии", () -> mode.isSelected("EntityCollision")).defaultValue(false);
    CheckBox entityCollisionSlowOnUse = new CheckBox("Замедление при использовании", () -> mode.isSelected("EntityCollision")).defaultValue(false);
    CheckBox entityCollisionPredict = new CheckBox("Предсказание", () -> mode.isSelected("EntityCollision")).defaultValue(true);
    Slider entityCollisionPredictFactor = new Slider("Множитель предсказания", () -> entityCollisionPredict.getEnabled() && mode.isSelected("EntityCollision"))
            .set(1.0f, 5.0f, 0.1f).defaultValue(2.0f);
    CheckBox entityCollisionSmooth = new CheckBox("Плавное движение", () -> mode.isSelected("EntityCollision")).defaultValue(true);

    // === GrimEntity2Plus ===
    Slider grimEntity2BoxExpand = new Slider("Расширение бокса", () -> mode.isSelected("GrimEntity2Plus"))
            .set(0.1f, 50.0f, 0.1f).defaultValue(1.3f).register(this);
    Slider grimEntity2ElytraBoxExpand = new Slider("Расширение бокса (элитра)", () -> mode.isSelected("GrimEntity2Plus"))
            .set(0.1f, 50.0f, 0.1f).defaultValue(2.0f).register(this);

    // === AresMine ===
    Collection aresMineSettings = new Collection("AresMine Настройки", () -> mode.isSelected("AresMine")).register(this);
    Slider aresMineSpeed = new Slider("Скорость", () -> mode.isSelected("AresMine"))
            .set(6.0f, 9.0f, 0.1f).defaultValue(7.0f);
    Slider aresMineExtremeRange = new Slider("Крайняя дальность", () -> mode.isSelected("AresMine"))
            .set(0.0f, 5.0f, 0.1f).defaultValue(2.0f);
    Slider aresMineComboFactor = new Slider("Множитель комбо", () -> mode.isSelected("AresMine"))
            .set(0.01f, 0.3f, 0.01f).defaultValue(0.07f);
    CheckBox aresMinePredict = new CheckBox("Предсказание", () -> mode.isSelected("AresMine")).defaultValue(true);
    CheckBox aresMineSmooth = new CheckBox("Плавное движение", () -> mode.isSelected("AresMine")).defaultValue(true);
    CheckBox aresMineOnlyPlayers = new CheckBox("Только игроки", () -> mode.isSelected("AresMine")).defaultValue(false);
    CheckBox aresMineArmorStands = new CheckBox("Стойки для брони", () -> mode.isSelected("AresMine")).defaultValue(false);
    CheckBox aresMineOtherCollision = new CheckBox("Другие коллизии", () -> mode.isSelected("AresMine")).defaultValue(false);
    CheckBox aresMineSlowOnUse = new CheckBox("Замедление при использовании", () -> mode.isSelected("AresMine")).defaultValue(false);

    @NonFinal
    int rwTicks, groundTicks;

    private void setupCollections() {
        grimComboPlusRange.collection(grimComboPlusSettings);
        grimComboPlusFactor.collection(grimComboPlusSettings);
        grimComboPlusPrioritizePlayers.collection(grimComboPlusSettings);

        entityCollisionOnlyPlayers.collection(entityCollisionSettings);
        entityCollisionSpeed.collection(entityCollisionSettings);
        entityCollisionRadius.collection(entityCollisionSettings);
        entityCollisionOtherCollision.collection(entityCollisionSettings);
        entityCollisionSlowOnUse.collection(entityCollisionSettings);
        entityCollisionPredict.collection(entityCollisionSettings);
        entityCollisionPredictFactor.collection(entityCollisionSettings);
        entityCollisionSmooth.collection(entityCollisionSettings);

        aresMineSpeed.collection(aresMineSettings);
        aresMineExtremeRange.collection(aresMineSettings);
        aresMineComboFactor.collection(aresMineSettings);
        aresMinePredict.collection(aresMineSettings);
        aresMineSmooth.collection(aresMineSettings);
        aresMineOnlyPlayers.collection(aresMineSettings);
        aresMineArmorStands.collection(aresMineSettings);
        aresMineOtherCollision.collection(aresMineSettings);
        aresMineSlowOnUse.collection(aresMineSettings);
    }

    // === Состояние ===
    public final PlayerSpeedUtil playerSpeedUtil = new PlayerSpeedUtil();
    public double baseSpeed;
    private int stage;
    private int ticks;
    private int prevSlot;
    private float prevForward;
    private TimerUtil elytraDelay;
    private TimerUtil startDelay;

    private Entity collisionTargetEntity;
    private Vec3d collisionLastTargetPos;
    private Vec3d collisionPredictedPos;
    private double[] collisionLastMotion;

    private Entity comboPlusTarget;
    private int comboPlusLostTicks;
    private int comboPlusStagger;

    private Entity aresMineTargetEntity;
    private Vec3d aresMineLastTargetPos;
    private Vec3d aresMineTargetPredictedPos;
    private double[] aresMineLastMotion;

    private boolean prevOnGround;
    private float lastFallDistance;

    private static final float ANGLE_BLEND = 0.12f;
    private static final int SMOOTH_CYCLE = 6;
    private static final int MAX_LOST_TICKS = 20;

    @Override
    public void onEnabled() {
        stage = 1;
        ticks = 0;
        baseSpeed = 0.2873;
        elytraDelay = new TimerUtil();
        startDelay = new TimerUtil();
        prevSlot = -1;
        prevOnGround = false;
        collisionTargetEntity = null;
        collisionLastTargetPos = null;
        collisionPredictedPos = null;
        collisionLastMotion = new double[]{0.0, 0.0};
        comboPlusTarget = null;
        comboPlusLostTicks = 0;
        comboPlusStagger = 0;
        aresMineTargetEntity = null;
        aresMineLastTargetPos = null;
        aresMineTargetPredictedPos = null;
        aresMineLastMotion = new double[]{0.0, 0.0};
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        LynxWave.tickTimer = 1.0f;
        TimerManager.reset();
        rwTicks = 0;
        groundTicks = 0;
        super.onDisabled();
    }

    // ===================== EVENTS =====================

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (event.getType() == PacketEvent.Type.RECEIVE) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                playerSpeedUtil.setOldSpeed(0);
                if (mode.isSelected("NCP") || mode.isSelected("StrictStrafe")) {
                    toggle();
                }
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInLiquids.getEnabled()) return;
        if (mc.player.isSneaking() && pauseWhileSneaking.getEnabled()) return;

        if (mode.isSelected("Matrix7")) {
            handleMatrix7();
            return;
        }

        if (mode.isSelected("Matrix")) {
            boolean closeToGround = mc.player.isOnGround();
            if (!closeToGround) {
                lastFallDistance = (float) mc.player.fallDistance;
            }
            if (!prevOnGround && closeToGround && lastFallDistance >= 0.5f && MovingUtil.isMoving()) {
                Vec3d vel = mc.player.getVelocity();
                mc.player.setVelocity(vel.x * 2.0, vel.y, vel.z * 2.0);
                baseSpeed = MovingUtil.getBaseSpeed() * 2.0;
            }
            prevOnGround = closeToGround;
            return;
        }

        if (mode.isSelected("Intave")) {
            handleIntave();
            return;
        }

        if (mode.isSelected("MatrixJB")) {
            boolean closeToGround = false;
            for (VoxelShape shape : mc.world.getBlockCollisions(mc.player,
                    mc.player.getBoundingBox().expand(0.5, 0.0, 0.5).offset(0.0, -1.0, 0.0))) {
                if (shape != VoxelShapes.empty()) {
                    closeToGround = true;
                    break;
                }
            }
            if (MovingUtil.isMoving() && closeToGround && mc.player.fallDistance <= 0.0f) {
                LynxWave.tickTimer = 1.0f;
                mc.player.setOnGround(true);
                mc.player.jump();
            } else if (mc.player.fallDistance > 0.0f && useTimer.getEnabled()) {
                LynxWave.tickTimer = matrixJBSpeed.getValue();
                mc.player.addVelocity(0.0, -0.003, 0.0);
            }
        }

        if (mode.isSelected("GrimComboPlus")) {
            comboPlusTarget = findComboPlusTarget();
            ++comboPlusStagger;
        }

        // FireWork
        if (mode.isSelected("FireWork")) {
            --ticks;
            int elytraSlot = InventoryHelper.getElytraHotbarSlot();
            int fireSlot = InventoryHelper.findItemNoChanges(9, Items.FIREWORK_ROCKET);
            boolean inOffHand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;

            if (elytraSlot != -1 && (fireSlot != -1 || inOffHand)
                    && !mc.player.isOnGround() && mc.player.fallDistance > 0.0f && ticks <= 0) {
                if (elytraSlot != -2) {
                    mc.interactionManager.clickSlot(0, elytraSlot, 1, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
                }
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                int currentSlot = mc.player.getInventory().getSelectedSlot();
                if (currentSlot != fireSlot && !inOffHand) {
                    sendPacket(new UpdateSelectedSlotC2SPacket(fireSlot));
                }
                mc.interactionManager.interactItem(mc.player, inOffHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                if (currentSlot != fireSlot && !inOffHand) {
                    sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                }
                if (elytraSlot != -2) {
                    mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(0, elytraSlot, 1, SlotActionType.PICKUP, mc.player);
                }
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                ticks = delay.getValue().intValue();
            }
        }

        // ElytraLowHop
        if (mode.isSelected("ElytraLowHop")) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
                return;
            }
            if (mc.world.getBlockCollisions(mc.player,
                    mc.player.getBoundingBox().expand(-0.29, 0.0, -0.29).offset(0.0, -3.0, 0.0)).iterator().hasNext()
                    && elytraDelay.isReached(150) && startDelay.isReached(500)) {
                int elytraSlot = InventoryHelper.getElytraHotbarSlot();
                if (elytraSlot == -1) {
                    toggle();
                    return;
                }
                elytraSwap(elytraSlot);
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                if (MovingUtil.isMoving()) {
                    MovingUtil.setVelocity(0.85);
                }
                elytraDelay.reset();
            }
        }

        // Vulcan
        if (mode.isSelected("Vulcan")) {
            if (mc.player.isOnGround() && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                    || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())) {
                mc.player.jump();
            }
            if (!mc.player.isOnGround() && mc.player.age % 14 == 0) {
                MovingUtil.setStrafe(0.595);
                mc.player.setVelocity(mc.player.getVelocity().x, -0.37, mc.player.getVelocity().z);
            }
            if (mc.player.isOnGround()) {
                MovingUtil.setStrafe(0.48);
            }
        }
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInLiquids.getEnabled()) return;
        if (mc.player.isSneaking() && pauseWhileSneaking.getEnabled()) return;

        // GrimEntity
        if (mode.isSelected("GrimEntity")) {
            for (PlayerEntity ent : mc.world.getPlayers()) {
                if (ent != mc.player && mc.player.squaredDistanceTo(ent) <= 2.25) {
                    float p = mc.world.getBlockState(mc.player.getVelocityAffectingPos()).getBlock().getSlipperiness();
                    float f = mc.player.isOnGround() ? p * 0.91f : 0.91f;
                    float f2 = mc.player.isOnGround() ? p : 0.99f;
                    mc.player.setVelocity(
                            mc.player.getVelocity().x / f * f2,
                            mc.player.getVelocity().y,
                            mc.player.getVelocity().z / f * f2
                    );
                    break;
                }
            }
        }

        // GrimEntity2
        if (mode.isSelected("GrimEntity2") && MovingUtil.isMoving()) {
            int collisions = 0;
            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;
                if (ent instanceof ArmorStandEntity && !armorStands.getEnabled()) continue;
                if (!(ent instanceof LivingEntity) && !(ent instanceof BoatEntity)) continue;
                if (mc.player.getBoundingBox().expand(1.0).intersects(ent.getBoundingBox())) {
                    ++collisions;
                }
            }
            double[] motion = MovingUtil.forward(0.08 * collisions);
            mc.player.addVelocity(motion[0], 0.0, motion[1]);
        }

        // EntityCollision
        if (mode.isSelected("EntityCollision")) {
            handleEntityCollision();
        }

        // AresMine
        if (mode.isSelected("AresMine")) {
            handleAresMine();
        }

        // GrimComboPlus
        if (mode.isSelected("GrimComboPlus") && MovingUtil.isMoving()) {
            handleGrimComboPlus();
        }

        // GrimEntity2Plus
        if (mode.isSelected("GrimEntity2Plus")) {
            handleGrimEntity2Plus();
        }

        // MatrixDamage
        if (mode.isSelected("MatrixDamage") && MovingUtil.isMoving()
                && mc.player.hurtTime > hurttime.getValue().intValue()) {
            if (mc.player.isOnGround()) {
                MovingUtil.setVelocity(0.387f * boostFactor.getValue());
            } else if (mc.player.isGliding()) {
                MovingUtil.setVelocity(0.346f * boostFactor.getValue());
            } else if (!mc.player.isOnGround() && allowOffGround.getEnabled()) {
                MovingUtil.setVelocity(0.448f * boostFactor.getValue());
            }
        }

        // Matrix
        if (mode.isSelected("Matrix")) {
            if (!mc.world.getBlockCollisions(mc.player,
                    mc.player.getBoundingBox().expand(0.5, 0, 0.5).offset(0, -1, 0)).iterator().hasNext()
                    && mc.player.age % 2 == 0) {
                mc.player.setMovementSpeed(0.026523f);
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        // GrimIce
        if (mode.isSelected("GrimIce") && mc.player.isOnGround()) {
            BlockPos pos = mc.player.getVelocityAffectingPos();
            int iceSlot = findIceInHotbar();
            if (mc.world.isAir(pos) && iceSlot != -1) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(iceSlot);
                sendPacket(new PlayerMoveC2SPacket.Full(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        mc.player.getYaw(), 90.0f, mc.player.isOnGround(), false));
                if (strict.getEnabled()) {
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN));
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
                }
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
                sendSequencedPacket((id) ->
                        new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                                new net.minecraft.util.hit.BlockHitResult(
                                        pos.toCenterPos().add(0.0, 0.5, 0.0),
                                        Direction.DOWN, pos, false), id));
                mc.world.setBlockState(pos, Blocks.ICE.getDefaultState());
            }
        }
    }

    @Subscribe
    public void onPostTick(PostMovementEvent event) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null) return;

        // GrimIce slot restore
        if (mode.isSelected("GrimIce") && prevSlot != -1) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }

        // PostMove for speed tracking
        playerSpeedUtil.postMove(event.getHorizontalMove());
    }

    @Subscribe
    public void onMovement(MovementEvent movementEvent) {
        if (FreeCamera.INSTANCE != null && FreeCamera.INSTANCE.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInLiquids.getEnabled()) return;
        if (mc.player.isSneaking() && pauseWhileSneaking.getEnabled()) return;

        // NCP / StrictStrafe
        if (mode.isSelected("NCP") || mode.isSelected("StrictStrafe")) {
            if (mc.player.getAbilities().flying) return;
            if (mc.player.isGliding()) return;
            if (mc.player.getHungerManager().getFoodLevel() <= 6) return;
            if (movementEvent.isCancelled()) return;

            movementEvent.cancelled();
            if (MovingUtil.isMoving()) {
                LynxWave.tickTimer = useTimer.getEnabled() ? 1.088f : 1.0f;

                float currentSpeed = mode.isSelected("NCP")
                        && dev.client.api.compat.InputCompat.movementForward(mc.player.input) <= 0.0f && prevForward > 0.0f
                        ? (float) (MovingUtil.getVelocity() * 0.8) : (float) MovingUtil.getVelocity();

                boolean canJump = !mc.player.horizontalCollision;
                if (stage == 1 && mc.player.isOnGround() && canJump) {
                    float jumpSpeed = getJumpSpeed();
                    mc.player.setVelocity(mc.player.getVelocity().x, jumpSpeed, mc.player.getVelocity().z);
                    movementEvent.setMotion(new Vec3d(movementEvent.getMotion().x, jumpSpeed, movementEvent.getMotion().z));
                    baseSpeed *= 2.149;
                    stage = 2;
                } else if (stage == 2) {
                    baseSpeed = currentSpeed - 0.66 * (currentSpeed - MovingUtil.getBaseSpeed());
                    stage = 3;
                } else {
                    if (mc.world.getBlockCollisions(mc.player,
                            mc.player.getBoundingBox().offset(0.0, mc.player.getVelocity().y, 0.0)).iterator().hasNext()
                            || mc.player.verticalCollision) {
                        stage = 1;
                    }
                    baseSpeed = currentSpeed - currentSpeed / 159.0;
                }

                baseSpeed = Math.max(baseSpeed, MovingUtil.getBaseSpeed());

                double speedRatio = ncpSpeed.getValue() / 0.576;
                double ncpSpd = (mode.isSelected("StrictStrafe") || dev.client.api.compat.InputCompat.movementForward(mc.player.input) < 1.0f ? 0.465 : 0.576) * speedRatio;
                double ncpBypassSpd = (mode.isSelected("StrictStrafe") || dev.client.api.compat.InputCompat.movementForward(mc.player.input) < 1.0f ? 0.44 : 0.57) * speedRatio;

                if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                    double amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
                    ncpSpd *= 1.0 + 0.2 * (amplifier + 1.0);
                    ncpBypassSpd *= 1.0 + 0.2 * (amplifier + 1.0);
                }
                if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                    double amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
                    ncpSpd /= 1.0 + 0.2 * (amplifier + 1.0);
                    ncpBypassSpd /= 1.0 + 0.2 * (amplifier + 1.0);
                }

                baseSpeed = Math.min(baseSpeed, ticks > 25 ? ncpSpd : ncpBypassSpd);
                if (ticks++ > 50) ticks = 0;

                setMovementSpeed(movementEvent, baseSpeed);
                prevForward = dev.client.api.compat.InputCompat.movementForward(mc.player.input);
            } else {
                LynxWave.tickTimer = 1.0f;
                movementEvent.setMotion(new Vec3d(0.0, movementEvent.getMotion().y, 0.0));
            }
        }
    }

    // ===================== РЕЖИМЫ =====================

    private void handleIntave() {
        if (!MovingUtil.isMoving()) {
            LynxWave.tickTimer = 1.0f;
            return;
        }
        if (mc.player.isOnGround()) {
            mc.options.forwardKey.setPressed(false);
            mc.player.jump();
        }
        if (!mc.player.isOnGround() && mc.player.fallDistance <= 0.1f) {
            LynxWave.tickTimer = 1.432f;
        } else if (mc.player.fallDistance > 0.1f && mc.player.fallDistance < 1.3f) {
            LynxWave.tickTimer = 0.733f;
        } else if (mc.player.fallDistance >= 1.3f) {
            LynxWave.tickTimer = 1.0f;
        }
    }

    private void handleMatrix7() {
        if (!MovingUtil.isMoving()) return;
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.419652, mc.player.getVelocity().z);
            MovingUtil.setStrafe(MovingUtil.getVelocity());
        } else {
            double motionSq = mc.player.getVelocity().x * mc.player.getVelocity().x
                    + mc.player.getVelocity().z * mc.player.getVelocity().z;
            if (motionSq < 0.04) {
                MovingUtil.setStrafe(MovingUtil.getVelocity());
            }
        }
    }

    // ===================== ENTITY COLLISION =====================

    private void handleEntityCollision() {
        if (!MovingUtil.isMoving()) return;

        double slowMultiplier = entityCollisionSlowOnUse.getEnabled() && mc.player.isUsingItem() ? 0.85 : 1.0;
        updateCollisionTarget();

        int collisions = 0;
        double expand = entityCollisionRadius.getValue();
        Box expandedBox = mc.player.getBoundingBox().expand(expand, expand, expand);

        for (Entity ent : mc.world.getEntities()) {
            if (ent == mc.player) continue;
            if (entityCollisionOnlyPlayers.getEnabled() && !(ent instanceof PlayerEntity)) continue;
            if (isEntityWithCollision(ent) && expandedBox.intersects(ent.getBoundingBox())) {
                ++collisions;
            }
        }

        if (collisions <= 0) return;

        boolean auraHasTarget = KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.getTarget() != null;
        float yaw = auraHasTarget ? RotationController.INSTANCE.getRotation().getYaw() : mc.player.getYaw();
        double boostSpeed = entityCollisionSpeed.getValue() * 0.01 * collisions * slowMultiplier;

        if (entityCollisionPredict.getEnabled() && collisionPredictedPos != null && collisionTargetEntity != null) {
            double[] directionMotion = getDirectionToPoint(dev.client.api.compat.EntityCompat.pos(mc.player), collisionPredictedPos, boostSpeed);
            if (entityCollisionSmooth.getEnabled()) {
                double accel = 0.6;
                directionMotion[0] = collisionLastMotion[0] + (directionMotion[0] - collisionLastMotion[0]) * accel;
                directionMotion[1] = collisionLastMotion[1] + (directionMotion[1] - collisionLastMotion[1]) * accel;
            }
            collisionLastMotion[0] = directionMotion[0];
            collisionLastMotion[1] = directionMotion[1];
            mc.player.addVelocity(directionMotion[0], 0.0, directionMotion[1]);
        } else {
            double[] directionMotion = forwardWithYaw(boostSpeed, yaw);
            mc.player.addVelocity(directionMotion[0], 0.0, directionMotion[1]);
        }
    }

    private boolean isEntityWithCollision(Entity ent) {
        if (ent instanceof LivingEntity) {
            return !(ent instanceof ArmorStandEntity) || armorStands.getEnabled();
        }
        if (ent instanceof BoatEntity) return true;
        if (!entityCollisionOtherCollision.getEnabled()) return false;
        return ent instanceof AbstractMinecartEntity;
    }

    private void updateCollisionTarget() {
        if (KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.getTarget() != null) {
            collisionTargetEntity = KillAura.INSTANCE.getTarget();
        } else {
            collisionTargetEntity = null;
            double closestDistance = Double.MAX_VALUE;
            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;
                if (entityCollisionOnlyPlayers.getEnabled() && !(ent instanceof PlayerEntity)) continue;
                if (!(ent instanceof LivingEntity) && !(ent instanceof BoatEntity)) continue;
                double distance = mc.player.squaredDistanceTo(ent);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    collisionTargetEntity = ent;
                }
            }
        }

        if (collisionTargetEntity != null) {
            if (collisionLastTargetPos == null) {
                collisionLastTargetPos = dev.client.api.compat.EntityCompat.pos(collisionTargetEntity);
                collisionPredictedPos = collisionLastTargetPos;
            } else {
                Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(collisionTargetEntity);
                Vec3d velocity = currentPos.subtract(collisionLastTargetPos);
                if (entityCollisionPredict.getEnabled()) {
                    collisionPredictedPos = currentPos.add(velocity.multiply(entityCollisionPredictFactor.getValue()));
                    if (!mc.world.isChunkLoaded((int) collisionPredictedPos.x >> 4, (int) collisionPredictedPos.z >> 4)) {
                        collisionPredictedPos = currentPos;
                    }
                } else {
                    collisionPredictedPos = currentPos;
                }
                collisionLastTargetPos = currentPos;
            }
        } else {
            collisionLastTargetPos = null;
            collisionPredictedPos = null;
        }
    }

    // ===================== ARESMINE =====================

    private void handleAresMine() {
        if (!MovingUtil.isMoving()) return;

        double slowMul = aresMineSlowOnUse.getEnabled() && mc.player.isUsingItem() ? 0.85 : 1.0;
        boolean hasTarget = KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.getTarget() != null;

        if (!hasTarget) {
            int collisions = 0;
            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;
                if (aresMineOnlyPlayers.getEnabled() && !(ent instanceof PlayerEntity)) continue;
                if (isAresMineEntityValid(ent) && mc.player.getBoundingBox().expand(0.5).intersects(ent.getBoundingBox())) {
                    ++collisions;
                }
            }
            if (collisions <= 0) return;
            double[] motion = MovingUtil.forward(aresMineSpeed.getValue() * 0.01 * collisions * slowMul);
            mc.player.addVelocity(motion[0], 0.0, motion[1]);
        } else {
            Entity target = KillAura.INSTANCE.getTarget();
            updateAresMineTarget(target);

            Vec3d targetPos = aresMinePredict.getEnabled() && aresMineTargetPredictedPos != null
                    ? aresMineTargetPredictedPos : dev.client.api.compat.EntityCompat.pos(target);
            boolean colliding = mc.player.getBoundingBox().expand(0.5).intersects(target.getBoundingBox());
            double dist = mc.player.distanceTo(target);

            if (colliding) {
                double speed = aresMineSpeed.getValue() * 0.01 * slowMul;
                double[] dir = getDirectionToPoint(dev.client.api.compat.EntityCompat.pos(mc.player), targetPos, speed);
                if (aresMineSmooth.getEnabled()) {
                    double accel = 0.72;
                    dir[0] = aresMineLastMotion[0] + (dir[0] - aresMineLastMotion[0]) * accel;
                    dir[1] = aresMineLastMotion[1] + (dir[1] - aresMineLastMotion[1]) * accel;
                }
                aresMineLastMotion[0] = dir[0];
                aresMineLastMotion[1] = dir[1];
                mc.player.addVelocity(dir[0], 0.0, dir[1]);
            } else if (aresMineExtremeRange.getValue() > 0.0f && dist <= 0.7 + aresMineExtremeRange.getValue()) {
                double[] dir = getDirectionToPoint(dev.client.api.compat.EntityCompat.pos(mc.player), targetPos, 1.0);
                double normalizedDist = Math.min(1.0, dist / (0.7 + aresMineExtremeRange.getValue()));
                double accelFactor = aresMineComboFactor.getValue() * (0.5 + 0.5 * normalizedDist);
                accelFactor = Math.min(accelFactor, aresMineSpeed.getValue() * 0.01);
                double groundMul = mc.player.isOnGround() ? 0.95 : 1.0;
                double fx = dir[0] * accelFactor * groundMul * slowMul;
                double fz = dir[1] * accelFactor * groundMul * slowMul;
                if (aresMineSmooth.getEnabled()) {
                    double accel = 0.6;
                    fx = aresMineLastMotion[0] + (fx - aresMineLastMotion[0]) * accel;
                    fz = aresMineLastMotion[1] + (fz - aresMineLastMotion[1]) * accel;
                }
                aresMineLastMotion[0] = fx;
                aresMineLastMotion[1] = fz;
                mc.player.addVelocity(fx, 0.0, fz);
            }
        }
    }

    private void updateAresMineTarget(Entity target) {
        aresMineTargetEntity = target;
        Vec3d currentPos = dev.client.api.compat.EntityCompat.pos(target);
        if (aresMineLastTargetPos == null) {
            aresMineLastTargetPos = currentPos;
            aresMineTargetPredictedPos = currentPos;
        } else {
            Vec3d velocity = currentPos.subtract(aresMineLastTargetPos);
            if (aresMinePredict.getEnabled()) {
                aresMineTargetPredictedPos = currentPos.add(velocity.multiply(3.0));
                if (!mc.world.isChunkLoaded((int) aresMineTargetPredictedPos.x >> 4, (int) aresMineTargetPredictedPos.z >> 4)) {
                    aresMineTargetPredictedPos = currentPos;
                }
            } else {
                aresMineTargetPredictedPos = currentPos;
            }
            aresMineLastTargetPos = currentPos;
        }
    }

    private boolean isAresMineEntityValid(Entity ent) {
        if (ent instanceof LivingEntity) {
            return !(ent instanceof ArmorStandEntity) || aresMineArmorStands.getEnabled();
        }
        if (ent instanceof BoatEntity) return true;
        if (!aresMineOtherCollision.getEnabled()) return false;
        return ent instanceof AbstractMinecartEntity;
    }

    // ===================== GRIM COMBO PLUS =====================

    private void handleGrimComboPlus() {
        if (comboPlusTarget != null) {
            Vec3d dir = getSmoothedDirectionTo(comboPlusTarget);
            double base = Math.sqrt(mc.player.squaredDistanceTo(comboPlusTarget));
            boolean approaching = isApproaching(comboPlusTarget);
            boolean colliding = mc.player.getBoundingBox().expand(0.3).intersects(comboPlusTarget.getBoundingBox());

            double factor = grimComboPlusFactor.getValue();
            if (approaching) {
                factor *= 1.1 * (1.0 + 0.4 * (1.0 - Math.min(1.0, base / grimComboPlusRange.getValue())));
            } else {
                factor *= 0.9 * (1.0 + 0.4 * Math.min(1.0, base / (grimComboPlusRange.getValue() / 2.0)));
            }
            if (colliding) factor *= 0.8;

            if (comboPlusStagger % SMOOTH_CYCLE == 0) {
                factor *= 0.85;
            } else {
                double ratio = (double) (comboPlusStagger % SMOOTH_CYCLE) / SMOOTH_CYCLE;
                factor *= 0.9 + 0.1 * Math.sin(ratio * Math.PI);
            }
            if (comboPlusStagger % 3 == 0) {
                factor *= 0.98 + Math.random() * 0.04;
            }
            factor = Math.min(factor, 0.06);

            double multiplier = mc.player.isOnGround() ? 0.95 : 1.0;
            mc.player.addVelocity(dir.x * factor * multiplier, 0.0, dir.z * factor * multiplier);
        } else {
            int collisions = 0;
            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;
                if (ent instanceof ArmorStandEntity && !armorStands.getEnabled()) continue;
                if (!(ent instanceof LivingEntity) && !(ent instanceof BoatEntity)) continue;
                if (mc.player.getBoundingBox().expand(0.8).intersects(ent.getBoundingBox())) {
                    ++collisions;
                }
            }
            double baseFwd = Math.min(0.04 * collisions, 0.05);
            double[] motion = MovingUtil.forward(baseFwd);
            double fade = 0.9 + 0.1 * Math.sin((double) (comboPlusStagger % SMOOTH_CYCLE) / SMOOTH_CYCLE * Math.PI);
            double mul = mc.player.isOnGround() ? 0.95 : 1.0;
            mc.player.addVelocity(motion[0] * fade * mul, 0.0, motion[1] * fade * mul);
        }
    }

    private Entity findComboPlusTarget() {
        if (comboPlusTarget != null && isValidComboPlusTarget(comboPlusTarget)
                && mc.player.squaredDistanceTo(comboPlusTarget) <= grimComboPlusRange.getValue() * grimComboPlusRange.getValue()) {
            comboPlusLostTicks = 0;
            return comboPlusTarget;
        }
        if (comboPlusLostTicks < MAX_LOST_TICKS) {
            ++comboPlusLostTicks;
            return comboPlusTarget;
        }

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity ent : mc.world.getEntities()) {
            if (!isValidComboPlusTarget(ent)) continue;
            double distSq = mc.player.squaredDistanceTo(ent);
            if (distSq > grimComboPlusRange.getValue() * grimComboPlusRange.getValue()) continue;
            double score = distSq;
            if (grimComboPlusPrioritizePlayers.getEnabled() && ent instanceof PlayerEntity) {
                score = distSq * 0.5;
            }
            if (score < bestScore) {
                bestScore = score;
                best = ent;
            }
        }
        comboPlusTarget = best;
        return best;
    }

    private boolean isValidComboPlusTarget(Entity e) {
        if (e == null || e == mc.player || !e.isAlive()) return false;
        if (e instanceof ArmorStandEntity && !armorStands.getEnabled()) return false;
        return e instanceof LivingEntity || e instanceof BoatEntity;
    }

    private boolean isApproaching(Entity target) {
        Vec3d look = mc.player.getRotationVector().normalize();
        Vec3d toTarget = dev.client.api.compat.EntityCompat.pos(target).subtract(dev.client.api.compat.EntityCompat.pos(mc.player)).normalize();
        return look.x * toTarget.x + look.z * toTarget.z > 0.0;
    }

    private Vec3d getSmoothedDirectionTo(Entity target) {
        Vec3d toTarget = dev.client.api.compat.EntityCompat.pos(target).subtract(dev.client.api.compat.EntityCompat.pos(mc.player)).normalize();
        Vec3d lookHor = new Vec3d(mc.player.getRotationVector().x, 0.0, mc.player.getRotationVector().z).normalize();
        Vec3d blended = new Vec3d(
                toTarget.x * (1.0 - ANGLE_BLEND) + lookHor.x * ANGLE_BLEND,
                0.0,
                toTarget.z * (1.0 - ANGLE_BLEND) + lookHor.z * ANGLE_BLEND
        ).normalize();
        return new Vec3d(blended.x, 0.0, blended.z);
    }

    // ===================== GRIM ENTITY2 PLUS =====================

    private void handleGrimEntity2Plus() {
        if (!MovingUtil.isMoving()) return;
        int collisions = 0;
        float expand = mc.player.isGliding() ? grimEntity2ElytraBoxExpand.getValue() : grimEntity2BoxExpand.getValue();

        for (Entity ent : mc.world.getEntities()) {
            if (ent == mc.player) continue;
            if (ent instanceof ArmorStandEntity && !armorStands.getEnabled()) continue;
            if (!(ent instanceof LivingEntity) && !(ent instanceof BoatEntity)) continue;
            if (mc.player.getBoundingBox().expand(expand).intersects(ent.getBoundingBox())) {
                ++collisions;
            }
        }
        if (collisions > 0) {
            double[] motion = MovingUtil.forward(0.08 * collisions);
            mc.player.addVelocity(motion[0], 0.0, motion[1]);
        }
    }

    // ===================== УТИЛИТЫ =====================

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double speed) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0.0) return new double[]{0.0, 0.0};
        return new double[]{dx / len * speed, dz / len * speed};
    }

    private double[] forwardWithYaw(double speed, float yaw) {
        double rad = Math.toRadians(yaw + 90.0f);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new double[]{speed * cos, speed * sin};
    }

    private float getJumpSpeed() {
        float jumpMotion = 0.42f;
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            jumpMotion += (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;
        }
        return jumpMotion;
    }

    private void setMovementSpeed(MovementEvent event, double speed) {
        float forward = dev.client.api.compat.InputCompat.movementForward(mc.player.input);
        float strafe = dev.client.api.compat.InputCompat.movementSideways(mc.player.input);
        float yaw = mc.player.getYaw();

        if (forward == 0 && strafe == 0) {
            event.setMotion(new Vec3d(0.0, event.getMotion().y, 0.0));
            return;
        }
        if (forward != 0) {
            if (strafe > 0) {
                yaw += (forward > 0 ? -45 : 45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }
            strafe = 0;
            forward = forward > 0 ? 1 : -1;
        }
        double rad = Math.toRadians(yaw + 90.0f);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        event.setMotion(new Vec3d(
                forward * speed * cos + strafe * speed * sin,
                event.getMotion().y,
                forward * speed * sin - strafe * speed * cos
        ));
    }

    private int findIceInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            var item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.ICE || item == Items.PACKED_ICE || item == Items.BLUE_ICE) {
                return i;
            }
        }
        return -1;
    }

    private void elytraSwap(int elytraSlot) {
        if (elytraSlot != -2) {
            mc.interactionManager.clickSlot(0, elytraSlot, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
        }
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        if (elytraSlot != -2) {
            mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, elytraSlot, 1, SlotActionType.PICKUP, mc.player);
        }
    }

    @Subscribe
    public void onVelocityPost(PlayerVelocityStrafeEvent e) {
        if (!isReallyWorld()) return;

        TimerManager.setTimer(1.7F);

        if (ticks > 3 && Simulations.hasPlayerMovement()) {
            double boost = 0.03;

            if (ticks % 2 == 0) {
                mc.player.addVelocityInternal(new Vec3d(0, 0.03F, 0));
                boost = mc.player.isOnGround() ? 0.085 : 0.03;
            }

            double[] direction = Simulations.forward(boost);
            mc.player.addVelocityInternal(new Vec3d(direction[0], 0, direction[1]));
        }

        ticks++;
    }

    @Subscribe
    public void onInput(InputEvent e) {
        if (!isReallyWorld()) return;

        if (mc.player.verticalCollision) groundTicks++;
        else groundTicks = 0;

        if (groundTicks >= 1) {
            mc.player.jump();
        }
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent e) {
        if (!isReallyWorld()) return;

        if (ticks % 2 == 0) {
            TimerManager.setTimer(0.3F);
            sendPacketSilent(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    @Subscribe
    public void onRwPacket(PacketEvent e) {
        if (!isReallyWorld()) return;

        if (e.getType() == PacketEvent.Type.RECEIVE && e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (rwTicks % 2 == 1) {
                rwTicks++;
            }

            TimerManager.reset();
        }
    }

    private boolean isReallyWorld() {
        return mode.isSelected("ReallyWorld") && mc.player != null && mc.world != null;
    }
}
