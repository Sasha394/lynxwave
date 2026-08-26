package dev.client.api.nullcry.helper.client;

import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.render.ColorUtils;
import lombok.experimental.UtilityClass;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.regex.Pattern;

@UtilityClass
public class PlayerHelper implements ClientApi {
    private final Pattern NAME_REGEX = Pattern.compile("^[A-zА-я0-9_]{3,16}$");

    public int getAnarchy() {
        final String SCOREBOARD_NAME = "TAB-Scoreboard";
        final String ANARCHY_PREFIX = "анархия";
        if (mc.world == null) {
            return -1;
        }

        ScoreboardObjective objective = mc.world.getScoreboard().getNullableObjective(SCOREBOARD_NAME);
        if (objective == null) {
            return -1;
        }
        String displayName = objective.getDisplayName().getString().toLowerCase();
        int prefixIndex = displayName.indexOf(ANARCHY_PREFIX);
        if (prefixIndex == -1) {
            return -1;
        }
        try {
            String numberStr = displayName.substring(prefixIndex + ANARCHY_PREFIX.length()).replaceAll("\\D", "");
            if (!numberStr.isEmpty()) {
                return Integer.parseInt(numberStr);
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    public PlayerEntity getRadiusPlayer(double radius) {
        if (mc.world == null || mc.player == null) return null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player) {
                double distanceSq = mc.player.squaredDistanceTo(player);

                if (distanceSq <= radius * radius) {
                    return player;
                }
            }
        }
        return null;
    }

    public static PlayerEntity getNearestFriend(double radius) {
        if (mc.world == null || mc.player == null) return null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && LynxWave.getInstance().getFriendManager().isFriend(player.getName().getString())) {
                double distSq = mc.player.squaredDistanceTo(player);
                if (distSq <= radius * radius) {
                    return player;
                }
            }
        }
        return null;
    }

    public boolean getRadiusPlayerFrom(PlayerEntity from, PlayerEntity to, double radius) {
        return from != null && to != null && from != to && from.squaredDistanceTo(to) <= radius * radius;
    }

    public String getPlayerPing(PlayerEntity player) {
        if (mc.isInSingleplayer()) {
            return "local";
        } else if (player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (info != null) {
                return String.valueOf(info.getLatency());
            }
        }
        return "N/A";
    }

    public String getLocalPing() {
        if (mc.isInSingleplayer()) {
            return "local";
        } else if (mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (info != null) {
                return info.getLatency() + "";
            }
        }
        return "N/A";
    }

    public float getHealth(LivingEntity entity) {
        float hp = entity.getHealth() + entity.getAbsorptionAmount();

        if (entity instanceof PlayerEntity player) {
            Scoreboard scoreboard = mc.world != null ? mc.world.getScoreboard() : null;
            ScoreboardObjective objective = scoreboard != null ? scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) : null;

            if (objective != null) {
                boolean shouldReadScoreboard = ConnectionHelper.isFT() || ConnectionHelper.isRW();

                if (shouldReadScoreboard) {
                    ReadableScoreboardScore readableScore = scoreboard.getScore(player, objective);

                    if (readableScore != null) {
                        MutableText formatted = ReadableScoreboardScore.getFormattedScore(readableScore, objective.getNumberFormatOr(StyledNumberFormat.EMPTY));
                        String sanitized = Formatting.strip(formatted.getString());

                        if (sanitized != null && !sanitized.isEmpty()) {
                            sanitized = sanitized.replace(",", ".");
                            try {
                                hp = Float.parseFloat(sanitized);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        }

        return MathHelper.clamp(hp, 0f, entity.getMaxHealth());
    }

    public static boolean isOnCarpetOrSnow() {
        if (mc.player == null || mc.world == null) return false;
        return mc.world.getBlockState(mc.player.getBlockPos()).getBlock() instanceof CarpetBlock || mc.world.getBlockState(mc.player.getBlockPos()).getBlock() instanceof SnowBlock;
    }

    public String formatTime(double seconds) {
        if (seconds >= 60) {
            int minutes = (int) (seconds / 60);
            double remainingSeconds = seconds % 60;
            return String.format("%dм %.1fс", minutes, remainingSeconds);
        } else {
            return String.format("%.1fс", seconds);
        }
    }

    public boolean isNameValid(String name) {
        return NAME_REGEX.matcher(name).matches();
    }

    public int getHealthColorHSV(LivingEntity target) {
        float health = target.getHealth();
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float hp = MathHelper.clamp(health / maxHealth, 0f, 1f);

        float hueDeg = 10f + 100f * hp;
        float hue = hueDeg / 360f;

        float e = easeOutCubic(hp);
        float sat = 0.85f - 0.15f * e;
        float val = 0.92f - 0.06f * e;

        return hsvToRgb(hue, sat, val);
    }

    private float easeOutCubic(float x) {
        float k = 1f - x;
        return 1f - k * k * k;
    }

    private int hsvToRgb(float hue, float s, float v) {
        float h = (hue - (float)Math.floor(hue)) * 6f;
        int i = (int) Math.floor(h);
        float f = h - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));

        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return ColorUtils.rgb(
                Math.round(r * 255f),
                Math.round(g * 255f),
                Math.round(b * 255f)
        );
    }
}
