package dev.client.api.nullcry.uiClient.autobuy.originalitems;

import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.items.list.*;
import dev.client.api.nullcry.uiClient.autobuy.settings.AutoBuySettingsManager;
import dev.client.api.nullcry.uiClient.autobuy.util.krushprovider.KrushProvider;

import java.util.ArrayList;
import java.util.List;

public class ItemRegistry {
    private static List<AutoBuyableItem> allItems = null;
    private static List<AutoBuyableItem> krushItems = null;
    private static List<AutoBuyableItem> talismanItems = null;
    private static List<AutoBuyableItem> sphereItems = null;
    private static List<AutoBuyableItem> miscItems = null;
    private static List<AutoBuyableItem> donatorItems = null;
    private static List<AutoBuyableItem> potionItems = null;
    private static boolean settingsLoaded = false;

    public static void ensureSettingsLoaded() {
        if (!settingsLoaded) {
            settingsLoaded = true;
        }
    }

    private static void ensureInitialized() {
        if (allItems != null) {
            return;
        }

        krushItems = KrushProvider.getKrush();
        talismanItems = TalismanProvider.getTalismans();
        sphereItems = SphereProvider.getSpheres();
        miscItems = MiscProvider.getMisc();
        donatorItems = DonatorProvider.getDonator();
        potionItems = PotionProvider.getPotions();

        allItems = new ArrayList<>();
        allItems.addAll(krushItems);
        allItems.addAll(talismanItems);
        allItems.addAll(sphereItems);
        allItems.addAll(miscItems);
        allItems.addAll(donatorItems);
        allItems.addAll(potionItems);

        for (AutoBuyableItem item : allItems) {
            AutoBuySettingsManager.getInstance().loadSettings(item.getDisplayName(), item.getSettings());
        }
    }

    public static List<AutoBuyableItem> getAllItems() {
        ensureInitialized();
        return allItems;
    }

    public static void reloadSettings() {
        ensureInitialized();
        for (AutoBuyableItem item : allItems) {
            AutoBuySettingsManager.getInstance().loadSettings(item.getDisplayName(), item.getSettings());
        }
    }

    public static List<AutoBuyableItem> getKrush() {
        ensureInitialized();
        return krushItems;
    }

    public static List<AutoBuyableItem> getTalismans() {
        ensureInitialized();
        return talismanItems;
    }

    public static List<AutoBuyableItem> getSpheres() {
        ensureInitialized();
        return sphereItems;
    }

    public static List<AutoBuyableItem> getMisc() {
        ensureInitialized();
        return miscItems;
    }

    public static List<AutoBuyableItem> getDonator() {
        ensureInitialized();
        return donatorItems;
    }

    public static List<AutoBuyableItem> getPotions() {
        ensureInitialized();
        return potionItems;
    }
}
