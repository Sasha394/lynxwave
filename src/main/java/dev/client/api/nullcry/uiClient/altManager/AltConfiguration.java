package dev.client.api.nullcry.uiClient.altManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.client.LynxWave;
import dev.client.api.nullcry.helper.client.crypter.AESEncryptor;
import dev.client.api.nullcry.helper.other.Console;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AltConfiguration {
    private static final File FILE = new File(LynxWave.getInstance().getFilesDir(), "alts.file");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<AltAccount> ACCOUNTS = new ArrayList<>();
    private static String SELECTED = null;
    private static long FAVORITE_COUNTER = 0L;
    private static long CREATION_COUNTER = 0L;

    private static final class Store {
        List<AltAccount> accounts = new ArrayList<>();
        String selected;
        long favoriteCounter;
        long creationCounter;
    }

    public static void init() {
        load();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                save();
                Console.logManager("Alts -> сохранены перед выходом.");
            } catch (Exception e) {
                e.printStackTrace();
                Console.logManager("Alts -> ошибка сохранения при выходе.");
            }
        }));
    }

    public static synchronized List<AltAccount> getAccounts() {
        return Collections.unmodifiableList(ACCOUNTS);
    }

    public static synchronized boolean add(String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.trim();
        if (indexOfName(n) != -1) return false;
        long order = ++CREATION_COUNTER;
        ACCOUNTS.add(new AltAccount(n, FMT.format(LocalDate.now()), false, 0L, order));
        reorderAccounts();
        saveQuiet();
        return true;
    }

    public static synchronized boolean removeByName(String name) {
        int i = indexOfName(name);
        if (i == -1) return false;
        if (ACCOUNTS.get(i).name.equalsIgnoreCase(SELECTED)) {
            SELECTED = null;
        }
        ACCOUNTS.remove(i);
        reorderAccounts();
        saveQuiet();
        return true;
    }

    public static synchronized void clear() {
        ACCOUNTS.clear();
        SELECTED = null;
        FAVORITE_COUNTER = 0L;
        CREATION_COUNTER = 0L;
        saveQuiet();
    }

    public static synchronized void setSelected(String name) {
        SELECTED = (name == null || name.isBlank()) ? null : name.trim();
        saveQuiet();
    }

    public static synchronized String getSelected() {
        return SELECTED;
    }

    public static synchronized boolean toggleFavorite(String name) {
        int idx = indexOfName(name);
        if (idx == -1) return false;
        AltAccount account = ACCOUNTS.get(idx);
        if (account == null) return false;

        account.favorite = !account.favorite;
        if (account.favorite) {
            account.favoriteOrder = ++FAVORITE_COUNTER;
        } else {
            account.favoriteOrder = 0L;
        }

        reorderAccounts();
        saveQuiet();
        return account.favorite;
    }

    private static int indexOfName(String name) {
        if (name == null) return -1;
        String n = name.trim();
        for (int i = 0; i < ACCOUNTS.size(); i++) {
            if (ACCOUNTS.get(i).name.equalsIgnoreCase(n)) return i;
        }
        return -1;
    }

    public static synchronized void save() {
        try {
            if (!FILE.exists()) {
                File dir = FILE.getParentFile();
                if (dir != null) dir.mkdirs();
                FILE.createNewFile();
            }
            Store store = new Store();
            store.accounts = ACCOUNTS;
            store.selected = SELECTED;
            store.favoriteCounter = FAVORITE_COUNTER;
            store.creationCounter = CREATION_COUNTER;

            String json = GSON.toJson(store);
            String encrypted = encrypt(json);

            try (FileWriter w = new FileWriter(FILE, StandardCharsets.UTF_8)) {
                w.write(encrypted);
            }
            Console.logManager("Alts -> сохранены в " + FILE.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            Console.logManager("Alts -> ошибка сохранения " + FILE.getAbsolutePath());
        }
    }

    public static synchronized void load() {
        try {
            if (!FILE.exists()) {
                Console.logManager("Alts -> файла нет, будет создан при сохранении.");
                return;
            }
            String enc = Files.readString(FILE.toPath(), StandardCharsets.UTF_8);
            String json = decrypt(enc);

            ACCOUNTS.clear();
            SELECTED = null;
            FAVORITE_COUNTER = 0L;
            CREATION_COUNTER = 0L;

            String trimmed = json == null ? "" : json.trim();
            if (trimmed.startsWith("[")) {
                AltAccount[] arr = GSON.fromJson(trimmed, AltAccount[].class);
                if (arr != null) ACCOUNTS.addAll(Arrays.asList(arr));
                normalizeFavorites();
                normalizeCreationOrder();
                saveQuiet();
            } else if (!trimmed.isEmpty()) {
                Store store = GSON.fromJson(trimmed, Store.class);
                if (store != null) {
                    if (store.accounts != null) ACCOUNTS.addAll(store.accounts);
                    SELECTED = store.selected;
                    FAVORITE_COUNTER = store.favoriteCounter;
                    CREATION_COUNTER = store.creationCounter;
                    normalizeFavorites();
                    normalizeCreationOrder();
                    saveQuiet();
                }
            } else {
                Console.logManager("Alts -> данные пусты или повреждены.");
            }
            reorderAccounts();
            Console.logManager("Alts -> загружено: " + ACCOUNTS.size() + ", selected=" + SELECTED);
        } catch (Exception e) {
            e.printStackTrace();
            Console.logManager("Alts -> ошибка загрузки " + FILE.getAbsolutePath());
        }
    }

    private static void saveQuiet() {
        try {
            save();
        } catch (Exception ignored) {
        }
    }

    public static final class AltAccount {
        public String name;
        public String date;
        public boolean favorite;
        public long favoriteOrder;
        public long createdOrder;

        public AltAccount(String name, String date, boolean favorite, long favoriteOrder, long createdOrder) {
            this.name = name;
            this.date = date;
            this.favorite = favorite;
            this.favoriteOrder = favoriteOrder;
            this.createdOrder = createdOrder;
        }
    }

    public static String encrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.encrypt(data) : data;
    }

    public static String decrypt(String data) {
        return LynxWave.getInstance().cryptEnabled() ? AESEncryptor.decrypt(data) : data;
    }

    private static void reorderAccounts() {
        List<AltAccount> favorites = new ArrayList<>();
        List<AltAccount> others = new ArrayList<>();
        for (AltAccount account : ACCOUNTS) {
            if (account == null) continue;
            if (account.favorite) {
                favorites.add(account);
            } else {
                others.add(account);
            }
        }

        favorites.sort(Comparator.comparingLong(a -> a.favoriteOrder <= 0L ? Long.MAX_VALUE : a.favoriteOrder));
        others.sort(Comparator
                .comparingLong((AltAccount a) -> a.createdOrder <= 0L ? Long.MAX_VALUE : a.createdOrder)
                .thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));

        ACCOUNTS.clear();
        ACCOUNTS.addAll(favorites);
        ACCOUNTS.addAll(others);
    }

    private static void normalizeFavorites() {
        long counter = FAVORITE_COUNTER;
        for (AltAccount account : ACCOUNTS) {
            if (account == null || !account.favorite) continue;
            if (account.favoriteOrder <= 0L) {
                counter++;
                account.favoriteOrder = counter;
            } else {
                counter = Math.max(counter, account.favoriteOrder);
            }
        }
        FAVORITE_COUNTER = counter;
    }

    private static void normalizeCreationOrder() {
        long counter = CREATION_COUNTER;
        for (AltAccount account : ACCOUNTS) {
            if (account == null) continue;
            if (account.createdOrder <= 0L) {
                counter++;
                account.createdOrder = counter;
            } else {
                counter = Math.max(counter, account.createdOrder);
            }
        }
        CREATION_COUNTER = counter;
    }
}
