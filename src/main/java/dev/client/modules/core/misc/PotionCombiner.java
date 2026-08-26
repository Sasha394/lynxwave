package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class PotionCombiner extends Module {

    public PotionCombiner() {
        super("PotionCombiner", ModuleCategory.Misc, "Автоматически совмещает зелья в наковальне");
    }

    ModeElement mode = new ModeElement("Зелье", () -> true).set("Силы", "Скорости").defaultValue("Силы").register(this);
    CheckBox autoExp = new CheckBox("Авто Бутылочки опыта", () -> true).defaultValue(false).register(this);
    Slider dep = new Slider("Пополнять опыт до", () -> !autoExp.getEnabled()).set(5f, 100f, 1f).defaultValue(15f).register(this);

    private final TimerUtil timer = new TimerUtil();
    private String currentName = "";

    boolean autoRepair;
    float pitch;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.currentScreen instanceof AnvilScreen && !autoRepair) {
            movePotionsToAnvil();
            if (areBothSlotsFilledWithStrengthPotions() && mc.player.experienceLevel >= 5) {
                if (((AnvilScreenHandler) ((AnvilScreen) mc.currentScreen).getScreenHandler()).getLevelCost() <= 5)
                    takeResult();
                appendExclamation();
            }
        }

        if (mc.player.experienceLevel < 5 && findExp() != -1) autoRepair = true;

        if (autoExp.getEnabled() && autoRepair) {

            if (mc.currentScreen instanceof AnvilScreen) {
                mc.player.closeScreen();
            }

            if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
                int expSlot = findExp();

                if (expSlot != -1 && expSlot != mc.player.getInventory().getSelectedSlot() + 36) {
                    InventoryHelper.moveItem(expSlot, mc.player.getInventory().getSelectedSlot() + 36, true);
                } else if (expSlot == -1) {
                    autoRepair = false;
                }
            } else if (timer.isReached(300)) {
                sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                timer.reset();

                if (mc.player.experienceLevel >= dep.getValue()) {
                    autoRepair = false;
                }
            }
        }
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (autoRepair) {
            event.setPitch(80.0f);
            mc.player.setPitch(80.0f);
            mc.player.headYaw = mc.player.getYaw();
            this.pitch = 80.0f;
        }
    }

    private boolean areBothSlotsFilledWithStrengthPotions() {
        return isStrengthPotion(getSlotStack(0)) && isStrengthPotion(getSlotStack(1));
    }

    private void takeResult() {
        if (timer.isReached(100)) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
            timer.reset();
            currentName = "";
            if (mc.currentScreen instanceof AnvilScreen) {
                ((AnvilScreen) mc.currentScreen).nameField.setText("");
            }
        }
    }

    private boolean isStrengthPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof PotionItem)) return false;

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;

        for (StatusEffectInstance effect : contents.getEffects()) {
            if ((mode.isSelected("Силы") && effect.getEffectType().value() == StatusEffects.STRENGTH.value() || mode.isSelected("Скорости") && effect.getEffectType().value() == StatusEffects.SPEED.value())
                    && effect.getAmplifier() == 1) {
                return true;
            }
        }
        return false;
    }

    private void movePotionsToAnvil() {
        if (mc.player == null || mc.interactionManager == null || !(mc.currentScreen instanceof AnvilScreen)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            if (getItem(i) instanceof AirBlockItem) {
                if (timer.isReached(300)) {
                    swapOneItem(findStrengthPotionInInventory(), i);
                    timer.reset();
                }
            }
        }
    }

    private int findStrengthPotionInInventory() {
        if (mc.player == null || mc.player.currentScreenHandler == null) {
            return -1;
        }

        int slotCount = mc.player.currentScreenHandler.slots.size();

        for (int i = 5; i < slotCount; i++) {
            ItemStack stack = mc.player.currentScreenHandler.slots.get(i).getStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof PotionItem)) continue;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null) {
                for (StatusEffectInstance effect : contents.getEffects()) {
                    if ((mode.isSelected("Силы") && effect.getEffectType().value() == StatusEffects.STRENGTH.value()
                            || mode.isSelected("Скорости") && effect.getEffectType().value() == StatusEffects.SPEED.value())
                            && effect.getAmplifier() == 1) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private void appendExclamation() {
        if (mc.currentScreen instanceof AnvilScreen) {
            AnvilScreen anvil = (AnvilScreen) mc.currentScreen;
            if (currentName.isEmpty()) {
                currentName = anvil.nameField.getText();
            }
            currentName += "!";
            anvil.nameField.setText(currentName);
        }
    }

    private int findExp() {
        if (mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            return 45;
        }

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); ++i) {
            ItemStack stack = mc.player.getInventory().getMainStacks().get(i);
            if (stack.getItem() == Items.EXPERIENCE_BOTTLE) {
                return i < 9 ? 36 + i : i;
            }
        }
        return -1;
    }

    public void swapOneItem(int from, int to) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, to, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private Item getItem(int slotId) {
        return mc.player.currentScreenHandler.slots.get(slotId).getStack().getItem();
    }

    private ItemStack getSlotStack(int slotId) {
        return mc.player.currentScreenHandler.slots.get(slotId).getStack();
    }
}
