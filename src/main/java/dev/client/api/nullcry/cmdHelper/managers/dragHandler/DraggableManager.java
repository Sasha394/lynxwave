package dev.client.api.nullcry.cmdHelper.managers.dragHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;
import dev.client.api.nullcry.helper.other.DraggableHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;

public class DraggableManager {
    private static final File file = new File(LynxWave.getInstance().getFilesDir(), "drag.file");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    public static LinkedHashMap<String, DraggableHandler> draggable = new LinkedHashMap<>();

    private static int lastMouseX = -1;
    private static int lastMouseY = -1;

    public static void init() {
        load();
        registerShutdownHook();
    }

    public static void save() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(file)) {
            String json = GSON.toJson(draggable.values());
            String encrypted = encrypt(json);
            writer.write(encrypted);
            Console.logManager("DragManager -> Позиции draggable элементов успешно сохранены в файл " + file.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
            Console.logManager("DragManager -> Ошибка при сохранении данных в файл: " + file.getAbsolutePath());
        }
    }

    public static void load() {
        if (!file.exists()) {
            Console.logManager("DragManager -> Файл с позициями draggable не найден. Будет создан новый файл после сохранения.");
            return;
        }

        try {
            String encryptedContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String decryptedContent = decrypt(encryptedContent);

            DraggableHandler[] loadedDrags = GSON.fromJson(decryptedContent, DraggableHandler[].class);

            if (loadedDrags != null) {
                for (DraggableHandler draggableHandler : loadedDrags) {
                    if (draggableHandler != null) {
                        DraggableHandler currentDrag = draggable.get(draggableHandler.getName());
                        if (currentDrag != null) {
                            currentDrag.setX(draggableHandler.getX());
                            currentDrag.setY(draggableHandler.getY());
                            draggable.put(draggableHandler.getName(), currentDrag);
                        } else {
                            draggable.put(draggableHandler.getName(), draggableHandler);
                        }
                    }
                }
                Console.logManager("DragManager -> Позиции draggable элементов загружены.");
            } else {
                Console.logManager("DragManager -> Данные в файле пусты или повреждены.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Console.logManager("DragManager -> Ошибка при загрузке данных из файла: " + file.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            Console.logManager("DragManager -> Ошибка при дешифровании данных.");
        }
    }

    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
                Console.logManager("DragManager -> Позиции draggable элементов сохранены перед выходом из игры.");
            } catch (Exception e) {
                e.printStackTrace();
                Console.logManager("DragManager -> Ошибка при сохранении draggable элементов при выходе.");
            }
        }));
    }

    public static String encrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.encrypt(data) : data;
    }

    public static String decrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.decrypt(data) : data;
    }

    public static void closeOtherPanels(DraggableHandler except) {
        draggable.values().forEach(handler -> {
            if (handler.getSettingsPanel() != null && handler != except) {
                handler.getSettingsPanel().close();
            }
        });
    }

    public static void closeAllPanels() {
        draggable.values().forEach(handler -> {
            if (handler.getSettingsPanel() != null) {
                handler.getSettingsPanel().close();
            }
        });
    }

    public static void updatePanelCursor(int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static void renderLingeringPanels(DrawContext context, float delta) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatScreen) return;

        for (var handler : draggable.values()) {
            var panel = handler.getSettingsPanel();
            if (panel != null && handler.isActive()) {
                panel.renderLingering(context, lastMouseX, lastMouseY, delta);
            }
        }
    }
}
