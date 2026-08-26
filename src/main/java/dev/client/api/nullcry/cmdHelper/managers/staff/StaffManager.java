package dev.client.api.nullcry.cmdHelper.managers.staff;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class StaffManager implements ClientApi {
    private static final File file = new File(LynxWave.getInstance().getFilesDir(), "staff.file");
    private final Set<String> staffs = new HashSet<>();

    public void init() throws IOException {
        if (file.exists()) {
            loadStaffs();
        } else {
            createNewFile();
        }

        registerShutdownHook();
    }

    private void loadStaffs() throws IOException {
        try {
            String encryptedContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!encryptedContent.isEmpty()) {
                String decryptedContent = decrypt(encryptedContent);
                staffs.clear();
                staffs.addAll(Arrays.stream(decryptedContent.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()));
            }
        } catch (IOException e) {
            Console.logManager("StaffManager -> Ошибка при загрузке списка модераторов: " + e.getMessage());
            throw e;
        }
    }

    private void createNewFile() throws IOException {
        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                throw new IOException("StaffManager -> Не удалось создать директорию для файла модераторов.");
            }
        }
        if (!file.createNewFile()) {
            throw new IOException("StaffManager -> Не удалось создать файл модераторов.");
        }
    }

    public void add(String name) {
        staffs.add(name);
        saveSafely();
    }

    public void remove(String name) {
        staffs.remove(name);
        saveSafely();
    }

    public void clear() {
        staffs.clear();
        saveSafely();
    }

    public boolean isStaff(String name) {
        return staffs.contains(name);
    }

    private void save() throws IOException {
        String content = String.join("\n", staffs);
        String encryptedContent = encrypt(content);
        Files.write(file.toPath(), encryptedContent.getBytes(StandardCharsets.UTF_8));
    }

    private void saveSafely() {
        try {
            save();
        } catch (IOException e) {
            Console.logManager("StaffManager -> Ошибка при сохранении списка модераторов: " + e.getMessage());
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
            } catch (IOException e) {
                Console.logManager("StaffManager -> Ошибка при сохранении списка модераторов при выходе: " + e.getMessage());
            }
        }));
    }

    public String encrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.encrypt(data) : data;
    }

    public String decrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.decrypt(data) : data;
    }
}