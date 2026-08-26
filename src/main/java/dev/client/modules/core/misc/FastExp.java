package dev.client.modules.core.misc;

import com.google.common.eventbus.Subscribe;
import dev.client.api.nullcry.events.core.network.UpdateEvent;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FastExp extends Module {

    public FastExp() {
        super("FastExp", ModuleCategory.Misc, "Устанавливает указанную задержку на бросок бутылька опыта");
    }

    CheckBox onlyPvP = new CheckBox("Только в пвп", () -> true).defaultValue(false).register(this);

    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        if (onlyPvP.getEnabled() && !isPvP()) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (isExperienceBottle(mainHand) || isExperienceBottle(offHand)) {
            mc.itemUseCooldown = 1;
        }
    }

    private boolean isExperienceBottle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
