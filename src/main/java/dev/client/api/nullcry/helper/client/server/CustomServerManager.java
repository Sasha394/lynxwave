package dev.client.api.nullcry.helper.client.server;

import dev.client.LynxWave;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerInfo.ServerType;

import java.util.*;

/**
 * Utility responsible for keeping LynxWave's custom servers synchronized with the Minecraft server list.
 */
public final class CustomServerManager {
    private static final List<Template> TEMPLATES;
    private static final Map<String, Template> BY_NORMALIZED_ADDRESS;

    static {
        List<Template> templates = new ArrayList<>();
        Map<String, Template> byAddress = new LinkedHashMap<>();
        for (String entry : LynxWave.customServer) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String[] split = entry.split(":", 2);
            if (split.length != 2) {
                continue;
            }

            String name = split[0].trim();
            String address = split[1].trim();
            if (name.isEmpty() || address.isEmpty()) {
                continue;
            }

            Template template = new Template(name, address);
            templates.add(template);
            byAddress.put(template.normalizedAddress(), template);
        }

        TEMPLATES = Collections.unmodifiableList(templates);
        BY_NORMALIZED_ADDRESS = Collections.unmodifiableMap(byAddress);
    }

    private CustomServerManager() {
    }

    /**
     * Ensures that the provided list starts with LynxWave's custom servers using their predefined order.
     */
    public static void synchronize(List<ServerInfo> servers, List<ServerInfo> hiddenServers) {
        if (servers == null || TEMPLATES.isEmpty()) {
            return;
        }

        removeCustomEntries(hiddenServers);

        Map<String, ServerInfo> existing = new LinkedHashMap<>();
        Iterator<ServerInfo> iterator = servers.iterator();
        while (iterator.hasNext()) {
            ServerInfo info = iterator.next();
            Template template = BY_NORMALIZED_ADDRESS.get(normalizeAddress(info.address));
            if (template != null) {
                iterator.remove();
                existing.put(template.normalizedAddress(), info);
            }
        }

        int index = 0;
        for (Template template : TEMPLATES) {
            ServerInfo info = existing.get(template.normalizedAddress());
            if (info == null) {
                info = new ServerInfo(template.name(), template.address(), ServerType.OTHER);
            }

            applyTemplate(info, template);
            servers.add(index++, info);
        }
    }

    /**
     * Applies the canonical name and address to the provided server entry if it represents a custom server.
     */
    public static void applyTemplate(ServerInfo info) {
        if (info == null) {
            return;
        }

        Template template = BY_NORMALIZED_ADDRESS.get(normalizeAddress(info.address));
        if (template != null) {
            applyTemplate(info, template);
        }
    }

    private static void applyTemplate(ServerInfo info, Template template) {
        info.name = template.name();
        info.address = template.address();
    }

    /**
     * Returns {@code true} if the provided server belongs to LynxWave's custom list.
     */
    public static boolean isCustomServer(ServerInfo info) {
        return info != null && BY_NORMALIZED_ADDRESS.containsKey(normalizeAddress(info.address));
    }

    /**
     * Returns {@code true} if the address corresponds to LynxWave's custom servers.
     */
    public static boolean isCustomAddress(String address) {
        return BY_NORMALIZED_ADDRESS.containsKey(normalizeAddress(address));
    }

    /**
     * Returns number of configured custom servers.
     */
    public static int customServerCount() {
        return TEMPLATES.size();
    }

    /**
     * Checks whether the given index of the list contains a custom server.
     */
    public static boolean isCustomIndex(List<ServerInfo> servers, int index) {
        if (servers == null || index < 0 || index >= servers.size()) {
            return false;
        }

        return isCustomServer(servers.get(index));
    }

    private static void removeCustomEntries(List<ServerInfo> servers) {
        if (servers == null || servers.isEmpty()) {
            return;
        }

        servers.removeIf(CustomServerManager::isCustomServer);
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }

        return address.trim().toLowerCase(Locale.ROOT);
    }

    private record Template(String name, String address) {
        private String normalizedAddress() {
            return normalizeAddress(address);
        }
    }
}

