package dev.client.api.nullcry.cmdHelper.managers.friend;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class FriendManager implements ClientApi {
    private static final File file = new File(LynxWave.getInstance().getFilesDir(), "friends.file");
    public final Set<String> friends = new HashSet<>();

    public void init() {
        load();
        registerShutdownHook();
    }

    @SneakyThrows
    public void load() {
        if (file.exists()) {
            String encryptedContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!encryptedContent.isEmpty()) {
                String decryptedContent = decrypt(encryptedContent);
                friends.clear();
                friends.addAll(Arrays.stream(decryptedContent.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()));
            }
        } else {
            file.createNewFile();
        }
    }

    public void add(String name) {
        friends.add(name);
        save();
    }

    public void delete(String name) {
        friends.remove(name);
        save();
    }

    public void clear() {
        friends.clear();
        save();
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }

    @SneakyThrows
    private void save() {
        String content = String.join("\n", friends);
        String encryptedContent = encrypt(content);
        Files.write(file.toPath(), encryptedContent.getBytes(StandardCharsets.UTF_8));
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
            } catch (Exception e) {
                e.printStackTrace();
                Console.logManager("FriendManager -> Ошибка при сохранении списка друзей при завершении игры.");
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