package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.input.MotionEvent;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.helper.client.PlayerHelper;
import dev.client.api.nullcry.helper.entity.world.ItemUseHelper;
import dev.client.api.nullcry.helper.entity.world.SlotHelper;
import dev.client.api.nullcry.helper.player.PlayerUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.modules.settings.SelectElements;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ClickHelper extends Module {

    public ClickHelper() {
        super("ClickHelper", ModuleCategory.Misc, "Автоматически использует предметы по нажатию клавиш");
    }

    SelectElements mode = new SelectElements("Режим", () -> true).set("Эндер-перл", "Друг").defaultValue("Эндер-перл").register(this);
    KeyBind pearlKey = new KeyBind("Кнопка Эндер-перла", () -> mode.isSelected("Эндер-перл")).set(-1).defaultValue(-98).register(this);
    KeyBind friendKey = new KeyBind("Кнопка Друга", () -> mode.isSelected("Друг")).set(-1).defaultValue(-98).register(this);
    CheckBox onlySlots = new CheckBox("Только в слоте", () -> true).defaultValue(false).register(this);
    CheckBox legitUse = new CheckBox("Использовать легитно", () -> true).defaultValue(false).register(this);

    boolean throwPearl;
    long delay;

    @Subscribe
    public void onKey(KeyBindEvent event) {
        if (event.getKey() == pearlKey.getKey() && mode.isSelected("Эндер-перл")) {
            throwPearl = true;
        }
        if (event.getKey() == friendKey.getKey() && mode.isSelected("Друг")) {
            if (mc.targetedEntity instanceof PlayerEntity) {
                clickFriend();
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (!mode.isSelected("Эндер-перл")) return;
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (throwPearl && mode.isSelected("Эндер-перл")) {
            useEnderPearl();
            throwPearl = false;
        }
    }

    private void useEnderPearl() {
        if (onlySlots.getEnabled()) {
            int hbSlotCheck = SlotHelper.getItemInventoryOrHotBar(Items.ENDER_PEARL, true);
            if (hbSlotCheck == -1) {
                printClient("Переместите предмет эндер перл в слот.");
                return;
            }
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
            double remainingCooldown = PlayerUtil.getItemCooldown(Items.ENDER_PEARL);
            printClient(String.format("Подождите %s до использования Эндер-перла", PlayerHelper.formatTime(remainingCooldown)));
            return;
        }

        int hbSlot = SlotHelper.getItemInventoryOrHotBar(Items.ENDER_PEARL, true);
        int invSlot = SlotHelper.getItemInventoryOrHotBar(Items.ENDER_PEARL, false);

        if (hbSlot == -1 && invSlot == -1) {
            printClient("Эндер перл не найден в инвентаре.");
            return;
        }

        final int previousSlot = mc.player.getInventory().getSelectedSlot();
        final int finalHbSlot = hbSlot;
        final int finalInvSlot = invSlot;
        final boolean hwLegit = legitUse.getEnabled();

        Runnable useAction = () -> {
            if (!isEnabled()) {
                return;
            }

            int usedSlot = hwLegit
                    ? ItemUseHelper.spoofUseItemHwLegit(previousSlot, finalHbSlot, finalInvSlot, Hand.MAIN_HAND, true)
                    : ItemUseHelper.spoofUseItem(previousSlot, finalHbSlot, finalInvSlot, Hand.MAIN_HAND, true, legitUse.getEnabled());
            if (usedSlot == -1) {
                printClient("Не удалось использовать эндер перл.");
                return;
            }

            delay = System.currentTimeMillis();
        };

        useAction.run();
    }

    private void clickFriend() {
        String playerName = mc.targetedEntity.getName().getString();
        if (!PlayerHelper.isNameValid(playerName)) {
            printClient("Невозможно добавить бота в друзья, как бы не хотелось");
            return;
        }
        if (handlerClient().getFriendManager().isFriend(playerName)) {
            handlerClient().getFriendManager().delete(playerName);
            printClient(playerName + " удалён из друзей");
        } else {
            handlerClient().getFriendManager().add(playerName);
            printClient(playerName + " добавлен в друзья");
        }
    }


    @Override
    public void onDisabled() {
        throwPearl = false;
        delay = 0;
        super.onDisabled();
    }
}
