package dev.client.api.nullcry.cmdHelper.managers.macro;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;
import lombok.SneakyThrows;
import lombok.Value;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MacroManager implements ClientApi {
    private static final File file = new File(LynxWave.getInstance().getFilesDir(), "macros.file");
    public List<Macro> macroList = new ArrayList<>();

    public void init() throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        } else {
            readFile();
        }
        registerShutdownHook();
    }

    public boolean isEmpty() {
        return macroList.isEmpty();
    }

    public void addMacro(String name, String message, int key) {
        macroList.add(new Macro(name, message, key));
        writeFile();
    }

    public boolean hasMacro(String macroName) {
        for (Macro macro : macroList) {
            if (macro.getName().equalsIgnoreCase(macroName)) {
                return true;
            }
        }
        return false;
    }

    public void deleteMacro(String name) {
        if (macroList.stream()
                .anyMatch(macro -> macro.getName().equals(name))) {
            macroList.removeIf(macro -> macro.getName().equalsIgnoreCase(name));
            writeFile();
        }
    }

    public void clearList() {
        if (!macroList.isEmpty()) {
            macroList.clear();
        }
        writeFile();
    }

    public void onKey(int key) {
        if (mc.player == null) {
            return;
        }

        macroList.stream()
                .filter(macro -> macro.getKey() == key)
                .findFirst()
                .ifPresent(macro -> {
                    try {
                        printChat(macro.getMessage());
                    } catch (Exception e) {
                        printClient("Ошибка при отправке команды " + e);
                    }
                });
    }

    public List<String> getMacroNames() {
        List<String> names = new ArrayList<>();
        for (Macro macro : macroList) {
            names.add(macro.getName());
        }
        return names;
    }

    @SneakyThrows
    public void writeFile() {
        StringBuilder builder = new StringBuilder();
        macroList.forEach(macro -> builder.append(macro.getName())
                .append(":").append(macro.getMessage())
                .append(":").append(macro.getKey())
                .append("\n"));
        String encrypted = encrypt(builder.toString().trim());
        Files.write(file.toPath(), encrypted.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private void readFile() {
        if (!file.exists()) return;

        String encryptedContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (encryptedContent.isEmpty()) return;

        String decryptedContent = decrypt(encryptedContent);
        BufferedReader reader = new BufferedReader(new StringReader(decryptedContent));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split(":", 3);
            if (parts.length == 3) {
                String name = parts[0];
                String command = parts[1];
                int key = Integer.parseInt(parts[2]);
                macroList.add(new Macro(name, command, key));
            }
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                writeFile();
            } catch (Exception e) {
                e.printStackTrace();
                Console.logManager("MacroManager -> Ошибка при сохранении списка макросов при завершении игры.");
            }
        }));
    }

    public String encrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.encrypt(data) : data;
    }

    public String decrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.decrypt(data) : data;
    }

    @Value
    public static class Macro {
        String name;
        String message;
        int key;
    }
}