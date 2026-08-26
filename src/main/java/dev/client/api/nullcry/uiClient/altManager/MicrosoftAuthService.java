package dev.client.api.nullcry.uiClient.altManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Microsoft OAuth 2.0 device code flow + Xbox Live + XSTS + Minecraft Services.
 * Для входа через Microsoft нужен Azure AD Client ID (публичное приложение, без секрета).
 * Зарегистрировать: https://portal.azure.com/ → App registrations → New registration.
 * Включить: Live SDK support, Public client flows. Scope: XboxLive.signin, offline_access.
 * Для доступа к Minecraft API может потребоваться форма: https://aka.ms/mce-reviewappid
 */
public final class MicrosoftAuthService {

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String DEVICE_LOGIN_PAGE = "https://microsoft.com/devicelogin";

    private static String getClientId() {
        String id = System.getProperty("lynxwave.microsoft.clientid", "");
        if (id != null && !id.isBlank()) return id.trim();
        return MicrosoftAuthConfig.getClientId();
    }

    /**
     * Запускает вход через Microsoft в фоне. При успехе callback вызывается на главном потоке с Session данными.
     * При ошибке callback вызывается с errorMessage != null.
     */
    public static void loginAsync(ResultCallback callback) {
        String clientId = getClientId();
        if (clientId == null || clientId.isBlank()) {
            callback.onResult(null, "Укажите Azure Client ID (настройки или -Dlynxwave.microsoft.clientid=...)");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

                // 1) Device code
                String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);
                HttpRequest deviceReq = HttpRequest.newBuilder()
                        .uri(URI.create(DEVICE_CODE_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> deviceResp = client.send(deviceReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (deviceResp.statusCode() != 200) {
                    callback.onResult(null, "Ошибка устройства: " + deviceResp.statusCode() + " " + deviceResp.body());
                    return;
                }
                JsonObject deviceJson = JsonParser.parseString(deviceResp.body()).getAsJsonObject();
                String userCode = deviceJson.has("user_code") ? deviceJson.get("user_code").getAsString() : "";
                String deviceCode = deviceJson.has("device_code") ? deviceJson.get("device_code").getAsString() : "";
                long intervalSec = deviceJson.has("interval") ? deviceJson.get("interval").getAsLong() : 5L;
                String message = deviceJson.has("message") ? deviceJson.get("message").getAsString() : "Откройте " + DEVICE_LOGIN_PAGE + " и введите код: " + userCode;

                callback.onProgress(message, userCode, DEVICE_LOGIN_PAGE);

                // 2) Poll for token
                String tokenBody = "grant_type=device_code&device_code=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8)
                        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
                HttpRequest tokenReq = HttpRequest.newBuilder()
                        .uri(URI.create(TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                        .build();

                String msAccessToken = null;
                for (int i = 0; i < 120; i++) {
                    HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    String tokenRespBody = tokenResp.body();
                    JsonObject tokenJson = JsonParser.parseString(tokenRespBody).getAsJsonObject();
                    if (tokenResp.statusCode() == 200 && tokenJson.has("access_token")) {
                        msAccessToken = tokenJson.get("access_token").getAsString();
                        break;
                    }
                    if (tokenJson.has("error")) {
                        String err = tokenJson.get("error").getAsString();
                        if ("authorization_pending".equals(err)) {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(Math.max(1, intervalSec)));
                            continue;
                        }
                        String errDesc = tokenJson.has("error_description") ? tokenJson.get("error_description").getAsString() : err;
                        callback.onResult(null, "Ошибка авторизации: " + errDesc);
                        return;
                    }
                    Thread.sleep(TimeUnit.SECONDS.toMillis(Math.max(1, intervalSec)));
                }
                if (msAccessToken == null) {
                    callback.onResult(null, "Время ожидания входа истекло. Повторите попытку.");
                    return;
                }

                // 3) Xbox Live
                String xboxBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"" + escapeJson(msAccessToken) + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
                HttpRequest xboxReq = HttpRequest.newBuilder()
                        .uri(URI.create(XBOX_AUTH_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(xboxBody))
                        .build();
                HttpResponse<String> xboxResp = client.send(xboxReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (xboxResp.statusCode() != 200) {
                    callback.onResult(null, "Ошибка Xbox Live: " + xboxResp.statusCode());
                    return;
                }
                JsonObject xboxJson = JsonParser.parseString(xboxResp.body()).getAsJsonObject();
                String xboxToken = xboxJson.get("Token").getAsString();
                String uhs = xboxJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

                // 4) XSTS
                String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + escapeJson(xboxToken) + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
                HttpRequest xstsReq = HttpRequest.newBuilder()
                        .uri(URI.create(XSTS_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(xstsBody))
                        .build();
                HttpResponse<String> xstsResp = client.send(xstsReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (xstsResp.statusCode() != 200) {
                    String errBody = xstsResp.body();
                    if (errBody.contains("2148916233")) {
                        callback.onResult(null, "У аккаунта нет Xbox. Зайдите на xbox.com и привяжите профиль.");
                    } else if (errBody.contains("2148916238")) {
                        callback.onResult(null, "Аккаунт несовершеннолетнего. Добавьте в семейную группу Xbox.");
                    } else {
                        callback.onResult(null, "Ошибка XSTS: " + xstsResp.statusCode());
                    }
                    return;
                }
                JsonObject xstsJson = JsonParser.parseString(xstsResp.body()).getAsJsonObject();
                String xstsToken = xstsJson.get("Token").getAsString();

                // 5) Minecraft login
                String identityToken = "XBL3.0 x=" + uhs + ";" + xstsToken;
                String mcBody = "{\"identityToken\":\"" + escapeJson(identityToken) + "\",\"ensureLegacyEnabled\":true}";
                HttpRequest mcLoginReq = HttpRequest.newBuilder()
                        .uri(URI.create(MC_LOGIN_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mcBody))
                        .build();
                HttpResponse<String> mcLoginResp = client.send(mcLoginReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (mcLoginResp.statusCode() != 200) {
                    callback.onResult(null, "Ошибка входа Minecraft: " + mcLoginResp.statusCode());
                    return;
                }
                JsonObject mcLoginJson = JsonParser.parseString(mcLoginResp.body()).getAsJsonObject();
                String mcAccessToken = mcLoginJson.get("access_token").getAsString();

                // 6) Profile (username, uuid)
                HttpRequest profileReq = HttpRequest.newBuilder()
                        .uri(URI.create(MC_PROFILE_URL))
                        .header("Authorization", "Bearer " + mcAccessToken)
                        .GET()
                        .build();
                HttpResponse<String> profileResp = client.send(profileReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (profileResp.statusCode() != 200) {
                    callback.onResult(null, "Нет лицензии Minecraft или ошибка профиля: " + profileResp.statusCode());
                    return;
                }
                JsonObject profile = JsonParser.parseString(profileResp.body()).getAsJsonObject();
                String username = profile.get("name").getAsString();
                String uuidStr = profile.get("id").getAsString();
                UUID uuid = parseUuid(uuidStr);

                String xuid = null;
                if (xboxJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().has("xid")) {
                    xuid = xboxJson.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("xid").getAsString();
                }

                MicrosoftSessionResult result = new MicrosoftSessionResult(
                        username,
                        uuid,
                        mcAccessToken,
                        xuid != null ? Optional.of(xuid) : Optional.empty()
                );
                callback.onResult(result, null);
            } catch (Exception e) {
                callback.onResult(null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static UUID parseUuid(String id) {
        if (id == null || id.isBlank()) return null;
        String clean = id.replace("-", "");
        if (clean.length() != 32) return null;
        return UUID.fromString(clean.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    public interface ResultCallback {
        void onProgress(String message, String userCode, String verificationUrl);

        void onResult(MicrosoftSessionResult session, String errorMessage);
    }

    public static final class MicrosoftSessionResult {
        private final String username;
        private final UUID uuid;
        private final String accessToken;
        private final Optional<String> xuid;

        public MicrosoftSessionResult(String username, UUID uuid, String accessToken, Optional<String> xuid) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.xuid = xuid;
        }

        public String getUsername() { return username; }
        public UUID getUuid() { return uuid; }
        public String getAccessToken() { return accessToken; }
        public Optional<String> getXuid() { return xuid; }
    }
}
