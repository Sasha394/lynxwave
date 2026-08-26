package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.EnchantmentUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.modules.core.combat.killaura.rotation.RaytracingUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class WaterSpeed extends Module {

    public WaterSpeed() {
        super("WaterSpeed", ModuleCategory.Movement, "Увеличивает скорость в воде");
    }

    TimerUtil timerUtil = new TimerUtil();

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player != null) {
            if (mc.options.forwardKey.isPressed()) {
                mc.options.sprintKey.setPressed(true);
            }
            if (mc.player.isSwimming() && mc.player.isTouchingWater() && !mc.player.hasStatusEffect(StatusEffects.WITHER) && !mc.options.useKey.isPressed()) {

                BlockHitResult zov = (BlockHitResult) rayTraceGoidi(0, mc.player.getYaw(), mc.player.getPitch(), mc.player);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, zov);

                if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                    if (EnchantmentUtil.getLevel(Enchantments.DEPTH_STRIDER, mc.player.getEquippedStack(EquipmentSlot.FEET)) > 0) {
                        if (mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) {
                        } else {
                            mc.player.updateVelocity(0.0160f, new Vec3d(mc.player.sidewaysSpeed, mc.player.upwardSpeed, mc.player.forwardSpeed));
                        }
                    } else {
                        if (timerUtil.isReached(15)) {
                            timerUtil.reset();
                            mc.player.updateVelocity(0.0185f, new Vec3d(mc.player.sidewaysSpeed, mc.player.upwardSpeed, mc.player.forwardSpeed));
                        }
                    }
                } else {

                    if (EnchantmentUtil.getLevel(Enchantments.DEPTH_STRIDER, mc.player.getEquippedStack(EquipmentSlot.FEET)) > 0) {
                        if (mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) {
                        } else {
                            mc.player.updateVelocity(0.0160f, new Vec3d(mc.player.sidewaysSpeed, mc.player.upwardSpeed, mc.player.forwardSpeed));
                        }
                    } else {
                        mc.player.updateVelocity(0.0185f, new Vec3d(mc.player.sidewaysSpeed, mc.player.upwardSpeed, mc.player.forwardSpeed));
                    }
                }
            }
        }
    }

    public static HitResult rayTraceGoidi(double zovDistance, float yawZova, float pitchZova, Entity goida) {
        Vec3d startVec = mc.player.getEyePos();
        Vec3d directionVec = RaytracingUtil.getVectorForRotation(pitchZova, yawZova);
        Vec3d endVec = startVec.add(directionVec.x * zovDistance, directionVec.y * zovDistance, directionVec.z * zovDistance);
        return mc.world.raycast(new RaycastContext(startVec, endVec, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, goida));
    }
}