package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {
    public static Criticals INSTANCE;

    public Criticals() {
        super("Criticals", ModuleCategory.Combat, "Дает возможность давать криты, не прыгая");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Grim").defaultValue("Grim").register(this);

    @Subscribe
    public void onAttack(PlayerAttackEvent e) {
        if (mc.player.isTouchingWater()) return;

        if (mode.isSelected("Grim")) {
            if (mc.player.isGliding()) {
                Angle angle = RotationController.INSTANCE.getRotation().random(1e-3F);
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();

                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y + 0.0625, z, angle.getYaw(), angle.getPitch(), false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                        x, y, z, angle.getYaw(), angle.getPitch(), false, false));
                mc.player.fallDistance = MathUtil.getRandom(1e-5F, 1e-4F);
                return;
            }

            if (!mc.player.isOnGround() && mc.player.fallDistance == 0) {
                double yOffset = -(mc.player.fallDistance = MathUtil.getRandom(1e-5F, 1e-4F));
                Angle angle = RotationController.INSTANCE.getRotation().random(1e-3F);

                // grimBupass$$$
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                        mc.player.getX(),
                        mc.player.getY() + yOffset,
                        mc.player.getZ(),
                        angle.getYaw(),
                        angle.getPitch(),
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision
                ));
            }
        }
    }
}
