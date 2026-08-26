package dev.client.modules.core.combat.killaura.attack;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.modules.core.combat.KillAura;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Vec3d;

public class IdealHitUtil implements ClientApi {

    public IdealHitUtil() {
        LynxWave.getInstance().getEventBus().register(this);
    }

    public boolean cooldownIsReached(boolean toSprinting) {
        float cooldown = 0;
        if (mc.player.getMainHandStack().isEmpty()) cooldown = 0.9f;
        if (PlayerUtil.isSword(mc.player.getMainHandStack())) cooldown = 0.52f;
        if (toSprinting) cooldown -= 0.15f;
        return mc.gameRenderer.firstPersonRenderer.equipProgressMainHand > cooldown;
    }

    public boolean canCritical() {
        double effectiveJumpHeight = mc.player.getStepHeight();
        Vec3d jumpVec = new Vec3d(0, effectiveJumpHeight, 0);
        Vec3d allowedMovement = mc.player.adjustMovementForCollisions(jumpVec);

        boolean notCrit = mc.player.isInLava() || mc.player.isClimbing() || mc.player.isSubmergedIn(FluidTags.WATER) || mc.player.hasStatusEffect(StatusEffects.LEVITATION) || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) || mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || PlayerUtil.isPlayerInBlock(Blocks.COBWEB) || mc.player.isGliding() || mc.player.hasVehicle() || mc.player.getAbilities().flying || (allowedMovement.y < mc.player.getStepHeight() - 0.5 && mc.player.isOnGround() || mc.player.getVelocity().y == -0.005 && mc.player.isSubmergedInWater()) || mc.player.isOnGround() && !mc.options.jumpKey.isPressed() && !KillAura.INSTANCE.criticalType.isSelected("Только криты");
        return (notCrit || mc.player.fallDistance > 0f);
    }
}
