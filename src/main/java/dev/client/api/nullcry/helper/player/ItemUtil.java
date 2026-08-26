package dev.client.api.nullcry.helper.player;

import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.ConnectionHelper;
import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

@UtilityClass
public class ItemUtil implements ClientApi {

    public int getPrice(ItemStack stack) {
        try {
            Item.TooltipContext context = Item.TooltipContext.DEFAULT;
            List<Text> itemTooltip = stack.getTooltip(context, null, TooltipType.BASIC);

            String sell = ConnectionHelper.isSpookyTime() ? "Цена" : ConnectionHelper.isHW() ? "Цена" : "Ценa";
            for (Text str : itemTooltip) {
                if (str.getString().contains(sell)) {
                    if (ConnectionHelper.isFT()) {
                        return Integer.parseInt(str.getString().replace("$ Ценa $", "").replace(" ", "").replace(",", ""));
                    } else if (ConnectionHelper.isHW()) {
                        return Integer.parseInt(str.getString().replace("▍", "").replace("¤", "").replace("Цена", "").replace(" ", "").replace(",", "").replace(":", "").replace("шага", ""));
                    } else if (ConnectionHelper.isFunSky()) {
                        return Integer.parseInt(str.getString().replace("$", "").replace("Цена", "").replace(":", "").replace("$ Цена $", "").replace(" ", "").replace(",", ""));
                    } else {
                        return Integer.parseInt(str.getString().replace("Цена", "").replace("$", "").replace(": ", "").replace(" ", "").replace(",", "").trim());
                    }
                }
            }
        } catch (NumberFormatException number) {
            new ClientApi(){}.debug(number);
            return -1;
        }
        return -1;
    }

    public boolean isRare(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();

        return name.contains("★")
                || name.contains("крушителя")
                || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                || stack.getItem() == Items.NETHERITE_INGOT
                || stack.getItem() == Items.NETHERITE_SCRAP
                || stack.getItem() == Items.PLAYER_HEAD
                || stack.getItem() == Items.SHULKER_BOX
                || (stack.getItem() == Items.TOTEM_OF_UNDYING && stack.isEnchantable());
    }
}
