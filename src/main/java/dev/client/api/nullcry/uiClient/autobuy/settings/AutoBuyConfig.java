package dev.client.api.nullcry.uiClient.autobuy.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.client.LynxWave;
import dev.client.api.nullcry.uiClient.autobuy.items.AutoBuyableItem;
import dev.client.api.nullcry.uiClient.autobuy.originalitems.ItemRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class AutoBuyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File(LynxWave.getInstance().getClientDir(), "AutoBuy");
    private static final File FILE = new File(DIR, "AutoBuyConfig.json");

    private AutoBuyConfig() {
    }

    public static void init() {
        load();
        Runtime.getRuntime().addShutdownHook(new Thread(AutoBuyConfig::save));
    }

    public static synchronized void load() {
        try {
            if (!FILE.exists()) {
                return;
            }

            ItemRegistry.getAllItems();
            try (FileReader reader = new FileReader(FILE)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) {
                    return;
                }

                if (json.has("enabled_items")) {
                    JsonObject enabledItems = json.getAsJsonObject("enabled_items");
                    for (AutoBuyableItem item : ItemRegistry.getAllItems()) {
                        if (enabledItems.has(item.getDisplayName())) {
                            item.setEnabled(enabledItems.get(item.getDisplayName()).getAsBoolean());
                        }
                    }
                }

                if (json.has("settings")) {
                    JsonObject settings = json.getAsJsonObject("settings");
                    AutoBuySettingsManager.getInstance().loadFromJson(settings);
                    ItemRegistry.reloadSettings();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            JsonObject json = new JsonObject();
            JsonObject enabledItems = new JsonObject();

            for (AutoBuyableItem item : ItemRegistry.getAllItems()) {
                enabledItems.addProperty(item.getDisplayName(), item.isEnabled());
                AutoBuySettingsManager.getInstance().saveSettings(item.getDisplayName(), item.getSettings());
            }
            json.add("enabled_items", enabledItems);
            json.add("settings", AutoBuySettingsManager.getInstance().saveToJson());

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
