package dev.client.api.nullcry.helper.client.server.core;

import dev.client.api.injection.accessor.IBossBarHudAccessor;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class MineViewer implements ClientApi {
    private static final Pattern TIME_FORMAT_PATTERN = Pattern.compile("^\\d{2}:\\d+.*$");
    private static final UUID BOSSBAR_UUID = UUID.nameUUIDFromBytes("Mine Info".getBytes());

    public String displayTimeText = "";
    public String displayLevelText = "";

    public String lastLevelText = "";
    private String nextLevelText = "";

    boolean hasData = false;

    public void onRender2D(RenderEvent.Draw2D e) {
        if (e.getContext().getMatrices() == null) return;

        boolean foundData = false;

        if (mc.world != null && mc.player != null) {
            Entity[] entities = findNearestEntities();

            Text timeComponent = entities[0] != null ? entities[0].getCustomName() : Text.literal("");
            Text levelComponentRaw = entities[1] != null ? entities[1].getCustomName() : Text.literal("");

            String filteredText = levelComponentRaw.getString().replaceAll("(?i)следующая:\\s*", "");

            Text levelComponent = null;
            if (levelComponentRaw instanceof MutableText) {
                levelComponent = levelComponentRaw.copy();
            }

            Text titleComponent = findTitleComponent();

            if (!timeComponent.getString().isEmpty() || !levelComponent.getString().isEmpty()) {
                foundData = true;

                if (!filteredText.isEmpty() && !filteredText.equals(nextLevelText)) {
                    lastLevelText = nextLevelText;
                    nextLevelText = filteredText;
                }

                createOrUpdateBossBar(timeComponent, levelComponent, titleComponent);

                displayTimeText = timeComponent.getString();
                displayLevelText = filteredText;
            }
        }

        hasData = foundData;

        if (!hasData) {
            removeBossBar();
        }
    }

    private Text findTitleComponent() {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity) || !entity.hasCustomName()) continue;

            String name = entity.getCustomName().getString().toLowerCase().replaceAll("[^а-яa-z\\s\\-]", "");
            if (name.contains("авто") && name.contains("шахта")) {
                return entity.getCustomName();
            }
        }
        return Text.literal("Авто-Шахта");
    }

    private Entity[] findNearestEntities() {
        Entity nearestTimeEntity = null;
        Entity nearestLevelEntity = null;
        double minTimeDistance = Double.MAX_VALUE;
        double minLevelDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity) || !entity.hasCustomName()) {
                continue;
            }

            Text customNameComponent = entity.getCustomName();
            if (customNameComponent == null) {
                continue;
            }

            String customName = customNameComponent.getString().toLowerCase();
            double distance = entity.squaredDistanceTo(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
            );

            if (isValidTimeText(customName) && distance < minTimeDistance) {
                minTimeDistance = distance;
                nearestTimeEntity = entity;
            } else if (isValidLevelText(customName) && distance < minLevelDistance) {
                minLevelDistance = distance;
                nearestLevelEntity = entity;
            }
        }

        return new Entity[]{nearestTimeEntity, nearestLevelEntity};
    }

    private boolean isValidTimeText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        if (!TIME_FORMAT_PATTERN.matcher(text).matches()) {
            return false;
        }

        for (char c : text.toCharArray()) {
            if (c != ':' && Character.isLetter(c)) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidLevelText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return text.contains("обычный") || text.contains("мифический") || text.contains("легендарный") || text.contains("обыч") || text.contains("миф") || text.contains("лег");
    }

    public void createOrUpdateBossBar(Text timeComponent, Text levelComponent, Text titleComponent) {
        if (mc.inGameHud == null) return;

        Text labelComponent = createBossBarLabel(titleComponent, levelComponent, timeComponent);

        float progress = parseTimeToProgress(timeComponent.getString());
        int secondsLeft = parseTimeToSeconds(timeComponent.getString());

        Map<UUID, ClientBossBar> bossMap = ((IBossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBarsMap();

        ClientBossBar bossInfo = bossMap.get(BOSSBAR_UUID);

        if (progress <= 0.0f) {
            bossMap.remove(BOSSBAR_UUID);
            return;
        }

        BossBar.Color color = getColorByTime(secondsLeft);

        if (bossInfo == null) {
            bossInfo = new CustomClientBossBar(
                    BOSSBAR_UUID,
                    labelComponent,
                    color,
                    BossBar.Style.PROGRESS,
                    progress,
                    false, false, false
            );
            bossMap.put(BOSSBAR_UUID, bossInfo);
        } else {
            bossInfo.setName(labelComponent);
            bossInfo.setPercent(progress);
            bossInfo.setColor(color);
        }
    }

    public Text createBossBarLabel(Text title, Text nextLevel, Text time) {
        MutableText root = Text.empty();

        root.append(title.copy());
        root.append(Text.literal(" | "));

        String nextLevelStr = nextLevel.getString().toLowerCase();
        if (!nextLevelStr.isEmpty() && !nextLevelStr.contains("следующая")) {
            root.append(Text.literal("Следующая: "));
        }
        root.append(nextLevel.copy());

        root.append(Text.literal(" | Обновление через: "));
        root.append(time.copy());

        return root;
    }

    public void removeBossBar() {
        if (mc == null || mc.inGameHud == null) return;
        Map<UUID, ClientBossBar> bossMap = ((IBossBarHudAccessor) mc.inGameHud.getBossBarHud()).getBossBarsMap();
        bossMap.remove(BOSSBAR_UUID);
    }

    private float parseTimeToProgress(String timeText) {
        if (!TIME_FORMAT_PATTERN.matcher(timeText).matches()) return 1.0f;
        try {
            String[] parts = timeText.split(":");
            int min = Integer.parseInt(parts[0]);
            int sec = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            int totalSeconds = min * 60 + sec;

            if (totalSeconds <= 0) return 0f;
            if (totalSeconds > 180) totalSeconds = 180;

            return (float) totalSeconds / 180f;
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private int parseTimeToSeconds(String timeText) {
        if (!TIME_FORMAT_PATTERN.matcher(timeText).matches()) return 180;
        try {
            String[] parts = timeText.split(":");
            int min = Integer.parseInt(parts[0]);
            int sec = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            return min * 60 + sec;
        } catch (Exception e) {
            return 150;
        }
    }

    private BossBar.Color getColorByTime(int secondsLeft) {
        if (secondsLeft <= 10) {
            return BossBar.Color.RED;
        } else if (secondsLeft <= 30) {
            return BossBar.Color.YELLOW;
        } else if (secondsLeft <= 60) {
            return BossBar.Color.YELLOW;
        } else {
            return BossBar.Color.GREEN;
        }
    }

    public static class CustomClientBossBar extends ClientBossBar {
        public CustomClientBossBar(UUID uuid, Text name, Color color, Style style, float percent,
                                   boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
            super(uuid, name, percent, color, style, darkenSky, dragonMusic, thickenFog);
        }

        public void updatePercent(float percent) {
            this.setPercent(percent);
        }

        public void updateName(Text name) {
            this.setName(name);
        }
    }
}