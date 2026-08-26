package dev.client.api.nullcry.helper.client.irc;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the IRC client label associated with a player and exposes texture/color helpers for the tab list.
 */
public final class ClientLabelRegistry {
    private static final int ICON_WIDTH = 9;

    private static final ClientLabelRegistry INSTANCE = new ClientLabelRegistry();

    private final Map<String, String> labelsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Identifier> textureByLabel = new ConcurrentHashMap<>();
    private final Map<String, String> canonicalLabelByNormalized = new ConcurrentHashMap<>();

    private volatile String selfLabel;

    private ClientLabelRegistry() {
        registerTexture("DanqClient", Identifier.of("lynxwave", "irc/danq.png"));
        registerTexture("LynxWave", Identifier.of("lynxwave", "irc/lynx.png"));
    }

    public static ClientLabelRegistry getInstance() {
        return INSTANCE;
    }

    public void registerTexture(String label, Identifier texture) {
        if (label == null || texture == null) {
            return;
        }
        String normalized = normalize(label);
        textureByLabel.put(normalized, texture);
        canonicalLabelByNormalized.put(normalized, label);
    }

    public Identifier textureForLabel(String label) {
        if (label == null) {
            return null;
        }
        return textureByLabel.get(normalize(label));
    }

    public Set<String> knownLabels() {
        return Collections.unmodifiableSet(textureByLabel.keySet());
    }

    public void assignLabel(String playerName, String label) {
        if (playerName == null || playerName.isEmpty() || label == null || label.isEmpty()) {
            return;
        }
        String normalizedLabel = normalize(label);
        labelsByPlayer.put(normalize(playerName), canonicalLabelByNormalized.getOrDefault(normalizedLabel, label));
        String stripped = stripFormatting(playerName);
        if (!stripped.isEmpty() && !stripped.equals(playerName)) {
            labelsByPlayer.put(normalize(stripped), canonicalLabelByNormalized.getOrDefault(normalizedLabel, label));
        }
    }

    public String labelForPlayer(String playerName) {
        if (playerName == null) {
            return null;
        }
        return labelsByPlayer.get(normalize(playerName));
    }

    public boolean assignLabelFromText(String playerName, String... textVariants) {
        if (playerName == null || textVariants == null) {
            return false;
        }
        for (String variant : textVariants) {
            String detected = detectLabelFromText(variant);
            if (detected != null) {
                assignLabel(playerName, detected);
                return true;
            }
        }
        return false;
    }

    public void removeLabel(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        labelsByPlayer.remove(normalize(playerName));
        String stripped = stripFormatting(playerName);
        if (!stripped.isEmpty() && !stripped.equals(playerName)) {
            labelsByPlayer.remove(normalize(stripped));
        }
    }

    public String detectLabelFromText(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        String normalizedText = normalize(stripFormatting(source));
        if (normalizedText.isEmpty()) {
            return null;
        }
        for (String normalizedLabel : textureByLabel.keySet()) {
            if (normalizedText.contains(normalizedLabel)) {
                return canonicalLabelByNormalized.getOrDefault(normalizedLabel, normalizedLabel);
            }
        }
        return null;
    }

    public void setSelfLabel(String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        selfLabel = label;
    }

    public String getSelfLabel() {
        return selfLabel;
    }

    public int getIconWidth() {
        return ICON_WIDTH;
    }

    public int resolveColor(String playerLabel) {
        if (playerLabel == null || selfLabel == null) {
            return 0xFFFFFFFF;
        }
        return Objects.equals(normalize(selfLabel), normalize(playerLabel)) ? 0xFF32FF7A : 0xFF47A3FF;
    }

    public void clear() {
        labelsByPlayer.clear();
        selfLabel = null;
    }

    private String normalize(String input) {
        return input.trim().toLowerCase();
    }

    private String stripFormatting(String input) {
        return input.replaceAll("\u00A7.", "").trim();
    }
}

