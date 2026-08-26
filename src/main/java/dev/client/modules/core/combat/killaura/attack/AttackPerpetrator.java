package dev.client.modules.core.combat.killaura.attack;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.other.UsingItemEvent;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

@Getter
public class AttackPerpetrator implements ClientApi {
    AttackHandler attackHandler = new AttackHandler();

    public void tick() {
        attackHandler.tick();
    }

    public void onPacket(PacketEvent e) {
        attackHandler.onPacket(e);
    }

    public void onUsingItem(UsingItemEvent e) {
        attackHandler.onUsingItem(e);
    }

    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class AttackPerpetratorConfigurable {
        LivingEntity target;
        Angle angle;
        float maximumRange;
        boolean shouldBreakShield, shouldUnPressShield, useDynamicCooldown, ignoreWalls;
        ModeElement criticalMode;
        Box box;
        ModeElement aimMode;

        public AttackPerpetratorConfigurable(LivingEntity target, Angle angle, float maximumRange, boolean breakShield, boolean unPressShield, boolean dynamicCooldown, boolean ignoreWalls, ModeElement aimMode, Box box, ModeElement criticalMode) {
            this.target = target;
            this.angle = angle;
            this.maximumRange = maximumRange;
            this.shouldBreakShield = breakShield;
            this.shouldUnPressShield = unPressShield;
            this.useDynamicCooldown = dynamicCooldown;
            this.ignoreWalls = ignoreWalls;
            this.criticalMode = criticalMode;
            this.box = box;
            this.aimMode = aimMode;
        }

        public boolean isOnlyCritical() {
            return isCriticalModeIndexSelected(0);
        }

        public boolean isSmartCritical() {
            return isCriticalModeIndexSelected(1);
        }

        private boolean isCriticalModeIndexSelected(int index) {
            if (criticalMode == null || criticalMode.getValue() == null) {
                return false;
            }
            if (criticalMode.getValues() == null || criticalMode.getValues().size() <= index) {
                return false;
            }
            return criticalMode.getValue().equalsIgnoreCase(criticalMode.getValues().get(index));
        }
    }
}
