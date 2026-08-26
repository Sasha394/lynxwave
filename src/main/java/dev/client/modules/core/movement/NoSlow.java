package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.events.core.other.UsingItemEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.ModeElement;
import lombok.ToString;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@ToString
public class NoSlow extends Module {
    public static NoSlow INSTANCE;

    public static NoSlow getInstance() {
        return INSTANCE;
    }

    public NoSlow() {
        super("NoSlow", ModuleCategory.Movement, "Убирает замедление при взаимодействии с предметами");
    }

    ModeElement mode = new ModeElement("Режим", () -> true).set("Matrix", "Grim", "GrimNew", "FunTime Snow").defaultValue("Matrix").register(this);

    int ticks = 0;

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player.isUsingItem()) {
            ticks++;
        } else {
            ticks = 0;
        }
    }

    @Subscribe
    public void onNoSlow(UsingItemEvent event) {
        processNoSlowEvent(event);
    }

    private void processNoSlowEvent(UsingItemEvent event) {
        if (mc.player.isUsingItem()) {
            switch (mode.getValue()) {
                case "Matrix" -> handleMatrix(event);
                case "Grim" -> handleGrim(event);
                case "GrimNew" -> handleGrimNewMode(event);
                case "FunTime Snow" -> handleFunTimeSnow(event);
            }
        }
    }

    private void handleMatrix(UsingItemEvent event) {
        event.cancelled();
    }

    private void handleGrim(UsingItemEvent event) {
        boolean isUsingOffhand = mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.OFF_HAND;
        boolean isUsingMainhand = mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;

        if (mc.player.getItemUseTime() >= 25 || mc.player.getItemUseTime() <= 4 || mc.player.getOffHandStack().getItem() == Items.SHIELD) {
            return;
        }

        if (mc.player.isUsingItem() && !mc.player.hasVehicle()) {
            mc.interactionManager.syncSelectedSlot();

            if (isUsingOffhand && !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(mc.player.getOffHandStack().getItem()))) {
                int currentSlot = mc.player.getInventory().getSelectedSlot();
                sendPacket(new UpdateSelectedSlotC2SPacket((currentSlot + 1) % 9));
                sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                mc.player.setSprinting(false);
                event.cancelled();
            }

            if (isUsingMainhand && !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(mc.player.getMainHandStack().getItem()))) {
                sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));

                if (mc.player.getOffHandStack().getUseAction() == UseAction.NONE) {
                    event.cancelled();
                }
            }
            mc.interactionManager.syncSelectedSlot();
        }
    }

    private void handleGrimNewMode(UsingItemEvent event) {
        if ((mc.player.getOffHandStack().getUseAction() != UseAction.BLOCK || mc.player.getActiveHand() != Hand.MAIN_HAND) && (mc.player.getOffHandStack().getUseAction() != UseAction.EAT || mc.player.getActiveHand() != Hand.MAIN_HAND)) {
            if (mc.player.getActiveHand() == Hand.MAIN_HAND) {
                event.cancelled();
                sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            } else {
                event.cancelled();
                sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            }
        }
    }

    private void handleFunTimeSnow(UsingItemEvent e) {
        if (PlayerHelper.isOnCarpetOrSnow()) {
            e.cancelled();
        }
    }
}
