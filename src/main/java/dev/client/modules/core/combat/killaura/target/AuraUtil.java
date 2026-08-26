
package dev.client.modules.core.combat.killaura.target;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.player.EnchantmentUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class AuraUtil implements ClientApi {

    public double fixHealth(LivingEntity e) {
        double hp = PlayerHelper.getHealth(e);

        if (e instanceof PlayerEntity p) {
            double armor = AuraUtil.AttributePointer.getArmor(p);
            return hp * (1.0 + armor / 20.0);
        }
        return hp;
    }

    public double getViewFov(LivingEntity entity) {
        Vec3d playerVec = mc.player.getRotationVector();
        Vec3d targetVec = dev.client.api.compat.EntityCompat.pos(entity).subtract(dev.client.api.compat.EntityCompat.pos(mc.player)).normalize();
        double dotProduct = playerVec.dotProduct(targetVec);
        return Math.acos(dotProduct) * (180 / Math.PI);
    }

    public static boolean isInFov(LivingEntity target, float baseFov) {
        double fov = MathUtil.calculateFOVFromCamera(target);
        return (Math.abs(fov) <= baseFov);
    }

    public static boolean isInSnapRange(LivingEntity target, float snapFov) {
        if (target == null || mc.player == null) return false;
        return isInFov(target, snapFov);
    }

    public boolean inFront(LivingEntity e) {
        return isInFov(e, 65f);
    }

    public double fovValue(LivingEntity e) {
        return getViewFov(e);
    }

    public static class AttributePointer {
        public static double getArmor(PlayerEntity player) {
            double armorValue = 0.0;
            for (int i = 0; i < 4; ++i) {
                ItemStack armorItem = switch (i) {
                    case 0 -> player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);
                    case 1 -> player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS);
                    case 2 -> player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
                    default -> player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
                };
                if (!PlayerUtil.isArmor(armorItem)) continue;
                armorValue += getProtection(armorItem);
            }
            return armorValue;
        }

        public static double getProtection(ItemStack stack) {
            if (!PlayerUtil.isArmor(stack)) return 0.0;

            int protLvl = EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            return PlayerUtil.getArmorProtection(stack) + protLvl * 0.25;
        }

        public static double getHealth(LivingEntity entity) {
            if (entity instanceof PlayerEntity player) {
                return (player.getHealth() + player.getAbsorptionAmount()) * (1.0 + getArmor(player) / 20.0);
            }
            return entity.getHealth() + entity.getAbsorptionAmount();
        }
    }
}
