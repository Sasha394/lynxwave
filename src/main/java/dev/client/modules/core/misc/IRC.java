package dev.client.modules.core.misc;


import dev.client.api.compat.RenderCompat;
import com.google.common.eventbus.Subscribe;
import dev.client.api.compat.RenderSystem;
import dev.client.LynxWave;
import dev.client.api.nullcry.ClientApi;
import dev.client.api.nullcry.events.core.input.KeyBindEvent;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.api.nullcry.helper.client.irc.ClientLabelRegistry;
import dev.client.api.nullcry.helper.client.irc.IRChatScreen;
import dev.client.api.nullcry.helper.client.irc.IRClient;
import dev.client.api.nullcry.helper.client.irc.PartyManager;
import dev.client.api.nullcry.helper.client.irc.PartyManager.PartyMarkerSnapshot;
import dev.client.api.nullcry.helper.client.projection.ProjectionUtil;
import dev.client.api.nullcry.helper.math.MathUtil;
import dev.client.api.nullcry.modules.Module;
import dev.client.api.nullcry.modules.ModuleCategory;
import dev.client.api.nullcry.modules.settings.CheckBox;
import dev.client.api.nullcry.modules.settings.KeyBind;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.api.nullcry.render.core.DrawUtil;
import dev.client.api.nullcry.render.core.builders.Builder;
import dev.client.modules.core.render.Interface;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public class IRC extends Module {
    public static IRC INSTANCE;

    public IRC() {
        super("IRC", ModuleCategory.Misc, "Позволяет общаться с юзерами читов");
        INSTANCE = this;
    }

    CheckBox markersEnabled = new CheckBox("Метки", () -> true).defaultValue(false).register(this);
    KeyBind markerKey = new KeyBind("Кнопка метки", markersEnabled::getEnabled).set(-1).register(this);
    KeyBind sendCoordinatesKey = new KeyBind("Кнопка координат", () -> PartyManager.getInstance().hasParty())
            .set(-1)
            .register(this);
    KeyBind openChatKey = new KeyBind("Открыть чат IRC", () -> true)
            .set(-1)
            .register(this);
    CheckBox autoPlacePartyCoordinates = new CheckBox("Ставить метку на координаты из party", () -> PartyManager.getInstance().hasParty())
            .defaultValue(false)
            .register(this);

    String SERVER_URI = "ws://2.26.81.84:8080";
    long MARKER_LIFETIME_MS = 15000L;
    double MARKER_RAY_DISTANCE = 120.0D;

    @Subscribe
    public void onKey(KeyBindEvent event) {
        handleMarkerKey(event);
        handleSendCoordinatesKey(event);
        handleOpenChatKey(event);
    }

    private void handleOpenChatKey(KeyBindEvent event) {
        Integer key = openChatKey.getKey();
        if (key == null || key == -1) return;
        if (event.isKeyDown(key) && mc.currentScreen == null) {
            mc.setScreen(new IRChatScreen());
        }
    }

    private void handleMarkerKey(KeyBindEvent event) {
        Integer key = markerKey.getKey();
        if (key == null || key == -1) {
            return;
        }
        if (!markersEnabled.getEnabled()) {
            return;
        }
        if (event.isKeyDown(key)) {
            placeMarker();
        }
    }

    private void handleSendCoordinatesKey(KeyBindEvent event) {
        Integer key = sendCoordinatesKey.getKey();
        if (key == null || key == -1) {
            return;
        }
        if (!PartyManager.getInstance().hasParty()) {
            return;
        }
        if (event.isKeyDown(key)) {
            sendCurrentCoordinatesToParty();
        }
    }

    @Subscribe
    public void onRender3D(RenderEvent.Draw3D event) {
        if (!shouldRenderMarkers()) {
            return;
        }
        List<PartyMarkerSnapshot> markers = PartyManager.getInstance().getMarkersSnapshot(MARKER_LIFETIME_MS);
        if (markers.isEmpty()) {
            return;
        }
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) {
            return;
        }

        MatrixStack matrices = event.getMatrices();
        matrices.push();
        DrawUtil.setupRender();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Vec3d cameraPos = camera.getCameraPos();
        Color baseColor = new Color(Interface.INSTANCE.getMainColor(), true);
        long now = System.currentTimeMillis();

        for (PartyMarkerSnapshot marker : markers) {
            float progress = MathHelper.clamp((now - marker.getCreatedAt()) / (float) MARKER_LIFETIME_MS, 0.0f, 1.0f);
            float easedProgress = MathHelper.clamp(progress * 1.1f, 0.0f, 1.0f);
            float radius = MathUtil.lerp(0.0f, 3.0f, easedProgress);
            float alpha = 1.0f - progress;
            int alphaInt = MathHelper.clamp((int) (alpha * 200.0f), 0, 200);

            Vec3d pos = new Vec3d(marker.getX(), marker.getY(), marker.getZ());
            double x = pos.x - cameraPos.x;
            double y = pos.y - cameraPos.y + 0.05;
            double z = pos.z - cameraPos.z;

            float minX = (float) (x - radius);
            float maxX = (float) (x + radius);
            float minZ = (float) (z - radius);
            float maxZ = (float) (z + radius);

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            buffer.vertex(matrix, minX, (float) y, minZ).color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt);
            buffer.vertex(matrix, maxX, (float) y, minZ).color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt);
            buffer.vertex(matrix, maxX, (float) y, maxZ).color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt);
            buffer.vertex(matrix, minX, (float) y, maxZ).color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt);
        }

        RenderCompat.draw(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        DrawUtil.endRender();
        matrices.pop();
    }

    @Subscribe
    public void onRender2D(RenderEvent.Draw2D event) {
        if (!shouldRenderMarkers() || mc.player == null) {
            return;
        }
        List<PartyMarkerSnapshot> markers = PartyManager.getInstance().getMarkersSnapshot(MARKER_LIFETIME_MS);
        if (markers.isEmpty()) {
            return;
        }

        DrawContext context = event.getContext();
        if (context == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Matrix4f matrix = dev.client.api.compat.GuiMatrixCompat.toMatrix4(context);

        for (PartyMarkerSnapshot marker : markers) {
            Vec3d screen = ProjectionUtil.projectCoordinates(new Vec3d(marker.getX(), marker.getY() + 1.1, marker.getZ()));
            if (screen.z <= 0 || screen.z >= 1) {
                continue;
            }
            double distance = dev.client.api.compat.EntityCompat.pos(mc.player).distanceTo(new Vec3d(marker.getX(), marker.getY(), marker.getZ()));
            float progress = MathHelper.clamp((now - marker.getCreatedAt()) / (float) MARKER_LIFETIME_MS, 0.0f, 1.0f);
            float alpha = MathHelper.clamp(1.0f - progress, 0.0f, 1.0f);
            int color = ColorUtils.setAlpha(Interface.INSTANCE.getMainColor(), MathHelper.clamp((int) (alpha * 255.0f), 0, 255));
            String distanceLabel = String.format(Locale.ROOT, "%.1fм", distance);
            String label = marker.getDisplayName() + " (" + distanceLabel + ")";

            float textSize = 7.0f;
            double width = Builder.INTER.get().getWidth(label, textSize, 0.05f, 0);
            double x = screen.x - width / 2.0;
            double y = screen.y - 12.0;

            Builder.TEXT_BUILDER.size(textSize).color(color).font(Builder.INTER.get()).text(label).build()
                    .render(matrix, x, y);
        }
    }

    public IRClient ensureConnected() throws Exception {
        IRClient IRClient = LynxWave.getInstance().getIRClient();
        if (IRClient == null) {
            IRClient = createNewConnection();
            if (IRClient == null) {
                throw new IllegalStateException("IRC connection could not be created");
            }
            LynxWave.getInstance().setIRClient(IRClient);
            IRClient.connect();
            return IRClient;
        }
        if (!IRClient.isOpen()) {
            IRClient.reconnect();
            IRClient = LynxWave.getInstance().getIRClient();
        }
        return IRClient;
    }

    public void disconnect() {
        IRClient IRClient = LynxWave.getInstance().getIRClient();
        if (IRClient != null) {
            IRClient.shutdown();
            LynxWave.getInstance().setIRClient(null);
        }
        ClientLabelRegistry.getInstance().clear();
        PartyManager.getInstance().clear();
    }

    private void placeMarker() {
        if (!canSendMarker()) {
            return;
        }
        Vec3d target = resolveMarkerPosition();
        if (target == null) {
            printClient(Formatting.RED + "Не удалось определить позицию метки");
            return;
        }
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client != null) {
            client.sendPartyMarker(target.x, target.y, target.z);
        }
    }

    private boolean shouldRenderMarkers() {
        return isEnabled() && markersEnabled.getEnabled() && PartyManager.getInstance().hasParty();
    }

    private boolean canSendMarker() {
        if (!markersEnabled.getEnabled() || mc.player == null || mc.world == null) {
            return false;
        }
        PartyManager partyManager = PartyManager.getInstance();
        if (!partyManager.hasParty()) {
            printClient(Formatting.RED + "Создайте или вступите в пати, чтобы ставить метки");
            return false;
        }
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client == null || !client.isOpen()) {
            printClient(Formatting.RED + "IRC не подключён");
            return false;
        }
        if (!client.isLoggedIn()) {
            printClient(Formatting.RED + "Авторизуйтесь в IRC для использования меток");
            return false;
        }
        return true;
    }

    private Vec3d resolveMarkerPosition() {
        if (mc.player == null) {
            return null;
        }
        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        HitResult hitResult = mc.player.raycast(MARKER_RAY_DISTANCE, tickDelta, false);
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            Vec3d origin = mc.player.getCameraPosVec(tickDelta);
            Vec3d direction = mc.player.getRotationVec(tickDelta).multiply(MARKER_RAY_DISTANCE);
            return origin.add(direction);
        }
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hitResult).getPos();
        }
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hitResult).getPos();
        }
        return null;
    }

    private void sendCurrentCoordinatesToParty() {
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client == null) {
            printClient(Formatting.RED + "IRC не подключён");
            return;
        }
        client.sendOwnPartyPosition();
    }

    public void handleIncomingPartyCoordinates(String ircName, String gameName, double x, double y, double z) {
        if (!autoPlacePartyCoordinates.getEnabled()) {
            return;
        }
        IRClient client = LynxWave.getInstance().getIRClient();
        if (client != null) {
            String selfNickname = client.getCurrentNickname();
            if (ircName != null && selfNickname != null && ircName.equalsIgnoreCase(selfNickname)) {
                return;
            }
        }
        String label = gameName != null && !gameName.isBlank() ? gameName : ircName;
        if (label == null || label.isBlank()) {
            label = "PartyPoint";
        }
        int xi = (int) Math.round(x);
        int yi = (int) Math.round(y);
        int zi = (int) Math.round(z);
        printChat(String.format(Locale.ROOT, ".gps add %s %d %d %d", label, xi, yi, zi));
    }

    private IRClient createNewConnection() throws Exception {
        return new IRClient(new URI(SERVER_URI));
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        try {
            ensureConnected();
        } catch (Exception e) {
            printClient("Не удалось подключиться к IRC: " + Formatting.RED + e.getMessage());
        }
    }

    @Override
    public void onDisabled() {
        disconnect();
        super.onDisabled();
    }
}
