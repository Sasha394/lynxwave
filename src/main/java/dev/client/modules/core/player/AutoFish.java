package dev.client.modules.core.player;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

public class AutoFish extends Module {

    public AutoFish() {
        super("AutoFish", ModuleCategory.Player, "Автоматически вылавливает рыбку");
    }

    CheckBox rodSave = new CheckBox("Сохранять удочку", () -> true).defaultValue(true).register(this);
    CheckBox changeRod = new CheckBox("Свапать на новую удочку", () -> true).defaultValue(false).register(this);

    private final TimerUtil timeout = new TimerUtil();
    private final TimerUtil cooldown = new TimerUtil();

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (event.isReceive()) {
            if (event.getPacket() instanceof PlaySoundS2CPacket playSoundS2CPacket) {
                if (playSoundS2CPacket.getSound().value().equals(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) && mc.player.fishHook != null && mc.player.fishHook.squaredDistanceTo(playSoundS2CPacket.getX(), playSoundS2CPacket.getY(), playSoundS2CPacket.getZ()) < 4f) {
                    catchFish();
                }
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player.getMainHandStack().getItem() instanceof FishingRodItem) {
            if (mc.player.getMainHandStack().getDamage() > 52) {
                if (rodSave.getEnabled() && !changeRod.getEnabled()) {
                    toggle();
                    printClient("Удочка почти сломалась!");
                } else if (changeRod.getEnabled() && getRodSlot() != -1) {
                    printClient("Свапнулся на новую удочку");
                    InventoryHelper.inventorySwapClick(Items.FISHING_ROD);
                    cooldown.reset();
                } else {
                    toggle();
                    printClient("Удочка почти сломалась!");
                }
            }
        }

        if (!cooldown.isReached(1000)) return;

        if (timeout.isReached(45000) && mc.player.getMainHandStack().getItem() instanceof FishingRodItem) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            timeout.reset();
            cooldown.reset();
        }
    }

    private void catchFish() {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        try {
            Thread.sleep((int) MathUtil.random(899, 1399));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        timeout.reset();
    }

    private int getRodSlot() {
        for (int i = 0; i < 9; i++) {
            final ItemStack item = mc.player.getInventory().getStack(i);
            if (item.getItem() == Items.FISHING_ROD && item.getDamage() < 52) return i;
        }
        return -1;
    }
}
