package dev.client.api.nullcry.helper.entity.world;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import dev.client.api.nullcry.helper.other.TimerUtil;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.component.core.inventory.PlayerInventoryComponent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.experimental.UtilityClass;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public class InventoryHelper implements ClientApi {
    final int HOTBAR_SIZE = 9;
    final int PLAYER_INV_END = 36;
    final int INV_SIZE = 45;
    final int OFFHAND_SLOT = 40;

    public final TimerUtil timer = new TimerUtil();
    public Task current;

    public static void use(Item item) {
        use(item, ConnectionHelper.isHW());
    }

    public void inventorySwapClick(Item item) {
        if (!isNullCheck() || getItemIndex(item) == -1) return;

        int i;
        if (findItemHotBar(item)) {
            for (i = 0; i < HOTBAR_SIZE; ++i) {
                if (mc.player.getInventory().getStack(i).getItem() != item) continue;

                if (i != mc.player.getInventory().getSelectedSlot()) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(i));
                }
                mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                if (i != mc.player.getInventory().getSelectedSlot()) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
                }
                break;
            }
        } else {
            for (i = 0; i < PLAYER_INV_END; ++i) {
                if (mc.player.getInventory().getStack(i).getItem() != item) continue;

                int swapButton = mc.player.getInventory().getSelectedSlot() % 8 + 1;
                mc.interactionManager.clickSlot(0, i, swapButton, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(swapButton));
                mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
                mc.interactionManager.clickSlot(0, i, swapButton, SlotActionType.SWAP, mc.player);
                break;
            }
        }
    }

    public void moveItem(int from, int to) {
        if (!isNullCheck() || from == to || from == -1) return;

        from = toContainerIndex(from);
        if (ConnectionHelper.isFT()) {
            int finalFrom = from;
            PlayerInventoryComponent.addTask(() -> {
                clickSlotId(finalFrom, 0, SlotActionType.SWAP, false);
                clickSlotId(to, 0, SlotActionType.SWAP, false);
                clickSlotId(finalFrom, 0, SlotActionType.SWAP, false);
            });
        } else {
            clickSlotId(from, 0, SlotActionType.SWAP, false);
            clickSlotId(to, 0, SlotActionType.SWAP, false);
            clickSlotId(from, 0, SlotActionType.SWAP, false);
        }
    }

    public void moveItem(int from, int to, boolean air) {
        if (!isNullCheck() || from == to) return;
        mc.interactionManager.clickSlot(0, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(0, to, 0, SlotActionType.PICKUP, mc.player);
        if (air) {
            mc.interactionManager.clickSlot(0, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    public static void use(Item item, boolean smooth) {
        if (!isNullCheck()) return;

        int slot = findItemNoChanges(44, item);
        if (slot == -1 || mc.currentScreen != null) return;

        if (mc.player.getOffHandStack().getItem() == item) {
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            return;
        }

        boolean inHotbar = slot <= 8;

        if (smooth) {
            current = new Task(slot);
        } else {
            if (inHotbar) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            } else {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                timer.reset();
            }
        }
    }

    public int getItem(Item input, boolean inHotBar) {
        if (!isNullCheck()) return -1;
        int firstSlot = inHotBar ? 0 : HOTBAR_SIZE;
        int lastSlot = inHotBar ? HOTBAR_SIZE : PLAYER_INV_END;

        for (int i = firstSlot; i < lastSlot; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() instanceof AirBlockItem) continue;
            if (itemStack.getItem() == input) return i;
        }
        return -1;
    }

    public ItemStack byItem(Item item) {
        if (!isNullCheck()) return null;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem().equals(item)) return stack;
        }
        return null;
    }

    public int findItemSlot(Item item, boolean armor) {
        if (!isNullCheck()) return -1;

        if (armor) {
            for (ItemStack stack : dev.client.api.compat.InventoryCompat.armorItems(mc.player)) {
                if (stack.getItem() == item) return -2;
            }
        }
        int slot = -1;
        for (int i = 0; i < PLAYER_INV_END; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                slot = i;
                break;
            }
        }
        if (slot < HOTBAR_SIZE && slot != -1) slot += PLAYER_INV_END;
        return slot;
    }

    public int findItemNoChanges(final int endSlot, final Item item) {
        if (!isNullCheck()) return -1;
        for (int i = 0; i < endSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    public static int getElytraHotbarSlot() {
        if (!isNullCheck()) return -1;

        for (ItemStack stack : dev.client.api.compat.InventoryCompat.armorItems(mc.player)) {
            if (stack.getItem() == Items.ELYTRA) return -2;
        }
        int slot = -1;
        for (int i = 0; i < PLAYER_INV_END; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ELYTRA) {
                slot = i;
                break;
            }
        }
        if (slot < HOTBAR_SIZE && slot != -1) slot += PLAYER_INV_END;
        return slot;
    }

    public int getChestplate() {
        if (!isNullCheck()) return -1;
        for (int i = 0; i < mc.player.getInventory().size(); ++i) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            EquippableComponent equip = itemStack.get(DataComponentTypes.EQUIPPABLE);
            if (equip != null && equip.slot() == EquipmentSlot.CHEST) return i;
        }
        return -1;
    }

    public int findBestSlotHotBar() {
        int emptySlot = findEmptySlot();
        return emptySlot != -1 ? emptySlot : findNonSwordSlot();
    }

    public int findEmptySlot(boolean inHotBar) {
        if (!isNullCheck()) return -1;
        int start = inHotBar ? 0 : HOTBAR_SIZE;
        int end = inHotBar ? HOTBAR_SIZE : INV_SIZE;

        for (int i = start; i < end; ++i) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private int findEmptySlot() {
        if (!isNullCheck()) return -1;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()
                    && mc.player.getInventory().getSelectedSlot() != i) {
                return i;
            }
        }
        return -1;
    }

    public int findNonSwordSlot() {
        if (!isNullCheck()) return -1;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!PlayerUtil.isSword(stack)
                    && !stack.isOf(Items.ELYTRA)
                    && mc.player.getInventory().getSelectedSlot() != i) {
                return i;
            }
        }
        return -1;
    }

    public boolean findItemHotBar(Item item) {
        if (!isNullCheck()) return false;
        for (int i = 0; i < HOTBAR_SIZE; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return true;
        }
        return false;
    }

    public int getItem(final int endSlot, final Item ofType) {
        if (!isNullCheck()) return -1;
        int slot = -1;
        for (int i = 0; i < endSlot; i++) {
            if (mc.player.getInventory().getStack(i).getItem() != ofType) continue;
            slot = normalizeIndex(i);
        }
        return slot;
    }

    public int getItem(int maxSlots, Item item, int startSlot) {
        if (!isNullCheck()) return -1;
        for (int i = startSlot; i < maxSlots; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return normalizeIndex(i);
            }
        }
        return -1;
    }

    public static int getItemIndex(Item item) {
        if (!isNullCheck()) return -1;
        for (int i = 0; i < INV_SIZE; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    public int getItem(Item input) {
        if (!isNullCheck()) return -1;
        return IntStream.range(0, HOTBAR_SIZE)
                .filter(i -> mc.player.getInventory().getStack(i).getItem() == input)
                .findFirst()
                .orElse(-1);
    }

    public int getCountItem(Item item) {
        if (!isNullCheck() || mc.player.currentScreenHandler == null) return 0;
        return mc.player.currentScreenHandler.slots.stream()
                .filter(slot -> slot.getStack().getItem().equals(item))
                .mapToInt(slot -> slot.getStack().getCount())
                .sum();
    }

    public int getInventoryCount(Item item) {
        if (!isNullCheck()) return 0;
        return IntStream.range(0, INV_SIZE)
                .filter(i -> mc.player.getInventory().getStack(i).getItem().equals(item))
                .map(i -> mc.player.getInventory().getStack(i).getCount())
                .sum();
    }

    public Slot getSlot(Item item) {
        if (!isNullCheck() || mc.player.currentScreenHandler == null) return null;
        return mc.player.currentScreenHandler.slots.stream()
                .filter(slot -> slot.getStack().getItem().equals(item))
                .findFirst()
                .orElse(null);
    }

    public Slot getSlot(Predicate<ItemStack> filter) {
        if (!isNullCheck() || mc.player.currentScreenHandler == null) return null;
        return mc.player.currentScreenHandler.slots.stream()
                .filter(slot -> filter.test(slot.getStack()))
                .findFirst()
                .orElse(null);
    }

    public Slot getSlot(List<Item> items) {
        if (!isNullCheck() || mc.player.currentScreenHandler == null) return null;
        return mc.player.currentScreenHandler.slots.stream()
                .filter(slot -> items.contains(slot.getStack().getItem()))
                .findFirst()
                .orElse(null);
    }

    public Slot getSlots(Predicate<Slot> filter) {
        return slots().filter(filter).findFirst().orElse(null);
    }

    public Stream<Slot> slots(){
        return mc.player.currentScreenHandler.slots.stream();
    }

    public void closeScreen(boolean packet) {
        if (packet) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        else mc.player.closeHandledScreen();
    }

    public void clickSlot(Slot slot, int button, SlotActionType clickType, boolean packet) {
        if (slot != null) clickSlotId(slot.id, button, clickType, packet);
    }

    public void clickSlotId(int slotId, int buttonId, SlotActionType clickType, boolean packet) {
        clickSlotId(mc.player.currentScreenHandler.syncId, slotId, buttonId, clickType, packet);
    }

    public void clickSlotId(int windowId, int slotId, int buttonId, SlotActionType clickType, boolean sendPacket) {
        if (!isNullCheck()) return;

        if (sendPacket) {
            Int2ObjectMap<ItemStackHash> modifiedStacks = new Int2ObjectOpenHashMap<>();
            mc.player.networkHandler.sendPacket(
                    new ClickSlotC2SPacket(
                            windowId,
                            mc.player.currentScreenHandler.nextRevision(),
                            (short) slotId,
                            (byte) buttonId,
                            clickType,
                            modifiedStacks,
                            ItemStackHash.EMPTY
                    )
            );
        } else {
            mc.interactionManager.clickSlot(windowId, slotId, buttonId, clickType, mc.player);
        }
    }

    private boolean isHotbarIndex(int i) {
        return i >= 0 && i < HOTBAR_SIZE;
    }

    private int toContainerIndex(int idx) {
        return isHotbarIndex(idx) ? PLAYER_INV_END + idx : idx;
    }

    private int normalizeIndex(int i) {
        return (i == OFFHAND_SLOT) ? 45 : (isHotbarIndex(i) ? PLAYER_INV_END + i : i);
    }

    private boolean isNullCheck() {
        return mc != null && mc.player != null && mc.interactionManager != null;
    }

    public static class Task {
        final int slot;
        Task(int slot) { this.slot = slot; }
    }
}
