package dev.client.api.nullcry.uiClient.autobuy.util.krushprovider;

import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.items.defaultsetpricec.Defaultpricec;
import dev.client.api.nullcry.uiClient.autobuy.items.list.KrushItems;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class KrushProvider {

    public static List<AutoBuyableItem> getKrush() {
        List<AutoBuyableItem> krush = new ArrayList<>();
        krush.add(new KrushItem("Шлем Крушителя", Items.NETHERITE_HELMET, KrushItems.getHelmet(), Defaultpricec.getPrice("Шлем крушителя")));
        krush.add(new KrushItem("Нагрудник Крушителя", Items.NETHERITE_CHESTPLATE, KrushItems.getChestplate(), Defaultpricec.getPrice("Нагрудник крушителя")));
        krush.add(new KrushItem("Поножи Крушителя", Items.NETHERITE_LEGGINGS, KrushItems.getLeggings(), Defaultpricec.getPrice("Поножи крушителя")));
        krush.add(new KrushItem("Ботинки Крушителя", Items.NETHERITE_BOOTS, KrushItems.getBoots(), Defaultpricec.getPrice("Ботинки крушителя")));
        krush.add(new KrushItem("Меч Крушителя", Items.NETHERITE_SWORD, KrushItems.getSword(), Defaultpricec.getPrice("Меч крушителя")));
        krush.add(new KrushItem("Кирка Крушителя", Items.NETHERITE_PICKAXE, KrushItems.getPickaxe(), Defaultpricec.getPrice("Кирка крушителя")));
        krush.add(new KrushItem("Арбалет Крушителя", Items.CROSSBOW, KrushItems.getCrossbow(), Defaultpricec.getPrice("Арбалет крушителя")));
        krush.add(new KrushItem("Трезубец Крушителя", Items.TRIDENT, KrushItems.getTrident(), Defaultpricec.getPrice("Трезубец крушителя")));
        krush.add(new KrushItem("Булава Крушителя", Items.MACE, KrushItems.getMace(), Defaultpricec.getPrice("Булава крушителя")));
        return krush;
    }


}