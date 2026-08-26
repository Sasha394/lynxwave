package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;

public class DogFly extends Module {
    public static DogFly INSTANCE;

    Slider cooldown = new Slider("Задержка феера", () -> true)
            .set(200f, 2000f, 50f).defaultValue(550f).register(this);

    TimerUtil timer = new TimerUtil();

    public DogFly() {
        super("DogFly", ModuleCategory.Movement, "Полёт с нагрудником через свап на элитру");
        INSTANCE = this;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        int elytraSlot = findItem(Items.ELYTRA);
        int fireworkSlot = findItem(Items.FIREWORK_ROCKET);

        if (elytraSlot == -1) {
            printClient(Formatting.RED + "Нет элитры в инвентаре");
            setEnabled(false);
            return;
        }
        if (fireworkSlot == -1) {
            printClient(Formatting.RED + "Нет феерверков в инвентаре");
            setEnabled(false);
            return;
        }

        if (mc.player.isOnGround() && !mc.player.isInLava() && !mc.player.isSwimming()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            mc.player.jump();
        }

        if (!timer.isReached((long) cooldown.getValue().floatValue())) return;
        if (mc.player.isOnGround() || mc.player.isSwimming() || mc.player.hasStatusEffect(StatusEffects.LEVITATION)) return;

        try {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }

            if (elytraSlot >= 0 && elytraSlot <= 8) {
                mc.interactionManager.clickSlot(0, 6, elytraSlot, SlotActionType.SWAP, mc.player);
                sendPacketSilent(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                useFirework(fireworkSlot);
                mc.interactionManager.clickSlot(0, 6, elytraSlot, SlotActionType.SWAP, mc.player);
            } else if (elytraSlot >= 9 && elytraSlot <= 45) {
                mc.interactionManager.clickSlot(0, elytraSlot, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                sendPacketSilent(new CloseHandledScreenC2SPacket(0));
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                useFirework(fireworkSlot);
                mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, elytraSlot, 8, SlotActionType.SWAP, mc.player);
            }

            timer.reset();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void useFirework(int slot) {
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (slot >= 0 && slot <= 8) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            return;
        }
        mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
        sendPacketSilent(new CloseHandledScreenC2SPacket(0));
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(8));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
    }

    private int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (!st.isEmpty() && st.getItem() == item) return i;
        }
        return -1;
    }
}
