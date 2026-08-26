package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.helper.player.BoostHelper;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.misc.ElytraHelper;
import dev.client.modules.core.movement.ElytraBooster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixins implements ClientApi {

    @Shadow
    @Nullable
    private LivingEntity shooter;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d getRotationVectorHook(LivingEntity instance, Operation<Vec3d> original) {
        if (shooter == mc.player && shooter.isGliding()) {
            return RotationController.INSTANCE.getMoveRotation().toVector();
        }
        return original.call(instance);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void elytraBooster$overrideVelocity(LivingEntity instance, Vec3d vanillaNewVelocity) {
        // ElytraBooster имеет приоритет над встроенным бустом ElytraHelper
        if (ElytraBooster.INSTANCE != null && ElytraBooster.INSTANCE.isEnabled()
                && instance instanceof PlayerEntity boosterPlayer && boosterPlayer.isGliding()) {
            MinecraftClient mcb = MinecraftClient.getInstance();
            if (mcb != null && mcb.player != null && boosterPlayer.getId() == mcb.player.getId()) {
                instance.setVelocity(ElytraBooster.INSTANCE.boost(instance.getVelocity()));
                return;
            }
        }

        ElytraHelper mod = ElytraHelper.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            instance.setVelocity(vanillaNewVelocity);
            return;
        }

        if (!(instance instanceof PlayerEntity player) || !player.isGliding()) {
            instance.setVelocity(vanillaNewVelocity);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || player.getId() != mc.player.getId()) {
            instance.setVelocity(vanillaNewVelocity);
            return;
        }

        final boolean boostEnabled = mod.getElytraBoost().getEnabled();
        if (!boostEnabled) {
            instance.setVelocity(vanillaNewVelocity);
            return;
        }

        final Vec3d dir = instance.getRotationVector();
        final Vec3d cur = instance.getVelocity();

        double speedSetting = mod.getElytraSpeed().getValue();
        Vec3d motion;

        if (Math.abs(speedSetting - 1.0D) < 1e-9) {
            double countableSpeed = BoostHelper.computeBoost(MotionEvent.LAST_YAW, MotionEvent.LAST_PITCH);
            motion = cur.add(
                    dir.x * 0.1D + (dir.x * countableSpeed - cur.x) * 0.5D,
                    dir.y * 0.1D + (dir.y * countableSpeed - cur.y) * 0.5D,
                    dir.z * 0.1D + (dir.z * countableSpeed - cur.z) * 0.5D
            );
        } else {
            Vec3d boost = new Vec3d(
                    speedSetting / 2.0D + 0.3D,
                    speedSetting / 2.0D + 0.3D,
                    speedSetting / 2.0D + 0.3D
            );

            motion = cur.add(
                    dir.x * 0.1D + (dir.x * 1.5D - cur.x) * 0.5D,
                    dir.y * 0.1D + (dir.y * 1.5D - cur.y) * 0.5D,
                    dir.z * 0.1D + (dir.z * 1.5D - cur.z) * 0.5D
            ).multiply(boost);
        }

        instance.setVelocity(motion);
    }
}
