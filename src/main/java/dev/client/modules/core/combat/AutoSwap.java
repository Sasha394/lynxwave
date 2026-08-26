package dev.client.modules.core.combat;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.helper.entity.world.HandSwapHelper;
import dev.client.api.nullcry.helper.entity.world.InventoryHelper;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.Input;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.ModeElement;
import dev.client.api.nullcry.uiClient.notification.type.NotificationType;
import dev.client.api.nullcry.uiClient.notification.type.RenderType;
import dev.client.modules.core.combat.autoSwap.TripleSwapScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

public class AutoSwap extends Module {
    public static AutoSwap INSTANCE;

    public AutoSwap() {
        super("AutoSwap", ModuleCategory.Combat, "Автоматически меняет заданный предмет на выбранный");
    }

    ModeElement swapMode = new ModeElement("Режим свапа", () -> true).set("Двойной", "Тройной").defaultValue("Двойной").register(this);
    ModeElement swapType1 = new ModeElement("Предмет", () -> swapMode.isSelected("Двойной")).set("Тотем", "Щит", "Геплы", "Шар").defaultValue("Тотем").register(this);
    ModeElement swapType2 = new ModeElement("Свапать на", () -> swapMode.isSelected("Двойной")).set("Щит", "Геплы", "Шар", "Тотем").defaultValue("Щит").register(this);

    CheckBox enchantTotem = new CheckBox("Свапать только зачарованные тотемы", () -> (swapMode.isSelected("Двойной") && (swapType1.isSelected("Тотем") || swapType2.isSelected("Тотем"))) || swapMode.isSelected("Тройной")).defaultValue(false).register(this);
    CheckBox notificationSwap = new CheckBox("Отображать информацию о свапе", () -> true).defaultValue(true).register(this);
    KeyBind keySwap = new KeyBind("Кнопка свапа", () -> swapMode.isSelected("Двойной")).set(-1).register(this);
    KeyBind keyOpen = new KeyBind("Кнопка открытия", () -> swapMode.isSelected("Тройной")).set(-1).register(this);

    Input tripleLeft = new Input("Тройной левый", () -> false).register(this);
    Input tripleRight = new Input("Тройной правый", () -> false).register(this);
    Input tripleBottom = new Input("Тройной нижний", () -> false).register(this);

    private TripleSlot pendingSelection;
    final HandSwapHelper handSwapHelper = new HandSwapHelper();
    Text lastSwappedComponent = null;
    long lastSwapTime = 0;

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (swapMode.isSelected("Двойной") && event.getKey() == keySwap.getKey()) {
            swapDouble();
            return;
        }

        if (swapMode.isSelected("Тройной") && event.getKey() == keyOpen.getKey()) {
            if (mc.currentScreen instanceof TripleSwapScreen radialScreen) {
                mc.setScreen(null);
                swapTriple(radialScreen.getSelectedSlot());
            } else {
                openRadialScreen();
            }
        }
    }

    private void swapDouble() {
        ItemStack offhand = mc.player.getOffHandStack();
        Slot first = InventoryHelper.getSlot(getFilterForType(swapType1.getValue()));
        Slot second = InventoryHelper.getSlot(getFilterForType(swapType2.getValue()));

        if (first == null || second == null) return;

        Slot validSlot = !offhand.getItem().equals(first.getStack().getItem()) ? first : second;
        ItemStack intendedOffhand = validSlot.getStack().copy();

        handSwapHelper.swapHand(validSlot, Hand.OFF_HAND, false);
        showSwapped(intendedOffhand);
    }

    public void swapTriple(TripleSlot slot) {
        if (slot == null) {
            return;
        }

        ItemStack target = getTripleStack(slot);
        if (target.isEmpty()) {
            return;
        }

        Slot targetSlot = InventoryHelper.getSlot(getFilterForStack(target));
        if (targetSlot == null) {
            return;
        }

        handSwapHelper.swapHand(targetSlot, Hand.OFF_HAND, false);
        showSwapped(target);
    }

    private void showSwapped(ItemStack newItem) {
        this.lastSwappedComponent = newItem.getName();
        this.lastSwapTime = System.currentTimeMillis();

        if (notificationSwap.getEnabled()) {
            handlerClient().getNotificationManager().add(
                    this.lastSwappedComponent,
                    2,
                    NotificationType.INFO,
                    RenderType.WORLD,
                    newItem.copy()
            );
        }
    }

    private Predicate<ItemStack> getFilterForType(String type) {
        return switch (type) {
            case "Тотем" -> stack -> {
                if (stack.getItem() != Items.TOTEM_OF_UNDYING) return false;
                if (!enchantTotem.getEnabled()) return true;
                return stack.get(DataComponentTypes.ENCHANTMENTS) != null && !stack.get(DataComponentTypes.ENCHANTMENTS).isEmpty();
            };
            case "Щит" -> stack -> stack.getItem() == Items.SHIELD;
            case "Геплы" -> stack -> stack.getItem() == Items.GOLDEN_APPLE;
            case "Шар" -> stack -> stack.getItem() == Items.PLAYER_HEAD;
            default -> stack -> false;
        };
    }

    private Predicate<ItemStack> getFilterForStack(ItemStack stack) {
        if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
            return getFilterForType("Тотем");
        }
        Item item = stack.getItem();
        return candidate -> candidate.getItem() == item;
    }

    public void openRadialScreen() {
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new TripleSwapScreen(this));
    }

    public void beginSelection(TripleSlot slot) {
        if (mc.player == null) {
            return;
        }
        pendingSelection = slot;
        mc.setScreen(new InventoryScreen(mc.player));
    }

    public boolean isWaitingForSelection() {
        return pendingSelection != null;
    }

    public void cancelSelection() {
        pendingSelection = null;
    }

    public void acceptSelection(ItemStack stack) {
        if (pendingSelection == null) {
            return;
        }
        setTripleStack(pendingSelection, stack);
        pendingSelection = null;
        openRadialScreen();
    }

    public ItemStack getTripleStack(TripleSlot slot) {
        String stored = getTripleSetting(slot).getValue();
        if (stored == null || stored.isBlank()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(stored);
        if (id == null || !Registries.ITEM.getIds().contains(id)) {
            return ItemStack.EMPTY;
        }
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    public void clearTripleStack(TripleSlot slot) {
        getTripleSetting(slot).set("");
    }

    public boolean hasTripleStack(TripleSlot slot) {
        return !getTripleStack(slot).isEmpty();
    }

    public void setTripleStack(TripleSlot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            clearTripleStack(slot);
            return;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        getTripleSetting(slot).set(id.toString());
    }

    private Input getTripleSetting(TripleSlot slot) {
        return switch (slot) {
            case LEFT -> tripleLeft;
            case RIGHT -> tripleRight;
            case BOTTOM -> tripleBottom;
        };
    }

    public enum TripleSlot {
        LEFT,
        RIGHT,
        BOTTOM
    }
}
