package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.api.nullcry.modules.settings.Slider;
import dev.client.modules.core.combat.killaura.attack.AttackHandler;
import dev.client.modules.core.combat.killaura.attack.AttackPerpetrator;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.movement.Sprint;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

public class TriggerBot extends Module {
    public static TriggerBot INSTANCE;

    public TriggerBot() {
        super("TriggerBot", ModuleCategory.Combat, "Автоматически атакует цель при пересечении её курсором");
    }

    public final Slider distance = new Slider("Дистанция", () -> true)
            .set(2.0f, 6.0f, 0.1f)
            .defaultValue(3.3f)
            .register(this);
    public final SelectElements filter = new SelectElements("Фильтр на", () -> true)
            .set("Игроков", "Мобов", "Животных", "Друзей", "Невидимых", "Голых", "Жителей")
            .defaultValue("Игроков", "Мобов")
            .register(this);
    public final ModeElement criticalType = new ModeElement("Режим критов", () -> true)
            .set("Только криты", "Умные криты")
            .defaultValue("Только криты")
            .register(this);
    public final CheckBox tpsSync = new CheckBox("Синхронизация с тиками", () -> true)
            .defaultValue(true)
            .register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        LivingEntity entity = getValidEntity();

        if (entity == null || mc.player == null || mc.interactionManager == null) return;
        if (!isWithinReach(entity)) return;

        AttackPerpetrator.AttackPerpetratorConfigurable config = createConfig(entity);
        AttackHandler attackHandler = handlerClient().getAttackPerpetrator().getAttackHandler();

        if (!attackHandler.canAttack(config, 0)) return;

        attack(entity);
        attackHandler.getAttackTimer().reset();
    }

    private void attack(Entity target) {
        if (!mc.player.isSwimming() && Sprint.INSTANCE != null) {
            Sprint.INSTANCE.tickStop = MathUtil.getRandom(1, 2);
        }
        if (!mc.player.isSwimming()) {
            mc.player.setSprinting(false);
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isWithinReach(LivingEntity entity) {
        double reach = distance.getValue();
        return mc.player.squaredDistanceTo(entity) <= reach * reach;
    }

    private AttackPerpetrator.AttackPerpetratorConfigurable createConfig(LivingEntity target) {
        Angle angle = RotationController.INSTANCE.getRotation();
        float reach = distance.getValue();
        Box box = target.getBoundingBox();
        return new AttackPerpetrator.AttackPerpetratorConfigurable(target, angle, reach, false, false, tpsSync.getEnabled(), false, null, box, criticalType);
    }

    private LivingEntity getValidEntity() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (entity instanceof LivingEntity living && isFilter(living)) {
                return living;
            }
        }
        return null;
    }

    private boolean isFilter(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive() || entity.isInvulnerable() || entity.getHealth() <= 0.0f) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity.age < 3) return false;

        if (entity instanceof PlayerEntity player) {
            if (!filter.get("Игроков").getEnabled()) return false;
            if (AntiBot.isBot(player)) return false;
            if (handlerClient().getFriendManager().isFriend(player.getName().getString())
                    && !filter.get("Друзей").getEnabled()) return false;

            boolean hasArmor = false;
            for (ItemStack armorPiece : dev.client.api.compat.InventoryCompat.armorItems(player)) {
                if (!armorPiece.isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }

            if (!hasArmor && !filter.get("Голых").getEnabled()) return false;

            if (player.isInvisible()) {
                if (!filter.get("Невидимых").getEnabled()) return false;
                if (!hasArmor && !filter.get("Голых").getEnabled()) return false;
            }
        }

        if (entity instanceof MobEntity && !filter.get("Мобов").getEnabled()) return false;
        if (entity instanceof AnimalEntity && !filter.get("Животных").getEnabled()) return false;
        return !(entity instanceof VillagerEntity) || filter.get("Жителей").getEnabled();
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
    }
}
