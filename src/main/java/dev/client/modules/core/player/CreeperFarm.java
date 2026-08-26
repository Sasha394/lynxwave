package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationConfig;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.other.task.TaskPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class CreeperFarm extends Module {

    private boolean retreating;
    private Vec3d retreatTarget;

    public CreeperFarm() {
        super("CreeperFarm", ModuleCategory.Player, "Автоматически убивает криперов на фармилке");
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!FabricLoader.getInstance().isModLoaded("baritone")) return;

        LivingEntity creeper = findClosestCreeper();
        if (creeper != null) {
            double distance = dev.client.api.compat.EntityCompat.pos(mc.player).distanceTo(dev.client.api.compat.EntityCompat.pos(creeper));
            if (distance <= 3.3) {
                Angle rot = calculateCreeperRotation(creeper);
                RotationController.INSTANCE.rotateTo(rot, RotationConfig.DEFAULT, TaskPriority.STANDARD, this);

                if (mc.player.getAttackCooldownProgress(1.0F) >= 0.9) {
                    mc.interactionManager.attackEntity(mc.player, creeper);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    Vec3d playerPos = dev.client.api.compat.EntityCompat.pos(mc.player);
                    Vec3d creeperPos = dev.client.api.compat.EntityCompat.pos(creeper);
                    Vec3d away = playerPos.subtract(creeperPos).normalize();
                    retreatTarget = playerPos.add(away.multiply(4.0));
                    retreating = true;
                }
            }
        }
    }

    private Angle calculateCreeperRotation(LivingEntity target) {
        Vec3d toCreeper = new Vec3d(
                target.getX(),
                target.getY() + (mc.player.distanceTo(target) < 2.0F ? 0.5F : target.getEyeHeight(target.getPose())),
                target.getZ()
        );
        double dx = toCreeper.x;
        double dy = toCreeper.y;
        double dz = toCreeper.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F + (float) MathUtil.getRandom(-2.0, 2.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz))) + (float) MathUtil.getRandom(-1.0, 1.0);
        return new Angle(yaw, pitch);
    }

    private Angle calculateRotationToward(Vec3d targetPos) {
        Vec3d playerEyes = new Vec3d(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                mc.player.getZ()
        );
        Vec3d toPoint = targetPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0).subtract(playerEyes);
        double dx = toPoint.x;
        double dy = toPoint.y;
        double dz = toPoint.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F + (float) MathUtil.getRandom(-2.0, 2.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz))) + (float) MathUtil.getRandom(-1.0, 1.0);
        return new Angle(yaw, pitch);
    }

    private LivingEntity findClosestCreeper() {
        LivingEntity closest = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (e instanceof CreeperEntity) {
                double d = dev.client.api.compat.EntityCompat.pos(mc.player).distanceTo(dev.client.api.compat.EntityCompat.pos(e));
                if (d <= 50.0 && Math.abs(mc.player.getY() - e.getY()) < 4.0 && d < bestDist) {
                    bestDist = d;
                    closest = (LivingEntity) e;
                }
            }
        }

        return closest;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.player == null) return;

        if (FabricLoader.getInstance().isModLoaded("baritone")) {
            mc.player.networkHandler.sendChatMessage("#follow entity creeper");
            mc.player.networkHandler.sendChatMessage("#allowBreak false");
        } else {
            printClient("Для работы " + getName() + " нужен мод baritone");
            toggle();
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        if (mc.player != null && FabricLoader.getInstance().isModLoaded("baritone")) {
            mc.player.networkHandler.sendChatMessage("#stop");
        }
    }
}
