package dev.client.modules.core.combat.killaura.attack;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.modules.core.combat.Criticals;
import dev.client.modules.core.movement.AirStuck;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;

public class IdealHitUtil implements ClientApi {

    public IdealHitUtil() {
        LynxWave.getInstance().getEventBus().register(this);
    }

    public boolean cooldownIsReached(boolean toSprinting) {
        if (mc.player == null) return false;

        float requiredProgress = toSprinting ? 0.85f : 0.95f;
        return mc.player.getAttackCooldownProgress(0.0f) >= requiredProgress;
    }

    public boolean canCritical() {
        if (mc.player == null || mc.world == null) return false;

        boolean slowFalling = mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        boolean criticalsEnabled = Criticals.INSTANCE != null && Criticals.INSTANCE.isEnabled();
        boolean airStuckEnabled = AirStuck.INSTANCE != null && AirStuck.INSTANCE.isEnabled();

        if ((!criticalsEnabled && slowFalling)
                || PlayerUtil.isPlayerInBlock(Blocks.COBWEB)
                || (slowFalling && mc.player.isTouchingWater())
                || mc.player.isGliding()
                || airStuckEnabled
                || mc.player.isClimbing()
                || mc.player.isInLava()
                || mc.player.isSubmergedInWater()
                || mc.player.getAbilities().flying
                || mc.player.hasVehicle()
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return true;
        }

        if (mc.player.isOnGround()
                || mc.player.isSprinting()
                || mc.player.getVelocity().y > 0.0) {
            return false;
        }

        double motionY = mc.player.getVelocity().y;
        return motionY < -0.08D;
    }
}
