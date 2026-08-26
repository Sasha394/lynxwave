package dev.client.api.nullcry.uiClient.autobuy.items.list;

import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuyItemSettings;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuySettingsManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;

public class UnbreakableItem implements AutoBuyableItem {
    private final String displayName;
    private final Item material;
    private final int price;
    private final List<Text> loreTexts;
    private final AutoBuyItemSettings settings;
    private boolean enabled = true;

    public UnbreakableItem(String displayName, Item material, int price, List<Text> loreTexts) {
        this.displayName = displayName;
        this.material = material;
        this.price = price;
        this.loreTexts = loreTexts;
        this.settings = new AutoBuyItemSettings(price, material, displayName);
        AutoBuySettingsManager.getInstance().loadSettings(displayName, this.settings);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(material);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        if (loreTexts != null && !loreTexts.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("HideFlags", 127);
        nbt.putBoolean("Unbreakable", true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    @Override
    public int getPrice() {
        return price;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public AutoBuyItemSettings getSettings() {
        return settings;
    }
}
