package dev.client.api.nullcry.uiClient.altManager;

/**
 * Azure AD Client ID для входа через Microsoft.
 * Сохраняется/загружается через конфиг (Configuration).
 */
public final class MicrosoftAuthConfig {
    private static volatile String clientId = "";

    public static String getClientId() {
        return clientId != null ? clientId : "";
    }

    public static void setClientId(String id) {
        clientId = (id == null || id.isBlank()) ? "" : id.trim();
    }
}
