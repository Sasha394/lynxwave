package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.game.RotationUpdateEvent;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.types.EventType;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.SelectElements;
import dev.client.modules.core.combat.killaura.rotation.Angle;
import dev.client.modules.core.combat.killaura.rotation.RotationConfig;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.other.task.TaskPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class AutoPotion extends Module implements ClientApi {
    public static AutoPotion INSTANCE;

    public AutoPotion() {
        super("AutoPotion", ModuleCategory.Player, "Автоматически кидает выбранные бафы");
        INSTANCE = this;
    }

    private static final String S_STRENGTH = "Зелье силы";
    private static final String S_SPEED = "Зелье скорости";
    private static final String S_FIRE_RES = "Зелье огнестойкости";

    private static final float THROW_PITCH = 90f;
    private static final long THROW_COOLDOWN_MS = 500L;

    private final SelectElements throwSettings = new SelectElements("Кидать", () -> true)
            .set(S_STRENGTH, S_SPEED, S_FIRE_RES)
            .defaultValue(S_STRENGTH, S_SPEED, S_FIRE_RES)
            .register(this);

    private final CheckBox autoDisable = new CheckBox("Выключать после использования", () -> true)
            .defaultValue(false)
            .register(this);

    private final TimerUtil timer = new TimerUtil();
    private boolean throwing = false;

    private enum PotionType {
        STRENGTH(StatusEffects.STRENGTH, S_STRENGTH),
        SPEED(StatusEffects.SPEED, S_SPEED),
        FIRE_RESISTANCE(StatusEffects.FIRE_RESISTANCE, S_FIRE_RES);

        final RegistryEntry<StatusEffect> effect;
        final String settingName;

        PotionType(RegistryEntry<StatusEffect> effect, String settingName) {
            this.effect = effect;
            this.settingName = settingName;
        }

        boolean isEnabled(AutoPotion module) {
            return module.throwSettings.isSelected(settingName);
        }
    }

    @Override
    public void onDisabled() {
        throwing = false;
        RotationController.INSTANCE.clear();
        super.onDisabled();
    }

    @Subscribe
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.player == null || mc.world == null) return;

        if (!canThrow()) {
            throwing = false;
            return;
        }

        Angle throwAngle = new Angle(mc.player.getYaw(), THROW_PITCH);
        RotationConfig config = new RotationConfig(true, true);
        RotationController.INSTANCE.rotateTo(throwAngle, 3, config, TaskPriority.HIGH_IMPORTANCE_1, this);
        throwing = true;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!throwing) return;
        if (!timer.isReached(THROW_COOLDOWN_MS)) return;

        Angle current = RotationController.INSTANCE.getRotation();
        if (current == null || current.getPitch() < 80f) return;

        for (PotionType type : PotionType.values()) {
            if (!type.isEnabled(this)) continue;
            if (mc.player.hasStatusEffect(type.effect)) continue;

            int slot = findPotionSlot(type.effect);
            if (slot == -1) continue;

            throwPotion(slot);
            timer.reset();

            if (autoDisable.getEnabled()) {
                setEnabled(false);
            }
            return;
        }
    }

    private void throwPotion(int slot) {
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (slot < 9) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        } else {
            mc.interactionManager.clickSlot(0, slot, previousSlot, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.interactionManager.clickSlot(0, slot, previousSlot, SlotActionType.SWAP, mc.player);
        }
    }

    private int findPotionSlot(RegistryEntry<StatusEffect> effect) {
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.SPLASH_POTION)) continue;
            PotionContentsComponent comp = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (comp == null) continue;
            for (StatusEffectInstance eff : comp.getEffects()) {
                if (eff != null && effect.equals(eff.getEffectType())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean canThrow() {
        if (mc.player == null || mc.world == null) return false;

        boolean onGround = mc.player.isOnGround()
                || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.3, mc.player.getZ())).isSolid();
        if (!onGround) return false;
        if (mc.player.isClimbing()) return false;
        if (mc.player.hasVehicle()) return false;
        if (mc.player.getAbilities().flying) return false;
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return false;

        for (PotionType type : PotionType.values()) {
            if (type.isEnabled(this)
                    && !mc.player.hasStatusEffect(type.effect)
                    && findPotionSlot(type.effect) != -1) {
                return true;
            }
        }
        return false;
    }
}
