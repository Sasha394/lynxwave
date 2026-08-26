package dev.client.api.nullcry.uiClient.clickGui.components.theme;

import dev.client.LynxWave;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private final File dir;
    private final File lastFile;

    private ThemeManager() {
        this.dir = new File(LynxWave.getInstance().getClientDir(), "themes");
        this.lastFile = new File(dir, "last_selected");
        ensureDir();
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public File getDir() {
        return dir;
    }

    public void save(Theme theme) {
        if (theme == null) return;
        ensureDir();
        Path out = dir.toPath().resolve(safeFileName(theme.getName()) + ".theme");
        List<String> lines = new ArrayList<>();
        lines.add("name=" + theme.getName());
        for (Theme.ElementColor ec : theme.getElementColors()) {
            lines.add(ec.getName() + "=" + ec.getColor().getRGB());
        }
        try {
            Files.write(out, lines, UTF8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAll(List<Theme> themes) {
        for (Theme t : themes) {
            if (t != null) save(t);
        }
    }

    public void saveLastSelected(Theme theme) {
        if (theme == null) return;
        ensureDir();
        try {
            Files.writeString(lastFile.toPath(), safeFileName(theme.getName()), UTF8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean remove(String themeName) {
        if (themeName == null || themeName.isEmpty()) return false;
        ensureDir();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".theme"));
        if (files == null) return false;
        for (File file : files) {
            try {
                String first = Files.lines(file.toPath(), UTF8).findFirst().orElse("");
                if (first.startsWith("name=") && themeName.equals(first.substring(5).trim())) {
                    return file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public Theme load(File file) {
        if (file == null || !file.exists()) return null;
        Map<String, String> map = readKeyValueFile(file.toPath());
        String name = map.getOrDefault("name", stripExtension(file.getName()));
        Theme theme = new Theme(name);
        for (Theme.ElementColor ec : theme.getElementColors()) {
            String key = ec.getName();
            if (map.containsKey(key)) {
                try {
                    int rgb = Integer.parseInt(map.get(key));
                    ec.setColor(new Color(rgb, true));
                } catch (NumberFormatException ignored) { }
            }
        }
        return theme;
    }

    public List<Theme> loadAll() {
        List<Theme> list = new ArrayList<>();
        ensureDir();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".theme"));
        if (files == null || files.length == 0) {
            return list;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : files) {
            Theme loaded = load(f);
            if (loaded != null) list.add(loaded);
        }
        return list;
    }

    public Theme loadLastSelected() {
        if (!lastFile.exists()) return null;
        try {
            String safeName = Files.readString(lastFile.toPath(), UTF8).trim();
            if (safeName.isEmpty()) return null;
            File f = new File(dir, safeName + ".theme");
            return f.exists() ? load(f) : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String safeFileName(String name) {
        return (name == null) ? "theme" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void ensureDir() {
        if (!dir.exists()) dir.mkdirs();
    }

    private Map<String, String> readKeyValueFile(Path p) {
        Map<String, String> map = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(p, UTF8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                map.put(line.substring(0, idx), line.substring(idx + 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return (idx <= 0) ? name : name.substring(0, idx);
    }
}
