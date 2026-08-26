package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;

public class AutoTotem extends Module {
    public static AutoTotem INSTANCE;

    private final Slider health = new Slider("Здоровье", () -> true)
            .set(1f, 20f, 0.1f).defaultValue(4f).register(this);

    private final Slider healthOnElytra = new Slider("Здоровье на элитре", () -> true)
            .set(1f, 20f, 0.1f).defaultValue(11f).register(this);

    private final CheckBox crystalsCheck = new CheckBox("Работать при кристаллах", () -> true)
            .defaultValue(false).register(this);

    private final CheckBox slowness = new CheckBox("Замедление", () -> true)
            .defaultValue(false).register(this);

    private final Slider slownessDuration = new Slider("Длительность замедления", () -> slowness.getEnabled())
            .set(0f, 160f, 1f).defaultValue(100f).register(this);

    private int swapBackItem = -1;
    private int cooldownTicks = 0;
    private final TimerUtil totemTimer = new TimerUtil();
    private long lastSwapAt = 0L;

    public AutoTotem() {
        super("AutoTotem", ModuleCategory.Combat, "Автоматически ставит тотем");
        INSTANCE = this;
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;
        if (cooldownTicks > 0) cooldownTicks--;
        updateSwap();
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (mc.player == null || !event.isReceive()) return;
        if (event.getPacket() instanceof EntityStatusS2CPacket packet
                && packet.getStatus() == 35
                && packet.getEntity(mc.world) == mc.player) {
            cooldownTicks = 5;
        }
    }

    private boolean condition() {
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;

        boolean crystalsNear = false;
        if (crystalsCheck.getEnabled()) {
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if (mc.player.getY() >= entity.getY()
                        && mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) <= 8) {
                    crystalsNear = true;
                    break;
                }
            }
        }

        float total = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean lowHp = total <= health.getValue();
        boolean lowHpOnElytra = total <= healthOnElytra.getValue()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

        return lowHp || lowHpOnElytra || (crystalsCheck.getEnabled() && crystalsNear);
    }

    private void updateSwap() {
        ItemStack offHand = mc.player.getOffHandStack();
        int totemSlot = searchItem(Items.TOTEM_OF_UNDYING);

        if (condition() && totemTimer.isReached(300) && totemSlot != -1
                && offHand.getItem() != Items.TOTEM_OF_UNDYING) {
            if (swapBackItem == -1 && !offHand.isEmpty()) {
                swapBackItem = totemSlot;
            }
            swapTotem(totemSlot);
            totemTimer.reset();
        }

        if (!condition() && swapBackItem != -1 && cooldownTicks == 0) {
            int back = swapBackItem;
            swapTotem(back);
            swapBackItem = -1;
        }
    }

    private void swapTotem(int slot) {
        if (slowness.getEnabled()) {
            long now = System.currentTimeMillis();
            long delay = slownessDuration.getValue().longValue();
            if (now - lastSwapAt < delay) return;
            lastSwapAt = now;
        }

        if (slot >= 0 && slot <= 8) {
            mc.interactionManager.clickSlot(0, 45, slot, SlotActionType.SWAP, mc.player);
        } else if (slot > 8 && slot <= 45) {
            mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
        }
    }

    private int searchItem(net.minecraft.item.Item item) {
        var handler = mc.player.currentScreenHandler;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).getStack().getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    public void reset() {
        swapBackItem = -1;
        cooldownTicks = 0;
        lastSwapAt = 0L;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        reset();
    }
}
