package dev.client.modules.core.movement;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.game.TickEvent;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.modules.settings.Slider;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

public class AirStuck extends Module {
    public static AirStuck INSTANCE;

    private static final String MODE_VANILLA = "Обычный";
    private static final String MODE_BUG = "Bug";

    private final ModeElement mode = new ModeElement("Режим", () -> true)
            .set(MODE_VANILLA, MODE_BUG)
            .defaultValue(MODE_VANILLA)
            .register(this);

    private final CheckBox cancelMovement = new CheckBox("Отменять движение", () -> mode.isSelected(MODE_VANILLA))
            .defaultValue(true)
            .register(this);

    private final CheckBox autoSwap = new CheckBox("Автосмена элитры", () -> mode.isSelected(MODE_BUG))
            .defaultValue(false)
            .register(this);

    private final Slider speed = new Slider("Скорость", () -> mode.isSelected(MODE_BUG))
            .set(0.1f, 10.0f, 0.1f)
            .defaultValue(2.0f)
            .register(this);

    private Vec3d freezePosition = Vec3d.ZERO;
    private boolean isElytraState;

    public AirStuck() {
        super("AirStuck", ModuleCategory.Movement, "Позволяет зависать в воздухе");
        INSTANCE = this;
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.player != null) {
            freezePosition = dev.client.api.compat.EntityCompat.pos(mc.player);
            isElytraState = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        freezePosition = Vec3d.ZERO;
        if (mc.player != null && isElytraState && autoSwap.getEnabled()) {
            swap(false);
        }
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (!mode.isSelected(MODE_VANILLA)) return;
        if (mc.player == null || freezePosition == Vec3d.ZERO) return;
        if (cancelMovement.getEnabled()) {
            event.cancelled();
        }
        mc.player.setPosition(freezePosition);
        mc.player.setVelocity(Vec3d.ZERO);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!mode.isSelected(MODE_BUG)) return;
        if (mc.player == null) return;

        isElytraState = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

        if (isElytraState && autoSwap.getEnabled()) {
            swap(true);
        }

        if (!mc.player.isGliding()) {
            if (mc.options.forwardKey.isPressed()) {
                float yaw = mc.player.getYaw();
                double motionX = -Math.sin(Math.toRadians(yaw)) * speed.getValue() * 0.1;
                double motionZ = Math.cos(Math.toRadians(yaw)) * speed.getValue() * 0.1;
                mc.player.setVelocity(motionX, 0, motionZ);
            } else {
                mc.player.setVelocity(0, 0, 0);
            }
        }
    }

    @Subscribe
    public void onPacket(PacketEvent event) {
        if (!mode.isSelected(MODE_BUG)) return;
        if (mc.player == null || mc.player.isGliding() || !event.isSend()) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket || event.getPacket() instanceof UpdatePlayerAbilitiesC2SPacket) {
            event.cancelled();
        }
    }

    private void swap(boolean toChestplate) {
        int slot = toChestplate ? findChestPlate() : findItem(Items.ELYTRA);
        if (slot != -1) {
            moveItem(slot, 6);
        }
    }

    private int findChestPlate() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() == Items.ELYTRA) continue;

            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable != null && equippable.slot() == EquipmentSlot.CHEST) {
                String itemId = stack.getItem().toString().toLowerCase();
                if (itemId.contains("chestplate") || itemId.contains("tunic") || itemId.contains("jacket")) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    private int findItem(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    private void moveItem(int slot, int targetSlot) {
        if (mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, targetSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
    }
}
